package miko.jssource

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun platformResolveUrl(input: String, base: String?): String {
    val inputValue = input.trim()
    val baseValue = base?.trim().orEmpty()
    return runCatching {
        val inputUrl = NSURL.URLWithString(inputValue)
        // Absolute input wins outright (mirrors URI.isAbsolute behavior).
        if (inputUrl != null && inputUrl.scheme != null) return inputUrl.absoluteString ?: inputValue
        if (baseValue.isBlank()) return inputValue
        val baseUrl = NSURL.URLWithString(baseValue)
            ?: return inputValue
        val resolved = NSURL.URLWithString(inputValue, relativeToURL = baseUrl)
        resolved?.absoluteString ?: inputValue
    }.getOrElse { inputValue }
}

@OptIn(ExperimentalForeignApi::class)
actual fun platformGetPathname(url: String): String {
    val value = url.trim()
    return runCatching { NSURL.URLWithString(value)?.path ?: "" }.getOrDefault("")
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun platformBase64Encode(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    return data.base64EncodedStringWithOptions(0u)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun platformBase64Decode(b64: String): ByteArray {
    val data = NSData.create(base64EncodedString = b64, options = 0u) ?: return ByteArray(0)
    val size = data.length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    memScoped {
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
    }
    return out
}
