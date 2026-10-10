package app.tellycine.ui.components

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Colours for a page tinted by a title's artwork. Text keeps the theme's colours, which read on all of these. */
@Immutable
data class ArtworkTint(
    val background: Color,
    val container: Color,
    val containerHigh: Color,
    val onContainer: Color,
    /** For the main button, progress and highlights, with [onAccent] for text on it. */
    val accent: Color,
    val onAccent: Color,
)

/**
 * The colour of [url]'s artwork: its most vibrant colour, or null until it's loaded, or if the
 * artwork is black and white (or nearly).
 */
@Composable
fun rememberArtworkColour(url: String?): Color? {
    val context = LocalContext.current
    val colour by produceState<Color?>(null, url) {
        value = url?.takeIf { it.isNotBlank() }?.let { artworkColour(context, it) }
    }
    return colour
}

private suspend fun artworkColour(context: Context, url: String): Color? {
    val loader = SingletonImageLoader.get(context)
    val request = ImageRequest.Builder(context).data(url).size(128).allowHardware(false).build()
    val result = loader.execute(request) as? SuccessResult ?: return null
    val bitmap = result.image.toBitmap()
    // Off the main thread, where the image loader decodes.
    return withContext(loader.defaults.decoderCoroutineContext) {
        val palette = Palette.from(bitmap).generate()
        val swatch = palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: palette.lightVibrantSwatch
            ?: palette.mutedSwatch ?: palette.dominantSwatch
        swatch?.hsl?.takeIf { it[1] >= 0.15f }?.let { Color.hsl(it[0], it[1], it[2]) }
    }
}

/**
 * Page colours in the hue of [source]: dark ones for a [dark] theme, light ones otherwise. The
 * accent is made light or dark enough for its text to stay readable.
 */
fun artworkTint(source: Color, dark: Boolean): ArtworkTint {
    val (h, s) = hueAndSaturation(source)
    fun hsl(sat: Float, light: Float) = Color.hsl(h, sat.coerceIn(0f, 1f), light)
    return if (dark) {
        val onAccent = hsl(min(s, 0.6f), 0.12f)
        ArtworkTint(
            background = hsl(min(s, 0.45f), 0.09f),
            container = hsl(min(s, 0.35f), 0.15f),
            containerHigh = hsl(min(s, 0.35f), 0.20f),
            onContainer = hsl(min(s, 0.4f), 0.88f),
            accent = readable(h, s.coerceIn(0.45f, 0.85f), from = 0.70f, step = 0.02f, against = onAccent),
            onAccent = onAccent,
        )
    } else {
        ArtworkTint(
            background = hsl(min(s, 0.5f), 0.96f),
            container = hsl(min(s, 0.4f), 0.92f),
            containerHigh = hsl(min(s, 0.4f), 0.88f),
            onContainer = hsl(min(s, 0.5f), 0.15f),
            accent = readable(h, s.coerceIn(0.45f, 0.8f), from = 0.40f, step = -0.02f, against = Color.White),
            onAccent = Color.White,
        )
    }
}

/** The colour of hue [h] and saturation [s], from lightness [from] in steps of [step], that first reads well with [against]. */
private fun readable(h: Float, s: Float, from: Float, step: Float, against: Color): Color {
    var light = from
    while (light in 0f..1f) {
        val c = Color.hsl(h, s, light)
        if (contrast(c, against) >= 4.5f) return c
        light += step
    }
    return Color.hsl(h, s, light.coerceIn(0f, 1f))
}

/** The WCAG contrast ratio between two colours: 4.5 or more is easy to read. */
internal fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

private fun hueAndSaturation(c: Color): Pair<Float, Float> {
    val r = c.red
    val g = c.green
    val b = c.blue
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val d = hi - lo
    if (d == 0f) return 0f to 0f
    val l = (hi + lo) / 2
    val s = d / (1 - kotlin.math.abs(2 * l - 1))
    val h = when (hi) {
        r -> 60 * (((g - b) / d).mod(6f))
        g -> 60 * ((b - r) / d + 2)
        else -> 60 * ((r - g) / d + 4)
    }
    return h to s.coerceIn(0f, 1f)
}

/** [this] scheme with [tint]'s colours, fading to them once they're known. */
@Composable
fun ColorScheme.tintedBy(tint: ArtworkTint?): ColorScheme {
    val spec = tween<Color>(600)
    val background by animateColorAsState(tint?.background ?: background, spec, label = "background")
    val surface by animateColorAsState(tint?.background ?: surface, spec, label = "surface")
    val container by animateColorAsState(tint?.container ?: surfaceContainer, spec, label = "container")
    val containerHigh by animateColorAsState(tint?.containerHigh ?: surfaceContainerHigh, spec, label = "containerHigh")
    val containerHighest by animateColorAsState(tint?.containerHigh ?: surfaceContainerHighest, spec, label = "containerHighest")
    val secondaryContainer by animateColorAsState(tint?.containerHigh ?: secondaryContainer, spec, label = "secondaryContainer")
    val onSecondaryContainer by animateColorAsState(tint?.onContainer ?: onSecondaryContainer, spec, label = "onSecondaryContainer")
    val primary by animateColorAsState(tint?.accent ?: primary, spec, label = "primary")
    val onPrimary by animateColorAsState(tint?.onAccent ?: onPrimary, spec, label = "onPrimary")
    return copy(
        background = background,
        surface = surface,
        surfaceContainer = container,
        surfaceContainerHigh = containerHigh,
        surfaceContainerHighest = containerHighest,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        primary = primary,
        onPrimary = onPrimary,
    )
}
