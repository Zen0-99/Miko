package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds browser-like headers to requests that don't already have them.
 *
 * Cloudflare and similar anti-bot systems often challenge requests that lack
 * standard browser headers. Adding these headers proactively reduces the chance
 * of a challenge being issued, complementing the [CloudflareInterceptor]'s
 * reactive bypass.
 *
 * Only adds headers that are not already present on the request — extensions
 * that set their own Accept/Accept-Language/etc. headers are not overridden.
 */
class BrowserHeadersInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        // Only add headers for HTTP(S) requests
        val scheme = originalRequest.url.scheme
        if (scheme != "http" && scheme != "https") {
            return chain.proceed(originalRequest)
        }

        addIfMissing(requestBuilder, "Accept", DEFAULT_ACCEPT)
        addIfMissing(requestBuilder, "Accept-Language", DEFAULT_ACCEPT_LANGUAGE)
        addIfMissing(requestBuilder, "Accept-Encoding", DEFAULT_ACCEPT_ENCODING)
        addIfMissing(requestBuilder, "Sec-Fetch-Dest", "document")
        addIfMissing(requestBuilder, "Sec-Fetch-Mode", "navigate")
        addIfMissing(requestBuilder, "Sec-Fetch-Site", "none")
        addIfMissing(requestBuilder, "Sec-Fetch-User", "?1")
        addIfMissing(requestBuilder, "Upgrade-Insecure-Requests", "1")

        return chain.proceed(requestBuilder.build())
    }

    private fun addIfMissing(builder: okhttp3.Request.Builder, name: String, value: String) {
        if (builder.build().header(name) == null) {
            builder.addHeader(name, value)
        }
    }

    companion object {
        private const val DEFAULT_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
        private const val DEFAULT_ACCEPT_LANGUAGE = "en-US,en;q=0.9"
        private const val DEFAULT_ACCEPT_ENCODING = "gzip, deflate, br"
    }
}
