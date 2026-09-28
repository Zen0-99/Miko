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
    fun `legit subtitle does not reject`() {
        // "Naruto: Shinden" — query title is a prefix; generic tail is
        // downgraded, not hard-rejected (spin-off rules need markers).
        val v = matcher.verify(naruto, SeriesMatcher.Candidate(title = "Naruto Shinden"))
        assertTrue(v.decision != Decision.REJECT || v.score < SeriesMatcher.FLOOR)
    }
}
