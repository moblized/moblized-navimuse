package app.navimuse

import app.navimuse.playback.MediaIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayTest {
    private val picked = MediaIds.song("a1", "a|album")
    private val single = MediaIds.song("s1")
    private val radio1 = MediaIds.song("r1", MediaIds.AUTOPLAY)
    private val radio2 = MediaIds.song("r2", MediaIds.AUTOPLAY)

    @Test
    fun autoplaySongsAreMarked() {
        assertTrue(MediaIds.isAutoplay(radio1))
        assertEquals("r1", MediaIds.songIdOf(radio1))
        assertFalse(MediaIds.isAutoplay(picked))
        assertFalse(MediaIds.isAutoplay(single))
    }

    @Test
    fun addToQueueGoesBeforeAutoplay() {
        // A single playing, radio after it: your song goes right after the single.
        assertEquals(1, MediaIds.addAt(listOf(single, radio1, radio2), current = 0))
        // Playing a radio song: after it, before the rest of the radio.
        assertEquals(2, MediaIds.addAt(listOf(single, radio1, radio2), current = 1))
        // Songs queued earlier stay ahead of the new one.
        assertEquals(2, MediaIds.addAt(listOf(single, picked, radio1), current = 0))
        // No radio: the end, as always.
        assertEquals(2, MediaIds.addAt(listOf(single, picked), current = 0))
        assertEquals(0, MediaIds.addAt(emptyList(), current = -1))
        // The last radio song playing: the end.
        assertEquals(3, MediaIds.addAt(listOf(single, radio1, radio2), current = 2))
    }
}
