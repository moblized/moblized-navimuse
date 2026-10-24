package app.navimuse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navimuse.net.Song
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.Route
import app.navimuse.ui.components.CardTone
import app.navimuse.ui.components.Cover
import app.navimuse.ui.components.InfoCard
import app.navimuse.ui.components.Load
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.PlaylistRow
import app.navimuse.ui.components.SectionHeader
import app.navimuse.ui.components.SongPickerDialog
import app.navimuse.ui.components.SongRow
import app.navimuse.ui.components.rememberLoad
import app.navimuse.ui.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class ToolResult(val title: String, val songs: List<Song>, val saveName: String)

private enum class Pick { MIX_SEED, PATH_START, PATH_END }

private val moodIdeas = listOf(
    "calm piano for a rainy night",
    "upbeat summer road trip",
    "dark and heavy guitars",
    "acoustic morning coffee",
    "late night jazz",
    "high energy workout",
    "dreamy synthwave",
    "sad and slow",
)

@Composable
fun MixesScreen(vm: AppViewModel) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val info by vm.serverInfo.collectAsState()
    val amLogin by vm.audioMuseLogin.collectAsState()
    val player by vm.playerState.collectAsState()
    val version by vm.playlistsVersion.collectAsState()

    var result by remember { mutableStateOf<ToolResult?>(null) }
    var working by remember { mutableStateOf<String?>(null) }
    var toolError by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<Pick?>(null) }
    var pathStart by remember { mutableStateOf<Song?>(null) }
    var pathEnd by remember { mutableStateOf<Song?>(null) }
    var pathLength by remember { mutableFloatStateOf(25f) }
    var mood by remember { mutableStateOf("") }

    fun runTool(label: String, block: suspend () -> ToolResult?) {
        if (working != null) return
        working = label
        toolError = null
        scope.launch {
            try {
                val r = block()
                if (r != null && r.songs.isEmpty()) toolError = "Nothing came back. AudioMuse-AI may not have analysed these songs yet."
                else result = r
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toolError = friendly(e)
            } finally {
                working = null
            }
        }
    }

    fun makePath() {
        val a = pathStart ?: return
        val b = pathEnd ?: return
        runTool("Finding a path…") {
            val songs = vm.sonicPath(a, b, pathLength.toInt())
            ToolResult("Path: ${a.title} → ${b.title}", songs, "Path ${a.title} to ${b.title}")
        }
    }

    fun searchMood(q: String) {
        if (q.isBlank()) return
        runTool("Searching by mood…") {
            ToolResult("Mood: $q", vm.moodSearch(q.trim()), q.trim().replaceFirstChar { it.uppercase() })
        }
    }

    val playlists = rememberLoad("am-playlists", version) { vm.api().playlists().filter { it.isAudioMuseManaged } }
    val sonicOn = info?.sonicSimilarity == true

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("AudioMuse-AI") })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                InfoCard(
                    title = if (sonicOn) "Sonic analysis is on" else "Sonic analysis not detected",
                    body = if (sonicOn) {
                        "Navidrome's AudioMuse-AI plugin is active: Instant Mix, similar songs and song paths come from how your music actually sounds."
                    } else {
                        "Navidrome didn't report the AudioMuse-AI plugin, so song paths won't work and Instant Mix may come from Last.fm. " +
                            "Install the plugin and put \"audiomuseai\" first in ND_AGENTS."
                    },
                    icon = if (sonicOn) Icons.Filled.CheckCircle else Icons.Filled.Info,
                    tone = if (sonicOn) CardTone.GOOD else CardTone.WARNING,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // Progress / error / results of the last tool used
            working?.let { label ->
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(label)
                    }
                }
            }
            toolError?.let { msg ->
                item {
                    Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            result?.let { r ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${r.songs.size} songs", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { result = null }) { Icon(Icons.Filled.Close, "Clear") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.play(r.songs) }) {
                                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                    Text(" Play")
                                }
                                FilledTonalButton(onClick = { ui.saveAsPlaylist(r.songs, r.saveName) }) {
                                    Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                                    Text(" Save as playlist")
                                }
                            }
                        }
                    }
                }
                itemsIndexed(r.songs.take(60), key = { i, s -> "res-$i-${s.id}" }) { i, s ->
                    SongRow(s, onPlay = { vm.play(r.songs, i) })
                }
            }

            // Instant Mix
            item { SectionHeader("Instant Mix") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Start from one song and AudioMuse-AI keeps going with songs that sound like it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        player.current?.song?.let { now ->
                            Button(onClick = { vm.instantMix(now) }) {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                                Text(" From what's playing")
                            }
                        }
                        FilledTonalButton(onClick = { picking = Pick.MIX_SEED }) {
                            Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
                            Text(" Pick a song")
                        }
                    }
                    TextButton(onClick = { vm.surpriseMix() }) { Text("Surprise me") }
                }
            }

            // Song path
            item { SectionHeader("Song path") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "A smooth journey from one song to another, one step at a time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PathEnd("Start", pathStart, Icons.Filled.MusicNote) { picking = Pick.PATH_START }
                    PathEnd("End", pathEnd, Icons.Filled.Flag) { picking = Pick.PATH_END }
                    Text("${pathLength.toInt()} songs", style = MaterialTheme.typography.labelLarge)
                    Slider(value = pathLength, onValueChange = { pathLength = it }, valueRange = 10f..60f, steps = 9)
                    Button(
                        onClick = { makePath() },
                        enabled = pathStart != null && pathEnd != null && working == null && sonicOn,
                    ) {
                        Icon(Icons.Filled.Timeline, null, Modifier.size(18.dp))
                        Text(" Make path")
                    }
                    if (!sonicOn) {
                        Text(
                            "Needs Navidrome's AudioMuse-AI plugin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Mood search
            item { SectionHeader("Search by mood") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (amLogin == null) {
                        InfoCard(
                            title = "Describe a vibe, get the songs",
                            body = "Type something like \"calm piano for a rainy night\" and AudioMuse-AI finds songs that sound like it. " +
                                "This talks to AudioMuse-AI directly, so it needs its address (and API token) in Settings. Only searches are ever sent.",
                            icon = Icons.Filled.Mood,
                            action = { TextButton(onClick = { ui.navigate(Route.SettingsPage) }) { Text("Set up in Settings") } },
                        )
                    } else {
                        OutlinedTextField(
                            value = mood,
                            onValueChange = { mood = it },
                            placeholder = { Text("e.g. calm piano for a rainy night") },
                            leadingIcon = { Icon(Icons.Filled.Mood, null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { searchMood(mood) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            moodIdeas.forEach { idea ->
                                AssistChip(onClick = {
                                    mood = idea
                                    searchMood(idea)
                                }, label = { Text(idea) })
                            }
                        }
                        Button(onClick = { searchMood(mood) }, enabled = mood.isNotBlank() && working == null) {
                            Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
                            Text(" Find songs")
                        }
                    }
                }
            }

            // Keep AudioMuse playlists
            item { SectionHeader("Keep AudioMuse playlists") }
            item {
                Text(
                    "AudioMuse-AI's clustering playlists end in _automatic and are deleted and remade on every run. " +
                        "Keep a copy of the ones you like; copies have normal names, so they're left alone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            when (val s = playlists.state) {
                is Load.Loading -> item { Row(Modifier.padding(16.dp)) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } }
                is Load.Failed -> item { Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                is Load.Ok -> {
                    if (s.value.isEmpty()) {
                        item {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("No _automatic playlists right now.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        item {
                            TextButton(onClick = { vm.keepAll(s.value) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("Keep copies of all ${s.value.size}")
                            }
                        }
                        items(s.value, key = { it.id }) { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlaylistRow(p, Modifier.weight(1f)) { ui.navigate(Route.PlaylistPage(p.id)) }
                                TextButton(onClick = { vm.keepCopy(p) }, modifier = Modifier.padding(end = 8.dp)) { Text("Keep") }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    when (picking) {
        Pick.MIX_SEED -> SongPickerDialog("Mix from which song?", onDismiss = { picking = null }) {
            picking = null
            vm.instantMix(it)
        }
        Pick.PATH_START -> SongPickerDialog("Start the path at…", onDismiss = { picking = null }) {
            picking = null
            pathStart = it
        }
        Pick.PATH_END -> SongPickerDialog("End the path at…", onDismiss = { picking = null }) {
            picking = null
            pathEnd = it
        }
        null -> Unit
    }
}

@Composable
private fun PathEnd(label: String, song: Song?, icon: androidx.compose.ui.graphics.vector.ImageVector, onPick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPick),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (song != null) Cover(song.coverArt, size = 40.dp) else Icon(icon, null, Modifier.size(40.dp).padding(8.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(song?.title ?: "Pick a song", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                song?.let { Text(it.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
        }
    }
}
