package app.navimuse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navimuse.data.Mood
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.Route
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.FloatingTopBar
import app.navimuse.ui.components.LoadBox
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.SongRow
import app.navimuse.ui.components.rememberLoad
import app.navimuse.ui.icons.Icon
import app.navimuse.ui.theme.ArtShape

fun moodIcon(mood: Mood): ImageVector = when (mood) {
    Mood.CHILL -> Icons.Filled.Spa
    Mood.HAPPY -> Icons.Filled.SentimentVerySatisfied
    Mood.ENERGETIC -> Icons.Filled.Bolt
    Mood.WORKOUT -> Icons.Filled.FitnessCenter
    Mood.PARTY -> Icons.Filled.Celebration
    Mood.FOCUS -> Icons.Filled.Psychology
    Mood.SLEEP -> Icons.Filled.Bedtime
    Mood.ROMANCE -> Icons.Filled.Favorite
    Mood.SAD -> Icons.Filled.WaterDrop
    Mood.DARK -> Icons.Filled.LocalFireDepartment
    Mood.ROAD_TRIP -> Icons.Filled.DirectionsCar
    Mood.LATE_NIGHT -> Icons.Filled.NightsStay
}

private fun Mood.brush() = Brush.linearGradient(listOf(Color(from), Color(to)))

/** The mood tiles in Discover: a sideways row; tapping one opens its mix. */
@Composable
fun MoodShelf(modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(Mood.entries, key = { it.name }) { mood ->
            MoodTile(mood) { ui.navigate(Route.MoodPage(mood)) }
        }
    }
}

@Composable
private fun MoodTile(mood: Mood, onClick: () -> Unit) {
    Box(
        Modifier
            .width(136.dp)
            .height(76.dp)
            .clip(ArtShape)
            .background(mood.brush())
            .clickable(onClick = onClick),
    ) {
        // A big faded icon tucked into the corner, Tidal-style.
        Icon(
            moodIcon(mood),
            null,
            tint = Color.White.copy(alpha = 0.28f),
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 10.dp).size(58.dp),
        )
        Text(
            mood.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp, end = 30.dp),
        )
    }
}

/** One mood's mix: play, shuffle, save, or ask for a new one. */
@Composable
fun MoodScreen(vm: AppViewModel, mood: Mood, onBack: () -> Unit) {
    val ui = LocalUi.current
    val player by vm.playerState.collectAsState()
    val loaded = rememberLoad("mood", mood) { vm.moodMix(mood) }
    val from = AppViewModel.PlaySource("${mood.label} mix", Route.MoodPage(mood))
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar(mood.label, solid = true, onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(ArtShape)
                        .background(mood.brush()),
                ) {
                    Icon(
                        moodIcon(mood),
                        null,
                        tint = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.align(Alignment.CenterEnd).offset(x = 18.dp).size(140.dp),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text("Mood", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                        Text(mood.label, style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 1)
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
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (mix.songs.isNotEmpty()) {
                                ActionPill("Play", Icons.Filled.PlayArrow, Modifier.weight(1f), primary = true) { vm.play(mix.songs, from = from) }
                                ActionPill("Shuffle", Icons.Filled.Shuffle, Modifier.weight(1f)) { vm.play(mix.songs, shuffle = true, from = from) }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ActionPill("New mix", Icons.Filled.Refresh, Modifier.weight(1f)) { loaded.retry() }
                            if (mix.songs.isNotEmpty()) {
                                ActionPill("Save", Icons.Filled.Save, Modifier.weight(1f)) { ui.saveAsPlaylist(mix.songs, "${mood.label} mix") }
                            }
                        }
                        if (mix.songs.isEmpty()) EmptyBox("No songs for this mood yet.")
                    }
                }
            }
            val songs = loaded.value?.songs.orEmpty()
            itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { i, s ->
                SongRow(s, onPlay = { vm.play(songs, i, from = from) }, isCurrent = player.current?.song?.id == s.id)
            }
        }
    }
}
