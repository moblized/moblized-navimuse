package app.navimuse

import app.navimuse.net.BlockedRequestException
import app.navimuse.net.PlaylistNames
import app.navimuse.net.SafetyGuard
import app.navimuse.net.ServerLogin
import app.navimuse.net.SubsonicClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The app's core promise: nothing that can delete or overwrite ever leaves the phone. */
class SafetyGuardTest {
    private fun blocked(method: String, vararg params: Pair<String, String>): Boolean = try {
        SafetyGuard.check(method, params.toList())
        false
    } catch (e: BlockedRequestException) {
        true
    }

    @Test
    fun everyDeleteIsBlocked() {
        val deletes = listOf(
            "deletePlaylist", "deleteShare", "deleteBookmark", "deleteInternetRadioStation",
            "deletePodcastChannel", "deletePodcastEpisode", "deleteUser",
        )
        deletes.forEach { assertTrue("$it must be blocked", blocked(it, "id" to "1")) }
        assertTrue(blocked("deletePlaylist.view", "id" to "1"))
    }

    @Test
    fun otherChangingRequestsAreBlocked() {
        listOf(
            "startScan", "savePlayQueue", "savePlayQueueByIndex", "createShare", "updateShare", "createUser",
            "updateUser", "changePassword", "jukeboxControl", "createInternetRadioStation", "updateInternetRadioStation",
            "refreshPodcasts", "createPodcastChannel", "downloadPodcastEpisode", "createBookmark", "setRating",
            "download", "somethingNew",
        ).forEach { assertTrue("$it must be blocked", blocked(it)) }
    }

    @Test
    fun readingIsAllowed() {
        listOf(
            "ping", "getAlbum", "getAlbumList2", "search3", "getPlaylists", "getPlaylist", "stream", "getCoverArt",
            "getSimilarSongs", "getSimilarSongs2", "getSonicSimilarTracks", "findSonicPath", "getStarred2", "getSong",
        ).forEach { assertFalse("$it should be allowed", blocked(it, "id" to "1")) }
    }

    @Test
    fun createPlaylistCanOnlyMakeNewOnes() {
        assertFalse(blocked("createPlaylist", "name" to "Road trip", "songId" to "a", "songId" to "b"))
        // playlistId turns createPlaylist into "replace this playlist's songs"
        assertTrue(blocked("createPlaylist", "playlistId" to "p1", "songId" to "a"))
        assertTrue(blocked("createPlaylist", "name" to "x", "playlistId" to "p1"))
        assertTrue(blocked("createPlaylist", "songId" to "a"))
    }

    @Test
    fun createPlaylistRefusesNamesAudioMuseDeletes() {
        assertTrue(blocked("createPlaylist", "name" to "Chill_automatic"))
        assertTrue(blocked("createPlaylist", "name" to "Chill_AUTOMATIC "))
        assertFalse(blocked("createPlaylist", "name" to "Chill automatic mix"))
    }

    @Test
    fun updatePlaylistCanOnlyAddSongs() {
        assertFalse(blocked("updatePlaylist", "playlistId" to "p1", "songIdToAdd" to "a", "songIdToAdd" to "b"))
        assertTrue(blocked("updatePlaylist", "playlistId" to "p1", "songIndexToRemove" to "0"))
        assertTrue(blocked("updatePlaylist", "playlistId" to "p1", "songIdToAdd" to "a", "songIndexToRemove" to "3"))
        assertTrue(blocked("updatePlaylist", "playlistId" to "p1", "name" to "x_automatic"))
        assertTrue(blocked("updatePlaylist", "playlistId" to "p1", "public" to "false"))
        assertTrue(blocked("updatePlaylist", "songIdToAdd" to "a"))
    }

    @Test
    fun audioMuseOnlyGetsSearches() {
        SafetyGuard.checkAudioMuse("POST", "/api/clap/search")
        SafetyGuard.checkAudioMuse("GET", "/api/search_tracks?search_query=a")
        listOf(
            "POST" to "/api/cleaning/start",
            "POST" to "/api/clustering/start",
            "POST" to "/api/analysis/start",
            "DELETE" to "/api/servers/1",
            "DELETE" to "/api/anchors/2",
            "POST" to "/api/create_playlist",
            "GET" to "/api/clap/search",
            "POST" to "/api/backup/restore",
        ).forEach { (m, p) ->
            try {
                SafetyGuard.checkAudioMuse(m, p)
                fail("$m $p must be blocked")
            } catch (e: BlockedRequestException) {
                // expected
            }
        }
    }

    @Test
    fun clientRefusesBeforeAnythingIsSent(): Unit = runBlocking {
        val seen = mutableListOf<String>()
        // Port 9 on localhost: if anything were sent, it would fail with a connection error instead.
        val client = SubsonicClient(
            ServerLogin("http://127.0.0.1:9", "me", "secret", "salt"),
            OkHttpClient(),
        ) { seen += it.request }
        try {
            client.call("deletePlaylist", listOf("id" to "p1"))
            fail("deletePlaylist must be refused")
        } catch (e: BlockedRequestException) {
            assertEquals(listOf("deletePlaylist"), seen)
        }
    }

    @Test
    fun linksNeverContainThePassword() {
        val client = SubsonicClient(ServerLogin("http://nas:4533", "me", "hunter2", "abc123"), OkHttpClient())
        val url = client.streamUrl("song1", 0)
        assertFalse(url.contains("hunter2"))
        assertTrue(url.startsWith("http://nas:4533/rest/stream?"))
        assertTrue(url.contains("t="))
        assertTrue(url.contains("s=abc123"))
        // Same salt → same link, so album art can be cached.
        assertEquals(client.coverUrl("al-1"), client.coverUrl("al-1"))
    }

    @Test
    fun playlistNames() {
        assertTrue(PlaylistNames.isAudioMuseManaged("Rock_Fast_automatic"))
        assertFalse(PlaylistNames.isAudioMuseManaged("Rock Fast"))
        assertEquals("Rock_Fast", PlaylistNames.safe("Rock_Fast_automatic"))
        assertEquals("Mix", PlaylistNames.safe("Mix_automatic_automatic"))
        assertEquals("My mix", PlaylistNames.safe("_automatic"))
        assertEquals("Rock Fast", PlaylistNames.pretty("Rock_Fast_automatic"))
        assertEquals("Rock Fast (kept)", PlaylistNames.keptCopyName("Rock_Fast_automatic", listOf("Other")))
        assertEquals("Rock Fast (kept 2)", PlaylistNames.keptCopyName("Rock_Fast_automatic", listOf("Rock Fast (kept)")))
        assertFalse(PlaylistNames.isAudioMuseManaged(PlaylistNames.keptCopyName("A_automatic", emptyList())))
    }
}
