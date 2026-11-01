package app.navimuse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.navimuse.data.Settings
import app.navimuse.net.AlbumListType
import app.navimuse.net.Song
import app.navimuse.ui.components.AddToPlaylistDialog
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.SavePlaylistDialog
import app.navimuse.ui.components.UiActions
import app.navimuse.ui.screens.AlbumScreen
import app.navimuse.ui.screens.AlbumsGridScreen
import app.navimuse.ui.screens.ArtistScreen
import app.navimuse.ui.screens.CollectionAlbumsScreen
import app.navimuse.ui.screens.CollectionArtistsScreen
import app.navimuse.ui.screens.CollectionPlaylistsScreen
import app.navimuse.ui.screens.CollectionScreen
import app.navimuse.ui.screens.FavouritesScreen
import app.navimuse.ui.screens.GenreScreen
import app.navimuse.ui.screens.HomeScreen
import app.navimuse.ui.screens.LoginScreen
import app.navimuse.ui.screens.MiniPlayer
import app.navimuse.ui.screens.MixesScreen
import app.navimuse.ui.screens.NowPlayingScreen
import app.navimuse.ui.screens.PlaylistScreen
import app.navimuse.ui.screens.SafetyScreen
import app.navimuse.ui.screens.SearchScreen
import app.navimuse.ui.screens.SettingsScreen
import app.navimuse.ui.screens.SimilarScreen

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Mixes("Mixes", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    Search("Search", Icons.Outlined.Search, Icons.Filled.Search),
    Collection("Collection", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic),
}

sealed interface Route {
    data class AlbumPage(val id: String) : Route
    data class ArtistPage(val id: String) : Route
    data class PlaylistPage(val id: String) : Route
    data class AlbumGrid(val type: AlbumListType) : Route
    data class Similar(val song: Song) : Route
    data class GenrePage(val name: String) : Route
    data object Albums : Route
    data object Artists : Route
    data object Playlists : Route
    data object Favourites : Route
    data object SettingsPage : Route
    data object Safety : Route
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val login by vm.login.collectAsStateWithLifecycle()
    if (login == null) {
        LoginScreen(vm)
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val stack = remember { mutableStateListOf<Route>() }
    var showPlayer by remember { mutableStateOf(false) }
    var playlistPickFor by remember { mutableStateOf<List<Song>?>(null) }
    var saveFor by remember { mutableStateOf<Pair<List<Song>, String>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val saveable = rememberSaveableStateHolder()
    val playerState by vm.playerState.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.onForeground()
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(playerState.error) {
        playerState.error?.let {
            snackbar.showSnackbar(it)
            vm.player.clearError()
        }
    }

    val actions = remember(vm) {
        UiActions(
            vm = vm,
            navigate = { route ->
                showPlayer = false
                stack.add(route)
            },
            pickPlaylistFor = { playlistPickFor = it },
            saveAsPlaylist = { songs, name -> saveFor = songs to name },
        )
    }

    BackHandler(enabled = showPlayer || stack.isNotEmpty()) {
        if (showPlayer) showPlayer = false else stack.removeAt(stack.lastIndex)
    }

    CompositionLocalProvider(LocalUi provides actions) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                // Each screen's header handles the status bar itself.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        if (playerState.current != null) {
                            MiniPlayer(vm, onOpen = { showPlayer = true })
                        }
                        BottomTabs(
                            selected = tab,
                            onSelect = { t ->
                                tab = t
                                stack.clear()
                            },
                        )
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    val top = stack.lastOrNull()
                    if (top == null) {
                        saveable.SaveableStateProvider("tab-${tab.name}") {
                            when (tab) {
                                Tab.Home -> HomeScreen(vm)
                                Tab.Mixes -> MixesScreen(vm)
                                Tab.Search -> SearchScreen(vm)
                                Tab.Collection -> CollectionScreen(vm)
                            }
                        }
                    } else {
                        val onBack: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
                        saveable.SaveableStateProvider("route-${stack.size}-$top") {
                            when (top) {
                                is Route.AlbumPage -> AlbumScreen(vm, top.id, onBack)
                                is Route.ArtistPage -> ArtistScreen(vm, top.id, onBack)
                                is Route.PlaylistPage -> PlaylistScreen(vm, top.id, onBack)
                                is Route.AlbumGrid -> AlbumsGridScreen(vm, top.type, onBack)
                                is Route.Similar -> SimilarScreen(vm, top.song, onBack)
                                is Route.GenrePage -> GenreScreen(vm, top.name, onBack)
                                Route.Albums -> CollectionAlbumsScreen(vm, onBack)
                                Route.Artists -> CollectionArtistsScreen(vm, onBack)
                                Route.Playlists -> CollectionPlaylistsScreen(vm, onBack)
                                Route.Favourites -> FavouritesScreen(vm, onBack)
                                Route.SettingsPage -> SettingsScreen(vm, onBack)
                                Route.Safety -> SafetyScreen(vm, onBack)
                            }
                        }
                    }
                    busy?.let {
                        LinearProgressIndicator(Modifier.fillMaxWidth().statusBarsPadding().height(2.dp))
                    }
                }
            }

            AnimatedVisibility(
                visible = showPlayer && playerState.current != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NowPlayingScreen(vm, onClose = { showPlayer = false })
            }
        }

        playlistPickFor?.let { songs ->
            AddToPlaylistDialog(
                songs = songs,
                onDismiss = { playlistPickFor = null },
                onNew = {
                    playlistPickFor = null
                    saveFor = songs to ""
                },
            )
        }
        saveFor?.let { (songs, name) ->
            SavePlaylistDialog(
                songCount = songs.size,
                suggestedName = name,
                onDismiss = { saveFor = null },
                onSave = {
                    vm.savePlaylist(it, songs)
                    saveFor = null
                },
            )
        }
    }
}

/** Icons with small labels on a plain bar; the current tab is drawn in full strength. */
@Composable
private fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .height(62.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = t == selected
            val color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(if (on) t.selectedIcon else t.icon, null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
                Text(t.label, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

/** Whether the app is showing its dark look right now. */
fun Settings.isDark(systemDark: Boolean): Boolean = when (theme) {
    app.navimuse.data.ThemeMode.SYSTEM -> systemDark
    app.navimuse.data.ThemeMode.LIGHT -> false
    app.navimuse.data.ThemeMode.DARK -> true
}
