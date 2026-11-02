package app.navimuse.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.navimuse.NaviMuseApp
import app.navimuse.net.Song
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** The one database Media3 keeps its cache and download indexes in. */
@OptIn(UnstableApi::class)
object Media3Db {
    @Volatile private var db: StandaloneDatabaseProvider? = null

    fun get(context: Context): StandaloneDatabaseProvider = db ?: synchronized(this) {
        db ?: StandaloneDatabaseProvider(context.applicationContext).also { db = it }
    }
}

/**
 * A cache of recently streamed audio, on the phone only, so replays and seeking are smooth.
 * It's the app's own temporary copy; nothing on the server is touched.
 */
@OptIn(UnstableApi::class)
object AudioCache {
    @Volatile private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, "audio"),
            LeastRecentlyUsedCacheEvictor(1024L * 1024 * 1024),
            Media3Db.get(context),
        ).also { cache = it }
    }
}

/**
 * Tells Navidrome what's playing ("now playing"), and records a play once a song has played
 * for half its length or four minutes. AudioMuse-AI's taste mixes are built from these plays.
 */
class Scrobbler(
    private val player: Player,
    private val scope: CoroutineScope,
    private val app: NaviMuseApp,
) : Player.Listener {
    private var songId: String? = null
    private var playedMs = 0L
    private var submitted = false
    private var ticker: Job? = null

    fun start() {
        player.addListener(this)
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                tick()
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        player.removeListener(this)
    }

    private fun tick() {
        if (!player.isPlaying) return
        playedMs += 1000
        val id = songId ?: return
        val duration = player.duration
        val threshold = if (duration > 0) minOf(duration / 2, 240_000L) else 240_000L
        if (!submitted && playedMs >= threshold) {
            submitted = true
            send(id, submission = true)
        }
    }

    /** Whether "now playing" was already reported for the current song. */
    private var announced = false

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        songId = mediaItem?.let { MediaIds.songIdOf(it.mediaId) }
        playedMs = 0
        submitted = false
        announced = false
        if (player.isPlaying) announce()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) announce()
    }

    private fun announce() {
        if (announced) return
        val id = songId ?: player.currentMediaItem?.let { MediaIds.songIdOf(it.mediaId) }?.also { songId = it } ?: return
        announced = true
        send(id, submission = false)
    }

    private fun send(id: String, submission: Boolean) {
        if (!app.settings.value.scrobble) return
        val api = app.api() ?: return
        scope.launch { runCatching { api.scrobble(id, submission) } }
    }
}

/**
 * Remembers the queue on the phone, so it's still there after the app is closed and can be
 * resumed from headphones or the car. Only song details are saved, never audio.
 */
class QueueMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("navimuse_queue", Context.MODE_PRIVATE)

    data class Saved(val songs: List<Song>, val index: Int, val positionMs: Long)

    fun save(player: Player) {
        val count = player.mediaItemCount
        if (count == 0) {
            prefs.edit().clear().apply()
            return
        }
        // Keep a window of at most 300 songs around the current one.
        val current = player.currentMediaItemIndex.coerceAtLeast(0)
        val from = (current - 100).coerceAtLeast(0)
        val to = minOf(count, from + 300)
        val arr = JSONArray()
        for (i in from until to) {
            val s = player.getMediaItemAt(i).toSong() ?: continue
            arr.put(
                JSONObject()
                    .put("id", s.id).put("title", s.title).put("artist", s.artist)
                    .put("artistId", s.artistId ?: "").put("album", s.album).put("albumId", s.albumId ?: "")
                    .put("coverArt", s.coverArt ?: "").put("duration", s.duration).put("starred", s.starred)
                    .put("suffix", s.suffix ?: "").put("channels", s.channels).put("bitRate", s.bitRate),
            )
        }
        prefs.edit()
            .putString("queue", arr.toString())
            .putInt("index", current - from)
            .putLong("position", player.currentPosition.coerceAtLeast(0))
            .apply()
    }

    fun load(): Saved? {
        val raw = prefs.getString("queue", null) ?: return null
        val songs = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Song(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    artistId = o.optString("artistId").ifEmpty { null },
                    album = o.optString("album"),
                    albumId = o.optString("albumId").ifEmpty { null },
                    coverArt = o.optString("coverArt").ifEmpty { null },
                    duration = o.optInt("duration"),
                    track = 0, disc = 0, year = 0,
                    suffix = o.optString("suffix").ifEmpty { null },
                    bitRate = o.optInt("bitRate"),
                    starred = o.optBoolean("starred"),
                    channels = o.optInt("channels"),
                )
            }.filter { it.id.isNotEmpty() }
        }.getOrNull().orEmpty()
        if (songs.isEmpty()) return null
        return Saved(songs, prefs.getInt("index", 0).coerceIn(0, songs.lastIndex), prefs.getLong("position", 0))
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
