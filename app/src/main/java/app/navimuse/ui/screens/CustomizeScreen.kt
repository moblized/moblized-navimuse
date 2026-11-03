package app.navimuse.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navimuse.data.Accent
import app.navimuse.data.ArtSize
import app.navimuse.data.CardSize
import app.navimuse.data.CollectionEntry
import app.navimuse.data.Corners
import app.navimuse.data.DarkShade
import app.navimuse.data.DetailsStyle
import app.navimuse.data.FontChoice
import app.navimuse.data.HomeSection
import app.navimuse.data.Look
import app.navimuse.data.MiniStyle
import app.navimuse.data.PlayButtonStyle
import app.navimuse.data.PlayerArt
import app.navimuse.data.PlayerBackground
import app.navimuse.data.SeekBarStyle
import app.navimuse.data.TabChoice
import app.navimuse.data.TabLabels
import app.navimuse.data.TextSize
import app.navimuse.data.TitleAlign
import app.navimuse.data.TitleFont
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.components.ArtPlaceholder
import app.navimuse.ui.components.LocalUi
import app.navimuse.ui.components.QualityBadge
import app.navimuse.ui.icons.Icon
import app.navimuse.ui.theme.ArtShape
import app.navimuse.ui.theme.Sans
import app.navimuse.ui.theme.isDark

/**
 * Settings → Customize: how the app looks and how it's laid out. Every change shows at once;
 * Reset brings back NaviMuse's own look.
 */
@Composable
fun CustomizeScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ui = LocalUi.current
    val settings by vm.settings.collectAsState()
    val appStyle by vm.appStyle.collectAsState()
    val nowPlayingStyle by vm.nowPlayingStyle.collectAsState()
    val player by vm.playerState.collectAsState()
    val look = settings.look
    var confirmReset by remember { mutableStateOf(false) }
    fun change(f: (Look) -> Look) = vm.updateLook(f)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Customize") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { TextButton(onClick = { confirmReset = true }) { Text("Reset") } },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            LookPreview()

            // --- colours ------------------------------------------------------------------------
            Group("Colours")
            ThemeModePicker(settings.theme) { mode -> vm.updateSettings { it.copy(theme = mode) } }
            AccentPicker(look.accent) { a -> change { it.copy(accent = a) } }
            Segments(
                "Dark background",
                DarkShade.entries,
                look.darkShade,
                { it.label },
                note = "Black saves battery on OLED screens. Used with NaviMuse's colours in dark mode.",
            ) { v -> change { it.copy(darkShade = v) } }
            ThemeSettings(vm)
            IconPackSettings(vm)

            // --- text ---------------------------------------------------------------------------
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Group("Text")
            Choice(
                "Font",
                FontChoice.entries,
                look.font,
                { it.label },
                fontOf = { fontFamilyOf(it) },
            ) { v -> change { it.copy(font = v) } }
            if (look.font == FontChoice.INSTRUMENT) {
                Segments("Page titles and names", TitleFont.entries, look.titleFont, { it.label }) { v -> change { it.copy(titleFont = v) } }
            }
            Segments("Text size", TextSize.entries, look.textSize, { it.label }) { v -> change { it.copy(textSize = v) } }

            // --- shapes and sizes ---------------------------------------------------------------
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Group("Shapes and sizes")
            Segments("Album art corners", Corners.entries, look.corners, { it.label }) { v -> change { it.copy(corners = v) } }
            Segments("Cards on Home", CardSize.entries, look.cardSize, { it.label }) { v -> change { it.copy(cardSize = v) } }
            Segments(
                "Album grid columns",
                listOf(0, 2, 3, 4),
                look.gridColumns,
                { if (it == 0) "Auto" else it.toString() },
                note = if (appStyle?.gridColumns != null) "Your Symfonium app style sets this for now." else null,
            ) { v -> change { it.copy(gridColumns = v) } }

            // --- bottom tabs --------------------------------------------------------------------
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Group("Bottom tabs")
            Hint("Turn tabs on or off and use the arrows to change their order. Home or Collection always stays, because Settings opens from there.")
            OrderList(
                all = TabChoice.entries,
                shown = look.tabs,
                label = { it.label },
                locked = { t ->
                    // The last of Home and Collection can't go.
                    (t == TabChoice.HOME && TabChoice.COLLECTION !in look.tabs) ||
                        (t == TabChoice.COLLECTION && TabChoice.HOME !in look.tabs)
                },
            ) { v -> change { it.copy(tabs = v) } }
            Segments("Tab names", TabLabels.entries, look.tabLabels, { it.label }) { v -> change { it.copy(tabLabels = v) } }
            Choice("Open the app on", look.tabs, look.startTab, { it.label }) { v -> change { it.copy(startTab = v) } }

            // --- home ---------------------------------------------------------------------------
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Group("Home")
            val styleHome = appStyle?.takeIf { it.homeRows.isNotEmpty() }
            if (styleHome != null) {
                StyleNote("Home is arranged by your Symfonium app style \"${styleHome.name}\" right now.", "Use these instead") { vm.removeAppStyle() }
            }
            Hint("Pick what Home shows, and in what order.")
            OrderList(HomeSection.entries, look.home, { it.label }) { v -> change { it.copy(home = v) } }

            // --- collection ---------------------------------------------------------------------
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Group("Collection")
            if (appStyle?.library.orEmpty().isNotEmpty()) {
                StyleNote("Your Symfonium app style sets the Collection menu right now.", "Use these instead") { vm.removeAppStyle() }
            }
            OrderList(CollectionEntry.entries, look.collection, { it.label }) { v -> change { it.copy(collection = v) } }
            SwitchRow("Recently played", "Albums you played lately, under the menu.", look.collectionRecent) { on ->
                change { it.copy(collectionRecent = on) }
            }

            // --- playing screen -----------------------------------------------------------------
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Group("Playing screen")
            nowPlayingStyle?.let { st ->
                StyleNote("Your Symfonium now-playing style \"${st.name}\" sets some of these, and wins where it does.", null) {}
            }
            Segments("Background", PlayerBackground.entries, look.playerBackground, { it.label }) { v -> change { it.copy(playerBackground = v) } }
            Choice("Album art", PlayerArt.entries, look.playerArt, { it.label }) { v -> change { it.copy(playerArt = v) } }
            Segments("Album art size", ArtSize.entries, look.artSize, { it.label }) { v -> change { it.copy(artSize = v) } }
            Segments("Song name", TitleAlign.entries, look.titleAlign, { it.label }) { v -> change { it.copy(titleAlign = v) } }
            Segments("Play button", PlayButtonStyle.entries, look.playButton, { it.label }) { v -> change { it.copy(playButton = v) } }
            Segments("Seek bar", SeekBarStyle.entries, look.seekBar, { it.label }) { v -> change { it.copy(seekBar = v) } }
            DetailsStylePicker(settings.detailsStyle) { d -> vm.updateSettings { it.copy(detailsStyle = d) } }
            SwitchRow("Times under the seek bar", null, look.showTimes) { on -> change { it.copy(showTimes = on) } }
            SwitchRow("Shuffle and repeat buttons", null, look.showShuffleRepeat) { on -> change { it.copy(showShuffleRepeat = on) } }
            SwitchRow("Artist photo", "Top left; tap it for the artist. Off shows a close button instead.", look.showArtistPhoto) { on ->
                change { it.copy(showArtistPhoto = on) }
            }
            SwitchRow("Speaker button", "Pick Bluetooth, the phone's speaker or another device.", look.showOutputButton) { on ->
                change { it.copy(showOutputButton = on) }
            }
            SwitchRow("\"Playing from\"", "Where the queue came from; tap it to go there.", look.showPlayingFrom) { on ->
                change { it.copy(showPlayingFrom = on) }
            }
            SwitchRow("Share button", null, look.showShare) { on -> change { it.copy(showShare = on) } }
            SwitchRow("Swipe the art to change songs", null, look.swipeArt) { on -> change { it.copy(swipeArt = on) } }
            SwitchRow("Keep the screen on", "While the playing screen is open.", look.keepScreenOn) { on -> change { it.copy(keepScreenOn = on) } }
            if (player.current != null) {
                OutlinedButton(onClick = { ui.openPlayer() }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open the playing screen")
                }
            } else {
                Hint("Play something to see your changes on the playing screen.")
            }

            // --- mini player --------------------------------------------------------------------
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Group("Mini player")
            Segments("Style", MiniStyle.entries, look.miniStyle, { it.label }) { v -> change { it.copy(miniStyle = v) } }
            SwitchRow("Progress line", null, look.miniProgress) { on -> change { it.copy(miniProgress = on) } }
            SwitchRow("Previous button", null, look.miniPrevious) { on -> change { it.copy(miniPrevious = on) } }
            SwitchRow("Next button", null, look.miniNext) { on -> change { it.copy(miniNext = on) } }
            SwitchRow("Swipe to change songs", null, look.miniSwipe) { on -> change { it.copy(miniSwipe = on) } }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(16.dp)) { Text("Reset to NaviMuse's look") }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset to NaviMuse's look?") },
            text = {
                Text(
                    "Colours, fonts, icons and layout go back to how NaviMuse comes. " +
                        "Imported colour themes stay in the list, and Symfonium styles stay until you remove them.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.updateSettings { it.copy(look = Look(), themeId = null, iconPack = null, detailsStyle = DetailsStyle.TIDAL) }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

private fun fontFamilyOf(f: FontChoice): FontFamily = when (f) {
    FontChoice.INSTRUMENT -> Sans
    FontChoice.PHONE -> FontFamily.Default
    FontChoice.SERIF -> FontFamily.Serif
    FontChoice.MONO -> FontFamily.Monospace
}

/** A small sample of the current look: art corners, title font, accent. */
@Composable
private fun LookPreview() {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtPlaceholder("Preview album", Icons.Filled.Album, Modifier.size(76.dp).clip(ArtShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Night Drive", style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Preview Artist · 2026", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            QualityBadge("24-BIT 96kHZ FLAC")
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(48.dp).background(scheme.secondary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, null, tint = scheme.onSecondary)
        }
    }
}

/** Round swatches for the accent colour, plus the theme's own, the song's art and the wallpaper. */
@Composable
private fun AccentPicker(value: Accent, onPick: (Accent) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDark
    val options = Accent.entries.filter { it != Accent.PHONE || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    Column(Modifier.padding(top = 12.dp)) {
        Text("Accent colour", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            value.label + when (value) {
                Accent.THEME -> " (NaviMuse's teal, or an imported theme's main colour)"
                Accent.ALBUM -> " (changes with each song)"
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.forEach { a ->
                val selected = a == value
                val fill = if (a.isPreset) Color(if (dark) a.dark else a.light) else scheme.surfaceContainerHigh
                val onFill = if (fill.luminance() > 0.5f) Color.Black else Color.White
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .border(if (selected) 3.dp else 1.dp, if (selected) scheme.onSurface else scheme.outlineVariant, CircleShape)
                        .clickable { onPick(a) }
                        .semantics { contentDescription = a.label },
                    contentAlignment = Alignment.Center,
                ) {
                    when (a) {
                        Accent.THEME -> Icon(Icons.Filled.Palette, null, modifier = Modifier.size(20.dp))
                        Accent.ALBUM -> Icon(Icons.Filled.Album, null, modifier = Modifier.size(20.dp))
                        Accent.PHONE -> Icon(Icons.Filled.Wallpaper, null, modifier = Modifier.size(20.dp))
                        else -> if (selected) Icon(Icons.Filled.Check, null, tint = onFill, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** A row of segmented buttons with a title above, for a few short choices. */
@Composable
private fun <T> Segments(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    note: String? = null,
    onPick: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, o ->
                SegmentedButton(
                    selected = o == selected,
                    onClick = { onPick(o) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    // With four choices there's no room for the check mark; the fill shows the choice.
                    icon = { if (options.size <= 3) SegmentedButtonDefaults.Icon(o == selected) },
                ) { Text(label(o), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        note?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A setting with a longer list of choices, picked from a menu. [fontOf] shows each in its own font. */
@Composable
private fun <T> Choice(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    fontOf: ((T) -> FontFamily)? = null,
    onPick: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(label(selected)) },
        trailingContent = {
            Icon(Icons.Filled.KeyboardArrowDown, null)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(label(o), fontFamily = fontOf?.invoke(o)) },
                        leadingIcon = { if (o == selected) Icon(Icons.Filled.Check, null) },
                        onClick = {
                            expanded = false
                            onPick(o)
                        },
                    )
                }
            }
        },
        modifier = Modifier.clickable { expanded = true },
    )
}

/**
 * Shown entries first, in order, each with a switch and arrows to move it; then the hidden ones,
 * switched off. Switching one on adds it at the end. [locked] entries can't be switched off.
 */
@Composable
private fun <T> OrderList(
    all: List<T>,
    shown: List<T>,
    label: (T) -> String,
    locked: (T) -> Boolean = { false },
    onChange: (List<T>) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    shown.forEachIndexed { i, item ->
        val name = label(item)
        val canHide = !locked(item)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = canHide) { onChange(shown - item) }
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(checked = true, onCheckedChange = { onChange(shown - item) }, enabled = canHide)
            Spacer(Modifier.width(14.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { onChange(shown.moved(i, i - 1)) }, enabled = i > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, "Move $name up")
            }
            IconButton(onClick = { onChange(shown.moved(i, i + 1)) }, enabled = i < shown.lastIndex) {
                Icon(Icons.Filled.KeyboardArrowDown, "Move $name down")
            }
        }
    }
    all.filter { it !in shown }.forEach { item ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onChange(shown + item) }
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(checked = false, onCheckedChange = { onChange(shown + item) })
            Spacer(Modifier.width(14.dp))
            Text(label(item), style = MaterialTheme.typography.bodyLarge, color = muted, modifier = Modifier.weight(1f))
        }
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (to !in indices) return this
    val m = toMutableList()
    m.add(to, m.removeAt(from))
    return m
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Says a Symfonium style is deciding something, with a way back to these settings. */
@Composable
private fun StyleNote(text: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}
