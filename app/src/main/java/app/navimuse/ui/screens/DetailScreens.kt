package app.navimuse.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navimuse.net.Song
import app.navimuse.playback.Dolby
import app.navimuse.ui.components.QualityBadge
import app.navimuse.net.SubsonicClient
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.MixSeed
import app.navimuse.ui.Route
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.components.AlbumCard
import app.navimuse.ui.components.ArtistBubble
import app.navimuse.ui.components.CardTone
import app.navimuse.ui.components.CircleAction
import app.navimuse.ui.components.CoverFill
import app.navimuse.ui.components.DownloadAction
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.FloatingTopBar
import app.navimuse.ui.components.InfoCard
import app.navimuse.ui.components.LoadBox
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.MenuItem
import app.navimuse.ui.components.SectionHeader
import app.navimuse.ui.components.SongRow
import app.navimuse.ui.components.glow
import app.navimuse.ui.components.placeholderColors
import app.navimuse.ui.components.rememberArtColor
import app.navimuse.ui.components.rememberLoad
import app.navimuse.ui.components.stageColor
import app.navimuse.ui.formatTotal
import app.navimuse.ui.theme.ArtShape
import app.navimuse.ui.theme.accent
import app.navimuse.ui.theme.isDark

/** True once the page's header has scrolled out from under the top bar. */
@Composable
private fun LazyListState.pastHeader(): Boolean {
    val past by remember(this) { derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 600 } }
    return past
}

/** A page with a header that scrolls under a floating back bar. */
@Composable
private fun DetailPage(
    title: String,
    onBack: () -> Unit,
    state: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (LazyListState) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content(state)
        FloatingTopBar(title, solid = state.pastHeader(), onBack = onBack, actions = actions)
    }
}

/** Big art on a wash of its own colour, then the title, a tappable subtitle and details. */
@Composable
private fun ArtHeader(
    coverArt: String?,
    seed: String,
    title: String,
    subtitle: String?,
    detail: String?,
    placeholder: ImageVector,
    onSubtitle: (() -> Unit)? = null,
    badge: String? = null,
) {
    val art = rememberArtColor(coverArt, seed)
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(glow(stageColor(art, scheme.isDark), scheme.background))
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 64.dp),
    ) {
        CoverFill(
            coverArt,
            Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(0.74f).widthIn(max = 340.dp).aspectRatio(1f),
            placeholder = placeholder,
            seed = seed,
        )
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onSubtitle != null) Modifier.clickable(onClick = onSubtitle) else Modifier,
            )
        }
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (badge != null) {
            Spacer(Modifier.height(10.dp))
            QualityBadge(badge, strong = badge == Dolby.ATMOS)
        }
    }
}

/** Two wide pill buttons side by side; give each `Modifier.weight(1f)`. */
@Composable
private fun ActionsRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Round icon buttons under a header: favourite, Instant Mix, more. */
@Composable
private fun IconsRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun StarAction(id: String, kind: SubsonicClient.StarKind, initial: Boolean) {
    val vm = LocalUi.current.vm
    val stars by vm.stars.collectAsState()
    val starred = stars[id] ?: initial
    CircleAction(
        if (starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        if (starred) "Remove from favourites" else "Add to favourites",
        tint = if (starred) MaterialTheme.colorScheme.accent else Color.Unspecified,
    ) { vm.setStar(id, kind, !starred) }
}

/** The "…" button: play next, add to queue, add to a playlist. */
@Composable
private fun MoreAction(songs: List<Song>) {
    val ui = LocalUi.current
    var open by remember { mutableStateOf(false) }
    Box {
        CircleAction(Icons.Filled.MoreHoriz, "More") { open = true }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("Play next", Icons.Filled.SkipNext) { open = false; ui.vm.playNext(songs) }
            MenuItem("Add to queue", Icons.AutoMirrored.Filled.QueueMusic) { open = false; ui.vm.addToQueue(songs) }
            MenuItem("Add to playlist…", Icons.AutoMirrored.Filled.PlaylistAdd) { open = false; ui.pickPlaylistFor(songs) }
        }
    }
}

@Composable
fun AlbumScreen(vm: AppViewModel, albumId: String, onBack: () -> Unit) {
    val ui = LocalUi.current
    val player by vm.playerState.collectAsState()
    val loaded = rememberLoad(albumId) { vm.api().album(albumId) }
    val dolbyKnown by vm.dolby.collectAsState()
    DetailPage(loaded.value?.album?.name ?: "Album", onBack) { state ->
        LoadBox(loaded, Modifier.statusBarsPadding().padding(top = 56.dp)) { detail ->
            val a = detail.album
            val songs = detail.songs
            LaunchedEffect(a.id) { vm.checkDolby(a.id, songs) }
            val dolbyBadge = dolbyKnown["a:${a.id}"]?.ifEmpty { null }
            val multiDisc = songs.map { it.disc }.distinct().size > 1
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    ArtHeader(
                        a.coverArt,
                        a.name,
                        a.name,
                        a.artist,
                        listOfNotNull(
                            a.year.takeIf { it > 0 }?.toString(),
                            a.genre,
                            "${songs.size} tracks",
                            formatTotal(songs.sumOf { it.duration }),
                        ).joinToString(" · "),
                        Icons.Filled.Album,
                        onSubtitle = a.artistId?.let { id -> { ui.navigate(Route.ArtistPage(id)) } },
                        badge = dolbyBadge,
                    )
                }
                item {
                    IconsRow {
                        StarAction(a.id, SubsonicClient.StarKind.ALBUM, a.starred)
                        DownloadAction(songs, onDownload = { vm.download(songs) }, onRemove = { vm.removeDownloads(songs) })
                        CircleAction(Icons.Filled.AutoAwesome, "Instant Mix") { ui.navigate(Route.MixPage(MixSeed.FromAlbum(a))) }
                        MoreAction(songs)
                    }
                    ActionsRow {
                        ActionPill("Play", Icons.Filled.PlayArrow, Modifier.weight(1f), primary = true) { vm.play(songs) }
                        ActionPill("Shuffle", Icons.Filled.Shuffle, Modifier.weight(1f)) { vm.play(songs, shuffle = true) }
                    }
                }
                itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                    if (multiDisc && (i == 0 || songs[i - 1].disc != s.disc)) {
                        Text(
                            "Disc ${s.disc}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    SongRow(
                        s,
                        onPlay = { vm.play(songs, i) },
                        number = s.track.takeIf { it > 0 } ?: (i + 1),
                        showArt = false,
                        isCurrent = player.current?.song?.id == s.id,
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistScreen(vm: AppViewModel, artistId: String, onBack: () -> Unit) {
    val ui = LocalUi.current
    val player by vm.playerState.collectAsState()
    val loaded = rememberLoad(artistId) { vm.api().artist(artistId) }
    val infoLoad = rememberLoad("info", artistId) { vm.api().artistInfo(artistId) }
    DetailPage(loaded.value?.artist?.name ?: "Artist", onBack) { state ->
        LoadBox(loaded, Modifier.statusBarsPadding().padding(top = 56.dp)) { detail ->
            val artist = detail.artist
            val topLoad = rememberLoad("top", artist.name) { vm.api().topSongs(artist.name, 10) }
            val info = infoLoad.value
            val bg = MaterialTheme.colorScheme.background
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    // Full-bleed photo fading into the page, with the name set large over its foot.
                    Box(Modifier.fillMaxWidth().height(360.dp)) {
                        CoverFill(artist.coverArt, Modifier.fillMaxSize(), shape = RectangleShape, placeholder = Icons.Filled.Person, seed = artist.name)
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to bg)))
                        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp)) {
                            Text(
                                artist.name,
                                style = MaterialTheme.typography.displayMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${detail.albums.size} album${if (detail.albums.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    IconsRow {
                        StarAction(artist.id, SubsonicClient.StarKind.ARTIST, artist.starred)
                    }
                    ActionsRow {
                        ActionPill("Artist radio", Icons.Filled.Radio, Modifier.weight(1f), primary = true) { vm.artistRadio(artist) }
                        topLoad.value?.takeIf { it.isNotEmpty() }?.let { top ->
                            ActionPill("Top tracks", Icons.Filled.PlayArrow, Modifier.weight(1f)) { vm.play(top) }
                        }
                    }
                }
                val top = topLoad.value.orEmpty()
                if (top.isNotEmpty()) {
                    item { SectionHeader("Popular tracks") }
                    itemsIndexed(top.take(5), key = { _, s -> "top-${s.id}" }) { i, s ->
                        SongRow(s, onPlay = { vm.play(top, i) }, isCurrent = player.current?.song?.id == s.id)
                    }
                }
                item { SectionHeader("Albums") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(detail.albums, key = { it.id }) { a -> AlbumCard(a) { ui.navigate(Route.AlbumPage(a.id)) } }
                    }
                }
                val similar = info?.similar.orEmpty()
                if (similar.isNotEmpty()) {
                    item { SectionHeader("Sounds like") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(similar, key = { it.id }) { a -> ArtistBubble(a) { ui.navigate(Route.ArtistPage(a.id)) } }
                        }
                    }
                }
                info?.biography?.let { bio ->
                    item { SectionHeader("About") }
                    item { Biography(bio) }
                }
            }
        }
    }
}

@Composable
private fun Biography(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp).animateContentSize()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if (expanded) "Show less" else "Read more") }
    }
}

@Composable
fun PlaylistScreen(vm: AppViewModel, playlistId: String, onBack: () -> Unit) {
    val ui = LocalUi.current
    val player by vm.playerState.collectAsState()
    val version by vm.playlistsVersion.collectAsState()
    val loaded = rememberLoad(playlistId, version) { vm.api().playlist(playlistId) }
    DetailPage(loaded.value?.playlist?.name ?: "Playlist", onBack) { state ->
        LoadBox(loaded, Modifier.statusBarsPadding().padding(top = 56.dp)) { detail ->
            val p = detail.playlist
            val songs = detail.songs
            val from = AppViewModel.PlaySource(p.name, Route.PlaylistPage(p.id))
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    ArtHeader(
                        p.coverArt,
                        p.name,
                        p.name,
                        p.owner?.let { "By $it" },
                        "${songs.size} tracks · ${formatTotal(songs.sumOf { it.duration })}",
                        Icons.AutoMirrored.Filled.QueueMusic,
                    )
                }
                if (p.isAudioMuseManaged) {
                    item {
                        InfoCard(
                            title = "AudioMuse-AI will replace this playlist",
                            body = "Its name ends in _automatic, so AudioMuse-AI deletes it and makes a new one every time it runs clustering. " +
                                "Keep a copy to hold on to these songs.",
                            icon = Icons.Filled.Shield,
                            tone = CardTone.WARNING,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                            action = { TextButton(onClick = { vm.keepCopy(p) }, contentPadding = PaddingValues(0.dp)) { Text("Keep a copy") } },
                        )
                    }
                }
                item {
                    IconsRow {
                        // Kept in sync: songs added to the playlist later download too.
                        DownloadAction(
                            songs,
                            onDownload = { vm.setPlaylistAutoDownload(p, songs, true) },
                            onRemove = { vm.setPlaylistAutoDownload(p, songs, false) },
                        )
                        if (songs.isNotEmpty()) {
                            CircleAction(Icons.Filled.AutoAwesome, "Instant Mix") { ui.navigate(Route.MixPage(MixSeed.FromSong(songs.random()))) }
                        }
                        CircleAction(Icons.Filled.ContentCopy, "Copy playlist") {
                            ui.saveAsPlaylist(songs, "${app.navimuse.net.PlaylistNames.pretty(p.name)} copy")
                        }
                        MoreAction(songs)
                    }
                    ActionsRow {
                        ActionPill("Play", Icons.Filled.PlayArrow, Modifier.weight(1f), primary = true) { vm.play(songs, from = from) }
                        ActionPill("Shuffle", Icons.Filled.Shuffle, Modifier.weight(1f)) { vm.play(songs, shuffle = true, from = from) }
                    }
                }
                if (songs.isEmpty()) item { EmptyBox("This playlist is empty.") }
                itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { i, s ->
                    SongRow(s, onPlay = { vm.play(songs, i, from = from) }, isCurrent = player.current?.song?.id == s.id)
                }
            }
        }
    }
}

/** Songs that sound like one song, with AudioMuse-AI's match score. */
@Composable
fun SimilarScreen(vm: AppViewModel, song: Song, onBack: () -> Unit) {
    val ui = LocalUi.current
    val loaded = rememberLoad("similar", song.id) { vm.sonicSimilar(song) }
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("Sounds like ${song.title}", solid = true, onBack = onBack)
        LoadBox(loaded) { matches ->
            val songs = listOf(song) + matches.map { it.song }.filter { it.id != song.id }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    ActionsRow {
                        ActionPill("Play all", Icons.Filled.PlayArrow, Modifier.weight(1f), primary = true) { vm.play(songs) }
                        ActionPill("Save", Icons.Filled.Save, Modifier.weight(1f)) { ui.saveAsPlaylist(songs, "Sounds like ${song.title}") }
                    }
                }
                if (matches.isEmpty()) item { EmptyBox("AudioMuse-AI found nothing similar. It may not have analysed this song yet.") }
                itemsIndexed(matches, key = { _, m -> m.song.id }) { i, m ->
                    SongRow(
                        m.song,
                        onPlay = { vm.play(matches.map { it.song }, i) },
                        trailingText = "${(m.similarity * 100).toInt()}%",
                    )
                }
            }
        }
    }
}

/** Every album in one genre, with a button to shuffle all of its songs. */
@Composable
fun GenreScreen(vm: AppViewModel, genre: String, onBack: () -> Unit) {
    val ui = LocalUi.current
    val colors = remember(genre) { placeholderColors(genre) }
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar(genre, solid = true, onBack = onBack)
        AlbumGrid(
            key = genre,
            load = { offset, size -> vm.api().albumsByGenre(genre, size, offset) },
            onOpen = { ui.navigate(Route.AlbumPage(it.id)) },
            header = {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .background(Brush.linearGradient(listOf(colors.first, colors.second)), ArtShape),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(
                            genre,
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    Row(Modifier.padding(top = 16.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionPill("Shuffle $genre", Icons.Filled.Shuffle, primary = true) { vm.playGenre(genre) }
                    }
                }
            },
        )
    }
}
