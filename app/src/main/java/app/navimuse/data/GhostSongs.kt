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
 * Ghost songs: songs Navidrome still lists but can't play, because their files were moved,
 * renamed or deleted since its last scan (or it isn't allowed to read them). They're found when
 * one fails to play, or by looking through the library, and kept here: left out of mixes, radio
 * and queues, marked in lists, and checked again now and then in case the file came back.
 * Delete on the Ghost songs page asks Navidrome for a quick scan, which drops the ones whose files
 * are gone (a file that's still there, but broken, moves to the Trash on the NAS).
 */
object GhostSongs {
    data class Ghost(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        /** Why it won't play, in a sentence. */
        val reason: String,
        val foundAt: Long,
        /**
         * You deleted it: off the Ghost songs list, still kept out of mixes and queues until
         * Navidrome stops listing it (then it's forgotten).
         */
        val removed: Boolean = false,
    )

    private var prefs: SharedPreferences? = null
    private val _all = MutableStateFlow<Map<String, Ghost>>(emptyMap())
    val all: StateFlow<Map<String, Ghost>> = _all.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("navimuse_ghosts", Context.MODE_PRIVATE)
        prefs = p
        _all.value = fromJson(p.getString("ghosts", null))
    }

    fun isGhost(id: String): Boolean = id in _all.value

    fun isGhost(song: Song): Boolean = isGhost(song.id)

    /** [songs] without the ghosts. */
    fun playable(songs: List<Song>): List<Song> = if (_all.value.isEmpty()) songs else songs.filterNot(::isGhost)

    fun mark(song: Song, reason: String) = mark(song.id, song.title, song.artist, song.album, reason)

    fun mark(id: String, title: String, artist: String, album: String, reason: String) = change {
        it + (id to Ghost(id, title, artist, album, reason, System.currentTimeMillis(), removed = it[id]?.removed ?: false))
    }

    /**
     * It plays after all (the file came back, or Navidrome was rescanned). A song waiting to move
     * to the Trash ([TrashQueue]) stays hidden: its file is still there, so it would play.
     */
    fun clear(id: String) {
        if (id !in _all.value || TrashQueue.has(id)) return
        change { it - id }
    }

    /** [clear] for many at once (one write, for long lists). */
    fun clear(ids: Collection<String>) {
        val drop = ids.filter { it in _all.value && !TrashQueue.has(it) }.toSet()
        if (drop.isNotEmpty()) change { it - drop }
    }

    /** [mark] for many songs at once (one write, for long lists). */
    fun markAll(found: List<Pair<Song, String>>) {
        if (found.isEmpty()) return
        val now = System.currentTimeMillis()
        change { m ->
            m + found.map { (s, reason) -> s.id to Ghost(s.id, s.title, s.artist, s.album, reason, now, removed = m[s.id]?.removed ?: false) }
        }
    }

    /** [mark] for many at once, keeping what's known of each. [removed]: see [Ghost.removed]. */
    fun relabel(ids: Collection<String>, reason: String, removed: Boolean? = null) {
        val set = ids.toSet()
        change { m ->
            m.mapValues { (id, g) -> if (id in set) g.copy(reason = reason, removed = removed ?: g.removed) else g }
        }
    }

    /** The ones on the Ghost songs list: not deleted, and not waiting to move to the Trash. */
    fun listed(all: Map<String, Ghost>, waiting: Collection<String>): Map<String, Ghost> =
        all.filter { (id, g) -> !g.removed && id !in waiting }

    fun clearAll() = change { emptyMap() }

    private fun change(f: (Map<String, Ghost>) -> Map<String, Ghost>) {
        _all.update(f)
        prefs?.edit()?.putString("ghosts", toJson(_all.value))?.apply()
    }

    fun toJson(ghosts: Map<String, Ghost>): String = JSONArray().apply {
        ghosts.values.forEach { g ->
            put(
                JSONObject().put("id", g.id).put("title", g.title).put("artist", g.artist).put("album", g.album)
                    .put("reason", g.reason).put("at", g.foundAt).put("removed", g.removed),
            )
        }
    }.toString()

    fun fromJson(json: String?): Map<String, Ghost> {
        val a = json?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyMap()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Ghost(
                id, o.optString("title"), o.optString("artist"), o.optString("album"), o.optString("reason"), o.optLong("at"),
                removed = o.optBoolean("removed", false),
            )
        }.associateBy { it.id }
    }
}
