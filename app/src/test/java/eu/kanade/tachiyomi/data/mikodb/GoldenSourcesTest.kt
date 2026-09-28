package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.source.MangaSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoldenSourcesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val mikoDbManager = mockk<MikoDbManager>()
    private val bridge = mockk<MangaNovelIdBridge>()
    private val mangaExtensions = mockk<MangaExtensionManager>()
    private val novelExtensions = mockk<NovelExtensionManager>()

    private val golden = GoldenSources(
        mikoDbManager = mikoDbManager,
        bridge = bridge,
        mangaExtensions = mangaExtensions,
        novelExtensions = novelExtensions,
        json = json,
    )

    private fun ext(pkg: String, vararg sourceIds: Long) = MangaExtension.Installed(
        name = pkg.substringAfterLast('.'),
        pkgName = pkg,
        versionName = "1.0",
        versionCode = 1L,
        libVersion = 1.4,
        lang = "en",
        isNsfw = false,
        pkgFactory = null,
        sources = sourceIds.map { id -> mockk<MangaSource> { every { this@mockk.id } returns id } },
        icon = null,
        hasUpdate = false,
        isObsolete = false,
        isShared = false,
    )

    private fun work(providerLinks: String) = MangaNovelIdBridge.WorkRow(
        mikoWorkId = "mk:mb:1",
        type = "manga",
        title = "Solo Leveling",
        providerLinks = providerLinks,
    )

    private fun meta(
        providers: List<String>,
        tier: String,
        name: String = "src",
    ) = MikoDbManager.SourceMeta(name = name, tier = tier, providers = providers)

    @Test
    fun `tier ranking orders S A B C`() {
        assertTrue(golden.tierRank("S") < golden.tierRank("A"))
        assertTrue(golden.tierRank("A") < golden.tierRank("B"))
        assertTrue(golden.tierRank("B") < golden.tierRank("C"))
        assertTrue(golden.tierRank("C") < golden.tierRank(""))
    }

    @Test
    fun `provider links parse with external ids and urls`() {
        val links = golden.providerLinksOf(
            """[{"provider":"mangadex","external_id":"abc","url":"https://mangadex.org/title/abc"}]""",
        )
        assertEquals(1, links.size)
        assertEquals("mangadex", links[0].provider)
        assertEquals("abc", links[0].externalId)
        assertEquals("https://mangadex.org/title/abc", links[0].url)
    }

    @Test
    fun `malformed provider links parse empty`() {
        assertTrue(golden.providerLinksOf("not json").isEmpty())
        assertTrue(golden.providerLinksOf("[]").isEmpty())
    }

    @Test
    fun `golden sources filter to installed and rank S first`() = runBlocking {
        every { mangaExtensions.installedExtensionsFlow } returns
            MutableStateFlow(
                listOf(
                    ext("eu.kanade.tachiyomi.extension.all.mangadex", 1L),
                    ext("eu.kanade.tachiyomi.extension.en.mangabat", 2L),
                ),
            )
        every { mikoDbManager.allSourceMeta() } returns mapOf(
            "eu.kanade.tachiyomi.extension.all.mangadex" to meta(listOf("mangadex"), "S", "MangaDex"),
            "eu.kanade.tachiyomi.extension.en.mangabat" to meta(listOf("mangadex"), "A", "MangaBat"),
            "eu.kanade.tachiyomi.extension.en.notinstalled" to meta(listOf("mangadex"), "S", "NotInstalled"),
        )
        val w = work(
            """[{"provider":"mangadex","external_id":"x","url":"https://mangadex.org/title/x"}]""",
        )

        val out = golden.forWorkRow(w, WorkIdResolver.MEDIUM_MANGA)
        assertEquals(listOf("MangaDex", "MangaBat"), out.map { it.name })
        assertEquals(listOf(1L), out[0].sourceIds)
        assertEquals("x", out[0].externalId)
    }

    @Test
    fun `no installed match returns empty`() = runBlocking {
        every { mangaExtensions.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        val w = work("""[{"provider":"mangadex","external_id":"x"}]""")
        assertTrue(golden.forWorkRow(w, WorkIdResolver.MEDIUM_MANGA).isEmpty())
    }

    @Test
    fun `falls back to B tier when no S or A installed`() = runBlocking {
        every { mangaExtensions.installedExtensionsFlow } returns
            MutableStateFlow(listOf(ext("eu.kanade.tachiyomi.extension.en.weak", 9L)))
        every { mikoDbManager.allSourceMeta() } returns mapOf(
            "eu.kanade.tachiyomi.extension.en.weak" to meta(listOf("mangadex"), "B", "Weak"),
        )
        val w = work("""[{"provider":"mangadex","external_id":"x"}]""")
        val out = golden.forWorkRow(w, WorkIdResolver.MEDIUM_MANGA)
        assertEquals(1, out.size)
        assertEquals("Weak", out[0].name)
    }
}
