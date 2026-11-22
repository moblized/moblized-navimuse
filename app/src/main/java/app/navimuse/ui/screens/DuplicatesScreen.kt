package app.navimuse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navimuse.data.Duplicates
import app.navimuse.data.GhostSongs
import app.navimuse.net.Song
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.components.CardTone
import app.navimuse.ui.components.Cover
import app.navimuse.ui.components.DeleteGhostsDialog
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.FloatingTopBar
import app.navimuse.ui.components.InfoCard
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.formatDuration
import app.navimuse.ui.icons.Icon
import app.navimuse.ui.theme.accent

/**
 * The same song more than once in the library (see Duplicates), grouped, with the copy worth
 * keeping marked. Each other copy can be played to compare, or moved to the Trash on the NAS.
 */
@Composable
fun DuplicatesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ui = LocalUi.current
    val scan by vm.duplicates.collectAsState()
    val share by vm.shareLogin.collectAsState()
    val ghosts by GhostSongs.all.collectAsState()
    val removal by vm.ghostRemoval.collectAsState()
    var confirm by remember { mutableStateOf<List<String>?>(null) }
    confirm?.let { ids ->
        DeleteGhostsDialog(
            count = ids.size,
            onDismiss = { confirm = null },
            onConfirm = {
                vm.deleteGhosts(ids)
                confirm = null
            },
        )
    }
    // Look straight away the first time.
    LaunchedEffect(Unit) { if (vm.duplicates.value == null) vm.lookForDuplicates() }
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("Duplicates", solid = true, onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (share == null) {
                item {
                    InfoCard(
                        title = "Set up your music folder to delete",
                        body = "To move a copy to the Trash, NaviMuse needs your music share on the NAS: Settings → General → Music folder (SMB).",
                        icon = Icons.Filled.Delete,
                        tone = CardTone.WARNING,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item {
                val s = scan
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        s == null -> Unit
                        !s.done -> {
                            Text("Reading your library: ${s.songs} songs…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        s.error != null -> Text(s.error, color = MaterialTheme.colorScheme.error)
                        else -> Text(
                            "${s.groups.size} song${if (s.groups.size == 1) "" else "s"} found more than once, in ${s.songs} songs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (s == null || s.done) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionPill("Look again", Icons.Filled.Search, primary = s == null) { vm.lookForDuplicates() }
                            // The copies that won't play: old entries, usually with no file left.
                            val old = s?.groups.orEmpty().flatMap { g -> g.songs.filter { it.id in ghosts } }.map { it.id }.distinct()
                            if (old.isNotEmpty() && removal == null) {
                                ActionPill("Delete ${old.size} old entr${if (old.size == 1) "y" else "ies"}", Icons.Filled.Delete) { confirm = old }
                            }
                        }
                    }
                }
            }
            removal?.let { r -> item { GhostRemovalProgress(r) } }
            val groups = scan?.groups.orEmpty()
            if (scan?.done == true && scan?.error == null && groups.isEmpty()) {
                item { EmptyBox("No duplicates. Every song is in your library once.") }
            }
            items(groups, key = { it.key }) { g ->
                Column(Modifier.padding(top = 12.dp)) {
                    HorizontalDivider()
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ContentCopy, null, tint = MaterialTheme.colorScheme.accent, modifier = Modifier.padding(end = 10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.songs.first().title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(g.kind.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.accent)
                        }
                        TextButton(onClick = { vm.notDuplicates(g.key) }) { Text("Not duplicates") }
                    }
                    Text(
                        g.kind.explain,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    g.songs.forEach { song ->
                        CopyRow(
                            song = song,
                            keep = song.id == g.keep,
                            ghost = song.id in ghosts,
                            onPlay = { vm.play(listOf(song)) },
                            onTrash = { if (song.id in ghosts) confirm = listOf(song.id) else ui.trashSong(song) },
                        )
                    }
                }
            }
        }
    }
}

/** One copy of a duplicated song: where it is, what it is, and what to do with it. */
@Composable
private fun CopyRow(song: Song, keep: Boolean, ghost: Boolean, onPlay: () -> Unit, onTrash: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !ghost, onClick = onPlay).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(song.coverArt, size = 44.dp, seed = song.album.ifBlank { song.title })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.album.ifBlank { "No album" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (keep) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Keep",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.accent).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            val details = listOfNotNull(
                song.artist.takeIf { it.isNotBlank() },
                song.year.takeIf { it > 0 }?.toString(),
                listOfNotNull(song.suffix?.uppercase(), song.bitRate.takeIf { it > 0 }?.let { "$it kbps" }).joinToString(" ").ifBlank { null },
                formatDuration(song.duration),
                if (ghost) "won't play" else null,
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2)
        }
        if (!ghost) {
            IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, "Play this copy", tint = muted) }
            IconButton(onClick = onTrash) { Icon(Icons.Filled.Delete, "Move this copy to the Trash", tint = muted) }
        } else {
            IconButton(onClick = onTrash) { Icon(Icons.Filled.Delete, "Delete this old entry", tint = muted) }
        }
    }
}
