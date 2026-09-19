package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Interceptor for DDoS-Guard protection (used by Anna's Archive and other sites).
 *
 * DDoS-Guard serves a JavaScript challenge page (HTTP 403 with `Server: ddos-guard`)
 * that sets `__ddg*` cookies once solved in a WebView. This interceptor detects
 * such responses, launches a WebView to solve the challenge, and retries the
 * request with the obtained cookies.
 *
 * Structurally parallel to [CloudflareInterceptor] but simpler — DDoS-Guard
 * does not use interactive widgets (Turnstile), so no interactive-challenge
 * detection is needed.
 */
class DDoSGuardInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
    private val challengeResolver: DDoSGuardChallengeResolver? = null,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val challengeLockByHost = ConcurrentHashMap<String, Any>()
    private val webViewChallengeResolver = challengeResolver ?: WebViewDDoSGuardChallengeResolver(
        context = context,
        cookieManager = cookieManager,
        mainExecutor = ContextCompat.getMainExecutor(context),
        createWebView = this::createWebView,
        parseHeaders = this::parseHeaders,
        isWebViewOutdated = { it.isOutdated() },
    )

    override fun shouldIntercept(response: Response): Boolean {
        val server = response.header("Server")
        if (server == null || !server.equals(SERVER_CHECK, ignoreCase = true)) {
            return false
        }

        // DDoS-Guard challenge: 403 (or 503/429) with ddos-guard server header.
        // Some challenge pages return 200 with a JS redirect, so also peek at
        // the body for the DDoS-Guard challenge marker.
        if (response.code in DDG_ERROR_CODES) {
            return true
        }

        if (response.code == 200) {
            return isChallengePage(response)
        }

        return false
    }

    private fun isChallengePage(response: Response): Boolean {
        return try {
            val body = response.peekBody(CHALLENGE_PEEK_BYTES).string()
            CHALLENGE_MARKERS.any { marker -> body.contains(marker, ignoreCase = true) }
        } catch (_: Exception) {
            false
        }
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        val host = request.url.host
        try {
            response.close()
            val hostLock = challengeLockByHost.getOrPut(host) { Any() }
            try {
                synchronized(hostLock) {
                    val oldCookie = cookieManager.get(request.url)
                        .firstOrNull { it.name == DDG_COOKIE_NAME }

                    // If we already have a clearance cookie, try a quick retry first.
                    if (oldCookie != null) {
                        val immediateRetry = chain.proceed(request)
                        if (!shouldIntercept(immediateRetry)) {
                            return immediateRetry
                        }
                        immediateRetry.close()
                        cookieManager.remove(request.url, DDG_COOKIE_NAMES, 0)
                    }

                    webViewChallengeResolver.resolve(request, oldCookie)

                    val firstAttempt = chain.proceed(request)
                    if (!shouldIntercept(firstAttempt)) {
                        return firstAttempt
                    }
                    firstAttempt.close()
                    return chain.proceed(request)
                }
            } finally {
                challengeLockByHost.remove(host, hostLock)
            }
        } catch (e: DDoSGuardBypassException) {
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }
}

private const val SERVER_CHECK = "ddos-guard"
internal val DDG_ERROR_CODES = listOf(403, 503, 429)
private val DDG_COOKIE_NAMES = listOf("__ddgid_", "__ddg8_", "__ddg9_", "__ddg10_", "__ddg5_", "__ddgmark_", "__ddg1_", "__ddg2_", "__ddg3_", "__ddg4_", "__ddg6_", "__ddg7_")
internal const val DDG_COOKIE_NAME = "__ddg1_"

// Body markers for DDoS-Guard challenge pages
private val CHALLENGE_MARKERS = listOf(
    "DDoS-Guard",
    "ddos-guard",
    "__ddg",
    "Checking your browser",
)

// Max bytes to peek for challenge detection
private const val CHALLENGE_PEEK_BYTES = 8L * 1024L
