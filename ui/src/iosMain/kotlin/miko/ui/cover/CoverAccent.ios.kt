package miko.ui.cover

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.kCIFormatRGBA8
import platform.Foundation.NSData
import platform.Foundation.create
import kotlin.math.min

/**
 * Decode to ≤128px RGBA via CoreImage — `CIContext.render(toBitmap:)`
 * hands back straight byte pixels without the CGBitmapContext
 * bitmapInfo enum juggling.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun decodeCoverPixels(data: ByteArray): DecodedPixels? = memScoped {
    val nsData = data.usePinned {
        NSData.create(bytes = it.addressOf(0), length = data.size.toULong())
    }
    val base = CIImage.imageWithData(nsData) ?: return null
    val extent0 = base.extent()
    val w0 = extent0.useContents { size.width }
    val h0 = extent0.useContents { size.height }
    if (w0 <= 0.0 || h0 <= 0.0) return null

    val s = min(1.0, 128.0 / maxOf(w0, h0))
    val originX = extent0.useContents { origin.x }
    val originY = extent0.useContents { origin.y }
    val scaled = base
        .imageByApplyingTransform(CGAffineTransformMakeScale(s, s))
        .imageByApplyingTransform(
            CGAffineTransformMakeTranslation(-originX * s, -originY * s),
        )
    val extent = scaled.extent()
    val w = extent.useContents { size.width.toInt() }.coerceAtLeast(1)
    val h = extent.useContents { size.height.toInt() }.coerceAtLeast(1)

    val ctx = CIContext.contextWithOptions(null)
    val buf = allocArray<kotlinx.cinterop.ByteVar>(w * h * 4)
    ctx.render(
        scaled,
        toBitmap = buf,
        rowBytes = (w * 4).toLong(),
        bounds = CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()),
        format = kCIFormatRGBA8,
        colorSpace = null,
    )
    val pixels = IntArray(w * h)
    for (i in 0 until w * h) {
        val o = i * 4
        val r = buf[o].toInt() and 0xFF
        val g = buf[o + 1].toInt() and 0xFF
        val b = buf[o + 2].toInt() and 0xFF
        val a = buf[o + 3].toInt() and 0xFF
        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    DecodedPixels(w, h, pixels)
}
