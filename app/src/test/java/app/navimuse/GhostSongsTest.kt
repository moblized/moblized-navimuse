package app.navimuse

import app.navimuse.data.Blocklist
import app.navimuse.data.GhostSongs
import app.navimuse.net.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GhostSongsTest {
    private fun song(id: String, title: String = "Song $id") =
        Song(id, title, "Saliva", "ar", "Every Twenty Years", "al", null, 240, 1, 1, 2021, "flac", 900, false)

    @Before
    fun reset() = GhostSongs.clearAll()

    @Test
    fun ghostsAreLeftOutOfMixesAndQueues() {
        val gone = song("a", "Your Disease (2021 Version)")
        val fine = song("b")
        GhostSongs.mark(gone, "Navidrome can't find its file")
        assertTrue(GhostSongs.isGhost(gone))
        assertEquals(listOf(fine), GhostSongs.playable(listOf(gone, fine)))
        // Mixes, radio and autoplay go through the blocklist's filter, which drops ghosts too.
        assertEquals(listOf(fine), Blocklist.allowed(listOf(gone, fine)))
    }

    @Test
    fun aSongThatPlaysAgainIsNoGhost() {
        val gone = song("a")
        GhostSongs.mark(gone, "Navidrome can't find its file")
        GhostSongs.clear("a")
        assertFalse(GhostSongs.isGhost(gone))
        assertEquals(listOf(gone), GhostSongs.playable(listOf(gone)))
    }

    @Test
    fun keptBetweenRuns() {
        GhostSongs.mark(song("a", "Your Disease"), "Navidrome can't find its file")
        GhostSongs.mark(song("b", "Lucy"), "Navidrome sent an empty file for it")
        val all = GhostSongs.all.value
        assertEquals(all, GhostSongs.fromJson(GhostSongs.toJson(all)))
        assertEquals(emptyMap<String, GhostSongs.Ghost>(), GhostSongs.fromJson("not json"))
    }
}
