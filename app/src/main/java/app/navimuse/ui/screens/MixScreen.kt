package app.navimuse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.MixSeed
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.components.Cover
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.FloatingTopBar
import app.navimuse.ui.components.LoadBox
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.SongRow
import app.navimuse.ui.components.rememberArtColor
import app.navimuse.ui.components.rememberLoad
import app.navimuse.ui.components.stageColor
import app.navimuse.ui.formatTotal
import app.navimuse.ui.icons.Icon

/**
 * An Instant Mix, Spotify-style: the mix opens as a page and nothing plays until you press Play
 * (or tap a song). "New mix" makes another one from the same start.
 */
@Composable
fun MixScreen(vm: AppViewModel, seed: MixSeed, onBack: () -> Unit) {
    val ui = LocalUi.current
    val player by vm.playerState.collectAsState()
    var fresh by remember { mutableStateOf(false) }
    val loaded = rememberLoad("mix", seed.key) { vm.mix(seed, fresh).also { fresh = false } }
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("Instant Mix", solid = true, onBack = onBack)
        LoadBox(loaded) { mix ->
            val cover = (seed as? MixSeed.FromAlbum)?.album?.coverArt ?: mix.first.coverArt
            val wash = stageColor(rememberArtColor(cover, mix.name), MaterialTheme.colorScheme.background.luminance() < 0.5f)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(wash.copy(alpha = 0.55f), Color.Transparent)))
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            Cover(cover, size = 220.dp, placeholder = Icons.Filled.Album, seed = mix.name)
                            Icon(
                                Icons.Filled.AutoAwesome,
                                null,
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(22.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            mix.name.removePrefix("Instant Mix: "),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            mix.basedOn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (mix.found) {
                            Text(
                                "${mix.songs.size} songs · ${formatTotal(mix.songs.sumOf { it.duration })}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                item {
                    if (mix.found) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ActionPill("Play", Icons.Filled.PlayArrow, Modifier.weight(1f), primary = true) { vm.playMix(mix) }
                            ActionPill("Shuffle", Icons.Filled.Shuffle, Modifier.weight(1f)) { vm.playMix(mix, shuffle = true) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (mix.found) {
                            ActionPill("Save", Icons.Filled.Save, Modifier.weight(1f)) { ui.saveAsPlaylist(mix.songs, mix.name) }
                        }
                        ActionPill("New mix", Icons.Filled.Refresh, Modifier.weight(1f)) {
                            fresh = true
                            loaded.retry()
                        }
                    }
                    if (!mix.found) EmptyBox("No similar songs came back. Has AudioMuse-AI analysed this song yet?")
                }
                itemsIndexed(mix.songs, key = { i, s -> "$i-${s.id}" }) { i, s ->
                    SongRow(s, onPlay = { vm.playMix(mix, i) }, isCurrent = player.current?.song?.id == s.id)
                }
            }
        }
    }
}
