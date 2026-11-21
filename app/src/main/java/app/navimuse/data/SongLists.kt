package app.navimuse.data

import app.navimuse.net.Song
import java.text.Normalizer

/** A song to find in your library: what a TIDAL playlist, a file or a pasted list says it is. */
data class WantedSong(val title: String, val artists: List<String> = emptyList(), val durationSec: Int = 0) {
    /** "Title – Artist", for the list of songs that weren't found. */
    val label: String get() = if (artists.isEmpty()) title else "$title – ${artists.joinToString(", ")}"
}

/**
 * Song lists from elsewhere, read into [WantedSong]s: a CSV exported from TIDAL by a transfer
 * service (TuneMyMusic, Soundiiz…: columns like "Track name", "Artist name"), or lines pasted
 * in as "Artist - Title" (or "Title by Artist").
 */
object SongLists {
    private val titleColumns = listOf("track name", "track title", "title", "track", "song", "song name", "name")
    private val artistColumns = listOf("artist name", "artist name(s)", "artist names", "artists", "artist", "album artist")
    private val durationColumns = listOf("duration", "duration (ms)", "length", "time")

    fun parse(text: String): List<WantedSong> {
        val lines = text.lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        csv(lines)?.let { return it }
        return lines.mapNotNull(::line)
    }

    /** A CSV with a header naming a title column (and usually an artist column); null if it isn't one. */
    private fun csv(lines: List<String>): List<WantedSong>? {
        val sep = listOf(',', ';', '\t').maxByOrNull { c -> lines.first().count { it == c } } ?: return null
        val header = cells(lines.first(), sep).map { it.trim().lowercase() }
        if (header.size < 2) return null
        val t = titleColumns.firstNotNullOfOrNull { c -> header.indexOf(c).takeIf { it >= 0 } } ?: return null
        val a = artistColumns.firstNotNullOfOrNull { c -> header.indexOf(c).takeIf { it >= 0 } }
        val d = durationColumns.firstNotNullOfOrNull { c -> header.indexOf(c).takeIf { it >= 0 } }
        val msDuration = d != null && header[d].contains("ms")
        return lines.drop(1).mapNotNull { row ->
            val cells = cells(row, sep)
            val title = cells.getOrNull(t)?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            val artists = a?.let { cells.getOrNull(it) }?.let(::splitArtists).orEmpty()
            val duration = d?.let { cells.getOrNull(it) }?.let { seconds(it, msDuration) } ?: 0
            WantedSong(title, artists, duration)
        }
    }

    /** One pasted line: "Artist - Title", "Title by Artist", or just a title. */
    private fun line(raw: String): WantedSong? {
        val s = raw.trim().removePrefix("﻿").replace(Regex("^\\d+[.)]\\s+"), "")
        if (s.isBlank() || s.startsWith("#")) return null
        Regex("\\s+[-–—]\\s+").find(s)?.let { m ->
            val artist = s.substring(0, m.range.first).trim()
            val title = s.substring(m.range.last + 1).trim()
            if (artist.isNotBlank() && title.isNotBlank()) return WantedSong(title, splitArtists(artist))
        }
        Regex("\\s+by\\s+", RegexOption.IGNORE_CASE).findAll(s).lastOrNull()?.let { m ->
            val title = s.substring(0, m.range.first).trim()
            val artist = s.substring(m.range.last + 1).trim()
            if (artist.isNotBlank() && title.isNotBlank()) return WantedSong(title, splitArtists(artist))
        }
        return WantedSong(s)
    }

    /** One CSV row's cells, quotes understood. */
    fun cells(row: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < row.length) {
            val c = row[i]
            when {
                quoted && c == '"' && row.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == sep -> { out += cell.toString(); cell.clear() }
                else -> cell.append(c)
            }
            i++
        }
        out += cell.toString()
        return out
    }

    private fun splitArtists(s: String): List<String> =
        s.split(Regex("\\s*(?:,|;|&| feat\\.? | ft\\.? | x | and )\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim() }.filter { it.isNotBlank() }
            .let { parts -> if (parts.size > 1) listOf(s.trim()) + parts else parts }

    private fun seconds(s: String, ms: Boolean): Int {
        val v = s.trim()
        Regex("^(\\d+):(\\d{2})(?::(\\d{2}))?$").find(v)?.let { m ->
            val (a, b, c) = m.destructured
            return if (c.isEmpty()) a.toInt() * 60 + b.toInt() else a.toInt() * 3600 + b.toInt() * 60 + c.toInt()
        }
        val n = v.toDoubleOrNull() ?: return 0
        return if (ms || n > 10_000) (n / 1000).toInt() else n.toInt()
    }
}

/** Finding the library's copy of a [WantedSong] among search results. */
object SongMatch {
    /** Plain form for comparing: no accents, case, brackets ("(feat. …)", "[Remastered]") or punctuation. */
    fun plain(s: String): String {
        val noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return noAccents.lowercase()
            .replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]"), " ")
            .replace(Regex("\\s+-\\s+.*(remaster|live|version|edit|mix|mono|stereo|deluxe|bonus|acoustic|demo).*$"), " ")
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    /** Search words for [w]: its plain title and first artist. */
    fun query(w: WantedSong): String = (plain(w.title) + " " + w.artists.firstOrNull()?.let(::plain).orEmpty()).trim()

    /**
     * How well [song] fits [w]: 0 means no. The titles must match (once plain); a matching artist
     * or a length within a few seconds makes it sure. With no artist known, the title alone does.
     */
    fun score(w: WantedSong, song: Song): Int {
        val wt = plain(w.title)
        val st = plain(song.title)
        val title = when {
            wt.isEmpty() || st.isEmpty() -> return 0
            wt == st -> 3
            (wt.length >= 4 && st.startsWith(wt)) || (st.length >= 4 && wt.startsWith(st)) -> 2
            else -> return 0
        }
        val sa = plain(song.artist)
        val artist = when {
            w.artists.isEmpty() -> 0
            w.artists.any { plain(it).let { a -> a.isNotEmpty() && (a == sa || sa.contains(a) || a.contains(sa)) } } -> 3
            else -> -1
        }
        val length = if (w.durationSec > 0 && song.duration > 0) {
            if (kotlin.math.abs(w.durationSec - song.duration) <= 5) 1 else -1
        } else {
            0
        }
        // A known artist that doesn't match is a different song, unless everything else is exact.
        if (artist < 0 && !(title == 3 && length > 0)) return 0
        return title + artist + length
    }

    /** The best fit for [w] among [candidates], or null when none fits. */
    fun best(w: WantedSong, candidates: List<Song>): Song? =
        candidates.map { it to score(w, it) }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
}
