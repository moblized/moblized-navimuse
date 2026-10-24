package app.navimuse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.navimuse.net.SubsonicClient
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.Route
import app.navimuse.ui.components.Cover
import app.navimuse.ui.components.CoverFill
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.SongMenu
import app.navimuse.ui.formatDuration
import kotlinx.coroutines.delay

/** Polls the player's position while something is on screen that shows it. */
@Composable
private fun rememberPosition(vm: AppViewModel, playing: Boolean): Long {
    var pos by remember { mutableLongStateOf(vm.player.positionMs()) }
    LaunchedEffect(playing) {
        pos = vm.player.positionMs()
        while (playing) {
            delay(500)
            pos = vm.player.positionMs()
        }
    }
    return pos
}

@Composable
fun MiniPlayer(vm: AppViewModel, onOpen: () -> Unit) {
    val state by vm.playerState.collectAsState()
    val current = state.current ?: return
    val pos = rememberPosition(vm, state.isPlaying)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            val progress = if (state.durationMs > 0) (pos.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(current.song.coverArt, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(current.song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(current.song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(contentAlignment = Alignment.Center) {
                    IconButton(onClick = { vm.player.togglePlay() }) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pause" else "Play")
                    }
                    if (state.buffering) CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 2.dp)
                }
                IconButton(onClick = { vm.player.next() }, enabled = state.hasNext) { Icon(Icons.Filled.SkipNext, "Next") }
            }
        }
    }
}

@Composable
fun NowPlayingScreen(vm: AppViewModel, onClose: () -> Unit) {
    val ui = LocalUi.current
    val state by vm.playerState.collectAsState()
    val info by vm.serverInfo.collectAsState()
    val stars by vm.stars.collectAsState()
    val current = state.current ?: return
    val song = current.song
    val pos = rememberPosition(vm, state.isPlaying)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val starred = stars[song.id] ?: song.starred
    val duration = state.durationMs.coerceAtLeast(1)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close") }
                Text(
                    "Now playing",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    SongMenu(song, expanded = menu, onDismiss = { menu = false })
                }
            }
            Spacer(Modifier.weight(0.5f))
            CoverFill(
                song.coverArt,
                Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(1f),
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.weight(0.5f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = song.artistId != null) {
                            song.artistId?.let { ui.navigate(Route.ArtistPage(it)) }
                        },
                    )
                    if (song.album.isNotBlank()) {
                        Text(
                            song.album,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(enabled = song.albumId != null) {
                                song.albumId?.let { ui.navigate(Route.AlbumPage(it)) }
                            },
                        )
                    }
                }
                IconButton(onClick = { vm.setStar(song.id, SubsonicClient.StarKind.SONG, !starred) }) {
                    Icon(
                        if (starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (starred) "Remove from favourites" else "Add to favourites",
                        tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val sliderValue = if (dragging) dragValue else (pos.toFloat() / duration).coerceIn(0f, 1f)
            Slider(
                value = sliderValue,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    vm.player.seekTo((dragValue * duration).toLong())
                    dragging = false
                },
            )
            Row(Modifier.fillMaxWidth()) {
                val shownPos = if (dragging) (dragValue * duration).toLong() else pos
                Text(formatDuration((shownPos / 1000).toInt()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(formatDuration((state.durationMs / 1000).toInt()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.player.setShuffle(!state.shuffle) }) {
                    Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.player.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(36.dp))
                }
                Box(contentAlignment = Alignment.Center) {
                    FilledIconButton(
                        onClick = { vm.player.togglePlay() },
                        modifier = Modifier.size(76.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(),
                    ) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pause" else "Play", modifier = Modifier.size(40.dp))
                    }
                    if (state.buffering) CircularProgressIndicator(Modifier.size(84.dp), strokeWidth = 3.dp)
                }
                IconButton(onClick = { vm.player.next() }, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { vm.player.cycleRepeat() }) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "Repeat",
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PlayerAction("Instant Mix", Icons.Filled.AutoAwesome) { vm.instantMix(song) }
                if (info?.sonicSimilarity == true) {
                    PlayerAction("Similar", Icons.Filled.Waves) { ui.navigate(Route.Similar(song)) }
                }
                PlayerAction("Add to…", Icons.AutoMirrored.Filled.PlaylistAdd) { ui.pickPlaylistFor(listOf(song)) }
                PlayerAction("Queue", Icons.AutoMirrored.Filled.QueueMusic) { showQueue = true }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showQueue) QueueSheet(vm, onDismiss = { showQueue = false })
}

@Composable
private fun PlayerAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun QueueSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val ui = LocalUi.current
    val state by vm.playerState.collectAsState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val listState = rememberLazyListState()
    val currentIndex = state.current?.index ?: -1
    LaunchedEffect(Unit) {
        if (currentIndex > 0) listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${state.queue.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { ui.saveAsPlaylist(state.queue.map { it.song }, "") }) {
                Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                Text("  Save as playlist")
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(state.queue, key = { _, e -> "${e.index}-${e.song.id}" }) { _, entry ->
                val i = entry.index
                val isCurrent = i == currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { vm.player.skipTo(i) }
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Cover(entry.song.coverArt, size = 44.dp)
                        if (isCurrent) Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { vm.player.moveInQueue(i, i - 1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                    IconButton(onClick = { vm.player.moveInQueue(i, i + 1) }, enabled = i < state.queue.size - 1) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                    IconButton(onClick = { vm.player.removeFromQueue(i) }, enabled = !isCurrent) { Icon(Icons.Filled.Close, "Remove from queue") }
                }
            }
        }
        Text(
            "Removing a song here only takes it out of what's playing. Nothing is deleted from your server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
