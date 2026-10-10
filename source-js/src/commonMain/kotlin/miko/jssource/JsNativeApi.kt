package miko.jssource

/**
 * Native bridge contract exposed to plugin JS as `__native.*`.
 * Ported verbatim from NovelJsRuntime.NativeApi — every method is
 * synchronous from the JS side; platform implementations may block
 * internally (e.g. runBlocking around a suspend HTTP client).
 */
interface JsNativeApi {
    fun fetch(url: String, optionsJson: String?): String
    fun fetchBinary(url: String, optionsJson: String?): String
    fun fetchProto(url: String, configJson: String, optionsJson: String?): String
    fun storageGet(key: String): String?
    fun storageSet(key: String, value: String)
    fun storageRemove(key: String)
    fun storageClear()
    fun storageKeys(): String
    fun localStorageGet(key: String): String?
    fun localStorageSet(key: String, value: String)
    fun localStorageRemove(key: String)
    fun localStorageClear()
    fun localStorageKeys(): String
    fun sessionStorageGet(key: String): String?
    fun sessionStorageSet(key: String, value: String)
    fun sessionStorageRemove(key: String)
    fun sessionStorageClear()
    fun sessionStorageKeys(): String
    fun resolveUrl(url: String, base: String?): String
    fun getPathname(url: String): String
    fun select(html: String, selector: String): String
    fun aesGcmDecrypt(keyB64: String, ivB64: String, cipherB64: String): String

    // DOM Store methods
    fun domLoad(html: String): Int
    fun domSelect(handle: Int, selector: String): String
    fun domParent(handle: Int): Int
    fun domChildren(handle: Int, selector: String?): String
    fun domNext(handle: Int, selector: String?): Int
    fun domPrev(handle: Int, selector: String?): Int
    fun domNextAll(handle: Int, selector: String?): String
    fun domPrevAll(handle: Int, selector: String?): String
    fun domSiblings(handle: Int, selector: String?): String
    fun domClosest(handle: Int, selector: String): Int
    fun domContents(handle: Int): String
    fun domIs(handle: Int, selector: String): Boolean
    fun domHas(handle: Int, selector: String): Boolean
    fun domNot(handle: Int, selector: String): String
    fun domHtml(handle: Int): String
    fun domOuterHtml(handle: Int): String
    fun domXml(handle: Int): String
    fun domText(handle: Int): String
    fun domAttr(handle: Int, name: String): String?
    fun domSetAttr(handle: Int, name: String, value: String)
    fun domRemoveAttr(handle: Int, name: String)
    fun domAttrs(handle: Int): String
    fun domHasClass(handle: Int, className: String): Boolean
    fun domData(handle: Int, key: String): String?
    fun domVal(handle: Int): String?
    fun domTagName(handle: Int): String
    fun domIsTextNode(handle: Int): Boolean
    fun domReplaceWith(handle: Int, html: String)
    fun domBefore(handle: Int, html: String)
    fun domAfter(handle: Int, html: String)
    fun domAppend(handle: Int, html: String)
    fun domPrepend(handle: Int, html: String)
    fun domEmpty(handle: Int)
    fun domRemove(handle: Int)
    fun domAddClass(handle: Int, className: String)
    fun domRemoveClass(handle: Int, className: String)
    fun domRelease(handle: Int)
    fun domReleaseAll()

    // Console
    fun consoleLog(message: String)
    fun consoleError(message: String)
    fun consoleWarn(message: String)
}
