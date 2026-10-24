package app.navimuse.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.navimuse.net.Album
import app.navimuse.net.Artist
import app.navimuse.net.Playlist
import app.navimuse.net.Song
import app.navimuse.net.SubsonicClient
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.Route
import app.navimuse.ui.formatDuration
import coil3.compose.AsyncImage

/** Things any screen can ask for: open a page, add songs to a playlist, save a mix. */
class UiActions(
    val vm: AppViewModel,
    val navigate: (Route) -> Unit,
    val pickPlaylistFor: (List<Song>) -> Unit,
    val saveAsPlaylist: (songs: List<Song>, suggestedName: String) -> Unit,
)

val LocalUi = staticCompositionLocalOf<UiActions> { error("UiActions not provided") }

/** Album art (or a placeholder while it loads / when there is none). */
@Composable
fun Cover(
    coverArt: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    placeholder: ImageVector = Icons.Filled.MusicNote,
) {
    val vm = LocalUi.current.vm
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(96, 900)
    val url = remember(coverArt, px) { vm.coverUrl(coverArt, px) }
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(placeholder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 2.6f))
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Big square art that fills the width it's given. */
@Composable
fun CoverFill(coverArt: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp), placeholder: ImageVector = Icons.Filled.Album) {
    val vm = LocalUi.current.vm
    val url = remember(coverArt) { vm.coverUrl(coverArt, 800) }
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(placeholder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            androidx.compose.material3.TextButton(onClick = onAction) { Text(action) }
        }
    }
}

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
    val ui = LocalUi.current
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArt) {
            Box(contentAlignment = Alignment.Center) {
                Cover(song.coverArt, size = 48.dp)
                if (isCurrent) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary) }
                }
            }
            Spacer(Modifier.width(12.dp))
        } else if (number != null) {
            Box(Modifier.widthIn(min = 32.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) {
                    Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                } else {
                    Text(number.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(song.artist, song.album.takeIf { showArt }).filter { !it.isNullOrBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            trailingText ?: formatDuration(song.duration),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            SongMenu(song, expanded = menu, onDismiss = { menu = false }, extra = extraMenu)
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
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem("Instant Mix", Icons.Filled.AutoAwesome) { onDismiss(); vm.instantMix(song) }
        if (info?.sonicSimilarity == true) {
            MenuItem("Similar songs", Icons.Filled.Waves) { onDismiss(); ui.navigate(Route.Similar(song)) }
        }
        HorizontalDivider()
        MenuItem("Play next", Icons.Filled.SkipNext) { onDismiss(); vm.playNext(listOf(song)) }
        MenuItem("Add to queue", Icons.AutoMirrored.Filled.QueueMusic) { onDismiss(); vm.addToQueue(listOf(song)) }
        MenuItem("Add to playlist…", Icons.AutoMirrored.Filled.PlaylistAdd) { onDismiss(); ui.pickPlaylistFor(listOf(song)) }
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

@Composable
fun AlbumCard(album: Album, modifier: Modifier = Modifier, width: Dp = 150.dp, onClick: () -> Unit) {
    Column(modifier.width(width).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Cover(album.coverArt, size = width - 8.dp, shape = RoundedCornerShape(10.dp), placeholder = Icons.Filled.Album)
        Spacer(Modifier.height(6.dp))
        Text(album.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An album that fills its grid cell. */
@Composable
fun AlbumTile(album: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp)) {
        CoverFill(album.coverArt, Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(10.dp))
        Spacer(Modifier.height(6.dp))
        Text(album.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Cover(artist.coverArt, size = 48.dp, shape = CircleShape, placeholder = Icons.Filled.Person)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        modifier.width(96.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Cover(artist.coverArt, size = 84.dp, shape = CircleShape, placeholder = Icons.Filled.Person)
        Spacer(Modifier.height(6.dp))
        Text(artist.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PlaylistRow(playlist: Playlist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(playlist.coverArt, size = 52.dp, placeholder = Icons.AutoMirrored.Filled.QueueMusic)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${playlist.songCount} songs" + (playlist.owner?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (playlist.isAudioMuseManaged) AudioMuseBadge()
    }
}

/** Marks a playlist AudioMuse-AI deletes and remakes on every clustering run. */
@Composable
fun AudioMuseBadge(modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(50)) {
        Text(
            "Replaced by AudioMuse",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** A tonal pill button with an icon, for rows of actions under a header. */
@Composable
fun ActionPill(text: String, icon: ImageVector, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    if (primary) {
        androidx.compose.material3.Button(onClick = onClick, modifier = modifier) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text)
        }
    } else {
        androidx.compose.material3.FilledTonalButton(onClick = onClick, modifier = modifier) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text)
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
    val (bg, fg) = when (tone) {
        CardTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
        CardTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        CardTone.GOOD -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(modifier.fillMaxWidth(), color = bg, contentColor = fg, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (action != null) {
                Spacer(Modifier.height(2.dp))
                action()
            }
        }
    }
}

enum class CardTone { NEUTRAL, WARNING, GOOD }
