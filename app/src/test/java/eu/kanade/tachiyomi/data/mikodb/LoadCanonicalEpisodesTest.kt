package eu.kanade.tachiyomi.data.mikodb

import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.repository.EpisodeRepository

class LoadCanonicalEpisodesTest {

    private val episodeRepository = mockk<EpisodeRepository>()

    // Real CanonicalEpisodeMap — bind/bindAbs never touch the API.
    private val episodeMap = CanonicalEpisodeMap(mockk())
    private val loader = LoadCanonicalEpisodes(episodeRepository, episodeMap)

    private val anime = Anime.create().copy(id = 42L, title = "Breaking Bad")

    private fun canon(
        seq: Int,
        kind: String = "episode",
        number: Double? = null,
        abs: Int? = null,
        season: Int? = null,
        epInSeason: Int? = null,
        label: String = "",
        dateUpload: Long? = null,
        image: String? = null,
        overview: String? = null,
        filler: Boolean = false,
    ) = CanonicalEpisodeMap.CanonEpisode(
        seq = seq,
        kind = kind,
        number = number,
        abs = abs,
        season = season,
        epInSeason = epInSeason,
        epKey = null,
        doc = null,
        label = label,
        airDate = null,
        dateUpload = dateUpload,
        image = image,
        overview = overview,
        runtime = null,
        anidbEid = null,
        tvdbEid = null,
        tvdbShowId = null,
        filler = filler,
        confidence = "verified",
        sources = listOf("mikodb:map"),
        refs = emptyMap(),
    )

    private fun local(
        id: Long,
        number: Double,
        scanlator: String? = null,
        name: String = "Episode $number",
        seen: Boolean = false,
        bookmark: Boolean = false,
        fillermark: Boolean = false,
        lastSecondSeen: Long = 0,
        totalSeconds: Long = 0,
        dateUpload: Long = 0L,
        summary: String? = null,
        previewUrl: String? = null,
        episodeType: String? = null,
        canonicalFiller: Boolean? = null,
    ) = Episode.create().copy(
        id = id,
        animeId = anime.id,
        name = name,
        episodeNumber = number,
        scanlator = scanlator,
        seen = seen,
        bookmark = bookmark,
        fillermark = fillermark,
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        dateUpload = dateUpload,
        summary = summary,
        previewUrl = previewUrl,
        episodeType = episodeType,
        canonicalFiller = canonicalFiller,
    )

    private fun captureUpdates(): CapturingSlot<List<EpisodeUpdate>> {
        val updates = slot<List<EpisodeUpdate>>()
        coEvery { episodeRepository.updateAllEpisodes(capture(updates)) } returns Unit
        return updates
    }

    @Test
    fun `canon row writes all canonical fields and preserves the rating suffix`() = runBlocking {
        val updates = captureUpdates()

        val n = loader.apply(
            anime,
            listOf(
                // Stray whitespace in the stored tag triggers the
                // rewrite; the `|★` rating suffix must survive verbatim.
                local(
                    id = 10,
                    number = 14.0,
                    scanlator = "Season 5 |★9.5",
                    name = "Episode 14",
                    dateUpload = 1L,
                ),
            ),
            listOf(
                canon(
                    seq = 13, kind = "episode", number = 14.0, abs = 62,
                    season = 5, epInSeason = 14,
                    label = "Ozymandias", dateUpload = 1_379_300_400_000L,
                    image = "https://img/ozy.jpg", overview = "Walt goes into hiding.",
                ),
            ),
        )

        assertEquals(1, n)
        val u = updates.captured.single()
        assertEquals(10L, u.id)
        assertEquals("Ozymandias", u.name)
        assertEquals(1_379_300_400_000L, u.dateUpload)
        assertEquals("Walt goes into hiding.", u.summary)
        assertEquals("https://img/ozy.jpg", u.previewUrl)
        assertEquals("Season 5|★9.5", u.scanlator)
        assertEquals("main", u.episodeType)
        assertEquals(false, u.canonicalFiller)
    }

    @Test
    fun `user-state fields are never part of a hydration update`() = runBlocking {
        val updates = captureUpdates()

        loader.apply(
            anime,
            listOf(
                local(
                    id = 11,
                    number = 1.0,
                    scanlator = "Season 1",
                    seen = true,
                    bookmark = true,
                    fillermark = true,
                    lastSecondSeen = 120,
                    totalSeconds = 1400,
                ),
            ),
            listOf(
                canon(
                    seq = 1,
                    kind = "episode",
                    abs = 1,
                    season = 1,
                    epInSeason = 1,
                    label = "Pilot",
                    filler = true,
                ),
            ),
        )

        val u = updates.captured.single()
        assertEquals(true, u.canonicalFiller)
        // User-owned state stays out of the update entirely.
        assertNull(u.seen)
        assertNull(u.bookmark)
        assertNull(u.fillermark)
        assertNull(u.lastSecondSeen)
        assertNull(u.totalSeconds)
        assertNull(u.episodeNumber)
        assertNull(u.url)
        assertNull(u.sourceOrder)
        assertNull(u.animeId)
    }

    @Test
    fun `special and nc canon rows map their episode types`() = runBlocking {
        val updates = captureUpdates()

        val n = loader.apply(
            anime,
            listOf(
                local(id = 12, number = 1.0, scanlator = "Season 0 |★8.0", name = "Extra 1"),
                local(id = 13, number = 2.0, scanlator = "Season 0", name = "Extra 2"),
            ),
            listOf(
                canon(seq = 20, kind = "special", number = 1.0, season = 0, epInSeason = 1, label = "Minisode"),
                canon(seq = 21, kind = "nc", number = 2.0, season = 0, epInSeason = 2, label = "NC OP"),
            ),
        )

        assertEquals(2, n)
        val byId = updates.captured.associateBy { it.id }
        assertEquals("special", byId[12L]?.episodeType)
        assertEquals("nc", byId[13L]?.episodeType)
        assertEquals("Minisode", byId[12L]?.name)
        assertEquals("Season 0|★8.0", byId[12L]?.scanlator)
    }

    @Test
    fun `season-0 locals never fall back into main abs space`() = runBlocking {
        val n = loader.apply(
            anime,
            // Cinemeta specials row — no season-0 canon row exists, and
            // main abs=1 must not claim it.
            listOf(local(id = 14, number = 1.0, scanlator = "Season 0", name = "OVA")),
            listOf(canon(seq = 1, kind = "episode", abs = 1, season = 1, epInSeason = 1, label = "Pilot")),
        )
        assertEquals(0, n)
        coVerify(exactly = 0) { episodeRepository.updateAllEpisodes(any()) }
    }

    @Test
    fun `empty canon is a no-op`() = runBlocking {
        val n = loader.apply(anime, listOf(local(15, 1.0, "Season 1")), emptyList())
        assertEquals(0, n)
        coVerify(exactly = 0) { episodeRepository.updateAllEpisodes(any()) }
        coVerify(exactly = 0) { episodeRepository.removeEpisodesWithIds(any()) }
    }

    @Test
    fun `unmatched local rows are untouched`() = runBlocking {
        val n = loader.apply(
            anime,
            // Season 7 has no canon row; abs fallback must not grab abs=1.
            listOf(local(id = 16, number = 1.0, scanlator = "Season 7", name = "Leftover")),
            listOf(canon(seq = 1, kind = "episode", abs = 1, season = 1, epInSeason = 1, label = "Pilot")),
        )
        assertEquals(0, n)
        coVerify(exactly = 0) { episodeRepository.updateAllEpisodes(any()) }
    }

    @Test
    fun `fully-matching rows produce no writes`() = runBlocking {
        val n = loader.apply(
            anime,
            listOf(
                local(
                    id = 17, number = 3.0, scanlator = "Season 1|★9.0",
                    name = "Fly", dateUpload = 127L,
                    summary = "ov", previewUrl = "img",
                    episodeType = "main", canonicalFiller = false,
                ),
            ),
            listOf(
                canon(
                    seq = 3, kind = "episode", abs = 3, season = 1, epInSeason = 3,
                    label = "Fly", dateUpload = 127L, image = "img", overview = "ov",
                ),
            ),
        )
        assertEquals(0, n)
        coVerify(exactly = 0) { episodeRepository.updateAllEpisodes(any()) }
    }

    @Test
    fun `seasonless local binds the canonical abs row`() = runBlocking {
        val updates = captureUpdates()

        val n = loader.apply(
            anime,
            listOf(local(id = 18, number = 1.0, scanlator = null, name = "Movie")),
            listOf(canon(seq = 1, kind = "episode", abs = 1, season = 1, epInSeason = 1, label = "The Movie")),
        )

        assertEquals(1, n)
        val u = updates.captured.single()
        assertEquals("The Movie", u.name)
        assertEquals("main", u.episodeType)
    }

    @Test
    fun `canon season lands on a seasonless scanlator`() = runBlocking {
        val updates = captureUpdates()

        loader.apply(
            anime,
            // Group-name scanlator — parseSeason nulls, but canon knows
            // the season, so the carrier is still written.
            listOf(local(id = 19, number = 2.0, scanlator = "[SubsPlease]", name = "Ep 2")),
            listOf(canon(seq = 2, kind = "episode", abs = 2, season = 1, epInSeason = 2, label = "Cat's in the Bag")),
        )

        val u = updates.captured.single()
        assertEquals("Season 1", u.scanlator)
    }
}
