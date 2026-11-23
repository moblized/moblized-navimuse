package app.navimuse.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SuggestionChip
import androidx.compose.ui.text.input.ImeAction
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
import app.navimuse.ui.icons.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import app.navimuse.net.PlaylistNames
import app.navimuse.net.TidalLinks
import app.navimuse.ui.AppViewModel
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

/** Ideas for a playlist title, in the plain words it understands. */
private val titleIdeas = listOf("Gym", "Rainy Sunday", "Late night drive", "Chill", "Road trip", "Party", "Focus")

/**
 * Name a new playlist, simply ("Gym", "Rainy Sunday", "Late night drive"), and the app finds
 * songs for it from that title, like Tidal. Nothing is saved until you press Save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewPlaylistDialog(onDismiss: () -> Unit, onFind: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    val risky = PlaylistNames.isAudioMuseManaged(title)
    // Ready to type straight away.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            Column {
                Text(
                    "Name it in a few plain words and NaviMuse fills it with songs from your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Playlist name") },
                    placeholder = { Text("e.g. Rainy Sunday") },
                    singleLine = true,
                    isError = risky,
                    supportingText = if (risky) {
                        { Text("AudioMuse-AI deletes playlists ending in _automatic. It'll be saved as \"${PlaylistNames.safe(title)}\".") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (title.isNotBlank()) onFind(title) }),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).focusRequester(focus),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    titleIdeas.forEach { idea ->
                        SuggestionChip(onClick = { title = idea }, label = { Text(idea) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onFind(title) }, enabled = title.isNotBlank()) { Text("Find songs") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Import a playlist: a TIDAL playlist link, songs pasted one per line ("Artist - Title"), or a
 * file (a CSV a transfer service exported from TIDAL, or a plain list). The songs are then found
 * in your library; nothing is saved until you press Save.
 */
@Composable
fun ImportPlaylistDialog(
    hasTidal: Boolean,
    onDismiss: () -> Unit,
    onSetUpTidal: () -> Unit,
    onImport: (AppViewModel.ImportSource) -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val isLink = TidalLinks.playlistId(text) != null
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val body = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Imported playlist"
        if (!body.isNullOrBlank()) onImport(AppViewModel.ImportSource.SongList(name, body))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import a playlist") },
        text = {
            Column {
                Text(
                    "Paste a TIDAL playlist link, or the songs one per line (Artist - Title). Or pick a file: a CSV exported from TIDAL by TuneMyMusic or Soundiiz works.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("TIDAL link or songs") },
                    placeholder = { Text("https://tidal.com/browse/playlist/…") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (isLink && !hasTidal) {
                    Text(
                        "Reading TIDAL links needs your own free TIDAL developer app (Settings → TIDAL). Or paste the songs instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    TextButton(onClick = onSetUpTidal) { Text("Set up TIDAL") }
                }
                TextButton(onClick = { pickFile.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }) {
                    Text("Choose a file")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onImport(
                        if (isLink) AppViewModel.ImportSource.Tidal(text.trim())
                        else AppViewModel.ImportSource.SongList("Imported playlist", text),
                    )
                },
                enabled = text.isNotBlank() && !(isLink && !hasTidal),
            ) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Asks before moving a song's file to the Trash on the NAS. Without a music folder set up, says
 * how to set one up instead.
 */
@Composable
fun TrashSongDialog(song: Song, ready: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit, onSetUp: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ready) "Move to Trash?" else "Set up your music folder") },
        text = {
            Text(
                if (ready) {
                    "\"${song.title}\" (${song.album.ifBlank { song.artist }}) moves to the _Trash folder in your music share, " +
                        "and Navidrome stops listing it. You can put it back from Settings → Playback → Trash; after " +
                        "${app.navimuse.net.SafetyGuard.TRASH_DAYS} days there it's deleted for good (unless that's turned off). " +
                        "Away from home, it's hidden now and moves next time you're on home Wi-Fi."
                } else {
                    "To delete songs, NaviMuse needs your music share on the NAS. Set it up in Settings → General → Music folder (SMB)."
                },
            )
        },
        confirmButton = {
            if (ready) TextButton(onClick = onConfirm) { Text("Move to Trash") } else TextButton(onClick = onSetUp) { Text("Set up") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Confirms deleting playlist [playlist]. */
@Composable
fun DeletePlaylistDialog(playlist: app.navimuse.net.Playlist, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${playlist.name}\"?") },
        text = {
            Text(
                "The playlist is deleted from Navidrome. Its songs stay in your library.\n\n" +
                    "NaviMuse keeps its name and songs on this phone for ${app.navimuse.data.DeletedPlaylists.KEEP_DAYS} days: " +
                    "restore it from Playlists → Recently deleted." +
                    if (playlist.isAudioMuseManaged) "\n\nAudioMuse-AI makes its _automatic playlists again the next time it runs clustering." else "",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Confirms deleting [count] ghost songs (songs Navidrome lists that won't play). */
@Composable
fun DeleteGhostsDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val what = if (count == 1) "this ghost song" else "$count ghost songs"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $what?") },
        text = {
            Text(
                "Most ghost songs have no file left: Navidrome's entry still points to where it was. NaviMuse asks " +
                    "Navidrome for a quick scan, which drops those entries.\n\nOne Navidrome still lists after that is tried " +
                    "again, and if it still won't play, its file moves to the _Trash folder on your NAS, where you can put it " +
                    "back (Settings → Playback → Trash). Songs that play now are kept.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
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
