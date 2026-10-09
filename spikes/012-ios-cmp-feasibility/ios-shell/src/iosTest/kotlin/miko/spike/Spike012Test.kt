package miko.spike

import androidx.compose.ui.window.ComposeUIViewController
import io.ktor.client.HttpClient
import io.ktor.client.engine.curl.Curl
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import miko.spike.diag.rawHttpGet
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
                var body = fetch("https://api.github.com/repos/Zen0-99/Miko");
                var r = JSON.parse(body);
                return JSON.stringify([{ title: r.full_name, url: r.html_url, id: r.id }]);
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
                val body = try {
                    client.get(url).bodyAsText()
                } catch (http: DarwinHttpRequestException) {
                    val o = http.origin
                    val diag = StringBuilder(
                        "darwin fetch failed for $url: " +
                            "domain=${o.domain} code=${o.code} desc=${o.localizedDescription}\n",
                    )
                    diag.append("ktor-curl: ").append(
                        runCatching {
                            HttpClient(Curl).get("https://api.github.com/zen").bodyAsText()
                        }.getOrElse { "FAILED: $it" },
                    ).append('\n')
                    diag.append("raw-socket-http: ").append(
                        runCatching { rawHttpGet("neverssl.com", "/") }
                            .getOrElse { "FAILED: $it" }
                            .take(140),
                    )
                    fail(diag.toString())
                }
                rt.supplyFetchResult(url, body)
                if (++guard > 8) fail("fetch continuation loop exceeded 8 rounds")
            }
        }

        assertTrue(json.contains("Zen0-99/Miko"), "source returned no entries: $json")
        println("TRACK-B RESULT: $json")
    }
}
