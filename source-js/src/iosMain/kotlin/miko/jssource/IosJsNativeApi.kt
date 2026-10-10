package miko.jssource

import com.fleeksoft.ksoup.Ksoup
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import eu.kanade.tachiyomi.core.network.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import io.ktor.http.formUrlEncode
import io.ktor.util.flattenEntries
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSLog

/**
 * iOS implementation of the `__native` bridge. Behavior mirrors the Android
 * NativeApiImpl: fetch via Ktor-Darwin, storage via NSUserDefaults, DOM via
 * the shared Ksoup [JsDomStore], AES-GCM via cryptography-apple.
 *
 * All methods are synchronous from JS — ktor calls block via runBlocking on
 * the runtime's engine thread.
 */
class IosJsNativeApi(
    private val pluginId: String,
    private val client: HttpClient = platformHttpClient(),
    private val json: Json = Json,
    private val storage: IosPluginStorage = IosPluginStorage(pluginId, "storage"),
    private val localStorage: IosPluginStorage = IosPluginStorage(pluginId, "localStorage"),
) : JsNativeApi {

    private val sessionStorage = IosSessionStorage()
    private val domStore = JsDomStore()

    // ------------------------------------------------------------------
    // Networking
    // ------------------------------------------------------------------

    override fun fetch(url: String, optionsJson: String?): String {
        val options = optionsJson?.let { json.decodeFromString<JsFetchRequest>(it) } ?: JsFetchRequest()
        return runCatching {
            runBlocking {
                val response = client.request(url) {
                    method = HttpMethod.parse(options.method.uppercase())
                    options.headers.forEach { (name, value) -> header(name, value) }
                    if (!options.referrer.isNullOrBlank() && options.headers.keys.none { it.equals("referer", true) }) {
                        header("Referer", options.referrer)
                    }
                    if (!options.origin.isNullOrBlank() && options.headers.keys.none { it.equals("origin", true) }) {
                        header("Origin", options.origin)
                    }
                    when (options.bodyType) {
                        BodyType.Form -> setBody(
                            Parameters.build {
                                options.formEntries?.forEach { append(it.key, it.value) }
                            }.formUrlEncode().let { TextContent(it, ContentType.Application.FormUrlEncoded) },
                        )
                        BodyType.Text -> setBody(
                            TextContent(
                                options.body ?: "",
                                options.headers["Content-Type"]?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                                    ?: ContentType.Application.Json,
                            ),
                        )
                        BodyType.None -> {}
                    }
                }
                val headers = response.headers.flattenEntries()
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, values) -> values.joinToString(",") }
                json.encodeToString(
                    JsFetchResponse(
                        status = response.status.value,
                        // Android reports the post-redirect request URL.
                        url = response.call.request.url.toString(),
                        headers = headers,
                        body = response.bodyAsText(),
                        bodyBase64 = null,
                    ),
                )
            }
        }.getOrElse { error ->
            json.encodeToString(
                JsFetchResponse(
                    status = 0,
                    url = url,
                    headers = emptyMap(),
                    body = error.message,
                ),
            )
        }
    }

    override fun fetchBinary(url: String, optionsJson: String?): String {
        val options = optionsJson?.let { json.decodeFromString<JsFetchRequest>(it) } ?: JsFetchRequest()
        return runCatching {
            runBlocking {
                val response = client.request(url) {
                    method = HttpMethod.parse(options.method.uppercase())
                    options.headers.forEach { (name, value) -> header(name, value) }
                }
                platformBase64Encode(response.bodyAsBytes())
            }
        }.getOrElse { "" }
    }

    override fun fetchProto(url: String, configJson: String, optionsJson: String?): String {
        val config = json.decodeFromString<JsProtoFetchConfig>(configJson)
        val options = optionsJson?.let { json.decodeFromString<JsFetchRequest>(it) }
            ?: JsFetchRequest(method = "POST")
        val requestPayload = GrpcWeb.encodeRequest(config)
        val framed = GrpcWeb.frameMessage(requestPayload)
        val responsePayload = runBlocking {
            client.request(url) {
                method = HttpMethod.Post
                options.headers.forEach { (name, value) -> header(name, value) }
                val names = options.headers.keys.map { it.lowercase() }.toSet()
                if ("content-type" !in names) header("Content-Type", "application/grpc-web+proto")
                if ("accept" !in names) header("Accept", "application/grpc-web+proto")
                if ("x-grpc-web" !in names) header("X-Grpc-Web", "1")
                if ("x-user-agent" !in names) header("X-User-Agent", "grpc-web-javascript/0.1")
                setBody(framed)
            }.bodyAsBytes()
        }
        return GrpcWeb.decodeResponse(config.responseType, GrpcWeb.extractPayload(responsePayload))
    }

    // ------------------------------------------------------------------
    // Storage
    // ------------------------------------------------------------------

    override fun storageGet(key: String): String? = storage.get(key)
    override fun storageSet(key: String, value: String) = storage.set(key, value)
    override fun storageRemove(key: String) = storage.remove(key)
    override fun storageClear() = storage.clear()
    override fun storageKeys(): String = json.encodeToString(storage.keys())

    override fun localStorageGet(key: String): String? = localStorage.get(key)
    override fun localStorageSet(key: String, value: String) = localStorage.set(key, value)
    override fun localStorageRemove(key: String) = localStorage.remove(key)
    override fun localStorageClear() = localStorage.clear()
    override fun localStorageKeys(): String = json.encodeToString(localStorage.keys())

    override fun sessionStorageGet(key: String): String? = sessionStorage.get(key)
    override fun sessionStorageSet(key: String, value: String) = sessionStorage.set(key, value)
    override fun sessionStorageRemove(key: String) = sessionStorage.remove(key)
    override fun sessionStorageClear() = sessionStorage.clear()
    override fun sessionStorageKeys(): String = json.encodeToString(sessionStorage.keys())

    // ------------------------------------------------------------------
    // URL / HTML helpers
    // ------------------------------------------------------------------

    override fun resolveUrl(url: String, base: String?): String = platformResolveUrl(url, base)
    override fun getPathname(url: String): String = platformGetPathname(url)

    override fun select(html: String, selector: String): String {
        val nodes = runCatching {
            Ksoup.parseBodyFragment(html)
                .select(selector)
                .map { element ->
                    JsDomNode(
                        html = element.outerHtml(),
                        text = element.text(),
                        attrs = element.attributes().associate { it.key to it.value },
                    )
                }
        }.getOrElse { emptyList() }
        return json.encodeToString(nodes)
    }

    // ------------------------------------------------------------------
    // Crypto
    // ------------------------------------------------------------------

    override fun aesGcmDecrypt(keyB64: String, ivB64: String, cipherB64: String): String {
        if (keyB64.isBlank() || ivB64.isBlank() || cipherB64.isBlank()) return ""
        return runCatching {
            val keyRaw = platformBase64Decode(keyB64)
            val iv = platformBase64Decode(ivB64)
            val cipherBytes = platformBase64Decode(cipherB64)
            // JCE semantics: zero-pad key to 32 bytes, 128-bit tag.
            val keyBytes = ByteArray(32)
            keyRaw.copyInto(keyBytes, endIndex = minOf(keyRaw.size, keyBytes.size))
            val provider = CryptographyProvider.Default
            val aesGcm = provider.get(AES.GCM)
            val key = aesGcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, keyBytes)
            // IvAuthenticatedCipher: decrypt expects ciphertext || tag, iv separately.
            val plain = key.cipher().decryptWithIvBlocking(iv, cipherBytes)
            platformBase64Encode(plain)
        }.getOrElse { "" }
    }

    // ------------------------------------------------------------------
    // DOM store
    // ------------------------------------------------------------------

    override fun domLoad(html: String): Int = domStore.loadDocument(html)

    override fun domSelect(handle: Int, selector: String): String {
        // jsoup does not support quotes inside :contains("..."), strip them
        val cleaned = selector.replace(Regex(""":contains\(["']([^"']+)["']\)""")) { match ->
            ":contains(${match.groupValues[1]})"
        }
        return json.encodeToString(domStore.select(handle, cleaned).toList())
    }

    override fun domParent(handle: Int): Int = domStore.parent(handle)
    override fun domChildren(handle: Int, selector: String?): String =
        json.encodeToString(domStore.children(handle, selector).toList())
    override fun domNext(handle: Int, selector: String?): Int = domStore.next(handle, selector)
    override fun domPrev(handle: Int, selector: String?): Int = domStore.prev(handle, selector)
    override fun domNextAll(handle: Int, selector: String?): String =
        json.encodeToString(domStore.nextAll(handle, selector).toList())
    override fun domPrevAll(handle: Int, selector: String?): String =
        json.encodeToString(domStore.prevAll(handle, selector).toList())
    override fun domSiblings(handle: Int, selector: String?): String =
        json.encodeToString(domStore.siblings(handle, selector).toList())
    override fun domClosest(handle: Int, selector: String): Int = domStore.closest(handle, selector)
    override fun domContents(handle: Int): String =
        json.encodeToString(domStore.contents(handle).toList())
    override fun domIs(handle: Int, selector: String): Boolean = domStore.matches(handle, selector)
    override fun domHas(handle: Int, selector: String): Boolean = domStore.has(handle, selector)
    override fun domNot(handle: Int, selector: String): String =
        json.encodeToString(domStore.not(handle, selector).toList())
    override fun domHtml(handle: Int): String = domStore.getHtml(handle)
    override fun domOuterHtml(handle: Int): String = domStore.getOuterHtml(handle)
    override fun domXml(handle: Int): String = domStore.getXml(handle)
    override fun domText(handle: Int): String = domStore.getText(handle)
    override fun domAttr(handle: Int, name: String): String? = domStore.getAttr(handle, name)
    override fun domSetAttr(handle: Int, name: String, value: String) = domStore.setAttr(handle, name, value)
    override fun domRemoveAttr(handle: Int, name: String) = domStore.removeAttr(handle, name)
    override fun domAttrs(handle: Int): String = json.encodeToString(domStore.getAllAttrs(handle))
    override fun domHasClass(handle: Int, className: String): Boolean = domStore.hasClass(handle, className)
    override fun domData(handle: Int, key: String): String? = domStore.getData(handle, key)
    override fun domVal(handle: Int): String? = domStore.getVal(handle)
    override fun domTagName(handle: Int): String = domStore.getTagName(handle)
    override fun domIsTextNode(handle: Int): Boolean = domStore.isTextNode(handle)
    override fun domReplaceWith(handle: Int, html: String) = domStore.replaceWith(handle, html)
    override fun domBefore(handle: Int, html: String) = domStore.before(handle, html)
    override fun domAfter(handle: Int, html: String) = domStore.after(handle, html)
    override fun domAppend(handle: Int, html: String) = domStore.append(handle, html)
    override fun domPrepend(handle: Int, html: String) = domStore.prepend(handle, html)
    override fun domEmpty(handle: Int) = domStore.empty(handle)
    override fun domRemove(handle: Int) = domStore.remove(handle)
    override fun domAddClass(handle: Int, className: String) = domStore.addClass(handle, className)
    override fun domRemoveClass(handle: Int, className: String) = domStore.removeClass(handle, className)
    override fun domRelease(handle: Int) = domStore.release(handle)
    override fun domReleaseAll() = domStore.releaseAll()

    // ------------------------------------------------------------------
    // Console
    // ------------------------------------------------------------------

    override fun consoleLog(message: String) {
        NSLog("NovelPlugin/$pluginId: %@", message)
    }
    override fun consoleError(message: String) {
        NSLog("NovelPlugin/$pluginId ERROR: %@", message)
    }
    override fun consoleWarn(message: String) {
        NSLog("NovelPlugin/$pluginId WARN: %@", message)
    }
}
