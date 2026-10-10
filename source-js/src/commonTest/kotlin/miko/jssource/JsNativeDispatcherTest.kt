package miko.jssource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

/**
 * Round-trip test: JS-side dispatch shape → JsNativeApi → JSON result.
 * Uses a recording fake so the wire contract is verified without an engine.
 */
class JsNativeDispatcherTest {

    private class FakeApi : JsNativeApi {
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        var fetchResult = """{"status":200,"url":"u","headers":{},"body":"hi"}"""
        var intResult = 7
        var boolResult = true
        var nullResult: String? = null

        override fun fetch(url: String, optionsJson: String?): String {
            calls += "fetch" to listOf(url, optionsJson); return fetchResult
        }
        override fun fetchBinary(url: String, optionsJson: String?): String = ""
        override fun fetchProto(url: String, configJson: String, optionsJson: String?): String = ""
        override fun storageGet(key: String): String? = calls.let { calls += "storageGet" to listOf(key); null }
        override fun storageSet(key: String, value: String) { calls += "storageSet" to listOf(key, value) }
        override fun storageRemove(key: String) {}
        override fun storageClear() {}
        override fun storageKeys(): String = "[]"
        override fun localStorageGet(key: String): String? = null
        override fun localStorageSet(key: String, value: String) {}
        override fun localStorageRemove(key: String) {}
        override fun localStorageClear() {}
        override fun localStorageKeys(): String = "[]"
        override fun sessionStorageGet(key: String): String? = null
        override fun sessionStorageSet(key: String, value: String) {}
        override fun sessionStorageRemove(key: String) {}
        override fun sessionStorageClear() {}
        override fun sessionStorageKeys(): String = "[]"
        override fun resolveUrl(url: String, base: String?): String = url
        override fun getPathname(url: String): String = ""
        override fun select(html: String, selector: String): String = "[]"
        override fun aesGcmDecrypt(keyB64: String, ivB64: String, cipherB64: String): String = ""
        override fun domLoad(html: String): Int { calls += "domLoad" to listOf(html); return intResult }
        override fun domSelect(handle: Int, selector: String): String = "[1,2]"
        override fun domParent(handle: Int): Int = -1
        override fun domChildren(handle: Int, selector: String?): String = "[]"
        override fun domNext(handle: Int, selector: String?): Int = -1
        override fun domPrev(handle: Int, selector: String?): Int = -1
        override fun domNextAll(handle: Int, selector: String?): String = "[]"
        override fun domPrevAll(handle: Int, selector: String?): String = "[]"
        override fun domSiblings(handle: Int, selector: String?): String = "[]"
        override fun domClosest(handle: Int, selector: String): Int = -1
        override fun domContents(handle: Int): String = "[]"
        override fun domIs(handle: Int, selector: String): Boolean = boolResult
        override fun domHas(handle: Int, selector: String): Boolean = false
        override fun domNot(handle: Int, selector: String): String = "[]"
        override fun domHtml(handle: Int): String = ""
        override fun domOuterHtml(handle: Int): String = ""
        override fun domXml(handle: Int): String = ""
        override fun domText(handle: Int): String = ""
        override fun domAttr(handle: Int, name: String): String? = nullResult
        override fun domSetAttr(handle: Int, name: String, value: String) {}
        override fun domRemoveAttr(handle: Int, name: String) {}
        override fun domAttrs(handle: Int): String = "{}"
        override fun domHasClass(handle: Int, className: String): Boolean = false
        override fun domData(handle: Int, key: String): String? = null
        override fun domVal(handle: Int): String? = null
        override fun domTagName(handle: Int): String = ""
        override fun domIsTextNode(handle: Int): Boolean = false
        override fun domReplaceWith(handle: Int, html: String) {}
        override fun domBefore(handle: Int, html: String) {}
        override fun domAfter(handle: Int, html: String) {}
        override fun domAppend(handle: Int, html: String) {}
        override fun domPrepend(handle: Int, html: String) {}
        override fun domEmpty(handle: Int) {}
        override fun domRemove(handle: Int) {}
        override fun domAddClass(handle: Int, className: String) {}
        override fun domRemoveClass(handle: Int, className: String) {}
        override fun domRelease(handle: Int) {}
        override fun domReleaseAll() {}
        override fun consoleLog(message: String) { calls += "consoleLog" to listOf(message) }
        override fun consoleError(message: String) {}
        override fun consoleWarn(message: String) {}
    }

    private val json = Json

    @Test
    fun fetchDispatchesArgsAndReturnsJsonEncodedString() {
        val api = FakeApi()
        val dispatcher = JsNativeDispatcher(api, json)
        val result = dispatcher.dispatch("fetch", """["https://x",{"method":"GET"}]""")
        // fetch is a raw-JSON method: the impl returns JSON text, which the
        // dispatcher passes through verbatim (the JS side does JSON.parse).
        assertEquals("{\"status\":200,\"url\":\"u\",\"headers\":{},\"body\":\"hi\"}", result)
        assertEquals("https://x", api.calls[0].second[0])
        assertEquals("{\"method\":\"GET\"}", api.calls[0].second[1])
    }

    @Test
    fun intAndBoolResultsEncodeDirectly() {
        val api = FakeApi()
        val dispatcher = JsNativeDispatcher(api, json)
        assertEquals("7", dispatcher.dispatch("domLoad", """["<p>x</p>"]"""))
        assertEquals("true", dispatcher.dispatch("domIs", """[3,"p"]"""))
    }

    @Test
    fun nullResultEncodesAsNull() {
        val api = FakeApi()
        val dispatcher = JsNativeDispatcher(api, json)
        assertEquals("null", dispatcher.dispatch("domAttr", """[1,"href"]"""))
        assertEquals("null", dispatcher.dispatch("domEmpty", """[1]"""))
    }

    @Test
    fun dispatchScriptCoversEveryMethod() {
        val api = FakeApi()
        val dispatcher = JsNativeDispatcher(api, json)
        // Every advertised method name must route without throwing.
        for (name in JsNativeDispatch.methodNames) {
            val arity = when (name) {
                "fetchProto" -> 3
                "fetch", "fetchBinary", "localStorageSet", "sessionStorageSet", "storageSet",
                "resolveUrl", "select", "domSelect", "domChildren", "domNext", "domPrev",
                "domNextAll", "domPrevAll", "domSiblings", "domClosest", "domIs", "domHas",
                "domNot", "domAttr", "domRemoveAttr", "domHasClass", "domData",
                "domReplaceWith", "domBefore", "domAfter", "domAppend", "domPrepend",
                "domAddClass", "domRemoveClass", -> 2
                "aesGcmDecrypt", "__aesGcmDecrypt", "domSetAttr" -> 3
                else -> 1
            }
            val args = "[" + List(arity) { i ->
                when {
                    name.startsWith("dom") && i == 0 -> "1"
                    name == "fetch" || name == "fetchBinary" -> if (i == 0) "\"u\"" else "null"
                    name == "fetchProto" -> "\"x\""
                    else -> "\"a\""
                }
            }.joinToString(",") + "]"
            dispatcher.dispatch(name, args) // must not throw
        }
    }
}
