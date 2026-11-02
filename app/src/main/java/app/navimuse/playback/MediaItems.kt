package app.navimuse.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.navimuse.downloads.Downloads
import app.navimuse.net.Album
import app.navimuse.net.Playlist
import app.navimuse.net.Song
import app.navimuse.net.SubsonicClient

/**
 * Media ids used by the player and the Android Auto browse tree.
 * Songs: "s|<songId>" or "s|<songId>|<context>" where context is "a|<albumId>",
 * "p|<playlistId>" or "f" (favourites), so a tap in Android Auto can queue the whole list.
 */
object MediaIds {
    const val ROOT = "root"
    const val MIXES = "mixes"
    const val RECENT = "recent"
    const val PLAYLISTS = "playlists"
    const val STARRED = "starred"
    const val MIX_NOW = "mix|now"
    const val SHUFFLE_ALL = "shuffle|all"
    const val MIX_ALBUM_PREFIX = "mix|album|"

    fun song(id: String, context: String? = null) = if (context == null) "s|$id" else "s|$id|$context"
    fun album(id: String) = "a|$id"
    fun playlist(id: String) = "p|$id"

    /** The Navidrome song id inside a song media id. */
    fun songIdOf(mediaId: String): String? =
        if (mediaId.startsWith("s|")) mediaId.removePrefix("s|").substringBefore('|').takeIf { it.isNotEmpty() } else null

    /** The list a song was picked from ("a|…", "p|…" or "f"), if any. */
    fun contextOf(mediaId: String): String? {
        if (!mediaId.startsWith("s|")) return null
        val rest = mediaId.removePrefix("s|")
        val bar = rest.indexOf('|')
        return if (bar < 0) null else rest.substring(bar + 1).takeIf { it.isNotEmpty() }
    }
}

object Extras {
    const val SONG_ID = "songId"
    const val ALBUM_ID = "albumId"
    const val ARTIST_ID = "artistId"
    const val COVER = "coverArt"
    const val DURATION = "duration"
    const val STARRED = "starred"
    const val SUFFIX = "suffix"
    const val CHANNELS = "channels"
    const val BITRATE = "bitRate"
}

/**
 * The cache key the player reads a song under: its download when it has one (so it plays
 * offline), otherwise the streamed copy at this bitrate.
 */
fun streamKey(songId: String, maxBitRateKbps: Int): String =
    if (Downloads.isDownloaded(songId)) Downloads.key(songId) else "$songId@$maxBitRateKbps"

fun Song.toMediaItem(api: SubsonicClient, maxBitRateKbps: Int, context: String? = null): MediaItem {
    val extras = Bundle().apply {
        putString(Extras.SONG_ID, id)
        putString(Extras.ALBUM_ID, albumId)
        putString(Extras.ARTIST_ID, artistId)
        putString(Extras.COVER, coverArt)
        putInt(Extras.DURATION, duration)
        putBoolean(Extras.STARRED, starred)
        putString(Extras.SUFFIX, suffix)
        putInt(Extras.CHANNELS, channels)
        putInt(Extras.BITRATE, bitRate)
    }
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setAlbumArtist(artist)
        .setArtworkUri(api.coverUrl(coverArt)?.let(Uri::parse))
        .setTrackNumber(track.takeIf { it > 0 })
        .setDiscNumber(disc.takeIf { it > 0 })
        .setRecordingYear(year.takeIf { it > 0 })
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(MediaIds.song(id, context))
        .setUri(api.streamUrl(id, maxBitRateKbps))
        .setCustomCacheKey(streamKey(id, maxBitRateKbps))
        .setMediaMetadata(meta)
        .build()
}

/** Rebuilds a song from the metadata the player carries (for the queue and the Now Playing screen). */
fun MediaItem.toSong(): Song? {
    val id = MediaIds.songIdOf(mediaId) ?: mediaMetadata.extras?.getString(Extras.SONG_ID) ?: return null
    val m = mediaMetadata
    val e = m.extras
    return Song(
        id = id,
        title = m.title?.toString() ?: "Unknown song",
        artist = m.artist?.toString() ?: "",
        artistId = e?.getString(Extras.ARTIST_ID),
        album = m.albumTitle?.toString() ?: "",
        albumId = e?.getString(Extras.ALBUM_ID),
        coverArt = e?.getString(Extras.COVER),
        duration = e?.getInt(Extras.DURATION) ?: 0,
        track = m.trackNumber ?: 0,
        disc = m.discNumber ?: 0,
        year = m.recordingYear ?: 0,
        suffix = e?.getString(Extras.SUFFIX),
        bitRate = e?.getInt(Extras.BITRATE) ?: 0,
        starred = e?.getBoolean(Extras.STARRED) ?: false,
        channels = e?.getInt(Extras.CHANNELS) ?: 0,
    )
}

fun browsable(id: String, title: String, subtitle: String? = null, artwork: Uri? = null, type: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(artwork)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(type)
                .build(),
        )
        .build()

/** A tappable action in Android Auto, like "Instant Mix from what's playing". */
fun playableAction(id: String, title: String, subtitle: String? = null, artwork: Uri? = null): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(artwork)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                .build(),
        )
        .build()

fun Album.toBrowsable(api: SubsonicClient): MediaItem =
    browsable(MediaIds.album(id), name, artist, api.coverUrl(coverArt, 300)?.let(Uri::parse), MediaMetadata.MEDIA_TYPE_ALBUM)

fun Playlist.toBrowsable(api: SubsonicClient): MediaItem =
    browsable(MediaIds.playlist(id), name, "$songCount songs", api.coverUrl(coverArt, 300)?.let(Uri::parse), MediaMetadata.MEDIA_TYPE_PLAYLIST)
