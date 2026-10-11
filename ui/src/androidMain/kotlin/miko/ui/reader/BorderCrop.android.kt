package miko.ui.reader

import android.graphics.Bitmap
import coil3.Bitmap as CoilBitmap

/**
 * Android border crop: read ARGB once, run the shared luma detection, then
 * `Bitmap.createBitmap` a subset — it shares the source pixel buffer so the
 * crop itself is zero-copy like the native decoder's.
 */
internal actual fun cropDetectedBorders(input: CoilBitmap): CoilBitmap {
    val w = input.width
    val h = input.height
    if (w <= 1 || h <= 1) return input
    val argb = IntArray(w * h)
    input.getPixels(argb, 0, w, 0, 0, w, h)
    val luma = ByteArray(w * h)
    for (i in argb.indices) {
        val p = argb[i]
        luma[i] = (
            299 * ((p shr 16) and 0xFF) +
                587 * ((p shr 8) and 0xFF) +
                114 * (p and 0xFF)
            ).div(1000).toByte()
    }
    val r = detectBorders(w, h, luma) ?: return input
    return runCatching {
        Bitmap.createBitmap(input, r[0], r[1], r[2] - r[0], r[3] - r[1])
    }.getOrDefault(input)
}
