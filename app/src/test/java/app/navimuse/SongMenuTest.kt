package app.navimuse

import app.navimuse.data.Blocklist
import app.navimuse.net.Credit
import app.navimuse.net.Parse
import app.navimuse.net.Song
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongMenuTest {
    private fun song(id: String, artistId: String? = "ar-1", artist: String = "Fall Of Envy") =
        Song(id, "White Flag", artist, artistId, "Poetic Rage", "al-1", null, 200, 1, 1, 2009, "flac", 900, false)

    @Test
    fun creditsListPeopleThenFacts() {
        val resp = JSONObject(
            """{"song":{"id":"s1","title":"White Flag","displayArtist":"Fall Of Envy","displayAlbumArtist":"Fall Of Envy",
              "displayComposer":"J. Smith","album":"Poetic Rage","year":2009,"track":3,"discNumber":1,"bpm":140,
              "genres":[{"name":"Metal"},{"name":"Rock"}],
              "contributors":[
                {"role":"composer","artist":{"id":"c1","name":"J. Smith"}},
                {"role":"producer","artist":{"id":"p1","name":"A. Producer"}},
                {"role":"producer","artist":{"id":"p2","name":"B. Producer"}},
                {"role":"performer","subRole":"guitar","artist":{"id":"g1","name":"C. Player"}},
                {"role":"artist","artist":{"id":"ar-1","name":"Fall Of Envy"}},
                {"role":"djmixer","artist":{"id":"d1","name":"DJ One"}}]}}""",
        )
        assertEquals(
            listOf(
                Credit("Artist", "Fall Of Envy"),
                Credit("Album artist", "Fall Of Envy"),
                Credit("Composer", "J. Smith"),
                Credit("Producer", "A. Producer, B. Producer"),
                Credit("Performer (guitar)", "C. Player"),
                Credit("DJ mixer", "DJ One"),
                Credit("Album", "Poetic Rage"),
                Credit("Year", "2009"),
                Credit("Genre", "Metal, Rock"),
                Credit("Track", "3"),
                Credit("BPM", "140"),
            ),
            Parse.credits(resp),
        )
    }

    @Test
    fun creditsFromAPlainSubsonicServer() {
        val resp = JSONObject("""{"song":{"id":"s1","title":"X","artist":"Someone","album":"A","genre":"Pop","track":2,"discNumber":2}}""")
        assertEquals(
            listOf(Credit("Artist", "Someone"), Credit("Album", "A"), Credit("Genre", "Pop"), Credit("Track", "2 (disc 2)")),
            Parse.credits(resp),
        )
        assertTrue(Parse.credits(JSONObject("{}")).isEmpty())
    }

    @Test
    fun blockedSongsAndArtists() {
        val lists = Blocklist.Lists(
            songs = mapOf("s1" to Blocklist.Entry("s1", "White Flag", "Fall Of Envy")),
            artists = mapOf("ar-9" to Blocklist.Entry("ar-9", "Someone Else")),
        )
        assertTrue(lists.isBlocked(song("s1")))
        assertFalse(lists.isBlocked(song("s2")))
        assertTrue(lists.isBlocked(song("s3", artistId = "ar-9")))
        // Without an artist id, the artist is blocked by name.
        val byName = Blocklist.Lists(artists = mapOf("name:nobody" to Blocklist.Entry("name:nobody", "Nobody")))
        assertTrue(byName.isBlocked(song("s4", artistId = null, artist = "Nobody")))
        assertEquals("name:nobody", Blocklist.artistKey(song("s4", artistId = null, artist = "Nobody")))
    }

    @Test
    fun blockedListSurvivesSaving() {
        val lists = Blocklist.Lists(
            songs = mapOf("s1" to Blocklist.Entry("s1", "White Flag", "Fall Of Envy")),
            artists = mapOf("ar-9" to Blocklist.Entry("ar-9", "Someone Else")),
        )
        assertEquals(lists, Blocklist.Lists.fromJson(lists.toJson()))
        assertEquals(Blocklist.Lists(), Blocklist.Lists.fromJson(null))
        assertEquals(Blocklist.Lists(), Blocklist.Lists.fromJson("broken"))
    }
}
