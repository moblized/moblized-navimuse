package app.navimuse.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import app.navimuse.net.PlaylistNames
import app.navimuse.net.Song
import app.navimuse.ui.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Pick one of your playlists to add songs to (or make a new one). */
@Composable
fun AddToPlaylistDialog(songs: List<Song>, onDismiss: () -> Unit, onNew: () -> Unit) {
    val vm = LocalUi.current.vm
    val version by vm.playlistsVersion.collectAsState()
    val loaded = rememberLoad(version) { vm.ownPlaylists() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (songs.size == 1) "Add \"${songs[0].title}\" to…" else "Add ${songs.size} songs to…", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("New playlist") },
                    leadingContent = { Icon(Icons.Filled.Add, null) },
                    modifier = Modifier.clickable { onNew() },
                )
                LoadBox(loaded) { lists ->
                    if (lists.isEmpty()) {
                        EmptyBox("You don't have any playlists of your own yet.")
                    } else {
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(lists, key = { it.id }) { p ->
                                ListItem(
                                    headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text("${p.songCount} songs") },
                                    modifier = Modifier.clickable {
                                        vm.addToPlaylist(p, songs)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }
                Text(
                    "Songs are only ever added. AudioMuse-AI's own playlists aren't listed, because it replaces them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Name a new playlist. Names AudioMuse-AI would delete are fixed before saving. */
@Composable
fun SavePlaylistDialog(songCount: Int, suggestedName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(PlaylistNames.safe(suggestedName, "")) }
    val risky = PlaylistNames.isAudioMuseManaged(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = risky,
                    supportingText = {
                        Text(
                            if (risky) {
                                "AudioMuse-AI deletes playlists ending in _automatic. It'll be saved as \"${PlaylistNames.safe(name)}\"."
                            } else {
                                "$songCount songs. A new playlist is made; nothing existing is replaced."
                            },
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Shield, null) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Search for one song (used to pick the ends of a song path, or a mix seed). */
@Composable
fun SongPickerDialog(title: String, onDismiss: () -> Unit, onPick: (Song) -> Unit) {
    val vm = LocalUi.current.vm
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        loading = true
        error = null
        try {
            results = vm.searchSongs(query.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = friendly(e)
        } finally {
            loading = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Song, artist or album") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (loading) CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(results, key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Cover(s.coverArt, size = 40.dp) },
                            modifier = Modifier.clickable { onPick(s) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
