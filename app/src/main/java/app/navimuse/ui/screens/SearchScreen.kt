package app.navimuse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navimuse.net.Genre
import app.navimuse.net.SearchResult
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.Route
import app.navimuse.ui.components.AlbumCard
import app.navimuse.ui.components.ArtistBubble
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.ErrorBox
import app.navimuse.ui.components.Load
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.PageHeader
import app.navimuse.ui.components.SectionHeader
import app.navimuse.ui.components.SongRow
import app.navimuse.ui.components.placeholderColors
import app.navimuse.ui.components.rememberLoad
import app.navimuse.ui.friendly
import app.navimuse.ui.theme.ArtShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(vm: AppViewModel) {
    val ui = LocalUi.current
    val focus = LocalFocusManager.current
    val player by vm.playerState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            result = null
            error = null
            return@LaunchedEffect
        }
        delay(350)
        loading = true
        error = null
        try {
            result = vm.api().search(q)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = friendly(e)
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Search")
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Songs, albums, artists") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                when {
                    loading -> CircularProgressIndicator(Modifier.padding(14.dp), strokeWidth = 2.dp)
                    query.isNotEmpty() -> IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val r = result
        when {
            error != null -> ErrorBox(error.orEmpty(), retry = null)
            r == null -> GenreBrowser(vm)
            r.isEmpty -> EmptyBox("Nothing found for \"${query.trim()}\".")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (r.songs.isNotEmpty()) {
                    item { SectionHeader("Tracks") }
                    itemsIndexed(r.songs.take(5), key = { _, s -> "top-${s.id}" }) { i, s ->
                        SongRow(s, onPlay = { vm.play(r.songs, i) }, isCurrent = player.current?.song?.id == s.id)
                    }
                }
                if (r.artists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(r.artists, key = { it.id }) { a -> ArtistBubble(a) { ui.navigate(Route.ArtistPage(a.id)) } }
                        }
                    }
                }
                if (r.albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(r.albums, key = { it.id }) { a -> AlbumCard(a) { ui.navigate(Route.AlbumPage(a.id)) } }
                        }
                    }
                }
                if (r.songs.size > 5) {
                    item { SectionHeader("More tracks") }
                    itemsIndexed(r.songs.drop(5), key = { _, s -> "more-${s.id}" }) { i, s ->
                        SongRow(s, onPlay = { vm.play(r.songs, i + 5) }, isCurrent = player.current?.song?.id == s.id)
                    }
                }
            }
        }
    }
}

/** The genres in your library as coloured tiles, biggest first. */
@Composable
private fun GenreBrowser(vm: AppViewModel) {
    val ui = LocalUi.current
    val loaded = rememberLoad("genres") { vm.api().genres().distinctBy { it.name.lowercase() }.sortedByDescending { it.albumCount } }
    when (val s = loaded.state) {
        is Load.Loading -> Unit
        is Load.Failed -> EmptyBox("Search your Navidrome library.")
        is Load.Ok -> if (s.value.isEmpty()) {
            EmptyBox("Search your Navidrome library.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Browse genres", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                }
                items(s.value.size, key = { s.value[it].name }) { i ->
                    GenreTile(s.value[i]) { ui.navigate(Route.GenrePage(s.value[i].name)) }
                }
            }
        }
    }
}

@Composable
private fun GenreTile(genre: Genre, onClick: () -> Unit) {
    val colors = remember(genre.name) { placeholderColors(genre.name) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(ArtShape)
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(genre.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (genre.albumCount > 0) {
                Text(
                    "${genre.albumCount} album${if (genre.albumCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
    }
}
