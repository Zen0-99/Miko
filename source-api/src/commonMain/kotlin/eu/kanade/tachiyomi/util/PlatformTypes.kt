package eu.kanade.tachiyomi.util

/**
 * Platform type shims for the source contract. On Android these
 * typealias to the real framework types — `android.net.Uri` and
 * `okhttp3.Headers` — so the compiled extension ABI is byte-identical
 * to today. On iOS they are lightweight KMP stand-ins.
 */
expect abstract class Uri private constructor() {
    // android.net.Uri declares toString() abstract — redeclare it so the
    // actual class's modality matches.
    abstract override fun toString(): String
}

expect class Headers {
    val size: Int
    fun name(index: Int): String
    fun value(index: Int): String

    companion object {
        fun headersOf(vararg namesAndValues: String): Headers
    }
}

/**
 * Download progress callback. `eu.kanade.tachiyomi.network.ProgressListener`
 * (core:common) on Android — kept on [eu.kanade.tachiyomi.source.model.Page]
 * because HttpSource passes pages to `newCachelessCallWithProgress`.
 */
expect interface ProgressListener {
    fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}

/**
 * Portable stand-in for `androidx.compose.runtime.Stable`. The androidx
 * runtime publishes no darwin artifacts at this project's BOM, so a
 * real typealias can't be shared in commonMain. The hint only feeds
 * compose-compiler stability inference — behavior is unaffected.
 */
annotation class Stable
