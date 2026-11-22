package app.navimuse

import app.navimuse.data.Duplicates
import app.navimuse.net.NavidromeNative
import app.navimuse.net.Song
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatesTest {
    private fun song(
        id: String,
        title: String,
        album: String,
        seconds: Int = 200,
        suffix: String = "mp3",
        kbps: Int = 320,
        artist: String = "Skillet",
    ) = Song(id, title, artist, "ar", album, "al-$album", null, seconds, 1, 1, 2009, suffix, kbps, false)

    private val none: (Song) -> Boolean = { false }

    @Test
    fun twoCopiesKeepTheBestOne() {
        val mp3 = song("a", "Hero", "Awake", suffix = "mp3", kbps = 320)
        val flac = song("b", "Hero", "Awake (Deluxe Edition)", seconds = 201, suffix = "flac", kbps = 900)
        val other = song("c", "Monster", "Awake")
        val groups = Duplicates.find(listOf(mp3, flac, other), none)
        assertEquals(1, groups.size)
        assertEquals(Duplicates.Kind.TWO_COPIES, groups[0].kind)
        assertEquals("b", groups[0].keep)
        assertEquals(listOf("b", "a"), groups[0].songs.map { it.id })
    }

    @Test
    fun aGhostAndItsNewCopy() {
        val old = song("old", "Your Disease (2021 Version)", "Every Twenty Years", 240, artist = "Saliva")
        val new = song("new", "Your Disease (2021 Version)", "Every Twenty Years", 240, artist = "Saliva")
        val g = Duplicates.find(listOf(old, new), isGhost = { it.id == "old" }).single()
        assertEquals(Duplicates.Kind.GHOST_COPY, g.kind)
        assertEquals("new", g.keep)
        // Only ghosts: that's for the Ghost songs page, not a duplicate.
        assertTrue(Duplicates.find(listOf(old, new), isGhost = { true }).isEmpty())
    }

    @Test
    fun singleAndAlbum() {
        val single = song("s", "Rise Up", "Rise Up", 238)
        val album = song("a", "Rise Up", "Victorious", 238, kbps = 256)
        val g = Duplicates.find(listOf(single, album), none).single()
        assertEquals(Duplicates.Kind.SINGLE_AND_ALBUM, g.kind)
        assertEquals("a", g.keep)
        assertTrue(Duplicates.onSingle(song("x", "Lucy", "Lucy - Single")))
    }

    @Test
    fun differentLengthsAreMaybeVersions() {
        val studio = song("a", "Your Disease", "Every Six Sins", 220, artist = "Saliva")
        val remake = song("b", "Your Disease (2021 Version)", "Every Twenty Years", 240, artist = "Saliva")
        val g = Duplicates.find(listOf(studio, remake), none).single()
        assertEquals(Duplicates.Kind.MAYBE_VERSIONS, g.kind)
        assertNull(g.keep)
    }

    @Test
    fun featuredArtistsAndDismissed() {
        val a = song("a", "Hero (feat. Someone)", "Awake", artist = "Skillet feat. Someone")
        val b = song("b", "Hero", "Awake (Deluxe)", artist = "Skillet")
        val groups = Duplicates.find(listOf(a, b), none)
        assertEquals(1, groups.size)
        assertTrue(Duplicates.find(listOf(a, b), none, dismissed = setOf(groups[0].key)).isEmpty())
        // The same title by different artists isn't a duplicate.
        assertTrue(Duplicates.find(listOf(song("x", "Hero", "A", artist = "Skillet"), song("y", "Hero", "B", artist = "Superchick")), none).isEmpty())
    }

    @Test
    fun whereNavidromeSaysTheFileIs() {
        val newer = NavidromeNative.parseSongFile(JSONObject("""{"id":"1","path":"Saliva/Every Twenty Years/03.flac","libraryPath":"/music"}"""))
        assertEquals("Saliva/Every Twenty Years/03.flac", newer.path)
        val older = NavidromeNative.parseSongFile(JSONObject("""{"id":"1","path":"/music/Saliva/03.flac","libraryPath":"/music"}"""))
        assertEquals("Saliva/03.flac", older.path)
        val bare = NavidromeNative.parseSongFile(JSONObject("""{"id":"1","path":"/music/Saliva/03.flac"}"""))
        assertEquals("music/Saliva/03.flac", bare.path)
    }
}
