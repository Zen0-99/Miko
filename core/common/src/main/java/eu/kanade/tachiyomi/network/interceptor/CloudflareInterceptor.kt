package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.CountDownLatch

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val executor = ContextCompat.getMainExecutor(context)

    override fun shouldIntercept(response: Response): Boolean {
        val server = response.header("Server")
        val isCloudflareServer = server in SERVER_CHECK

        // Case 1: Classic CF challenge — error status + cloudflare server header
        if (response.code in ERROR_CODES && isCloudflareServer) {
            return true
        }

        // Case 2: cf-mitigated header — modern CF indicator (challenge/block/managed-challenge)
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
        try {
            response.close()
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            val oldCookie = cookieManager.get(request.url)
                .firstOrNull { it.name == "cf_clearance" }
            resolveWithWebView(request, oldCookie)

            return chain.proceed(request)
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareBypassException) {
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?) {
        // We need to lock this thread until the WebView finds the challenge solution url, because
        // OkHttp doesn't support asynchronous interceptors.
        val latch = CountDownLatch(1)

        var webview: WebView? = null

        var challengeFound = false
        var cloudflareBypassed = false
        var isWebViewOutdated = false

        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)

        executor.execute {
            webview = createWebView(originalRequest)

            webview?.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    fun isCloudFlareBypassed(): Boolean {
                        return cookieManager.get(origRequestUrl.toHttpUrl())
                            .firstOrNull { it.name == "cf_clearance" }
                            .let { it != null && it != oldCookie }
                    }

                    if (isCloudFlareBypassed()) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }

                    if (url == origRequestUrl && !challengeFound) {
                        // The first request didn't return the challenge, abort.
                        latch.countDown()
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        if (error.errorCode in ERROR_CODES) {
                            // Found the Cloudflare challenge page.
                            challengeFound = true
                        } else {
                            // Unlock thread, the challenge wasn't found.
                            latch.countDown()
                        }
                    }
                }
            }

            webview?.loadUrl(origRequestUrl, headers)
        }

        latch.awaitFor30Seconds()

        executor.execute {
            if (!cloudflareBypassed) {
                isWebViewOutdated = webview?.isOutdated() == true
            }

            webview?.run {
                stopLoading()
                destroy()
            }
        }

        // Throw exception if we failed to bypass Cloudflare
        if (!cloudflareBypassed) {
            // Prompt user to update WebView if it seems too outdated
            if (isWebViewOutdated) {
                context.toast(MR.strings.information_webview_outdated, Toast.LENGTH_LONG)
            }

            throw CloudflareBypassException()
        }
    }
}

private val ERROR_CODES = listOf(403, 503, 429)
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
)

// Max bytes to peek for challenge detection (challenge pages are small)
private const val CHALLENGE_PEEK_BYTES = 64_000L

private class CloudflareBypassException : Exception()
