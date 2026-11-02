package app.navimuse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import app.navimuse.ui.icons.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navimuse.downloads.Downloads
import app.navimuse.net.Album
import app.navimuse.net.AlbumListType
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.Route
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.components.AlbumCard
import app.navimuse.ui.components.ArtistBubble
import app.navimuse.ui.components.ArtistRow
import app.navimuse.ui.components.CardTone
import app.navimuse.ui.components.Cover
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.FloatingTopBar
import app.navimuse.ui.components.InfoCard
import app.navimuse.ui.components.LoadBox
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.PageHeader
import app.navimuse.ui.components.PlaylistRow
import app.navimuse.ui.components.SectionHeader
import app.navimuse.ui.components.SongRow
import app.navimuse.ui.components.rememberLoad
import app.navimuse.ui.symfonium.Shortcut
import kotlinx.coroutines.delay

/** Your library as a short menu, then what you played lately. */
@Composable
fun CollectionScreen(vm: AppViewModel) {
    val ui = LocalUi.current
    val recent = rememberLoad("collection-recent") { vm.api().albumList(AlbumListType.RECENT, 12) }
    val appStyle by vm.appStyle.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { PageHeader("My collection") }
        item { Spacer(Modifier.size(8.dp)) }
        // A Symfonium app style's library menu order; NaviMuse's own otherwise.
        val styled = appStyle?.library.orEmpty().filter { it in menuEntries }
        val menu = (styled.ifEmpty { listOf(Shortcut.PLAYLISTS, Shortcut.ALBUMS, Shortcut.ARTISTS) } +
            listOf(Shortcut.FAVOURITES, Shortcut.DOWNLOADS)).distinct()
        menu.forEach { sc ->
            item(key = "menu-$sc") { MenuRow(sc.label, shortcutIcon(sc)) { openShortcut(vm, ui, sc) } }
        }
        val albums = recent.value.orEmpty()
        if (albums.isNotEmpty()) {
            item { SectionHeader("Recently played") }
            items(albums, key = { "recent-${it.id}" }) { a -> AlbumLine(a) { ui.navigate(Route.AlbumPage(a.id)) } }
        }
    }
}

private val menuEntries = setOf(Shortcut.PLAYLISTS, Shortcut.ALBUMS, Shortcut.ARTISTS, Shortcut.GENRES, Shortcut.FAVOURITES, Shortcut.DOWNLOADS)

@Composable
private fun MenuRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An album as one line: art, name, artist. */
@Composable
private fun AlbumLine(album: Album, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(album.coverArt, size = 56.dp, placeholder = Icons.Filled.Album, seed = album.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(album.artist, album.year.takeIf { it > 0 }?.toString()).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CollectionAlbumsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ui = LocalUi.current
    var sort by rememberSaveable { mutableStateOf(AlbumListType.NEWEST) }
    var sortMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("Albums", solid = true, onBack = onBack) {
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
        AlbumGrid(
            key = sort,
            load = { offset, size -> vm.api().albumList(sort, size, offset) },
            maxPages = if (sort == AlbumListType.RANDOM) 4 else Int.MAX_VALUE,
            onOpen = { ui.navigate(Route.AlbumPage(it.id)) },
            header = {
                Text(sort.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
        )
    }
}

@Composable
fun CollectionArtistsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ui = LocalUi.current
    val loaded = rememberLoad("artists") { vm.api().artists() }
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("Artists", solid = true, onBack = onBack)
        LoadBox(loaded) { artists ->
            if (artists.isEmpty()) {
                EmptyBox("No artists yet.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(artists, key = { it.id }) { a -> ArtistRow(a) { ui.navigate(Route.ArtistPage(a.id)) } }
                }
            }
        }
    }
}

@Composable
fun CollectionPlaylistsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ui = LocalUi.current
    val version by vm.playlistsVersion.collectAsState()
    val loaded = rememberLoad("playlists", version) { vm.api().playlists() }
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("Playlists", solid = true, onBack = onBack)
        LoadBox(loaded) { playlists ->
            val automatic = playlists.filter { it.isAudioMuseManaged }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (automatic.isNotEmpty()) {
                    item {
                        InfoCard(
                            title = "${automatic.size} AudioMuse-AI playlist${if (automatic.size == 1) "" else "s"} will be replaced",
                            body = "AudioMuse-AI deletes every playlist ending in _automatic each time it runs clustering, then makes new ones. " +
                                "Keep a copy of any you like and it stays put.",
                            icon = Icons.Filled.Shield,
                            tone = CardTone.WARNING,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            action = {
                                TextButton(onClick = { vm.keepAll(automatic) }, contentPadding = PaddingValues(0.dp)) {
                                    Text("Keep copies of all ${automatic.size}")
                                }
                            },
                        )
                    }
                }
                if (playlists.isEmpty()) item { EmptyBox("No playlists yet. Save a mix or your queue to make one.") }
                items(playlists, key = { it.id }) { p -> PlaylistRow(p) { ui.navigate(Route.PlaylistPage(p.id)) } }
            }
        }
    }
}

@Composable
fun FavouritesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ui = LocalUi.current
    val stars by vm.stars.collectAsState()
    val player by vm.playerState.collectAsState()
    val loaded = rememberLoad("starred") { vm.api().starred() }
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("Favourites", solid = true, onBack = onBack)
        LoadBox(loaded) { starred ->
            val songs = starred.songs.filter { stars[it.id] ?: true }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionPill("Play", Icons.Filled.PlayArrow, Modifier.weight(1f), primary = true) { vm.play(songs, from = favourites) }
                        ActionPill("Shuffle", Icons.Filled.Shuffle, Modifier.weight(1f)) { vm.play(songs, shuffle = true, from = favourites) }
                    }
                }
                if (starred.albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(starred.albums, key = { it.id }) { a -> AlbumCard(a) { ui.navigate(Route.AlbumPage(a.id)) } }
                        }
                    }
                }
                if (starred.artists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(starred.artists, key = { it.id }) { a -> ArtistBubble(a) { ui.navigate(Route.ArtistPage(a.id)) } }
                        }
                    }
                }
                item { SectionHeader("Tracks") }
                if (songs.isEmpty()) item { EmptyBox("Tap the heart on a song to add it here.") }
                itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                    SongRow(s, onPlay = { vm.play(songs, i, from = favourites) }, isCurrent = player.current?.song?.id == s.id)
                }
            }
        }
    }
}

private val favourites = AppViewModel.PlaySource("Favourites", Route.Favourites)

/** Everything saved on the phone, playable with no connection. */
@Composable
fun DownloadsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val downloads by vm.downloads.collectAsState()
    val settings by vm.settings.collectAsState()
    val player by vm.playerState.collectAsState()
    val items = downloads.values.toList()
    val done = items.filter { it.state == Downloads.State.DONE }.map { it.song }
    val pending = items.filter { it.state != Downloads.State.DONE }
    val busy = pending.any { it.state != Downloads.State.FAILED }
    LaunchedEffect(busy) {
        while (busy) {
            vm.refreshDownloadProgress()
            delay(1000)
        }
    }
    val bytes = remember(done.size) { vm.downloadBytes() }
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("Downloads", solid = true, onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "${done.size} track${if (done.size == 1) "" else "s"} · ${formatBytes(bytes)} on this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (done.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionPill("Play", Icons.Filled.PlayArrow, Modifier.weight(1f), primary = true) { vm.play(done, from = downloadsSource) }
                        ActionPill("Shuffle", Icons.Filled.Shuffle, Modifier.weight(1f)) { vm.play(done, shuffle = true, from = downloadsSource) }
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.setFavouritesAutoDownload(!settings.autoDownloadFavourites) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Download favourites automatically", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (settings.downloadWifiOnly) "New favourites save on Wi-Fi" else "New favourites save on any connection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.autoDownloadFavourites, onCheckedChange = { vm.setFavouritesAutoDownload(it) })
                }
            }
            if (pending.isNotEmpty()) {
                item { SectionHeader("Downloading") }
                items(pending, key = { "p-${it.song.id}" }) { d ->
                    SongRow(
                        d.song,
                        onPlay = {},
                        trailingText = when (d.state) {
                            Downloads.State.FAILED -> "Failed"
                            Downloads.State.DOWNLOADING -> "${d.percent.toInt()}%"
                            else -> "Waiting"
                        },
                    )
                }
            }
            if (done.isNotEmpty()) item { SectionHeader("On this phone") }
            if (items.isEmpty()) {
                item { EmptyBox("Nothing downloaded yet. Tap the download button on an album or playlist to play it without a connection.") }
            }
            itemsIndexed(done, key = { _, s -> "d-${s.id}" }) { i, s ->
                SongRow(s, onPlay = { vm.play(done, i, from = downloadsSource) }, isCurrent = player.current?.song?.id == s.id)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} KB"
}
private val downloadsSource = AppViewModel.PlaySource("Downloads", Route.Downloads)
