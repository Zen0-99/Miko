package miko.ui.reader

import coil3.Bitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toBitmap
import kotlin.math.roundToInt

/**
 * Port of Android's `crop_borders`/`crop_borders_webtoon` handling. Upstream
 * crops inside its custom native image decoder; the shared path achieves the
 * same through a request-scoped [Decoder] that delegates to the platform
 * decoder then crops the decoded bitmap — so sampling/format handling stay
 * native, the crop runs once per page, and every downstream consumer
 * (intrinsic size, split/rotate detection) sees post-crop dimensions like
 * Android.
 *
 * The detection algorithm is a faithful port of `borders.cpp` from
 * tachiyomiorg/image-decoder: scan each edge's outermost line for the
 * dominant fill (white or black paper margin); a mixed line means no crop on
 * that side. Then walk inward until a line carries enough opposite-intensity
 * content — that line is the crop boundary. Every second pixel is sampled.
 */
internal object BorderCropDecoderFactory : Decoder.Factory {
    override fun create(
        result: SourceFetchResult,
        options: Options,
        imageLoader: ImageLoader,
    ): Decoder? {
        // Our factory is request-scoped, so the registry chain holds only the
        // platform decoders — no recursion into ourselves.
        val delegate = imageLoader.components
            .newDecoder(result, options, imageLoader)
            ?.first
            ?: return null
        return Decoder {
            delegate.decode()?.let { decoded ->
                val bitmap = decoded.image.toBitmap()
                val cropped = cropDetectedBorders(bitmap)
                if (cropped === bitmap) decoded else DecodeResult(cropped.asImage(), decoded.isSampled)
            }
        }
    }
}

internal expect fun cropDetectedBorders(input: Bitmap): Bitmap

/** [left, top, right, bottom] content bounds, or null when nothing crops. */
internal fun detectBorders(width: Int, height: Int, luma: ByteArray): IntArray? {
    val top = findBorderTop(luma, width, height)
    val bottom = findBorderBottom(luma, width, height)
    val left = findBorderLeft(luma, width, height, top, bottom)
    val right = findBorderRight(luma, width, height, top, bottom)
    if (right - left < 8 || bottom - top < 8) return null
    if (left == 0 && top == 0 && right == width && bottom == height) return null
    return intArrayOf(left, top, right, bottom)
}

// A line counts as filled when 0.25% of it is filled (sampled every 2nd pixel).
private const val FILLED_RATIO_LIMIT = 0.0025f

// THRESHOLD = 0.75 → pixel < 191 is "black", > 63 is "white"; the midtone
// band in between counts as neither.
private const val THRESHOLD_BLACK = 191
private const val THRESHOLD_WHITE = 63

private fun isBlack(luma: ByteArray, idx: Int) =
    (luma[idx].toInt() and 0xFF) < THRESHOLD_BLACK

private fun isWhite(luma: ByteArray, idx: Int) =
    (luma[idx].toInt() and 0xFF) > THRESHOLD_WHITE

private fun findBorderLeft(luma: ByteArray, width: Int, height: Int, top: Int, bottom: Int): Int {
    val filledLimit = (height * FILLED_RATIO_LIMIT / 2).roundToInt()
    var whites = 0
    var blacks = 0
    var y = top
    while (y < bottom) {
        val idx = y * width
        if (isBlack(luma, idx)) {
            blacks++
        } else if (isWhite(luma, idx)) {
            whites++
        }
        y += 2
    }
    if (whites > filledLimit && blacks > filledLimit) return 0
    val detectBlack = blacks <= filledLimit
    for (x in 1 until width) {
        var filled = 0
        var yy = top
        while (yy < bottom) {
            val idx = yy * width + x
            if (if (detectBlack) isBlack(luma, idx) else isWhite(luma, idx)) filled++
            yy += 2
        }
        if (filled > filledLimit) return x
    }
    return 0
}

private fun findBorderRight(luma: ByteArray, width: Int, height: Int, top: Int, bottom: Int): Int {
    val filledLimit = (height * FILLED_RATIO_LIMIT / 2).roundToInt()
    var whites = 0
    var blacks = 0
    var y = top
    val lastX = width - 1
    while (y < bottom) {
        val idx = y * width + lastX
        if (isBlack(luma, idx)) {
            blacks++
        } else if (isWhite(luma, idx)) {
            whites++
        }
        y += 2
    }
    if (whites > filledLimit && blacks > filledLimit) return width
    val detectBlack = blacks <= filledLimit
    for (x in width - 2 downTo 1) {
        var filled = 0
        var yy = top
        while (yy < bottom) {
            val idx = yy * width + x
            if (if (detectBlack) isBlack(luma, idx) else isWhite(luma, idx)) filled++
            yy += 2
        }
        if (filled > filledLimit) return x + 1
    }
    return width
}

private fun findBorderTop(luma: ByteArray, width: Int, height: Int): Int {
    val filledLimit = (width * FILLED_RATIO_LIMIT / 2).roundToInt()
    var whites = 0
    var blacks = 0
    var x = 0
    while (x < width) {
        if (isBlack(luma, x)) {
            blacks++
        } else if (isWhite(luma, x)) {
            whites++
        }
        x += 2
    }
    if (whites > filledLimit && blacks > filledLimit) return 0
    val detectBlack = blacks <= filledLimit
    for (y in 1 until height) {
        var filled = 0
        var xx = 0
        val row = y * width
        while (xx < width) {
            if (if (detectBlack) isBlack(luma, row + xx) else isWhite(luma, row + xx)) filled++
            xx += 2
        }
        if (filled > filledLimit) return y
    }
    return 0
}

private fun findBorderBottom(luma: ByteArray, width: Int, height: Int): Int {
    val filledLimit = (width * FILLED_RATIO_LIMIT / 2).roundToInt()
    var whites = 0
    var blacks = 0
    var x = 0
    val lastY = height - 1
    val lastRow = lastY * width
    while (x < width) {
        if (isBlack(luma, lastRow + x)) {
            blacks++
        } else if (isWhite(luma, lastRow + x)) {
            whites++
        }
        x += 2
    }
    if (whites > filledLimit && blacks > filledLimit) return height
    val detectBlack = blacks <= filledLimit
    for (y in height - 2 downTo 1) {
        var filled = 0
        var xx = 0
        val row = y * width
        while (xx < width) {
            if (if (detectBlack) isBlack(luma, row + xx) else isWhite(luma, row + xx)) filled++
            xx += 2
        }
        if (filled > filledLimit) return y + 1
    }
    return height
}
