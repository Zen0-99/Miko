package miko.jssource

/**
 * JS-side adapter that builds the `__native` object on engines without a
 * native-binding API (JavaScriptCore). Every method marshals its arguments
 * to JSON and calls the single host callback `__nativeInvoke(name, argsJson)`,
 * which returns a JSON string result. This collapses ~60 C-interop callback
 * registrations into one.
 *
 * Kotlin side must register `__nativeInvoke` before this script evaluates and
 * dispatch to [JsNativeApi] via [JsNativeDispatcher.dispatch].
 */
internal object JsNativeDispatch {

    val methodNames: List<String> = listOf(
        "fetch", "fetchBinary", "fetchProto",
        "storageGet", "storageSet", "storageRemove", "storageClear", "storageKeys",
        "localStorageGet", "localStorageSet", "localStorageRemove", "localStorageClear", "localStorageKeys",
        "sessionStorageGet", "sessionStorageSet", "sessionStorageRemove", "sessionStorageClear", "sessionStorageKeys",
        "resolveUrl", "getPathname", "select", "aesGcmDecrypt", "__aesGcmDecrypt",
        "domLoad", "domSelect", "domParent", "domChildren", "domNext", "domPrev",
        "domNextAll", "domPrevAll", "domSiblings", "domClosest", "domContents",
        "domIs", "domHas", "domNot", "domHtml", "domOuterHtml", "domXml", "domText",
        "domAttr", "domSetAttr", "domRemoveAttr", "domAttrs", "domHasClass", "domData",
        "domVal", "domTagName", "domIsTextNode",
        "domReplaceWith", "domBefore", "domAfter", "domAppend", "domPrepend", "domEmpty",
        "domRemove", "domAddClass", "domRemoveClass", "domRelease", "domReleaseAll",
        "consoleLog", "consoleError", "consoleWarn",
    )

    /**
     * Script that creates `global.__native` forwarding each method to
     * `__nativeInvoke`. String args pass through; JSON-encoded args are
     * parsed back on the Kotlin side.
     */
    val script: String
        get() = buildString {
            append("(function(global) {\n")
            append("  if (typeof __nativeInvoke !== 'function') throw new Error('__nativeInvoke not bound');\n")
            append("  function __call(name, args) {\n")
            append("    var raw = __nativeInvoke(name, JSON.stringify(args));\n")
            append("    return raw == null ? null : JSON.parse(raw);\n")
            append("  }\n")
            append("  global.__native = {};\n")
            for (name in methodNames) {
                append(
                    "  global.__native.$name = function() {" +
                        " return __call('$name', Array.prototype.slice.call(arguments)); };\n",
                )
            }
            append("})(this);\n")
        }
}
