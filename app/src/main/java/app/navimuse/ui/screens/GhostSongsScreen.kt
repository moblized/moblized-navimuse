package app.navimuse.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.navimuse.data.GhostSongs
import app.navimuse.trash.TrashMover
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.components.CardTone
import app.navimuse.ui.components.DeleteGhostsDialog
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.FloatingTopBar
import app.navimuse.ui.components.InfoCard

/**
 * Ghost songs (see GhostSongs): songs Navidrome lists but can't play. Look through the library
 * for them, try them again, take one off the list if you know its file is back, or pick some (or
 * all) and delete them.
 */
@Composable
fun GhostSongsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val allGhosts by GhostSongs.all.collectAsState()
    // Songs waiting to move to the Trash are on the Trash page instead; deleted ones are gone.
    val waiting by vm.trashQueue.collectAsState()
    val ghosts = GhostSongs.listed(allGhosts, waiting.map { it.songId }.toSet())
    val scan by vm.ghostScan.collectAsState()
    val removal by vm.ghostRemoval.collectAsState()
    val list = ghosts.values.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() }))
    val scanning = scan?.done == false
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val selected = picked.filterTo(HashSet()) { it in ghosts }
    var confirm by remember { mutableStateOf<List<String>?>(null) }
    confirm?.let { ids ->
        DeleteGhostsDialog(
            count = ids.size,
            onDismiss = { confirm = null },
            onConfirm = {
                vm.deleteGhosts(ids)
                picked = emptySet()
                confirm = null
            },
        )
    }
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("Ghost songs", solid = true, onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                InfoCard(
                    title = "Songs that won't play",
                    body = "Navidrome still lists these, but can't open their files: they were moved, renamed or deleted since its last scan " +
                        "(so its Missing Files page doesn't show them yet), or it can't read them. NaviMuse leaves them out of mixes, radio and queues. " +
                        "Delete asks Navidrome for a quick scan to drop the ones with no file left, and moves a file that's still there but won't play to the Trash.",
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
            if (list.isNotEmpty() && !scanning && removal == null) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (selected.isEmpty()) {
                            ActionPill("Select all", Icons.Filled.DoneAll, Modifier.weight(1f)) { picked = ghosts.keys }
                            ActionPill("Delete all (${list.size})", Icons.Filled.Delete, Modifier.weight(1f)) { confirm = list.map { it.id } }
                        } else {
                            ActionPill("Clear (${selected.size})", Icons.Filled.Close, Modifier.weight(1f)) { picked = emptySet() }
                            ActionPill("Delete (${selected.size})", Icons.Filled.Delete, Modifier.weight(1f), primary = true) {
                                confirm = selected.toList()
                            }
                        }
                    }
                }
            }
            removal?.let { r -> item { GhostRemovalProgress(r) } }
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
                val on = g.id in selected
                ListItem(
                    modifier = Modifier.clickable(enabled = removal == null) { picked = if (on) selected - g.id else selected + g.id },
                    headlineContent = { Text(g.title) },
                    supportingContent = {
                        Column {
                            Text(listOf(g.artist, g.album).filter { it.isNotBlank() }.joinToString(" · "))
                            Text(g.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingContent = {
                        Checkbox(
                            checked = on,
                            onCheckedChange = { picked = if (it) selected + g.id else selected - g.id },
                            enabled = removal == null,
                        )
                    },
                    trailingContent = { TextButton(onClick = { GhostSongs.clear(g.id) }) { Text("Not a ghost") } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

/** How deleting ghost songs is going (it can take a while: Navidrome scans first). */
@Composable
fun GhostRemovalProgress(r: TrashMover.GhostRemoval) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            if (r.total > 0) "${r.step} ${r.done} of ${r.total}" else r.step,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (r.total > 0) {
            LinearProgressIndicator(progress = { r.done.toFloat() / r.total }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}
