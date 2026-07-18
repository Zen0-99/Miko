package eu.kanade.tachiyomi.extension.novel.js

import android.content.Context
import app.cash.quickjs.QuickJs
import app.cash.quickjs.QuickJsException
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Closeable
import java.io.File

/**
 * QuickJS-based runtime for evaluating LNReader-compatible JS novel plugins.
 *
 * The runtime provides a [NativeApi] bridge that JS plugins call for network
 * requests, storage, and DOM manipulation. The bridge is registered as a
 * global `NativeApi` object in the QuickJS context.
 *
 * Each plugin gets its own runtime instance (QuickJS is not thread-safe).
 * The runtime is single-threaded — all calls must come from the same thread
 * or be synchronized externally.
 */
class NovelJsRuntime(
    private val pluginId: String,
    private val context: Context,
    private val storageDir: File,
) : Closeable {

    @Volatile
    private var released = false

    private val quickJs: QuickJs = QuickJs.create()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val nativeApi = NativeApiImpl(pluginId, context, storageDir, json)

    init {
        // Register the NativeApi bridge as a global JS object
        quickJs.set("NativeApi", NativeApi::class.java, nativeApi)

        // Inject bootstrap script that sets up the plugin context
        evaluateVoid(BOOTSTRAP_SCRIPT, "novel-js-runtime-bootstrap.js")
    }

    /**
     * Evaluate a JS script and return the result as a String.
     */
    fun evaluate(script: String, fileName: String = "novel-plugin.js"): String? {
        check(!released) { "Runtime already released" }
        return try {
            val result = quickJs.evaluate(script, fileName)
            result?.toString()
        } catch (e: QuickJsException) {
            logcat(LogPriority.ERROR, e) { "[$pluginId] JS evaluation error in $fileName: ${e.message}" }
            throw e
        }
    }

    /**
     * Evaluate a JS script without caring about the result.
     */
    fun evaluateVoid(script: String, fileName: String = "novel-plugin.js") {
        check(!released) { "Runtime already released" }
        try {
            quickJs.evaluate(script, fileName)
        } catch (e: QuickJsException) {
            logcat(LogPriority.ERROR, e) { "[$pluginId] JS void evaluation error in $fileName: ${e.message}" }
            throw e
        }
    }

    /**
     * Load and initialize a plugin script.
     * The script is wrapped via [NovelPluginScriptBuilder] and the plugin
     * instance is assigned to `__plugin`.
     */
    fun loadPlugin(script: String, pluginName: String) {
        val wrapped = NovelPluginScriptBuilder().wrap(script, pluginName)
        evaluateVoid(wrapped, "$pluginName.js")
    }

    /**
     * Call a method on the plugin instance and return the result as a JSON string.
     */
    fun callPluginMethod(methodName: String, vararg jsonArgs: String): String? {
        val argList = jsonArgs.joinToString(", ") { it }
        val script = "JSON.stringify(__plugin.$methodName($argList))"
        return evaluate(script, "$methodName-call.js")
    }

    /**
     * Call a method on the plugin instance that already returns a JSON string.
     */
    fun callPluginMethodRaw(methodName: String, vararg jsonArgs: String): String? {
        val argList = jsonArgs.joinToString(", ") { it }
        val script = "__plugin.$methodName($argList)"
        return evaluate(script, "$methodName-call.js")
    }

    /**
     * Check if the plugin instance has a given method.
     */
    fun hasMethod(methodName: String): Boolean {
        return try {
            evaluate("typeof __plugin.$methodName === 'function'", "has-method.js") == "true"
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        if (released) return
        released = true
        try {
            quickJs.close()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "[$pluginId] Error closing QuickJS runtime" }
        }
    }

    /**
     * Native API bridge exposed to JS plugins as `NativeApi`.
     *
     * JS plugins call these methods for network requests, storage, and DOM
     * manipulation. The bridge delegates to OkHttp, file-based storage,
     * and JSoup respectively.
     */
    interface NativeApi {
        fun fetch(url: String, optionsJson: String?): String
        fun storageGet(key: String): String?
        fun storageSet(key: String, value: String)
        fun storageRemove(key: String)
        fun storageClear()
        fun storageKeys(): String
        fun resolveUrl(url: String, base: String?): String
        fun consoleLog(message: String)
        fun consoleError(message: String)
    }

    private companion object {
        const val BOOTSTRAP_SCRIPT = """
            var __plugin = null;
            var console = {
                log: function(msg) { NativeApi.consoleLog(String(msg)); },
                error: function(msg) { NativeApi.consoleError(String(msg)); },
                warn: function(msg) { NativeApi.consoleLog(String(msg)); },
                info: function(msg) { NativeApi.consoleLog(String(msg)); },
                debug: function(msg) { NativeApi.consoleLog(String(msg)); }
            };
        """
    }
}

/**
 * Implementation of [NovelJsRuntime.NativeApi] using OkHttp, file storage, and JSoup.
 */
private class NativeApiImpl(
    private val pluginId: String,
    private val context: Context,
    private val storageDir: File,
    private val json: Json,
) : NovelJsRuntime.NativeApi {

    private val networkHelper: NetworkHelper by lazy { Injekt.get<NetworkHelper>() }
    private val storageFile: File by lazy {
        File(storageDir, "$pluginId.json").apply { parentFile?.mkdirs() }
    }
    private val storage: MutableMap<String, String> by lazy {
        loadStorage()
    }

    @Synchronized
    private fun loadStorage(): MutableMap<String, String> {
        if (!storageFile.exists()) return mutableMapOf()
        return try {
            val content = storageFile.readText()
            json.decodeFromString<Map<String, String>>(content).toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    @Synchronized
    private fun saveStorage() {
        try {
            storageFile.writeText(json.encodeToString(storage))
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "[$pluginId] Failed to save storage" }
        }
    }

    override fun fetch(url: String, optionsJson: String?): String {
        return try {
            runBlocking {
                val builder = Request.Builder().url(url)

                // Parse options (method, headers, body)
                if (optionsJson != null) {
                    val options = json.parseToJsonElement(optionsJson).jsonObject
                    val method = options["method"]?.jsonPrimitive?.contentOrNull ?: "GET"
                    // TODO: Add headers and body support
                }

                val client = networkHelper.client
                val response = client.newCall(builder.build()).execute()
                val body = response.body?.string() ?: ""
                val headersJson = response.headers.names().associateWith { response.header(it) ?: "" }

                val headersObj = JsonObject(
                    headersJson.entries.associate { it.key to JsonPrimitive(it.value) },
                )
                json.encodeToString(
                    JsonObject(
                        mapOf(
                            "status" to JsonPrimitive(response.code),
                            "headers" to headersObj,
                            "body" to JsonPrimitive(body),
                        ),
                    ),
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "[$pluginId] fetch error: $url" }
            json.encodeToString(
                JsonObject(
                    mapOf(
                        "status" to JsonPrimitive(0),
                        "body" to JsonPrimitive(""),
                        "error" to JsonPrimitive(e.message ?: "Unknown error"),
                    ),
                ),
            )
        }
    }

    override fun storageGet(key: String): String? = storage[key]

    override fun storageSet(key: String, value: String) {
        storage[key] = value
        saveStorage()
    }

    override fun storageRemove(key: String) {
        storage.remove(key)
        saveStorage()
    }

    override fun storageClear() {
        storage.clear()
        saveStorage()
    }

    override fun storageKeys(): String {
        return json.encodeToString(storage.keys.toList())
    }

    override fun resolveUrl(url: String, base: String?): String {
        return if (base != null) {
            java.net.URI(base).resolve(url).toString()
        } else {
            url
        }
    }

    override fun consoleLog(message: String) {
        logcat(LogPriority.DEBUG) { "[$pluginId] JS: $message" }
    }

    override fun consoleError(message: String) {
        logcat(LogPriority.ERROR) { "[$pluginId] JS ERROR: $message" }
    }
}
