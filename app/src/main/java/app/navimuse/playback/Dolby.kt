package app.navimuse.playback

import android.content.Context
import android.content.SharedPreferences
import app.navimuse.net.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Dolby Atmos and other Dolby or surround music: spotting it, naming it, and remembering it.
 *
 * Such songs must reach the phone untouched. If the server converts them to save data, it
 * mixes them down to plain stereo and the Atmos is gone. Navidrome can't tell an Atmos .m4a
 * from an ordinary AAC or ALAC one, so the app looks inside the file itself ([sniff]) and
 * remembers what it finds, per song and per album.
 */
object Dolby {
    const val ATMOS = "Dolby Atmos"
    const val DIGITAL_PLUS = "Dolby Digital Plus"
    const val DIGITAL = "Dolby Digital"
    const val TRUEHD = "Dolby TrueHD"
    const val AC4 = "Dolby AC-4"

    private val dolbySuffixes = setOf("ac3", "ec3", "eac3", "ac4", "mlp", "thd", "truehd")

    /** Containers that may hold Dolby audio but look like any other file from the outside. */
    private val sniffableSuffixes = setOf("m4a", "mp4", "mka", "mkv")

    // --- naming ------------------------------------------------------------------------------

    /** A label for audio the player is actually decoding, from its MIME type and channels. */
    fun fromMime(mime: String?, channels: Int): String? = when (mime) {
        "audio/eac3-joc" -> ATMOS
        "audio/eac3" -> DIGITAL_PLUS
        "audio/ac3" -> DIGITAL
        "audio/true-hd" -> TRUEHD
        "audio/ac4" -> AC4
        else -> surround(channels)
    }

    fun surround(channels: Int): String? = when {
        channels >= 8 -> "7.1 surround"
        channels >= 6 -> "5.1 surround"
        channels > 2 -> "Surround"
        else -> null
    }

    /** What the server's own details give away: a Dolby file type, or more than two channels. */
    fun hint(song: Song): String? {
        val suffix = song.suffix?.lowercase()
        return when {
            suffix == "ac3" -> DIGITAL
            suffix == "ec3" || suffix == "eac3" -> DIGITAL_PLUS
            suffix == "ac4" -> AC4
            suffix != null && suffix in dolbySuffixes -> TRUEHD
            else -> surround(song.channels)
        }
    }

    fun canSniff(song: Song): Boolean = song.suffix?.lowercase() in sniffableSuffixes

    // --- looking inside files ----------------------------------------------------------------

    /**
     * The Dolby format inside the first (or last) part of an MP4/M4A or Matroska file, or null
     * when there's no Dolby audio in it.
     */
    fun sniff(bytes: ByteArray): String? {
        if (indexOf(bytes, "ec-3") >= 0) {
            val dec3 = indexOf(bytes, "dec3")
            return if (dec3 >= 4 && isJoc(bytes, dec3)) ATMOS else DIGITAL_PLUS
        }
        if (indexOf(bytes, "ac-4") >= 0) return AC4
        if (indexOf(bytes, "ac-3") >= 0) return DIGITAL
        // Matroska codec IDs
        if (indexOf(bytes, "A_EAC3") >= 0) return DIGITAL_PLUS
        if (indexOf(bytes, "A_AC3") >= 0) return DIGITAL
        if (indexOf(bytes, "A_TRUEHD") >= 0) return TRUEHD
        return null
    }

    /** An MP4 whose details ("moov") weren't in the first part read, so they're at the end. */
    fun needsTail(head: ByteArray): Boolean =
        indexOf(head, "ftyp") in 0..64 && indexOf(head, "moov") < 0

    /**
     * Reads the E-AC-3 settings box ("dec3", ETSI TS 102 366 Annex F). After the substream
     * descriptions, a set flag_ec3_extension_type_a means Joint Object Coding: Dolby Atmos.
     */
    private fun isJoc(bytes: ByteArray, typeAt: Int): Boolean {
        val size = readInt(bytes, typeAt - 4)
        val start = typeAt + 4
        val end = typeAt - 4 + size
        if (size < 8 || end > bytes.size || start >= end) return false
        val bits = Bits(bytes, start, end)
        bits.skip(13) // data_rate
        val substreams = bits.read(3) + 1
        repeat(substreams) {
            bits.skip(16) // fscod, bsid, reserved, asvc, bsmod, acmod, lfeon
            bits.skip(3) // reserved
            val dependents = bits.read(4)
            bits.skip(if (dependents > 0) 9 else 1) // chan_loc or reserved
        }
        if (bits.left() < 8) return false
        bits.skip(7)
        return bits.read(1) == 1
    }

    private class Bits(private val data: ByteArray, start: Int, private val end: Int) {
        private var pos = start * 8L
        fun left(): Long = end * 8L - pos
        fun skip(n: Int) {
            pos += n
        }
        fun read(n: Int): Int {
            var v = 0
            repeat(n) {
                val byte = (pos / 8).toInt()
                val bit = if (byte < end) (data[byte].toInt() shr (7 - (pos % 8).toInt())) and 1 else 0
                v = (v shl 1) or bit
                pos++
            }
            return v
        }
    }

    private fun readInt(b: ByteArray, at: Int): Int =
        if (at < 0 || at + 4 > b.size) 0
        else ((b[at].toInt() and 0xff) shl 24) or ((b[at + 1].toInt() and 0xff) shl 16) or
            ((b[at + 2].toInt() and 0xff) shl 8) or (b[at + 3].toInt() and 0xff)

    private fun indexOf(b: ByteArray, text: String): Int {
        val t = text.toByteArray(Charsets.US_ASCII)
        outer@ for (i in 0..b.size - t.size) {
            for (j in t.indices) if (b[i + j] != t[j]) continue@outer
            return i
        }
        return -1
    }

    // --- remembering -------------------------------------------------------------------------

    private var prefs: SharedPreferences? = null

    /** "s:<songId>" / "a:<albumId>" → label; an empty label means "checked: not Dolby". */
    private val _known = MutableStateFlow<Map<String, String>>(emptyMap())
    val known: StateFlow<Map<String, String>> = _known.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("navimuse_dolby", Context.MODE_PRIVATE)
        prefs = p
        _known.value = p.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
    }

    private fun put(key: String, label: String) {
        if (_known.value[key] == label) return
        _known.update { it + (key to label) }
        prefs?.edit()?.putString(key, label)?.apply()
    }

    /** The Dolby format of a song, from what's been seen before or the server's details. */
    fun labelFor(song: Song): String? =
        _known.value["s:${song.id}"]?.ifEmpty { null }
            ?: song.albumId?.let { _known.value["a:$it"] }?.ifEmpty { null }
            ?: hint(song)

    fun albumLabel(albumId: String): String? = _known.value["a:$albumId"]?.ifEmpty { null }

    fun albumChecked(albumId: String): Boolean = _known.value.containsKey("a:$albumId")

    fun rememberAlbum(albumId: String, label: String?) = put("a:$albumId", label.orEmpty())

    /** The player found Dolby audio in this song: stream it (and its album) untouched from now on. */
    fun learn(song: Song, label: String) {
        put("s:${song.id}", label)
        song.albumId?.let { id ->
            // Atmos wins over a plainer label seen on another track of the same album.
            if (albumLabel(id) != ATMOS) put("a:$id", label)
        }
    }

    fun clear() {
        _known.value = emptyMap()
        prefs?.edit()?.clear()?.apply()
    }
}
