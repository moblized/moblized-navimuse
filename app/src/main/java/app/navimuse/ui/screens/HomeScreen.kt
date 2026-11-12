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
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import app.navimuse.ui.icons.Icon
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
import app.navimuse.ui.MixSeed
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
import app.navimuse.data.HomeSection
import app.navimuse.ui.theme.ArtShape
import app.navimuse.ui.theme.LocalLook
import app.navimuse.update.Updater
import app.navimuse.ui.components.ArtistBubble
import app.navimuse.ui.components.PlaylistCard
import app.navimuse.ui.components.SongRow
import app.navimuse.ui.Tab
import app.navimuse.ui.symfonium.AppStyle
import app.navimuse.ui.symfonium.HomeRow
import app.navimuse.ui.symfonium.HomeSource
import app.navimuse.ui.symfonium.Shortcut
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import kotlinx.coroutines.CancellationException

@Composable
fun HomeScreen(vm: AppViewModel) {
    val ui = LocalUi.current
    val info by vm.serverInfo.collectAsState()
    val update by vm.updateStatus.collectAsState()
    val appStyle by vm.appStyle.collectAsState()
    val settings by vm.settings.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            PageHeader("Home") {
                IconButton(onClick = { ui.navigate(Route.SettingsPage) }) { Icon(Icons.Filled.Settings, "Settings") }
            }
        }
        (update as? Updater.Status.Ready)?.let { ready ->
            item {
                InfoCard(
                    title = "NaviMuse ${ready.release.versionName} is ready",
                    body = ready.note ?: "Install it now. Your login, downloads and queue stay as they are.",
                    icon = Icons.Filled.SystemUpdate,
                    tone = CardTone.GOOD,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    action = { TextButton(onClick = { vm.installUpdate() }, contentPadding = PaddingValues(0.dp)) { Text("Install") } },
                )
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
        val style = appStyle
        if (style != null && style.homeRows.isNotEmpty()) {
            // A Symfonium application style's home: its sections, in its order, with its titles.
            style.homeRows.forEachIndexed { index, row ->
                item(key = "style-$index") { StyleRow(vm, style, row) }
                if (index == 0) item(key = "style-mixes") { MixesForYou(vm) }
            }
        } else {
            // The sections picked in Settings → Customize, in their order.
            settings.look.home.forEach { section ->
                item(key = "home-${section.name}") { HomeSectionView(vm, section) }
            }
            if (settings.look.home.isEmpty()) {
                item { EmptyBox("Home is empty. Pick what to show in Settings → Customize.") }
            }
        }
    }
}

@Composable
private fun HomeSectionView(vm: AppViewModel, section: HomeSection) {
    val ui = LocalUi.current
    fun row(source: HomeSource) = HomeRow(source, title = null, showHeader = true, count = 20, gridRows = 1, gridColumns = 3)
    when (section) {
        HomeSection.SHORTCUTS -> Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionPill("Shuffle all", Icons.Filled.Shuffle, primary = true) { vm.shuffleAll() }
            ActionPill("Surprise mix", Icons.Filled.AutoAwesome) { ui.navigate(Route.MixPage(MixSeed.Surprise())) }
        }
        HomeSection.MIXES -> MixesForYou(vm)
        HomeSection.RECENTLY_PLAYED -> StyleRow(vm, null, row(HomeSource.RECENTLY_PLAYED))
        HomeSection.RECENTLY_ADDED -> StyleRow(vm, null, row(HomeSource.RECENTLY_ADDED))
        HomeSection.MOST_PLAYED -> StyleRow(vm, null, row(HomeSource.MOST_PLAYED))
        HomeSection.DISCOVER -> StyleRow(vm, null, row(HomeSource.RANDOM_ALBUMS))
        HomeSection.RANDOM_SONGS -> StyleRow(vm, null, row(HomeSource.RANDOM_SONGS))
        HomeSection.FAVOURITE_SONGS -> StyleRow(vm, null, row(HomeSource.FAVOURITE_SONGS))
        HomeSection.FAVOURITE_ALBUMS -> StyleRow(vm, null, row(HomeSource.FAVOURITE_ALBUMS))
        HomeSection.FAVOURITE_ARTISTS -> StyleRow(vm, null, row(HomeSource.FAVOURITE_ARTISTS))
        HomeSection.PLAYLISTS -> StyleRow(vm, null, row(HomeSource.PLAYLISTS))
    }
}

/** One home section, from a Symfonium application style ([style]) or Customize. */
@Composable
private fun StyleRow(vm: AppViewModel, style: AppStyle?, row: HomeRow) {
    val ui = LocalUi.current
    val title = (row.title ?: row.source.defaultTitle).takeIf { row.showHeader && it.isNotBlank() }
    when (row.source) {
        HomeSource.SHORTCUTS -> {
            val set = style?.shortcuts?.getOrNull(row.title?.toIntOrNull() ?: 0).orEmpty()
            ShortcutRow(vm, set)
        }
        HomeSource.RECENTLY_PLAYED -> StyleAlbums(vm, title, row, AlbumListType.RECENT) { vm.api().albumList(AlbumListType.RECENT, row.count) }
        HomeSource.RECENTLY_ADDED -> StyleAlbums(vm, title, row, AlbumListType.NEWEST) { vm.api().albumList(AlbumListType.NEWEST, row.count) }
        HomeSource.MOST_PLAYED -> StyleAlbums(vm, title, row, AlbumListType.FREQUENT) { vm.api().albumList(AlbumListType.FREQUENT, row.count) }
        HomeSource.RANDOM_ALBUMS -> {
            // Discover: mood tiles first (unless turned off in Settings → Layout), then random albums.
            val moods = LocalLook.current.discoverMoods
            StyleAlbums(
                vm, title, row, AlbumListType.RANDOM,
                under = if (moods) ({ MoodShelf(Modifier.padding(bottom = 16.dp)) }) else null,
            ) { vm.api().albumList(AlbumListType.RANDOM, row.count) }
        }
        HomeSource.FAVOURITE_ALBUMS -> StyleAlbums(vm, title, row, AlbumListType.STARRED) { vm.api().starred().albums.take(row.count) }
        HomeSource.RANDOM_SONGS -> StyleSongs(vm, title, row) { vm.api().randomSongs(row.count.coerceAtMost(50)) }
        HomeSource.FAVOURITE_SONGS -> StyleSongs(vm, title, row) { vm.api().starred().songs.take(row.count) }
        HomeSource.FAVOURITE_ARTISTS -> {
            val loaded = rememberLoad("style-artists") { vm.api().starred().artists.take(row.count) }
            val artists = loaded.value.orEmpty()
            if (artists.isNotEmpty()) {
                title?.let { SectionHeader(it) }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(artists, key = { it.id }) { a -> ArtistBubble(a) { ui.navigate(Route.ArtistPage(a.id)) } }
                }
            }
        }
        HomeSource.PLAYLISTS -> {
            val version by vm.playlistsVersion.collectAsState()
            val loaded = rememberLoad("style-playlists", version) { vm.api().playlists().take(row.count) }
            val playlists = loaded.value.orEmpty()
            if (playlists.isNotEmpty()) {
                title?.let { SectionHeader(it, action = "View all") { ui.navigate(Route.Playlists) } }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(playlists, key = { it.id }) { p -> PlaylistCard(p) { ui.navigate(Route.PlaylistPage(p.id)) } }
                }
            }
        }
    }
}

/** Albums as a sideways shelf, or as a grid when the style asks for several rows. */
@Composable
private fun StyleAlbums(
    vm: AppViewModel,
    title: String?,
    row: HomeRow,
    type: AlbumListType,
    under: (@Composable () -> Unit)? = null,
    load: suspend () -> List<Album>,
) {
    val ui = LocalUi.current
    val loaded = rememberLoad("style-${row.source}", row.count) { load() }
    val s = loaded.state
    if (s is Load.Ok && s.value.isEmpty()) {
        // No albums, but [under] (Discover's moods) still shows.
        if (under != null) {
            title?.let { SectionHeader(it) }
            under()
        }
        return
    }
    title?.let { SectionHeader(it, action = "View all") { ui.navigate(Route.AlbumGrid(type)) } }
    under?.invoke()
    when (s) {
        is Load.Loading -> ShelfPlaceholder(LocalLook.current.cardWidth)
        is Load.Failed -> ErrorBox(s.message, loaded.retry)
        is Load.Ok -> if (row.gridRows > 1) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                s.value.take(row.gridRows * row.gridColumns).chunked(row.gridColumns).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        line.forEach { album -> AlbumTile(album, Modifier.weight(1f)) { ui.navigate(Route.AlbumPage(album.id)) } }
                        repeat(row.gridColumns - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.value, key = { it.id }) { album -> AlbumCard(album) { ui.navigate(Route.AlbumPage(album.id)) } }
            }
        }
    }
}

@Composable
private fun StyleSongs(vm: AppViewModel, title: String?, row: HomeRow, load: suspend () -> List<app.navimuse.net.Song>) {
    val loaded = rememberLoad("style-${row.source}", row.count) { load() }
    val songs = loaded.value.orEmpty()
    if (songs.isEmpty()) return
    title?.let { SectionHeader(it) }
    songs.take((row.gridRows * 4).coerceIn(4, 12)).forEachIndexed { i, song ->
        SongRow(song, onPlay = { vm.play(songs, i) })
    }
}

/** A row of shortcut buttons from a Symfonium app style. */
@Composable
private fun ShortcutRow(vm: AppViewModel, set: List<Shortcut>) {
    val ui = LocalUi.current
    if (set.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        set.forEachIndexed { i, sc ->
            ActionPill(sc.label, shortcutIcon(sc), primary = i == 0) { openShortcut(vm, ui, sc) }
        }
    }
}

fun shortcutIcon(sc: Shortcut): ImageVector = when (sc) {
    Shortcut.LIBRARY -> Icons.Filled.LibraryMusic
    Shortcut.SEARCH -> Icons.Filled.Search
    Shortcut.SETTINGS -> Icons.Filled.Settings
    Shortcut.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
    Shortcut.ALBUMS -> Icons.Filled.Album
    Shortcut.ARTISTS -> Icons.Filled.Person
    Shortcut.GENRES -> Icons.Filled.Category
    Shortcut.FAVOURITES -> Icons.Filled.Favorite
    Shortcut.DOWNLOADS -> Icons.Filled.OfflinePin
    Shortcut.MIX -> Icons.Filled.AutoAwesome
    Shortcut.MIXES -> Icons.Filled.Radio
    Shortcut.SHUFFLE -> Icons.Filled.Shuffle
}

fun openShortcut(vm: AppViewModel, ui: app.navimuse.ui.components.UiActions, sc: Shortcut) {
    when (sc) {
        Shortcut.LIBRARY -> ui.selectTab(Tab.Collection)
        Shortcut.SEARCH, Shortcut.GENRES -> ui.selectTab(Tab.Search)
        Shortcut.SETTINGS -> ui.navigate(Route.SettingsPage)
        Shortcut.PLAYLISTS -> ui.navigate(Route.Playlists)
        Shortcut.ALBUMS -> ui.navigate(Route.Albums)
        Shortcut.ARTISTS -> ui.navigate(Route.Artists)
        Shortcut.FAVOURITES -> ui.navigate(Route.Favourites)
        Shortcut.DOWNLOADS -> ui.navigate(Route.Downloads)
        Shortcut.MIX -> ui.navigate(Route.MixPage(MixSeed.Surprise()))
        Shortcut.MIXES -> ui.selectTab(Tab.Mixes)
        Shortcut.SHUFFLE -> vm.shuffleAll()
    }
}

/** Instant Mixes seeded from albums you play a lot (or random ones on a new server). */
@Composable
private fun MixesForYou(vm: AppViewModel) {
    val ui = LocalUi.current
    val loaded = rememberLoad("mixes") {
        vm.api().albumList(AlbumListType.FREQUENT, 10).ifEmpty { vm.api().albumList(AlbumListType.RANDOM, 10) }
    }
    SectionHeader("Your Instant Mixes")
    when (val s = loaded.state) {
        is Load.Loading -> ShelfPlaceholder(LocalLook.current.cardWidth + 16)
        is Load.Failed -> ErrorBox(s.message, loaded.retry)
        is Load.Ok -> if (s.value.isEmpty()) {
            EmptyBox("Play some music and mixes will show up here.")
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.value, key = { it.id }) { album -> MixCard(album) { ui.navigate(Route.MixPage(MixSeed.FromAlbum(album))) } }
            }
        }
    }
}

/** Album art washed in its own colour, with the artist the mix is built around printed over it. */
@Composable
private fun MixCard(album: Album, onClick: () -> Unit) {
    val art = rememberArtColor(album.coverArt, album.name)
    val wash = stageColor(art, dark = true)
    val size = (LocalLook.current.cardWidth + 16).dp
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
    val vm = LocalUi.current.vm
    // A Symfonium app style can fix the number of columns, or Customize can.
    val appStyle by vm.appStyle.collectAsState()
    val fixedColumns = appStyle?.gridColumns ?: LocalLook.current.gridColumns.takeIf { it > 0 }
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
        columns = fixedColumns?.let { GridCells.Fixed(it) } ?: GridCells.Adaptive(150.dp),
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
