package app.navimuse.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
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

private fun display(size: Int, line: Int, tracking: Double) = TextStyle(
    fontFamily = Condensed,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private fun sans(weight: FontWeight, size: Double, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Sans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private val AppTypography = Typography(
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

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

/** Corner of album art: nearly square, like a record sleeve. */
val ArtShape = RoundedCornerShape(4.dp)

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

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
