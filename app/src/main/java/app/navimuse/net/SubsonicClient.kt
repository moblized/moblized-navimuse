package app.navimuse.net

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class ServerLogin(
    /** Normalised base address, e.g. "http://192.168.1.5:4533". */
    val baseUrl: String,
    val username: String,
    val password: String,
    /** A salt saved once per install, used for album-art and stream links so they can be cached. */
    val mediaSalt: String,
)

/**
 * Talks to Navidrome over the Subsonic API. Every request — including stream and album-art
 * links — is checked by [SafetyGuard] first, so nothing that could delete or overwrite is ever sent.
 */
class SubsonicClient(
    val login: ServerLogin,
    private val http: OkHttpClient,
    /** Told about every request the guard refused (shown on the Safety screen). */
    private val onBlocked: (BlockedRequestException) -> Unit = {},
) {
    private val base: HttpUrl = login.baseUrl.toHttpUrl()

    private fun buildUrl(method: String, params: List<Pair<String, String>>, salt: String): HttpUrl {
        try {
            SafetyGuard.check(method, params)
        } catch (e: BlockedRequestException) {
            onBlocked(e)
            throw e
        }
        val b = base.newBuilder().addPathSegment("rest").addPathSegment(method)
        b.addQueryParameter("u", login.username)
        b.addQueryParameter("t", SubsonicAuth.md5Hex(login.password + salt))
        b.addQueryParameter("s", salt)
        b.addQueryParameter("v", API_VERSION)
        b.addQueryParameter("c", CLIENT_NAME)
        b.addQueryParameter("f", "json")
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    /** Runs a request and returns its "subsonic-response" object. */
    suspend fun call(method: String, params: List<Pair<String, String>> = emptyList()): JSONObject =
        withContext(Dispatchers.IO) {
            val url = buildUrl(method, params, SubsonicAuth.newSalt())
            val request = Request.Builder().url(url).get().build()
            http.newCall(request).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (r.code == 404 && (method == "getSonicSimilarTracks" || method == "findSonicPath")) {
                    throw SubsonicException(404, "Sonic similarity isn't turned on in Navidrome (the AudioMuse-AI plugin isn't active).")
                }
                if (!r.isSuccessful && !body.trimStart().startsWith("{")) {
                    throw IOException("The server answered ${r.code}${r.message.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}.")
                }
                try {
                    Parse.envelope(body)
                } catch (e: org.json.JSONException) {
                    throw SubsonicException(0, "That address answered, but not like a Navidrome server. Check the address (and port).")
                }
            }
        }

    // --- links for the player and images (same guard, stable salt so they can be cached) -------

    fun streamUrl(songId: String, maxBitRateKbps: Int): String {
        val p = buildList {
            add("id" to songId)
            if (maxBitRateKbps > 0) {
                add("maxBitRate" to maxBitRateKbps.toString())
                // Lets the player seek inside converted streams.
                add("estimateContentLength" to "true")
            } else {
                add("format" to "raw")
            }
        }
        return buildUrl("stream", p, login.mediaSalt).toString()
    }

    fun coverUrl(coverArtId: String?, size: Int = 600): String? {
        if (coverArtId.isNullOrBlank()) return null
        return buildUrl("getCoverArt", listOf("id" to coverArtId, "size" to size.toString()), login.mediaSalt).toString()
    }

    // --- reading -------------------------------------------------------------------------------

    suspend fun serverInfo(): ServerInfo {
        val ping = call("ping")
        val ext = runCatching { call("getOpenSubsonicExtensions") }.getOrNull()
        return Parse.serverInfo(ping, ext)
    }

    suspend fun albumList(type: AlbumListType, size: Int = 30, offset: Int = 0): List<Album> =
        Parse.albumList(call("getAlbumList2", listOf("type" to type.apiValue, "size" to size.toString(), "offset" to offset.toString())))

    suspend fun album(id: String): AlbumDetail = Parse.albumDetail(call("getAlbum", listOf("id" to id)))

    suspend fun artists(): List<Artist> = Parse.artistIndex(call("getArtists"))

    suspend fun artist(id: String): ArtistDetail = Parse.artistDetail(call("getArtist", listOf("id" to id)))

    suspend fun artistInfo(id: String, similarCount: Int = 12): ArtistInfo =
        Parse.artistInfo(call("getArtistInfo2", listOf("id" to id, "count" to similarCount.toString())))

    suspend fun topSongs(artistName: String, count: Int = 10): List<Song> =
        Parse.songList(call("getTopSongs", listOf("artist" to artistName, "count" to count.toString())), "topSongs")

    suspend fun song(id: String): Song? = Parse.singleSong(call("getSong", listOf("id" to id)))

    suspend fun playlists(): List<Playlist> = Parse.playlists(call("getPlaylists"))

    suspend fun playlist(id: String): PlaylistDetail = Parse.playlistDetail(call("getPlaylist", listOf("id" to id)))

    suspend fun search(query: String, songs: Int = 30, albums: Int = 12, artists: Int = 8): SearchResult =
        Parse.search(
            call(
                "search3",
                listOf(
                    "query" to query,
                    "songCount" to songs.toString(),
                    "albumCount" to albums.toString(),
                    "artistCount" to artists.toString(),
                ),
            ),
        )

    suspend fun starred(): Starred = Parse.starred(call("getStarred2"))

    suspend fun randomSongs(size: Int = 50): List<Song> =
        Parse.songList(call("getRandomSongs", listOf("size" to size.toString())), "randomSongs")

    // --- AudioMuse-AI, through Navidrome's plugin -------------------------------------------------

    /** Instant Mix: songs that sound like this song (or album / artist). */
    suspend fun similarSongs(id: String, count: Int = 50): List<Song> =
        Parse.songList(call("getSimilarSongs", listOf("id" to id, "count" to count.toString())), "similarSongs")

    /** Artist radio. */
    suspend fun similarSongsByArtist(artistId: String, count: Int = 50): List<Song> =
        Parse.songList(call("getSimilarSongs2", listOf("id" to artistId, "count" to count.toString())), "similarSongs2")

    /** Similar songs with a match score (OpenSubsonic sonicSimilarity). */
    suspend fun sonicSimilar(songId: String, count: Int = 30): List<SonicMatch> =
        Parse.sonicMatches(call("getSonicSimilarTracks", listOf("id" to songId, "count" to count.toString())))

    /** A smooth journey of songs from one song to another. */
    suspend fun sonicPath(startSongId: String, endSongId: String, count: Int = 25): List<SonicMatch> =
        Parse.sonicMatches(
            call("findSonicPath", listOf("startSongId" to startSongId, "endSongId" to endSongId, "count" to count.toString())),
        )

    /** Looks up many songs at once (for AudioMuse-AI mood search results), keeping their order. */
    suspend fun songs(ids: List<String>): List<Song> = coroutineScope {
        val gate = Semaphore(6)
        ids.distinct().map { id -> async { gate.withPermit { runCatching { song(id) }.getOrNull() } } }
            .awaitAll()
            .filterNotNull()
    }

    // --- writing (add-only; see SafetyGuard) ------------------------------------------------------

    /**
     * Makes a NEW playlist. Never replaces one. The name is cleaned so AudioMuse-AI's
     * clustering (which deletes "…_automatic" playlists) can't remove it.
     * Returns the new playlist's id.
     */
    suspend fun createPlaylist(name: String, songIds: List<String>): String {
        val safeName = PlaylistNames.safe(name)
        val first = songIds.take(BATCH)
        val resp = call("createPlaylist", listOf("name" to safeName) + first.map { "songId" to it })
        val id = Parse.createdPlaylistId(resp)
            ?: playlists().filter { it.name == safeName }.maxByOrNull { it.songCount.coerceAtMost(first.size) }?.id
            ?: throw IOException("The playlist was made, but the server didn't say which one it is.")
        songIds.drop(BATCH).chunked(BATCH).forEach { chunk -> addToPlaylist(id, chunk) }
        return id
    }

    /** Adds songs to the end of a playlist. Nothing already in it is touched. */
    suspend fun addToPlaylist(playlistId: String, songIds: List<String>) {
        songIds.chunked(BATCH).forEach { chunk ->
            call("updatePlaylist", listOf("playlistId" to playlistId) + chunk.map { "songIdToAdd" to it })
        }
    }

    suspend fun star(id: String, kind: StarKind) {
        call("star", listOf(kind.param to id))
    }

    suspend fun unstar(id: String, kind: StarKind) {
        call("unstar", listOf(kind.param to id))
    }

    /** Tells Navidrome what you're playing / played, which AudioMuse-AI uses for "your taste" mixes. */
    suspend fun scrobble(songId: String, submission: Boolean) {
        call("scrobble", listOf("id" to songId, "submission" to submission.toString()))
    }

    enum class StarKind(val param: String) { SONG("id"), ALBUM("albumId"), ARTIST("artistId") }

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "NaviMuse"
        private const val BATCH = 100
    }
}
