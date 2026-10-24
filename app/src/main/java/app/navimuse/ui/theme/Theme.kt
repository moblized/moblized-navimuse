package app.navimuse.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// A late-night listening palette: deep violet primary, warm coral accent.
private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3FD0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1B0063),
    secondary = Color(0xFF5F5C71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5DFF9),
    onSecondaryContainer = Color(0xFF1C192B),
    tertiary = Color(0xFF9A4521),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCD),
    onTertiaryContainer = Color(0xFF360F00),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE5E0EC),
    onSurfaceVariant = Color(0xFF48454E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2FA),
    surfaceContainer = Color(0xFFF0ECF4),
    surfaceContainerHigh = Color(0xFFEBE6EF),
    surfaceContainerHighest = Color(0xFFE5E1E9),
    outline = Color(0xFF79757F),
    outlineVariant = Color(0xFFC9C4D0),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCBBEFF),
    onPrimary = Color(0xFF2E009C),
    primaryContainer = Color(0xFF4422B8),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFC9C3DC),
    onSecondary = Color(0xFF312E41),
    secondaryContainer = Color(0xFF474459),
    onSecondaryContainer = Color(0xFFE5DFF9),
    tertiary = Color(0xFFFFB597),
    onTertiary = Color(0xFF581E00),
    tertiaryContainer = Color(0xFF7B2F0B),
    onTertiaryContainer = Color(0xFFFFDBCD),
    background = Color(0xFF141318),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF141318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF48454E),
    onSurfaceVariant = Color(0xFFC9C4D0),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1C1B20),
    surfaceContainer = Color(0xFF201F25),
    surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF36343A),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF48454E),
    error = Color(0xFFFFB4AB),
)

/** Green used for the "protected" safety badges. */
val SafeGreenLight = Color(0xFF1B7F4B)
val SafeGreenDark = Color(0xFF6BDB9C)

@Composable
fun AppTheme(dark: Boolean, dynamicColor: Boolean, pureBlack: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    if (dark && pureBlack) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0B0B),
            surfaceContainer = Color(0xFF111111),
            surfaceContainerHigh = Color(0xFF181818),
            surfaceContainerHighest = Color(0xFF202020),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
