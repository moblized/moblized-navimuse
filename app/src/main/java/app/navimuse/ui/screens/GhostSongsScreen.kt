package app.navimuse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.LinearProgressIndicator
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
import app.navimuse.data.GhostSongs
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.components.CardTone
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.FloatingTopBar
import app.navimuse.ui.components.InfoCard
import app.navimuse.ui.icons.Icon

/**
 * Ghost songs (see GhostSongs): songs Navidrome lists but can't play. Look through the library
 * for them, try them again, or take one off the list if you know its file is back.
 */
@Composable
fun GhostSongsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ghosts by GhostSongs.all.collectAsState()
    val scan by vm.ghostScan.collectAsState()
    val list = ghosts.values.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() }))
    val scanning = scan?.done == false
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("Ghost songs", solid = true, onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                InfoCard(
                    title = "Songs that won't play",
                    body = "Navidrome still lists these, but their files were moved, renamed or deleted since its last scan (or it can't read them). " +
                        "NaviMuse leaves them out of mixes, radio and queues. A full scan in Navidrome clears them for good; nothing is deleted from here.",
                    icon = Icons.Filled.CloudOff,
                    tone = CardTone.WARNING,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (scanning) {
                        ActionPill("Stop", Icons.Filled.Stop, Modifier.weight(1f)) { vm.stopLookingForGhosts() }
                    } else {
                        ActionPill("Look for them", Icons.Filled.Search, Modifier.weight(1f), primary = true) { vm.lookForGhosts() }
                    }
                    if (list.isNotEmpty() && !scanning) {
                        ActionPill("Check again", Icons.Filled.Refresh, Modifier.weight(1f)) { vm.recheckGhosts() }
                    }
                }
            }
            scan?.let { s ->
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            when {
                                !s.done -> "Looking through your library: ${s.checked} songs checked, ${s.found} ghost${if (s.found == 1) "" else "s"} so far…"
                                s.error != null -> "${s.error} ${s.checked} songs checked, ${s.found} ghost${if (s.found == 1) "" else "s"} found."
                                else -> "Checked all ${s.checked} songs: ${if (s.found == 0) "no ghosts" else "${s.found} ghost${if (s.found == 1) "" else "s"}"}."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!s.done) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
            if (list.isEmpty() && !scanning) {
                item { EmptyBox("No ghost songs. Tap Look for them to check your whole library.") }
            }
            items(list, key = { it.id }) { g ->
                ListItem(
                    headlineContent = { Text(g.title) },
                    supportingContent = {
                        Column {
                            Text(listOf(g.artist, g.album).filter { it.isNotBlank() }.joinToString(" · "))
                            Text(g.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingContent = { Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { TextButton(onClick = { GhostSongs.clear(g.id) }) { Text("Not a ghost") } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
