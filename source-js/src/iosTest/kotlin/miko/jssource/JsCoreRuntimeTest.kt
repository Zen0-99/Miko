package miko.jssource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * End-to-end probe of the iOS JS runtime: JSCore eval, __nativeInvoke
 * bridge roundtrip, module registry, plugin wrapper + promise drain,
 * DOM store through the bridge, and storage persistence.
 */
class JsCoreRuntimeTest {

    private class TestNativeApi(pluginId: String) : JsNativeApi by IosJsNativeApi(pluginId = pluginId)

    @Test
    fun evaluatesArithmetic() {
        val engine = JsCoreEngine("test", TestNativeApi("test"))
        try {
            assertEquals(3.0, engine.evaluate("1 + 2"))
            assertEquals("hello", engine.evaluate("'hel' + 'lo'"))
            assertEquals(true, engine.evaluate("typeof JSON.stringify === 'function'"))
        } finally {
            engine.release()
        }
    }

    @Test
    fun nativeInvokeRoundTripsStorage() {
        val runtime = IosJsRuntimeFactory().create("test-storage")
        try {
            runtime.evaluate("""__native.storageSet("greeting", "world")""")
            val got = runtime.evaluate("""__native.storageGet("greeting")""")
            assertEquals("world", got)
            runtime.evaluate("""__native.storageRemove("greeting")""")
            assertEquals(null, runtime.evaluate("""__native.storageGet("greeting")"""))
        } finally {
            runtime.release()
        }
    }

    @Test
    fun domBridgeSelectsAndMutates() {
        val runtime = IosJsRuntimeFactory().create("test-dom")
        try {
            runtime.evaluate(
                """var __doc = __native.domLoad('<div><p class="a">one</p><p>two</p></div>');""",
            )
            val handles = runtime.evaluate("""__native.domSelect(__doc, "p.a")""")
            assertNotNull(handles)
            // domSelect returns a JSON array of handles
            val arr = Json.parseToJsonElement(handles as String)
            assertEquals(1, (arr as kotlinx.serialization.json.JsonArray).size)
            val text = runtime.evaluate("""__native.domText(__native.domSelect(__doc, "p.a")[0])""")
            assertEquals("one", text)
        } finally {
            runtime.release()
        }
    }

    @Test
    fun requireResolvesBundledModules() {
        val runtime = IosJsRuntimeFactory().create("test-require")
        try {
            val status = runtime.evaluate(
                """require("@libs/novelStatus").NovelStatus ? "ok" : "missing"""",
            )
            assertEquals("ok", status)
            val abs = runtime.evaluate(
                """require("@libs/isAbsoluteUrl").isAbsoluteUrl("https://x")""",
            )
            assertEquals(true, abs)
        } finally {
            runtime.release()
        }
    }

    @Test
    fun pluginWrapperAndPromiseDrain() = runBlocking {
        val runtime = IosJsRuntimeFactory().create("test-plugin")
        try {
            // Minimal LNReader-shaped plugin: method returns a promise.
            val pluginSrc = """
                module.exports = {
                    baseUrl: "https://example.com",
                    popularNovels: function(page) {
                        return Promise.resolve([
                            { name: "n" + page, url: "/n/" + page, cover: "" }
                        ]);
                    },
                    filters: { sort: { type: "picker", value: "a" } }
                };
            """.trimIndent()
            runtime.loadPlugin(pluginSrc, "test-plugin")

            val popular = runtime.callPluginFunction("popularNovels", listOf("3"))
            assertTrue(popular.contains("\"name\":\"n3\""), "popularNovels result: $popular")
            assertTrue(popular.contains("\"url\":\"/n/3\""))

            val filters = runtime.callPluginFunction("filters")
            assertTrue(filters.contains("\"sort\""), "filters: $filters")

            // Async plugin method going through the promise drain:
            val pluginSrc2 = """
                module.exports = {
                    fetchData: function() {
                        return Promise.resolve(41 + 1);
                    }
                };
            """.trimIndent()
            runtime.loadPlugin(pluginSrc2, "test-plugin-2")
            val out = runtime.callPluginFunction("fetchData")
            assertEquals("42", out)
        } finally {
            runtime.release()
        }
    }

    @Test
    fun resolveUrlAndPathname() {
        val runtime = IosJsRuntimeFactory().create("test-url")
        try {
            val resolved = runtime.evaluate(
                """__native.resolveUrl("/series/x", "https://site.example/base/")""",
            )
            assertEquals("https://site.example/series/x", resolved)
            val path = runtime.evaluate("""__native.getPathname("https://site.example/a/b?c=1")""")
            assertEquals("/a/b", path)
        } finally {
            runtime.release()
        }
    }
}
