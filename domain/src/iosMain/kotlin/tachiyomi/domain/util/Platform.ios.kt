package tachiyomi.domain.util

import platform.Foundation.NSURL

actual interface PlatformSerializable

actual annotation class Immutable

internal actual fun String.toHttpUrlOrNull(): String? {
    val url = NSURL.URLWithString(this.trim()) ?: return null
    val scheme = url.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    return url.absoluteURL?.absoluteString
}
