package app.navimuse

import app.navimuse.data.SongLists
import app.navimuse.data.SongMatch
import app.navimuse.data.WantedSong
import app.navimuse.net.Song
import app.navimuse.net.TidalLinks
import app.navimuse.net.TidalParse
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportTest {
    private fun song(title: String, artist: String, seconds: Int = 200, id: String = title) =
        Song(id, title, artist, "ar", "Album", "al", null, seconds, 1, 1, 2021, "flac", 900, false)

    @Test
    fun tidalLinks() {
        val id = "4261748a-4287-4758-aaab-6d5be3e99e52"
        assertEquals(id, TidalLinks.playlistId("https://tidal.com/browse/playlist/$id"))
        assertEquals(id, TidalLinks.playlistId("Listen to this https://listen.tidal.com/playlist/$id?u"))
        assertEquals(id, TidalLinks.playlistId("https://tidal.com/playlist/${id.uppercase()}"))
        assertEquals(id, TidalLinks.playlistId("  $id "))
        assertNull(TidalLinks.playlistId("https://tidal.com/browse/album/12345"))
        assertNull(TidalLinks.playlistId("Skillet - Rise Up"))
    }

    @Test
    fun tidalPlaylistPages() {
        // A page of /playlists/{id}/relationships/items?include=items (JSON:API).
        val page = JSONObject(
            """
            {"data":[{"id":"75413016","type":"tracks","meta":{"itemId":"a"}},{"id":"9","type":"videos"},{"id":"110","type":"tracks"}],
             "included":[{"id":"75413016","type":"tracks","attributes":{"title":"Rise Up","version":null,"isrc":"USAT21300001","duration":"PT3M58S"}},
                         {"id":"110","type":"tracks","attributes":{"title":"Your Disease","version":"2021 Version","duration":"PT4M"}}],
             "links":{"self":"/playlists/x/relationships/items","next":"/playlists/x/relationships/items?countryCode=US&page[cursor]=abc"}}
            """.trimIndent(),
        )
        val items = TidalParse.items(page)
        assertEquals(listOf("75413016", "110"), items.ids)
        assertEquals("Rise Up", items.tracks.first { it.id == "75413016" }.title)
        assertEquals(238, items.tracks.first { it.id == "75413016" }.durationSec)
        assertEquals("2021 Version", items.tracks.first { it.id == "110" }.version)
        assertEquals("/playlists/x/relationships/items?countryCode=US&page[cursor]=abc", TidalParse.nextLink(page))
        assertNull(TidalParse.nextLink(JSONObject("""{"data":[],"links":{"self":"/x"}}""")))
    }

    @Test
    fun tidalTracksWithArtists() {
        val doc = JSONObject(
            """
            {"data":[{"id":"1","type":"tracks","attributes":{"title":"Hero","duration":"PT3M6S"},
                      "relationships":{"artists":{"data":[{"id":"7","type":"artists"}]}}},
                     {"id":"2","type":"tracks","attributes":{"title":"Domination","duration":"PT2M56S"},
                      "relationships":{"artists":{"data":[{"id":"8","type":"artists"},{"id":"9","type":"artists"}]}}}],
             "included":[{"id":"7","type":"artists","attributes":{"name":"Skillet"}},
                         {"id":"8","type":"artists","attributes":{"name":"Saliva"}},
                         {"id":"9","type":"artists","attributes":{"name":"Guest"}}]}
            """.trimIndent(),
        )
        val tracks = TidalParse.tracks(doc)
        assertEquals(listOf("Skillet"), tracks.first { it.id == "1" }.artists)
        assertEquals(listOf("Saliva", "Guest"), tracks.first { it.id == "2" }.artists)
        assertEquals("My mix", TidalParse.playlistName(JSONObject("""{"data":{"id":"x","type":"playlists","attributes":{"name":"My mix"}}}""")))
    }

    @Test
    fun isoDurations() {
        assertEquals(205, TidalParse.isoSeconds("PT3M25S"))
        assertEquals(3723, TidalParse.isoSeconds("PT1H2M3S"))
        assertEquals(240, TidalParse.isoSeconds("PT4M"))
        assertEquals(59, TidalParse.isoSeconds("PT59.5S"))
        assertEquals(0, TidalParse.isoSeconds("three minutes"))
    }

    @Test
    fun pastedLists() {
        val list = SongLists.parse(
            """
            Skillet - Rise Up
            1. Saliva – Your Disease (2021 Version)
            Time of Dying by Three Days Grace
            # a note
            Lucy
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                WantedSong("Rise Up", listOf("Skillet")),
                WantedSong("Your Disease (2021 Version)", listOf("Saliva")),
                WantedSong("Time of Dying", listOf("Three Days Grace")),
                WantedSong("Lucy"),
            ),
            list,
        )
    }

    @Test
    fun csvExports() {
        // The way transfer services export a TIDAL playlist.
        val csv = "Track name,Artist name,Album,Playlist name,Type,ISRC\n" +
            "\"Hero\",\"Skillet\",\"Awake\",\"Rock\",\"Playlist\",\"USAT20900001\"\n" +
            "\"Villain I'm Not\",\"Three Days Grace\",\"Outsider\",\"Rock\",\"Playlist\",\"\"\n" +
            "\"Say \"\"Hi\"\"\",\"A, B\",\"X\",\"Rock\",\"Playlist\",\"\"\n"
        val list = SongLists.parse(csv)
        assertEquals(3, list.size)
        assertEquals(WantedSong("Hero", listOf("Skillet")), list[0])
        assertEquals("Villain I'm Not", list[1].title)
        assertEquals("Say \"Hi\"", list[2].title)
        assertEquals(listOf("A, B", "A", "B"), list[2].artists)
        val semi = SongLists.parse("Title;Artist;Duration\nHero;Skillet;3:06\n")
        assertEquals(WantedSong("Hero", listOf("Skillet"), 186), semi.single())
    }

    @Test
    fun matching() {
        val library = listOf(
            song("Your Disease (2021 Version)", "Saliva", 240, "a"),
            song("Your Disease", "Saliva", 220, "b"),
            song("Hero", "Skillet", 186, "c"),
            song("Hero", "Superchick", 200, "d"),
        )
        // The exact title wins over the "(2021 Version)" one only on length; both are Saliva.
        assertEquals("a", SongMatch.best(WantedSong("Your Disease", listOf("Saliva"), 240), library)?.id)
        // The artist decides between two songs called Hero.
        assertEquals("c", SongMatch.best(WantedSong("Hero", listOf("Skillet")), library)?.id)
        assertEquals("d", SongMatch.best(WantedSong("Hero", listOf("Superchick")), library)?.id)
        // A different artist and no matching length: not this song.
        assertNull(SongMatch.best(WantedSong("Hero", listOf("Nickelback")), library))
        // Remaster tags and "feat." don't get in the way.
        assertEquals("c", SongMatch.best(WantedSong("Hero - Remastered 2011", listOf("Skillet")), library)?.id)
        assertTrue(SongMatch.score(WantedSong("Hero (feat. Someone)", listOf("Skillet")), library[2]) > 0)
        assertEquals("your disease saliva", SongMatch.query(WantedSong("Your Disease (2021 Version)", listOf("Saliva"))))
    }
}
