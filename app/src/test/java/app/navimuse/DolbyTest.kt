package app.navimuse

import app.navimuse.net.Parse
import app.navimuse.net.Song
import app.navimuse.playback.Dolby
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DolbyTest {
    private fun file(name: String): ByteArray =
        javaClass.getResourceAsStream("/dolby/$name")!!.use { it.readBytes() }

    private fun song(suffix: String?, channels: Int = 0) = Song(
        id = "s1", title = "T", artist = "A", artistId = null, album = "Al", albumId = "al1", coverArt = null,
        duration = 1, track = 1, disc = 1, year = 0, suffix = suffix, bitRate = 768, starred = false, channels = channels,
    )

    @Test
    fun findsEac3InsideM4a() {
        assertEquals(Dolby.DIGITAL_PLUS, Dolby.sniff(file("eac3-51.m4a")))
    }

    @Test
    fun readsTheEndWhenTheDetailsAreThere() {
        val all = file("eac3-51-moovend.m4a")
        val head = all.copyOfRange(0, 2048)
        assertNull(Dolby.sniff(head))
        assertTrue(Dolby.needsTail(head))
        assertEquals(Dolby.DIGITAL_PLUS, Dolby.sniff(all.copyOfRange(all.size - 2048, all.size)))
    }

    @Test
    fun plainAacIsNotDolby() {
        val aac = file("aac.m4a")
        assertNull(Dolby.sniff(aac))
        assertFalse(Dolby.needsTail(aac))
    }

    @Test
    fun findsEac3InsideMatroska() {
        assertEquals(Dolby.DIGITAL_PLUS, Dolby.sniff(file("eac3.mka")))
    }

    /** The same 5.1 E-AC-3 settings box, with the Joint Object Coding flag set: Dolby Atmos. */
    @Test
    fun jocFlagMeansAtmos() {
        val dec3 = byteArrayOf(0, 0, 0, 15) + "dec3".toByteArray() +
            byteArrayOf(0x03, 0x00, 0x20, 0x0f, 0x00, 0x01, 0x10)
        val box = "ec-3".toByteArray() + ByteArray(28) + dec3
        assertEquals(Dolby.ATMOS, Dolby.sniff(box))
        val plain = file("eac3-51.m4a")
        assertEquals(Dolby.DIGITAL_PLUS, Dolby.sniff(plain))
    }

    @Test
    fun namesWhatThePlayerDecodes() {
        assertEquals(Dolby.ATMOS, Dolby.fromMime("audio/eac3-joc", 6))
        assertEquals(Dolby.DIGITAL_PLUS, Dolby.fromMime("audio/eac3", 6))
        assertEquals(Dolby.TRUEHD, Dolby.fromMime("audio/true-hd", 8))
        assertEquals("5.1 surround", Dolby.fromMime("audio/flac", 6))
        assertNull(Dolby.fromMime("audio/mpeg", 2))
    }

    @Test
    fun serverDetailsGiveSomeAway() {
        assertEquals(Dolby.DIGITAL_PLUS, Dolby.hint(song("ec3")))
        assertEquals("7.1 surround", Dolby.hint(song("flac", channels = 8)))
        assertNull(Dolby.hint(song("m4a", channels = 2)))
        assertTrue(Dolby.canSniff(song("m4a")))
        assertFalse(Dolby.canSniff(song("mp3")))
    }

    @Test
    fun channelCountIsRead() {
        val s = Parse.song(JSONObject("""{"id":"x","title":"T","suffix":"flac","channelCount":6}"""))
        assertEquals(6, s.channels)
        assertEquals("5.1 surround", Dolby.labelFor(s))
    }
}
