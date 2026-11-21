package app.navimuse

import app.navimuse.playback.SongTrouble
import app.navimuse.playback.StreamCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamCheckTest {
    private fun bytes(s: String) = s.toByteArray()

    // The start of real audio files: binary from the first bytes.
    private val flac = byteArrayOf(0x66, 0x4c, 0x61, 0x43, 0x00, 0x00, 0x00, 0x22, 0x12, 0x00)
    private val mp3 = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x01, 0x76)

    @Test
    fun audioIsAudio() {
        assertEquals(StreamCheck.Audio, StreamCheck.classify(206, "audio/flac", flac))
        assertEquals(StreamCheck.Audio, StreamCheck.classify(200, "audio/mpeg", mp3))
        // No content type, or a vague one: still judged by the bytes.
        assertEquals(StreamCheck.Audio, StreamCheck.classify(200, null, mp3))
        assertEquals(StreamCheck.Audio, StreamCheck.classify(200, "application/octet-stream", flac))
    }

    @Test
    fun aMissingFileIsSeenInNavidromesJsonError() {
        // What Navidrome sends for the original file when it can't open it.
        val body = """{"subsonic-response":{"status":"failed","version":"1.16.1","type":"navidrome","serverVersion":"0.60.0","openSubsonic":true,""" +
            """"error":{"code":0,"message":"Internal Server Error: open /music/Saliva/Every Twenty Years/03 - Your Disease.flac: no such file or directory"}}}"""
        val check = StreamCheck.classify(200, "application/json", bytes(body))
        assertTrue(check is StreamCheck.Refused)
        check as StreamCheck.Refused
        assertEquals(0, check.code)
        assertTrue(check.missing)
        assertTrue(check.songOnly)
        assertEquals("open /music/Saliva/Every Twenty Years/03 - Your Disease.flac: no such file or directory", check.message)
    }

    @Test
    fun notFoundByCodeOrXml() {
        val json = """{"subsonic-response":{"status":"failed","error":{"code":70,"message":"data not found"}}}"""
        assertTrue((StreamCheck.classify(200, "application/json; charset=utf-8", bytes(json)) as StreamCheck.Refused).missing)
        val xml = """<?xml version="1.0" encoding="UTF-8"?><subsonic-response status="failed"><error message="Song &quot;x&quot; not found" code="70"/></subsonic-response>"""
        val check = StreamCheck.classify(200, "text/xml", bytes(xml)) as StreamCheck.Refused
        assertEquals(70, check.code)
        assertEquals("Song \"x\" not found", check.message)
    }

    @Test
    fun anErrorIsSeenEvenWithTheWrongContentType() {
        val json = """{"subsonic-response":{"status":"failed","error":{"code":0,"message":"open /m/a.flac: permission denied"}}}"""
        val check = StreamCheck.classify(200, "audio/flac", bytes(json)) as StreamCheck.Refused
        assertTrue(check.forbidden)
        assertFalse(check.missing)
        assertTrue(check.songOnly)
    }

    @Test
    fun serverWideTroubleIsNotBlamedOnTheSong() {
        val auth = """{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}"""
        val check = StreamCheck.classify(200, "application/json", bytes(auth)) as StreamCheck.Refused
        assertFalse(check.songOnly)
        assertEquals("Navidrome said: Wrong username or password", SongTrouble.reason(check))
        // A proxy's error page.
        val page = StreamCheck.classify(502, "text/html", bytes("<!DOCTYPE html><html><body>Bad gateway</body></html>")) as StreamCheck.Refused
        assertEquals("HTTP 502", page.message)
        assertFalse(page.songOnly)
        assertEquals(StreamCheck.Refused(null, "HTTP 500"), StreamCheck.classify(500, "audio/mpeg", ByteArray(0)))
    }

    @Test
    fun anEmptyConversionIsEmpty() {
        // Navidrome's answer when ffmpeg had nothing to convert.
        assertEquals(StreamCheck.Empty, StreamCheck.classify(200, "audio/mpeg", ByteArray(0)))
    }

    @Test
    fun busyMeansTryAgainShortly() {
        val body = """{"subsonic-response":{"status":"failed","error":{"code":0,"message":"too many concurrent transcodes, please retry shortly"}}}"""
        assertEquals(StreamCheck.Busy(5), StreamCheck.classify(429, "application/json", bytes(body), "5"))
        assertEquals(StreamCheck.Busy(5), StreamCheck.classify(429, "application/json", bytes(body)))
    }

    @Test
    fun errorContentTypes() {
        assertTrue(StreamCheck.isErrorContentType("application/json"))
        assertTrue(StreamCheck.isErrorContentType("application/json; charset=utf-8"))
        assertTrue(StreamCheck.isErrorContentType("text/xml"))
        assertTrue(StreamCheck.isErrorContentType("text/html; charset=UTF-8"))
        assertFalse(StreamCheck.isErrorContentType(""))
        assertFalse(StreamCheck.isErrorContentType(null))
        assertFalse(StreamCheck.isErrorContentType("audio/mpeg"))
        assertFalse(StreamCheck.isErrorContentType("audio/ogg"))
        assertFalse(StreamCheck.isErrorContentType("application/octet-stream"))
        assertFalse(StreamCheck.isErrorContentType("text/plain"))
    }

    @Test
    fun notices() {
        val gone = StreamCheck.Refused(70, "data not found")
        assertEquals(
            "Skipped \"Your Disease (2021 Version)\": Navidrome can't find its file on your server. " +
                "It may have been moved, renamed or deleted; a full scan in Navidrome tidies that up.",
            SongTrouble.notice("Your Disease (2021 Version)", gone, skipped = true),
        )
        assertEquals("Can't play \"Lucy\": Navidrome sent an empty file for it.", SongTrouble.notice("Lucy", StreamCheck.Empty, skipped = false))
    }
}
