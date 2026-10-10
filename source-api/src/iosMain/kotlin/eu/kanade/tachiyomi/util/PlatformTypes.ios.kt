package eu.kanade.tachiyomi.util

/**
 * iOS stand-in for `android.net.Uri` — opaque marker in the source
 * contract; nothing constructs it on iOS.
 */
actual abstract class Uri private actual constructor() {
    actual abstract override fun toString(): String
}

/**
 * iOS stand-in for `okhttp3.Headers` — a flat ordered pair list.
 */
actual class Headers internal constructor(
    private val entries: List<Pair<String, String>>,
) {
    actual val size: Int
        get() = entries.size

    actual fun name(index: Int): String = entries[index].first

    actual fun value(index: Int): String = entries[index].second

    actual companion object {
        actual fun headersOf(vararg namesAndValues: String): Headers =
            Headers(namesAndValues.toList().chunked(2).map { it[0] to it[1] })
    }
}

actual interface ProgressListener {
    actual fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}
