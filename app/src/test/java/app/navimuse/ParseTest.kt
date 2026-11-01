package app.navimuse

import app.navimuse.net.Parse
import app.navimuse.net.ServerUrl
import app.navimuse.net.SubsonicAuth
import app.navimuse.net.SubsonicException
import app.navimuse.playback.MediaIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ParseTest {
    private fun ok(inner: String) =
        """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"0.62.0","openSubsonic":true$inner}}"""

    @Test
    fun errorsBecomeReadableMessages() {
        try {
            Parse.envelope("""{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}""")
            fail()
        } catch (e: SubsonicException) {
            assertEquals(40, e.code)
            assertEquals("Wrong username or password.", e.message)
        }
    }

    @Test
    fun album() {
        val resp = Parse.envelope(
            ok(
                ""","album":{"id":"al1","name":"Discovery","artist":"Daft Punk","artistId":"ar1","coverArt":"al-al1","songCount":2,"year":2001,
                "song":[{"id":"s2","title":"Aerodynamic","track":2,"discNumber":1,"duration":212,"albumId":"al1","artist":"Daft Punk"},
                        {"id":"s1","title":"One More Time","track":1,"discNumber":1,"duration":320,"albumId":"al1","artist":"Daft Punk","starred":"2024-01-01T00:00:00Z"}]}""",
            ),
        )
        val d = Parse.albumDetail(resp)
        assertEquals("Discovery", d.album.name)
        assertEquals(listOf("s1", "s2"), d.songs.map { it.id })
        assertTrue(d.songs[0].starred)
        assertFalse(d.songs[1].starred)
        assertEquals(320, d.songs[0].duration)
    }

    @Test
    fun sonicMatches() {
        val resp = Parse.envelope(
            ok(""","sonicMatch":[{"entry":{"id":"a","title":"A"},"similarity":0.95},{"entry":{"id":"b","title":"B"},"similarity":0.5}]"""),
        )
        val m = Parse.sonicMatches(resp)
        assertEquals(listOf("a", "b"), m.map { it.song.id })
        assertEquals(0.95, m[0].similarity, 0.0001)
    }

    @Test
    fun serverInfoDetectsAudioMusePlugin() {
        val ping = Parse.envelope(ok(""))
        val ext = Parse.envelope(ok(""","openSubsonicExtensions":[{"name":"songLyrics","versions":[1]},{"name":"sonicSimilarity","versions":[1]}]"""))
        val info = Parse.serverInfo(ping, ext)
        assertTrue(info.sonicSimilarity)
        assertTrue(info.lyrics)
        assertEquals("navidrome", info.serverType)
        assertFalse(Parse.serverInfo(ping, null).sonicSimilarity)
    }

    @Test
    fun playlistsAndAutomaticFlag() {
        val resp = Parse.envelope(
            ok(""","playlists":{"playlist":[{"id":"1","name":"Chill_automatic","songCount":40,"owner":"me"},{"id":"2","name":"Gym","songCount":12,"owner":"me","public":true}]}"""),
        )
        val p = Parse.playlists(resp)
        assertTrue(p[0].isAudioMuseManaged)
        assertFalse(p[1].isAudioMuseManaged)
        assertTrue(p[1].public)
    }

    @Test
    fun loneObjectsCountAsLists() {
        val resp = Parse.envelope(ok(""","randomSongs":{"song":{"id":"x","title":"Solo"}}"""))
        assertEquals(listOf("x"), Parse.songList(resp, "randomSongs").map { it.id })
    }

    @Test
    fun moodHits() {
        val hits = Parse.moodHits("""{"query":"calm","count":1,"results":[{"item_id":"nd1","title":"T","author":"A","similarity":0.3}]}""")
        assertEquals("nd1", hits.single().itemId)
    }

    @Test
    fun genres() {
        val g = Parse.genres(
            Parse.envelope(ok(""","genres":{"genre":[{"value":"Rock","songCount":12,"albumCount":3},{"value":"","songCount":1},{"value":"Jazz","albumCount":1}]}""")),
        )
        assertEquals(listOf("Rock", "Jazz"), g.map { it.name })
        assertEquals(3, g[0].albumCount)
    }

    @Test
    fun syncedLyricsArePreferredAndOffsetApplied() {
        val l = Parse.lyrics(
            Parse.envelope(
                ok(
                    ""","lyricsList":{"structuredLyrics":[
                        {"lang":"eng","synced":false,"line":[{"value":"plain one"},{"value":"plain two"}]},
                        {"lang":"eng","synced":true,"offset":-100,"line":[{"start":0,"value":"first"},{"start":5000,"value":"second"}]}]}""",
                ),
            ),
        )!!
        assertTrue(l.synced)
        assertEquals(listOf(100L, 5100L), l.lines.map { it.startMs })
        assertEquals("second", l.lines[1].text)
    }

    @Test
    fun noLyrics() {
        assertNull(Parse.lyrics(Parse.envelope(ok(""","lyricsList":{}"""))))
        val plain = Parse.lyrics(Parse.envelope(ok(""","lyricsList":{"structuredLyrics":{"synced":false,"line":[{"value":"only"}]}}""")))!!
        assertFalse(plain.synced)
        assertNull(plain.lines.single().startMs)
    }

    @Test
    fun serverAddresses() {
        assertEquals("http://192.168.1.5:4533", ServerUrl.normalize("192.168.1.5:4533"))
        assertEquals("https://music.example.com", ServerUrl.normalize("https://music.example.com/app/#/album/all"))
        assertEquals("https://example.com/navidrome", ServerUrl.normalize("https://example.com/navidrome/"))
        assertEquals("http://nas:4533", ServerUrl.normalize(" http://nas:4533/rest/ping.view?u=x "))
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("ftp://nas"))
    }

    @Test
    fun tokenIsMd5OfPasswordAndSalt() {
        // Example from the Subsonic API docs.
        assertEquals("26719a1196d2a940705a59634eb18eab", SubsonicAuth.md5Hex("sesame" + "c19b2d"))
    }

    @Test
    fun mediaIds() {
        assertEquals("abc", MediaIds.songIdOf("s|abc|a|al1"))
        assertEquals("a|al1", MediaIds.contextOf("s|abc|a|al1"))
        assertEquals("abc", MediaIds.songIdOf("s|abc"))
        assertNull(MediaIds.contextOf("s|abc"))
        assertNull(MediaIds.songIdOf("a|al1"))
    }
}
