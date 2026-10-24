package app.navimuse.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import app.navimuse.BuildConfig
import app.navimuse.MainActivity
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
    private lateinit var player: ExoPlayer
    private lateinit var memory: QueueMemory

    private val app: NaviMuseApp get() = application as NaviMuseApp

    override fun onCreate() {
        super.onCreate()
        memory = QueueMemory(this)

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("NaviMuse/${BuildConfig.VERSION_NAME}")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val cached = CacheDataSource.Factory()
            .setCache(AudioCache.get(this))
            .setUpstreamDataSourceFactory(http)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cached))
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
            .build()

        scrobbler = Scrobbler(player, scope, app).also { it.start() }
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
        val bitrate = app.currentMaxBitRate()
        player.setMediaItems(saved.songs.map { it.toMediaItem(api, bitrate) }, saved.index, saved.positionMs)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away stops the service unless music is playing.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            memory.save(player)
            stopSelf()
        }
    }

    override fun onDestroy() {
        memory.save(player)
        scrobbler?.stop()
        session?.release()
        session = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    // --- resolving what to play -------------------------------------------------------------

    private fun requireApi(): SubsonicClient =
        app.api() ?: throw IllegalStateException("Not logged in to Navidrome")

    private fun Song.item(context: String? = null): MediaItem =
        toMediaItem(requireApi(), app.currentMaxBitRate(), context)

    /** Gives each item a fresh stream link (and its details, if they're missing). */
    private suspend fun resolve(items: List<MediaItem>): MutableList<MediaItem> {
        val api = requireApi()
        val bitrate = app.currentMaxBitRate()
        val out = ArrayList<MediaItem>(items.size)
        for (item in items) {
            val songId = MediaIds.songIdOf(item.mediaId)
            if (songId == null) {
                // A voice request like "play Daft Punk on NaviMuse".
                val query = item.requestMetadata.searchQuery
                if (!query.isNullOrBlank()) {
                    out += api.search(query, songs = 50, albums = 0, artists = 0).songs.map { it.toMediaItem(api, bitrate) }
                }
                continue
            }
            if (item.mediaMetadata.title != null) {
                out += item.buildUpon()
                    .setUri(api.streamUrl(songId, bitrate))
                    .setCustomCacheKey("$songId@$bitrate")
                    .build()
            } else {
                api.song(songId)?.let { out += it.toMediaItem(api, bitrate, MediaIds.contextOf(item.mediaId)) }
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
            mediaId == MediaIds.SHUFFLE_ALL -> return api.randomSongs(100).map { it.item() } to 0
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
        val similar = runCatching { requireApi().similarSongs(seed.id, size) }.getOrDefault(emptyList())
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
            val bitrate = app.currentMaxBitRate()
            MediaSession.MediaItemsWithStartPosition(
                saved.songs.map { it.toMediaItem(api, bitrate) },
                saved.index,
                saved.positionMs,
            )
        }
    }
}
