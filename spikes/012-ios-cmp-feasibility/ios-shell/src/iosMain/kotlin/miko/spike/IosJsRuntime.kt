package miko.spike

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import platform.JavaScriptCore.JSContext

actual fun platformName(): String = "ios"

class FetchNeeded(val url: String) : Exception("fetch:$url")

class JsEvalFailure(message: String?) : Exception(message)

/**
 * Track B: JavaScriptCore-evaluated source runtime.
 *
 * JS calls `fetch(url)`; the shim throws `{__fetch: url}` when the response
 * isn't cached — Kotlin catches, fetches over ktor-darwin, stores the body in
 * `__cache`, and re-evaluates the entry point. Synchronous continuation
 * without needing ObjC-block bridging (probeNativeBlockBridging tests whether
 * the direct lambda bridge also works — diagnostic only).
 */
@OptIn(ExperimentalForeignApi::class)
class IosJsRuntime {
    private val context = JSContext()
    private var lastFetchUrl: String? = null

    init {
        context.evaluateScript(
            """
            var __cache = {};
            function fetch(u) {
                if (__cache[u] === undefined) throw { __fetch: u };
                return __cache[u];
            }
            """.trimIndent(),
        )
    }

    /** Evaluates script; throws FetchNeeded / JsEvalFailure on JS exceptions. */
    fun evalOrThrow(script: String): String {
        val value = context.evaluateScript(script)
        context.exception?.let { ex ->
            context.exception = null
            val url = ex.objectForKeyedSubscript("__fetch")?.toString()
            if (url != null) {
                lastFetchUrl = url
                throw FetchNeeded(url)
            }
            throw JsEvalFailure(ex.toString())
        }
        return value?.toString() ?: ""
    }

    fun consumeFetchRequest(): String? = lastFetchUrl.also { lastFetchUrl = null }

    fun supplyFetchResult(url: String, body: String) {
        val key = Json.encodeToString(url)
        val value = Json.encodeToString(body)
        evalOrThrow("__cache[$key] = $value")
    }

    /** Diagnostic: does passing a Kotlin lambda to setObject give JS a callable? */
    fun probeNativeBlockBridging(): Boolean {
        context.setObject({ s: String -> "pong:$s" }, forKeyedSubscript = "ping")
        return try {
            evalOrThrow("typeof ping") == "function" && evalOrThrow("ping('x')") == "pong:x"
        } catch (e: Exception) {
            false
        }
    }
}
