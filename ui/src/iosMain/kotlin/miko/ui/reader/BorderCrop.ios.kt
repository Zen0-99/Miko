package miko.ui.reader

import coil3.Bitmap
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageInfo

/**
 * iOS border crop: `coil3.Bitmap` is `org.jetbrains.skia.Bitmap` on this
 * platform. `readPixels` converts the native layout to straight RGBA for the
 * shared luma pass (falling back to interpreting the native layout when
 * conversion isn't possible), then `extractSubset` returns a bitmap sharing
 * the source pixel buffer — zero-copy like Android's `createBitmap` subset.
 */
internal actual fun cropDetectedBorders(input: Bitmap): Bitmap {
    val w = input.width
    val h = input.height
    if (w <= 1 || h <= 1) return input
    val info = input.imageInfo

    val rgba = ImageInfo(
        ColorInfo(ColorType.RGBA_8888, info.colorAlphaType, null),
        w,
        h,
    )
    val converted = input.readPixels(dstInfo = rgba, dstRowBytes = w * 4)
    val nativeType: ColorType
    val bytes = if (converted != null) {
        nativeType = ColorType.RGBA_8888
        converted
    } else {
        nativeType = info.colorType
        val bpp = when (nativeType) {
            ColorType.RGBA_8888, ColorType.BGRA_8888 -> 4
            ColorType.RGB_565 -> 2
            ColorType.GRAY_8 -> 1
            else -> return input
        }
        input.readPixels(dstInfo = info, dstRowBytes = w * bpp) ?: return input
    }

    val luma = when (nativeType) {
        ColorType.RGBA_8888, ColorType.BGRA_8888 -> luma8888(bytes, w, h, nativeType == ColorType.BGRA_8888)
        ColorType.RGB_565 -> luma565(bytes, w, h)
        ColorType.GRAY_8 -> bytes
        else -> return input
    }
    val r = detectBorders(w, h, luma) ?: return input
    val dst = org.jetbrains.skia.Bitmap()
    return if (input.extractSubset(dst, IRect(r[0], r[1], r[2], r[3]))) dst else input
}

private fun luma8888(bytes: ByteArray, w: Int, h: Int, bgra: Boolean): ByteArray {
    val luma = ByteArray(w * h)
    var src = 0
    for (i in luma.indices) {
        val r: Int
        val g: Int
        val b: Int
        if (bgra) {
            b = bytes[src].toInt() and 0xFF
            g = bytes[src + 1].toInt() and 0xFF
            r = bytes[src + 2].toInt() and 0xFF
        } else {
            r = bytes[src].toInt() and 0xFF
            g = bytes[src + 1].toInt() and 0xFF
            b = bytes[src + 2].toInt() and 0xFF
        }
        luma[i] = ((299 * r + 587 * g + 114 * b) / 1000).toByte()
        src += 4
    }
    return luma
}

private fun luma565(bytes: ByteArray, w: Int, h: Int): ByteArray {
    val luma = ByteArray(w * h)
    var src = 0
    for (i in luma.indices) {
        val p = (bytes[src + 1].toInt() and 0xFF shl 8) or (bytes[src].toInt() and 0xFF)
        val r = (p shr 11 and 0x1F) shl 3
        val g = (p shr 5 and 0x3F) shl 2
        val b = (p and 0x1F) shl 3
        luma[i] = ((299 * r + 587 * g + 114 * b) / 1000).toByte()
        src += 2
    }
    return luma
}
