package eu.kanade.tachiyomi.data.mikodb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeCoverageTest {

    private fun canon(
        seq: Int,
        kind: String = "episode",
        abs: Int? = null,
        season: Int? = null,
        epInSeason: Int? = null,
        number: Double? = null,
        sources: List<String> = emptyList(),
    ) = CanonicalEpisodeMap.CanonEpisode(
        seq = seq,
        kind = kind,
        number = number,
        abs = abs,
        season = season,
        epInSeason = epInSeason,
        epKey = null,
        doc = null,
        label = "E$seq",
        airDate = null,
        dateUpload = null,
        image = null,
        overview = null,
        runtime = null,
        anidbEid = null,
        tvdbEid = null,
        tvdbShowId = null,
        filler = false,
        confidence = "verified",
        sources = sources,
        refs = emptyMap(),
    )

    private fun mapping(
        anilistId: Long? = null,
        malId: Long? = null,
        anidbId: Long? = null,
    ) = AnimeIdBridge.AnimeMapping(
        malId = malId,
        malDubId = null,
        anilistId = anilistId,
        kitsuId = null,
        anidbId = anidbId,
        simklId = null,
        tvdbId = null,
        tmdbId = null,
        imdbId = "tt0903747",
        traktId = null,
        title = null,
        tvdbSeason = null,
        tvdbPart = null,
        duration = null,
        score = null,
        status = null,
        animeMediaType = "TV",
        animeMediaEpisodes = null,
        globalMediaEpisodes = null,
        dubStatus = null,
    )

    private fun artEp(
        number: Double?,
        abs: Int?,
        type: String = "main",
        anidbId: Long = 8112,
    ) = AnimeIdBridge.AnimeEpisode(
        anidbId = anidbId,
        epKey = number?.toInt()?.toString() ?: "S1",
        type = type,
        number = number,
        season = if (type == "main") 1 else 0,
        epInSeason = number?.toInt(),
        absNumber = abs,
        title = null,
        overview = null,
        image = null,
        airDate = null,
        anidbAirDate = null,
        runtime = null,
        rating = null,
        anidbEid = null,
        tvdbEid = null,
        tvdbShowId = null,
        filler = false,
    )

    // ---------- unserved / served ----------

    @Test
    fun `unserved keeps artifact-only rows and rows no readable carrier serves`() {
        val canon = listOf(
            canon(seq = 1, abs = 1, sources = listOf("mikodb:map")),
            canon(seq = 2, abs = 2, sources = listOf("mikodb:map", "en.kitsu")),
            canon(seq = 3, abs = 3, sources = listOf("en.anilist", "en.kitsu")),
            canon(seq = 4, abs = 4, sources = listOf("en.uninstalled")),
        )
        val readable = setOf("en.kitsu", "en.anilist")

        assertEquals(
            listOf(1, 4),
            EpisodeCoverage.unserved(canon, readable).map { it.seq },
        )
        assertEquals(
            listOf(2, 3),
            EpisodeCoverage.served(canon, readable).map { it.seq },
        )
    }

    @Test
    fun `unserved never counts mikodb-map as a serving carrier`() {
        // mikodb:map in readablePkgs by mistake must not flip a row served.
        val canon = listOf(canon(seq = 1, abs = 1, sources = listOf("mikodb:map")))
        assertEquals(1, EpisodeCoverage.unserved(canon, setOf("mikodb:map")).size)
    }

    // ---------- boundEntry ----------

    @Test
    fun `boundEntry routes anilist and mal refs through idType`() {
        val rows = listOf(
            mapping(anilistId = 11061, malId = 1535, anidbId = 8112),
            mapping(anilistId = 22, malId = 44, anidbId = 9999),
        )
        assertEquals(
            8112L,
            EpisodeCoverage.boundEntry(rows, "11061", "anilist")?.anidbId,
        )
        assertEquals(
            8112L,
            EpisodeCoverage.boundEntry(rows, "1535", "mal")?.anidbId,
        )
        // Compound vocabulary tries anilist first, then mal.
        assertEquals(
            8112L,
            EpisodeCoverage.boundEntry(rows, "1535", "anilist+mal")?.anidbId,
        )
        // The same ref under the other entry resolves that entry.
        assertEquals(
            9999L,
            EpisodeCoverage.boundEntry(rows, "22", "anilist")?.anidbId,
        )
    }

    @Test
    fun `boundEntry returns null for unmappable refs and vocabularies`() {
        val rows = listOf(mapping(anilistId = 11061, anidbId = 8112))
        assertNull(EpisodeCoverage.boundEntry(rows, null, "anilist"))
        assertNull(EpisodeCoverage.boundEntry(rows, "slug-style-url", "anilist"))
        assertNull(EpisodeCoverage.boundEntry(rows, "11061", "title"))
        assertNull(EpisodeCoverage.boundEntry(rows, "11061", null))
        assertNull(EpisodeCoverage.boundEntry(rows, "99999", "anilist"))
    }

    // ---------- canonicalizeNumbers ----------

    @Test
    fun `canonicalizeNumbers maps carrier numbers to abs via main rows`() {
        val entryRows = listOf(
            artEp(number = 1.0, abs = 1),
            artEp(number = 2.0, abs = 2),
            artEp(number = 1.0, abs = null, type = "special"),
        )
        assertEquals(
            mapOf(1.0 to 1, 2.0 to 2),
            EpisodeCoverage.canonicalizeNumbers(listOf(1.0, 2.0), entryRows),
        )
    }

    @Test
    fun `canonicalizeNumbers leaves out-of-range and null-abs numbers unmapped`() {
        val entryRows = listOf(
            artEp(number = 1.0, abs = 1),
            artEp(number = 2.0, abs = null), // no upstream abs — must not map
            artEp(number = 1.0, abs = 50, type = "special"), // specials never mint abs
        )
        val out = EpisodeCoverage.canonicalizeNumbers(listOf(1.0, 2.0, 3.0, 99.0), entryRows)
        assertEquals(mapOf(1.0 to 1), out)
        assertTrue(out.keys.none { it == 2.0 || it == 3.0 || it == 99.0 })
    }
}
