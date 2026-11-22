package app.navimuse.net

/** Thrown before a request leaves the phone, because it could delete or overwrite something. */
class BlockedRequestException(val request: String, reason: String) : Exception(reason)

/**
 * The one door every request goes through. Anything not on these lists is refused before it's
 * sent, so the app can't delete or overwrite songs, playlists or anything else on your server,
 * even by mistake or from a bug elsewhere in the code.
 */
object SafetyGuard {
    /** Requests that only read. */
    private val readOnly = setOf(
        "ping", "getLicense", "getOpenSubsonicExtensions", "getMusicFolders",
        "getArtists", "getArtist", "getArtistInfo2", "getAlbum", "getAlbumInfo2", "getSong",
        "getAlbumList2", "getRandomSongs", "getGenres", "getSongsByGenre", "search3",
        "getPlaylists", "getPlaylist", "getStarred2", "getSimilarSongs", "getSimilarSongs2",
        "getTopSongs", "getSonicSimilarTracks", "findSonicPath", "getLyricsBySongId",
        "getCoverArt", "stream", "getNowPlaying", "getUser", "getScanStatus",
    )

    /**
     * Requests that only add something or record what you listened to. None of them can remove
     * a song, a file or a playlist. They're still checked in detail below.
     */
    private val additive = setOf("createPlaylist", "updatePlaylist", "star", "unstar", "scrobble", "startScan")

    /** The only parameters [updatePlaylist] may carry: which playlist, and songs to add to it. */
    private val updatePlaylistParams = setOf("playlistId", "songIdToAdd")

    /** Checks a Navidrome (Subsonic API) request. [params] are the request's own parameters, without login details. */
    fun check(method: String, params: List<Pair<String, String>>) {
        val name = method.removeSuffix(".view")
        val keys = params.map { it.first }.toSet()
        when {
            name in readOnly -> Unit
            name !in additive -> throw BlockedRequestException(
                name,
                "\"$name\" isn't on this app's list of safe requests, so it was never sent to your server.",
            )
            name == "createPlaylist" -> {
                // With playlistId, createPlaylist REPLACES the songs of an existing playlist.
                if ("playlistId" in keys) {
                    throw BlockedRequestException(name, "Replacing the songs of an existing playlist is blocked.")
                }
                val title = params.firstOrNull { it.first == "name" }?.second.orEmpty()
                if (title.isBlank()) throw BlockedRequestException(name, "A new playlist needs a name.")
                if (PlaylistNames.isAudioMuseManaged(title)) {
                    throw BlockedRequestException(
                        name,
                        "AudioMuse-AI deletes playlists whose names end in ${PlaylistNames.AUDIOMUSE_SUFFIX}, so the app won't create one.",
                    )
                }
            }
            name == "startScan" -> {
                // A quick scan, after a song is moved to the Trash or put back. Never the full one.
                if (params.any { it.first == "fullScan" && it.second != "false" }) {
                    throw BlockedRequestException(name, "Only a quick scan is allowed.")
                }
            }
            name == "updatePlaylist" -> {
                if ("playlistId" !in keys) throw BlockedRequestException(name, "No playlist given.")
                val extra = keys - updatePlaylistParams
                if (extra.isNotEmpty()) {
                    throw BlockedRequestException(
                        name,
                        "Only adding songs to a playlist is allowed (blocked: ${extra.sorted().joinToString()}).",
                    )
                }
            }
        }
    }

    /**
     * Requests to Navidrome's own (non-Subsonic) API: signing in, and reading one song's details
     * to learn where its file really is. Nothing that changes anything.
     */
    fun checkNavidromeNative(httpMethod: String, path: String) {
        val clean = "/" + path.substringBefore('?').trim('/')
        val m = httpMethod.uppercase()
        val ok = (m == "POST" && clean.endsWith("/auth/login")) ||
            (m == "GET" && Regex(".*/api/song/[A-Za-z0-9_-]+$").matches(clean))
        if (!ok) throw BlockedRequestException("$m $clean", "Navidrome request \"$m $clean\" isn't allowed, so it was never sent.")
    }

    /** The music share's Trash folder. A file only ever moves into it, or back out to where it was. */
    const val TRASH = "_Trash"

    /**
     * Checks a file move on your music share (paths relative to the music folder, "/" between
     * folders). The only moves allowed are a song into the Trash at the same place inside it, and
     * back out again: nothing is ever deleted, overwritten or moved anywhere else.
     */
    fun checkMove(from: String, to: String) {
        fun parts(p: String) = p.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        val a = parts(from)
        val b = parts(to)
        val bad = (a + b).any { it == ".." || it == "." } || a.isEmpty() || b.isEmpty()
        val intoTrash = a.firstOrNull() != TRASH && b.firstOrNull() == TRASH && b.drop(1) == a
        val outOfTrash = a.firstOrNull() == TRASH && b.firstOrNull() != TRASH && a.drop(1) == b
        if (bad || !(intoTrash || outOfTrash)) {
            throw BlockedRequestException("move", "Moving \"$from\" to \"$to\" isn't allowed: songs only go into the Trash and back.")
        }
    }

    /**
     * Requests the app may send straight to AudioMuse-AI. Its API token has admin rights there
     * (it can start cleaning, re-clustering or delete things), so only searches are allowed.
     */
    private val audioMuseAllowed = setOf(
        "GET /api/search_tracks",
        "POST /api/clap/search",
    )

    fun checkAudioMuse(httpMethod: String, path: String) {
        val clean = "/" + path.substringBefore('?').trim('/')
        val key = "${httpMethod.uppercase()} $clean"
        if (key !in audioMuseAllowed) {
            throw BlockedRequestException(key, "AudioMuse-AI request \"$key\" isn't allowed, so it was never sent.")
        }
    }
}

/** Playlist naming rules that keep your playlists out of AudioMuse-AI's clean-up. */
object PlaylistNames {
    /**
     * Every time AudioMuse-AI makes its clustering playlists, it first deletes every playlist
     * whose name ends in this. (It checks case-sensitively; the app is stricter and ignores case.)
     */
    const val AUDIOMUSE_SUFFIX = "_automatic"

    fun isAudioMuseManaged(name: String): Boolean =
        name.trim().endsWith(AUDIOMUSE_SUFFIX, ignoreCase = true)

    /** [name] with any _automatic ending removed, so AudioMuse-AI will never delete it. */
    fun safe(name: String, fallback: String = "My mix"): String {
        var n = name.trim()
        while (isAudioMuseManaged(n)) n = n.dropLast(AUDIOMUSE_SUFFIX.length).trim()
        n = n.trimEnd('_', ' ', '-')
        return n.ifBlank { fallback }
    }

    /** A readable name for an AudioMuse-AI playlist, e.g. "Rock_Fast_automatic" → "Rock Fast". */
    fun pretty(name: String): String =
        safe(name, fallback = "AudioMuse mix").replace('_', ' ').replace(Regex("\\s+"), " ").trim()

    /** The name for a kept copy of an AudioMuse-AI playlist that doesn't clash with [existing] names. */
    fun keptCopyName(automaticName: String, existing: Collection<String>): String {
        val base = "${pretty(automaticName)} (kept)"
        val taken = existing.map { it.trim().lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        var i = 2
        while ("${pretty(automaticName)} (kept $i)".lowercase() in taken) i++
        return "${pretty(automaticName)} (kept $i)"
    }
}
