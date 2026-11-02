package app.navimuse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import app.navimuse.ui.icons.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.navimuse.BuildConfig
import app.navimuse.data.DetailsStyle
import app.navimuse.data.Quality
import app.navimuse.data.ThemeMode
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.Route
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.rememberSoundEffects
import app.navimuse.update.Updater
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ui = LocalUi.current
    val settings by vm.settings.collectAsState()
    val login by vm.login.collectAsState()
    val info by vm.serverInfo.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmRemoveDownloads by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group("Navidrome")
            ListItem(
                headlineContent = { Text(login?.username ?: "") },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            login?.baseUrl,
                            info?.serverVersion?.let { "${info?.serverType ?: "Server"} $it" },
                        ).joinToString("\n"),
                    )
                },
                trailingContent = {
                    IconButton(onClick = { confirmLogout = true }) { Icon(Icons.AutoMirrored.Filled.Logout, "Log out") }
                },
            )
            ListItem(
                headlineContent = { Text("Safety") },
                supportingContent = { Text("What this app will never do, and how to keep AudioMuse-AI from touching your files") },
                leadingContent = { Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.secondary) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                modifier = Modifier.clickable { ui.navigate(Route.Safety) },
            )

            HorizontalDivider()
            Group("AudioMuse-AI (optional)")
            AudioMuseSettings(vm)

            HorizontalDivider()
            Group("Playback")
            QualityPicker("Streaming on Wi-Fi", settings.wifiQuality) { q -> vm.updateSettings { it.copy(wifiQuality = q) } }
            QualityPicker("Streaming on mobile data", settings.mobileQuality) { q -> vm.updateSettings { it.copy(mobileQuality = q) } }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Instant Mix length: ${settings.mixSize} songs", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = settings.mixSize.toFloat(),
                    onValueChange = { v -> vm.updateSettings { it.copy(mixSize = (v / 10).toInt() * 10) } },
                    valueRange = 20f..100f,
                    steps = 7,
                )
            }
            SwitchRow(
                "Play Dolby Atmos in its original format",
                "Atmos and surround songs stream and download untouched, even on mobile data, so your phone's Dolby decoder plays them. " +
                    "Turn off if they won't play, and the server converts them to stereo.",
                settings.dolbyOriginal,
            ) { on -> vm.updateSettings { it.copy(dolbyOriginal = on) } }
            DetailsStylePicker(settings.detailsStyle) { d -> vm.updateSettings { it.copy(detailsStyle = d) } }
            rememberSoundEffects()?.let { open ->
                ListItem(
                    headlineContent = { Text("Sound effects") },
                    supportingContent = { Text("Your phone's equalizer and effects, such as Dolby Atmos on Samsung phones") },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                    modifier = Modifier.clickable { open() },
                )
            }
            SwitchRow(
                "Report plays to Navidrome",
                "Keeps play counts and \"recently played\" up to date. AudioMuse-AI uses these for taste-based mixes.",
                settings.scrobble,
            ) { on -> vm.updateSettings { it.copy(scrobble = on) } }

            HorizontalDivider()
            Group("Downloads")
            QualityPicker("Download quality", settings.downloadQuality) { q -> vm.updateSettings { it.copy(downloadQuality = q) } }
            SwitchRow("Download on Wi-Fi only", "Downloads wait until you're on Wi-Fi.", settings.downloadWifiOnly) { on ->
                vm.updateSettings { it.copy(downloadWifiOnly = on) }
            }
            SwitchRow(
                "Download favourites automatically",
                "Every favourite song is saved on the phone, and new ones as you add them.",
                settings.autoDownloadFavourites,
            ) { on -> vm.setFavouritesAutoDownload(on) }
            val downloads by vm.downloads.collectAsState()
            val bytes = remember(downloads.size) { vm.downloadBytes() }
            ListItem(
                headlineContent = { Text("Downloaded songs") },
                supportingContent = { Text("${downloads.size} on this phone, ${"%.1f".format(bytes / 1_073_741_824.0)} GB") },
                trailingContent = {
                    if (downloads.isNotEmpty()) TextButton(onClick = { confirmRemoveDownloads = true }) { Text("Remove all") }
                },
            )

            HorizontalDivider()
            Group("Updates")
            SwitchRow(
                "Update automatically",
                "Checks GitHub twice a day and downloads new versions. You tap once to install.",
                settings.autoUpdate,
            ) { on ->
                vm.updateSettings { it.copy(autoUpdate = on) }
                if (on) ui.askNotifications()
            }
            UpdateRow(vm)

            HorizontalDivider()
            Group("Appearance")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = settings.theme == mode,
                        onClick = { vm.updateSettings { it.copy(theme = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(mode.label) }
                }
            }
            Text(
                "The home-screen icon switches to its dark version when the app is in dark mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ThemeSettings(vm)
            IconPackSettings(vm)

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Text(
                "NaviMuse ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (confirmRemoveDownloads) {
        AlertDialog(
            onDismissRequest = { confirmRemoveDownloads = false },
            title = { Text("Remove all downloads?") },
            text = { Text("The songs are deleted from this phone only. They stay on your server and you can download them again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveDownloads = false
                    vm.removeAllDownloads()
                }) { Text("Remove all") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveDownloads = false }) { Text("Cancel") } },
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("Playback stops, and your login and downloads are removed from this phone. Nothing on the server changes.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    vm.logOut()
                }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun UpdateRow(vm: AppViewModel) {
    val status by vm.updateStatus.collectAsState()
    val (line, button) = when (val s = status) {
        Updater.Status.Idle -> "You have ${BuildConfig.VERSION_NAME}" to "Check now"
        Updater.Status.Checking -> "Checking GitHub…" to null
        Updater.Status.UpToDate -> "${BuildConfig.VERSION_NAME} is the newest version" to "Check now"
        is Updater.Status.Downloading -> "Downloading ${s.release.versionName}… ${s.percent}%" to null
        is Updater.Status.Ready -> (s.note ?: "${s.release.versionName} is ready to install") to "Install"
        Updater.Status.Installing -> "Installing…" to null
        is Updater.Status.Failed -> s.message to "Try again"
    }
    ListItem(
        headlineContent = { Text("App version") },
        supportingContent = { Text(line) },
        trailingContent = {
            if (button != null) {
                TextButton(onClick = {
                    if (status is Updater.Status.Ready) vm.installUpdate() else vm.checkForUpdate(manual = true)
                }) { Text(button) }
            }
        },
    )
}

@Composable
private fun Group(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 6.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

/** How the audio details under the seek bar read (Tidal, Symfonium, or hidden). */
@Composable
private fun DetailsStylePicker(value: DetailsStyle, onPick: (DetailsStyle) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("Audio details on the playing screen") },
        supportingContent = { Text("${value.label}: ${value.example}") },
        trailingContent = {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DetailsStyle.entries.forEach { d ->
                    DropdownMenuItem(
                        text = { Text("${d.label}  ·  ${d.example}") },
                        onClick = {
                            expanded = false
                            onPick(d)
                        },
                    )
                }
            }
        },
        modifier = Modifier.clickable { expanded = true },
    )
}

@Composable
private fun QualityPicker(title: String, value: Quality, onPick: (Quality) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value.label) },
        trailingContent = {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Quality.entries.forEach { q ->
                    DropdownMenuItem(text = { Text(q.label) }, onClick = {
                        expanded = false
                        onPick(q)
                    })
                }
            }
        },
        modifier = Modifier.clickable { expanded = true },
    )
}

@Composable
private fun AudioMuseSettings(vm: AppViewModel) {
    val current by vm.audioMuseLogin.collectAsState()
    val scope = rememberCoroutineScope()
    var address by remember(current) { mutableStateOf(current?.baseUrl ?: "") }
    var token by remember(current) { mutableStateOf(current?.token ?: "") }
    var serverName by remember(current) { mutableStateOf(current?.serverName ?: "") }
    var showToken by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Instant Mix, similar songs and song paths already work through Navidrome's AudioMuse-AI plugin. " +
                "Add AudioMuse-AI here only for mood search. The app sends it searches and nothing else; its clean-up, clustering and delete functions are blocked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("AudioMuse-AI address") },
            placeholder = { Text("http://192.168.1.10:8000") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("API token (if login is on)") },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showToken) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = serverName,
            onValueChange = { serverName = it },
            label = { Text("Server name in AudioMuse-AI (only with several servers)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        status?.let {
            Text(it, color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    busy = true
                    status = null
                    scope.launch {
                        val err = vm.saveAudioMuse(address, token, serverName)
                        statusIsError = err != null
                        status = err ?: "Connected. Mood search is ready on the Mixes tab."
                        busy = false
                    }
                },
                enabled = !busy && address.isNotBlank(),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Test and save")
            }
            if (current != null) {
                OutlinedButton(onClick = {
                    vm.removeAudioMuse()
                    status = "Removed."
                    statusIsError = false
                }) { Text("Remove") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
