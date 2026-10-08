package app.tellycine.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import app.tellycine.ui.theme.DarkColors

/** TV colours: the phone app's dark palette, on a slightly deeper background for the big screen. */
object TvPalette {
    val background = Color(0xFF110D09)
    val surface = Color(0xFF1F1B16)
    val surfaceHigh = Color(0xFF2E2924)
    val surfaceHighest = Color(0xFF39342E)
    val onSurface = Color(0xFFEBE1D9)
    val onSurfaceVariant = Color(0xFFD3C4B4)
    val primary = Color(0xFFFFB951)
    val onPrimary = Color(0xFF462A00)
    val focus = Color(0xFFFFDDB3)
}

private val TvColors = darkColorScheme(
    primary = TvPalette.primary,
    onPrimary = TvPalette.onPrimary,
    primaryContainer = Color(0xFF643F00),
    onPrimaryContainer = Color(0xFFFFDDB3),
    inversePrimary = Color(0xFF845400),
    secondary = Color(0xFFDDC2A1),
    onSecondary = Color(0xFF3E2D16),
    secondaryContainer = Color(0xFF56442A),
    onSecondaryContainer = Color(0xFFFADEBC),
    tertiary = Color(0xFFB8CEA1),
    onTertiary = Color(0xFF243515),
    tertiaryContainer = Color(0xFF3A4C2A),
    onTertiaryContainer = Color(0xFFD4EABB),
    background = TvPalette.background,
    onBackground = TvPalette.onSurface,
    surface = TvPalette.surface,
    onSurface = TvPalette.onSurface,
    surfaceVariant = TvPalette.surfaceHigh,
    onSurfaceVariant = TvPalette.onSurfaceVariant,
    // Focused items light up in the warm off-white of the reel's highlights.
    inverseSurface = TvPalette.focus,
    inverseOnSurface = Color(0xFF2A1800),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    border = Color(0xFF9C8E80),
    borderVariant = Color(0xFF4F4539),
    scrim = Color.Black,
)

/** Compose Material 3 colours for the parts TV Material doesn't provide (text fields, progress). */
private val TvMaterial3Colors = DarkColors.copy(
    background = TvPalette.background,
    surface = TvPalette.background,
    surfaceContainerLowest = Color(0xFF0C0906),
    surfaceContainerLow = Color(0xFF1A1611),
    surfaceContainer = TvPalette.surface,
    surfaceContainerHigh = TvPalette.surfaceHigh,
    surfaceContainerHighest = TvPalette.surfaceHighest,
)

@Composable
fun TellycineTvTheme(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(colorScheme = TvMaterial3Colors) {
        MaterialTheme(colorScheme = TvColors, content = content)
    }
}
