package app.navimuse.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import android.os.Bundle
import kotlinx.coroutines.launch
import app.navimuse.BuildConfig
import app.navimuse.MainActivity
import app.navimuse.data.Blocklist
import app.navimuse.NaviMuseApp
import app.navimuse.net.AlbumListType
import app.navimuse.net.Song
import app.navimuse.net.SubsonicClient
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

/**
 * Plays music in the background, with notification / lock-screen / Bluetooth controls,
 * and shows the library in Android Auto.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaLibrarySession? = null
    private var scrobbler: Scrobbler? = null
    private var preloader: AutoCacher? = null
    private lateinit var player: ExoPlayer
    private lateinit var memory: QueueMemory

    private val app: NaviMuseApp get() = application as NaviMuseApp

    override fun onCreate() {
        super.onCreate()
        memory = QueueMemory(this)

        // If the phone's Dolby (or other) decoder can't start, try its next decoder instead of stopping.
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(Streaming.playerSource(app))
                    .setLoadErrorHandlingPolicy(Streaming.loadErrors()),
            )
            .setLoadControl(Streaming.loadControl())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(open)
            .setMediaButtonPreferences(buttons())
            .build()

        scrobbler = Scrobbler(player, scope, app).also { it.start() }
        preloader = AutoCacher(player, scope, app).also { it.start() }
        openEffects(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                closeEffects()
                openEffects(audioSessionId)
            }

            // What's really playing: Dolby Atmos (E-AC-3 JOC), other Dolby, or surround. Remembered,
            // so the song and its album always stream untouched from now on.
            override fun onTracksChanged(tracks: Tracks) {
                val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected } ?: return
                val index = (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: return
                val format = group.getTrackFormat(index)
                val label = Dolby.fromMime(format.sampleMimeType, format.channelCount) ?: return
                player.currentMediaItem?.toSong()?.let { Dolby.learn(it, label) }
            }
        })
        // A song that didn't play: first see what the server is sending (see StreamCheck). An error
        // from Navidrome (the song's file is gone, say) is said as it is, and the queue moves on.
        // Audio the phone can't play is tried another way (the original file, MP3, a lower bitrate)
        // from the same spot, and what worked is remembered once it really plays.
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (Convert.isSongError(error.errorCode)) lookInto(error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_READY) return
                skippedInARow = 0
                busyInARow = 0
                val item = player.currentMediaItem ?: return
                val song = item.toSong() ?: return
                retriedSame -= song.id
                val kbps = kbpsOfKey(item.localConfiguration?.customCacheKey) ?: return
                val switch = Convert.worked(song, kbps) ?: return
                app.player.notice(
                    "\"${song.title}\" wouldn't play as ${Convert.describe(switch.from, song.suffix)} on this phone, " +
                        "so it's playing as ${Convert.describe(switch.to, song.suffix)}.",
                )
            }
        })
        // Autoplay radio: when the last song up starts (the end of the queue, a single, a one-song
        // album), songs like it are added after it, so the music keeps going.
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) autoRadioFor = null
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    )
                ) {
                    keepGoing()
                }
            }
        })

        // Blocked songs and artists are skipped when they come up next (picking one yourself plays it).
        player.addListener(object : Player.Listener {
            private var skipped = 0
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val passing = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                val song = mediaItem?.toSong()
                if (passing && song != null && Blocklist.isBlocked(song) && player.hasNextMediaItem() && skipped < player.mediaItemCount) {
                    skipped++
                    player.seekToNextMediaItem()
                } else {
                    skipped = 0
                }
            }
        })
        // Keep the car's (and notification's) favourite, shuffle and repeat buttons in step.
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refreshButtons()
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = refreshButtons()
            override fun onRepeatModeChanged(repeatMode: Int) = refreshButtons()
        })
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = memory.save(player)
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) memory.save(player)
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) memory.save(player)
            }
        })

        restoreQueue()
    }

    // --- songs that don't play -------------------------------------------------------------

    /** Songs skipped one after another because the server wouldn't send them (so a queue of them ends). */
    private var skippedInARow = 0
    /** Times in a row the server said it was too busy converting. */
    private var busyInARow = 0
    /** Songs already given a second go in the same form, after the server sent something odd once. */
    private val retriedSame = HashSet<String>()
    private var looking: Job? = null

    /** Works out why the current song stopped (see the listener above) and does something about it. */
    private fun lookInto(error: PlaybackException) {
        val index = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return
        val song = item.toSong() ?: return
        val api = app.api() ?: return
        val key = item.localConfiguration?.customCacheKey
        val failed = kbpsOfKey(key)
        // A broken half-saved copy would fail the same way again.
        if (failed != null && key != null) runCatching { AudioCache.get(app).removeResource(key) }
        looking?.cancel()
        looking = scope.launch {
            // The phone's own download didn't play: nothing to ask the server, stream it instead.
            val check = if (failed == null) StreamCheck.Audio else look(api, song.id, failed, item.localConfiguration?.uri?.toString())
            // Moved on meanwhile (another song, or Play pressed again): leave it be.
            fun stillStuck() = player.playerError != null && player.currentMediaItemIndex == index &&
                player.currentMediaItem?.mediaId == item.mediaId
            // Autoplay may be adding songs after this one: wait for it, so there's somewhere to go.
            autoRadioJob?.join()
            if (!stillStuck()) return@launch
            when (check) {
                StreamCheck.Audio -> {
                    if (Convert.isFormatError(error.errorCode)) {
                        tryAnotherWay(index, item, song, failed, api)
                    } else if (retriedSame.add(song.id)) {
                        // The server sent an error before but audio now: once more as it was.
                        player.prepare()
                    } else {
                        Convert.giveUp(song.id)
                        app.player.notice("Can't play \"${song.title}\": your server didn't send it properly. Press play to try again.")
                    }
                }
                is StreamCheck.Busy -> {
                    if (++busyInARow > 3) {
                        Convert.giveUp(song.id)
                        app.player.notice(SongTrouble.notice(song.title, check, skipped = false))
                    } else {
                        delay(check.retryAfterSeconds * 1000L)
                        if (stillStuck()) player.prepare()
                    }
                }
                StreamCheck.Unreachable -> {
                    Convert.giveUp(song.id)
                    app.player.notice("Can't reach your server to play \"${song.title}\".")
                }
                StreamCheck.Empty, is StreamCheck.Refused -> {
                    // The server's trouble, not the phone's: nothing to learn about formats.
                    Convert.giveUp(song.id)
                    val songOnly = check !is StreamCheck.Refused || check.songOnly
                    if (songOnly && player.hasNextMediaItem() && skippedInARow < player.mediaItemCount) {
                        skippedInARow++
                        app.player.notice(SongTrouble.notice(song.title, check, skipped = true))
                        player.seekToNextMediaItem()
                        player.prepare()
                    } else {
                        app.player.notice(SongTrouble.notice(song.title, check, skipped = false))
                    }
                }
            }
        }
    }

    /**
     * What the server sends for [songId] as [failed]. When a conversion failed on the server (an
     * empty answer: ffmpeg had nothing to read, or an error), the original file says why: if it's
     * missing, that's the answer; if it's there, the phone can try it another way.
     */
    private suspend fun look(api: SubsonicClient, songId: String, failed: Int, url: String?): StreamCheck {
        val check = StreamCheck.run(app.http, url ?: api.streamUrl(songId, failed))
        if (failed == 0 || check == StreamCheck.Audio || check is StreamCheck.Busy || check == StreamCheck.Unreachable) return check
        return StreamCheck.run(app.http, api.streamUrl(songId, 0))
    }

    /** The phone can't play [song] as [failed]: the next form to try, or on to the next song. */
    private fun tryAnotherWay(index: Int, item: MediaItem, song: Song, failed: Int?, api: SubsonicClient) {
        val next = Convert.nextTry(song.id, failed, app.currentMaxBitRate())
        if (next == null) {
            if (player.hasNextMediaItem()) {
                app.player.notice("This phone can't play \"${song.title}\" in any form the server sends, so it was skipped.")
                player.seekToNextMediaItem()
                player.prepare()
            } else {
                app.player.notice("This phone can't play \"${song.title}\" in any form the server sends.")
            }
            return
        }
        val position = player.currentPosition
        player.replaceMediaItem(
            index,
            item.buildUpon().setUri(api.streamUrl(song.id, next)).setCustomCacheKey("${song.id}@$next").build(),
        )
        player.seekTo(index, position)
        player.prepare()
    }

    // --- autoplay radio ----------------------------------------------------------------------

    /** The queue item autoplay last looked for songs for (asked once per song, even if none came). */
    private var autoRadioFor: String? = null
    private var autoRadioJob: Job? = null

    /**
     * When the song playing is the last one up and repeat is off, adds songs like it after it (see
     * [autoplayPicks]), so the music keeps going without a gap. They're marked as autoplay, so "Add to queue" puts your
     * songs before them.
     */
    private fun keepGoing() {
        if (!app.settings.value.autoRadio) return
        if (!player.playWhenReady || player.repeatMode != Player.REPEAT_MODE_OFF) return
        if (player.mediaItemCount == 0 || player.hasNextMediaItem()) return
        val item = player.currentMediaItem ?: return
        val seed = item.toSong() ?: return
        if (autoRadioFor == item.mediaId) return
        autoRadioFor = item.mediaId
        autoRadioJob?.cancel()
        autoRadioJob = scope.launch {
            val api = app.api() ?: return@launch
            val size = app.settings.value.mixSize
            val queued = (0 until player.mediaItemCount).mapNotNullTo(HashSet<String>()) { MediaIds.songIdOf(player.getMediaItemAt(it).mediaId) }
            val picks = autoplayPicks(api, seed, size, queued)
            // Something else came up next meanwhile (you queued songs, or picked another): leave it.
            if (picks.isEmpty() || player.currentMediaItem?.mediaId != item.mediaId || player.hasNextMediaItem()) return@launch
            val items = picks.map { it.toMediaItem(api, app.bitrateFor(it), MediaIds.AUTOPLAY) }
            // With shuffle on, new songs would be shuffled in among the ones already played: keep
            // the order so far, then the radio in its own order.
            val order = if (player.shuffleModeEnabled) shuffleOrderNow() else null
            val first = player.mediaItemCount
            player.addMediaItems(items)
            if (order != null && order.size == first) {
                player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder((order + (first until first + items.size)).toIntArray(), System.nanoTime()))
            }
        }
    }

    /**
     * Songs to keep going with after [seed], none of them [queued] or blocked: songs that sound
     * like it (AudioMuse-AI's Instant Mix), else its artist's radio, else songs from its genre,
     * else anything from your library, so the music never just stops.
     */
    private suspend fun autoplayPicks(api: SubsonicClient, seed: Song, size: Int, queued: Set<String>): List<Song> {
        suspend fun from(source: suspend () -> List<Song>): List<Song> =
            Blocklist.allowed(runCatching { source() }.getOrDefault(emptyList())).filter { it.id !in queued }.distinctBy { it.id }
        from { api.similarSongs(seed.id, size) }.let { if (it.isNotEmpty()) return it }
        seed.artistId?.let { artistId -> from { api.similarSongsByArtist(artistId, size) }.let { if (it.isNotEmpty()) return it } }
        // The queue's copy of a song doesn't carry its genre: ask for it.
        val genre = seed.genre ?: runCatching { api.song(seed.id)?.genre }.getOrNull()
        if (!genre.isNullOrBlank()) from { api.randomSongs(size, genre) }.let { if (it.isNotEmpty()) return it }
        return from { api.randomSongs(size) }
    }

    /** The queue's indices in the order shuffle plays them. */
    private fun shuffleOrderNow(): List<Int> {
        val timeline = player.currentTimeline
        val out = ArrayList<Int>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(true)
        while (i != C.INDEX_UNSET && out.size < timeline.windowCount) {
            out += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
        }
        return out
    }

    /** Puts the last queue back (paused), so the app opens where you left off. */
    private fun restoreQueue() {
        val api = app.api() ?: return
        val saved = memory.load() ?: return
        player.setMediaItems(saved.songs.map { it.toMediaItem(api, app.bitrateFor(it)) }, saved.index, saved.positionMs)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away stops the service unless music is playing.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            memory.save(player)
            stopSelf()
        }
    }

    /**
     * Tells the phone's sound-effects apps (Samsung's Dolby Atmos and equalizer, Wavelet…)
     * that music is playing on this audio output, so their effects apply to it.
     */
    private fun openEffects(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        app.audioSessionId = sessionId
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC),
        )
    }

    private fun closeEffects() {
        val sessionId = app.audioSessionId
        if (sessionId == 0) return
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName),
        )
        app.audioSessionId = 0
    }

    override fun onDestroy() {
        closeEffects()
        memory.save(player)
        scrobbler?.stop()
        preloader?.stop()
        session?.release()
        session = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    // --- Android Auto and notification buttons ----------------------------------------------

    /** Favourites changed from the car or the notification, ahead of the song's own details. */
    private val starredNow = HashMap<String, Boolean>()

    private fun currentStarred(): Boolean {
        val song = player.currentMediaItem?.toSong() ?: return false
        return starredNow[song.id] ?: song.starred
    }

    /**
     * The buttons beside play/pause in Android Auto (and in the phone's media controls), like
     * Tidal's: favourite, shuffle, repeat and radio (an Instant Mix from what's playing).
     */
    private fun buttons(): ImmutableList<CommandButton> {
        val starred = currentStarred()
        val shuffle = player.shuffleModeEnabled
        val repeat = player.repeatMode
        fun button(icon: Int, name: String, command: SessionCommand) =
            CommandButton.Builder(icon)
                .setDisplayName(name)
                .setSessionCommand(command)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build()
        return ImmutableList.of(
            button(
                if (starred) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
                if (starred) "Remove from favourites" else "Add to favourites",
                FAVOURITE,
            ),
            button(if (shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF, if (shuffle) "Shuffle on" else "Shuffle", SHUFFLE),
            button(
                when (repeat) {
                    Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                    Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                    else -> CommandButton.ICON_REPEAT_OFF
                },
                when (repeat) {
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    else -> "Repeat"
                },
                REPEAT,
            ),
            button(CommandButton.ICON_RADIO, "Instant Mix", RADIO),
        )
    }

    private fun refreshButtons() {
        session?.setMediaButtonPreferences(buttons())
    }

    /** Records a song's favourite state here, so the car's heart and the app agree. */
    private fun markStarred(songId: String, starred: Boolean) {
        starredNow[songId] = starred
        val index = player.currentMediaItemIndex
        val item = player.currentMediaItem
        if (item != null && item.toSong()?.id == songId) {
            // The song's own details too, so the app shows the same.
            val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply { putBoolean(Extras.STARRED, starred) }
            player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build())
        }
        refreshButtons()
    }

    private fun toggleFavourite() {
        val song = player.currentMediaItem?.toSong() ?: return
        val now = !currentStarred()
        markStarred(song.id, now)
        scope.launch {
            try {
                val api = requireApi()
                if (now) api.star(song.id, SubsonicClient.StarKind.SONG) else api.unstar(song.id, SubsonicClient.StarKind.SONG)
                if (now && app.settings.value.autoDownloadFavourites) runCatching { app.syncAutoDownloads() }
            } catch (e: Exception) {
                // Didn't reach the server: put the button back.
                markStarred(song.id, !now)
            }
        }
    }

    /** Radio: this song, then songs that sound like it (needs AudioMuse-AI behind Navidrome). */
    private fun startRadio() {
        val seed = player.currentMediaItem?.toSong() ?: return
        scope.launch {
            val items = runCatching { mix(seed, app.settings.value.mixSize) }.getOrDefault(emptyList())
            if (items.size < 2) return@launch // Nothing similar came back; keep what's playing.
            player.setMediaItems(items, 0, C.TIME_UNSET)
            player.prepare()
            player.play()
        }
    }

    // --- resolving what to play -------------------------------------------------------------

    private fun requireApi(): SubsonicClient =
        app.api() ?: throw IllegalStateException("Not logged in to Navidrome")

    private fun Song.item(context: String? = null): MediaItem =
        toMediaItem(requireApi(), app.bitrateFor(this), context)

    /** Gives each item a fresh stream link (and its details, if they're missing). */
    private suspend fun resolve(items: List<MediaItem>): MutableList<MediaItem> {
        val api = requireApi()
        val out = ArrayList<MediaItem>(items.size)
        for (item in items) {
            val songId = MediaIds.songIdOf(item.mediaId)
            if (songId == null) {
                // A voice request like "play Daft Punk on NaviMuse".
                val query = item.requestMetadata.searchQuery
                if (!query.isNullOrBlank()) {
                    out += api.search(query, songs = 50, albums = 0, artists = 0).songs.map { it.toMediaItem(api, app.bitrateFor(it)) }
                }
                continue
            }
            val known = item.toSong()
            if (item.mediaMetadata.title != null && known != null) {
                val kbps = app.bitrateFor(known)
                out += item.buildUpon()
                    .setUri(api.streamUrl(songId, kbps))
                    .setCustomCacheKey(streamKey(songId, kbps))
                    .build()
            } else {
                api.song(songId)?.let { out += it.toMediaItem(api, app.bitrateFor(it), MediaIds.contextOf(item.mediaId)) }
            }
        }
        return out
    }

    /** One tapped item that stands for a whole list (an album track, a mix…). */
    private suspend fun expand(mediaId: String): Pair<List<MediaItem>, Int>? {
        val api = requireApi()
        val mixSize = app.settings.value.mixSize
        when {
            mediaId == MediaIds.MIX_NOW -> {
                val seed = player.currentMediaItem?.toSong() ?: api.randomSongs(1).firstOrNull() ?: return null
                return mix(seed, mixSize) to 0
            }
            mediaId == MediaIds.SHUFFLE_ALL -> return Blocklist.allowed(api.randomSongs(100)).map { it.item() } to 0
            mediaId.startsWith(MediaIds.MIX_ALBUM_PREFIX) -> {
                val songs = api.album(mediaId.removePrefix(MediaIds.MIX_ALBUM_PREFIX)).songs
                val seed = songs.randomOrNull() ?: return null
                return mix(seed, mixSize) to 0
            }
        }
        val songId = MediaIds.songIdOf(mediaId) ?: return null
        val context = MediaIds.contextOf(mediaId) ?: return null
        val list: List<Song> = when {
            context.startsWith("a|") -> api.album(context.removePrefix("a|")).songs
            context.startsWith("p|") -> api.playlist(context.removePrefix("p|")).songs
            context == "f" -> api.starred().songs
            else -> return null
        }
        if (list.isEmpty()) return null
        val index = list.indexOfFirst { it.id == songId }.coerceAtLeast(0)
        return list.map { it.item(context) } to index
    }

    private suspend fun mix(seed: Song, size: Int): List<MediaItem> {
        val similar = Blocklist.allowed(runCatching { requireApi().similarSongs(seed.id, size) }.getOrDefault(emptyList()))
        return (listOf(seed) + similar.filter { it.id != seed.id }).map { it.item() }
    }

    // --- Android Auto browse tree -----------------------------------------------------------

    private suspend fun children(parentId: String): List<MediaItem> {
        val api = requireApi()
        return when {
            parentId == MediaIds.ROOT -> listOf(
                browsable(MediaIds.MIXES, "Mixes"),
                browsable(MediaIds.RECENT, "Recently added", type = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                browsable(MediaIds.PLAYLISTS, "Playlists", type = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                browsable(MediaIds.STARRED, "Favourites"),
            )
            parentId == MediaIds.MIXES -> buildList {
                add(playableAction(MediaIds.MIX_NOW, "Instant Mix", "Songs that sound like what's playing"))
                add(playableAction(MediaIds.SHUFFLE_ALL, "Shuffle everything"))
                api.albumList(AlbumListType.FREQUENT, size = 8).forEach { a ->
                    add(
                        playableAction(
                            MediaIds.MIX_ALBUM_PREFIX + a.id,
                            "Mix: ${a.name}",
                            a.artist,
                            api.coverUrl(a.coverArt, 300)?.let(Uri::parse),
                        ),
                    )
                }
            }
            parentId == MediaIds.RECENT -> api.albumList(AlbumListType.NEWEST, size = 50).map { it.toBrowsable(api) }
            parentId == MediaIds.PLAYLISTS -> api.playlists().map { it.toBrowsable(api) }
            parentId == MediaIds.STARRED -> api.starred().songs.map { it.item("f") }
            parentId.startsWith("a|") -> {
                val id = parentId.removePrefix("a|")
                api.album(id).songs.map { it.item(parentId) }
            }
            parentId.startsWith("p|") -> {
                val id = parentId.removePrefix("p|")
                api.playlist(id).songs.map { it.item(parentId) }
            }
            else -> emptyList()
        }
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        /** Every controller (Android Auto, the notification, the app) may use the extra buttons. */
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(FAVOURITE)
                .add(SHUFFLE)
                .add(REPEAT)
                .add(RADIO)
                .add(SessionCommand(ACTION_SET_STARRED, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setMediaButtonPreferences(buttons())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                FAVOURITE.customAction -> toggleFavourite()
                SHUFFLE.customAction -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                REPEAT.customAction -> player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                RADIO.customAction -> startRadio()
                ACTION_SET_STARRED -> args.getString("id")?.let { markStarred(it, args.getBoolean("starred")) }
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browsable(MediaIds.ROOT, "NaviMuse"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            try {
                val all = children(parentId)
                val from = (page.toLong() * pageSize).coerceAtMost(all.size.toLong()).toInt()
                val to = (from.toLong() + pageSize).coerceAtMost(all.size.toLong()).toInt()
                LibraryResult.ofItemList(if (pageSize > 0) all.subList(from, to) else all, params)
            } catch (e: Exception) {
                LibraryResult.ofError<ImmutableList<MediaItem>>(SessionError.ERROR_UNKNOWN)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            try {
                val songId = MediaIds.songIdOf(mediaId)
                val item = if (songId != null) {
                    requireApi().song(songId)?.item(MediaIds.contextOf(mediaId))
                } else {
                    browsable(mediaId, mediaId)
                }
                if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError<MediaItem>(SessionError.ERROR_BAD_VALUE)
            } catch (e: Exception) {
                LibraryResult.ofError<MediaItem>(SessionError.ERROR_UNKNOWN)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            val single = mediaItems.singleOrNull()
            val expanded = single?.let { if (MediaIds.contextOf(it.mediaId) == null) expand(it.mediaId) else null }
            expanded?.first?.toMutableList() ?: resolve(mediaItems)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val single = mediaItems.singleOrNull()
            val expanded = single?.let { expand(it.mediaId) }
            if (expanded != null) {
                MediaSession.MediaItemsWithStartPosition(expanded.first, expanded.second, C.TIME_UNSET)
            } else {
                val resolved = resolve(mediaItems)
                val index = if (startIndex == C.INDEX_UNSET) C.INDEX_UNSET else startIndex.coerceIn(0, (resolved.size - 1).coerceAtLeast(0))
                MediaSession.MediaItemsWithStartPosition(resolved, index, startPositionMs)
            }
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val api = requireApi()
            val saved = memory.load() ?: throw IllegalStateException("Nothing to resume")
            MediaSession.MediaItemsWithStartPosition(
                saved.songs.map { it.toMediaItem(api, app.bitrateFor(it)) },
                saved.index,
                saved.positionMs,
            )
        }
    }

    companion object {
        /** Sent by the app when a song is (un)favourited there. Args: "id", "starred". */
        const val ACTION_SET_STARRED = "app.navimuse.SET_STARRED"
        private val FAVOURITE = SessionCommand("app.navimuse.FAVOURITE", Bundle.EMPTY)
        private val SHUFFLE = SessionCommand("app.navimuse.SHUFFLE", Bundle.EMPTY)
        private val REPEAT = SessionCommand("app.navimuse.REPEAT", Bundle.EMPTY)
        private val RADIO = SessionCommand("app.navimuse.RADIO", Bundle.EMPTY)
    }
}
