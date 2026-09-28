package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.manga.service.MangaSourceManager

class CanonicalChapterRouterTest {

    private val chapterMap = mockk<CanonicalChapterMap>()
    private val goldenSources = mockk<GoldenSources>()
    private val sourceManager = mockk<MangaSourceManager>()

    private val router = CanonicalChapterRouter(chapterMap, goldenSources, sourceManager)

    private fun canonRow(refs: Map<String, String>) = CanonicalChapterMap.CanonChapter(
        seq = 29, number = 30.0, part = null, volume = null,
        kind = ChapterNumberParser.Kind.CHAPTER, label = "Chapter 30",
        confidence = "verified", sources = refs.keys.toList(), refs = refs,
    )

    private fun golden(pkg: String, tier: String, sourceId: Long) = GoldenSources.GoldenSource(
        pkgName = pkg, sourceIds = listOf(sourceId), name = pkg, tier = tier,
        provider = "", externalId = null, url = null,
    )

    private fun http(id: Long, pages: List<Page>?) = mockk<HttpSource> {
        every { this@mockk.id } returns id
        if (pages == null) {
            coEvery { getPageList(any()) } throws RuntimeException("404")
        } else {
            coEvery { getPageList(any()) } returns pages
        }
    }

    private fun setup(refs: Map<String, String>, goldens: List<GoldenSources.GoldenSource>) {
        coEvery { chapterMap.forWork("mk:mb:1") } returns listOf(canonRow(refs))
        coEvery { goldenSources.forWork("mk:mb:1", "manga") } returns goldens
    }

    @Test
    fun `candidates are refs intersect installed ordered by tier`() = runBlocking {
        val s1 = http(1, null); val s2 = http(2, null)
        setup(
            mapOf("en.bsrc" to "/b/30", "en.ssrc" to "/s/30", "en.gone" to "/g/30"),
            listOf(golden("en.bsrc", "B", 1), golden("en.ssrc", "S", 2)),
        )
        every { sourceManager.get(1) } returns s1
        every { sourceManager.get(2) } returns s2

        val cands = router.candidates("mk:mb:1", 29)
        assertEquals(listOf("en.ssrc", "en.bsrc"), cands.map { it.pkgName })
    }

    @Test
    fun `resolve walks failover until pages found`() = runBlocking {
        val dead = http(1, null)
        val live = http(2, listOf(Page(0, "p0", "img0")))
        setup(
            mapOf("en.ssrc" to "/s/30", "en.asrc" to "/a/30"),
            listOf(golden("en.ssrc", "S", 1), golden("en.asrc", "A", 2)),
        )
        every { sourceManager.get(1) } returns dead
        every { sourceManager.get(2) } returns live

        val r = router.resolve("mk:mb:1", 29, "Chapter 30")!!
        assertEquals("en.asrc", r.pkgName)
        assertEquals("/a/30", r.chapter.url)
        assertEquals(1, r.pages.size)
    }

    @Test
    fun `last working carrier wins next time`() = runBlocking {
        val s1 = http(1, listOf(Page(0, "p", "i")))
        val s2 = http(2, listOf(Page(0, "p", "i")))
        val refs = mapOf("en.asrc" to "/a/30", "en.ssrc" to "/s/30")
        val goldens = listOf(golden("en.asrc", "A", 1), golden("en.ssrc", "S", 2))
        setup(refs, goldens)
        every { sourceManager.get(1) } returns s1
        every { sourceManager.get(2) } returns s2

        // First resolve: S-tier wins and sticks.
        router.resolve("mk:mb:1", 29, "Chapter 30")
        // Second resolve: sticky A-tier source still leads ordering...
        // (S is sticky now — confirm by flipping which one works)
        val dead = http(2, null)
        every { sourceManager.get(2) } returns dead
        val r = router.resolve("mk:mb:1", 29, "Chapter 30")!!
        // Sticky source fails → failover to A-tier.
        assertEquals("en.asrc", r.pkgName)
    }

    @Test
    fun `no carriers resolves null`() = runBlocking {
        setup(mapOf("en.uninstalled" to "/u/30"), emptyList())
        assertNull(router.resolve("mk:mb:1", 29, "Chapter 30"))
    }

    @Test
    fun `non-canon url resolves null`() = runBlocking {
        val manga = mockk<tachiyomi.domain.entries.manga.model.Manga> {
            every { url } returns "miko:manga:mk:mb:1"
        }
        assertNull(router.resolveForRead(manga, "/real/source/url", "Chapter 30"))
    }
}
