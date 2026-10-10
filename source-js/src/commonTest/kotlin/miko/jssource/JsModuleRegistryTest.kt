package miko.jssource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsModuleRegistryTest {

    @Test
    fun registryExposesAllBundledModules() {
        val modules = JsModuleRegistry().modules().map { it.name }
        assertEquals(
            listOf(
                "novelStatus.js", "storage.js", "filterInputs.js", "defaultCover.js",
                "proseMirrorToHtml.js", "fetch.js", "isAbsoluteUrl.js",
                "typesConstants.js", "aes.js", "urlencode.js", "cheerio.js",
                "htmlparser2.js", "dayjs.js",
            ),
            modules,
        )
    }

    @Test
    fun bootstrapDefinesRequireAndNativeDrain() {
        val script = JsBootstrap.script
        assertTrue(script.contains("global.require = function"))
        assertTrue(script.contains("global.__drainJobs"))
        assertTrue(script.contains("global.Promise"))
        assertTrue(script.contains("FormData"))
        assertTrue(script.contains("URLSearchParams"))
        assertTrue(script.contains("Headers"))
    }

    @Test
    fun dispatchScriptCoversAllNativeMethods() {
        for (name in JsNativeDispatch.methodNames) {
            assertTrue(
                JsNativeDispatch.script.contains("global.__native.$name"),
                "dispatch script missing $name",
            )
        }
    }

    @Test
    fun fetchModuleCallsNativeFetch() {
        val fetchModule = JsModuleRegistry().modules().first { it.name == "fetch.js" }
        assertTrue(fetchModule.script.contains("__native.fetch("))
        assertTrue(fetchModule.script.contains("__native.fetchBinary("))
        assertTrue(fetchModule.script.contains("__native.fetchProto("))
    }
}
