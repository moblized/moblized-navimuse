package app.navimuse.ui.symfonium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import app.navimuse.net.Song
import app.navimuse.playback.PlayerUiState
import app.navimuse.ui.formatDuration
import app.navimuse.ui.theme.accent

/**
 * The values a Symfonium template can show for the song that's playing. Names follow
 * Symfonium's field list; anything Navidrome doesn't know is left empty.
 */
fun songFields(
    song: Song,
    state: PlayerUiState?,
    positionMs: Long,
    format: String?,
    next: Song? = null,
): (String) -> String? = fields@{ raw ->
    val isNext = raw.startsWith("next.")
    val name = raw.removePrefix("next.")
    val s = if (isNext) next ?: return@fields null else song
    fun time(ms: Long) = formatDuration((ms / 1000).toInt())
    val durationMs = state?.durationMs?.takeIf { it > 0 } ?: (s.duration * 1000L)
    when (name) {
        "title", "title.raw", "cleantitle" -> s.title
        "artist", "albumartist", "original.artist" -> s.artist
        "album", "album.raw", "album.title", "album.title.raw", "original.album" -> s.album
        "year", "releasedate.year", "originaldate.year", "releasedate", "originaldate" -> s.year.takeIf { it > 0 }?.toString()
        "track" -> s.track.takeIf { it > 0 }?.toString()
        "disc" -> s.disc.takeIf { it > 0 }?.toString()
        "genre" -> s.genre
        "duration" -> formatDuration(s.duration)
        "duration.seconds" -> s.duration.toString()
        "favorite" -> s.starred.toString()
        "providername" -> "Navidrome"
        "mediatype" -> "song"
        "isaudiobook" -> "false"
        "format" -> format
        "format.codec" -> s.suffix?.uppercase()
        "format.bitrate", "format.bitrate.raw" -> s.bitRate.takeIf { it > 0 }?.let { "$it kbps" }
        "format.channels" -> s.channels.takeIf { it > 0 }?.toString()
        "player.position" -> if (isNext) null else time(positionMs)
        "player.position.seconds" -> if (isNext) null else (positionMs / 1000).toString()
        "player.duration" -> time(durationMs)
        "player.duration.seconds" -> (durationMs / 1000).toString()
        "player.remaining" -> time((durationMs - positionMs).coerceAtLeast(0))
        "player.remaining.seconds" -> ((durationMs - positionMs).coerceAtLeast(0) / 1000).toString()
        "player.paused" -> (state?.isPlaying != true).toString()
        "player.shuffle.mode" -> if (state?.shuffle == true) "1" else "0"
        "player.repeat.mode" -> state?.repeatMode?.toString()
        "queue.position" -> state?.current?.index?.plus(1)?.toString()
        "queue.size" -> state?.queue?.size?.toString()
        "bool1", "bool2", "bool3", "bool4", "bool5", "smartqueue", "smartflow", "radio.mode" -> "false"
        "string.track.one" -> "track"
        "string.track.other" -> "tracks"
        "string.album.one" -> "album"
        "string.album.other" -> "albums"
        "string.lossless" -> "Lossless"
        "string.lossy" -> "Lossy"
        "string.hires" -> "Hi-Res"
        "string.hires.lossless" -> "Hi-Res Lossless"
        else -> null
    }
}

/**
 * A text line from a Symfonium style. Parts split by `\t` are spread across the width, as in
 * Symfonium. Shows nothing when the template comes out empty.
 */
@Composable
fun StyledLine(line: TextLine, fields: (String) -> String?, base: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val rendered = Template.render(line.template, fields)
    if (rendered.isBlank) return
    val style = remember(base, line) {
        base.copy(
            fontSize = base.fontSize * line.size,
            lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * line.size else base.lineHeight,
            fontWeight = if (line.bold) FontWeight.Bold else base.fontWeight,
        )
    }
    val tint = when {
        color != Color.Unspecified -> color
        line.accent -> MaterialTheme.colorScheme.accent
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(modifier) {
        rendered.lines.take(line.maxLines.coerceAtLeast(1)).forEach { parts ->
            if (parts.size == 1) {
                Text(parts[0], style = style, color = tint, maxLines = line.maxLines, overflow = TextOverflow.Ellipsis)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    parts.forEach { Text(it, style = style, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}
