package miko.jssource

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Routes a single `(name, argsJson)` host callback into the [JsNativeApi]
 * surface. Engines that expose only one bound function (JavaScriptCore via
 * `__nativeInvoke`) call [dispatch] from their native callback; the JS-side
 * [JsNativeDispatch] adapter provides the `__native.*` facade.
 */
class JsNativeDispatcher(
    private val api: JsNativeApi,
    private val json: Json = Json,
) {
    /**
     * Dispatch [name] with JSON-array arguments [argsJson]; returns the
     * result as a JSON-encoded string (strings are quoted, nulls → "null").
     */
    fun dispatch(name: String, argsJson: String): String {
        val args = json.parseToJsonElement(argsJson.let { if (it.isBlank()) "[]" else it })
        val list = (args as? kotlinx.serialization.json.JsonArray) ?: error("args not an array")
        fun str(i: Int): String = list[i].let {
            if (it is kotlinx.serialization.json.JsonPrimitive && it.isString) it.content else it.toString()
        }
        fun strOrNull(i: Int): String? =
            list.getOrNull(i)?.let { el ->
                when {
                    el is kotlinx.serialization.json.JsonNull -> null
                    el is kotlinx.serialization.json.JsonPrimitive && el.isString -> el.content
                    else -> el.toString()
                }
            }
        fun int(i: Int): Int = list[i].jsonPrimitive.intOrNull ?: str(i).toInt()
        fun bool(i: Int): Boolean = list[i].jsonPrimitive.booleanOrNull ?: str(i).toBoolean()

        val result: Any? = when (name) {
            "fetch" -> api.fetch(str(0), strOrNull(1))
            "fetchBinary" -> api.fetchBinary(str(0), strOrNull(1))
            "fetchProto" -> api.fetchProto(str(0), str(1), strOrNull(2))
            "storageGet" -> api.storageGet(str(0))
            "storageSet" -> api.storageSet(str(0), str(1))
            "storageRemove" -> api.storageRemove(str(0))
            "storageClear" -> api.storageClear()
            "storageKeys" -> api.storageKeys()
            "localStorageGet" -> api.localStorageGet(str(0))
            "localStorageSet" -> api.localStorageSet(str(0), str(1))
            "localStorageRemove" -> api.localStorageRemove(str(0))
            "localStorageClear" -> api.localStorageClear()
            "localStorageKeys" -> api.localStorageKeys()
            "sessionStorageGet" -> api.sessionStorageGet(str(0))
            "sessionStorageSet" -> api.sessionStorageSet(str(0), str(1))
            "sessionStorageRemove" -> api.sessionStorageRemove(str(0))
            "sessionStorageClear" -> api.sessionStorageClear()
            "sessionStorageKeys" -> api.sessionStorageKeys()
            "resolveUrl" -> api.resolveUrl(str(0), strOrNull(1))
            "getPathname" -> api.getPathname(str(0))
            "select" -> api.select(str(0), str(1))
            "aesGcmDecrypt", "__aesGcmDecrypt" -> api.aesGcmDecrypt(str(0), str(1), str(2))
            "domLoad" -> api.domLoad(str(0))
            "domSelect" -> api.domSelect(int(0), str(1))
            "domParent" -> api.domParent(int(0))
            "domChildren" -> api.domChildren(int(0), strOrNull(1))
            "domNext" -> api.domNext(int(0), strOrNull(1))
            "domPrev" -> api.domPrev(int(0), strOrNull(1))
            "domNextAll" -> api.domNextAll(int(0), strOrNull(1))
            "domPrevAll" -> api.domPrevAll(int(0), strOrNull(1))
            "domSiblings" -> api.domSiblings(int(0), strOrNull(1))
            "domClosest" -> api.domClosest(int(0), str(1))
            "domContents" -> api.domContents(int(0))
            "domIs" -> api.domIs(int(0), str(1))
            "domHas" -> api.domHas(int(0), str(1))
            "domNot" -> api.domNot(int(0), str(1))
            "domHtml" -> api.domHtml(int(0))
            "domOuterHtml" -> api.domOuterHtml(int(0))
            "domXml" -> api.domXml(int(0))
            "domText" -> api.domText(int(0))
            "domAttr" -> api.domAttr(int(0), str(1))
            "domSetAttr" -> api.domSetAttr(int(0), str(1), str(2))
            "domRemoveAttr" -> api.domRemoveAttr(int(0), str(1))
            "domAttrs" -> api.domAttrs(int(0))
            "domHasClass" -> api.domHasClass(int(0), str(1))
            "domData" -> api.domData(int(0), str(1))
            "domVal" -> api.domVal(int(0))
            "domTagName" -> api.domTagName(int(0))
            "domIsTextNode" -> api.domIsTextNode(int(0))
            "domReplaceWith" -> api.domReplaceWith(int(0), str(1))
            "domBefore" -> api.domBefore(int(0), str(1))
            "domAfter" -> api.domAfter(int(0), str(1))
            "domAppend" -> api.domAppend(int(0), str(1))
            "domPrepend" -> api.domPrepend(int(0), str(1))
            "domEmpty" -> api.domEmpty(int(0))
            "domRemove" -> api.domRemove(int(0))
            "domAddClass" -> api.domAddClass(int(0), str(1))
            "domRemoveClass" -> api.domRemoveClass(int(0), str(1))
            "domRelease" -> api.domRelease(int(0))
            "domReleaseAll" -> api.domReleaseAll()
            "consoleLog" -> api.consoleLog(str(0))
            "consoleError" -> api.consoleError(str(0))
            "consoleWarn" -> api.consoleWarn(str(0))
            else -> throw IllegalArgumentException("Unknown __native method: $name")
        }
        return encodeResult(result, rawJson = name in RAW_JSON_METHODS)
    }

    private fun encodeResult(result: Any?, rawJson: Boolean): String {
        return when (result) {
            null, is Unit -> "null"
            is String -> if (rawJson) {
                result
            } else {
                json.encodeToString(JsonElement.serializer(), kotlinx.serialization.json.JsonPrimitive(result))
            }
            is Boolean, is Int, is Long, is Double -> result.toString()
            is JsonElement -> json.encodeToString(JsonElement.serializer(), result)
            else -> json.encodeToString(JsonElement.serializer(), kotlinx.serialization.json.JsonPrimitive(result.toString()))
        }
    }

    companion object {
        /**
         * Methods whose [JsNativeApi] implementation already returns a
         * JSON-encoded string. Encoding it again as a JsonPrimitive would
         * double-quote it and break `JSON.parse` on the JS side.
         */
        private val RAW_JSON_METHODS = setOf(
            "fetch", "fetchProto", "select",
            "storageKeys", "localStorageKeys", "sessionStorageKeys",
            "domSelect", "domChildren", "domNextAll", "domPrevAll", "domSiblings",
            "domContents", "domNot", "domAttrs",
        )
    }
}
