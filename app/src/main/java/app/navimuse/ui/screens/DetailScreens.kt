package app.navimuse.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navimuse.net.Song
import app.navimuse.net.SubsonicClient
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.Route
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.components.AlbumCard
import app.navimuse.ui.components.ArtistBubble
import app.navimuse.ui.components.CardTone
import app.navimuse.ui.components.CoverFill
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.InfoCard
import app.navimuse.ui.components.LoadBox
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.SectionHeader
import app.navimuse.ui.components.SongRow
import app.navimuse.ui.components.rememberLoad
import app.navimuse.ui.formatTotal

@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { actions() },
    )
}

@Composable
private fun Header(coverArt: String?, title: String, subtitle: String?, detail: String?, onSubtitle: (() -> Unit)? = null, round: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CoverFill(
            coverArt,
            Modifier.width(220.dp).aspectRatio(1f),
            shape = if (round) CircleShape else RoundedCornerShape(14.dp),
            placeholder = if (round) Icons.Filled.Person else Icons.AutoMirrored.Filled.QueueMusic,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = if (onSubtitle != null) Modifier.clickable(onClick = onSubtitle) else Modifier,
            )
        }
        if (!detail.isNullOrBlank()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ActionsRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun StarButton(id: String, kind: SubsonicClient.StarKind, initial: Boolean) {
    val vm = LocalUi.current.vm
    val stars by vm.stars.collectAsState()
    val starred = stars[id] ?: initial
    IconButton(onClick = { vm.setStar(id, kind, !starred) }) {
        Icon(
            if (starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            if (starred) "Remove from favourites" else "Add to favourites",
            tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AlbumScreen(vm: AppViewModel, albumId: String, onBack: () -> Unit) {
    val ui = LocalUi.current
    val player by vm.playerState.collectAsState()
    val loaded = rememberLoad(albumId) { vm.api().album(albumId) }
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(loaded.value?.album?.name ?: "Album", onBack) {
            loaded.value?.album?.let { StarButton(it.id, SubsonicClient.StarKind.ALBUM, it.starred) }
        }
        LoadBox(loaded) { detail ->
            val a = detail.album
            val songs = detail.songs
            val multiDisc = songs.map { it.disc }.distinct().size > 1
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Header(
                        a.coverArt,
                        a.name,
                        a.artist,
                        listOfNotNull(
                            a.year.takeIf { it > 0 }?.toString(),
                            a.genre,
                            "${songs.size} songs",
                            formatTotal(songs.sumOf { it.duration }),
                        ).joinToString(" · "),
                        onSubtitle = a.artistId?.let { id -> { ui.navigate(Route.ArtistPage(id)) } },
                    )
                }
                item {
                    ActionsRow {
                        ActionPill("Play", Icons.Filled.PlayArrow, primary = true) { vm.play(songs) }
                        ActionPill("Shuffle", Icons.Filled.Shuffle) { vm.play(songs, shuffle = true) }
                        ActionPill("Instant Mix", Icons.Filled.AutoAwesome) { vm.mixFromAlbum(a) }
                        ActionPill("Play next", Icons.Filled.SkipNext) { vm.playNext(songs) }
                        ActionPill("Add to…", Icons.AutoMirrored.Filled.PlaylistAdd) { ui.pickPlaylistFor(songs) }
                    }
                }
                itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                    if (multiDisc && (i == 0 || songs[i - 1].disc != s.disc)) {
                        Text(
                            "Disc ${s.disc}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
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
    val loaded = rememberLoad(artistId) { vm.api().artist(artistId) }
    val infoLoad = rememberLoad("info", artistId) { vm.api().artistInfo(artistId) }
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(loaded.value?.artist?.name ?: "Artist", onBack) {
            loaded.value?.artist?.let { StarButton(it.id, SubsonicClient.StarKind.ARTIST, it.starred) }
        }
        LoadBox(loaded) { detail ->
            val artist = detail.artist
            val topLoad = rememberLoad("top", artist.name) { vm.api().topSongs(artist.name, 10) }
            val info = infoLoad.value
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Header(
                        artist.coverArt,
                        artist.name,
                        null,
                        "${detail.albums.size} album${if (detail.albums.size == 1) "" else "s"}",
                        round = true,
                    )
                }
                item {
                    ActionsRow {
                        ActionPill("Artist radio", Icons.Filled.Radio, primary = true) { vm.artistRadio(artist) }
                        topLoad.value?.takeIf { it.isNotEmpty() }?.let { top ->
                            ActionPill("Play top songs", Icons.Filled.PlayArrow) { vm.play(top) }
                        }
                    }
                }
                val top = topLoad.value.orEmpty()
                if (top.isNotEmpty()) {
                    item { SectionHeader("Top songs") }
                    itemsIndexed(top.take(5), key = { _, s -> "top-${s.id}" }) { i, s -> SongRow(s, onPlay = { vm.play(top, i) }) }
                }
                item { SectionHeader("Albums") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(detail.albums, key = { it.id }) { a -> AlbumCard(a) { ui.navigate(Route.AlbumPage(a.id)) } }
                    }
                }
                val similar = info?.similar.orEmpty()
                if (similar.isNotEmpty()) {
                    item { SectionHeader("Sounds like") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "More") }
    }
}

@Composable
fun PlaylistScreen(vm: AppViewModel, playlistId: String, onBack: () -> Unit) {
    val ui = LocalUi.current
    val player by vm.playerState.collectAsState()
    val version by vm.playlistsVersion.collectAsState()
    val loaded = rememberLoad(playlistId, version) { vm.api().playlist(playlistId) }
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(loaded.value?.playlist?.name ?: "Playlist", onBack)
        LoadBox(loaded) { detail ->
            val p = detail.playlist
            val songs = detail.songs
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Header(
                        p.coverArt,
                        p.name,
                        p.owner?.let { "by $it" },
                        "${songs.size} songs · ${formatTotal(songs.sumOf { it.duration })}",
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            action = { TextButton(onClick = { vm.keepCopy(p) }) { Text("Keep a copy") } },
                        )
                    }
                }
                item {
                    ActionsRow {
                        ActionPill("Play", Icons.Filled.PlayArrow, primary = true) { vm.play(songs) }
                        ActionPill("Shuffle", Icons.Filled.Shuffle) { vm.play(songs, shuffle = true) }
                        songs.firstOrNull()?.let { first ->
                            ActionPill("Instant Mix", Icons.Filled.AutoAwesome) { vm.instantMix(songs.random()) }
                        }
                        ActionPill("Copy", Icons.Filled.ContentCopy) { ui.saveAsPlaylist(songs, "${app.navimuse.net.PlaylistNames.pretty(p.name)} copy") }
                    }
                }
                if (songs.isEmpty()) item { EmptyBox("This playlist is empty.") }
                itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { i, s ->
                    SongRow(s, onPlay = { vm.play(songs, i) }, isCurrent = player.current?.song?.id == s.id)
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
        DetailTopBar("Sounds like ${song.title}", onBack)
        LoadBox(loaded) { matches ->
            val songs = listOf(song) + matches.map { it.song }.filter { it.id != song.id }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    ActionsRow {
                        ActionPill("Play all", Icons.Filled.PlayArrow, primary = true) { vm.play(songs) }
                        ActionPill("Save as playlist", Icons.Filled.Save) { ui.saveAsPlaylist(songs, "Sounds like ${song.title}") }
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
