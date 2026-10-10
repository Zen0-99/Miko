package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.model.Carrier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CanonizeWorkTest {

    private val bridge = mockk<MangaNovelIdBridge>()
    private val collector = mockk<CanonicalFeedCollector>()
    private val chapterMap = mockk<CanonicalChapterMap>()
    private val api = mockk<WorkIdentityApi>()
    private val goldenSources = mockk<GoldenSources>(relaxed = true)
    private val episodeMap = mockk<CanonicalEpisodeMap>()
    private val mikoDbManager = mockk<MikoDbManager>()
    private val animeBridge = mockk<AnimeIdBridge>()
    private val mintTelemetry = mockk<MintTelemetry>(relaxed = true)
    private val workCarriers = mockk<WorkCarriers>(relaxed = true)
    private val sourcePreferences = mockk<SourcePreferences>(relaxed = true)

    private val canonize = CanonizeWork(
        bridge, collector, chapterMap, api, goldenSources, episodeMap, mikoDbManager,
        mintTelemetry, workCarriers, sourcePreferences,
    )

    init {
        // An empty queue drains to null — the canonize stubs below
        // predate the bundled-telemetry field and match on null.
        coEvery { mintTelemetry.drain() } returns null
    }

    private val work = MangaNovelIdBridge.WorkRow(
        mikoWorkId = "mk:mb:1",
        type = "manga",
        title = "Solo Leveling",
    )

    private val showsWork = MangaNovelIdBridge.WorkRow(
        mikoWorkId = "mk:imdb:tt0903747",
        type = "series",
        title = "Breaking Bad",
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

    private fun animeFeed(
        pkg: String,
        verdict: SeriesMatcher.Decision,
        score: Double = 0.9,
    ) = CanonicalFeedCollector.Feed(
        sourceId = 1L,
        pkgName = pkg,
        tier = "S",
        url = "/anime/x",
        seriesTitle = "Breaking Bad",
        chapters = emptyList(),
        verdict = verdict,
        score = score,
        reasons = emptyList(),
        episodes = listOf(
            WorkIdentityApi.FeedEpisode(name = "Pilot", url = "/ep/1", number = 1.0),
            WorkIdentityApi.FeedEpisode(name = "Cat's in the Bag", url = "/ep/2", number = 2.0),
        ),
    )

    private fun artifactEpisode(
        epKey: String,
        type: String = "main",
        number: Double? = 1.0,
        abs: Int? = 1,
        season: Int? = 1,
        epInSeason: Int? = 1,
    ) = AnimeIdBridge.AnimeEpisode(
        anidbId = 8112,
        epKey = epKey,
        type = type,
        number = number,
        season = season,
        epInSeason = epInSeason,
        absNumber = abs,
        title = "Pilot",
        overview = "First episode.",
        image = "https://img/1.jpg",
        airDate = "2008-01-20",
        anidbAirDate = "2008-01-19",
        runtime = 58,
        rating = 8.1,
        anidbEid = 162001,
        tvdbEid = 349232,
        tvdbShowId = 81189,
        filler = false,
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
        coEvery { chapterMap.forWork("mk:mb:1") } returns listOf(mockk())
        every { sourcePreferences.canonizeRanAtFor("mk:mb:1") } returns
            System.currentTimeMillis()
        val out = canonize.run("mk:mb:1")
        assertEquals(CanonizeWork.Outcome.Fresh, out)
        coVerify(exactly = 0) { collector.collect(any(), any(), any()) }
    }

    @Test
    fun `accept feeds post with verified provenance`() = runBlocking {
        coEvery { chapterMap.forWork("mk:mb:1") } returns emptyList()
        coEvery { bridge.byWorkId("mk:mb:1") } returns work
        coEvery { collector.collect(work, "manga", any(), any(), any(), any()) } returns
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
        coEvery { chapterMap.forWork("mk:mb:1") } returns emptyList()
        coEvery { bridge.byWorkId("mk:mb:1") } returns work
        coEvery { collector.collect(work, "manga", any(), any(), any(), any()) } returns result(
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
        coEvery { chapterMap.forWork("mk:mb:1") } returns emptyList()
        coEvery { bridge.byWorkId("mk:mb:1") } returns work
        coEvery { collector.collect(work, "manga", any(), any(), any(), any()) } returns
            result(tentative = listOf(feed("en.asura", SeriesMatcher.Decision.TENTATIVE)))

        val out = canonize.run("mk:mb:1")

        assertEquals(CanonizeWork.Outcome.NoVerifiedFeeds, out)
        coVerify(exactly = 0) { api.canonize(any()) }
    }

    @Test
    fun `failure budget exceeded aborts`() = runBlocking {
        coEvery { chapterMap.forWork("mk:mb:1") } returns emptyList()
        coEvery { bridge.byWorkId("mk:mb:1") } returns work
        coEvery { collector.collect(work, "manga", any(), any(), any(), any()) } returns result(
            failed = mapOf("a" to "x", "b" to "y", "c" to "z"),
        )

        val out = canonize.run("mk:mb:1")
        assertEquals(CanonizeWork.Outcome.TooManyFailures, out)
        coVerify(exactly = 0) { api.canonize(any()) }
    }

    @Test
    fun `post failure surfaces`() = runBlocking {
        coEvery { chapterMap.forWork("mk:mb:1") } returns emptyList()
        coEvery { bridge.byWorkId("mk:mb:1") } returns work
        coEvery { collector.collect(work, "manga", any(), any(), any(), any()) } returns
            result(feeds = listOf(feed("en.mangadex", SeriesMatcher.Decision.ACCEPT)))
        coEvery { api.canonize(any()) } returns null

        val out = canonize.run("mk:mb:1")
        assertEquals(CanonizeWork.Outcome.PostFailed, out)
    }

    // ---------- shows path (19-03): artifact + carrier episode feeds ----------

    private fun stubShows(
        workId: String,
        work: MangaNovelIdBridge.WorkRow,
        artifactRows: List<AnimeIdBridge.AnimeEpisode> = emptyList(),
        collect: CanonicalFeedCollector.Result = result(),
    ) {
        coEvery { episodeMap.forWork(workId) } returns emptyList()
        coEvery { bridge.byWorkId(workId) } returns work
        every { mikoDbManager.bridge() } returns animeBridge
        every { animeBridge.episodesForImdb(any()) } returns artifactRows
        coEvery { collector.collect(work, "shows", any(), any(), any(), any()) } returns collect
        every { episodeMap.invalidate(any()) } returns Unit
        coEvery { episodeMap.prime(any()) } returns emptyList()
    }

    @Test
    fun `shows work posts mikodb map artifact feed plus carrier feeds`() = runBlocking {
        stubShows(
            "mk:imdb:tt0903747",
            showsWork,
            artifactRows = listOf(artifactEpisode("1")),
            collect = result(feeds = listOf(animeFeed("en.kitsu", SeriesMatcher.Decision.ACCEPT))),
        )
        val reqSlot = slot<List<WorkIdentityApi.EpisodeFeed>>()
        coEvery { api.canonizeEpisodes(eq("mk:imdb:tt0903747"), capture(reqSlot), any()) } returns
            WorkIdentityApi.CanonizeEpisodesResponse("mk:imdb:tt0903747", 3, 3, 0, 0)

        val out = canonize.run("mk:imdb:tt0903747", Carrier.SHOWS)

        assertEquals(CanonizeWork.Outcome.Canonized(3, 0), out)
        val feeds = reqSlot.captured
        // Artifact feed leads; carrier feeds follow.
        assertEquals(2, feeds.size)
        assertEquals("mikodb:map", feeds[0].sourceKey)
        assertTrue(feeds[0].verified)
        assertEquals(1.0, feeds[0].score)
        assertEquals("device_feed", feeds[0].provenance)
        assertEquals("en.kitsu", feeds[1].sourceKey)
        assertEquals(2, feeds[1].episodes.size)
        verify { episodeMap.invalidate("mk:imdb:tt0903747") }
    }

    @Test
    fun `artifact feed episodes carry the forge-guard structure`() = runBlocking {
        stubShows(
            "mk:imdb:tt0903747",
            showsWork,
            artifactRows = listOf(
                artifactEpisode("1"),
                artifactEpisode("S1", type = "special", number = 1.0, abs = null, season = 0, epInSeason = 1),
            ),
            collect = result(feeds = listOf(animeFeed("en.kitsu", SeriesMatcher.Decision.ACCEPT))),
        )
        val reqSlot = slot<List<WorkIdentityApi.EpisodeFeed>>()
        coEvery { api.canonizeEpisodes(eq("mk:imdb:tt0903747"), capture(reqSlot), any()) } returns
            WorkIdentityApi.CanonizeEpisodesResponse("mk:imdb:tt0903747", 4, 4, 0, 0)

        canonize.run("mk:imdb:tt0903747", Carrier.SHOWS)

        val ep = reqSlot.captured[0].episodes[0]
        assertEquals("1", ep.key)
        assertEquals("anidb:8112", ep.doc)
        assertEquals("episode", ep.kind)
        assertEquals(1, ep.abs)
        assertEquals(1, ep.season)
        assertEquals(1, ep.epInSeason)
        assertEquals("2008-01-20", ep.airDate)
        assertEquals(162001L, ep.anidbEid)
        assertEquals(349232L, ep.tvdbEid)
        assertEquals(81189L, ep.tvdbShowId)
        assertEquals(false, ep.filler)
        // Specials fold into the special kind and keep doc+coords.
        val special = reqSlot.captured[0].episodes[1]
        assertEquals("S1", special.key)
        assertEquals("special", special.kind)
        assertNull(special.abs)
        assertEquals(0, special.season)
    }

    @Test
    fun `shows provisional mint work posts carrier feeds only`() = runBlocking {
        val mintWork = showsWork.copy(mikoWorkId = "mk:m:abc123")
        stubShows(
            "mk:m:abc123",
            mintWork,
            collect = result(feeds = listOf(animeFeed("en.kitsu", SeriesMatcher.Decision.ACCEPT))),
        )
        val reqSlot = slot<List<WorkIdentityApi.EpisodeFeed>>()
        coEvery { api.canonizeEpisodes(eq("mk:m:abc123"), capture(reqSlot), any()) } returns
            WorkIdentityApi.CanonizeEpisodesResponse("mk:m:abc123", 2, 0, 0, 0)

        val out = canonize.run("mk:m:abc123", Carrier.SHOWS)

        assertEquals(CanonizeWork.Outcome.Canonized(2, 0), out)
        assertEquals(1, reqSlot.captured.size)
        assertEquals("en.kitsu", reqSlot.captured[0].sourceKey)
    }

    @Test
    fun `shows fresh episode map skips collection`() = runBlocking {
        coEvery { episodeMap.forWork("mk:imdb:tt0903747") } returns listOf(mockk())
        every { sourcePreferences.canonizeRanAtFor("mk:imdb:tt0903747") } returns
            System.currentTimeMillis()

        val out = canonize.run("mk:imdb:tt0903747", Carrier.SHOWS)

        assertEquals(CanonizeWork.Outcome.Fresh, out)
        coVerify(exactly = 0) { collector.collect(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { api.canonizeEpisodes(any(), any(), any()) }
    }

    @Test
    fun `shows zero feeds and no artifact coverage never posts`() = runBlocking {
        stubShows(
            "mk:m:abc123",
            showsWork.copy(mikoWorkId = "mk:m:abc123"),
            collect = result(),
        )

        val out = canonize.run("mk:m:abc123", Carrier.SHOWS)

        assertEquals(CanonizeWork.Outcome.NoVerifiedFeeds, out)
        coVerify(exactly = 0) { api.canonizeEpisodes(any(), any(), any()) }
    }

    @Test
    fun `shows failure budget exceeded aborts`() = runBlocking {
        stubShows(
            "mk:imdb:tt0903747",
            showsWork,
            artifactRows = listOf(artifactEpisode("1")),
            collect = result(failed = mapOf("a" to "x", "b" to "y", "c" to "z")),
        )

        val out = canonize.run("mk:imdb:tt0903747", Carrier.SHOWS)

        assertEquals(CanonizeWork.Outcome.TooManyFailures, out)
        coVerify(exactly = 0) { api.canonizeEpisodes(any(), any(), any()) }
    }
}
