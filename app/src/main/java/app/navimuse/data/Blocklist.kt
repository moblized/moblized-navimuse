package app.navimuse.data

import android.content.Context
import android.content.SharedPreferences
import app.navimuse.net.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/**
 * Songs and artists you've blocked, kept on this phone only (nothing changes on the server).
 * Blocked music is left out of mixes, radio and shuffle, and skipped when it comes up next in
 * the queue. Picking a blocked song yourself still plays it.
 */
object Blocklist {
    /** [name] and [detail] are for showing the list ("White Flag", "Fall Of Envy"). */
    data class Entry(val id: String, val name: String, val detail: String = "")

    data class Lists(val songs: Map<String, Entry> = emptyMap(), val artists: Map<String, Entry> = emptyMap()) {
        fun isBlocked(song: Song): Boolean =
            song.id in songs || Blocklist.artistKey(song) in artists

        fun toJson(): String = JSONObject()
            .put("songs", JSONArray(songs.values.map { it.json() }))
            .put("artists", JSONArray(artists.values.map { it.json() }))
            .toString()

        private fun Entry.json() = JSONObject().put("id", id).put("name", name).put("detail", detail)

        companion object {
            fun fromJson(json: String?): Lists {
                val o = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return Lists()
                fun read(key: String): Map<String, Entry> {
                    val a = o.optJSONArray(key) ?: return emptyMap()
                    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                        .mapNotNull { e -> e.optString("id").takeIf { it.isNotBlank() }?.let { Entry(it, e.optString("name"), e.optString("detail")) } }
                        .associateBy { it.id }
                }
                return Lists(read("songs"), read("artists"))
            }
        }
    }

    /** An artist is blocked by id, or by name when the server gives no id. */
    fun artistKey(song: Song): String = song.artistId ?: "name:${song.artist.lowercase()}"

    private var prefs: SharedPreferences? = null
    private val _lists = MutableStateFlow(Lists())
    val lists: StateFlow<Lists> = _lists.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("navimuse_blocked", Context.MODE_PRIVATE)
        prefs = p
        _lists.value = Lists.fromJson(p.getString("lists", null))
    }

    fun isBlocked(song: Song): Boolean = _lists.value.isBlocked(song)

    /** [songs] without anything blocked, for mixes, radio and shuffle. */
    fun allowed(songs: List<Song>): List<Song> = songs.filterNot(::isBlocked)

    fun blockSong(song: Song) = change { it.copy(songs = it.songs + (song.id to Entry(song.id, song.title, song.artist))) }

    fun blockArtist(song: Song) {
        val key = artistKey(song)
        change { it.copy(artists = it.artists + (key to Entry(key, song.artist))) }
    }

    fun unblockSong(id: String) = change { it.copy(songs = it.songs - id) }

    fun unblockArtist(id: String) = change { it.copy(artists = it.artists - id) }

    private fun change(f: (Lists) -> Lists) {
        _lists.update(f)
        prefs?.edit()?.putString("lists", _lists.value.toJson())?.apply()
    }
}
