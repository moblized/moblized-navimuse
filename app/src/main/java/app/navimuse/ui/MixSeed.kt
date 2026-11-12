package app.navimuse.ui

import app.navimuse.net.Album
import app.navimuse.net.Song

/** What an Instant Mix starts from. Opening one shows the mix; nothing plays until you press Play. */
sealed interface MixSeed {
    /** Same seed, same mix (until "New mix"). */
    val key: String

    data class FromSong(val song: Song) : MixSeed {
        override val key get() = "song:${song.id}"
    }

    data class FromAlbum(val album: Album) : MixSeed {
        override val key get() = "album:${album.id}"
    }

    /** A mix from a random song in the library; [made] keeps each surprise its own. */
    data class Surprise(val made: Long = System.currentTimeMillis()) : MixSeed {
        override val key get() = "surprise:$made"
    }
}

/** A made Instant Mix: the song it grew from first, then songs that sound like it. */
data class Mix(val seed: MixSeed, val first: Song, val songs: List<Song>) {
    /** "Instant Mix: White Flag", also the name it's saved under. */
    val name: String
        get() = "Instant Mix: " + when (seed) {
            is MixSeed.FromAlbum -> seed.album.name
            else -> first.title
        }

    /** "Based on White Flag by Fall Of Envy". */
    val basedOn: String
        get() = when (seed) {
            is MixSeed.FromAlbum -> "Based on ${seed.album.name} by ${seed.album.artist}"
            is MixSeed.Surprise -> "A surprise, based on ${first.title} by ${first.artist}"
            is MixSeed.FromSong -> "Based on ${first.title} by ${first.artist}"
        }

    /** False when nothing similar came back, so there's only the first song. */
    val found: Boolean get() = songs.size > 1
}
