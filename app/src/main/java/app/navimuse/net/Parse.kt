package app.navimuse.net

import org.json.JSONArray
import org.json.JSONObject

/** Turns Subsonic / OpenSubsonic JSON into the app's models. Lenient about missing fields. */
object Parse {
    /** The "subsonic-response" object, or throws [SubsonicException] if the server reported an error. */
    fun envelope(body: String): JSONObject {
        val root = JSONObject(body)
        val resp = root.optJSONObject("subsonic-response")
            ?: throw SubsonicException(0, "That address answered, but it isn't a Navidrome / Subsonic server.")
        if (resp.optString("status") != "ok") {
            val err = resp.optJSONObject("error")
            val code = err?.optInt("code") ?: 0
            val msg = err?.str("message") ?: "The server reported an error."
            throw SubsonicException(code, friendlyError(code, msg))
        }
        return resp
    }

    private fun friendlyError(code: Int, message: String): String = when (code) {
        40 -> "Wrong username or password."
        41 -> "This account can't use token login (LDAP). $message"
        50 -> "This account isn't allowed to do that."
        70 -> "Not found on the server."
        else -> message
    }

    fun song(o: JSONObject) = Song(
        id = o.optString("id"),
        title = o.str("title") ?: o.str("name") ?: "Unknown song",
        artist = o.str("displayArtist") ?: o.str("artist") ?: "Unknown artist",
        artistId = o.str("artistId"),
        album = o.str("album") ?: "",
        albumId = o.str("albumId") ?: o.str("parent"),
        coverArt = o.str("coverArt"),
        duration = o.optInt("duration"),
        track = o.optInt("track"),
        disc = o.optInt("discNumber"),
        year = o.optInt("year"),
        suffix = o.str("suffix"),
        bitRate = o.optInt("bitRate"),
        starred = o.str("starred") != null,
    )

    fun album(o: JSONObject) = Album(
        id = o.optString("id"),
        name = o.str("name") ?: o.str("album") ?: o.str("title") ?: "Unknown album",
        artist = o.str("displayArtist") ?: o.str("artist") ?: "Unknown artist",
        artistId = o.str("artistId"),
        coverArt = o.str("coverArt"),
        songCount = o.optInt("songCount"),
        duration = o.optInt("duration"),
        year = o.optInt("year"),
        genre = o.str("genre"),
        starred = o.str("starred") != null,
    )

    fun artist(o: JSONObject) = Artist(
        id = o.optString("id"),
        name = o.str("name") ?: "Unknown artist",
        coverArt = o.str("coverArt"),
        albumCount = o.optInt("albumCount"),
        imageUrl = o.str("artistImageUrl"),
        starred = o.str("starred") != null,
    )

    fun playlist(o: JSONObject) = Playlist(
        id = o.optString("id"),
        name = o.str("name") ?: "Playlist",
        songCount = o.optInt("songCount"),
        duration = o.optInt("duration"),
        owner = o.str("owner"),
        public = o.optBoolean("public", false),
        coverArt = o.str("coverArt"),
        comment = o.str("comment"),
    )

    fun songs(arr: JSONArray?): List<Song> = arr.objects().map(::song).filter { it.id.isNotEmpty() }
    fun albums(arr: JSONArray?): List<Album> = arr.objects().map(::album).filter { it.id.isNotEmpty() }
    fun artists(arr: JSONArray?): List<Artist> = arr.objects().map(::artist).filter { it.id.isNotEmpty() }

    // --- whole responses -------------------------------------------------------------------

    fun albumList(resp: JSONObject): List<Album> = albums(resp.optJSONObject("albumList2")?.arrayOf("album"))

    fun albumDetail(resp: JSONObject): AlbumDetail {
        val o = resp.optJSONObject("album") ?: throw SubsonicException(70, "Album not found.")
        return AlbumDetail(album(o), songs(o.arrayOf("song")).sortedWith(compareBy({ it.disc }, { it.track })))
    }

    fun artistIndex(resp: JSONObject): List<Artist> {
        val index = resp.optJSONObject("artists")?.arrayOf("index")
        return index.objects().flatMap { artists(it.arrayOf("artist")) }
    }

    fun artistDetail(resp: JSONObject): ArtistDetail {
        val o = resp.optJSONObject("artist") ?: throw SubsonicException(70, "Artist not found.")
        return ArtistDetail(artist(o), albums(o.arrayOf("album")).sortedByDescending { it.year })
    }

    fun artistInfo(resp: JSONObject): ArtistInfo {
        val o = resp.optJSONObject("artistInfo2") ?: resp.optJSONObject("artistInfo") ?: JSONObject()
        val bio = o.str("biography")?.replace(Regex("<[^>]*>"), "")?.trim()?.takeIf { it.isNotEmpty() }
        return ArtistInfo(bio, o.str("largeImageUrl") ?: o.str("mediumImageUrl"), artists(o.arrayOf("similarArtist")))
    }

    fun playlists(resp: JSONObject): List<Playlist> =
        resp.optJSONObject("playlists")?.arrayOf("playlist").objects().map(::playlist)

    fun playlistDetail(resp: JSONObject): PlaylistDetail {
        val o = resp.optJSONObject("playlist") ?: throw SubsonicException(70, "Playlist not found.")
        return PlaylistDetail(playlist(o), songs(o.arrayOf("entry")))
    }

    fun createdPlaylistId(resp: JSONObject): String? = resp.optJSONObject("playlist")?.str("id")

    fun search(resp: JSONObject): SearchResult {
        val o = resp.optJSONObject("searchResult3") ?: JSONObject()
        return SearchResult(artists(o.arrayOf("artist")), albums(o.arrayOf("album")), songs(o.arrayOf("song")))
    }

    fun starred(resp: JSONObject): Starred {
        val o = resp.optJSONObject("starred2") ?: JSONObject()
        return Starred(artists(o.arrayOf("artist")), albums(o.arrayOf("album")), songs(o.arrayOf("song")))
    }

    fun songList(resp: JSONObject, container: String): List<Song> = songs(resp.optJSONObject(container)?.arrayOf("song"))

    fun singleSong(resp: JSONObject): Song? = resp.optJSONObject("song")?.let(::song)

    /** getSonicSimilarTracks / findSonicPath: a top-level "sonicMatch" array of {entry, similarity}. */
    fun sonicMatches(resp: JSONObject): List<SonicMatch> {
        val arr = resp.optJSONArray("sonicMatch")
            ?: resp.optJSONObject("sonicMatch")?.optJSONArray("sonicMatch")
            ?: resp.optJSONObject("sonicMatches")?.optJSONArray("sonicMatch")
        return arr.objects().mapNotNull { m ->
            val entry = m.optJSONObject("entry") ?: m.optJSONObject("song") ?: return@mapNotNull null
            val s = song(entry)
            if (s.id.isEmpty()) null else SonicMatch(s, m.optDouble("similarity", 0.0))
        }
    }

    fun serverInfo(ping: JSONObject, extensions: JSONObject?): ServerInfo {
        val names = extensions?.optJSONArray("openSubsonicExtensions").objects().mapNotNull { it.str("name") }.toSet()
        return ServerInfo(
            serverType = ping.str("type"),
            serverVersion = ping.str("serverVersion"),
            openSubsonic = ping.optBoolean("openSubsonic", false),
            sonicSimilarity = "sonicSimilarity" in names,
            lyrics = "songLyrics" in names,
        )
    }

    fun genres(resp: JSONObject): List<Genre> =
        resp.optJSONObject("genres")?.arrayOf("genre").objects().mapNotNull { g ->
            val name = g.str("value") ?: g.str("name") ?: return@mapNotNull null
            Genre(name.trim(), g.optInt("songCount"), g.optInt("albumCount"))
        }.filter { it.name.isNotEmpty() }

    /**
     * OpenSubsonic getLyricsBySongId → lyricsList.structuredLyrics[]. Synced lyrics are preferred.
     * Their "offset" (ms) is applied: a positive offset shows lines sooner, a negative one later.
     */
    fun lyrics(resp: JSONObject): Lyrics? {
        val all = resp.optJSONObject("lyricsList")?.arrayOf("structuredLyrics").objects().map { l ->
            val offset = l.optLong("offset", 0L)
            val synced = l.optBoolean("synced", false)
            val lines = l.arrayOf("line").objects().map { line ->
                val start = if (synced && line.has("start")) (line.optLong("start") - offset).coerceAtLeast(0L) else null
                LyricLine(start, line.optString("value", "").trim())
            }
            Lyrics(synced, lines)
        }.filter { l -> l.lines.any { it.text.isNotEmpty() } }
        return all.firstOrNull { it.synced } ?: all.firstOrNull()
    }

    /** AudioMuse-AI POST /api/clap/search → {"results": [{item_id, title, author, similarity}]} */
    fun moodHits(body: String): List<MoodHit> {
        val o = JSONObject(body)
        return o.optJSONArray("results").objects().mapNotNull { r ->
            val id = r.str("item_id") ?: return@mapNotNull null
            MoodHit(id, r.str("title") ?: "", r.str("author") ?: r.str("artist") ?: "", r.optDouble("similarity", 0.0))
        }
    }

    // --- helpers ---------------------------------------------------------------------------

    /** A string field, or null when it's missing, JSON null or empty. */
    fun JSONObject.str(key: String): String? =
        if (!has(key) || isNull(key)) null else opt(key)?.toString()?.takeIf { it.isNotEmpty() }

    /** Some servers send a lone object where a list is expected; treat both as a list. */
    fun JSONObject.arrayOf(key: String): JSONArray? {
        optJSONArray(key)?.let { return it }
        val single = optJSONObject(key) ?: return null
        return JSONArray().put(single)
    }

    fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
