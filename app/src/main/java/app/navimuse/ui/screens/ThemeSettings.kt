package app.navimuse.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import app.navimuse.ui.icons.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.icons.IconPacks
import app.navimuse.ui.symfonium.SymfoniumStyles
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.theme.CustomTheme
import app.navimuse.ui.theme.isDark

private const val SYMFONIUM_THEMES = "https://styles.symfonium.app/"

/**
 * Colour themes: NaviMuse's own, or ones imported from Symfonium (Symfonium Styles, the
 * Symfonium forum) or Material Theme Builder.
 */
@Composable
fun ThemeSettings(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val themes by vm.themes.collectAsState()
    var linkDialog by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<CustomTheme?>(null) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { vm.importTheme(it) }
    }
    val dark = MaterialTheme.colorScheme.isDark

    Text(
        "Colour theme",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
    ThemeRow(
        name = "NaviMuse",
        swatches = if (dark) listOf(Color.Black, Color.White, Color(0xFF3DDCD2)) else listOf(Color.White, Color.Black, Color(0xFF00807A)),
        selected = settings.themeId == null,
        onSelect = { vm.useTheme(null) },
    )
    themes.forEach { t ->
        val roles = t.roles(dark)
        ThemeRow(
            name = t.name,
            swatches = listOfNotNull(
                (roles["surface"] ?: roles["background"])?.let { Color(it) },
                roles["surfaceContainerHigh"]?.let { Color(it) },
                roles["primary"]?.let { Color(it) },
                roles["tertiary"]?.let { Color(it) },
            ),
            selected = settings.themeId == t.id,
            onSelect = { vm.useTheme(t.id) },
            onRemove = { removing = t },
        )
    }
    StyleRows(vm)
    Text(
        "Import colour themes, now-playing styles and full app styles made for Symfonium (from Symfonium Styles or the Symfonium forum), " +
            "or a Material Theme Builder export. Colour themes follow the light or dark setting above. " +
            "Styles bring their text lines, layout and home sections; settings only Symfonium understands are skipped.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActionPill("Import file", Icons.Filled.FileOpen, Modifier.weight(1f)) {
            pickFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        ActionPill("Import link", Icons.Filled.Link, Modifier.weight(1f)) { linkDialog = true }
    }
    TextButton(
        onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SYMFONIUM_THEMES)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Icon(Icons.Filled.Palette, null, modifier = Modifier.size(18.dp))
        Text("  Browse Symfonium Styles")
    }

    if (linkDialog) {
        ThemeLinkDialog(
            onDismiss = { linkDialog = false },
            onImport = {
                linkDialog = false
                vm.importThemeFromLink(it)
            },
        )
    }
    removing?.let { t ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove \"${t.name}\"?") },
            text = { Text("You can import it again any time.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    vm.deleteTheme(t)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ThemeRow(
    name: String,
    swatches: List<Color>,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        // The theme's background with its other colours as dots on it.
        Box(
            Modifier
                .size(width = 64.dp, height = 36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(swatches.firstOrNull() ?: Color.Gray)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                swatches.drop(1).forEach { c -> Box(Modifier.size(12.dp).background(c, CircleShape)) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, "Remove $name", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ThemeLinkDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val context = LocalContext.current
    var link by remember {
        // Start with a link already on the clipboard, if there is one.
        val clip = runCatching {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()
        mutableStateOf(clip?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }.orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import from a link") },
        text = {
            Column {
                Text(
                    "Paste the link to a colour theme on Symfonium Styles, or a direct link to a theme's .json file.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text("https://styles.symfonium.app/styles/…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImport(link) }, enabled = link.isNotBlank()) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val SYMFONIUM_ICON_PACKS = "https://styles.symfonium.app/styles?type=icon_pack"

/**
 * Icon packs made for Symfonium. They're small apps: install one and it shows up here.
 * The list is re-read each time you come back to the screen.
 */
@Composable
fun IconPackSettings(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val packs = remember(refresh) { IconPacks.installed(context) }

    Text(
        "Icons",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
    PackRow("NaviMuse", settings.iconPack == null) { vm.useIconPack(null) }
    packs.forEach { p -> PackRow(p.label, settings.iconPack == p.id) { vm.useIconPack(p.id) } }
    if (settings.iconPack != null && packs.none { it.id == settings.iconPack }) {
        Text(
            "The icon pack you picked isn't installed any more, so NaviMuse's own icons are showing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    Text(
        if (packs.isEmpty()) {
            "Icon packs made for Symfonium work here too. Install one from Symfonium Styles and it shows up in this list."
        } else {
            "Icons a pack doesn't include keep NaviMuse's own."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    TextButton(
        onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SYMFONIUM_ICON_PACKS)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Icon(Icons.Filled.Palette, null, modifier = Modifier.size(18.dp))
        Text("  Browse Symfonium icon packs")
    }
}

@Composable
private fun PackRow(name: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The Symfonium now-playing and app styles in use, each with a way back to NaviMuse's own. */
@Composable
private fun StyleRows(vm: AppViewModel) {
    val nowPlaying by vm.nowPlayingStyle.collectAsState()
    val app by vm.appStyle.collectAsState()
    val ownNowPlaying = remember(nowPlaying) { SymfoniumStyles.ownNowPlayingName() }
    if (app == null && ownNowPlaying == null) return
    Text(
        "Symfonium styles",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
    app?.let { a ->
        StyleRow("App style", a.name) { vm.removeAppStyle() }
    }
    ownNowPlaying?.let { name ->
        StyleRow("Now playing style", name) { vm.removeNowPlayingStyle() }
    }
}

@Composable
private fun StyleRow(kind: String, name: String, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}
