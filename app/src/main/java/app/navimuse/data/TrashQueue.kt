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
 * Songs you moved to the Trash while away from home, when the NAS couldn't be reached (its SMB
 * share only answers on your home network). Each waits here, hidden from your lists as a ghost
 * song, and its file moves to the Trash the next time the phone reaches the NAS: on home Wi-Fi,
 * even with the app closed, or when you open the app there. Nothing on the NAS changes until
 * then, and Cancel on the Trash page keeps the song.
 */
object TrashQueue {
    data class Entry(
        val songId: String,
        val title: String,
        val artist: String,
        val album: String,
        /** Navidrome's path for its file, if Navidrome said when it was queued. */
        val path: String?,
        val queuedAt: Long,
        /**
         * Why the last try didn't work, when the NAS answered but the move went wrong (say, the
         * file wasn't there). Not tried again by itself; opening the Trash tries again.
         */
        val problem: String? = null,
        /**
         * A ghost song deleted away from home: if its file turns out to be gone already, there's
         * nothing to move, and that's done too.
         */
        val ghost: Boolean = false,
    )

    private var prefs: SharedPreferences? = null
    private val _all = MutableStateFlow<List<Entry>>(emptyList())
    val all: StateFlow<List<Entry>> = _all.asStateFlow()

    private val _moved = MutableStateFlow(0)
    /** Goes up each time a waiting song moves to the Trash, so the Trash page can show it. */
    val moved: StateFlow<Int> = _moved.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("navimuse_trash_queue", Context.MODE_PRIVATE)
        prefs = p
        _all.value = fromJson(p.getString("queue", null))
    }

    fun has(songId: String): Boolean = _all.value.any { it.songId == songId }

    /** Songs that move by themselves when the NAS answers (not the ones that went wrong). */
    fun waiting(): List<Entry> = _all.value.filter { it.problem == null }

    fun add(entry: Entry) = change { list -> list.filterNot { it.songId == entry.songId } + entry }

    /** [add] for many at once (one write). */
    fun addAll(entries: List<Entry>) {
        val ids = entries.map { it.songId }.toSet()
        change { list -> list.filterNot { it.songId in ids } + entries }
    }

    fun remove(songId: String) = change { list -> list.filterNot { it.songId == songId } }

    /** Its file is in the Trash now. */
    fun done(songId: String) {
        remove(songId)
        _moved.update { it + 1 }
    }

    fun setProblem(songId: String, problem: String?) = change { list ->
        list.map { if (it.songId == songId) it.copy(problem = problem) else it }
    }

    fun clearAll() = change { emptyList() }

    private fun change(f: (List<Entry>) -> List<Entry>) {
        _all.update(f)
        prefs?.edit()?.putString("queue", toJson(_all.value))?.apply()
    }

    fun toJson(entries: List<Entry>): String = JSONArray().apply {
        entries.forEach { e ->
            put(
                JSONObject().put("id", e.songId).put("title", e.title).put("artist", e.artist).put("album", e.album)
                    .put("path", e.path ?: JSONObject.NULL).put("at", e.queuedAt).put("problem", e.problem ?: JSONObject.NULL).put("ghost", e.ghost),
            )
        }
    }.toString()

    fun fromJson(json: String?): List<Entry> {
        val a = json?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Entry(
                songId = id,
                title = o.optString("title"),
                artist = o.optString("artist"),
                album = o.optString("album"),
                path = o.optString("path").takeIf { !o.isNull("path") && it.isNotBlank() },
                queuedAt = o.optLong("at"),
                problem = o.optString("problem").takeIf { !o.isNull("problem") && it.isNotBlank() },
                ghost = o.optBoolean("ghost", false),
            )
        }.distinctBy { it.songId }
    }
}
