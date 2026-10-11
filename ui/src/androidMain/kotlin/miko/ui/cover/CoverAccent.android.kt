package miko.ui.cover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.min
import kotlin.math.roundToInt

/** Decode to ≤128px ARGB — the shared accent math runs on the pixels. */
internal actual fun decodeCoverPixels(data: ByteArray): DecodedPixels? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    val w0 = bounds.outWidth
    val h0 = bounds.outHeight
    if (w0 <= 0 || h0 <= 0) return null
    // FIT to 128 max edge — keeps the full poster like Android's extractor.
    val sample = maxOf(1, min(w0, h0) / 64)
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val raw = runCatching {
        BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    }.getOrNull() ?: return null
    val scale = min(1f, 128f / maxOf(raw.width, raw.height))
    val bmp = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            raw,
            maxOf(1, (raw.width * scale).roundToInt()),
            maxOf(1, (raw.height * scale).roundToInt()),
            true,
        )
    } else {
        raw
    }
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    return DecodedPixels(bmp.width, bmp.height, pixels)
}
