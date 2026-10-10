package tachiyomi.domain.extension.novel.api

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository
import org.junit.jupiter.api.Test

class JsPluginApiTest {

    private val repo = ExtensionRepo(
        baseUrl = "https://example.com/repo",
        name = "MikoDB",
        shortName = null,
        website = "",
        signingKeyFingerprint = "fp",
    )

    private fun api(
        repos: List<ExtensionRepo>,
        fetcher: JsPluginIndexFetcher,
    ) = JsPluginApi(
        repoProvider = object : JsPluginRepoProvider {
            override suspend fun getAll() = repos
        },
        fetcher = fetcher,
        parser = JsPluginIndexParser(Json { ignoreUnknownKeys = true }),
        includeKotlinExtensions = false,
    )

    @Test
    fun `available plugins are stamped with repo name`() = runTest {
        val api = api(
            repos = listOf(repo),
            fetcher = object : JsPluginIndexFetcher {
                override suspend fun fetch(repoUrl: String) =
                    """[{"id":"a","name":"A","site":"s","url":"a.js","version":1}]"""
            },
        )
        api.fetchAvailablePlugins().single().repoName shouldBe "MikoDB"
    }

    @Test
    fun `a failing repo does not fail the whole fetch`() = runTest {
        val api = api(
            repos = listOf(repo, repo.copy(baseUrl = "https://example.com/broken")),
            fetcher = object : JsPluginIndexFetcher {
                override suspend fun fetch(repoUrl: String): String {
                    if (repoUrl.contains("broken")) error("boom")
                    return """[{"id":"a","name":"A","site":"s","url":"a.js","version":1}]"""
                }
            },
        )
        api.fetchAvailablePlugins() shouldHaveSize 1
    }

    @Test
    fun `kotlin extensions are filtered when includeKotlinExtensions is false`() = runTest {
        val api = api(
            repos = listOf(repo),
            fetcher = object : JsPluginIndexFetcher {
                override suspend fun fetch(repoUrl: String) =
                    """
                    [
                        {"id":"a","name":"A","site":"s","url":"a.js","version":1},
                        {"isNovel":true,"pkg":"com.x","apk":"x.apk","code":1}
                    ]
                    """.trimIndent()
            },
        )
        api.fetchAvailablePlugins() shouldHaveSize 1
    }

    @Test
    fun `combined provider dedupes repos across the three tables`() = runTest {
        val novelRepo = mockk<NovelExtensionRepoRepository>()
        val mangaRepo = mockk<MangaExtensionRepoRepository>()
        val animeRepo = mockk<AnimeExtensionRepoRepository>()
        coEvery { novelRepo.getAll() } returns listOf(repo)
        coEvery { mangaRepo.getAll() } returns listOf(
            repo,
            repo.copy(baseUrl = "https://example.com/manga-only"),
        )
        coEvery { animeRepo.getAll() } returns emptyList()

        val provider = CombinedJsPluginRepoProvider(novelRepo, mangaRepo, animeRepo)
        provider.getAll().map { it.baseUrl } shouldBe listOf(
            "https://example.com/repo",
            "https://example.com/manga-only",
        )
    }
}
