package tachiyomi.data.extension.novel

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.service.RepoHttpFetcher
import org.junit.jupiter.api.Test

class KtorJsIndexFetcherTest {

    private val repo = "https://raw.githubusercontent.com/example/mikoDB/main"

    private class RecordingFetcher(
        private val hits: Map<String, String?> = emptyMap(),
    ) : RepoHttpFetcher {
        val requested = mutableListOf<String>()
        override suspend fun get(url: String): String? {
            requested += url
            return hits[url]
        }
        override suspend fun getBytes(url: String): ByteArray? = null
    }

    @Test
    fun `candidates are tried in android order`() = runTest {
        val fetcher = RecordingFetcher(mapOf("$repo/index.json" to "[]"))
        KtorJsIndexFetcher(fetcher).fetch(repo) shouldBe "[]"
        fetcher.requested shouldBe listOf(
            "$repo/plugins.min.json",
            "$repo/plugins.json",
            "$repo/.dist/plugins.min.json",
            "$repo/.dist/plugins.json",
            "$repo/index.min.json",
            "$repo/index.json",
        )
    }

    @Test
    fun `first successful candidate wins`() = runTest {
        val fetcher = RecordingFetcher(
            mapOf(
                "$repo/plugins.json" to "[]",
                "$repo/index.json" to "[{}]",
            ),
        )
        KtorJsIndexFetcher(fetcher).fetch(repo) shouldBe "[]"
        fetcher.requested shouldBe listOf(
            "$repo/plugins.min.json",
            "$repo/plugins.json",
        )
    }

    @Test
    fun `json-suffixed repo url is fetched directly`() = runTest {
        val direct = "$repo/plugins.min.json"
        val fetcher = RecordingFetcher(mapOf(direct to "[]"))
        KtorJsIndexFetcher(fetcher).fetch(direct) shouldBe "[]"
        fetcher.requested shouldBe listOf(direct)
    }

    @Test
    fun `all candidates failing throws`() = runTest {
        val fetcher = RecordingFetcher()
        shouldThrow<JsPluginIndexFetchException> {
            KtorJsIndexFetcher(fetcher).fetch(repo)
        }
        fetcher.requested shouldBe listOf(
            "$repo/plugins.min.json",
            "$repo/plugins.json",
            "$repo/.dist/plugins.min.json",
            "$repo/.dist/plugins.json",
            "$repo/index.min.json",
            "$repo/index.json",
        )
    }
}
