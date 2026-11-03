package app.navimuse.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import kotlinx.coroutines.SupervisorJob
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
    private var preloader: NextTrackPreloader? = null
    private lateinit var player: ExoPlayer
    private lateinit var memory: QueueMemory

    private val app: NaviMuseApp get() = application as NaviMuseApp

    override fun onCreate() {
        super.onCreate()
        memory = QueueMemory(this)

        // If the phone's Dolby (or other) decoder can't start, try its next decoder instead of stopping.
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(Streaming.playerSource(app)))
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
        preloader = NextTrackPreloader(player, scope, app).also { it.start() }
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
