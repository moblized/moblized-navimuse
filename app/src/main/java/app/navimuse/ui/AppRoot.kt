package app.navimuse.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import app.navimuse.ui.icons.Icon
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.navimuse.data.Settings
import app.navimuse.data.TabChoice
import app.navimuse.data.TabLabels
import app.navimuse.ui.screens.CustomizeScreen
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
import app.navimuse.ui.screens.DownloadsScreen
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

/** [packKey] is the Symfonium icon-pack key; ".selected" is added for the current tab. */
enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val packKey: String, val packKeyHasSelected: Boolean = true) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home, "navigation.home"),
    Mixes("Mixes", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome, "playback.radio.mix", packKeyHasSelected = false),
    Search("Search", Icons.Outlined.Search, Icons.Filled.Search, "navigation.search"),
    Collection("Collection", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic, "navigation.library"),
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
    data object Downloads : Route
    data object SettingsPage : Route
    data object Safety : Route
    data object Customize : Route
}

/** The bottom tab for a choice made in Customize. */
fun TabChoice.tab(): Tab = when (this) {
    TabChoice.HOME -> Tab.Home
    TabChoice.MIXES -> Tab.Mixes
    TabChoice.SEARCH -> Tab.Search
    TabChoice.COLLECTION -> Tab.Collection
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val login by vm.login.collectAsStateWithLifecycle()
    if (login == null) {
        LoginScreen(vm)
        return
    }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val look = settings.look
    val tabs = look.tabs.map { it.tab() }
    var tab by rememberSaveable { mutableStateOf(look.startTab.tab()) }
    // A tab hidden in Customize while it's open: go to the first one still shown.
    LaunchedEffect(tabs) { if (tab !in tabs) tab = tabs.first() }
    val stack = remember { mutableStateListOf<Route>() }
    var showPlayer by remember { mutableStateOf(false) }
    var playlistPickFor by remember { mutableStateOf<List<Song>?>(null) }
    var saveFor by remember { mutableStateOf<Pair<List<Song>, String>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val saveable = rememberSaveableStateHolder()
    // A page's scroll position is kept while it's open (so Back returns to where you were),
    // and forgotten once it's closed, so opening it again starts at the top.
    fun pageKey(index: Int, route: Route) = "route-${index + 1}-$route"
    fun popPage() {
        if (stack.isEmpty()) return
        val i = stack.lastIndex
        saveable.removeState(pageKey(i, stack[i]))
        stack.removeAt(i)
    }
    fun clearPages() {
        stack.forEachIndexed { i, r -> saveable.removeState(pageKey(i, r)) }
        stack.clear()
    }
    val playerState by vm.playerState.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var bottomBarPx by remember { mutableIntStateOf(0) }

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

    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val askNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 && !askedNotifications &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
            askNotifications = { askNotifications() },
            selectTab = { t ->
                showPlayer = false
                tab = t
                clearPages()
            },
            openPlayer = { showPlayer = true },
        )
    }

    BackHandler(enabled = showPlayer || stack.isNotEmpty()) {
        if (showPlayer) showPlayer = false else popPage()
    }

    CompositionLocalProvider(LocalUi provides actions) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                // Each screen's header handles the status bar itself.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    Column(Modifier.onSizeChanged { bottomBarPx = it.height }) {
                        if (playerState.current != null) {
                            MiniPlayer(vm, onOpen = { showPlayer = true })
                        }
                        if (tabs.size > 1) {
                            BottomTabs(
                                tabs = tabs,
                                labels = look.tabLabels,
                                selected = tab,
                                onSelect = { t ->
                                    tab = t
                                    clearPages()
                                },
                            )
                        } else {
                            Spacer(Modifier.fillMaxWidth().navigationBarsPadding())
                        }
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
                        val onBack: () -> Unit = { popPage() }
                        saveable.SaveableStateProvider(pageKey(stack.lastIndex, top)) {
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
                                Route.Downloads -> DownloadsScreen(vm, onBack)
                                Route.SettingsPage -> SettingsScreen(vm, onBack)
                                Route.Safety -> SafetyScreen(vm, onBack)
                                Route.Customize -> CustomizeScreen(vm, onBack)
                            }
                        }
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

            // Messages and the busy line go on top of everything, the playing screen included,
            // so "Saved playlist" (or what went wrong) shows wherever you are.
            busy?.let {
                LinearProgressIndicator(Modifier.fillMaxWidth().statusBarsPadding().height(2.dp))
            }
            val playerOpen = showPlayer && playerState.current != null
            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (playerOpen) {
                            Modifier.navigationBarsPadding().padding(bottom = 72.dp)
                        } else {
                            Modifier.padding(bottom = with(LocalDensity.current) { bottomBarPx.toDp() })
                        },
                    ),
            )
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
private fun BottomTabs(tabs: List<Tab>, labels: TabLabels, selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .height(62.dp),
    ) {
        tabs.forEach { t ->
            val on = t == selected
            val color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val showLabel = labels == TabLabels.ALWAYS || (labels == TabLabels.SELECTED && on)
                Icon(
                    if (on) t.selectedIcon else t.icon,
                    // Without a label, the icon says which tab it is.
                    if (showLabel) null else t.label,
                    tint = color,
                    modifier = Modifier.size(if (showLabel) 24.dp else 27.dp),
                    key = if (on && t.packKeyHasSelected) "${t.packKey}.selected" else t.packKey,
                )
                if (showLabel) {
                    Spacer(Modifier.height(4.dp))
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = color)
                }
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
