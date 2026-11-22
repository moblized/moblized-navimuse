package app.navimuse.data

import app.navimuse.net.Song

/**
 * Finding the same song more than once in the library. Songs count as the same when their title
 * and artist match once made plain (see SongMatch: no "(feat. …)", "(2021 Version)", accents or
 * punctuation). Each group says what kind of duplicate it looks like and which copy to keep.
 */
object Duplicates {
    enum class Kind(val label: String, val explain: String) {
        GHOST_COPY(
            "Old entry and new copy",
            "One of these won't play: Navidrome still lists it, but can't open its file (moved, renamed or gone since its last scan). Delete it to clear it out.",
        ),
        TWO_COPIES(
            "Two copies of the same song",
            "The same recording, more than once. Keep the best copy and move the others to the Trash.",
        ),
        SINGLE_AND_ALBUM(
            "Single and album version",
            "The same recording on a single and on an album. Usually the album copy is the one to keep.",
        ),
        MAYBE_VERSIONS(
            "Maybe different versions",
            "Same title and artist but different lengths: could be a live take, a remaster or a re-recording. Keep both unless you know better.",
        ),
    }

    data class Group(val key: String, val kind: Kind, val songs: List<Song>, val keep: String?)

    /** Two copies within this many seconds of each other count as the same recording. */
    private const val SAME_LENGTH_SECONDS = 3

    private val lossless = setOf("flac", "alac", "wav", "aiff", "aif", "ape", "wv", "dsf", "dff")

    /** The plain title and first artist that two copies of a song share. */
    fun key(song: Song): String {
        val artist = song.artist.split(Regex("\\s*(?:;|,|&| feat\\.? | ft\\.? | x )\\s*", RegexOption.IGNORE_CASE)).first()
        return SongMatch.plain(song.title) + "|" + SongMatch.plain(artist)
    }

    /** On a single, not an album: the album is named after the song, or says it's a single. */
    fun onSingle(song: Song): Boolean {
        val album = SongMatch.plain(song.album)
        return album.isNotEmpty() && (album == SongMatch.plain(song.title) || Regex("\\bsingle\\b").containsMatchIn(album))
    }

    /** Which copy sounds best: lossless first, then the higher bit rate, then an album over a single. */
    fun quality(song: Song): Int =
        (if (song.suffix?.lowercase() in lossless) 100_000 else 0) + song.bitRate + (if (onSingle(song)) 0 else 1)

    /**
     * Groups of the same song in [songs], most certain first. Groups whose key is in [dismissed]
     * (marked "Not duplicates") are left out, and so are groups made only of ghosts.
     */
    fun find(songs: List<Song>, isGhost: (Song) -> Boolean, dismissed: Set<String> = emptySet()): List<Group> =
        songs.distinctBy { it.id }
            .groupBy(::key)
            .filter { (k, g) -> g.size > 1 && k !in dismissed && !k.startsWith("|") && !k.endsWith("|") }
            .mapNotNull { (k, g) -> classify(k, g, isGhost) }
            .sortedWith(compareBy({ it.kind.ordinal }, { it.songs.first().artist.lowercase() }, { it.songs.first().title.lowercase() }))

    private fun classify(key: String, group: List<Song>, isGhost: (Song) -> Boolean): Group? {
        val playable = group.filterNot(isGhost)
        if (playable.isEmpty()) return null
        val best = playable.maxBy(::quality)
        val ordered = listOf(best) + (group - best).sortedByDescending(::quality)
        if (playable.size < group.size) return Group(key, Kind.GHOST_COPY, ordered, best.id)
        val lengths = group.map { it.duration }.filter { it > 0 }
        val sameLength = lengths.size < 2 || lengths.max() - lengths.min() <= SAME_LENGTH_SECONDS
        if (!sameLength) return Group(key, Kind.MAYBE_VERSIONS, ordered, null)
        val singles = group.count(::onSingle)
        if (singles in 1 until group.size && group.map { it.albumId ?: it.album }.distinct().size > 1) {
            val keep = group.filterNot(::onSingle).maxBy(::quality)
            return Group(key, Kind.SINGLE_AND_ALBUM, listOf(keep) + (group - keep).sortedByDescending(::quality), keep.id)
        }
        return Group(key, Kind.TWO_COPIES, ordered, best.id)
    }
}
