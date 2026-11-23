package app.navimuse.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/**
 * Playlists you deleted, kept on this phone for [KEEP_DAYS] days so you can restore them: the
 * name and the songs, in order. Restoring makes a new playlist in Navidrome with the same songs.
 * The songs themselves are never touched by deleting a playlist.
 */
object DeletedPlaylists {
    const val KEEP_DAYS = 30
    private const val DAY_MS = 24L * 60 * 60 * 1000

    data class Entry(
        /** Its id in Navidrome before it was deleted. */
        val playlistId: String,
        val name: String,
        val songIds: List<String>,
        val durationSeconds: Int,
        val deletedAt: Long,
    ) {
        val restoreUntil: Long get() = deletedAt + KEEP_DAYS * DAY_MS
    }

    private var prefs: SharedPreferences? = null
    private val _all = MutableStateFlow<List<Entry>>(emptyList())

    /** Newest first. */
    val all: StateFlow<List<Entry>> = _all.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("navimuse_deleted_playlists", Context.MODE_PRIVATE)
        prefs = p
        _all.value = fromJson(p.getString("deleted", null))
        prune()
    }

    fun add(entry: Entry) = change { list -> listOf(entry) + list.filterNot { it.playlistId == entry.playlistId } }

    fun remove(playlistId: String) = change { list -> list.filterNot { it.playlistId == playlistId } }

    /** Lets go of the ones older than [KEEP_DAYS] days. */
    fun prune(now: Long = System.currentTimeMillis()) {
        if (_all.value.any { it.restoreUntil < now }) change { list -> list.filter { it.restoreUntil >= now } }
    }

    fun clearAll() = change { emptyList() }

    private fun change(f: (List<Entry>) -> List<Entry>) {
        _all.update(f)
        prefs?.edit()?.putString("deleted", toJson(_all.value))?.apply()
    }

    fun toJson(entries: List<Entry>): String = JSONArray().apply {
        entries.forEach { e ->
            put(
                JSONObject().put("id", e.playlistId).put("name", e.name).put("songs", JSONArray(e.songIds))
                    .put("duration", e.durationSeconds).put("at", e.deletedAt),
            )
        }
    }.toString()

    fun fromJson(json: String?): List<Entry> {
        val a = json?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val songs = o.optJSONArray("songs")?.let { s -> (0 until s.length()).map { s.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
            Entry(id, o.optString("name"), songs, o.optInt("duration"), o.optLong("at"))
        }
    }
}
