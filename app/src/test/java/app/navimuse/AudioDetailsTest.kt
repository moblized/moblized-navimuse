package app.navimuse

import app.navimuse.net.Song
import app.navimuse.playback.DecodedFormat
import app.navimuse.ui.components.AudioDetails
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioDetailsTest {
    private fun song(suffix: String, kbps: Int, hz: Int = 0, bits: Int = 0) = Song(
        id = "s", title = "T", artist = "A", artistId = null, album = "Al", albumId = null, coverArt = null,
        duration = 200, track = 1, disc = 1, year = 0, suffix = suffix, bitRate = kbps, starred = false,
        samplingRate = hz, bitDepth = bits,
    )

    @Test
    fun dolbyInMp4() {
        val line = AudioDetails.line(song("m4a", 768, 48000), DecodedFormat("audio/eac3-joc", 48000, 6, 0), 0)
        assertEquals("MP4/EAC3 • 768 kb/s • 48.0 kHz", line)
    }

    @Test
    fun losslessShowsBitDepth() {
        val line = AudioDetails.line(song("flac", 1411, 44100, 16), DecodedFormat("audio/flac", 44100, 2, 0), 0)
        assertEquals("FLAC • 1,411 kb/s • 16-bit • 44.1 kHz", line)
    }

    @Test
    fun convertedByTheServer() {
        val line = AudioDetails.line(song("flac", 1411, 96000, 24), DecodedFormat("audio/opus", 48000, 2, 0), 320)
        assertEquals("OPUS • 320 kb/s • 48.0 kHz", line)
    }

    @Test
    fun beforeThePlayerKnows() {
        assertEquals("MP3 • 320 kb/s • 44.1 kHz", AudioDetails.line(song("mp3", 320, 44100), null, 0))
        assertEquals("AAC • 256 kb/s • 44.1 kHz", AudioDetails.line(song("m4a", 256, 44100), DecodedFormat("audio/mp4a-latm", 44100, 2, 0), 0))
    }
}
