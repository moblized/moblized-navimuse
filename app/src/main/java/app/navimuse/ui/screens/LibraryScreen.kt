package app.navimuse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.navimuse.net.AlbumListType
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.Route
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.components.ArtistRow
import app.navimuse.ui.components.CardTone
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.InfoCard
import app.navimuse.ui.components.LoadBox
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.PlaylistRow
import app.navimuse.ui.components.SongRow
import app.navimuse.ui.components.rememberLoad

private enum class LibraryTab(val label: String) { Albums("Albums"), Artists("Artists"), Playlists("Playlists"), Favourites("Favourites") }

@Composable
fun LibraryScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Albums) }
    var sort by rememberSaveable { mutableStateOf(AlbumListType.NEWEST) }
    var sortMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Library") },
            actions = {
                if (tab == LibraryTab.Albums) {
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            AlbumListType.entries.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.label) },
                                    leadingIcon = { if (t == sort) Icon(Icons.Filled.Check, null) },
                                    onClick = {
                                        sort = t
                                        sortMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            LibraryTab.entries.forEach { t ->
                Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
            }
        }
        when (tab) {
            LibraryTab.Albums -> {
                val ui = LocalUi.current
                AlbumGrid(vm, sort, onOpen = { ui.navigate(Route.AlbumPage(it.id)) })
            }
            LibraryTab.Artists -> ArtistsList(vm)
            LibraryTab.Playlists -> PlaylistsList(vm)
            LibraryTab.Favourites -> FavouritesList(vm)
        }
    }
}

@Composable
private fun ArtistsList(vm: AppViewModel) {
    val ui = LocalUi.current
    val loaded = rememberLoad("artists") { vm.api().artists() }
    LoadBox(loaded) { artists ->
        if (artists.isEmpty()) {
            EmptyBox("No artists yet.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(artists, key = { it.id }) { a -> ArtistRow(a) { ui.navigate(Route.ArtistPage(a.id)) } }
            }
        }
    }
}

@Composable
private fun PlaylistsList(vm: AppViewModel) {
    val ui = LocalUi.current
    val version by vm.playlistsVersion.collectAsState()
    val loaded = rememberLoad("playlists", version) { vm.api().playlists() }
    LoadBox(loaded) { playlists ->
        val automatic = playlists.filter { it.isAudioMuseManaged }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (automatic.isNotEmpty()) {
                item {
                    InfoCard(
                        title = "${automatic.size} AudioMuse-AI playlist${if (automatic.size == 1) "" else "s"} will be replaced",
                        body = "AudioMuse-AI deletes every playlist ending in _automatic each time it runs clustering, then makes new ones. " +
                            "Keep a copy of any you like and it stays put.",
                        icon = Icons.Filled.Shield,
                        tone = CardTone.WARNING,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        action = { TextButton(onClick = { vm.keepAll(automatic) }) { Text("Keep copies of all ${automatic.size}") } },
                    )
                }
            }
            if (playlists.isEmpty()) item { EmptyBox("No playlists yet. Save a mix or your queue to make one.") }
            items(playlists, key = { it.id }) { p -> PlaylistRow(p) { ui.navigate(Route.PlaylistPage(p.id)) } }
        }
    }
}

@Composable
private fun FavouritesList(vm: AppViewModel) {
    val ui = LocalUi.current
    val stars by vm.stars.collectAsState()
    val loaded = rememberLoad("starred") { vm.api().starred() }
    LoadBox(loaded) { starred ->
        val songs = starred.songs.filter { stars[it.id] ?: true }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionPill("Play", Icons.Filled.PlayArrow, primary = true) { vm.play(songs) }
                    ActionPill("Shuffle", Icons.Filled.Shuffle) { vm.play(songs, shuffle = true) }
                }
            }
            if (starred.albums.isNotEmpty()) {
                item { app.navimuse.ui.components.SectionHeader("Albums") }
                item {
                    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(starred.albums, key = { it.id }) { a ->
                            app.navimuse.ui.components.AlbumCard(a) { ui.navigate(Route.AlbumPage(a.id)) }
                        }
                    }
                }
            }
            if (starred.artists.isNotEmpty()) {
                item { app.navimuse.ui.components.SectionHeader("Artists") }
                item {
                    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(starred.artists, key = { it.id }) { a ->
                            app.navimuse.ui.components.ArtistBubble(a) { ui.navigate(Route.ArtistPage(a.id)) }
                        }
                    }
                }
            }
            item { app.navimuse.ui.components.SectionHeader("Songs") }
            if (songs.isEmpty()) item { EmptyBox("Tap the heart on a song to add it here.") }
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s -> SongRow(s, onPlay = { vm.play(songs, i) }) }
        }
    }
}
