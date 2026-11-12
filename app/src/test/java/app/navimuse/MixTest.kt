package app.navimuse

import app.navimuse.net.Album
import app.navimuse.net.Song
import app.navimuse.ui.Mix
import app.navimuse.ui.MixSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixTest {
    private fun song(id: String, title: String = "White Flag") =
        Song(id, title, "Fall Of Envy", "ar-1", "Poetic Rage", "al-1", null, 200, 1, 1, 2009, "flac", 900, false)

    @Test
    fun aSongMixIsNamedAfterTheSong() {
        val mix = Mix(MixSeed.FromSong(song("s1")), song("s1"), listOf(song("s1"), song("s2", "Other")))
        assertEquals("Instant Mix: White Flag", mix.name)
        assertEquals("Based on White Flag by Fall Of Envy", mix.basedOn)
        assertTrue(mix.found)
    }

    @Test
    fun anAlbumMixIsNamedAfterTheAlbum() {
        val album = Album("al-1", "Poetic Rage", "Fall Of Envy", "ar-1", null, 10, 2400, 2009, "Metal", false)
        val mix = Mix(MixSeed.FromAlbum(album), song("s3", "Track Three"), listOf(song("s3", "Track Three")))
        assertEquals("Instant Mix: Poetic Rage", mix.name)
        assertEquals("Based on Poetic Rage by Fall Of Envy", mix.basedOn)
        // Nothing similar came back: just the song it started from.
        assertFalse(mix.found)
    }

    @Test
    fun theSameStartGivesTheSameMixButEachSurpriseIsNew() {
        assertEquals(MixSeed.FromSong(song("s1")).key, MixSeed.FromSong(song("s1")).key)
        assertNotEquals(MixSeed.Surprise(1).key, MixSeed.Surprise(2).key)
    }
}
