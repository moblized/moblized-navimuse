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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.components.FloatingTopBar
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.SectionHeader
import app.navimuse.ui.components.SongRow
import app.navimuse.ui.friendly
import kotlinx.coroutines.CancellationException

private sealed interface ImportState {
    data object Starting : ImportState
    data class Going(val progress: AppViewModel.ImportProgress) : ImportState
    data class Failed(val message: String) : ImportState
}

/**
 * An import under way, then its result: the songs found in your library (to play or save as a
 * new playlist) and, below them, the ones your library doesn't have.
 */
@Composable
fun ImportScreen(vm: AppViewModel, key: String, onBack: () -> Unit) {
    val ui = LocalUi.current
    val player by vm.playerState.collectAsState()
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<ImportState>(ImportState.Starting, key, attempt) {
        value = ImportState.Starting
        try {
            vm.runImport(key) { value = ImportState.Going(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            value = ImportState.Failed(friendly(e))
        }
    }
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("Import", solid = true, onBack = onBack)
        when (val s = state) {
            ImportState.Starting -> Column(Modifier.padding(16.dp)) {
                Text("Reading the playlist…", style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            is ImportState.Failed -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                ActionPill("Try again", Icons.Filled.Refresh) { attempt++ }
            }
            is ImportState.Going -> {
                val p = s.progress
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(p.name, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                if (p.finished) {
                                    "Found ${p.found.size} of ${p.total} songs in your library" +
                                        when (p.missing.size) {
                                            0 -> ""
                                            1 -> "; 1 isn't in it"
                                            else -> "; ${p.missing.size} aren't in it"
                                        } +
                                        when (p.unplayable.size) {
                                            0 -> "."
                                            1 -> "; 1 is in it but won't play."
                                            else -> "; ${p.unplayable.size} are in it but won't play."
                                        }
                                } else {
                                    "Looking for the songs in your library: ${p.done} of ${p.total}…"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!p.finished) {
                                LinearProgressIndicator(
                                    progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total },
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                )
                            }
                        }
                    }
                    if (p.finished && p.found.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ActionPill("Save playlist", Icons.Filled.Save, Modifier.weight(1f), primary = true) {
                                    ui.saveAsPlaylist(p.found, p.name)
                                }
                                ActionPill("Play", Icons.Filled.PlayArrow, Modifier.weight(1f)) {
                                    vm.play(p.found, from = AppViewModel.PlaySource(p.name))
                                }
                            }
                        }
                    }
                    itemsIndexed(p.found, key = { i, song -> "found-$i-${song.id}" }) { i, song ->
                        SongRow(song, onPlay = { vm.play(p.found, i, from = AppViewModel.PlaySource(p.name)) }, isCurrent = player.current?.song?.id == song.id)
                    }
                    if (p.unplayable.isNotEmpty()) {
                        item { SectionHeader("In your library, but won't play (${p.unplayable.size})") }
                        item {
                            Text(
                                "Navidrome lists these but can't open their files, so they're left out. They're on the Ghost songs page now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        items(p.unplayable, key = { "ghost-${it.id}" }) { song ->
                            Text(
                                "${song.title} – ${song.artist}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (p.missing.isNotEmpty()) {
                        item { SectionHeader("Not in your library (${p.missing.size})") }
                        items(p.missing) { w ->
                            Text(
                                w.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
