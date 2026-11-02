package app.navimuse.net

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String?,
    val album: String,
    val albumId: String?,
    val coverArt: String?,
    /** Seconds. */
    val duration: Int,
    val track: Int,
    val disc: Int,
    val year: Int,
    val suffix: String?,
    val bitRate: Int,
    val starred: Boolean,
    /** Audio channels, when the server reports them (OpenSubsonic). More than 2 means surround. */
    val channels: Int = 0,
    val genre: String? = null,
    /** Hz, when the server reports it (OpenSubsonic). */
    val samplingRate: Int = 0,
    val bitDepth: Int = 0,
    /** File size in bytes. */
    val size: Long = 0,
)

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int,
    val duration: Int,
    val year: Int,
    val genre: String?,
    val starred: Boolean,
)

data class Artist(
    val id: String,
    val name: String,
    val coverArt: String?,
    val albumCount: Int,
    val imageUrl: String?,
    val starred: Boolean,
)

data class Playlist(
    val id: String,
    val name: String,
    val songCount: Int,
    val duration: Int,
    val owner: String?,
    val public: Boolean,
    val coverArt: String?,
    val comment: String?,
) {
    /** AudioMuse-AI deletes and remakes this playlist every time it runs clustering. */
    val isAudioMuseManaged: Boolean get() = PlaylistNames.isAudioMuseManaged(name)
}

data class AlbumDetail(val album: Album, val songs: List<Song>)
data class ArtistDetail(val artist: Artist, val albums: List<Album>)
data class ArtistInfo(val biography: String?, val imageUrl: String?, val similar: List<Artist>)
data class PlaylistDetail(val playlist: Playlist, val songs: List<Song>)
data class SearchResult(val artists: List<Artist>, val albums: List<Album>, val songs: List<Song>) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && songs.isEmpty()
}
data class Starred(val artists: List<Artist>, val albums: List<Album>, val songs: List<Song>)

data class Genre(val name: String, val songCount: Int, val albumCount: Int)

/** One line of lyrics; [startMs] is set when the lyrics are synced to the song. */
data class LyricLine(val startMs: Long?, val text: String)

data class Lyrics(val synced: Boolean, val lines: List<LyricLine>)

/** A song with how close it sounds to another one (1.0 = the same song). */
data class SonicMatch(val song: Song, val similarity: Double)

/** One result of an AudioMuse-AI mood search; [itemId] is the Navidrome song id. */
data class MoodHit(val itemId: String, val title: String, val artist: String, val similarity: Double)

/** What the server can do, read once after logging in. */
data class ServerInfo(
    val serverType: String?,
    val serverVersion: String?,
    val openSubsonic: Boolean,
    /** The AudioMuse-AI plugin is active in Navidrome (sonic similarity + song paths). */
    val sonicSimilarity: Boolean,
    val lyrics: Boolean,
)

enum class AlbumListType(val apiValue: String, val label: String) {
    NEWEST("newest", "Recently added"),
    RECENT("recent", "Recently played"),
    FREQUENT("frequent", "Most played"),
    RANDOM("random", "Random"),
    ALPHABETICAL("alphabeticalByName", "A to Z"),
    BY_ARTIST("alphabeticalByArtist", "By artist"),
    STARRED("starred", "Favourites"),
}

class SubsonicException(val code: Int, message: String) : Exception(message)
