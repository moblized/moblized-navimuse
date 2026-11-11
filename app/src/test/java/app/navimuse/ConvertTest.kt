package app.navimuse

import app.navimuse.net.Song
import app.navimuse.playback.Convert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConvertTest {
    private fun song(id: String, suffix: String = "flac", rate: Int = 44100, bits: Int = 16) =
        Song(id, "Rainy Day Woman", "Waylon Jennings", "ar-1", "Ultimate", "al-1", null, 152, 1, 1, 1975, suffix, 942, false,
            channels = 2, samplingRate = rate, bitDepth = bits)

    @Before
    fun reset() = Convert.forgetAll()

    @Test
    fun aFailedConversionTriesTheOriginalThenMp3ThenLower() {
        // Converted at 192 failed: the original file next, then MP3, then 128 kbps MP3, then nothing.
        assertEquals(0, Convert.nextTry("s1", 192, 192))
        assertEquals(Convert.MP3, Convert.nextTry("s1", 0, 192))
        assertEquals(-128, Convert.nextTry("s1", Convert.MP3, 192))
        assertNull(Convert.nextTry("s1", -128, 192))
        // Having run out, the next attempt starts over.
        assertEquals(0, Convert.nextTry("s1", 192, 192))
    }

    @Test
    fun aFailedOriginalOnWifiGoesToMp3() {
        assertEquals(Convert.MP3, Convert.nextTry("s2", 0, 0))
        assertEquals(192, Convert.nextTry("s2", Convert.MP3, 0))
    }

    @Test
    fun aBrokenDownloadStreamsInstead() {
        assertEquals(0, Convert.nextTry("s3", null, 192))
        assertTrue(Convert.downloadFailed("s3"))
    }

    @Test
    fun whatWorkedAfterARetryIsRemembered() {
        val s = song("s4")
        // Played fine the first time: nothing to remember.
        Convert.worked(s, 192)
        assertNull(Convert.preferred(s))
        Convert.nextTry("s4", 192, 192)
        Convert.worked(s, 0)
        assertEquals(0, Convert.preferred(s))
    }

    @Test
    fun threeSongsOfAKindTeachTheKind() {
        var m = Convert.Memory()
        m = m.remember(song("a", rate = 192000, bits = 24), Convert.MP3)
        m = m.remember(song("b", rate = 192000, bits = 24), Convert.MP3)
        assertNull(m.preferred(song("new", rate = 192000, bits = 24)))
        m = m.remember(song("c", rate = 192000, bits = 24), Convert.MP3)
        assertEquals(Convert.MP3, m.preferred(song("new", rate = 192000, bits = 24)))
        // Other kinds are left alone.
        assertNull(m.preferred(song("cd", rate = 44100, bits = 16)))
    }

    @Test
    fun memorySurvivesSaving() {
        val m = Convert.Memory()
            .remember(song("a"), 0)
            .remember(song("b", suffix = "m4a"), Convert.MP3)
            .copy(badDownloads = setOf("d1"))
        assertEquals(m, Convert.Memory.fromJson(m.toJson()))
        assertEquals(Convert.Memory(), Convert.Memory.fromJson("not json"))
        assertEquals(Convert.Memory(), Convert.Memory.fromJson(null))
    }

    @Test
    fun messagesNameTheWay() {
        assertEquals("the original FLAC", Convert.describe(0, "flac"))
        assertEquals("MP3 at 320 kbps", Convert.describe(Convert.MP3, "flac"))
        assertEquals("converted at 192 kbps", Convert.describe(192, "flac"))
        assertEquals("your downloaded copy", Convert.describe(null, "flac"))
    }
}
