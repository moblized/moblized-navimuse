package app.navimuse.playback

import android.content.Context
import androidx.media3.common.PlaybackException
import app.navimuse.net.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * When the phone can't play a song the way the server sent it, the player tries it another way
 * (the original file, MP3, a lower bitrate) and remembers what worked, per song. Once three songs
 * of one kind (say 24-bit 192 kHz FLAC) needed the same fix, every song of that kind gets it.
 *
 * Stream "bitrates" here: 0 = the original file, above 0 = converted by the server at that many
 * kbps (in its usual format), below 0 = converted to MP3 at that many kbps.
 */
object Convert {
    const val MP3 = -320
    private const val PREFS = "navimuse_convert"
    private const val KEY = "memory"

    /** The order to try a song in after something failed. */
    fun candidates(setting: Int): List<Int> = listOf(0, MP3, if (setting > 0) setting else 192, -128).distinct()

    private var prefs: android.content.SharedPreferences? = null
    private var memory = Memory()

    /** Ways already tried this session for songs that haven't played yet. */
    private val tried = HashMap<String, MutableSet<Int>>()
    private const val DOWNLOAD = Int.MIN_VALUE

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        memory = Memory.fromJson(p.getString(KEY, null))
    }

    @Synchronized
    fun preferred(song: Song): Int? = memory.preferred(song)

    @Synchronized
    fun downloadFailed(songId: String): Boolean = songId in memory.badDownloads

    /** Errors that mean the phone couldn't read or decode what it was sent. */
    fun isFormatError(code: Int): Boolean = code in setOf(
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
    )

    /**
     * The next way to try [songId] after [failed] didn't play (null: its downloaded copy), or null
     * when every way has been tried.
     */
    @Synchronized
    fun nextTry(songId: String, failed: Int?, setting: Int): Int? {
        val done = tried.getOrPut(songId) { mutableSetOf() }
        done += failed ?: DOWNLOAD
        if (failed == null) {
            memory = memory.copy(badDownloads = memory.badDownloads + songId)
            save()
        }
        val next = candidates(setting).firstOrNull { it !in done }
        if (next == null) tried.remove(songId) // start over next time it's played
        return next
    }

    /** [song] played fine as [kbps]: remember it, if it took a retry to get there. */
    @Synchronized
    fun worked(song: Song, kbps: Int) {
        if (tried.remove(song.id) == null) return
        memory = memory.remember(song, kbps)
        save()
    }

    /** Forgets every fix, e.g. after the server's conversion settings changed. */
    @Synchronized
    fun forgetAll() {
        memory = Memory()
        tried.clear()
        save()
    }

    @Synchronized
    fun count(): Int = memory.songs.size

    private fun save() {
        prefs?.edit()?.putString(KEY, memory.toJson())?.apply()
    }

    /** "the original FLAC", "MP3 at 320 kbps"… for messages. */
    fun describe(kbps: Int?, suffix: String?): String = when {
        kbps == null -> "your downloaded copy"
        kbps == 0 -> "the original " + (suffix?.uppercase()?.takeIf { it.isNotBlank() } ?: "file")
        kbps < 0 -> "MP3 at ${-kbps} kbps"
        else -> "converted at $kbps kbps"
    }

    /** What's remembered, kept apart from Android so it can be tested. */
    data class Memory(
        /** Song id → the way it plays. */
        val songs: Map<String, Int> = emptyMap(),
        /** Song id → its kind, for counting songs of a kind. */
        val kindOf: Map<String, String> = emptyMap(),
        /** Kind of file → the way songs of that kind play. */
        val kinds: Map<String, Int> = emptyMap(),
        /** Songs whose downloaded copy didn't play. */
        val badDownloads: Set<String> = emptySet(),
    ) {
        fun preferred(song: Song): Int? = songs[song.id] ?: kind(song)?.let { kinds[it] }

        fun remember(song: Song, kbps: Int): Memory {
            // Newest last, and at most MAX songs.
            val s = (songs - song.id + (song.id to kbps)).entries.toList().takeLast(MAX).associate { it.key to it.value }
            val k = kind(song)
            val ko = (if (k != null) kindOf + (song.id to k) else kindOf).filterKeys { it in s }
            val same = if (k == null) 0 else s.count { (id, v) -> v == kbps && ko[id] == k }
            val kinds2 = if (k != null && same >= KIND_AFTER) kinds + (k to kbps) else kinds
            return Memory(s, ko, kinds2, badDownloads)
        }

        fun toJson(): String = JSONObject().apply {
            put("songs", JSONObject(songs.mapValues { it.value }))
            put("kindOf", JSONObject(kindOf))
            put("kinds", JSONObject(kinds.mapValues { it.value }))
            put("badDownloads", JSONArray(badDownloads.toList()))
        }.toString()

        companion object {
            const val MAX = 3000
            const val KIND_AFTER = 3

            /** A song's kind of file: type, sample rate, bit depth and channels. */
            fun kind(song: Song): String? {
                val suffix = song.suffix?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
                return "$suffix/${song.samplingRate}/${song.bitDepth}/${song.channels}"
            }

            fun fromJson(text: String?): Memory {
                if (text.isNullOrBlank()) return Memory()
                return runCatching {
                    val o = JSONObject(text)
                    fun ints(key: String): Map<String, Int> {
                        val m = o.optJSONObject(key) ?: return emptyMap()
                        return m.keys().asSequence().associateWith { m.getInt(it) }
                    }
                    fun strings(key: String): Map<String, String> {
                        val m = o.optJSONObject(key) ?: return emptyMap()
                        return m.keys().asSequence().associateWith { m.getString(it) }
                    }
                    val bad = o.optJSONArray("badDownloads")
                    Memory(
                        ints("songs"), strings("kindOf"), ints("kinds"),
                        (0 until (bad?.length() ?: 0)).map { bad!!.getString(it) }.toSet(),
                    )
                }.getOrDefault(Memory())
            }
        }
    }
}
