package eu.kanade.tachiyomi.source.novel

import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Shared politeness funnel for the public, unauthenticated catalogues
 * (LibriVox, Internet Archive — 22-03, SD-6).
 *
 * Neither host publishes a numeric rate cap, so the conservative floor
 * applies: ≥ [SPACING_MS] between request starts, max one request in
 * flight globally (one mutex serializes every call — strictly more
 * polite than the per-source floor), a descriptive `Miko/{version}
 * (audiobook-source)` User-Agent, and HTTP 429/503 handling that honors
 * `Retry-After` before falling back to exponential backoff, ≤3 retries.
 *
 * Tests inject [nowMs]/[delayMs] to record spacing without real sleeps,
 * and drive [fetch] with fakes — production requests go through [get].
 *
 * NO auth headers are ever attached here — public hosts take no
 * credentials and the ABS Bearer path is host-scoped elsewhere
 * (T-22-03-03).
 */
object PoliteFetch {

    /** Clock seam — tests swap in a recording fake. */
    internal var nowMs: () -> Long = { System.currentTimeMillis() }

    /** Delay seam — tests record waits instead of sleeping. */
    internal var delayMs: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }

    /** HTTP seam — tests stub [get] without touching OkHttp/Injekt. */
    internal var open: suspend (url: String) -> String = { url -> httpGet(url) }

    private val mutex = Mutex()
    private var lastStartMs = 0L

    private val httpClient by lazy {
        Injekt.get<NetworkHelper>().nonCloudflareClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Descriptive UA — read lazily so BuildConfig never loads early. */
    val userAgent: String by lazy {
        val version = runCatching { AppInfo.getVersionName() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "dev"
        "Miko/$version (audiobook-source)"
    }

    /**
     * Polite GET of [url] — throttled, UA-tagged, retried on 429/503.
     * This is the entry point the built-in public sources use.
     */
    suspend fun get(url: String): String = fetch { open(url) }

    /**
     * ≥1000 ms between request starts, serialized by [mutex]. Records the
     * start AFTER waiting so back-to-back callers still space out.
     */
    suspend fun throttle() {
        mutex.withLock {
            val wait = SPACING_MS - (nowMs() - lastStartMs)
            if (wait > 0) delayMs(wait)
            lastStartMs = nowMs()
        }
    }

    /**
     * [block] behind [throttle] with 429/503 retry: `Retry-After` wins
     * when present, else exponential backoff (1s → 2s → 4s, capped).
     * After [MAX_RETRIES] the last error propagates. Non-throttle HTTP
     * errors and cancellation are never swallowed.
     */
    suspend fun <T> fetch(block: suspend () -> T): T {
        var retries = 0
        var backoffMs = INITIAL_BACKOFF_MS
        while (true) {
            throttle()
            try {
                return block()
            } catch (e: RateLimitedException) {
                if (retries >= MAX_RETRIES) throw e
                retries++
                delayMs(e.retryAfterMs ?: backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            } catch (e: HttpException) {
                if ((e.code != HTTP_TOO_MANY_REQUESTS && e.code != HTTP_UNAVAILABLE) ||
                    retries >= MAX_RETRIES
                ) {
                    throw e
                }
                retries++
                delayMs(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    /** Thrown by transports when a host answers 429/503 — carries the
     *  parsed `Retry-After` (ms) when the header was sent. */
    class RateLimitedException(
        val code: Int,
        val retryAfterMs: Long?,
    ) : IllegalStateException("HTTP $code (rate-limited)")

    // ---- production transport ------------------------------------------------

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .get()
            .build()
        httpClient.newCall(request).await().use { response ->
            if (response.isSuccessful) {
                response.body.string()
            } else {
                val code = response.code
                val retryAfterMs = response.header("Retry-After")
                    ?.let { parseRetryAfterMs(it, nowMs()) }
                throw if (code == HTTP_TOO_MANY_REQUESTS || code == HTTP_UNAVAILABLE) {
                    RateLimitedException(code, retryAfterMs)
                } else {
                    HttpException(code)
                }
            }
        }
    }

    /** `Retry-After`: delta-seconds, or an HTTP-date → ms from [now]. */
    internal fun parseRetryAfterMs(header: String, now: Long): Long? {
        val trimmed = header.trim()
        trimmed.toLongOrNull()?.let { return (it * 1000L).coerceAtLeast(0L) }
        return runCatching {
            ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli() - now
        }.getOrNull()?.takeIf { it > 0 }
    }

    /** Test hook — restore real clock/delay and clear spacing state. */
    internal fun resetForTest() {
        nowMs = { System.currentTimeMillis() }
        delayMs = { kotlinx.coroutines.delay(it) }
        open = { url -> httpGet(url) }
        kotlinx.coroutines.runBlocking {
            mutex.withLock { lastStartMs = 0L }
        }
    }

    const val SPACING_MS = 1000L
    const val MAX_RETRIES = 3
    private const val INITIAL_BACKOFF_MS = 1000L
    private const val MAX_BACKOFF_MS = 8000L
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_UNAVAILABLE = 503
}
