package eu.kanade.tachiyomi.extension.novel.util

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Creates a WebView-based resolver for novel extensions that need WebView interaction
 * (countdown timers, non-Cloudflare CAPTCHAs, redirect-based pages).
 *
 * Returns a dynamic proxy implementing the extension library's [WebViewResolver] interface,
 * which is loaded in the extension's classloader and not available on the app's compile classpath.
 *
 * For Cloudflare challenges specifically, the [CloudflareInterceptor] handles bypass
 * automatically — this resolver is for other WebView scenarios.
 */
class NovelWebViewResolver(
    private val context: Context,
) {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveUrl(initialUrl: String, expectedPattern: Regex): String? {
        return withTimeoutOrNull(RESOLVER_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                var webview: WebView? = null

                ContextCompat.getMainExecutor(context).execute {
                    webview = WebView(context).apply {
                        setDefaultSettings()
                        settings.javaScriptEnabled = true
                    }

                    webview?.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url.toString()
                            if (expectedPattern.matches(url)) {
                                logcat(LogPriority.DEBUG) { "[NovelWebViewResolver] Matched: $url" }
                                cont.resume(url)
                                return true
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            if (expectedPattern.matches(url)) {
                                logcat(LogPriority.DEBUG) { "[NovelWebViewResolver] Matched on finish: $url" }
                                cont.resume(url)
                            }
                        }
                    }

                    webview?.loadUrl(initialUrl)
                }

                cont.invokeOnCancellation {
                    ContextCompat.getMainExecutor(context).execute {
                        webview?.run {
                            stopLoading()
                            destroy()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val RESOLVER_TIMEOUT_MS = 30_000L
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /**
         * Create a dynamic proxy that implements the extension library's [WebViewResolver]
         * interface (loaded in [extensionClassLoader]), delegating to [resolver].
         *
         * The proxy bridges the app-side [NovelWebViewResolver] with the extension's
         * interface without requiring a compile-time dependency on the extension library.
         */
        fun createProxy(resolver: NovelWebViewResolver, extensionClassLoader: ClassLoader): Any {
            // Find the WebViewResolver interface in the extension's classloader
            val resolverInterface = Class.forName(
                "yokai.extension.novel.lib.WebViewResolver",
                false,
                extensionClassLoader,
            )

            return Proxy.newProxyInstance(
                extensionClassLoader,
                arrayOf(resolverInterface),
                ResolverInvocationHandler(resolver),
            )
        }
    }
}

/**
 * Invocation handler that delegates calls to the extension's WebViewResolver.resolveUrl()
 * to our [NovelWebViewResolver]. Uses runBlocking since the extension's suspend function
 * is invoked via reflection (which doesn't support coroutine suspension across classloaders).
 */
private class ResolverInvocationHandler(
    private val resolver: NovelWebViewResolver,
) : InvocationHandler {
    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
        if (method.name == "resolveUrl" && args != null && args.size == 2) {
            val initialUrl = args[0] as String
            val expectedPattern = args[1] as Regex
            return runBlocking { resolver.resolveUrl(initialUrl, expectedPattern) }
        }
        return null
    }
}
