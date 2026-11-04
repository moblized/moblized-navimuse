package app.navimuse.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import app.navimuse.BuildConfig
import app.navimuse.NaviMuseApp
import app.navimuse.downloads.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * How audio reaches the player, fastest source first:
 *  1. songs downloaded for offline play,
 *  2. the cache of recently streamed audio,
 *  3. the server, over the app's own OkHttp client so every song reuses one warm connection
 *     (no new TCP and TLS handshake per track).
 */
@OptIn(UnstableApi::class)
object Streaming {
    fun http(app: NaviMuseApp): DataSource.Factory =
        OkHttpDataSource.Factory(app.http).setUserAgent("NaviMuse/${BuildConfig.VERSION_NAME}")

    /** Reads the stream cache, filling it from the server. */
    fun streamCache(app: NaviMuseApp): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(AudioCache.get(app))
            .setUpstreamDataSourceFactory(http(app))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Everything the player reads: downloads (read-only) on top of the stream cache. */
    fun playerSource(app: NaviMuseApp): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(Downloads.cache(app))
            .setUpstreamDataSourceFactory(streamCache(app))
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Start playing after one second of audio instead of the default 2.5, and keep up to
     * three minutes read ahead so a weak signal or a busy VPN doesn't cause stalls.
     */
    fun loadControl(): LoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 180_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500,
            )
            .setTargetBufferBytes(48 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

    fun unmetered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

/**
 * Keeps songs in the stream cache ahead of time, so they start instantly and play without a
 * connection. With auto caching on (Settings → Downloads): the next songs in the queue in full,
 * then the rest of the song that's playing, on Wi-Fi (or mobile data too, if allowed). Otherwise
 * just the start of the next song, so skipping is quick. Downloaded songs are left alone.
 */
@OptIn(UnstableApi::class)
class AutoCacher(
    private val player: Player,
    private val scope: CoroutineScope,
    private val app: NaviMuseApp,
) : Player.Listener {
    private data class Target(val key: String, val uri: Uri, val bytes: Long)

    private var job: Job? = null
    @Volatile private var writer: CacheWriter? = null
    private var plan: List<String> = emptyList()

    fun start() = player.addListener(this)

    fun stop() {
        player.removeListener(this)
        cancel()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = schedule()

    override fun onTimelineChanged(timeline: Timeline, reason: Int) = schedule()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = schedule()

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) schedule()
    }

    private fun cancel() {
        writer?.cancel()
        writer = null
        job?.cancel()
        job = null
        plan = emptyList()
    }

    /** The next [count] songs in the order they'll play (shuffle and repeat-all included). */
    private fun upcoming(count: Int): List<Int> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty || count <= 0) return emptyList()
        val repeat = if (player.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        val out = ArrayList<Int>(count)
        var i = player.currentMediaItemIndex
        while (out.size < count) {
            i = timeline.getNextWindowIndex(i, repeat, player.shuffleModeEnabled)
            if (i == C.INDEX_UNSET || i == player.currentMediaItemIndex || i in out) break
            out += i
        }
        return out
    }

    private fun targets(): List<Target> {
        val settings = app.settings.value
        val unmetered = Streaming.unmetered(app)
        val list = ArrayList<Target>()
        fun add(index: Int, bytes: Long) {
            if (index == C.INDEX_UNSET || index >= player.mediaItemCount) return
            val config = player.getMediaItemAt(index).localConfiguration ?: return
            val key = config.customCacheKey ?: return
            if (Downloads.isKey(key) || list.any { it.key == key }) return
            list += Target(key, config.uri, bytes)
        }
        if (settings.autoCache && (unmetered || settings.cacheAheadMobile)) {
            upcoming(settings.cacheAhead).forEach { add(it, C.LENGTH_UNSET.toLong()) }
            if (settings.cacheWholeSong) add(player.currentMediaItemIndex, C.LENGTH_UNSET.toLong())
        } else {
            // Just the start of the next song: a few seconds on mobile data, most of it on Wi-Fi.
            add(player.nextMediaItemIndex, if (unmetered) 16L * 1024 * 1024 else 3L * 1024 * 1024)
        }
        return list
    }

    private fun schedule() {
        val targets = targets()
        val keys = targets.map { "${it.key}#${it.bytes}" }
        if (keys == plan) return
        cancel()
        plan = keys
        if (targets.isEmpty()) return
        job = scope.launch(Dispatchers.IO) {
            for (t in targets) {
                if (!isActive) break
                val spec = DataSpec.Builder().setUri(t.uri).setKey(t.key)
                    .apply { if (t.bytes > 0) setLength(t.bytes) }
                    .build()
                val w = CacheWriter(Streaming.streamCache(app).createDataSource(), spec, null, null)
                writer = w
                // Already-cached parts are skipped; a failed song just isn't cached.
                runCatching { w.cache() }
            }
            writer = null
        }
    }
}
