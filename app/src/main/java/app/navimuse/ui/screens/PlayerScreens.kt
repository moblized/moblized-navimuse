package app.navimuse.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import app.navimuse.net.Song
import app.navimuse.playback.PlayerUiState
import app.navimuse.ui.symfonium.NowPlayingStyle
import app.navimuse.ui.symfonium.NpElement
import app.navimuse.ui.symfonium.StyledLine
import app.navimuse.ui.symfonium.songFields
import app.navimuse.data.Look
import app.navimuse.net.Credit
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.AlertDialog
import app.navimuse.data.MiniStyle
import app.navimuse.data.PlayButtonStyle
import app.navimuse.data.PlayerArt
import app.navimuse.data.PlayerBackground
import app.navimuse.data.TitleAlign
import app.navimuse.ui.theme.ArtShape
import app.navimuse.ui.theme.LocalLook
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalView
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
import app.navimuse.ui.MixSeed
import app.navimuse.ui.Route
import app.navimuse.ui.queuePlaylistName
import app.navimuse.ui.components.Cover
import app.navimuse.ui.components.CoverFill
import app.navimuse.ui.components.Load
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.QualityBadge
import app.navimuse.ui.components.SongMenu
import app.navimuse.ui.components.qualityLabel
import app.navimuse.ui.components.FormatLine
import app.navimuse.ui.components.AudioDetails
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
    val style by vm.nowPlayingStyle.collectAsState()
    val current = state.current ?: return
    val song = current.song
    val pos = rememberPosition(vm, state.isPlaying)
    val progress = if (state.durationMs > 0) (pos.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val st = style
    val look = LocalLook.current
    // A Symfonium style's floating player, or the one picked in Customize.
    val styleFloats = st?.miniFloating == true
    val floating = styleFloats || look.miniStyle == MiniStyle.FLOATING
    val shape = when {
        !floating -> RectangleShape
        styleFloats -> RoundedCornerShape(percent = st!!.miniCornerPercent.coerceAtLeast(8))
        else -> RoundedCornerShape(look.miniCorner.dp)
    }
    val marginH = if (styleFloats) st!!.miniMarginH else 0
    val marginBottom = if (styleFloats) st!!.miniMarginBottom else 0
    val swipe = look.miniSwipe && st?.swipeToSkip != false
    val fields = songFields(song, state, pos, null)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        shadowElevation = if (floating) 6.dp else 0.dp,
        modifier = if (floating) {
            Modifier.padding(start = (marginH + 8).dp, end = (marginH + 8).dp, bottom = (marginBottom + 6).dp)
        } else {
            Modifier
        },
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Swipe the song left or right to skip, like on the playing screen.
                Row(
                    Modifier.weight(1f).let {
                        if (!swipe) it else it.swipeToSkip(onNext = { vm.player.next() }, onPrevious = { vm.player.previous() })
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(
                        song.coverArt,
                        size = look.miniArt.dp,
                        seed = song.album.ifBlank { song.title },
                        shape = st?.takeIf { styleFloats }?.let { RoundedCornerShape(percent = it.miniThumbCornerPercent) } ?: ArtShape,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val title = st?.miniTitle
                        val subtitle = st?.miniSubtitle
                        if (title != null) {
                            StyledLine(title.copy(maxLines = 1), fields, MaterialTheme.typography.titleSmall)
                        } else {
                            Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (subtitle != null) {
                            StyledLine(subtitle.copy(maxLines = 1), fields, MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (look.miniPrevious) {
                    IconButton(onClick = { vm.player.previous() }) { Icon(Icons.Filled.SkipPrevious, "Previous") }
                }
                Box(contentAlignment = Alignment.Center) {
                    IconButton(onClick = { vm.player.togglePlay() }) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pause" else "Play")
                    }
                    if (state.buffering) CircularProgressIndicator(Modifier.size(38.dp), strokeWidth = 2.dp)
                }
                if (look.miniNext) {
                    IconButton(onClick = { vm.player.next() }, enabled = state.hasNext) { Icon(Icons.Filled.SkipNext, "Next") }
                }
            }
            if (look.miniProgress) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface))
                }
            }
        }
    }
}

@Composable
fun NowPlayingScreen(vm: AppViewModel, onClose: () -> Unit) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val state by vm.playerState.collectAsState()
    val info by vm.serverInfo.collectAsState()
    val stars by vm.stars.collectAsState()
    val style by vm.nowPlayingStyle.collectAsState()
    val source by vm.source.collectAsState()
    val current = state.current ?: return
    val song = current.song
    var showLyrics by remember { mutableStateOf(false) }
    val pos = rememberPosition(vm, state.isPlaying, everyMs = if (showLyrics || style != null) 250 else 500)
    var showQueue by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
    val starred = stars[song.id] ?: song.starred
    val scheme = MaterialTheme.colorScheme
    val art = rememberArtColor(song.coverArt, song.album.ifBlank { song.title })
    val stage = stageColor(art, scheme.isDark)
    val dolbyKnown by vm.dolby.collectAsState()
    val special = state.formatLabel ?: remember(song.id, dolbyKnown) { Dolby.labelFor(song) }
    val kbps = remember(song.id) { vm.streamingKbps(song) }
    val quality = special ?: remember(song.id) { qualityLabel(song, kbps) }
    val soundEffects = rememberSoundEffects()
    val st = style
    val next = state.queue.getOrNull((current.index) + 1)?.song
    val fields = songFields(song, state, pos, AudioDetails.line(song, state.decoded, kbps) ?: quality, next)
    // Pull down to close, like Tidal (not while reading lyrics, which scroll).
    val dragDown = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val closeAt = with(LocalDensity.current) { 140.dp.toPx() }
    val look = LocalLook.current
    // Keep the screen on while this is open, if asked to in Customize.
    val view = LocalView.current
    DisposableEffect(look.keepScreenOn) {
        view.keepScreenOn = look.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Swipe up from the bottom part of the screen to open the queue.
    val openQueueAt = with(LocalDensity.current) { 72.dp.toPx() }
    val lyricsOpen by rememberUpdatedState(showLyrics)
    Surface(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = dragDown.value }
            .pointerInput(Unit) {
                var startY = 0f
                var moved = 0f
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        startY = offset.y
                        moved = 0f
                    },
                    onDragEnd = {
                        val queue = startY > size.height * 0.65f && -moved > openQueueAt
                        scope.launch {
                            when {
                                queue -> {
                                    showQueue = true
                                    dragDown.animateTo(0f)
                                }
                                dragDown.value > closeAt -> {
                                    onClose()
                                    dragDown.snapTo(0f)
                                }
                                else -> dragDown.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { dragDown.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    moved += amount
                    // Pull down to close (not while reading lyrics, which scroll).
                    if (!lyricsOpen) scope.launch { dragDown.snapTo((dragDown.value + amount).coerceAtLeast(0f)) }
                }
            },
        color = scheme.background,
    ) {
        // Background: the song's colour fading down (NaviMuse's own), blurred art, or plain.
        val blur = st?.backgroundBlur?.takeIf { it > 0 } ?: if (look.playerBackground == PlayerBackground.BLUR) 90 else 0
        val plain = blur == 0 && look.playerBackground == PlayerBackground.PLAIN
        Box(
            Modifier.fillMaxSize().background(
                if (plain) Brush.verticalGradient(listOf(scheme.background, scheme.background))
                else Brush.verticalGradient(0f to stage, 0.85f to scheme.background),
            ),
        ) {
            if (blur > 0) {
                val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                CoverFill(
                    song.coverArt,
                    Modifier.fillMaxSize().let { if (canBlur) it.blur((blur / 2).coerceIn(8, 60).dp) else it },
                    shape = RectangleShape,
                    seed = song.album.ifBlank { song.title },
                )
                // Older phones can't blur, so the art is dimmed more instead.
                Box(Modifier.fillMaxSize().background(scheme.background.copy(alpha = if (canBlur) 0.55f else 0.8f)))
            }
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            ) {
                // Top: the artist's picture (tap for the artist), a style's top text, and where it's playing.
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (look.showArtistPhoto) {
                        Cover(
                            song.artistId?.let { "ar-$it" } ?: song.coverArt,
                            size = 44.dp,
                            shape = CircleShape,
                            placeholder = Icons.Filled.Person,
                            seed = song.artist,
                            modifier = Modifier.clip(CircleShape).clickable(enabled = song.artistId != null) {
                                song.artistId?.let { ui.navigate(Route.ArtistPage(it)) }
                            },
                        )
                    } else {
                        RoundButton(Icons.Filled.KeyboardArrowDown, "Close") { onClose() }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        st?.topTitle?.let { StyledLine(it.copy(maxLines = 1), fields, MaterialTheme.typography.titleSmall) }
                        st?.topSubtitle?.let { StyledLine(it.copy(maxLines = 1), fields, MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant) }
                    }
                    if (look.showOutputButton) {
                        RoundButton(Icons.Filled.Speaker, "Play on another device") { openOutputSwitcher(context) }
                    } else {
                        Spacer(Modifier.size(52.dp))
                    }
                }

                if (showLyrics) {
                    Spacer(Modifier.height(16.dp))
                    LyricsPanel(vm, song.id, pos, Modifier.fillMaxWidth().weight(1f), onClose = { showLyrics = false })
                    Spacer(Modifier.height(16.dp))
                } else {
                    Spacer(Modifier.weight(0.5f))
                    // Tap the art for lyrics (when the server has them).
                    NowPlayingArt(vm, song, state.isPlaying, st, look, onTap = if (info?.lyrics == true) ({ showLyrics = true }) else null)
                    Spacer(Modifier.weight(0.5f))
                }

                val elements = st?.elements
                // A style's seek bar size wins when it sets one; Customize's otherwise.
                val seekScale = st?.sliderScale?.takeIf { it != 1f } ?: look.seekScale
                val showTimes = (st?.sliderTimes ?: true) && look.showTimes
                if (elements == null) {
                    TitleBlock(vm, song, starred, look.titleAlign)
                    Spacer(Modifier.height(18.dp))
                    SeekBar(vm, state, pos, scale = seekScale, showTimes = showTimes, song = song, kbps = kbps)
                    Spacer(Modifier.height(14.dp))
                    ControlButtons(vm, state, look)
                } else {
                    // A Symfonium style's text lines, seek bar and buttons, in its order.
                    elements.forEach { el ->
                        when (el) {
                            is NpElement.Text -> StyledLine(
                                el.line,
                                fields,
                                MaterialTheme.typography.titleMedium,
                                Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(enabled = song.albumId != null) {
                                    song.albumId?.let { ui.navigate(Route.AlbumPage(it)) }
                                },
                            )
                            NpElement.Slider -> SeekBar(vm, state, pos, scale = seekScale, showTimes = showTimes, song = song, kbps = kbps)
                            NpElement.Buttons -> {
                                Spacer(Modifier.height(6.dp))
                                ControlButtons(vm, state, look)
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))

                // Bottom: queue, where it's playing from, share, more.
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Icons.Filled.Menu, "Queue") { showQueue = true }
                    val from = source ?: song.albumId?.let { AppViewModel.PlaySource(song.album, Route.AlbumPage(it)) }
                    if (!look.showPlayingFrom) Spacer(Modifier.weight(1f)) else Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = from?.route != null) { from?.route?.let { ui.navigate(it) } }
                            .padding(vertical = 4.dp),
                    ) {
                        Text("Playing from", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                        Text(from?.name ?: song.album, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (look.showShare) {
                        RoundButton(Icons.Filled.Share, "Share") { shareSong(context, song) }
                        Spacer(Modifier.width(10.dp))
                    }
                    RoundButton(Icons.Filled.MoreHoriz, "More") { menu = true }
                }
            }
        }
    }

    if (showQueue) QueueSheet(vm, onDismiss = { showQueue = false })
    if (menu) {
        NowPlayingMenu(
            vm,
            song,
            starred,
            onDismiss = { menu = false },
            lyrics = if (info?.lyrics == true) (if (showLyrics) "Hide lyrics" else "Lyrics") to { showLyrics = !showLyrics } else null,
            soundEffects = soundEffects,
            onCredits = { showCredits = true },
        )
    }
    if (showCredits) CreditsDialog(vm, song, kbps, state.decoded) { showCredits = false }
}

/**
 * Everything you can do with the song that's playing, as a sheet like Tidal's: play next,
 * favourite, save for later, playlists, share, radio, credits, album, artist, lyrics, sound
 * effects and blocking.
 */
@Composable
private fun NowPlayingMenu(
    vm: AppViewModel,
    song: Song,
    starred: Boolean,
    onDismiss: () -> Unit,
    lyrics: Pair<String, () -> Unit>?,
    soundEffects: (() -> Unit)?,
    onCredits: () -> Unit,
) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = scheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            // The song: art, title, artist.
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(song.coverArt, size = 84.dp, seed = song.album.ifBlank { song.title })
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 4.dp), color = scheme.outlineVariant)
            fun pick(action: () -> Unit): () -> Unit = {
                onDismiss()
                action()
            }
            SheetItem("Play next", Icons.AutoMirrored.Filled.PlaylistPlay, pick { vm.playNext(listOf(song)) })
            SheetItem(
                if (starred) "Remove from favourites" else "Add to favourites",
                if (starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                pick { vm.setStar(song.id, SubsonicClient.StarKind.SONG, !starred) },
            )
            SheetItem("Save for later", Icons.Filled.BookmarkBorder, pick { vm.saveForLater(song) })
            SheetItem("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd, pick { ui.pickPlaylistFor(listOf(song)) })
            SheetItem("Share", Icons.Filled.Share, pick { shareSong(context, song) })
            SheetItem("Go to track radio", Icons.Filled.Radio, pick { ui.navigate(Route.MixPage(MixSeed.FromSong(song))) })
            SheetItem("Credits", Icons.Filled.Info, pick(onCredits))
            song.albumId?.let { id -> SheetItem("Go to album", Icons.Filled.Album, pick { ui.navigate(Route.AlbumPage(id)) }) }
            song.artistId?.let { id -> SheetItem("Go to artist", Icons.Filled.Mic, pick { ui.navigate(Route.ArtistPage(id)) }) }
            lyrics?.let { (label, toggle) -> SheetItem(label, Icons.Filled.FormatQuote, pick(toggle)) }
            soundEffects?.let { open -> SheetItem("Sound effects", Icons.Filled.GraphicEq, pick(open)) }
            SheetItem("Block track", Icons.Filled.Block, pick { vm.blockSong(song) })
            SheetItem("Block artist", Icons.Filled.Block, pick { vm.blockArtist(song) })
        }
    }
}

@Composable
private fun SheetItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(26.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Who made the song (from Navidrome's tags) and what it is: album, year, genre, audio. */
@Composable
private fun CreditsDialog(vm: AppViewModel, song: Song, kbps: Int, decoded: app.navimuse.playback.DecodedFormat?, onDismiss: () -> Unit) {
    val loaded = rememberLoad("credits", song.id) { vm.credits(song) }
    val audio = remember(song.id, decoded) { AudioDetails.line(song, decoded, kbps) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Credits") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(song.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                when (val s = loaded.state) {
                    is Load.Loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    is Load.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                    is Load.Ok -> {
                        val lines = s.value + listOfNotNull(
                            audio?.let { Credit("Audio", it) },
                            Credit("Length", formatDuration(song.duration)).takeIf { song.duration > 0 },
                        )
                        lines.forEach { c ->
                            Text(c.role, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(c.value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 10.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** A round, softly filled icon button, like Tidal's on the playing screen. */
@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, modifier = Modifier.size(24.dp))
    }
}

/** Shares the song's name and artist as text. */
private fun shareSong(context: Context, song: Song) {
    val text = buildString {
        append(song.title).append(" — ").append(song.artist)
        if (song.album.isNotBlank()) append(" (").append(song.album).append(")")
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Opens the phone's output picker (Bluetooth, speakers, Cast), the panel Android shows for
 * media. Falls back to Bluetooth settings on phones without it.
 */
private fun openOutputSwitcher(context: Context) {
    val pkg = context.packageName
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ok = runCatching {
            context.sendBroadcast(
                Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                    .setPackage("com.android.systemui")
                    .putExtra("package_name", pkg),
            )
        }.isSuccess
        if (ok) return
    }
    val panel = Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
        .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", pkg)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(panel) }.onFailure {
        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/**
 * The cover art: swipe for the next or previous song. Its shape and size come from Customize,
 * or from a Symfonium style that sets them (rounded, inset, uncropped or spinning).
 */
@Composable
private fun ColumnScope.NowPlayingArt(
    vm: AppViewModel,
    song: Song,
    playing: Boolean,
    st: NowPlayingStyle?,
    look: Look,
    onTap: (() -> Unit)? = null,
) {
    val tapSource = remember { MutableInteractionSource() }
    val record = st?.coverRotates == true || (st?.coverCornerPercent == null && look.playerArt == PlayerArt.RECORD)
    val rotation = remember { Animatable(0f) }
    // With "Remove animations" on, a turn would finish at once and the loop would redraw nonstop,
    // so the record stays still then.
    val context = LocalContext.current
    val animationsOn = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    LaunchedEffect(playing, record, animationsOn) {
        if (playing && record && animationsOn) {
            while (true) {
                rotation.snapTo(rotation.value % 360f)
                rotation.animateTo(rotation.value + 360f, tween(12_000, easing = LinearEasing))
            }
        }
    }
    val shape = when {
        record -> CircleShape
        st?.coverCornerPercent != null -> RoundedCornerShape(percent = st.coverCornerPercent)
        look.playerArtCorner == null -> CircleShape
        else -> RoundedCornerShape(look.playerArtCorner!!.dp)
    }
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .padding(horizontal = (st?.coverMarginH ?: look.playerArtMargin).dp)
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .aspectRatio(1f)
            .semantics { contentDescription = "Album art" }
            .let { if (onTap != null) it.clickable(tapSource, indication = null, onClickLabel = "Show lyrics", onClick = onTap) else it }
            .let { if (look.swipeArt) it.swipeToSkip(onNext = { vm.player.next() }, onPrevious = { vm.player.previous() }) else it }
            .graphicsLayer { rotationZ = if (record) rotation.value else 0f },
        contentAlignment = Alignment.Center,
    ) {
        CoverFill(
            song.coverArt,
            Modifier.fillMaxSize(),
            shape = shape,
            seed = song.album.ifBlank { song.title },
            fit = st?.coverNoCrop == true && !record,
        )
        if (record) {
            // A label ring and the spindle hole, so it reads as a record.
            Box(Modifier.fillMaxSize(0.34f).border(2.dp, Color.Black.copy(alpha = 0.28f), CircleShape))
            Box(Modifier.size(16.dp).background(MaterialTheme.colorScheme.background, CircleShape))
        }
    }
}

/**
 * Title (tap for the album) with an E for explicit, artist (tap for them), and a round favourite
 * button. Left-aligned like Tidal, or centred.
 */
@Composable
private fun TitleBlock(vm: AppViewModel, song: Song, starred: Boolean, align: TitleAlign) {
    val ui = LocalUi.current
    val scheme = MaterialTheme.colorScheme
    val centred = align == TitleAlign.CENTER
    val big = MaterialTheme.typography.headlineSmall
    val artistStyle = MaterialTheme.typography.titleMedium
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Balances the favourite button so the text sits in the middle.
        if (centred) Spacer(Modifier.width(52.dp))
        Column(Modifier.weight(1f), horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.title,
                    // Bigger than the headline style, and growing with the text size chosen in Customize.
                    style = big.copy(fontSize = big.fontSize * (26f / 22f), lineHeight = big.lineHeight * (30f / 28f)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (centred) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.weight(1f, fill = false).clickable(enabled = song.albumId != null) {
                        song.albumId?.let { ui.navigate(Route.AlbumPage(it)) }
                    },
                )
                if (song.explicit) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "E",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.background,
                        modifier = Modifier.background(scheme.onSurface, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                song.artist,
                style = artistStyle.copy(fontSize = artistStyle.fontSize * (19f / 16f)),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centred) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.clickable(enabled = song.artistId != null) {
                    song.artistId?.let { ui.navigate(Route.ArtistPage(it)) }
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        // In your favourites: a filled circle with a check. Not yet: an outlined plus.
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (starred) scheme.onSurface else Color.Transparent)
                .border(if (starred) 0.dp else 1.5.dp, scheme.onSurface.copy(alpha = 0.6f), CircleShape)
                .clickable { vm.setStar(song.id, SubsonicClient.StarKind.SONG, !starred) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (starred) Icons.Filled.Check else Icons.Filled.Add,
                if (starred) "Remove from favourites" else "Add to favourites",
                tint = if (starred) scheme.background else scheme.onSurface,
                modifier = Modifier.size(22.dp),
                key = if (starred) "action.favorite.filled" else "action.favorite",
            )
        }
    }
}

/**
 * The seek bar. Touch anywhere along it to jump there, or drag; the touch area is taller than
 * the line so it's easy to hit. Under it: position, audio details, duration.
 */
@Composable
private fun SeekBar(vm: AppViewModel, state: PlayerUiState, pos: Long, scale: Float, showTimes: Boolean, song: Song, kbps: Int) {
    val scheme = MaterialTheme.colorScheme
    val settings by vm.settings.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1)
    val value = if (dragging) dragValue else (pos.toFloat() / duration).coerceIn(0f, 1f)
    val haptics = LocalHapticFeedback.current
    fun fraction(x: Float, width: Int) = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height((36 * scale).coerceAtLeast(32f).dp)
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.player.seekTo((fraction(offset.x, size.width) * duration).toLong())
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragValue = fraction(offset.x, size.width)
                    },
                    onDragEnd = {
                        vm.player.seekTo((dragValue * duration).toLong())
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    dragValue = fraction(change.position.x, size.width)
                }
            }
            .semantics {
                contentDescription = "Seek"
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { target ->
                    vm.player.seekTo((target.coerceIn(0f, 1f) * duration).toLong())
                    true
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val track = ((if (dragging) 5 else 3) * scale).dp
        Box(Modifier.fillMaxWidth().height(track).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxWidth(value).fillMaxHeight().background(scheme.onSurface))
        }
        // The thumb, centred on the played position.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = ((if (dragging) 8 else 6) * scale).dp
            val h = ((if (dragging) 24 else 18) * scale).dp
            Box(
                Modifier
                    .offset(x = (maxWidth - w) * value)
                    .size(width = w, height = h)
                    .background(scheme.onSurface, RoundedCornerShape(50)),
            )
        }
    }
    if (showTimes) {
        // Position, the audio details (tap for more), duration.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val shownPos = if (dragging) (dragValue * duration).toLong() else pos
            Text(formatDuration((shownPos / 1000).toInt()), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FormatLine(song, state.decoded, kbps, settings.detailsStyle)
            }
            Text(formatDuration((state.durationMs / 1000).toInt()), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ControlButtons(vm: AppViewModel, state: PlayerUiState, look: Look) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (look.showShuffleRepeat) Arrangement.SpaceBetween else Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (look.showShuffleRepeat) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (state.shuffle) scheme.onSurface.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { vm.player.setShuffle(!state.shuffle) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Shuffle,
                    if (state.shuffle) "Shuffle on" else "Shuffle",
                    tint = if (state.shuffle) scheme.onSurface else scheme.onSurfaceVariant,
                    key = if (state.shuffle) "playback.shuffle.on" else "playback.shuffle",
                )
            }
        }
        IconButton(onClick = { vm.player.previous() }, modifier = Modifier.size((look.controlIcon + 20).dp)) {
            Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(look.controlIcon.dp))
        }
        Box(contentAlignment = Alignment.Center) {
            if (look.playButton == PlayButtonStyle.CIRCLE) {
                // A solid round button, the icon cut out of it.
                Box(
                    Modifier.size(look.playButtonSize.dp).clip(CircleShape).background(scheme.onSurface).clickable { vm.player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play",
                        tint = scheme.background,
                        modifier = Modifier.size((look.playButtonSize * 0.59f).dp),
                    )
                }
            } else {
                IconButton(onClick = { vm.player.togglePlay() }, modifier = Modifier.size(look.playButtonSize.dp)) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size((look.playButtonSize * 0.82f).dp),
                    )
                }
            }
            if (state.buffering) CircularProgressIndicator(Modifier.size((look.playButtonSize - 4).dp), strokeWidth = 2.dp, color = scheme.accent)
        }
        IconButton(onClick = { vm.player.next() }, enabled = state.hasNext, modifier = Modifier.size((look.controlIcon + 20).dp)) {
            Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(look.controlIcon.dp))
        }
        if (look.showShuffleRepeat) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (state.repeatMode != Player.REPEAT_MODE_OFF) scheme.onSurface.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { vm.player.cycleRepeat() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repeat",
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) scheme.onSurface else scheme.onSurfaceVariant,
                    key = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> "playback.repeat.one.on"
                        Player.REPEAT_MODE_ALL -> "playback.repeat.on"
                        else -> "playback.repeat"
                    },
                )
            }
        }
    }
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
private fun LyricsPanel(vm: AppViewModel, songId: String, positionMs: Long, modifier: Modifier = Modifier, onClose: () -> Unit = {}) {
    val state by vm.playerState.collectAsState()
    val song = state.current?.song
    val loaded = rememberLoad("lyrics", songId) { song?.let { vm.lyrics(it) } }
    // Tap anywhere that isn't a line to go back to the art (tapping a line jumps there).
    Box(
        modifier
            .semantics { contentDescription = "Lyrics" }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Show the album art", onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
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
    val source by vm.source.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Queue", style = MaterialTheme.typography.headlineMedium)
                Text("${state.queue.size} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = {
                val songs = state.queue.map { it.song }
                // Close the queue first, so the save box isn't stacked on top of it.
                onDismiss()
                ui.saveAsPlaylist(songs, queuePlaylistName(source))
            }) {
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
