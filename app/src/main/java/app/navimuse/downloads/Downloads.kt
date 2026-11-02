package app.navimuse.downloads

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import app.navimuse.NaviMuseApp
import app.navimuse.data.Settings
import app.navimuse.net.Song
import app.navimuse.net.SubsonicClient
import app.navimuse.playback.Media3Db
import app.navimuse.playback.Streaming
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/**
 * Songs saved on the phone for offline play. Downloads live in the app's own storage and are
 * read by the player before it ever asks the server. Removing one only deletes the phone's copy.
 */
@OptIn(UnstableApi::class)
object Downloads {
    private const val KEY_PREFIX = "dl:"

    /** Cache key a downloaded song is stored under. */
    fun key(songId: String) = "$KEY_PREFIX$songId"
    fun isKey(cacheKey: String) = cacheKey.startsWith(KEY_PREFIX)

    enum class State { QUEUED, DOWNLOADING, DONE, FAILED }

    data class Item(val song: Song, val state: State, val percent: Float, val bytes: Long)

    private val _items = MutableStateFlow<Map<String, Item>>(emptyMap())
    /** Every download by song id, in the order they were added. */
    val items: StateFlow<Map<String, Item>> = _items.asStateFlow()

    fun isDownloaded(songId: String): Boolean = _items.value[songId]?.state == State.DONE

    @Volatile private var cache: SimpleCache? = null

    fun cache(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.applicationContext.filesDir, "downloads"),
            NoOpCacheEvictor(),
            Media3Db.get(context),
        ).also { cache = it }
    }

    @Volatile private var manager: DownloadManager? = null

    /** The download manager. Call on the main thread; it reports back on it. */
    fun manager(context: Context): DownloadManager = manager ?: synchronized(this) {
        manager ?: create(context.applicationContext as NaviMuseApp).also { manager = it }
    }

    private fun create(app: NaviMuseApp): DownloadManager {
        val m = DownloadManager(app, Media3Db.get(app), cache(app), Streaming.http(app), Executors.newFixedThreadPool(3))
        m.maxParallelDownloads = 3
        m.requirements = requirementsFor(app.settings.value)
        m.addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) = reload(downloadManager)

            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                val item = itemOf(download) ?: return
                _items.update { it + (item.song.id to item) }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                _items.update { it - download.request.id }
            }
        })
        return m
    }

    private fun reload(m: DownloadManager) {
        val all = LinkedHashMap<String, Item>()
        runCatching {
            m.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) itemOf(cursor.download)?.let { all[it.song.id] = it }
            }
        }
        _items.value = all
    }

    /** Refreshes the percentages of songs that are downloading right now. */
    fun refreshProgress(context: Context) {
        val current = manager(context).currentDownloads
        if (current.isEmpty()) return
        _items.update { map ->
            val next = LinkedHashMap(map)
            current.forEach { d -> itemOf(d)?.let { next[it.song.id] = it } }
            next
        }
    }

    private fun itemOf(d: Download): Item? {
        val song = runCatching { songFrom(String(d.request.data, Charsets.UTF_8)) }.getOrNull() ?: return null
        val state = when (d.state) {
            Download.STATE_COMPLETED -> State.DONE
            Download.STATE_DOWNLOADING -> State.DOWNLOADING
            Download.STATE_FAILED -> State.FAILED
            else -> State.QUEUED
        }
        val percent = if (d.percentDownloaded >= 0f) d.percentDownloaded else 0f
        return Item(song, state, percent, d.bytesDownloaded)
    }

    private fun requirementsFor(s: Settings) =
        Requirements(if (s.downloadWifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

    fun onSettingsChanged(context: Context, s: Settings) {
        val m = manager(context)
        val wanted = requirementsFor(s)
        if (m.requirements != wanted) m.requirements = wanted
    }

    /**
     * Queues songs that aren't downloaded yet (failed ones are tried again). [kbpsFor] picks each
     * song's quality (0 = the original file). Returns how many.
     */
    fun add(context: Context, api: SubsonicClient, songs: List<Song>, kbpsFor: (Song) -> Int): Int {
        val m = manager(context)
        val have = _items.value
        val todo = songs.distinctBy { it.id }.filter { s -> have[s.id]?.state.let { it == null || it == State.FAILED } }
        if (todo.isEmpty()) return 0
        todo.forEach { s ->
            val request = DownloadRequest.Builder(s.id, Uri.parse(api.streamUrl(s.id, kbpsFor(s))))
                .setCustomCacheKey(key(s.id))
                .setData(songJson(s).toByteArray(Charsets.UTF_8))
                .build()
            m.addDownload(request)
            _items.update { it + (s.id to Item(s, State.QUEUED, 0f, 0)) }
        }
        m.resumeDownloads()
        // Keeps the downloads going (with a progress notification) if you leave the app.
        runCatching { DownloadService.start(context, DownloadsService::class.java) }
        return todo.size
    }

    /** Deletes the phone's copies of these songs. Nothing on the server changes. */
    fun remove(context: Context, songIds: Collection<String>) {
        val m = manager(context)
        songIds.forEach { m.removeDownload(it) }
        _items.update { it - songIds.toSet() }
    }

    fun removeAll(context: Context) {
        manager(context).removeAllDownloads()
        _items.value = emptyMap()
    }

    /** Space the downloads take on the phone. */
    fun bytesUsed(context: Context): Long = runCatching { cache(context).cacheSpace }.getOrDefault(0L)

    private fun songJson(s: Song): String = JSONObject()
        .put("id", s.id).put("title", s.title).put("artist", s.artist).put("artistId", s.artistId ?: "")
        .put("album", s.album).put("albumId", s.albumId ?: "").put("coverArt", s.coverArt ?: "")
        .put("duration", s.duration).put("track", s.track).put("disc", s.disc).put("year", s.year)
        .put("suffix", s.suffix ?: "").put("bitRate", s.bitRate).put("starred", s.starred).put("channels", s.channels)
        .put("samplingRate", s.samplingRate).put("bitDepth", s.bitDepth).put("size", s.size)
        .toString()

    private fun songFrom(json: String): Song? {
        val o = JSONObject(json)
        val id = o.optString("id").ifEmpty { return null }
        return Song(
            id = id,
            title = o.optString("title"),
            artist = o.optString("artist"),
            artistId = o.optString("artistId").ifEmpty { null },
            album = o.optString("album"),
            albumId = o.optString("albumId").ifEmpty { null },
            coverArt = o.optString("coverArt").ifEmpty { null },
            duration = o.optInt("duration"),
            track = o.optInt("track"),
            disc = o.optInt("disc"),
            year = o.optInt("year"),
            suffix = o.optString("suffix").ifEmpty { null },
            bitRate = o.optInt("bitRate"),
            starred = o.optBoolean("starred"),
            channels = o.optInt("channels"),
            samplingRate = o.optInt("samplingRate"),
            bitDepth = o.optInt("bitDepth"),
            size = o.optLong("size"),
        )
    }
}
