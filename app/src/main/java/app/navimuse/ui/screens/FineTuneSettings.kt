package app.navimuse.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.navimuse.data.Accent
import app.navimuse.data.ColorRole
import app.navimuse.data.Look
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.components.ActionPill
import app.navimuse.ui.icons.Icon
import app.navimuse.ui.isDark
import app.navimuse.ui.theme.accent
import kotlin.math.roundToInt

/**
 * Settings → Fine-tune: exact colours for every part of the app (dark and light mode each),
 * and exact sizes on sliders. A fine-tuned value wins over the preset picked in Look, Layout
 * or Player; ↺ puts it back.
 */
@Composable
internal fun FineTuneSettings(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val look = settings.look
    val dark = settings.isDark(isSystemInDarkTheme())
    val scheme = MaterialTheme.colorScheme
    var picking by remember { mutableStateOf<ColorRole?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    fun change(f: (Look) -> Look) = vm.updateLook(f)

    Hint("Exact colours and sizes. Changes show straight away, and ↺ puts one back to what the Look, Layout and Player tabs say.")

    Group(if (dark) "Colours in dark mode" else "Colours in light mode")
    Hint("Switch between light and dark in the Look tab to set the other mode's colours.")
    ColorRole.entries.forEach { role ->
        val set = look.colors(dark)[role]
        ColorRow(
            label = role.label,
            detail = set?.let { "#" + hex(Color(it)) } ?: role.detail,
            color = roleColor(scheme, role),
            fineTuned = set != null,
            onClick = { picking = role },
            onReset = { change { it.withColor(dark, role, null) } },
        )
    }

    HorizontalDivider(Modifier.padding(top = 8.dp))
    Group("Text")
    ScaleRow("Text size", look.effectiveTextScale, Look.TEXT_SCALE, 0.05f, look.textScale != null,
        { v -> change { it.copy(textScale = v) } }) { change { it.copy(textScale = null) } }
    ScaleRow("Page titles and names", look.effectiveTitleScale, Look.TITLE_SCALE, 0.05f, look.titleScale != null,
        { v -> change { it.copy(titleScale = v) } }) { change { it.copy(titleScale = null) } }

    HorizontalDivider(Modifier.padding(top = 8.dp))
    Group("Shapes and spacing")
    DpRow("Album art corners", look.artCorner, Look.ART_CORNER, look.artCornerDp != null,
        { v -> change { it.copy(artCornerDp = v) } }) { change { it.copy(artCornerDp = null) } }
    DpRow("Cards on Home", look.cardWidth, Look.CARD_SIZE, look.cardSizeDp != null,
        { v -> change { it.copy(cardSizeDp = v) } }) { change { it.copy(cardSizeDp = null) } }
    DpRow("Space around list rows", look.rowPadding, Look.ROW_PADDING, look.rowPaddingDp != null,
        { v -> change { it.copy(rowPaddingDp = v) } }) { change { it.copy(rowPaddingDp = null) } }

    HorizontalDivider(Modifier.padding(top = 8.dp))
    Group("Playing screen")
    val artCorner = look.playerArtCorner
    if (artCorner == null) {
        Hint("The album art is a circle (Player tab), so it has no corners to set.")
    } else {
        DpRow("Album art corners", artCorner, Look.PLAYER_ART_CORNER, look.playerArtCornerDp != null,
            { v -> change { it.copy(playerArtCornerDp = v) } }) { change { it.copy(playerArtCornerDp = null) } }
    }
    DpRow("Space beside the art", look.playerArtMargin, Look.PLAYER_ART_MARGIN, look.playerArtMarginDp != null,
        { v -> change { it.copy(playerArtMarginDp = v) } }) { change { it.copy(playerArtMarginDp = null) } }
    ScaleRow("Seek bar thickness", look.seekScale, Look.SEEK_SCALE, 0.1f, look.seekBarScale != null,
        { v -> change { it.copy(seekBarScale = v) } }) { change { it.copy(seekBarScale = null) } }
    DpRow("Play button", look.playButtonSize, Look.PLAY_BUTTON, look.playButtonDp != null,
        { v -> change { it.copy(playButtonDp = v) } }) { change { it.copy(playButtonDp = null) } }
    DpRow("Previous and next buttons", look.controlIcon, Look.CONTROL_ICON, look.controlIconDp != null,
        { v -> change { it.copy(controlIconDp = v) } }) { change { it.copy(controlIconDp = null) } }

    HorizontalDivider(Modifier.padding(top = 8.dp))
    Group("Mini player")
    DpRow("Cover size", look.miniArt, Look.MINI_ART, look.miniArtDp != null,
        { v -> change { it.copy(miniArtDp = v) } }) { change { it.copy(miniArtDp = null) } }
    DpRow("Corners (floating style)", look.miniCorner, Look.MINI_CORNER, look.miniCornerDp != null,
        { v -> change { it.copy(miniCornerDp = v) } }) { change { it.copy(miniCornerDp = null) } }

    HorizontalDivider(Modifier.padding(top = 8.dp))
    Group("Bottom tabs")
    DpRow("Bar height", look.tabBarHeight, Look.TAB_BAR, look.tabBarHeightDp != null,
        { v -> change { it.copy(tabBarHeightDp = v) } }) { change { it.copy(tabBarHeightDp = null) } }
    DpRow("Icon size", look.tabIcon, Look.TAB_ICON, look.tabIconDp != null,
        { v -> change { it.copy(tabIconDp = v) } }) { change { it.copy(tabIconDp = null) } }

    HorizontalDivider(Modifier.padding(top = 8.dp))
    Group("Share your look")
    Hint("Copies every choice in Look, Layout, Player and Fine-tune as text, to keep or send to someone. Paste one to use it.")
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionPill("Copy my look", Icons.Filled.ContentCopy, Modifier.weight(1f)) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("NaviMuse look", look.toJson()))
            vm.say("Copied your look")
        }
        ActionPill("Paste a look", Icons.Filled.ContentPaste, Modifier.weight(1f)) { pasting = true }
    }
    OutlinedButton(onClick = { confirmReset = true }, enabled = look.isFineTuned, modifier = Modifier.padding(16.dp)) {
        Text("Reset fine-tuning")
    }

    picking?.let { role ->
        ColorPickerDialog(
            title = role.label + if (dark) " (dark mode)" else " (light mode)",
            initial = roleColor(scheme, role),
            dark = dark,
            onDismiss = { picking = null },
        ) { c ->
            picking = null
            change { it.withColor(dark, role, c.toArgb().toLong() and 0xFFFFFFFFL) }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset fine-tuning?") },
            text = { Text("Every exact colour and size goes back to what the Look, Layout and Player tabs say. Those choices stay as they are.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    change { it.withoutFineTuning() }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    if (pasting) PasteLookDialog(vm) { pasting = false }
}

/** The colour a role has right now (fine-tuned, from a theme, or NaviMuse's own). */
private fun roleColor(s: ColorScheme, role: ColorRole): Color = when (role) {
    ColorRole.ACCENT -> s.secondary
    ColorRole.BUTTONS -> s.primary
    ColorRole.BACKGROUND -> s.background
    ColorRole.CARDS -> s.surfaceContainer
    ColorRole.TEXT -> s.onSurface
    ColorRole.SECONDARY_TEXT -> s.onSurfaceVariant
    ColorRole.LINES -> s.outline
}

private fun hex(c: Color): String = "%06X".format(c.toArgb() and 0xFFFFFF)

@Composable
private fun ColorRow(label: String, detail: String, color: Color, fineTuned: Boolean, onClick: () -> Unit, onReset: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (fineTuned) IconButton(onClick = onReset) { Icon(Icons.Filled.Refresh, "Reset $label") }
    }
}

/** A size in dp on a slider, with ↺ when it's been fine-tuned. */
@Composable
private fun DpRow(title: String, value: Int, range: IntRange, fineTuned: Boolean, onChange: (Int) -> Unit, onReset: () -> Unit) {
    TuneRow(title, "$value dp", value.toFloat(), range.first.toFloat()..range.last.toFloat(), fineTuned, { onChange(it.roundToInt()) }, onReset)
}

/** A scale (shown as a percentage) on a slider, rounded to [step]. */
@Composable
private fun ScaleRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    fineTuned: Boolean,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    TuneRow(title, "${(value * 100).roundToInt()}%", value, range, fineTuned, { onChange((it / step).roundToInt() * step) }, onReset)
}

@Composable
private fun TuneRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    fineTuned: Boolean,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = if (fineTuned) MaterialTheme.colorScheme.accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (fineTuned) IconButton(onClick = onReset) { Icon(Icons.Filled.Refresh, "Reset $title") } else Spacer(Modifier.width(12.dp))
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, modifier = Modifier.padding(end = 8.dp))
    }
}

/** Pick any colour: hue, saturation and brightness sliders, a hex code, or a quick swatch. */
@Composable
private fun ColorPickerDialog(title: String, initial: Color, dark: Boolean, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var h by remember { mutableFloatStateOf(start[0]) }
    var s by remember { mutableFloatStateOf(start[1]) }
    var v by remember { mutableFloatStateOf(start[2]) }
    fun hsv(hue: Float, sat: Float, value: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    val color = hsv(h, s, v)
    var code by remember { mutableStateOf(hex(initial)) }
    fun setFrom(c: Color) {
        val t = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), t)
        h = t[0]
        s = t[1]
        v = t[2]
        code = hex(c)
    }
    val swatches = Accent.entries.filter { it.isPreset }.map { Color(if (dark) it.dark else it.light) } +
        listOf(Color.Black, Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFF3A3A3A), Color(0xFFA3A3A3), Color(0xFFF2F2F2), Color.White)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(12.dp))
                GradientSlider("Hue", h, 0f..360f, Brush.horizontalGradient((0..6).map { hsv(it * 60f % 360f, 1f, 1f) })) {
                    h = it
                    code = hex(hsv(h, s, v))
                }
                GradientSlider("Saturation", s, 0f..1f, Brush.horizontalGradient(listOf(hsv(h, 0f, v), hsv(h, 1f, v)))) {
                    s = it
                    code = hex(hsv(h, s, v))
                }
                GradientSlider("Brightness", v, 0f..1f, Brush.horizontalGradient(listOf(Color.Black, hsv(h, s, 1f)))) {
                    v = it
                    code = hex(hsv(h, s, v))
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { t ->
                        code = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        if (code.length == 6) setFrom(Color(0xFF000000 or code.toLong(16)))
                    },
                    label = { Text("Hex code") },
                    prefix = { Text("#") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    swatches.forEach { c ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(c)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { setFrom(c) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("Use") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GradientSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, track: Brush, onChange: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(track))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

/** Paste a look someone copied (or you kept) and use it. */
@Composable
private fun PasteLookDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember {
        val clip = runCatching {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()
        mutableStateOf(clip?.trim()?.takeIf { it.startsWith("{") }.orEmpty())
    }
    var error by remember { mutableStateOf<String?>(null) }
    val err = error
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste a look") },
        text = {
            Column {
                Text("Paste the text from \"Copy my look\".", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    minLines = 3,
                    maxLines = 6,
                    isError = err != null,
                    supportingText = if (err != null) {
                        { Text(err) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val o = runCatching { org.json.JSONObject(text.trim()) }.getOrNull()
                if (o == null || !(o.has("accent") || o.has("home") || o.has("font"))) {
                    error = "That isn't a NaviMuse look."
                } else {
                    vm.updateLook { Look.fromJson(text.trim()) }
                    vm.say("Using the pasted look")
                    onDismiss()
                }
            }) { Text("Use it") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
