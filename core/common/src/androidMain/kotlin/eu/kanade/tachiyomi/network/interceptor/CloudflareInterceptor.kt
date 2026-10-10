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

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
    private val challengeResolver: CloudflareChallengeResolver? = null,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val challengeLockByHost = ConcurrentHashMap<String, Any>()
    private val webViewChallengeResolver = challengeResolver ?: WebViewCloudflareChallengeResolver(
        context = context,
        cookieManager = cookieManager,
        mainExecutor = ContextCompat.getMainExecutor(context),
        createWebView = this::createWebView,
        parseHeaders = this::parseHeaders,
        isWebViewOutdated = { it.isOutdated() },
    )

    override fun shouldIntercept(response: Response): Boolean {
        val server = response.header("Server")
        val isCloudflareServer = server in SERVER_CHECK

        // Case 1: Classic CF challenge — error status + cloudflare server header
        if (response.code in ERROR_CODES && isCloudflareServer) {
            // cf-mitigated header — modern CF indicator (challenge/block/managed-challenge)
            val cfMitigated = response.header("cf-mitigated")
            if (cfMitigated != null && cfMitigated.lowercase() in CF_MITIGATED_VALUES) {
                return true
            }
            // Check body for challenge markers (tightened: only when server is CF)
            return isChallengePage(response)
        }

        // Case 2: cf-mitigated header on any status — modern CF indicator
        val cfMitigated = response.header("cf-mitigated")
        if (cfMitigated != null && cfMitigated.lowercase() in CF_MITIGATED_VALUES) {
            return true
        }

        // Case 3: 200 OK with CF challenge page (Turnstile / managed challenge / "Just a moment")
        // Only peek body for 200 responses from CF server to avoid performance impact
        if (response.code == 200 && isCloudflareServer) {
            return isChallengePage(response)
        }

        return false
    }

    /**
     * Peek at the response body (without consuming it) to detect CF challenge pages.
     * Modern CF challenges (Turnstile, managed challenges) can return 200 with an
     * interstitial page containing challenge markers.
     */
    private fun isChallengePage(response: Response): Boolean {
        return try {
            // peekBody returns a buffered copy; original body remains readable
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
                        .firstOrNull { it.name == "cf_clearance" }

                    // Only pay for an immediate network retry when there is a clearance to try.
                    // With no cookie this was just an extra blocked request before WebView.
                    if (oldCookie != null) {
                        val immediateRetry = chain.proceed(request)
                        if (!shouldIntercept(immediateRetry)) {
                            return immediateRetry
                        }
                        immediateRetry.close()
                        cookieManager.remove(request.url, COOKIE_NAMES, 0)
                    }

                    webViewChallengeResolver.resolve(request, oldCookie)

                    val firstAttempt = chain.proceed(request)
                    if (!shouldIntercept(firstAttempt)) {
                        return firstAttempt
                    }
                    // The cookie set on CookieManager may not have propagated to OkHttp's
                    // CookieJar yet for the in-flight connection; close and retry once.
                    firstAttempt.close()
                    return chain.proceed(request)
                }
            } finally {
                challengeLockByHost.remove(host, hostLock)
            }
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareInteractiveChallengeException) {
            throw IOException(
                context.stringResource(MR.strings.information_cloudflare_interactive_challenge),
                e,
            )
        } catch (e: CloudflareBypassException) {
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }
}

internal val ERROR_CODES = listOf(403, 503, 429)
private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")

// cf-mitigated header values that indicate a challenge/block
private val CF_MITIGATED_VALUES = listOf("challenge", "block", "managed-challenge")

// Body markers for CF challenge pages (Turnstile, "Just a moment", managed challenges)
private val CHALLENGE_MARKERS = listOf(
    "__cf_chl__",
    "cf_chl_managed",
    "challenge-platform",
    "Just a moment...",
    "cf-turnstile",
    "cdn-cgi/challenge-platform",
    "challenge-error-title",
    "challenge-error-text",
)

// Max bytes to peek for challenge detection (challenge pages are small; 8KB is enough)
private const val CHALLENGE_PEEK_BYTES = 8L * 1024L
