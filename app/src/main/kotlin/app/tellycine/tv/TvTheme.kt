package app.tellycine.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import app.tellycine.container
import app.tellycine.data.settings.TvTheme
import app.tellycine.ui.components.ArtworkTint
import app.tellycine.ui.components.tintedBy
import app.tellycine.ui.theme.DarkColors

/** The colours of a TV theme. All are dark: a big, bright screen is tiring to look at in a dim room. */
@Immutable
data class TvColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    /** The highlight on the focused item, and the text on it. */
    val focus: Color,
    val onFocus: Color,
    /** Programmes on now in the guide, and their text. */
    val onNow: Color,
    val onOnNow: Color,
    /** Programmes that have finished, in the guide. */
    val past: Color,
)

/** The colours of each theme in Settings. */
fun TvTheme.colors(): TvColors = when (this) {
    // The phone app's dark palette, on a slightly deeper background for the big screen.
    TvTheme.WARM -> TvColors(
        background = Color(0xFF110D09),
        surface = Color(0xFF1F1B16),
        surfaceHigh = Color(0xFF2E2924),
        surfaceHighest = Color(0xFF39342E),
        onSurface = Color(0xFFEBE1D9),
        onSurfaceVariant = Color(0xFFD3C4B4),
        primary = Color(0xFFFFB951),
        onPrimary = Color(0xFF462A00),
        focus = Color(0xFFFFDDB3),
        onFocus = Color(0xFF2A1800),
        onNow = Color(0xFF56442A),
        onOnNow = Color(0xFFFADEBC),
        past = Color(0xFF1A1611),
    )
    // Blue-black with cool white text and a sky-blue accent.
    TvTheme.MIDNIGHT -> TvColors(
        background = Color(0xFF0A0D13),
        surface = Color(0xFF151A23),
        surfaceHigh = Color(0xFF202734),
        surfaceHighest = Color(0xFF2A3242),
        onSurface = Color(0xFFEEF2F8),
        onSurfaceVariant = Color(0xFFB8C2D2),
        primary = Color(0xFF7CB9FF),
        onPrimary = Color(0xFF00315C),
        focus = Color(0xFFDDEBFF),
        onFocus = Color(0xFF0A1A2E),
        onNow = Color(0xFF1F3A5E),
        onOnNow = Color(0xFFD6E7FF),
        past = Color(0xFF10141B),
    )
    // Neutral greys with pure white text: the crispest-looking text, and no colour cast.
    TvTheme.GRAPHITE -> TvColors(
        background = Color(0xFF0D0D0D),
        surface = Color(0xFF1A1A1A),
        surfaceHigh = Color(0xFF262626),
        surfaceHighest = Color(0xFF323232),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFFC9C9C9),
        primary = Color(0xFF4FD8C9),
        onPrimary = Color(0xFF00332D),
        focus = Color(0xFFFFFFFF),
        onFocus = Color(0xFF000000),
        onNow = Color(0xFF1D3B38),
        onOnNow = Color(0xFFD3F7F2),
        past = Color(0xFF121212),
    )
    // Deep green with a soft green accent.
    TvTheme.FOREST -> TvColors(
        background = Color(0xFF0A100C),
        surface = Color(0xFF151F19),
        surfaceHigh = Color(0xFF203026),
        surfaceHighest = Color(0xFF2A3C31),
        onSurface = Color(0xFFE8F1EA),
        onSurfaceVariant = Color(0xFFB8C9BC),
        primary = Color(0xFF7FDA9C),
        onPrimary = Color(0xFF00391B),
        focus = Color(0xFFD8F5E0),
        onFocus = Color(0xFF0B2614),
        onNow = Color(0xFF24432F),
        onOnNow = Color(0xFFCDEFD7),
        past = Color(0xFF0E1611),
    )
    // Black, white and yellow, for the most contrast: easiest to read from across the room.
    TvTheme.HIGH_CONTRAST -> TvColors(
        background = Color(0xFF000000),
        surface = Color(0xFF121212),
        surfaceHigh = Color(0xFF1F1F1F),
        surfaceHighest = Color(0xFF2B2B2B),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFFE6E6E6),
        primary = Color(0xFFFFD600),
        onPrimary = Color(0xFF000000),
        focus = Color(0xFFFFD600),
        onFocus = Color(0xFF000000),
        onNow = Color(0xFF3D3600),
        onOnNow = Color(0xFFFFF4A3),
        past = Color(0xFF0A0A0A),
    )
}

private val LocalTvColors = staticCompositionLocalOf { TvTheme.WARM.colors() }

/** The current TV theme's colours. */
object TvPalette {
    val background: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.background
    val surface: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.surface
    val surfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.surfaceHigh
    val surfaceHighest: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.surfaceHighest
    val onSurface: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.onSurface
    val onSurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.onSurfaceVariant
    val primary: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.primary
    val onPrimary: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.onPrimary
    val focus: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.focus
    val onFocus: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.onFocus
    val onNow: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.onNow
    val onOnNow: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.onOnNow
    val past: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.past
}

/** Warm keeps the scheme the TV interface has always had. */
private val WarmScheme = darkColorScheme(
    primary = Color(0xFFFFB951),
    onPrimary = Color(0xFF462A00),
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
    background = Color(0xFF110D09),
    onBackground = Color(0xFFEBE1D9),
    surface = Color(0xFF1F1B16),
    onSurface = Color(0xFFEBE1D9),
    surfaceVariant = Color(0xFF2E2924),
    onSurfaceVariant = Color(0xFFD3C4B4),
    // Focused items light up in the warm off-white of the reel's highlights.
    inverseSurface = Color(0xFFFFDDB3),
    inverseOnSurface = Color(0xFF2A1800),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    border = Color(0xFF9C8E80),
    borderVariant = Color(0xFF4F4539),
    scrim = Color.Black,
)

/** The other themes' schemes, from their colours. */
private fun tvColorScheme(c: TvColors) = darkColorScheme(
    primary = c.primary,
    onPrimary = c.onPrimary,
    primaryContainer = c.onNow,
    onPrimaryContainer = c.onOnNow,
    inversePrimary = c.primary,
    secondary = c.onSurfaceVariant,
    onSecondary = c.background,
    secondaryContainer = c.onNow,
    onSecondaryContainer = c.onOnNow,
    tertiary = Color(0xFFB8CEA1),
    onTertiary = Color(0xFF243515),
    tertiaryContainer = Color(0xFF3A4C2A),
    onTertiaryContainer = Color(0xFFD4EABB),
    background = c.background,
    onBackground = c.onSurface,
    surface = c.surface,
    onSurface = c.onSurface,
    surfaceVariant = c.surfaceHigh,
    onSurfaceVariant = c.onSurfaceVariant,
    // Focused items light up in the theme's highlight.
    inverseSurface = c.focus,
    inverseOnSurface = c.onFocus,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    border = c.onSurfaceVariant.copy(alpha = 0.7f),
    borderVariant = c.surfaceHighest,
    scrim = Color.Black,
)

/** Compose Material 3 colours for the parts TV Material doesn't provide (text fields, progress). */
private val WarmMaterial3Scheme = DarkColors.copy(
    background = Color(0xFF110D09),
    surface = Color(0xFF110D09),
    surfaceContainerLowest = Color(0xFF0C0906),
    surfaceContainerLow = Color(0xFF1A1611),
    surfaceContainer = Color(0xFF1F1B16),
    surfaceContainerHigh = Color(0xFF2E2924),
    surfaceContainerHighest = Color(0xFF39342E),
)

private fun material3ColorScheme(c: TvColors) = DarkColors.copy(
    primary = c.primary,
    onPrimary = c.onPrimary,
    primaryContainer = c.onNow,
    onPrimaryContainer = c.onOnNow,
    secondaryContainer = c.onNow,
    onSecondaryContainer = c.onOnNow,
    background = c.background,
    onBackground = c.onSurface,
    surface = c.background,
    onSurface = c.onSurface,
    onSurfaceVariant = c.onSurfaceVariant,
    surfaceContainerLowest = c.background,
    surfaceContainerLow = c.past,
    surfaceContainer = c.surface,
    surfaceContainerHigh = c.surfaceHigh,
    surfaceContainerHighest = c.surfaceHighest,
)

/** The TV interface's theme, in the colours chosen in Settings. */
@Composable
fun TellycineTvTheme(content: @Composable () -> Unit) {
    val theme by LocalContext.current.container.tvTheme.collectAsStateWithLifecycle()
    val colors = theme.colors()
    val scheme = if (theme == TvTheme.WARM) WarmScheme else tvColorScheme(colors)
    CompositionLocalProvider(LocalTvColors provides colors) {
        val material3 = if (theme == TvTheme.WARM) WarmMaterial3Scheme else material3ColorScheme(colors)
        androidx.compose.material3.MaterialTheme(colorScheme = material3) {
            MaterialTheme(colorScheme = scheme, content = content)
        }
    }
}

/**
 * Tints [content] with colours from a title's artwork: the background, surfaces and accent. The
 * focus highlight keeps the theme's colours, and High contrast isn't tinted at all; [content] is
 * told whether it's tinted.
 */
@Composable
fun TvArtworkTint(tint: ArtworkTint?, content: @Composable (tinted: Boolean) -> Unit) {
    val base = LocalTvColors.current
    val use = tint?.takeIf { base != TvTheme.HIGH_CONTRAST.colors() }
    val spec = tween<Color>(600)
    val background by animateColorAsState(use?.background ?: base.background, spec, label = "background")
    val accent by animateColorAsState(use?.accent ?: base.primary, spec, label = "accent")
    val onAccent by animateColorAsState(use?.onAccent ?: base.onPrimary, spec, label = "onAccent")
    CompositionLocalProvider(LocalTvColors provides base.copy(background = background, primary = accent, onPrimary = onAccent)) {
        androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.MaterialTheme.colorScheme.tintedBy(use)) {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(background = background, primary = accent, onPrimary = onAccent)) {
                content(use != null)
            }
        }
    }
}
