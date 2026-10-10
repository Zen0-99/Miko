package miko.jssource

import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Engine-neutral plugin runtime: owns the engine lifecycle (bootstrap +
 * module registration), the plugin-call polling protocol, and release.
 *
 * The polling protocol is ported verbatim from NovelJsSource.callPluginPolling —
 * it works entirely through [JsEngine.evaluate], so any conforming engine
 * (J2V8, JavaScriptCore, QuickJS) can host it unchanged.
 */
class JsRuntime(
    private val pluginId: String,
    private val engine: JsEngine,
    moduleRegistry: JsModuleRegistry = JsModuleRegistry(),
) {
    @Volatile
    var released = false
        private set

    init {
        engine.evaluate(JsBootstrap.script, "novel-js-runtime-bootstrap.js")
        val modules = moduleRegistry.modules()
        modules.forEach { module ->
            engine.evaluate(module.script, module.name)
        }
    }

    fun evaluate(script: String, fileName: String = "novel-plugin.js"): Any? {
        check(!released) { "JsRuntime for plugin $pluginId is released" }
        return engine.evaluate(script, fileName)
    }

    /**
     * Evaluate a plugin script inside a CommonJS-ish wrapper, exposing the
     * plugin instance as `__plugin`. Ported from NovelPluginScriptBuilder.
     */
    fun loadPlugin(script: String, moduleName: String) {
        evaluate(PluginScriptWrapper.wrap(script, moduleName), "$moduleName.js")
    }

    /**
     * Call `__plugin.<functionName>(args...)` and return the JSON-stringified
     * result. [args] are raw JS expressions (already JSON-encoded by the
     * caller). Async plugin methods resolve through the __drainJobs shim.
     */
    suspend fun callPluginFunction(
        functionName: String,
        args: List<String> = emptyList(),
        timeoutMs: Long = 30_000L,
    ): String {
        if (functionName == "filters") {
            val result = evaluate(
                script = "JSON.stringify(__plugin && __plugin.filters ? __plugin.filters : {})",
                fileName = "novel-plugin-call.js",
            )
            return result as? String ?: ""
        }
        val pollSleepMs = 10L
        val maxCycles = ((timeoutMs + pollSleepMs - 1L) / pollSleepMs).toInt().coerceIn(1, 3_000)
        return callPluginPolling(functionName, maxCycles, args)
    }

    private suspend fun callPluginPolling(
        functionName: String,
        maxCycles: Int,
        args: List<String>,
    ): String {
        val token = "rn_${Random.nextLong()}_${tokenCounter++}"
        val joinedArgs = args.joinToString(", ")

        evaluate(
            """
            (function() {
                globalThis.__r_$token = null;
                globalThis.__e_$token = null;
                globalThis.__d_$token = false;
                try {
                    var mp = (__plugin.$functionName($joinedArgs));
                    Promise.resolve(mp).then(function(v) {
                        try { globalThis.__r_$token = JSON.stringify(v); } catch(e) {}
                        globalThis.__d_$token = true;
                    }).catch(function(e) {
                        globalThis.__e_$token = String(e);
                        globalThis.__d_$token = true;
                    });
                } catch(e) {
                    globalThis.__e_$token = String(e);
                    globalThis.__d_$token = true;
                }
            })();
            """.trimIndent(),
            "novel-plugin-call-$functionName.js",
        )

        var cycles = 0
        while (cycles < maxCycles) {
            val done = evaluate(
                """
                (function() {
                    if (typeof __drainJobs === 'function') __drainJobs(1000);
                    return !!globalThis.__d_$token;
                })();
                """.trimIndent(),
                "novel-plugin-drain-check.js",
            ) as? Boolean ?: false
            if (done) break
            cycles++
            if (cycles < maxCycles && cycles >= FAST_POLL_SPIN_CYCLES) {
                delay(10L)
            }
        }

        val error = evaluate(
            "globalThis.__e_$token == null ? null : String(globalThis.__e_$token)",
            "novel-plugin-error-read.js",
        ) as? String
        val result = evaluate(
            "globalThis.__r_$token == null ? null : String(globalThis.__r_$token)",
            "novel-plugin-result-read.js",
        ) as? String

        evaluate(
            """
            delete globalThis.__r_$token;
            delete globalThis.__e_$token;
            delete globalThis.__d_$token;
            """.trimIndent(),
            "novel-plugin-result-cleanup.js",
        )

        if (!error.isNullOrEmpty() && error != "null") {
            throw PluginCallException("Plugin error in $functionName: $error")
        }
        return result ?: ""
    }

    fun release() {
        if (released) return
        released = true
        engine.release()
    }

    private var tokenCounter = 0

    companion object {
        private const val FAST_POLL_SPIN_CYCLES = 8
    }

    class PluginCallException(message: String) : Exception(message)
}
