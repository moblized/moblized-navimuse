package app.navimuse.ui.components

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlinx.coroutines.launch
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import app.navimuse.ui.icons.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import app.navimuse.downloads.Downloads
import app.navimuse.net.Album
import app.navimuse.net.Artist
import app.navimuse.net.Playlist
import app.navimuse.net.Song
import app.navimuse.net.SubsonicClient
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.Route
import app.navimuse.ui.formatDuration
import app.navimuse.ui.theme.ArtShape
import app.navimuse.ui.theme.LocalLook
import app.navimuse.ui.theme.accent
import app.navimuse.ui.theme.warning
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Things any screen can ask for: open a page, add songs to a playlist, save a mix. */
class UiActions(
    val vm: AppViewModel,
    val navigate: (Route) -> Unit,
    val pickPlaylistFor: (List<Song>) -> Unit,
    val saveAsPlaylist: (songs: List<Song>, suggestedName: String) -> Unit,
    /** Asks once for permission to show download and update notifications (Android 13+). */
    val askNotifications: () -> Unit = {},
    /** Switches to a bottom tab, closing any open page. */
    val selectTab: (app.navimuse.ui.Tab) -> Unit = {},
    /** Opens the playing screen (when something is playing). */
    val openPlayer: () -> Unit = {},
)

val LocalUi = staticCompositionLocalOf<UiActions> { error("UiActions not provided") }

// --- Art ----------------------------------------------------------------------------------------

/** Two colours for something without art, picked from its name so it always looks the same. */
fun placeholderColors(seed: String): Pair<Color, Color> {
    val hue = ((seed.hashCode().toLong() and 0xffffffffL) % 360).toFloat()
    return Color.hsl(hue, 0.42f, 0.34f) to Color.hsl((hue + 38f) % 360f, 0.52f, 0.14f)
}

/** A gradient tile standing in for missing art. */
@Composable
fun ArtPlaceholder(seed: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = remember(seed) { placeholderColors(seed) }
    BoxWithConstraints(
        modifier.background(Brush.linearGradient(listOf(colors.first, colors.second))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(maxWidth * 0.34f))
    }
}

/** Album art at a fixed size (or a gradient tile while it loads / when there is none). */
@Composable
fun Cover(
    coverArt: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = ArtShape,
    placeholder: ImageVector = Icons.Filled.MusicNote,
    seed: String? = null,
) {
    val vm = LocalUi.current.vm
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(96, 900)
    val url = remember(coverArt, px) { vm.coverUrl(coverArt, px) }
    Box(modifier.size(size).clip(shape)) {
        ArtPlaceholder(seed ?: coverArt.orEmpty(), placeholder, Modifier.fillMaxSize())
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Big art that fills the space it's given. */
@Composable
fun CoverFill(
    coverArt: String?,
    modifier: Modifier = Modifier,
    shape: Shape = ArtShape,
    placeholder: ImageVector = Icons.Filled.Album,
    seed: String? = null,
    /** Show the whole picture instead of filling the square. */
    fit: Boolean = false,
) {
    val vm = LocalUi.current.vm
    val url = remember(coverArt) { vm.coverUrl(coverArt, 900) }
    Box(modifier.clip(shape)) {
        if (!fit) ArtPlaceholder(seed ?: coverArt.orEmpty(), placeholder, Modifier.fillMaxSize())
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val artColorCache = LruCache<String, Int>(300)

/**
 * The strongest colour in a piece of art, for the glow behind album pages and the player.
 * Falls back to the placeholder colour for [seed] when there's no art.
 */
@Composable
fun rememberArtColor(coverArt: String?, seed: String): Color {
    val vm = LocalUi.current.vm
    val url = remember(coverArt) { vm.coverUrl(coverArt, 160) }
    return rememberArtColorOf(url, seed)
}

/** The same, for a small cover-art link (works outside the app's screens, for the theme). */
@Composable
fun rememberArtColorOf(url: String?, seed: String): Color {
    val context = LocalContext.current
    val fallback = remember(seed) { placeholderColors(seed).first }
    var color by remember(url, seed) { mutableStateOf(url?.let { artColorCache.get(it) }?.let { Color(it) } ?: fallback) }
    LaunchedEffect(url) {
        if (url == null || artColorCache.get(url) != null) return@LaunchedEffect
        val picked = withContext(Dispatchers.IO) { runCatching { artColorOf(context, url) }.getOrNull() }
        if (picked != null) {
            artColorCache.put(url, picked)
            color = Color(picked)
        }
    }
    return animateColorAsState(color, tween(600), label = "artColor").value
}

private suspend fun artColorOf(context: Context, url: String): Int? {
    val request = ImageRequest.Builder(context).data(url).size(120).allowHardware(false).build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    val palette = Palette.from(result.image.toBitmap()).maximumColorCount(16).generate()
    return (palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch)?.rgb
}

/** [art] made deep enough to sit behind white text (dark look) or pale enough for black text. */
fun stageColor(art: Color, dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(art.toArgb(), hsl)
    if (dark) {
        hsl[1] = hsl[1].coerceAtMost(0.7f)
        hsl[2] = hsl[2].coerceIn(0.16f, 0.3f)
    } else {
        hsl[1] = hsl[1].coerceAtMost(0.55f)
        hsl[2] = hsl[2].coerceIn(0.84f, 0.9f)
    }
    return Color(ColorUtils.HSLToColor(hsl))
}

/** A wash of [color] fading into the page background, behind headers and the player. */
fun glow(color: Color, background: Color): Brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f).compositeOn(background), background))

private fun Color.compositeOn(bg: Color): Color = Color(
    red = red * alpha + bg.red * (1 - alpha),
    green = green * alpha + bg.green * (1 - alpha),
    blue = blue * alpha + bg.blue * (1 - alpha),
    alpha = 1f,
)

/**
 * Swipe left for the next song, right for the previous one. The content follows the finger,
 * slides out when the swipe counts, and the new song slides in from the other side.
 */
@Composable
fun Modifier.swipeToSkip(onNext: () -> Unit, onPrevious: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    return this
        .graphicsLayer {
            translationX = offset.value
            alpha = 1f - (abs(offset.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.6f)
        }
        .pointerInput(Unit) {
            val width = size.width.toFloat()
            detectHorizontalDragGestures(
                onDragEnd = {
                    scope.launch {
                        val dx = offset.value
                        if (abs(dx) >= threshold) {
                            val dir = if (dx < 0) -1f else 1f
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            offset.animateTo(dir * width, tween(120))
                            if (dir < 0) next() else previous()
                            offset.snapTo(-dir * width * 0.5f)
                            offset.animateTo(0f, tween(220))
                        } else {
                            offset.animateTo(0f, spring())
                        }
                    }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
            ) { change, amount ->
                change.consume()
                scope.launch { offset.snapTo(offset.value + amount) }
            }
        }
}

// --- Headers ------------------------------------------------------------------------------------

/** The big title at the top of each tab, which scrolls away with the page. */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onAction).padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * The bar over a detail page: a back button, and the page's title once the header has
 * scrolled away ([solid]).
 */
@Composable
fun FloatingTopBar(title: String, solid: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    val bg by animateColorAsState(
        if (solid) MaterialTheme.colorScheme.surface else Color.Transparent,
        tween(200),
        label = "topBar",
    )
    Row(
        modifier.fillMaxWidth().background(bg).statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(
            if (solid) title else "",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

// --- Rows and cards -----------------------------------------------------------------------------

/**
 * One song in a list, with a menu of everything you can do with it.
 * [onPlay] plays it (usually as part of its list).
 */
@Composable
fun SongRow(
    song: Song,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    showArt: Boolean = true,
    trailingText: String? = null,
    isCurrent: Boolean = false,
    extraMenu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ui = LocalUi.current
    val downloads by ui.vm.downloads.collectAsState()
    val saved = downloads[song.id]?.state == Downloads.State.DONE
    // Swipe right to add the song to the queue (can be turned off in Customize).
    val swipe = LocalLook.current.swipeToQueue
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val mark = with(LocalDensity.current) { 88.dp.toPx() }
    val accent = MaterialTheme.colorScheme.accent
    val onAccent = MaterialTheme.colorScheme.onSecondary
    Box(modifier.fillMaxWidth()) {
        if (drag.value > 0f) {
            // What's uncovered as the row slides: an accent strip saying what letting go will do.
            Row(
                Modifier
                    .matchParentSize()
                    .drawWithContent { clipRect(right = drag.value) { this@drawWithContent.drawContent() } }
                    .background(if (drag.value >= mark) accent else accent.copy(alpha = 0.5f))
                    .padding(start = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = onAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add to queue", style = MaterialTheme.typography.labelLarge, color = onAccent, maxLines = 1)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = drag.value }
                .pointerInput(swipe, song.id) {
                    if (!swipe) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (drag.value >= mark) ui.vm.addToQueue(listOf(song))
                                drag.animateTo(0f, spring())
                            }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(0f, spring()) } },
                    ) { change, amount ->
                        // Only rightwards; a left swipe does nothing.
                        if (amount > 0f || drag.value > 0f) {
                            change.consume()
                            val before = drag.value
                            val next = (before + amount).coerceIn(0f, size.width * 0.6f)
                            if (before < mark && next >= mark) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { drag.snapTo(next) }
                        }
                    }
                }
                .clickable(onClick = onPlay)
                .padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showArt) {
                Box(contentAlignment = Alignment.Center) {
                    Cover(song.coverArt, size = 48.dp, seed = song.album.ifBlank { song.title })
                    if (isCurrent) {
                        Box(Modifier.size(48.dp).clip(ArtShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.accent, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
            } else if (number != null) {
                Box(Modifier.widthIn(min = 26.dp), contentAlignment = Alignment.CenterStart) {
                    if (isCurrent) {
                        Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.accent, modifier = Modifier.size(18.dp))
                    } else {
                        Text(number.toString(), style = MaterialTheme.typography.bodyMedium, color = muted)
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCurrent) MaterialTheme.colorScheme.accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (saved) {
                        Icon(Icons.Filled.OfflinePin, "Downloaded", tint = MaterialTheme.colorScheme.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        listOf(song.artist, song.album.takeIf { showArt }).filter { !it.isNullOrBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                trailingText ?: formatDuration(song.duration),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = muted) }
                SongMenu(song, expanded = menu, onDismiss = { menu = false }, extra = extraMenu)
            }
        }
    }
}

@Composable
fun SongMenu(song: Song, expanded: Boolean, onDismiss: () -> Unit, extra: (@Composable (close: () -> Unit) -> Unit)? = null) {
    val ui = LocalUi.current
    val vm = ui.vm
    val stars by vm.stars.collectAsState()
    val info by vm.serverInfo.collectAsState()
    val starred = stars[song.id] ?: song.starred
    val downloads by vm.downloads.collectAsState()
    val saved = downloads[song.id] != null
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem("Instant Mix", Icons.Filled.AutoAwesome) { onDismiss(); vm.instantMix(song) }
        if (info?.sonicSimilarity == true) {
            MenuItem("Similar songs", Icons.Filled.Waves) { onDismiss(); ui.navigate(Route.Similar(song)) }
        }
        HorizontalDivider()
        MenuItem("Play next", Icons.Filled.SkipNext) { onDismiss(); vm.playNext(listOf(song)) }
        MenuItem("Add to queue", Icons.AutoMirrored.Filled.QueueMusic) { onDismiss(); vm.addToQueue(listOf(song)) }
        MenuItem("Add to playlist…", Icons.AutoMirrored.Filled.PlaylistAdd) { onDismiss(); ui.pickPlaylistFor(listOf(song)) }
        if (saved) {
            MenuItem("Remove download", Icons.Filled.RemoveCircleOutline) { onDismiss(); vm.removeDownloads(listOf(song)) }
        } else {
            MenuItem("Download", Icons.Filled.FileDownload) { onDismiss(); ui.askNotifications(); vm.download(listOf(song)) }
        }
        MenuItem(if (starred) "Remove from favourites" else "Add to favourites", if (starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder) {
            onDismiss()
            vm.setStar(song.id, SubsonicClient.StarKind.SONG, !starred)
        }
        HorizontalDivider()
        song.albumId?.let { id -> MenuItem("Go to album", Icons.Filled.Album) { onDismiss(); ui.navigate(Route.AlbumPage(id)) } }
        song.artistId?.let { id -> MenuItem("Go to artist", Icons.Filled.Person) { onDismiss(); ui.navigate(Route.ArtistPage(id)) } }
        extra?.invoke(onDismiss)
    }
}

@Composable
fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

/** An album in a sideways shelf. */
@Composable
fun AlbumCard(album: Album, modifier: Modifier = Modifier, width: Dp = LocalLook.current.cardSize.dp.dp, onClick: () -> Unit) {
    Column(modifier.width(width).clickable(onClick = onClick)) {
        Cover(album.coverArt, size = width, placeholder = Icons.Filled.Album, seed = album.name)
        Spacer(Modifier.height(8.dp))
        Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An album that fills its grid cell. */
@Composable
fun AlbumTile(album: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        CoverFill(album.coverArt, Modifier.fillMaxWidth().aspectRatio(1f), seed = album.name)
        Spacer(Modifier.height(8.dp))
        Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(album.artist, album.year.takeIf { it > 0 }?.toString()).filterNotNull().joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ArtistRow(artist: Artist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(artist.coverArt, size = 52.dp, shape = CircleShape, placeholder = Icons.Filled.Person, seed = artist.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist.albumCount > 0) {
                Text(
                    "${artist.albumCount} album${if (artist.albumCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ArtistBubble(artist: Artist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.width(108.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Cover(artist.coverArt, size = 100.dp, shape = CircleShape, placeholder = Icons.Filled.Person, seed = artist.name)
        Spacer(Modifier.height(8.dp))
        Text(artist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PlaylistRow(playlist: Playlist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(playlist.coverArt, size = 56.dp, placeholder = Icons.AutoMirrored.Filled.QueueMusic, seed = playlist.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${playlist.songCount} tracks" + (playlist.owner?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (playlist.isAudioMuseManaged) {
                Spacer(Modifier.height(4.dp))
                AudioMuseBadge()
            }
        }
    }
}

/** A playlist in a sideways shelf. */
@Composable
fun PlaylistCard(playlist: Playlist, modifier: Modifier = Modifier, width: Dp = LocalLook.current.cardSize.dp.dp, onClick: () -> Unit) {
    Column(modifier.width(width).clickable(onClick = onClick)) {
        Cover(playlist.coverArt, size = width, placeholder = Icons.AutoMirrored.Filled.QueueMusic, seed = playlist.name)
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${playlist.songCount} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Marks a playlist AudioMuse-AI deletes and remakes on every clustering run. */
@Composable
fun AudioMuseBadge(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.warning
    Text(
        "Replaced by AudioMuse",
        style = MaterialTheme.typography.labelSmall,
        color = c,
        modifier = modifier.border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** What's actually reaching your ears: the file's format, or the bitrate the server converts to. */
fun qualityLabel(song: Song, streamingKbps: Int): String? {
    val format = song.suffix?.uppercase()?.takeIf { it.isNotBlank() }
    return when {
        streamingKbps > 0 && (song.bitRate == 0 || song.bitRate > streamingKbps) ->
            "$streamingKbps kbps" + (format?.let { " from $it" } ?: "")
        format != null && song.bitRate > 0 -> "$format ${"%,d".format(song.bitRate)} kbps"
        else -> format
    }
}

/** An outlined label for audio quality; [strong] fills it in, for Dolby Atmos. */
@Composable
fun QualityBadge(label: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    val c = MaterialTheme.colorScheme.accent
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (strong) MaterialTheme.colorScheme.onSecondary else c,
        modifier = modifier
            .background(if (strong) c else Color.Transparent, RoundedCornerShape(4.dp))
            .border(1.dp, if (strong) c else c.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Opens the phone's equalizer and sound effects for NaviMuse's audio (on Samsung phones, where
 * Dolby Atmos is switched on). Null when the phone has no such screen.
 */
@Composable
fun rememberSoundEffects(): (() -> Unit)? {
    val context = LocalContext.current
    val vm = LocalUi.current.vm
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val available = remember {
        Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).resolveActivity(context.packageManager) != null
    }
    if (!available) return null
    return {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, vm.audioSessionId())
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        runCatching { launcher.launch(intent) }.onFailure { vm.say("This phone has no sound effects screen.") }
    }
}

// --- Buttons ------------------------------------------------------------------------------------

/** A pill button: solid for the main action on a page, outlined for the rest. */
@Composable
fun ActionPill(text: String, icon: ImageVector?, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
    if (primary) {
        Button(onClick = onClick, modifier = modifier.height(44.dp), contentPadding = PaddingValues(horizontal = 22.dp), content = content)
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = PaddingValues(horizontal = 18.dp),
            content = content,
        )
    }
}

/** A round outlined icon button that sits next to [ActionPill]s. */
@Composable
fun CircleAction(icon: ImageVector, description: String, modifier: Modifier = Modifier, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Icon(icon, description, tint = if (tint == Color.Unspecified) LocalContentColor.current else tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * Download button for a set of songs: an arrow when they aren't saved, a ring while they
 * download, and a filled pin once every one is on the phone (tap again to remove them).
 */
@Composable
fun DownloadAction(songs: List<Song>, onDownload: () -> Unit, onRemove: () -> Unit) {
    val ui = LocalUi.current
    val downloads by ui.vm.downloads.collectAsState()
    val states = songs.map { downloads[it.id]?.state }
    val done = songs.isNotEmpty() && states.all { it == Downloads.State.DONE }
    val busy = states.any { it == Downloads.State.QUEUED || it == Downloads.State.DOWNLOADING }
    LaunchedEffect(busy) {
        while (busy) {
            ui.vm.refreshDownloadProgress()
            kotlinx.coroutines.delay(1000)
        }
    }
    val progress = if (songs.isEmpty()) 0f else songs.sumOf { s ->
        val item = downloads[s.id]
        when (item?.state) {
            Downloads.State.DONE -> 100.0
            Downloads.State.DOWNLOADING -> item.percent.toDouble()
            else -> 0.0
        }
    }.toFloat() / songs.size / 100f
    Box(contentAlignment = Alignment.Center) {
        when {
            done -> CircleAction(Icons.Filled.OfflinePin, "Remove download", tint = MaterialTheme.colorScheme.accent, onClick = onRemove)
            busy -> CircleAction(Icons.Filled.FileDownload, "Downloading", tint = MaterialTheme.colorScheme.accent, onClick = onRemove)
            else -> CircleAction(Icons.Filled.FileDownload, "Download") {
                ui.askNotifications()
                onDownload()
            }
        }
        if (busy) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0.02f, 1f) },
                modifier = Modifier.size(44.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.accent,
                trackColor = Color.Transparent,
            )
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.NEUTRAL,
    action: (@Composable () -> Unit)? = null,
) {
    val tint = when (tone) {
        CardTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        CardTone.WARNING -> MaterialTheme.colorScheme.warning
        CardTone.GOOD -> MaterialTheme.colorScheme.accent
    }
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (action != null) 6.dp else 16.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (action != null) Box(Modifier.padding(top = 2.dp)) { action() }
            }
        }
    }
}

enum class CardTone { NEUTRAL, WARNING, GOOD }
