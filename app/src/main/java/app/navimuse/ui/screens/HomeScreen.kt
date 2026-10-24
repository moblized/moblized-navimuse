package app.navimuse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navimuse.net.Album
import app.navimuse.net.AlbumListType
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.Route
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.components.AlbumCard
import app.navimuse.ui.components.AlbumTile
import app.navimuse.ui.components.CardTone
import app.navimuse.ui.components.Cover
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.ErrorBox
import app.navimuse.ui.components.InfoCard
import app.navimuse.ui.components.Load
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.SectionHeader
import app.navimuse.ui.components.rememberLoad
import app.navimuse.ui.friendly
import kotlinx.coroutines.CancellationException

@Composable
fun HomeScreen(vm: AppViewModel) {
    val ui = LocalUi.current
    val info by vm.serverInfo.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("NaviMuse", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { ui.navigate(Route.Safety) }) { Icon(Icons.Filled.Shield, "Safety") }
                IconButton(onClick = { ui.navigate(Route.SettingsPage) }) { Icon(Icons.Filled.Settings, "Settings") }
            },
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            val i = info
            if (i != null && !i.sonicSimilarity) {
                item {
                    InfoCard(
                        title = "AudioMuse-AI isn't answering through Navidrome",
                        body = "Navidrome didn't report sonic similarity, so Instant Mix may use Last.fm instead (or return nothing). " +
                            "Check that the AudioMuse-AI plugin is installed and \"audiomuseai\" is first in ND_AGENTS.",
                        icon = Icons.Filled.Info,
                        tone = CardTone.WARNING,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionPill("Shuffle all", Icons.Filled.Shuffle, primary = true) { vm.shuffleAll() }
                    ActionPill("Surprise mix", Icons.Filled.AutoAwesome) { vm.surpriseMix() }
                }
            }
            item { MixesForYou(vm) }
            item { AlbumShelf(vm, "Recently added", AlbumListType.NEWEST) }
            item { AlbumShelf(vm, "Recently played", AlbumListType.RECENT) }
            item { AlbumShelf(vm, "Most played", AlbumListType.FREQUENT) }
            item { AlbumShelf(vm, "Discover", AlbumListType.RANDOM) }
        }
    }
}

/** Instant Mixes seeded from albums you play a lot (or random ones on a new server). */
@Composable
private fun MixesForYou(vm: AppViewModel) {
    val loaded = rememberLoad("mixes") {
        vm.api().albumList(AlbumListType.FREQUENT, 10).ifEmpty { vm.api().albumList(AlbumListType.RANDOM, 10) }
    }
    SectionHeader("Instant Mixes for you")
    when (val s = loaded.state) {
        is Load.Loading -> ShelfPlaceholder()
        is Load.Failed -> ErrorBox(s.message, loaded.retry)
        is Load.Ok -> if (s.value.isEmpty()) {
            EmptyBox("Play some music and mixes will show up here.")
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.value, key = { it.id }) { album -> MixCard(album) { vm.mixFromAlbum(album) } }
            }
        }
    }
}

@Composable
private fun MixCard(album: Album, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box {
            Cover(album.coverArt, size = 142.dp, shape = RoundedCornerShape(10.dp), placeholder = Icons.Filled.Album)
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) {
                Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.padding(6.dp).size(18.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Mix: ${album.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Sounds like ${album.artist}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlbumShelf(vm: AppViewModel, title: String, type: AlbumListType) {
    val ui = LocalUi.current
    val loaded = rememberLoad(type) { vm.api().albumList(type, 20) }
    SectionHeader(title, action = "See all") { ui.navigate(Route.AlbumGrid(type)) }
    when (val s = loaded.state) {
        is Load.Loading -> ShelfPlaceholder()
        is Load.Failed -> ErrorBox(s.message, loaded.retry)
        is Load.Ok -> if (s.value.isEmpty()) {
            EmptyBox("Nothing here yet.")
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.value, key = { it.id }) { album -> AlbumCard(album) { ui.navigate(Route.AlbumPage(album.id)) } }
            }
        }
    }
}

@Composable
private fun ShelfPlaceholder() {
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Every album, in the chosen order, loading more as you scroll. */
@Composable
fun AlbumsGridScreen(vm: AppViewModel, initialType: AlbumListType, onBack: () -> Unit) {
    val ui = LocalUi.current
    var type by remember { mutableStateOf(initialType) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(type.label) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        AlbumGrid(vm, type, onOpen = { ui.navigate(Route.AlbumPage(it.id)) })
    }
}

@Composable
fun AlbumGrid(vm: AppViewModel, type: AlbumListType, onOpen: (Album) -> Unit, header: (@Composable () -> Unit)? = null) {
    val albums = remember(type) { mutableStateListOf<Album>() }
    var loading by remember(type) { mutableStateOf(false) }
    var done by remember(type) { mutableStateOf(false) }
    var error by remember(type) { mutableStateOf<String?>(null) }
    var page by remember(type) { mutableStateOf(0) }
    var retryTick by remember(type) { mutableStateOf(0) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(type, page, retryTick) {
        if (done) return@LaunchedEffect
        loading = true
        error = null
        try {
            val next = vm.api().albumList(type, PAGE, page * PAGE)
            val known = albums.map { it.id }.toHashSet()
            albums.addAll(next.filter { it.id !in known })
            if (next.size < PAGE || type == AlbumListType.RANDOM && page >= 3) done = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = friendly(e)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(gridState, type) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (!loading && !done && error == null && albums.isNotEmpty() && last >= albums.size - 12) page++
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = gridState,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (header != null) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
        }
        items(albums, key = { it.id }) { album ->
            AlbumTile(album) { onOpen(album) }
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            when {
                error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { error = null; retryTick++ }) { Text("Try again") }
                }
                loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                albums.isEmpty() && done -> EmptyBox("No albums.")
                else -> Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private const val PAGE = 60
