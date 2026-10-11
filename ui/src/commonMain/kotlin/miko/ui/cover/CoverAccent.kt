package miko.ui.cover

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Port of Android's `EntryCoverColorExtractor`: the details accent is the
 * cover's dominant perimeter color (edge ring avoids baked-in title
 * logos), saturation-injected, then theme-adjusted. The pixel math is
 * shared; each platform only decodes image bytes to ARGB.
 */

/** Decoded image at ≤128px max dimension — platform-produced. */
internal class DecodedPixels(val width: Int, val height: Int, val pixels: IntArray)

internal expect fun decodeCoverPixels(data: ByteArray): DecodedPixels?

internal fun rgbToHsl(argb: Int): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val maxC = maxOf(r, g, b)
    val minC = minOf(r, g, b)
    var h = 0f
    var s = 0f
    val l = (maxC + minC) / 2f
    if (maxC != minC) {
        val d = maxC - minC
        s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
        h = when (maxC) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        h /= 6f
    }
    return floatArrayOf(h * 360f, s, l)
}

internal fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        (r + m).coerceIn(0f, 1f),
        (g + m).coerceIn(0f, 1f),
        (b + m).coerceIn(0f, 1f),
        1f,
    )
}

/** Android's `injectSaturation` — gray covers get a forced hue floor. */
private fun injectSaturation(color: Int): Int {
    val hsl = rgbToHsl(color)
    return if (hsl[1] < 0.15f) {
        hslToColor(hsl[0], 0.4f, hsl[2]).toArgb()
    } else if (hsl[1] < 0.3f) {
        hslToColor(hsl[0], 0.5f, hsl[2]).toArgb()
    } else {
        color
    }
}

private const val MIN_ACCENT_SATURATION = 0.45f
private const val MAX_ACCENT_SATURATION = 0.65f

/** Android's `adjustForTheme` — HSL lightness nudge + saturation window. */
internal fun adjustForTheme(color: Int, isDark: Boolean): Int {
    val hsl = rgbToHsl(color)
    val luminance = relativeLuminance(color)
    when {
        !isDark && luminance > 0.4f -> {
            hsl[2] = (hsl[2] * (1f - luminance * 0.5f)).coerceIn(0f, 1f)
        }
        isDark && luminance <= 0.6f -> {
            hsl[2] = (hsl[2] + (1f - hsl[2]) * (1f - luminance) * 0.33f).coerceIn(0f, 1f)
        }
    }
    hsl[1] = hsl[1].coerceIn(MIN_ACCENT_SATURATION, MAX_ACCENT_SATURATION)
    return hslToColor(hsl[0], hsl[1], hsl[2]).toArgb()
}

private fun relativeLuminance(color: Int): Float {
    fun lin(v: Int): Float {
        val c = v / 255f
        return if (c <= 0.03928f) {
            c / 12.92f
        } else {
            ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
    }
    return 0.2126f * lin((color shr 16) and 0xFF) +
        0.7152f * lin((color shr 8) and 0xFF) +
        0.0722f * lin(color and 0xFF)
}

/**
 * Dominant cover accent — Android's edge-ring + dominant/vibrant swatch.
 * Pixels are quantized to 4-bit-per-channel buckets (Palette's approach);
 * the most populous bucket in the perimeter wins, falling back to the
 * most saturated populous bucket anywhere.
 */
internal fun dominantAccent(img: DecodedPixels): Int? {
    val w = img.width
    val h = img.height
    if (w < 8 || h < 8) return null
    val side = max(1, (w * 0.16f).toInt())
    val topRows = max(1, (h * 0.14f).toInt())
    val midBottom = min(h, (h * 0.72f).toInt())

    fun bucket(c: Int): Int {
        val r = ((c shr 16) and 0xFF) shr 4
        val g = ((c shr 8) and 0xFF) shr 4
        val b = (c and 0xFF) shr 4
        return (r shl 8) or (g shl 4) or b
    }

    val ringCounts = HashMap<Int, LongArray>(4096)
    fun addPixel(px: Int, counts: HashMap<Int, LongArray>) {
        val b = bucket(px)
        val acc = counts.getOrPut(b) { LongArray(4) }
        acc[0]++
        acc[1] += ((px shr 16) and 0xFF).toLong()
        acc[2] += ((px shr 8) and 0xFF).toLong()
        acc[3] += (px and 0xFF).toLong()
    }
    // Edge ring: top strip + left/right columns of the top ~72%.
    for (y in 0 until topRows) {
        for (x in 0 until w) {
            addPixel(img.pixels[y * w + x], ringCounts)
        }
    }
    for (y in topRows until midBottom) {
        for (x in 0 until side) {
            addPixel(img.pixels[y * w + x], ringCounts)
        }
        for (x in w - side until w) {
            addPixel(img.pixels[y * w + x], ringCounts)
        }
    }

    fun bucketAverage(acc: LongArray): Int =
        (
            0xFF shl 24 or
                ((acc[1] / acc[0]).toInt() shl 16) or
                ((acc[2] / acc[0]).toInt() shl 8) or
                (acc[3] / acc[0]).toInt()
            )

    val dominant = ringCounts.maxByOrNull { it.value[0] }
    if (dominant != null) return injectSaturation(bucketAverage(dominant.value))

    // Fallback: most saturated populous bucket anywhere in the image.
    val allCounts = HashMap<Int, LongArray>(4096)
    for (px in img.pixels) addPixel(px, allCounts)
    val vibrant = allCounts.maxByOrNull { (_, acc) ->
        val c = bucketAverage(acc)
        val hsl = rgbToHsl(c)
        (hsl[1] * 1000).toLong() + acc[0]
    }
    return vibrant?.let { injectSaturation(bucketAverage(it.value)) }
}

private val accentCache = HashMap<String, Int>()
private val accentClient by lazy { HttpClient() }

/** Session-cached accent for [coverUrl] — Android's `rememberEntryAccentColor`. */
@Composable
fun rememberEntryAccentColor(coverUrl: String?): Color? {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var base by remember(coverUrl) {
        mutableStateOf(coverUrl?.let { accentCache[it] })
    }
    if (!LocalInspectionMode.current) {
        LaunchedEffect(coverUrl) {
            if (coverUrl == null) {
                base = null
                return@LaunchedEffect
            }
            if (accentCache.containsKey(coverUrl)) {
                base = accentCache[coverUrl]
                return@LaunchedEffect
            }
            val raw = runCatching {
                val bytes = accentClient.get(coverUrl).bodyAsBytes()
                decodeCoverPixels(bytes)?.let(::dominantAccent)
            }.getOrNull() ?: return@LaunchedEffect
            accentCache[coverUrl] = raw
            base = raw
        }
    }
    return base?.let { Color(adjustForTheme(it, isDark)) }
        ?.let {
            animateColorAsState(
                targetValue = it,
                animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
                label = "entry_accent",
            ).value
        }
}
