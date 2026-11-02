package app.navimuse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navimuse.net.Song
import app.navimuse.playback.DecodedFormat
import app.navimuse.playback.Dolby
import app.navimuse.ui.icons.Icon
import java.util.Locale

/**
 * The audio details shown under the seek bar, like "MP4/EAC3 • 768 kb/s • 48.0 kHz":
 * file type and codec, bitrate, bit depth (for lossless) and sample rate. When the server is
 * converting the song, it describes what's actually playing and what it was made from.
 */
object AudioDetails {
    private val containers = mapOf(
        "m4a" to "MP4", "mp4" to "MP4", "m4b" to "MP4", "alac" to "MP4", "aac" to "AAC",
        "mka" to "MKA", "mkv" to "MKV", "webm" to "WEBM", "ogg" to "OGG", "oga" to "OGG", "opus" to "OPUS",
        "flac" to "FLAC", "mp3" to "MP3", "wav" to "WAV", "aif" to "AIFF", "aiff" to "AIFF", "dsf" to "DSF",
        "wv" to "WV", "ape" to "APE", "wma" to "WMA", "ec3" to "EAC3", "eac3" to "EAC3", "ac3" to "AC3",
    )

    private val codecs = mapOf(
        "audio/eac3-joc" to "EAC3", "audio/eac3" to "EAC3", "audio/ac3" to "AC3", "audio/ac4" to "AC4",
        "audio/true-hd" to "TRUEHD", "audio/flac" to "FLAC", "audio/mpeg" to "MP3", "audio/mp4a-latm" to "AAC",
        "audio/alac" to "ALAC", "audio/opus" to "OPUS", "audio/vorbis" to "VORBIS", "audio/raw" to "PCM",
        "audio/x-wav" to "PCM", "audio/vnd.dts" to "DTS", "audio/vnd.dts.hd" to "DTS-HD",
    )

    private val lossless = setOf("FLAC", "ALAC", "PCM", "WAV", "AIFF", "TRUEHD", "APE", "WV", "DSF")

    /** "MP4/EAC3", "FLAC", "OPUS"… */
    fun type(song: Song, decoded: DecodedFormat?, converted: Boolean): String? {
        val codec = decoded?.mime?.let { codecs[it] ?: it.substringAfter('/').uppercase() }
        if (converted) return codec ?: "Converted"
        val container = song.suffix?.lowercase()?.let { containers[it] ?: it.uppercase() }
        return when {
            container == null -> codec
            codec == null || codec == container -> container
            // An MP4/M4A of ALAC or AAC reads better as just the codec.
            container == "MP4" && codec in setOf("AAC", "ALAC") -> codec
            else -> "$container/$codec"
        }
    }

    fun khz(hz: Int): String = String.format(Locale.US, "%.1f kHz", hz / 1000.0)

    /**
     * The line under the seek bar. [streamKbps] is what the server was asked to convert to
     * (0 = the original file).
     */
    fun line(song: Song, decoded: DecodedFormat?, streamKbps: Int): String? {
        val converted = streamKbps > 0 && (song.bitRate == 0 || song.bitRate > streamKbps)
        val type = type(song, decoded, converted)
        val kbps = if (converted) streamKbps else song.bitRate.takeIf { it > 0 } ?: decoded?.bitrate?.takeIf { it > 0 }?.div(1000)
        val hz = decoded?.sampleRate?.takeIf { it > 0 } ?: song.samplingRate.takeIf { it > 0 }
        val bits = song.bitDepth.takeIf { it > 0 && !converted && (type?.substringAfter('/') in lossless || type in lossless) }
        val parts = listOfNotNull(
            type,
            kbps?.let { "${"%,d".format(Locale.US, it)} kb/s" },
            bits?.let { "$it-bit" },
            hz?.let { khz(it) },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
    }

    /** Every detail, for the ⓘ dialog. */
    fun details(song: Song, decoded: DecodedFormat?, streamKbps: Int, dolby: String?): List<Pair<String, String>> {
        val converted = streamKbps > 0 && (song.bitRate == 0 || song.bitRate > streamKbps)
        val fileType = song.suffix?.uppercase()
        return listOfNotNull(
            "Playing" to if (converted) "Converted by Navidrome to ${type(song, decoded, true) ?: "a smaller file"} at $streamKbps kb/s" else "The original file",
            fileType?.let { "File" to it },
            decoded?.mime?.let { m -> "Codec" to (codecs[m] ?: m.substringAfter('/').uppercase()) },
            dolby?.let { "Format" to it },
            song.bitRate.takeIf { it > 0 }?.let { "File bitrate" to "${"%,d".format(Locale.US, it)} kb/s" },
            (decoded?.sampleRate?.takeIf { it > 0 } ?: song.samplingRate.takeIf { it > 0 })?.let { "Sample rate" to khz(it) },
            song.bitDepth.takeIf { it > 0 }?.let { "Bit depth" to "$it-bit" },
            (decoded?.channels?.takeIf { it > 0 } ?: song.channels.takeIf { it > 0 })?.let { "Channels" to channels(it) },
            song.size.takeIf { it > 0 }?.let { "File size" to String.format(Locale.US, "%.1f MB", it / 1_048_576.0) },
        )
    }

    private fun channels(n: Int) = when (n) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$n channels"
    }
}

/** "ⓘ MP4/EAC3 • 768 kb/s • 48.0 kHz"; tap it for every detail. */
@Composable
fun FormatLine(song: Song, decoded: DecodedFormat?, streamKbps: Int, modifier: Modifier = Modifier) {
    val line = AudioDetails.line(song, decoded, streamKbps) ?: return
    var open by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).clickable { open = true }.padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Info, "Audio details", tint = muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(line, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (open) {
        val dolby = Dolby.fromMime(decoded?.mime, decoded?.channels ?: 0) ?: Dolby.labelFor(song)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Audio details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioDetails.details(song, decoded, streamKbps, dolby).forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(k, style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.width(110.dp))
                            Text(v, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}
