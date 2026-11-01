package app.navimuse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import app.navimuse.ui.components.FloatingTopBar
import app.navimuse.ui.components.InfoCard
import app.navimuse.ui.components.Load
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.PageHeader
import app.navimuse.ui.components.SectionHeader
import app.navimuse.ui.components.rememberArtColor
import app.navimuse.ui.components.rememberLoad
import app.navimuse.ui.components.stageColor
import app.navimuse.ui.friendly
import app.navimuse.ui.theme.ArtShape
import kotlinx.coroutines.CancellationException

@Composable
fun HomeScreen(vm: AppViewModel) {
    val ui = LocalUi.current
    val info by vm.serverInfo.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            PageHeader("Home") {
                IconButton(onClick = { ui.navigate(Route.Safety) }) { Icon(Icons.Filled.Shield, "Safety") }
                IconButton(onClick = { ui.navigate(Route.SettingsPage) }) { Icon(Icons.Filled.Settings, "Settings") }
            }
        }
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
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionPill("Shuffle all", Icons.Filled.Shuffle, primary = true) { vm.shuffleAll() }
                ActionPill("Surprise mix", Icons.Filled.AutoAwesome) { vm.surpriseMix() }
            }
        }
        item { AlbumShelf(vm, "Recently played", AlbumListType.RECENT, hideWhenEmpty = true) }
        item { MixesForYou(vm) }
        item { AlbumShelf(vm, "Recently added", AlbumListType.NEWEST) }
        item { AlbumShelf(vm, "Most played", AlbumListType.FREQUENT, hideWhenEmpty = true) }
        item { AlbumShelf(vm, "Discover", AlbumListType.RANDOM) }
    }
}

/** Instant Mixes seeded from albums you play a lot (or random ones on a new server). */
@Composable
private fun MixesForYou(vm: AppViewModel) {
    val loaded = rememberLoad("mixes") {
        vm.api().albumList(AlbumListType.FREQUENT, 10).ifEmpty { vm.api().albumList(AlbumListType.RANDOM, 10) }
    }
    SectionHeader("Your Instant Mixes")
    when (val s = loaded.state) {
        is Load.Loading -> ShelfPlaceholder(172)
        is Load.Failed -> ErrorBox(s.message, loaded.retry)
        is Load.Ok -> if (s.value.isEmpty()) {
            EmptyBox("Play some music and mixes will show up here.")
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.value, key = { it.id }) { album -> MixCard(album) { vm.mixFromAlbum(album) } }
            }
        }
    }
}

/** Album art washed in its own colour, with the artist the mix is built around printed over it. */
@Composable
private fun MixCard(album: Album, onClick: () -> Unit) {
    val art = rememberArtColor(album.coverArt, album.name)
    val wash = stageColor(art, dark = true)
    val size = 172.dp
    Column(Modifier.width(size).clickable(onClick = onClick)) {
        Box(Modifier.size(size).clip(ArtShape)) {
            Cover(album.coverArt, size = size, shape = RectangleShape, placeholder = Icons.Filled.Album, seed = album.name)
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(0.3f to Color.Transparent, 0.95f to wash.copy(alpha = 0.96f))),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    album.artist,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Instant Mix", style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(
            "Starts from ${album.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AlbumShelf(vm: AppViewModel, title: String, type: AlbumListType, hideWhenEmpty: Boolean = false) {
    val ui = LocalUi.current
    val loaded = rememberLoad(type) { vm.api().albumList(type, 20) }
    val s = loaded.state
    if (hideWhenEmpty && s is Load.Ok && s.value.isEmpty()) return
    SectionHeader(title, action = "View all") { ui.navigate(Route.AlbumGrid(type)) }
    when (s) {
        is Load.Loading -> ShelfPlaceholder(156)
        is Load.Failed -> ErrorBox(s.message, loaded.retry)
        is Load.Ok -> if (s.value.isEmpty()) {
            EmptyBox("Nothing here yet.")
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.value, key = { it.id }) { album -> AlbumCard(album) { ui.navigate(Route.AlbumPage(album.id)) } }
            }
        }
    }
}

/** Grey squares where a shelf is about to appear. */
@Composable
private fun ShelfPlaceholder(size: Int) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Column {
                Box(Modifier.size(size.dp).clip(ArtShape).background(MaterialTheme.colorScheme.surfaceContainer))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width((size * 0.7f).dp).height(12.dp).clip(ArtShape).background(MaterialTheme.colorScheme.surfaceContainer))
            }
        }
    }
}

/** Every album in the chosen order, loading more as you scroll. */
@Composable
fun AlbumsGridScreen(vm: AppViewModel, type: AlbumListType, onBack: () -> Unit) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar(type.label, solid = true, onBack = onBack)
        AlbumGrid(
            key = type,
            load = { offset, size -> vm.api().albumList(type, size, offset) },
            maxPages = if (type == AlbumListType.RANDOM) 4 else Int.MAX_VALUE,
            onOpen = { ui.navigate(Route.AlbumPage(it.id)) },
        )
    }
}

/**
 * A grid of albums that loads [PAGE] more each time you near the end.
 * [key] restarts it from the top (for a new sort order or genre).
 */
@Composable
fun AlbumGrid(
    key: Any,
    load: suspend (offset: Int, size: Int) -> List<Album>,
    onOpen: (Album) -> Unit,
    maxPages: Int = Int.MAX_VALUE,
    header: (@Composable () -> Unit)? = null,
) {
    val albums = remember(key) { mutableStateListOf<Album>() }
    var loading by remember(key) { mutableStateOf(false) }
    var done by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var page by remember(key) { mutableIntStateOf(0) }
    var retryTick by remember(key) { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(key, page, retryTick) {
        if (done) return@LaunchedEffect
        loading = true
        error = null
        try {
            val next = load(page * PAGE, PAGE)
            val known = albums.map { it.id }.toHashSet()
            albums.addAll(next.filter { it.id !in known })
            if (next.size < PAGE || page + 1 >= maxPages) done = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = friendly(e)
        } finally {
            loading = false
        }
    }
    val nearEnd by remember(key) {
        derivedStateOf { (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= albums.size - 12 }
    }
    LaunchedEffect(gridState, key) {
        snapshotFlow { nearEnd }.collect { near ->
            if (near && !loading && !done && error == null && albums.isNotEmpty()) page++
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        items(albums, key = { it.id }) { album ->
            AlbumTile(album) { onOpen(album) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            when {
                error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { error = null; retryTick++ }) { Text("Try again") }
                }
                loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                }
                albums.isEmpty() && done -> EmptyBox("No albums.")
                else -> Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private const val PAGE = 60
