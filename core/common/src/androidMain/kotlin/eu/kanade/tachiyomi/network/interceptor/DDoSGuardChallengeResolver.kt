package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import tachiyomi.i18n.MR
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

private fun CountDownLatch.awaitFor30Seconds() {
    await(30, TimeUnit.SECONDS)
}

interface DDoSGuardChallengeResolver {
    fun resolve(originalRequest: Request, oldCookie: Cookie?)
}

/**
 * WebView-based resolver for DDoS-Guard JS challenges.
 *
 * Loads the challenged URL in a WebView, lets the DDoS-Guard JavaScript execute
 * (which sets `__ddg*` cookies via Set-Cookie headers), then flushes those
 * cookies to the OkHttp [cookieManager] so subsequent OkHttp requests pass.
 *
 * Simpler than [WebViewCloudflareChallengeResolver] — DDoS-Guard does not use
 * interactive widgets (Turnstile), so there is no interactive-challenge path.
 */
internal class WebViewDDoSGuardChallengeResolver(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    private val mainExecutor: Executor,
    private val createWebView: (Request) -> WebView,
    private val parseHeaders: (Headers) -> Map<String, String>,
    private val isWebViewOutdated: (WebView) -> Boolean,
) : DDoSGuardChallengeResolver {

    @SuppressLint("SetJavaScriptEnabled")
    override fun resolve(originalRequest: Request, oldCookie: Cookie?) {
        val latch = CountDownLatch(1)

        var webview: WebView? = null
        var challengeBypassed = false
        var isWebViewOutdatedNow = false

        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)

        mainExecutor.execute {
            val createdWebView = createWebView(originalRequest)
            webview = createdWebView

            createdWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (hasNewDDoSGuardClearance(originalRequest, url, oldCookie)) {
                        challengeBypassed = true
                        CookieManager.getInstance().flush()
                        latch.countDown()
                        return
                    }

                    // If we landed on the actual page (not a challenge), we're done.
                    // DDoS-Guard may redirect to the original URL after solving.
                    if (url == origRequestUrl || url.toHttpUrlOrNull()?.host != originalRequest.url.host) {
                        // Give the page a brief moment — DDoS-Guard may still be
                        // setting cookies via JS after onPageFinished.
                        view.postDelayed({
                            if (hasNewDDoSGuardClearance(originalRequest, url, oldCookie)) {
                                challengeBypassed = true
                                CookieManager.getInstance().flush()
                            }
                            latch.countDown()
                        }, 2000)
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        latch.countDown()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    // DDoS-Guard initially returns 403 — that's expected.
                    // The WebView will execute the JS challenge and reload.
                    // Don't count down here; wait for onPageFinished.
                }
            }

            createdWebView.loadUrl(origRequestUrl, headers)
        }

        latch.awaitFor30Seconds()

        mainExecutor.execute {
            if (!challengeBypassed) {
                isWebViewOutdatedNow = webview?.let(isWebViewOutdated) == true
            }
            webview?.run {
                stopLoading()
                destroy()
            }
        }

        if (!challengeBypassed) {
            if (isWebViewOutdatedNow) {
                context.toast(MR.strings.information_webview_outdated, Toast.LENGTH_LONG)
            }
            throw DDoSGuardBypassException()
        }
    }

    /**
     * Check whether the cookie jar now has a fresh `__ddgid_` cookie for the
     * request's domain (different from [oldCookie], or present when there was none).
     */
    private fun hasNewDDoSGuardClearance(originalRequest: Request, currentUrl: String, oldCookie: Cookie?): Boolean {
        return listOfNotNull(originalRequest.url, currentUrl.toHttpUrlOrNull())
            .distinctBy { it.host }
            .any { url ->
                val cookie = cookieManager.get(url).firstOrNull { it.name == DDG_COOKIE_NAME }
                cookie != null && (url.host != originalRequest.url.host || cookie != oldCookie)
            }
    }
}

internal open class DDoSGuardBypassException : Exception()
