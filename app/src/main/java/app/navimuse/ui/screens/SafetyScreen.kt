package app.navimuse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.components.CardTone
import app.navimuse.ui.components.InfoCard
import app.navimuse.ui.components.SectionHeader
import java.text.DateFormat
import java.util.Date

private val neverDoes = listOf(
    "Delete songs, albums or files",
    "Delete or rename playlists",
    "Replace the songs in an existing playlist",
    "Remove songs from a playlist",
    "Make playlists ending in _automatic (AudioMuse-AI deletes those)",
    "Start a library scan, or change users, shares or radio stations",
    "Ask AudioMuse-AI to clean up, re-cluster or delete anything (only searches are sent)",
)

private val canDo = listOf(
    "Stream and browse your library",
    "Make new playlists and add songs to the end of your own",
    "Add and remove favourites (hearts)",
    "Report what you play, so play counts stay right",
)

@Composable
fun SafetyScreen(vm: AppViewModel, onBack: () -> Unit) {
    val blocked by vm.blocked.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Safety") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                InfoCard(
                    title = "Every request is checked before it's sent",
                    body = "NaviMuse only knows a short list of safe requests. Anything else, including every kind of delete, is refused on your phone and never reaches the server.",
                    icon = Icons.Filled.Shield,
                    tone = CardTone.GOOD,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item { SectionHeader("This app never") }
            items(neverDoes) { Bullet(it, Icons.Filled.Block, MaterialTheme.colorScheme.error) }
            item { SectionHeader("It only") }
            items(canDo) { Bullet(it, Icons.Filled.CheckCircle, MaterialTheme.colorScheme.secondary) }

            item { SectionHeader("Keep AudioMuse-AI away from your files") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoCard(
                        title = "AudioMuse-AI empties its temp folder on every analysis",
                        body = "It downloads each song through Navidrome into /app/temp_audio, analyses it, and deletes the copy. At the start and end of every analysis run it also clears that whole folder. " +
                            "If your music is mounted there, or TEMP_DIR points at your music, your songs get deleted.",
                        icon = Icons.Filled.Warning,
                        tone = CardTone.WARNING,
                    )
                    Text("In your AudioMuse-AI docker-compose (flask and worker services):", style = MaterialTheme.typography.bodyMedium)
                    Code(
                        "environment:\n" +
                            "  TEMP_DIR: \"/app/temp_audio\"\n" +
                            "volumes:\n" +
                            "  - temp-audio-worker:/app/temp_audio   # its own volume\n" +
                            "  # no music folder here: with Navidrome,\n" +
                            "  # AudioMuse streams songs over the API",
                    )
                    Text(
                        "If you do need your music inside the AudioMuse-AI containers, mount it read-only by adding :ro, for example /mnt/tank/music:/music:ro. " +
                            "On TrueNAS, also give the AudioMuse-AI app read-only access to the music dataset.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Navidrome itself only needs read access too: /mnt/tank/music:/music:ro.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { SectionHeader("Blocked this session") }
            if (blocked.isEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Nothing needed blocking.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                items(blocked) { b ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Dns, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(6.dp))
                            Text(b.request, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.weight(1f))
                            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(b.atMillis)), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(b.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bullet(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Code(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal, modifier = Modifier.padding(12.dp))
    }
}
