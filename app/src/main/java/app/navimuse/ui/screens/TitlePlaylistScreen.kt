package app.navimuse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.Route
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.theme.ArtShape
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.FloatingTopBar
import app.navimuse.ui.components.LoadBox
import app.navimuse.ui.components.SongRow
import app.navimuse.ui.components.placeholderColors
import app.navimuse.ui.components.rememberLoad
import app.navimuse.ui.icons.Icon

/**
 * A new playlist made from its title (see AppViewModel.titleMix): the songs it found, to play,
 * trim (the × on a song) and save under that title, or "New picks" for another go.
 */
@Composable
fun TitlePlaylistScreen(vm: AppViewModel, title: String, onBack: () -> Unit) {
    val player by vm.playerState.collectAsState()
    val loaded = rememberLoad("title-playlist", title) { vm.titleMix(title) }
    // Songs taken out before saving; a new set of picks starts clean.
    val dropped = remember(loaded.value) { mutableStateListOf<String>() }
    val songs = loaded.value?.songs.orEmpty().filter { it.id !in dropped }
    val from = AppViewModel.PlaySource(title, Route.TitlePlaylist(title))
    val colors = remember(title) { placeholderColors(title) }
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("New playlist", solid = true, onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(ArtShape)
                        .background(Brush.linearGradient(listOf(colors.first, colors.second))),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        null,
                        tint = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.align(Alignment.CenterEnd).offset(x = 18.dp).size(140.dp),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text("Playlist", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                        Text(title, style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item {
                LoadBox(loaded) { mix ->
                    Column {
                        Text(
                            mix.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        if (songs.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ActionPill("Save playlist", Icons.Filled.Save, Modifier.weight(1f), primary = true) {
                                    vm.savePlaylist(title, songs)
                                    onBack()
                                }
                                ActionPill("Play", Icons.Filled.PlayArrow, Modifier.weight(1f)) { vm.play(songs, from = from) }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ActionPill("New picks", Icons.Filled.Refresh, Modifier.weight(1f)) { loaded.retry() }
                            if (songs.isNotEmpty()) {
                                ActionPill("Shuffle", Icons.Filled.Shuffle, Modifier.weight(1f)) { vm.play(songs, shuffle = true, from = from) }
                            }
                        }
                        if (mix.songs.isNotEmpty() && songs.isEmpty()) EmptyBox("You took every song out. Tap New picks for more.")
                    }
                }
            }
            itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SongRow(s, onPlay = { vm.play(songs, i, from = from) }, isCurrent = player.current?.song?.id == s.id)
                    }
                    IconButton(onClick = { dropped += s.id }) {
                        Icon(Icons.Filled.Close, "Take out of the playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
