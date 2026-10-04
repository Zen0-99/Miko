package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.domain.ui.model.Carrier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CanonizeWorkTest {

    private val bridge = mockk<MangaNovelIdBridge>()
    private val collector = mockk<CanonicalFeedCollector>()
    private val chapterMap = mockk<CanonicalChapterMap>()
    private val api = mockk<WorkIdentityApi>()
    private val goldenSources = mockk<GoldenSources>(relaxed = true)

    private val canonize = CanonizeWork(bridge, collector, chapterMap, api, goldenSources)

    private val work = MangaNovelIdBridge.WorkRow(
        mikoWorkId = "mk:mb:1",
        type = "manga",
        title = "Solo Leveling",
    )

    private fun feed(
        pkg: String,
        verdict: SeriesMatcher.Decision,
        score: Double = 0.9,
    ) = CanonicalFeedCollector.Feed(
        sourceId = 1L,
        pkgName = pkg,
        tier = "S",
        url = "/manga/x",
        seriesTitle = "Solo Leveling",
        chapters = listOf(
            WorkIdentityApi.FeedChapter("Chapter 1", "/ch/1"),
            WorkIdentityApi.FeedChapter("Chapter 2", "/ch/2"),
        ),
        verdict = verdict,
        score = score,
        reasons = emptyList(),
    )

    private fun result(
        feeds: List<CanonicalFeedCollector.Feed> = emptyList(),
        tentative: List<CanonicalFeedCollector.Feed> = emptyList(),
        failed: Map<String, String> = emptyMap(),
    ) = CanonicalFeedCollector.Result(
        feeds = feeds,
        tentative = tentative,
        rejected = emptyList(),
        failed = failed,
    )

    @Test
    fun `fresh cache skips collection entirely`() = runBlocking {
        every { chapterMap.isStale("mk:mb:1") } returns false
        val out = canonize.run("mk:mb:1")
        assertEquals(CanonizeWork.Outcome.Fresh, out)
        coVerify(exactly = 0) { collector.collect(any(), any(), any()) }
    }

    @Test
    fun `shows medium is rejected at the entry — canonize is chapter-shaped`() = runBlocking {
        val out = canonize.run("mk:imdb:tt0903747", Carrier.SHOWS)
        assertEquals(CanonizeWork.Outcome.UnsupportedMedium, out)
        coVerify(exactly = 0) { collector.collect(any(), any(), any()) }
        coVerify(exactly = 0) { api.canonize(any()) }
    }

    @Test
    fun `accept feeds post with verified provenance`() = runBlocking {
        every { chapterMap.isStale("mk:mb:1") } returns true
        coEvery { bridge.byWorkId("mk:mb:1") } returns work
        coEvery { collector.collect(work, "manga") } returns
            result(feeds = listOf(feed("en.mangadex", SeriesMatcher.Decision.ACCEPT)))
        val reqSlot = slot<WorkIdentityApi.CanonizeRequest>()
        coEvery { api.canonize(capture(reqSlot)) } returns
            WorkIdentityApi.CanonizeResponse("mk:mb:1", count = 2, verified = 2, skipped = 0, conflicts = 0)
        every { chapterMap.invalidate(any()) } returns Unit

        val out = canonize.run("mk:mb:1")

        assertEquals(CanonizeWork.Outcome.Canonized(2, 0), out)
        val req = reqSlot.captured
        assertEquals("mk:mb:1", req.work_id)
        assertEquals(1, req.feeds.size)
        assertTrue(req.feeds[0].verified)
        assertEquals("en.mangadex", req.feeds[0].source_key)
        assertEquals(2, req.feeds[0].chapters.size)
    }

    @Test
    fun `tentative feed submits flagged for review`() = runBlocking {
        every { chapterMap.isStale("mk:mb:1") } returns true
        coEvery { bridge.byWorkId("mk:mb:1") } returns work
        coEvery { collector.collect(work, "manga") } returns result(
            feeds = listOf(feed("en.mangadex", SeriesMatcher.Decision.ACCEPT)),
            tentative = listOf(feed("en.asura", SeriesMatcher.Decision.TENTATIVE, 0.83)),
        )
        val reqSlot = slot<WorkIdentityApi.CanonizeRequest>()
        coEvery { api.canonize(capture(reqSlot)) } returns
            WorkIdentityApi.CanonizeResponse("mk:mb:1", 3, 3, 0, 0)
        every { chapterMap.invalidate(any()) } returns Unit

        val out = canonize.run("mk:mb:1")

        assertEquals(CanonizeWork.Outcome.Canonized(3, 1), out)
        val tent = reqSlot.captured.feeds.first { it.source_key == "en.asura" }
        assertFalse(tent.verified)
        assertEquals(0.83, tent.score)
    }

    @Test
    fun `zero accept feeds never posts`() = runBlocking {
        every { chapterMap.isStale("mk:mb:1") } returns true
        coEvery { bridge.byWorkId("mk:mb:1") } returns work
        coEvery { collector.collect(work, "manga") } returns
            result(tentative = listOf(feed("en.asura", SeriesMatcher.Decision.TENTATIVE)))

        val out = canonize.run("mk:mb:1")

        assertEquals(CanonizeWork.Outcome.NoVerifiedFeeds, out)
        coVerify(exactly = 0) { api.canonize(any()) }
    }

    @Test
    fun `failure budget exceeded aborts`() = runBlocking {
        every { chapterMap.isStale("mk:mb:1") } returns true
        coEvery { bridge.byWorkId("mk:mb:1") } returns work
        coEvery { collector.collect(work, "manga") } returns result(
            failed = mapOf("a" to "x", "b" to "y", "c" to "z"),
        )

        val out = canonize.run("mk:mb:1")
        assertEquals(CanonizeWork.Outcome.TooManyFailures, out)
        coVerify(exactly = 0) { api.canonize(any()) }
    }

    @Test
    fun `post failure surfaces`() = runBlocking {
        every { chapterMap.isStale("mk:mb:1") } returns true
        coEvery { bridge.byWorkId("mk:mb:1") } returns work
        coEvery { collector.collect(work, "manga") } returns
            result(feeds = listOf(feed("en.mangadex", SeriesMatcher.Decision.ACCEPT)))
        coEvery { api.canonize(any()) } returns null

        val out = canonize.run("mk:mb:1")
        assertEquals(CanonizeWork.Outcome.PostFailed, out)
    }
}
