package app.tellycine.ui.components

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.scale
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.roundToInt

/** How long each backdrop shows for, and how long one takes to fade into the next. */
private const val SLIDE_MS = 9_000L
private const val FADE_MS = 1_500

/** How far a backdrop slowly zooms in while it's shown. */
private const val ZOOM = 0.06f

/** The blurred poster is drawn from an image this many pixels across, so it's cheap and soft. */
private const val BLUR_SIZE = 40
private const val BLUR_RADIUS = 2

/**
 * The artwork behind a movie or series page: its [backdrops] in turn, fading from one to the
 * next and slowly zooming in, or a blurred, enlarged [poster] when there's no backdrop (or none
 * loads). Nothing is drawn when there's neither, so the page's own background shows.
 */
@Composable
fun DetailBackdrop(backdrops: List<String>, poster: String?, modifier: Modifier = Modifier) {
    var failed by remember(backdrops) { mutableStateOf(emptySet<String>()) }
    val shown = remember(backdrops, failed) { backdrops.filter { it.isNotBlank() && it !in failed } }
    when {
        shown.isNotEmpty() -> BackdropSlideshow(shown, modifier, onError = { failed = failed + it })
        !poster.isNullOrBlank() -> BlurredArtwork(poster, modifier.graphicsLayer { alpha = 0.6f })
    }
}

@Composable
private fun BackdropSlideshow(urls: List<String>, modifier: Modifier, onError: (String) -> Unit) {
    val context = LocalContext.current
    // Respect "Remove animations": a still picture, with no fading or zooming.
    val noAnimations = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (urls.size == 1 || noAnimations) {
        BackdropImage(urls.first(), modifier, onError)
        return
    }

    var index by remember(urls) { mutableIntStateOf(0) }
    val now = remember { mutableLongStateOf(0L) }
    // When each backdrop started showing, for its zoom.
    val shownAt = remember(urls) { mutableStateMapOf<Int, Long>() }
    LaunchedEffect(urls) {
        // Timed by animation frames, which pause while the screen isn't showing (and which tests skip).
        var since = withInfiniteAnimationFrameMillis { it }
        shownAt[index] = since
        while (true) {
            val t = withInfiniteAnimationFrameMillis { it }
            now.longValue = t
            if (t - since >= SLIDE_MS) {
                index = (index + 1) % urls.size
                since = t
                shownAt[index] = t
            }
        }
    }
    // Fetch the next backdrop ahead, so it fades in rather than popping in.
    LaunchedEffect(urls, index) {
        SingletonImageLoader.get(context).enqueue(ImageRequest.Builder(context).data(urls[(index + 1) % urls.size]).build())
    }
    Crossfade(targetState = index.coerceIn(urls.indices), animationSpec = tween(FADE_MS), modifier = modifier, label = "backdrop") { i ->
        BackdropImage(
            urls[i],
            Modifier.fillMaxSize().graphicsLayer {
                val start = shownAt[i] ?: return@graphicsLayer
                val age = ((now.longValue - start).toFloat() / (SLIDE_MS + FADE_MS)).coerceIn(0f, 1f)
                scaleX = 1f + ZOOM * age
                scaleY = 1f + ZOOM * age
            },
            onError,
        )
    }
}

@Composable
private fun BackdropImage(url: String, modifier: Modifier, onError: (String) -> Unit) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
        onError = { onError(url) },
    )
}

@Composable
private fun BlurredArtwork(url: String, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(BLUR_SIZE * 2)
            .allowHardware(false)
            .transformations(SoftBlur)
            .build()
    }
    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.fillMaxSize())
}

/**
 * Shrinks artwork to a few dozen pixels and blurs it, so drawn large it's a soft wash of its
 * colours. Done by hand, as the platform's blur needs Android 12 and many TVs are older.
 */
private object SoftBlur : Transformation() {
    override val cacheKey = "soft-blur-$BLUR_SIZE-$BLUR_RADIUS"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val scale = BLUR_SIZE.toFloat() / maxOf(input.width, input.height)
        val w = (input.width * scale).roundToInt().coerceAtLeast(1)
        val h = (input.height * scale).roundToInt().coerceAtLeast(1)
        val small = input.scale(w, h)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        // Three box blurs look much like a Gaussian blur.
        repeat(3) { boxBlur(pixels, w, h, BLUR_RADIUS) }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}

/** Blurs [pixels] (a [w] by [h] image) in place, averaging each pixel with those [r] either side. */
internal fun boxBlur(pixels: IntArray, w: Int, h: Int, r: Int) {
    val tmp = IntArray(pixels.size)
    blurLines(pixels, tmp, lines = h, length = w, r) { line, i -> line * w + i }
    blurLines(tmp, pixels, lines = w, length = h, r) { line, i -> i * w + line }
}

private inline fun blurLines(src: IntArray, dst: IntArray, lines: Int, length: Int, r: Int, at: (Int, Int) -> Int) {
    val n = 2 * r + 1
    for (line in 0 until lines) {
        for (i in 0 until length) {
            var a = 0
            var red = 0
            var green = 0
            var blue = 0
            for (k in -r..r) {
                val c = src[at(line, (i + k).coerceIn(0, length - 1))]
                a += c ushr 24
                red += (c shr 16) and 0xFF
                green += (c shr 8) and 0xFF
                blue += c and 0xFF
            }
            dst[at(line, i)] = ((a / n) shl 24) or ((red / n) shl 16) or ((green / n) shl 8) or (blue / n)
        }
    }
}
