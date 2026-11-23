package app.navimuse.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.navimuse.data.DeletedPlaylists
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.components.CardTone
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.FloatingTopBar
import app.navimuse.ui.components.InfoCard
import app.navimuse.ui.icons.Icon
import kotlin.math.ceil

/** Playlists you deleted in the last 30 days, to restore (see DeletedPlaylists). */
@Composable
fun DeletedPlaylistsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val deleted by vm.deletedPlaylists.collectAsState()
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("Recently deleted", solid = true, onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                InfoCard(
                    title = "Deleted playlists",
                    body = "NaviMuse keeps the name and songs of each playlist you delete for ${DeletedPlaylists.KEEP_DAYS} days. " +
                        "Restore makes it again in Navidrome, with the same songs in the same order.",
                    icon = Icons.Filled.Restore,
                    tone = CardTone.NEUTRAL,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (deleted.isEmpty()) item { EmptyBox("No deleted playlists.") }
            items(deleted, key = { it.playlistId }) { e ->
                val daysLeft = ceil((e.restoreUntil - System.currentTimeMillis()) / 86_400_000.0).toInt().coerceAtLeast(0)
                ListItem(
                    headlineContent = { Text(e.name) },
                    supportingContent = {
                        Text(
                            "${e.songIds.size} tracks · ${if (daysLeft == 1) "1 day" else "$daysLeft days"} left to restore",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { TextButton(onClick = { vm.restorePlaylist(e) }) { Text("Restore") } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
