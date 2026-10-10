package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.data.mikodb.SeriesMatcher.Decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Golden cases derived from `MikoDB/reports/golden-probe-v2` captures:
 * real wrong-series pages that raw-HTTP search landed on, which must
 * never contribute chapter feeds to the canonical sequence.
 */
class SeriesMatcherTest {

    private val matcher = SeriesMatcher()

    private val soloLeveling = SeriesMatcher.Query(
        titles = listOf("Solo Leveling", "Ore dake Level Up na Ken", "Na Honjaman Level Up"),
        author = "Chugong",
        year = 2016,
        totalChapters = 200.0,
        type = "manhwa",
    )
    private val naruto = SeriesMatcher.Query(
        titles = listOf("Naruto", "NARUTO"),
        author = "Masashi Kishimoto",
        year = 1999,
        totalChapters = 700.0,
        type = "manga",
    )

    @Test
    fun `exact title accept`() {
        val v = matcher.verify(
            soloLeveling,
            SeriesMatcher.Candidate(title = "Solo Leveling", author = "Chugong"),
        )
        assertEquals(Decision.ACCEPT, v.decision)
    }

    // ── Probe-captured false positives ────────────────────────────────

    @Test
    fun `asura solo max-level newbie rejected`() {
        // v2 probe: asurascans.com search "solo leveling" landed here.
        val v = matcher.verify(soloLeveling, SeriesMatcher.Candidate(title = "Solo Max-Level Newbie"))
        assertEquals(Decision.REJECT, v.decision)
    }

    @Test
    fun `rizz leveling up with the gods rejected`() {
        val v = matcher.verify(soloLeveling, SeriesMatcher.Candidate(title = "Leveling Up With The Gods"))
        assertEquals(Decision.REJECT, v.decision)
    }

    @Test
    fun `ravenscans renge to naruto rejected`() {
        val v = matcher.verify(naruto, SeriesMatcher.Candidate(title = "Renge to Naruto"))
        assertEquals(Decision.REJECT, v.decision)
    }

    @Test
    fun `solo leveling ragnarok spinoff rejected`() {
        // Real but different work — spin-off marker must kill it.
        val v = matcher.verify(soloLeveling, SeriesMatcher.Candidate(title = "Solo Leveling: Ragnarok"))
        assertEquals(Decision.REJECT, v.decision)
    }

    @Test
    fun `solo leveling arise rejected`() {
        val v = matcher.verify(soloLeveling, SeriesMatcher.Candidate(title = "Solo Leveling: Arise"))
        assertEquals(Decision.REJECT, v.decision)
    }

    @Test
    fun `unrelated listing page rejected`() {
        // Featured/default-page grabs (elftoon "Infinite Evolution From
        // Zero", manta "Under the Oak Tree") share no title at all.
        val v = matcher.verify(naruto, SeriesMatcher.Candidate(title = "Under the Oak Tree"))
        assertEquals(Decision.REJECT, v.decision)
    }

    // ── L1 identifiers ────────────────────────────────────────────────

    @Test
    fun `provider identifier match accepts regardless of title`() {
        val q = soloLeveling.copy(identifiers = mapOf("mangadex" to "32d76d19-8a05-4db0-9fc2-b0f3dae6be51"))
        val v = matcher.verify(
            q,
            SeriesMatcher.Candidate(
                title = "Completely Different Title",
                providerIds = mapOf("mangadex" to "32d76d19-8a05-4db0-9fc2-b0f3dae6be51"),
            ),
        )
        assertEquals(Decision.ACCEPT, v.decision)
        assertEquals(1.0, v.score, 0.001)
    }

    @Test
    fun `different identifier does not take L1 path`() {
        val q = soloLeveling.copy(identifiers = mapOf("mangadex" to "aaaa"))
        val v = matcher.verify(
            q,
            SeriesMatcher.Candidate(title = "Solo Leveling", providerIds = mapOf("mangadex" to "bbbb")),
        )
        // Title may still carry it to ACCEPT via L2, but the identifier
        // matchpoint must not fire.
        assertTrue(v.reasons.none { it.startsWith("identifier:") })
    }

    // ── L4 floor + margin ─────────────────────────────────────────────

    @Test
    fun `below floor rejects`() {
        val v = matcher.verify(naruto, SeriesMatcher.Candidate(title = "Nar"))
        assertEquals(Decision.REJECT, v.decision)
    }

    @Test
    fun `floor without margin is tentative`() {
        // Two near-identical candidates: correct title + a same-score
        // alt. Best clears the floor but can't beat the runner-up.
        val cands = listOf(
            SeriesMatcher.Candidate(title = "Solo Leveling"),
            SeriesMatcher.Candidate(title = "Solo Leveling"), // duplicate listing
        )
        val verdicts = matcher.verifyAll(soloLeveling, cands)
        assertEquals(Decision.TENTATIVE, verdicts[0].decision)
        assertEquals(Decision.TENTATIVE, verdicts[1].decision)
    }

    @Test
    fun `clear winner accepts when rival is weak`() {
        val cands = listOf(
            SeriesMatcher.Candidate(title = "Solo Leveling"),
            SeriesMatcher.Candidate(title = "Solo Max-Level Newbie"),
        )
        val verdicts = matcher.verifyAll(soloLeveling, cands)
        assertEquals(Decision.ACCEPT, verdicts[0].decision)
        assertEquals(Decision.REJECT, verdicts[1].decision)
    }

    // ── Medium mismatch ───────────────────────────────────────────────

    @Test
    fun `manga query rejects novel-marked candidate`() {
        val v = matcher.verify(
            naruto,
            SeriesMatcher.Candidate(title = "Naruto (Light Novel)"),
        )
        assertEquals(Decision.REJECT, v.decision)
        assertTrue(v.reasons.any { it.contains("medium") })
    }

    @Test
    fun `possessive extension is not a subtitle tail`() {
        // mk:mb:2060 canonical title is "Omniscient Reader"; every
        // provider lists it as "Omniscient Reader's Viewpoint". The
        // stripped 's leaves a lone "s" tail token — that's a
        // possessive continuation of the same work's full name, not
        // a "Title: Subtitle" tail, so it must not be downgraded
        // below the floor.
        val orv = SeriesMatcher.Query(
            titles = listOf("Omniscient Reader"),
            author = "Singsyong, UMI",
            year = 2020,
            type = "manhwa",
        )
        val v = matcher.verify(
            orv,
            SeriesMatcher.Candidate(title = "Omniscient Reader's Viewpoint"),
        )
        assertTrue(v.decision != Decision.REJECT)
    }

    @Test
    fun `curly apostrophe possessive also accepted`() {
        val orv = SeriesMatcher.Query(
            titles = listOf("Omniscient Reader"),
            type = "manhwa",
        )
        val v = matcher.verify(
            orv,
            SeriesMatcher.Candidate(title = "Omniscient Reader’s Viewpoint"),
        )
        assertTrue(v.decision != Decision.REJECT)
    }

    @Test
    fun `non-possessive prefix extension still downgraded`() {
        // "Renge to Naruto"-shape guard: prefix extras that aren't a
        // possessive must still fall under the tail rule.
        val v = matcher.verify(naruto, SeriesMatcher.Candidate(title = "Naruto Shippuden Chronicle"))
        assertTrue(v.decision == Decision.REJECT || v.score < SeriesMatcher.FLOOR)
    }

    @Test
    fun `legit subtitle does not reject`() {
        // "Naruto: Shinden" — query title is a prefix; generic tail is
        // downgraded, not hard-rejected (spin-off rules need markers).
        val v = matcher.verify(naruto, SeriesMatcher.Candidate(title = "Naruto Shinden"))
        assertTrue(v.decision != Decision.REJECT || v.score < SeriesMatcher.FLOOR)
    }

    // ── 19-05: season/part vetoes + format/year penalties ─────────────

    private val attackOnTitan = SeriesMatcher.Query(
        titles = listOf("Attack on Titan", "Shingeki no Kyojin"),
        year = 2013,
        type = "anime",
        expectedSeasons = setOf(1),
        expectedParts = setOf(1),
        format = "TV",
    )

    @Test
    fun `season mismatch vetoes when both sides assert`() {
        val v = matcher.verify(
            attackOnTitan,
            SeriesMatcher.Candidate(title = "Attack on Titan Season 2", season = 2),
        )
        assertEquals(Decision.REJECT, v.decision)
        assertTrue("veto:season" in v.reasons)
        assertEquals("season_veto", v.dominantReason())
    }

    @Test
    fun `expected season survives the sequel-marker rule`() {
        // A work covering S1+S2 keeps its "Season 2" candidate — the
        // digit is the season marker, not a sequel claim.
        val v = matcher.verify(
            attackOnTitan.copy(expectedSeasons = setOf(1, 2)),
            SeriesMatcher.Candidate(title = "Attack on Titan Season 2", season = 2),
        )
        assertTrue(v.decision != Decision.REJECT)
        assertTrue(v.reasons.none { it.startsWith("veto:") })
    }

    @Test
    fun `implicit season is a soft penalty not a veto`() {
        // No expectedSeasons (e.g. a mk:m mint) — a S2+ claim can't be
        // disproven, so it soft-penalizes instead of vetoing.
        val v = matcher.verify(
            attackOnTitan.copy(expectedSeasons = emptySet(), expectedParts = emptySet()),
            SeriesMatcher.Candidate(title = "Attack on Titan Season 2", season = 2),
        )
        assertTrue(v.reasons.none { it == "veto:season" })
        assertTrue("penalty:season-implicit" in v.reasons)
        assertEquals("season_veto", v.dominantReason())
    }

    @Test
    fun `part mismatch vetoes when both sides assert`() {
        val v = matcher.verify(
            attackOnTitan,
            SeriesMatcher.Candidate(title = "Attack on Titan Part 2", part = 2),
        )
        assertEquals(Decision.REJECT, v.decision)
        assertTrue("veto:part" in v.reasons)
        assertEquals("part_veto", v.dominantReason())
    }

    @Test
    fun `format mismatch soft-penalizes shows`() {
        // Artifact says MOVIE; the candidate page is marked "(TV)".
        val v = matcher.verify(
            attackOnTitan.copy(format = "MOVIE"),
            SeriesMatcher.Candidate(title = "Attack on Titan (TV)", format = "tv"),
        )
        assertTrue("penalty:format" in v.reasons)
        assertEquals("format", v.dominantReason())
    }

    @Test
    fun `manga format marker mismatch soft-penalizes`() {
        // Per-medium vocab: manga work vs a "(Webtoon)"-marked page.
        val v = matcher.verify(
            naruto,
            SeriesMatcher.Candidate(title = "Naruto (Webtoon)", format = "webtoon"),
        )
        assertTrue("penalty:format" in v.reasons)
        assertEquals("format", v.dominantReason())
    }

    @Test
    fun `large year gap is an explainable penalty`() {
        val v = matcher.verify(
            naruto,
            SeriesMatcher.Candidate(title = "Naruto", year = 1990),
        )
        assertTrue("penalty:year" in v.reasons)
        assertEquals("year", v.dominantReason())
    }

    @Test
    fun `books candidates never assert season`() {
        // The medium gate lives at candidate construction — books feeds
        // leave season/part null, so "Book 2" titles can't veto or
        // penalty even with expected data absent.
        val v = matcher.verify(
            naruto.copy(type = "novel", expectedSeasons = setOf(1)),
            SeriesMatcher.Candidate(title = "Naruto Book 2"),
        )
        assertTrue(v.reasons.none { it == "veto:season" || it == "penalty:season-implicit" })
    }

    @Test
    fun `spinoff reject still dominates season signals`() {
        // Ragnarok regression: the derivative reject outranks a
        // co-asserted season difference only when the veto also fired;
        // without a season marker on the title, spinoff stands alone.
        val v = matcher.verify(
            soloLeveling.copy(expectedSeasons = setOf(1)),
            SeriesMatcher.Candidate(title = "Solo Leveling: Ragnarok"),
        )
        assertEquals(Decision.REJECT, v.decision)
        assertEquals("spinoff", v.dominantReason())
    }

    @Test
    fun `dominantReason precedence veto beats reject`() {
        val v = SeriesMatcher.Verdict(
            Decision.REJECT,
            0.0,
            null,
            listOf("veto:season", "reject:spinoff:ragnarok"),
        )
        assertEquals("season_veto", v.dominantReason())
    }

    @Test
    fun `dominantReason thin margin`() {
        val cands = listOf(
            SeriesMatcher.Candidate(title = "Solo Leveling"),
            SeriesMatcher.Candidate(title = "Solo Leveling"),
        )
        val verdicts = matcher.verifyAll(soloLeveling, cands)
        assertEquals("thin_margin", verdicts[0].dominantReason())
    }

    @Test
    fun `dominantReason identifier`() {
        val v = SeriesMatcher.Verdict(
            Decision.ACCEPT,
            1.0,
            null,
            listOf("identifier:mangadex"),
        )
        assertEquals("identifier", v.dominantReason())
    }

    @Test
    fun `dominantReason empty on clean accept`() {
        val v = matcher.verify(
            soloLeveling,
            SeriesMatcher.Candidate(title = "Solo Leveling", author = "Chugong"),
        )
        assertEquals(null, v.dominantReason())
    }

    // ── parseSeasonPart / formatMarker ────────────────────────────────

    @Test
    fun `parseSeasonPart extracts season forms`() {
        assertEquals(2 to null, SeriesMatcher.parseSeasonPart("Title S2"))
        assertEquals(2 to null, SeriesMatcher.parseSeasonPart("Title S2E05"))
        assertEquals(2 to null, SeriesMatcher.parseSeasonPart("Title Season 2"))
        assertEquals(2 to null, SeriesMatcher.parseSeasonPart("Title 2nd Season"))
        assertEquals(2 to null, SeriesMatcher.parseSeasonPart("Title Cours 2"))
        assertEquals(3 to null, SeriesMatcher.parseSeasonPart("Title Season III"))
        assertEquals(null to null, SeriesMatcher.parseSeasonPart("Title"))
    }

    @Test
    fun `parseSeasonPart extracts part forms`() {
        assertEquals(null to 3, SeriesMatcher.parseSeasonPart("Title Part 3"))
        assertEquals(null to 3, SeriesMatcher.parseSeasonPart("Title Part III"))
        assertEquals(null to 2, SeriesMatcher.parseSeasonPart("Title Pt 2"))
        assertEquals(2 to 3, SeriesMatcher.parseSeasonPart("Title Season 2 Part 3"))
    }

    @Test
    fun `formatMarker reads title markers`() {
        assertEquals("tv", SeriesMatcher.formatMarker("Title (TV)"))
        assertEquals("movie", SeriesMatcher.formatMarker("Title (Movie)"))
        assertEquals("ova", SeriesMatcher.formatMarker("Title OVA"))
        assertEquals("ona", SeriesMatcher.formatMarker("Title ONA"))
        assertEquals("novel", SeriesMatcher.formatMarker("Title (Novel)"))
        assertEquals("webtoon", SeriesMatcher.formatMarker("Title (Webtoon)"))
        assertEquals("audiobook", SeriesMatcher.formatMarker("Title (Audiobook)"))
        assertEquals(null, SeriesMatcher.formatMarker("Title"))
    }
}
