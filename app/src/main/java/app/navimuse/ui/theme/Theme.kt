package app.navimuse.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import app.navimuse.data.Accent
import app.navimuse.data.ColorRole
import app.navimuse.data.DarkShade
import app.navimuse.data.FontChoice
import app.navimuse.data.Look
import app.navimuse.data.TitleFont
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.navimuse.R

/*
 * The look: a black stage that lets album art supply the colour.
 *
 *   Ink     #000000  background
 *   Graphite #161616 raised surfaces (mini player, sheets, search field)
 *   Smoke   #A3A3A3  secondary text
 *   Paper   #FFFFFF  text and the main buttons
 *   Signal  #3DDCD2  the one accent: what's playing, audio quality, active toggles
 *   Amber   #F2B84B  warnings (playlists AudioMuse-AI will replace)
 *
 * Type is Instrument Sans throughout; page titles and names use its condensed bold cut.
 */

private val Ink = Color(0xFF000000)
private val Paper = Color(0xFFFFFFFF)
private val Signal = Color(0xFF3DDCD2)
private val SignalDeep = Color(0xFF00807A)
private val Amber = Color(0xFFF2B84B)

private val DarkColors = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    primaryContainer = Color(0xFF262626),
    onPrimaryContainer = Paper,
    inversePrimary = Ink,
    secondary = Signal,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF1F1F1F),
    onSecondaryContainer = Paper,
    tertiary = Amber,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFF2B2312),
    onTertiaryContainer = Color(0xFFF6D894),
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFA3A3A3),
    surfaceTint = Color.Transparent,
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    surfaceDim = Ink,
    surfaceBright = Color(0xFF2E2E2E),
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Color(0xFF0F0F0F),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF282828),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF2A2A2A),
    error = Color(0xFFFF7A7A),
    onError = Ink,
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFFD0D0),
    scrim = Ink,
)

/** The dark look on dark grey instead of black. */
private val DarkGreyColors = DarkColors.copy(
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceDim = Color(0xFF121212),
    surfaceVariant = Color(0xFF262626),
    surfaceBright = Color(0xFF383838),
    surfaceContainerLowest = Color(0xFF0C0C0C),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF202020),
    surfaceContainerHigh = Color(0xFF292929),
    surfaceContainerHighest = Color(0xFF333333),
    primaryContainer = Color(0xFF2E2E2E),
    secondaryContainer = Color(0xFF292929),
    outlineVariant = Color(0xFF353535),
)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Color(0xFFEDEDED),
    onPrimaryContainer = Ink,
    inversePrimary = Paper,
    secondary = SignalDeep,
    onSecondary = Paper,
    secondaryContainer = Color(0xFFEDEDED),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF8A5600),
    onTertiary = Paper,
    tertiaryContainer = Color(0xFFFFF0D1),
    onTertiaryContainer = Color(0xFF3D2700),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF5C5C5C),
    surfaceTint = Color.Transparent,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    surfaceDim = Color(0xFFE6E6E6),
    surfaceBright = Paper,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFEBEBEB),
    surfaceContainerHighest = Color(0xFFE2E2E2),
    outline = Color(0xFF9C9C9C),
    outlineVariant = Color(0xFFDEDEDE),
    error = Color(0xFFC62828),
    onError = Paper,
    errorContainer = Color(0xFFFFE3E3),
    onErrorContainer = Color(0xFF5C0F0F),
    scrim = Ink,
)

/** Instrument Sans, for everything. */
val Sans = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_bold, FontWeight.Bold),
)

/** Instrument Sans condensed bold, for page titles, names and the song that's playing. */
val Condensed = FontFamily(Font(R.font.instrument_sans_condensed_bold, FontWeight.Bold))

/** Type for the font, title style and text size chosen in Customize. */
private fun typographyFor(look: Look): Typography {
    val body = when (look.font) {
        FontChoice.INSTRUMENT -> Sans
        FontChoice.PHONE -> FontFamily.Default
        FontChoice.SERIF -> FontFamily.Serif
        FontChoice.MONO -> FontFamily.Monospace
    }
    val narrow = look.font == FontChoice.INSTRUMENT && look.titleFont == TitleFont.CONDENSED
    val titles = if (narrow) Condensed else body
    val k = look.effectiveTextScale
    // Page titles and names can be sized on their own in Fine-tune.
    val kt = k * look.effectiveTitleScale
    fun display(size: Int, line: Int, tracking: Double) = TextStyle(
        fontFamily = titles,
        fontWeight = FontWeight.Bold,
        fontSize = (size * kt).sp,
        lineHeight = (line * kt).sp,
        // Tight tracking suits the narrow cut; wider fonts get less of it.
        letterSpacing = (if (narrow) tracking else tracking * 0.4).sp,
    )
    fun sans(weight: FontWeight, size: Double, line: Int, tracking: Double = 0.0) = TextStyle(
        fontFamily = body,
        fontWeight = weight,
        fontSize = (size * k).sp,
        lineHeight = (line * k).sp,
        letterSpacing = tracking.sp,
    )
    return Typography(
        displayLarge = display(56, 56, -1.2),
        displayMedium = display(46, 48, -0.9),
        displaySmall = display(38, 40, -0.6),
        headlineLarge = display(34, 36, -0.5),
        headlineMedium = display(30, 32, -0.4),
        headlineSmall = sans(FontWeight.Bold, 22.0, 28, -0.3),
        titleLarge = sans(FontWeight.Bold, 20.0, 24, -0.3),
        titleMedium = sans(FontWeight.SemiBold, 16.0, 20, -0.1),
        titleSmall = sans(FontWeight.SemiBold, 14.0, 18),
        bodyLarge = sans(FontWeight.Normal, 16.0, 22),
        bodyMedium = sans(FontWeight.Normal, 14.0, 20),
        bodySmall = sans(FontWeight.Normal, 12.5, 16, 0.1),
        labelLarge = sans(FontWeight.SemiBold, 14.0, 18, 0.1),
        labelMedium = sans(FontWeight.Medium, 12.0, 16, 0.2),
        labelSmall = sans(FontWeight.Medium, 11.0, 14, 0.3),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

/** The choices from Settings → Customize, for any screen that needs them. */
val LocalLook = staticCompositionLocalOf { Look() }

private val artShapes = app.navimuse.data.Corners.entries.associateWith { RoundedCornerShape(it.dp.dp) }

/** Corner of album art, as chosen in Settings (nearly square by default, like a record sleeve). */
val ArtShape: Shape
    @Composable @ReadOnlyComposable
    get() = LocalLook.current.let { look ->
        look.artCornerDp?.let { RoundedCornerShape(it.dp) } ?: artShapes.getValue(look.corners)
    }

/** Green used for the "protected" safety badges. */
val SafeGreenLight = Color(0xFF1B7F4B)
val SafeGreenDark = Color(0xFF6BDB9C)

/** The single accent colour: what's playing, audio quality, active toggles. */
val ColorScheme.accent: Color get() = secondary

/** Warnings, such as playlists AudioMuse-AI will replace. */
val ColorScheme.warning: Color get() = tertiary

/** True when the app is drawing its dark look. */
val ColorScheme.isDark: Boolean get() = background.red + background.green + background.blue < 1.5f

object Tone {
    val accent: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
    val muted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * The app's colours with an imported Symfonium / Material theme laid over them. The theme's
 * primary colour becomes the app's accent too, as it is in Symfonium. Roles the theme leaves
 * out are worked out from its surface and text colours.
 */
fun themedScheme(base: ColorScheme, roles: Map<String, Long>, dark: Boolean): ColorScheme {
    fun c(role: String): Color? = roles[role]?.let { Color(it) }
    val surface = c("surface") ?: c("background") ?: base.surface
    val onSurface = c("onSurface") ?: c("onBackground") ?: base.onSurface
    fun step(amount: Float) = lerp(surface, onSurface, amount)
    val primary = c("primary") ?: base.primary
    val onPrimary = c("onPrimary") ?: if (primary.luminance() > 0.5f) Color.Black else Color.White
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = c("primaryContainer") ?: base.primaryContainer,
        onPrimaryContainer = c("onPrimaryContainer") ?: base.onPrimaryContainer,
        inversePrimary = c("inversePrimary") ?: base.inversePrimary,
        // The accent (what's playing, quality badges, active toggles) follows the theme's primary.
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = c("secondaryContainer") ?: step(0.16f),
        onSecondaryContainer = c("onSecondaryContainer") ?: onSurface,
        tertiaryContainer = c("tertiaryContainer") ?: base.tertiaryContainer,
        onTertiaryContainer = c("onTertiaryContainer") ?: base.onTertiaryContainer,
        error = c("error") ?: base.error,
        onError = c("onError") ?: base.onError,
        errorContainer = c("errorContainer") ?: base.errorContainer,
        onErrorContainer = c("onErrorContainer") ?: base.onErrorContainer,
        background = c("background") ?: surface,
        onBackground = c("onBackground") ?: onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = c("surfaceVariant") ?: step(0.12f),
        onSurfaceVariant = c("onSurfaceVariant") ?: lerp(onSurface, surface, 0.35f),
        surfaceTint = Color.Transparent,
        inverseSurface = c("inverseSurface") ?: onSurface,
        inverseOnSurface = c("inverseOnSurface") ?: surface,
        outline = c("outline") ?: step(0.45f),
        outlineVariant = c("outlineVariant") ?: step(0.2f),
        surfaceDim = c("surfaceDim") ?: surface,
        surfaceBright = c("surfaceBright") ?: step(0.18f),
        surfaceContainerLowest = c("surfaceContainerLowest") ?: lerp(surface, if (dark) Color.Black else Color.White, 0.5f),
        surfaceContainerLow = c("surfaceContainerLow") ?: step(0.04f),
        surfaceContainer = c("surfaceContainer") ?: step(0.07f),
        surfaceContainerHigh = c("surfaceContainerHigh") ?: step(0.1f),
        surfaceContainerHighest = c("surfaceContainerHighest") ?: step(0.14f),
    )
}

private fun onColor(c: Color): Color = if (c.luminance() > 0.5f) Color.Black else Color.White

/**
 * Exact colours from Settings → Fine-tune laid over the scheme. Shades a role implies (the
 * lighter and darker cards, the fainter lines) are worked out from the colour picked.
 */
fun ColorScheme.withOverrides(o: Map<ColorRole, Long>): ColorScheme {
    if (o.isEmpty()) return this
    var s = this
    o[ColorRole.ACCENT]?.let { s = s.withAccent(Color(it)) }
    o[ColorRole.BUTTONS]?.let { val c = Color(it); s = s.copy(primary = c, onPrimary = onColor(c)) }
    o[ColorRole.BACKGROUND]?.let { val c = Color(it); s = s.copy(background = c, surface = c, surfaceDim = c, surfaceContainerLowest = c) }
    o[ColorRole.TEXT]?.let { val c = Color(it); s = s.copy(onBackground = c, onSurface = c, onPrimaryContainer = c, onSecondaryContainer = c, inverseSurface = c) }
    o[ColorRole.CARDS]?.let {
        val c = Color(it)
        val text = s.onSurface
        s = s.copy(
            surfaceContainer = c,
            surfaceContainerLow = lerp(c, s.background, 0.4f),
            surfaceContainerHigh = lerp(c, text, 0.05f),
            surfaceContainerHighest = lerp(c, text, 0.1f),
            surfaceVariant = lerp(c, text, 0.04f),
            surfaceBright = lerp(c, text, 0.12f),
            secondaryContainer = lerp(c, text, 0.06f),
            primaryContainer = lerp(c, text, 0.08f),
        )
    }
    o[ColorRole.SECONDARY_TEXT]?.let { s = s.copy(onSurfaceVariant = Color(it)) }
    o[ColorRole.LINES]?.let { val c = Color(it); s = s.copy(outline = c, outlineVariant = lerp(c, s.background, 0.45f)) }
    return s
}

/** [color] with text on it in black or white, whichever reads better. */
fun ColorScheme.withAccent(color: Color): ColorScheme =
    copy(secondary = color, onSecondary = if (color.luminance() > 0.5f) Color.Black else Color.White)

/**
 * A colour taken from album art, made light enough to read on the dark look or dark enough
 * on the light one.
 */
fun readableAccent(color: Color, dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    if (dark) {
        hsl[1] = hsl[1].coerceAtMost(0.85f)
        hsl[2] = hsl[2].coerceIn(0.62f, 0.8f)
    } else {
        hsl[2] = hsl[2].coerceIn(0.25f, 0.4f)
    }
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * NaviMuse's colours, or an imported theme laid over them, then the accent, font, text size and
 * corners from Customize. [songAccent] is the playing song's art colour, used when the accent
 * follows the song.
 */
@Composable
fun AppTheme(
    dark: Boolean,
    custom: CustomTheme? = null,
    look: Look = Look(),
    songAccent: Color? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base = when {
        !dark -> LightColors
        look.darkShade == DarkShade.GREY -> DarkGreyColors
        else -> DarkColors
    }
    val themed = if (custom == null) base else themedScheme(base, custom.roles(dark), dark)
    val accent: Color? = when {
        look.accent.isPreset -> Color(if (dark) look.accent.dark else look.accent.light)
        look.accent == Accent.ALBUM -> songAccent?.let { readableAccent(it, dark) }
        look.accent == Accent.PHONE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
        else -> null
    }
    val scheme = (accent?.let { themed.withAccent(it) } ?: themed).withOverrides(look.colors(dark))
    val typography = remember(look.font, look.titleFont, look.effectiveTextScale, look.effectiveTitleScale) { typographyFor(look) }
    CompositionLocalProvider(LocalLook provides look) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
