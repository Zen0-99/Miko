package miko.jssource

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

@OptIn(ExperimentalEncodingApi::class)
actual fun platformBase64Encode(bytes: ByteArray): String =
    if (bytes.isEmpty()) "" else Base64.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
actual fun platformBase64Decode(b64: String): ByteArray =
    runCatching { Base64.decode(b64) }.getOrElse { ByteArray(0) }
