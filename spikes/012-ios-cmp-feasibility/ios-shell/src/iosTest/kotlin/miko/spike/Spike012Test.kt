package miko.spike

import androidx.compose.ui.window.ComposeUIViewController
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class Spike012Test {

    // Track A gate: Compose screen instantiates as a real UIViewController
    // on the iOS simulator — commonMain Compose → iOS rendering stack.
    // enforceStrictPlistSanityCheck=false: the test bundle's Info.plist lacks
    // the app keys the check wants; without this it throws asynchronously on
    // the main workloop and kills the test process.
    @Test
    fun trackA_composeUIViewControllerInstantiates() {
        val vc = ComposeUIViewController(
            configure = { enforceStrictPlistSanityCheck = false },
            content = { SpikeScreen() },
        )
        assertNotNull(vc.view, "Compose view failed to load headlessly")
        assertEquals("ios", platformName())
    }

    // Track B gate part 1: pure JSCore evaluation works on Kotlin/Native.
    @Test
    fun trackB_pureEval() {
        val rt = IosJsRuntime()
        assertEquals("6", rt.evalOrThrow("1 + 2 * 3 - 1"))
    }

    // Track B gate part 2: a mangayomi-flavored source script fetches real
    // network data through the Kotlin bridge and returns parsed entries.
    @Test
    fun trackB_jsSourceWithFetchBridge() = runBlocking {
        val client = HttpClient(Darwin)
        val rt = IosJsRuntime()

        rt.evalOrThrow(
            """
            function __getPopularJson() {
                var body = fetch("https://jsonplaceholder.typicode.com/posts");
                var posts = JSON.parse(body).slice(0, 5);
                return JSON.stringify(posts.map(function(p) {
                    return { title: p.title, url: "/post/" + p.id };
                }));
            }
            """.trimIndent(),
        )

        var json = ""
        var guard = 0
        while (true) {
            try {
                json = rt.evalOrThrow("__getPopularJson()")
                break
            } catch (e: FetchNeeded) {
                val url = rt.consumeFetchRequest() ?: fail("fetch marker lost")
                rt.supplyFetchResult(url, client.get(url).bodyAsText())
                if (++guard > 8) fail("fetch continuation loop exceeded 8 rounds")
            }
        }

        assertTrue(json.contains("\"title\""), "source returned no entries: $json")
        println("TRACK-B RESULT: $json")
    }
}
