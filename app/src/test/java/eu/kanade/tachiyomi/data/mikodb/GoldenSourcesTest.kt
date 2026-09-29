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

    private fun ext(pkg: String, vararg sourceIds: Long, nsfw: Boolean = false) = MangaExtension.Installed(
        name = pkg.substringAfterLast('.'),
        pkgName = pkg,
        versionName = "1.0",
        versionCode = 1L,
        libVersion = 1.4,
        lang = "en",
        isNsfw = nsfw,
        pkgFactory = null,
        sources = sourceIds.map { id -> mockk<MangaSource> { every { this@mockk.id } returns id } },
        icon = null,
        hasUpdate = false,
        isObsolete = false,
        isShared = false,
    )

    private fun work(providerLinks: String, nsfw: Boolean = false) = MangaNovelIdBridge.WorkRow(
        mikoWorkId = "mk:mb:1",
        type = "manga",
        title = "Solo Leveling",
        providerLinks = providerLinks,
        nsfw = nsfw,
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

    @Test
    fun `host slug extracts second-level label`() {
        assertEquals("webtoons", golden.hostSlugOf("https://www.webtoons.com/en/x?title_no=1"))
        assertEquals("mangadex", golden.hostSlugOf("https://mangadex.org/title/abc"))
        assertEquals("naver", golden.hostSlugOf("https://comic.naver.com/webtoon/list?titleId=1"))
        assertEquals(null, golden.hostSlugOf(null))
    }

    @Test
    fun `pkg tail matches provider slug or link host`() {
        assertTrue(
            golden.pkgMatchesProvider(
                "eu.kanade.tachiyomi.extension.all.webtoons",
                "webtoons",
                "webtoons",
            ),
        )
        assertTrue(
            golden.pkgMatchesProvider(
                "yokai.extension.novel.en.annasarchive",
                "unknown",
                "annasarchive",
            ),
        )
        assertTrue(
            !golden.pkgMatchesProvider(
                "eu.kanade.tachiyomi.extension.all.mangadex",
                "webtoons",
                "webtoons",
            ),
        )
    }

    @Test
    fun `heuristic binds installed pkg to carrier without providers meta`() = runBlocking {
        every { mangaExtensions.installedExtensionsFlow } returns
            MutableStateFlow(
                listOf(
                    ext("eu.kanade.tachiyomi.extension.all.webtoons", 7L),
                    ext("eu.kanade.tachiyomi.extension.en.mangabat", 8L),
                ),
            )
        every { mikoDbManager.allSourceMeta() } returns mapOf(
            // miko.json ships tier but no providers for text extensions.
            "eu.kanade.tachiyomi.extension.all.webtoons" to meta(emptyList(), "S", "Webtoons"),
            "eu.kanade.tachiyomi.extension.en.mangabat" to meta(emptyList(), "B", "MangaBat"),
        )
        val w = work(
            """[{"provider":"webtoons","external_id":"123","url":"https://www.webtoons.com/en/x?title_no=123"}]""",
        )

        val out = golden.forWorkRow(w, WorkIdResolver.MEDIUM_MANGA)
        assertEquals(listOf("Webtoons"), out.map { it.name })
        assertEquals(listOf(7L), out[0].sourceIds)
        assertEquals("123", out[0].externalId)
    }

    @Test
    fun `broad fallback returns tier-sorted installed when nothing link-bound`() = runBlocking {
        every { mangaExtensions.installedExtensionsFlow } returns
            MutableStateFlow(
                listOf(
                    ext("eu.kanade.tachiyomi.extension.en.lowtier", 1L),
                    ext("eu.kanade.tachiyomi.extension.all.mangadex", 2L),
                ),
            )
        every { mikoDbManager.allSourceMeta() } returns mapOf(
            "eu.kanade.tachiyomi.extension.en.lowtier" to meta(emptyList(), "C", "LowTier"),
            "eu.kanade.tachiyomi.extension.all.mangadex" to meta(emptyList(), "S", "MangaDex"),
        )
        // Provider link exists but no installed carrier matches it.
        val w = work("""[{"provider":"royalroad","url":"https://www.royalroad.com/fiction/1"}]""")

        val out = golden.forWorkRow(w, WorkIdResolver.MEDIUM_MANGA)
        assertEquals(listOf("MangaDex", "LowTier"), out.map { it.name })
    }

    @Test
    fun `nsfw extensions are never probed for sfw works`() = runBlocking {
        every { mangaExtensions.installedExtensionsFlow } returns
            MutableStateFlow(
                listOf(
                    ext("eu.kanade.tachiyomi.extension.all.nhentai", 1L, nsfw = true),
                    ext("eu.kanade.tachiyomi.extension.en.mangabat", 2L),
                ),
            )
        every { mikoDbManager.allSourceMeta() } returns mapOf(
            "eu.kanade.tachiyomi.extension.all.nhentai" to meta(emptyList(), "S", "NHentai"),
            "eu.kanade.tachiyomi.extension.en.mangabat" to meta(emptyList(), "B", "MangaBat"),
        )
        val w = work("[]") // SFW work → broad fallback must skip the S-tier porn ext.

        val out = golden.forWorkRow(w, WorkIdResolver.MEDIUM_MANGA)
        assertEquals(listOf("MangaBat"), out.map { it.name })
    }

    @Test
    fun `nsfw works keep nsfw extensions in the pool`() = runBlocking {
        every { mangaExtensions.installedExtensionsFlow } returns
            MutableStateFlow(
                listOf(
                    ext("eu.kanade.tachiyomi.extension.all.nhentai", 1L, nsfw = true),
                    ext("eu.kanade.tachiyomi.extension.en.mangabat", 2L),
                ),
            )
        every { mikoDbManager.allSourceMeta() } returns mapOf(
            "eu.kanade.tachiyomi.extension.all.nhentai" to meta(emptyList(), "S", "NHentai"),
            "eu.kanade.tachiyomi.extension.en.mangabat" to meta(emptyList(), "B", "MangaBat"),
        )
        val w = work("[]", nsfw = true)

        val out = golden.forWorkRow(w, WorkIdResolver.MEDIUM_MANGA)
        assertEquals(listOf("NHentai", "MangaBat"), out.map { it.name })
    }
}
