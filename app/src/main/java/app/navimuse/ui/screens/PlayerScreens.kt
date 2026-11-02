package app.navimuse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.FormatQuote
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
import app.navimuse.ui.icons.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.navimuse.net.Lyrics
import app.navimuse.net.SubsonicClient
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.Route
import app.navimuse.ui.components.Cover
import app.navimuse.ui.components.CoverFill
import app.navimuse.ui.components.Load
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.QualityBadge
import app.navimuse.ui.components.SongMenu
import app.navimuse.ui.components.qualityLabel
import app.navimuse.ui.components.MenuItem
import app.navimuse.ui.components.rememberSoundEffects
import app.navimuse.ui.components.swipeToSkip
import app.navimuse.playback.Dolby
import app.navimuse.ui.components.rememberArtColor
import app.navimuse.ui.components.rememberLoad
import app.navimuse.ui.components.stageColor
import app.navimuse.ui.formatDuration
import app.navimuse.ui.theme.accent
import app.navimuse.ui.theme.isDark
import kotlinx.coroutines.delay

/** Polls the player's position while something is on screen that shows it. */
@Composable
private fun rememberPosition(vm: AppViewModel, playing: Boolean, everyMs: Long = 500): Long {
    var pos by remember { mutableLongStateOf(vm.player.positionMs()) }
    LaunchedEffect(playing, everyMs) {
        pos = vm.player.positionMs()
        while (playing) {
            delay(everyMs)
            pos = vm.player.positionMs()
        }
    }
    return pos
}

@Composable
fun MiniPlayer(vm: AppViewModel, onOpen: () -> Unit) {
    val state by vm.playerState.collectAsState()
    val current = state.current ?: return
    val song = current.song
    val pos = rememberPosition(vm, state.isPlaying)
    val progress = if (state.durationMs > 0) (pos.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Swipe the song left or right to skip, like on the playing screen.
                Row(
                    Modifier.weight(1f).swipeToSkip(onNext = { vm.player.next() }, onPrevious = { vm.player.previous() }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(song.coverArt, size = 44.dp, seed = song.album.ifBlank { song.title })
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(contentAlignment = Alignment.Center) {
                    IconButton(onClick = { vm.player.togglePlay() }) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pause" else "Play")
                    }
                    if (state.buffering) CircularProgressIndicator(Modifier.size(38.dp), strokeWidth = 2.dp)
                }
                IconButton(onClick = { vm.player.next() }, enabled = state.hasNext) { Icon(Icons.Filled.SkipNext, "Next") }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface))
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
    var showLyrics by remember { mutableStateOf(false) }
    val pos = rememberPosition(vm, state.isPlaying, everyMs = if (showLyrics) 200 else 500)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val starred = stars[song.id] ?: song.starred
    val duration = state.durationMs.coerceAtLeast(1)
    val scheme = MaterialTheme.colorScheme
    val art = rememberArtColor(song.coverArt, song.album.ifBlank { song.title })
    val stage = stageColor(art, scheme.isDark)
    val dolbyKnown by vm.dolby.collectAsState()
    // Dolby Atmos / surround as decoded right now, else as known for the song, else the file's quality.
    val special = state.formatLabel ?: remember(song.id, dolbyKnown) { Dolby.labelFor(song) }
    val quality = special ?: remember(song.id) { qualityLabel(song, vm.streamingKbps(song)) }
    val soundEffects = rememberSoundEffects()

    Surface(Modifier.fillMaxSize(), color = scheme.background) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to stage, 0.8f to scheme.background))) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Now playing", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                        if (song.album.isNotBlank()) {
                            Text(song.album, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        SongMenu(
                            song,
                            expanded = menu,
                            onDismiss = { menu = false },
                            extra = soundEffects?.let { open ->
                                { close -> MenuItem("Sound effects", Icons.Filled.GraphicEq) { close(); open() } }
                            },
                        )
                    }
                }

                if (showLyrics) {
                    Spacer(Modifier.height(16.dp))
                    LyricsPanel(vm, song.id, pos, Modifier.fillMaxWidth().weight(1f))
                    Spacer(Modifier.height(16.dp))
                } else {
                    Spacer(Modifier.weight(0.5f))
                    // Swipe the art left for the next song, right for the previous one.
                    CoverFill(
                        song.coverArt,
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .fillMaxWidth()
                            .widthIn(max = 440.dp)
                            .aspectRatio(1f)
                            .swipeToSkip(onNext = { vm.player.next() }, onPrevious = { vm.player.previous() }),
                        shape = RoundedCornerShape(6.dp),
                        seed = song.album.ifBlank { song.title },
                    )
                    Spacer(Modifier.weight(0.5f))
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        // Tap the song's name to open its album.
                        Text(
                            song.title,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(enabled = song.albumId != null) {
                                song.albumId?.let { ui.navigate(Route.AlbumPage(it)) }
                            },
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(enabled = song.artistId != null) {
                                song.artistId?.let { ui.navigate(Route.ArtistPage(it)) }
                            },
                        )
                    }
                    IconButton(onClick = { vm.setStar(song.id, SubsonicClient.StarKind.SONG, !starred) }) {
                        Icon(
                            if (starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (starred) "Remove from favourites" else "Add to favourites",
                            tint = if (starred) scheme.accent else scheme.onSurface,
                        )
                    }
                }
                if (quality != null) {
                    Spacer(Modifier.height(10.dp))
                    QualityBadge(quality, strong = quality == Dolby.ATMOS)
                }
                Spacer(Modifier.height(14.dp))

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
                    thumb = {
                        Box(Modifier.size(if (dragging) 16.dp else 12.dp).background(scheme.onSurface, CircleShape))
                    },
                    track = {
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.22f))) {
                            Box(Modifier.fillMaxWidth(sliderValue).fillMaxHeight().background(scheme.onSurface))
                        }
                    },
                    modifier = Modifier.height(28.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    val shownPos = if (dragging) (dragValue * duration).toLong() else pos
                    Text(formatDuration((shownPos / 1000).toInt()), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(formatDuration((state.durationMs / 1000).toInt()), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.player.setShuffle(!state.shuffle) }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            "Shuffle",
                            tint = if (state.shuffle) scheme.accent else scheme.onSurfaceVariant,
                            key = if (state.shuffle) "playback.shuffle.on" else "playback.shuffle",
                        )
                    }
                    IconButton(onClick = { vm.player.previous() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(38.dp))
                    }
                    Box(contentAlignment = Alignment.Center) {
                        FilledIconButton(onClick = { vm.player.togglePlay() }, modifier = Modifier.size(72.dp)) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (state.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(38.dp),
                            )
                        }
                        if (state.buffering) CircularProgressIndicator(Modifier.size(80.dp), strokeWidth = 2.dp, color = scheme.accent)
                    }
                    IconButton(onClick = { vm.player.next() }, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(38.dp))
                    }
                    IconButton(onClick = { vm.player.cycleRepeat() }) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            "Repeat",
                            tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) scheme.accent else scheme.onSurfaceVariant,
                            key = when (state.repeatMode) {
                                Player.REPEAT_MODE_ONE -> "playback.repeat.one.on"
                                Player.REPEAT_MODE_ALL -> "playback.repeat.on"
                                else -> "playback.repeat"
                            },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PlayerAction("Queue", Icons.AutoMirrored.Filled.QueueMusic) { showQueue = true }
                    if (info?.lyrics == true) {
                        PlayerAction("Lyrics", Icons.Filled.FormatQuote, active = showLyrics) { showLyrics = !showLyrics }
                    }
                    PlayerAction("Instant Mix", Icons.Filled.AutoAwesome) { vm.instantMix(song) }
                    if (info?.sonicSimilarity == true) {
                        PlayerAction("Similar", Icons.Filled.Waves) { ui.navigate(Route.Similar(song)) }
                    }
                    PlayerAction("Add to…", Icons.AutoMirrored.Filled.PlaylistAdd) { ui.pickPlaylistFor(listOf(song)) }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showQueue) QueueSheet(vm, onDismiss = { showQueue = false })
}

@Composable
private fun PlayerAction(label: String, icon: ImageVector, active: Boolean = false, onClick: () -> Unit) {
    val color = if (active) MaterialTheme.colorScheme.accent else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/**
 * Lyrics for the song that's playing. Synced lyrics follow the music, with the current
 * line lit; tap any line to jump there.
 */
@Composable
private fun LyricsPanel(vm: AppViewModel, songId: String, positionMs: Long, modifier: Modifier = Modifier) {
    val state by vm.playerState.collectAsState()
    val song = state.current?.song
    val loaded = rememberLoad("lyrics", songId) { song?.let { vm.lyrics(it) } }
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val s = loaded.state) {
            is Load.Loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            is Load.Failed -> LyricsMessage(s.message)
            is Load.Ok -> {
                val lyrics = s.value
                if (lyrics == null) LyricsMessage("No lyrics for this song.") else LyricsLines(vm, lyrics, positionMs)
            }
        }
    }
}

@Composable
private fun LyricsMessage(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun LyricsLines(vm: AppViewModel, lyrics: Lyrics, positionMs: Long) {
    val listState = rememberLazyListState()
    val currentIndex = if (lyrics.synced) lyrics.lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= positionMs } else -1
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && !listState.isScrollInProgress) listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
    }
    val on = MaterialTheme.colorScheme.onSurface
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 24.dp)) {
        itemsIndexed(lyrics.lines) { i, line ->
            val lit = !lyrics.synced || i == currentIndex
            Text(
                line.text.ifEmpty { "♪" },
                style = if (lyrics.synced) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                color = if (lit) on else on.copy(alpha = 0.38f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = line.startMs != null) { line.startMs?.let { vm.player.seekTo(it) } }
                    .padding(vertical = if (lyrics.synced) 8.dp else 2.dp),
            )
        }
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Queue", style = MaterialTheme.typography.headlineMedium)
                Text("${state.queue.size} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { ui.saveAsPlaylist(state.queue.map { it.song }, "") }) {
                Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                Text("  Save as playlist")
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(state.queue, key = { _, e -> "${e.index}-${e.song.id}" }) { _, entry ->
                val i = entry.index
                val isCurrent = i == currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { vm.player.skipTo(i) }
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Cover(entry.song.coverArt, size = 44.dp, seed = entry.song.album.ifBlank { entry.song.title })
                        if (isCurrent) Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.song.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isCurrent) MaterialTheme.colorScheme.accent else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
