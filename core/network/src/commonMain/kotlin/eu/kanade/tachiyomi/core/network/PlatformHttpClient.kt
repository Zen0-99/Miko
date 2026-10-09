package eu.kanade.tachiyomi.core.network

import io.ktor.client.HttpClient

/**
 * Shared HTTP client contract. Each platform supplies its engine:
 * OkHttp on Android (keeps the existing OkHttp stack — interceptors,
 * DoH, brotli get wired here when `app` adopts this contract) and
 * NSURLSession via Ktor Darwin on iOS.
 */
expect fun platformHttpClient(): HttpClient
