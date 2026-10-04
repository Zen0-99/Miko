package eu.kanade.tachiyomi.data.mikodb

import app.cash.sqldelight.Query
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.comments.CommentsApi
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager

class CanonicalChapterRouterTest {

    private val chapterMap = mockk<CanonicalChapterMap>()
    private val goldenSources = mockk<GoldenSources>()
    private val sourceManager = mockk<MangaSourceManager>()
    private val novelSourceManager = mockk<NovelSourceManager>()
    private val workCarriers = mockk<WorkCarriers>()
    private val bridge = mockk<MangaNovelIdBridge>()
    private val matcher = mockk<SeriesMatcher>()
    private val commentsApi = mockk<CommentsApi>()
    private val handler = mockk<MangaDatabaseHandler>()
    private val sourcePreferences = mockk<SourcePreferences>()
    private val db = mockk<Database>(relaxed = true)

    private val router = CanonicalChapterRouter(
        chapterMap, goldenSources, sourceManager, novelSourceManager,
        workCarriers, bridge, matcher, commentsApi, handler, sourcePreferences,
    )

    init {
        every { sourcePreferences.preferredCarrierFor(any()) } returns null
        every { sourcePreferences.preferredChapterCarrier(any(), any()) } returns null
        coEvery {
            handler.awaitOneOrNull(any<Boolean>(), any<suspend Database.() -> Query<Any>>())
        } returns null
        coEvery {
            handler.await<Any?>(any<Boolean>(), any<suspend Database.() -> Any?>())
        } coAnswers { secondArg<suspend Database.() -> Any?>().invoke(db) }
    }

    private fun canonRow(refs: Map<String, String>) = CanonicalChapterMap.CanonChapter(
        seq = 29, number = 30.0, part = null, volume = null,
        kind = ChapterNumberParser.Kind.CHAPTER, label = "Chapter 30",
        confidence = "verified", sources = refs.keys.toList(), refs = refs,
    )

    private fun golden(pkg: String, tier: String, sourceId: Long) = carrier(pkg, tier, sourceId)

    private fun http(id: Long, pages: List<Page>?) = mockk<HttpSource> {
        every { this@mockk.id } returns id
        if (pages == null) {
            coEvery { getPageList(any()) } throws RuntimeException("404")
        } else {
            coEvery { getPageList(any()) } returns pages
        }
    }

    private fun setup(refs: Map<String, String>, carriers: List<WorkCarriers.Carrier>) {
        coEvery { chapterMap.forWork("mk:mb:1") } returns listOf(canonRow(refs))
        coEvery { workCarriers.forWork("mk:mb:1", "manga") } returns
            WorkCarriers.Matrix("mk:mb:1", carriers, fetched = true)
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

    // ---------- 16-04: carrier preference + bound-source expansion ----------

    private fun carrier(pkg: String, tier: String, sourceId: Long) = WorkCarriers.Carrier(
        pkgName = pkg, displayName = pkg, tier = tier, providerRef = "/series/$pkg",
        provenance = "crawler", lastSeenAt = null, installed = true, enabled = true,
        sourceIds = listOf(sourceId), iconUrl = null, lang = "en", installable = false,
    )

    private fun verdict(decision: SeriesMatcher.Decision) = SeriesMatcher.Verdict(
        decision = decision, score = 1.0, margin = null, reasons = emptyList(),
    )

    private fun workRow() = MangaNovelIdBridge.WorkRow(
        mikoWorkId = "mk:mb:1", type = "manga", title = "Solo Leveling",
    )

    private fun expandedHttp(id: Long, title: String, chapters: List<SChapter>) = mockk<HttpSource> {
        every { this@mockk.id } returns id
        coEvery { getMangaDetails(any()) } answers {
            SManga.create().apply { this.title = title }
        }
        coEvery { getChapterList(any()) } returns chapters
    }

    private fun expansionSetup(
        pkg: String,
        sourceId: Long,
        chapters: List<SChapter>,
        title: String = "Solo Leveling",
    ): HttpSource {
        setup(mapOf("en.uninstalled" to "/u/30"), emptyList())
        coEvery { workCarriers.forWork("mk:mb:1", "manga") } returns
            WorkCarriers.Matrix("mk:mb:1", listOf(carrier(pkg, "A", sourceId)), fetched = true)
        coEvery { bridge.byWorkId("mk:mb:1") } returns workRow()
        val src = expandedHttp(sourceId, title, chapters)
        every { sourceManager.get(sourceId) } returns src
        return src
    }

    @Test
    fun `preferred carrier outranks tier`() = runBlocking {
        setup(
            mapOf("en.bsrc" to "/b/30", "en.ssrc" to "/s/30"),
            listOf(golden("en.bsrc", "B", 1), golden("en.ssrc", "S", 2)),
        )
        every { sourceManager.get(1) } returns http(1, null)
        every { sourceManager.get(2) } returns http(2, null)
        every { sourcePreferences.preferredCarrierFor("mk:mb:1") } returns "en.bsrc"

        val cands = router.candidates("mk:mb:1", 29)
        assertEquals(listOf("en.bsrc", "en.ssrc"), cands.map { it.pkgName })
    }

    @Test
    fun `per-chapter override outranks work preference`() = runBlocking {
        setup(
            mapOf("en.bsrc" to "/b/30", "en.ssrc" to "/s/30"),
            listOf(golden("en.bsrc", "B", 1), golden("en.ssrc", "S", 2)),
        )
        every { sourceManager.get(1) } returns http(1, null)
        every { sourceManager.get(2) } returns http(2, null)
        every { sourcePreferences.preferredCarrierFor("mk:mb:1") } returns "en.bsrc"
        every { sourcePreferences.preferredChapterCarrier("mk:mb:1", 29) } returns "en.ssrc"

        val cands = router.candidates("mk:mb:1", 29)
        assertEquals(listOf("en.ssrc", "en.bsrc"), cands.map { it.pkgName })
    }

    @Test
    fun `peekCarrier returns top direct candidate without expansion`() = runBlocking {
        setup(
            mapOf("en.bsrc" to "/b/30", "en.ssrc" to "/s/30"),
            listOf(golden("en.bsrc", "B", 1), golden("en.ssrc", "S", 2)),
        )
        every { sourceManager.get(1) } returns http(1, null)
        every { sourceManager.get(2) } returns http(2, null)

        assertEquals("en.ssrc", router.peekCarrier("mk:mb:1", 29))
        // Direct-candidate hit — bound-source expansion never ran.
        coVerify(exactly = 0) { bridge.byWorkId(any()) }
    }

    @Test
    fun `bound-source expansion aligns and routes`() = runBlocking {
        val ch = SChapter.create().apply {
            name = "Chapter 30"
            url = "/new/30"
            chapter_number = 30f
        }
        expansionSetup("en.newsrc", 3, listOf(ch))
        every { matcher.verify(any(), any(), any()) } returns verdict(SeriesMatcher.Decision.ACCEPT)
        every { chapterMap.bind("Chapter 30", any()) } returns canonRow(emptyMap())
        coEvery {
            commentsApi.alignChapters(any(), any(), any(), any(), any())
        } returns CommentsApi.Result.Ok(CommentsApi.AlignResponse("aligned"))

        val cands = router.candidates("mk:mb:1", 29)
        assertEquals(listOf("en.newsrc"), cands.map { it.pkgName })
        assertEquals("/new/30", cands.first().chapterUrl)
    }

    @Test
    fun `expansion disqualified when identity rejected`() = runBlocking {
        val ch = SChapter.create().apply {
            name = "Chapter 30"
            url = "/new/30"
            chapter_number = 30f
        }
        expansionSetup("en.newsrc", 3, listOf(ch))
        every { matcher.verify(any(), any(), any()) } returns verdict(SeriesMatcher.Decision.REJECT)

        assertEquals(emptyList<String>(), router.candidates("mk:mb:1", 29).map { it.pkgName })
        coVerify(exactly = 0) { commentsApi.alignChapters(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `misaligned verdict disqualifies the carrier`() = runBlocking {
        val ch = SChapter.create().apply {
            name = "Chapter 30"
            url = "/new/30"
            chapter_number = 30f
        }
        expansionSetup("en.newsrc", 3, listOf(ch))
        every { matcher.verify(any(), any(), any()) } returns verdict(SeriesMatcher.Decision.ACCEPT)
        every { chapterMap.bind("Chapter 30", any()) } returns canonRow(emptyMap())
        coEvery {
            commentsApi.alignChapters(any(), any(), any(), any(), any())
        } returns CommentsApi.Result.Ok(CommentsApi.AlignResponse("misaligned"))

        assertEquals(emptyList<String>(), router.candidates("mk:mb:1", 29).map { it.pkgName })
    }
}
