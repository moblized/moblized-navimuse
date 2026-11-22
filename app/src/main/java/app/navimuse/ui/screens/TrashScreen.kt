package app.navimuse.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.navimuse.net.SafetyGuard
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.components.CardTone
import app.navimuse.ui.components.EmptyBox
import app.navimuse.ui.components.FloatingTopBar
import app.navimuse.ui.components.InfoCard
import app.navimuse.ui.components.LoadBox
import app.navimuse.ui.components.rememberLoad
import app.navimuse.ui.friendly
import kotlinx.coroutines.launch

/** Songs moved to the Trash on the NAS, each with Put back, and when each is deleted for good. */
@Composable
fun TrashScreen(vm: AppViewModel, onBack: () -> Unit) {
    val loaded = rememberLoad("trash") { vm.trash() }
    val settings by vm.settings.collectAsState()
    val autoEmpty = settings.trashAutoEmpty
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        FloatingTopBar("Trash", solid = true, onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                InfoCard(
                    title = if (autoEmpty) "Deleted for good after ${SafetyGuard.TRASH_DAYS} days" else "Kept until you empty it",
                    body = "Songs you delete in NaviMuse go to the _Trash folder in your music share, where Navidrome doesn't look. " +
                        "Put one back and it's where it was. " +
                        if (autoEmpty) {
                            "After ${SafetyGuard.TRASH_DAYS} days in the Trash a song is deleted for good (turn that off in Settings → General → Music folder)."
                        } else {
                            "Emptying it after ${SafetyGuard.TRASH_DAYS} days is off, so empty _Trash from a computer to free the space."
                        },
                    icon = Icons.Filled.Delete,
                    tone = CardTone.NEUTRAL,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            problem?.let { p -> item { Text(p, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            item {
                LoadBox(loaded) { items ->
                    if (items.isEmpty()) EmptyBox("The Trash is empty.")
                }
            }
            items(loaded.value.orEmpty(), key = { it.path }) { t ->
                ListItem(
                    headlineContent = { Text(t.path.substringAfterLast('/')) },
                    supportingContent = {
                        // Whole days left, rounded up: one put in a minute ago has 30.
                        val left = kotlin.math.ceil((t.deletesAt - System.currentTimeMillis()) / 86_400_000.0).toLong().coerceAtLeast(0)
                        Text(
                            listOfNotNull(
                                t.path.substringBeforeLast('/', "").ifBlank { null },
                                "%.1f MB".format(t.bytes / 1_048_576.0),
                                if (autoEmpty) (if (left <= 1) "deleted for good tomorrow" else "deleted for good in $left days") else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        TextButton(
                            enabled = busy == null,
                            onClick = {
                                busy = t.path
                                problem = null
                                scope.launch {
                                    try {
                                        vm.putBack(t)
                                        loaded.retry()
                                    } catch (e: Exception) {
                                        problem = friendly(e)
                                    } finally {
                                        busy = null
                                    }
                                }
                            },
                        ) { Text(if (busy == t.path) "Putting back…" else "Put back") }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
