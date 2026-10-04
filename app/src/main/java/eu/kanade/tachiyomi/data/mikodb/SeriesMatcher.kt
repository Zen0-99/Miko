package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.metadata.stream.TitleMatcher

/**
 * Series disambiguation gate (Phase 13-01) — decides whether a
 * source-side series page is the canonical work before its chapter
 * feed may contribute to `canonize`.
 *
 * Layered model ported from entity-resolution systems:
 *  1. Identifier matchpoints (OpenLibrary DCB clustering): shared
 *     provider/external id → hard accept, title math skipped.
 *  2. Weighted-field score (beets Distance / Picard weights): title
 *     similarity + TF-adjusted token overlap (Splink) + author/artist +
 *     year ±2 + chapter-count sanity.
 *  3. Domain negative rules (comic_matcher): spin-off/subtitle markers,
 *     sequel-number mismatch → reject or heavy downgrade.
 *  4. Floor + margin verdict (Picard): best must clear FLOOR and beat
 *     the runner-up by MARGIN, else TENTATIVE (caller flags review).
 *
 * Probe-proven hazards this guards against (reports/golden-probe-v2):
 * "Solo Leveling" → "Solo Max-Level Newbie", "Naruto" → "Renge to
 * Naruto", "Solo Leveling" → "Solo Leveling: Ragnarok" (a real but
 * *different* work), and listing/featured-page grabs that share no
 * title at all.
 *
 * Pure Kotlin, no I/O — candidates are plain data so the golden cases
 * run on the JVM test harness.
 */
class SeriesMatcher {

    enum class Decision { ACCEPT, TENTATIVE, REJECT }

    data class Verdict(
        val decision: Decision,
        val score: Double,
        val margin: Double?,
        val reasons: List<String>,
    )

    /** Source-side series details, normalized for comparison. */
    data class Candidate(
        val title: String,
        val altTitles: List<String> = emptyList(),
        val author: String? = null,
        val artist: String? = null,
        val year: Long? = null,
        val chapterCount: Int? = null,
        /** Provider ids extractable from the series page/details. */
        val providerIds: Map<String, String> = emptyMap(),
    )

    /** Canonical-side work facts (adapts `MangaNovelIdBridge.WorkRow`). */
    data class Query(
        val titles: List<String>,
        val author: String? = null,
        val year: Long? = null,
        val totalChapters: Double? = null,
        val type: String? = null,
        /** provider → external_id from provider_links + external_ids. */
        val identifiers: Map<String, String> = emptyMap(),
    ) {
        companion object {
            fun of(row: MangaNovelIdBridge.WorkRow) = Query(
                titles = (listOfNotNull(row.title, row.romanizedTitle, row.nativeTitle) +
                    row.altTitles).distinct(),
                author = row.author,
                year = row.year,
                totalChapters = row.totalChapters?.toDoubleOrNull(),
                type = row.type,
                identifiers = row.externalIds,
            )
        }
    }

    /**
     * Verify [candidate] against [query]. [runnerUp] is the best other
     * candidate's L2 score — the margin check only applies when a real
     * runner-up exists.
     */
    fun verify(query: Query, candidate: Candidate, runnerUp: Double? = null): Verdict {
        val reasons = mutableListOf<String>()

        // ── L1: identifier matchpoints ────────────────────────────────
        for ((provider, id) in candidate.providerIds) {
            if (id.isNotBlank() && query.identifiers[provider] == id) {
                reasons += "identifier:$provider"
                return Verdict(Decision.ACCEPT, 1.0, null, reasons)
            }
        }

        // ── L3 fast-reject: medium mismatch markers ───────────────────
        if (mediumMismatch(query, candidate)) {
            reasons += "medium-mismatch"
            return Verdict(Decision.REJECT, 0.0, null, reasons)
        }

        // ── L2: weighted fields ───────────────────────────────────────
        val titleScore = bestTitleScore(query.titles, listOf(candidate.title) + candidate.altTitles)
        val tokenScore = tfTokenOverlap(query.titles, candidate.title)
        val authorScore = authorMatch(query.author, candidate.author ?: candidate.artist)
        val yearScore = yearMatch(query.year, candidate.year)
        val countScore = countMatch(query.totalChapters, candidate.chapterCount)

        // Absent fields redistribute their weight.
        val fields = buildList {
            add(Field(0.40, titleScore))
            add(Field(0.20, tokenScore))
            if (authorScore != null) add(Field(0.15, authorScore))
            if (yearScore != null) add(Field(0.10, yearScore))
            if (countScore != null) add(Field(0.15, countScore))
        }
        val wSum = fields.sumOf { it.w }
        var score = fields.sumOf { it.w * it.s } / wSum
        reasons += "title=${"%.2f".format(titleScore)} tokens=${"%.2f".format(tokenScore)}"

        // ── L3: negative rules ────────────────────────────────────────
        val neg = negativeRules(query.titles, candidate.title)
        if (neg.first) {
            reasons += "reject:${neg.second}"
            return Verdict(Decision.REJECT, score * 0.3, null, reasons + listOfNotNull(neg.second))
        }
        neg.second?.let {
            score *= 0.5
            reasons += "downgrade:$it"
        }

        // ── L4: floor + margin ────────────────────────────────────────
        val margin = runnerUp?.let { score - it }
        return when {
            score < FLOOR -> Verdict(Decision.REJECT, score, margin, reasons + "below-floor")
            margin != null && margin < MARGIN ->
                Verdict(Decision.TENTATIVE, score, margin, reasons + "thin-margin")
            else -> Verdict(Decision.ACCEPT, score, margin, reasons)
        }
    }

    /**
     * Verify a whole candidate list, applying the margin rule against
     * each candidate's best rival.
     */
    fun verifyAll(query: Query, candidates: List<Candidate>): List<Verdict> {
        val base = candidates.map { verify(query, it) }
        val scores = base.map { it.score }
        return base.mapIndexed { i, v ->
            val runnerUp = scores.filterIndexed { j, _ -> j != i }.maxOrNull() ?: return@mapIndexed v
            val margin = v.score - runnerUp
            if (v.decision == Decision.ACCEPT && margin < MARGIN) {
                v.copy(decision = Decision.TENTATIVE, margin = margin, reasons = v.reasons + "thin-margin")
            } else {
                v.copy(margin = margin)
            }
        }
    }

    // ── L2 field scorers ──────────────────────────────────────────────

    private fun bestTitleScore(queryTitles: List<String>, candTitles: List<String>): Double {
        val m = TitleMatcher()
        var best = 0.0
        for (q in queryTitles) {
            for (c in candTitles) {
                best = maxOf(best, m.match(q, c))
            }
        }
        return best
    }

    /**
     * Splink-style term-frequency weighting: shared tokens count toward
     * similarity proportional to their rarity, scored as F1 coverage of
     * *both* sides — "solo max-level newbie" shares every query token of
     * "solo leveling" but its extra distinctive tokens dilute the score.
     * Domain-common and benign format tokens carry almost no weight.
     */
    private fun tfTokenOverlap(queryTitles: List<String>, candTitle: String): Double {
        val cTokens = tokens(candTitle)
        if (cTokens.isEmpty()) return 0.0
        var best = 0.0
        for (q in queryTitles) {
            val qTokens = tokens(q)
            if (qTokens.isEmpty()) continue
            var shared = 0.0
            var qTotal = 0.0
            var cTotal = 0.0
            for (t in qTokens) {
                val w = tokenWeight(t)
                qTotal += w
                if (t in cTokens) shared += w
            }
            for (t in cTokens) cTotal += tokenWeight(t)
            if (qTotal > 0 && cTotal > 0 && shared > 0) {
                val covQ = shared / qTotal
                val covC = shared / cTotal
                best = maxOf(best, 2 * covQ * covC / (covQ + covC))
            }
        }
        return best
    }

    private fun tokens(title: String): Set<String> =
        TitleNormalizer.normalize(title).split(" ")
            .filter { it.isNotBlank() && it !in BENIGN_TOKENS }
            .toSet()

    private fun tokenWeight(token: String): Double = when {
        token in CHEAP_TOKENS -> 0.15
        token.length <= 2 -> 0.3 // "i", "to", "x" — weak evidence
        token.all { it.isDigit() } -> 0.4 // bare numbers are ambiguous
        else -> 1.0
    }

    private fun authorMatch(queryAuthor: String?, candAuthor: String?): Double? {
        if (queryAuthor.isNullOrBlank() || candAuthor.isNullOrBlank()) return null
        val qa = TitleNormalizer.normalize(queryAuthor)
        val ca = TitleNormalizer.normalize(candAuthor)
        if (qa.isEmpty() || ca.isEmpty()) return null
        return if (qa == ca || qa in ca || ca in qa) 1.0 else 0.0
    }

    private fun yearMatch(q: Long?, c: Long?): Double? {
        if (q == null || c == null || q <= 0 || c <= 0) return null
        val d = kotlin.math.abs(q - c)
        return when {
            d <= 2L -> 1.0
            d <= 4L -> 0.5
            else -> 0.0
        }
    }

    /**
     * Partial catalogs are normal (a source legitimately carries fewer
     * chapters) — penalize only gross overshoot, which indicates a
     * completely different long-running work.
     */
    private fun countMatch(total: Double?, count: Int?): Double? {
        if (total == null || total <= 0 || count == null) return null
        return if (count <= total * 1.5) 1.0 else 0.0
    }

    // ── L3 rules ──────────────────────────────────────────────────────

    /** True → hard reject; false + reason → downgrade only. */
    private fun negativeRules(queryTitles: List<String>, candTitle: String): Pair<Boolean, String?> {
        val candNorm = TitleNormalizer.normalize(candTitle)
        val candTokens = candNorm.split(" ").toSet() - BENIGN_TOKENS

        for (q in queryTitles) {
            val qNorm = TitleNormalizer.normalize(q)
            val qTokens = tokens(q)
            // Benign format suffixes ("- Season 1", "(Webtoon)") are
            // stripped first so real same-work titles aren't penalized.
            val extra = (candTokens - qTokens) - BENIGN_TOKENS
            if (extra.isEmpty()) continue

            // Sequel/continuation number the query lacks → hard reject.
            // Bare digits only count as sequels at 2+ ("Season 1" is fine).
            val candSequel = extra.firstOrNull { it in ROMAN_NUMERALS || (it.toIntOrNull() ?: 0) in 2..9 }
            if (candSequel != null && extra.size <= 3) {
                return true to "sequel-marker:$candSequel"
            }

            // Subtitle/spin-off markers the query lacks → reject.
            val spinoff = extra.firstOrNull { it in SPINOFF_MARKERS }
            if (spinoff != null) return true to "spinoff:$spinoff"

            // Long generic tails ("renge to naruto" → extra "renge to")
            // downgrade rather than reject — could be a subtitle of the
            // same work ("Naruto: Shinden").
            if (qNorm in candNorm && extra.size >= 2) return false to "subtitle-tail"
        }
        return false to null
    }

    private fun mediumMismatch(query: Query, candidate: Candidate): Boolean {
        val qt = query.type?.lowercase() ?: return false
        val cNorm = TitleNormalizer.normalize(candidate.title)
        val novelMarker = "novel" in cNorm.split(" ")
        return when {
            qt in MANGA_TYPES && novelMarker -> true
            qt == "novel" && ("(manga)" in cNorm || "(comic)" in cNorm) -> true
            else -> false
        }
    }

    private data class Field(val w: Double, val s: Double)

    companion object {
        const val FLOOR = 0.80
        const val MARGIN = 0.08

        /** Domain-cheap tokens — carry almost no match evidence. */
        private val CHEAP_TOKENS = setOf(
            "the", "a", "an", "of", "to", "in", "no", "wa", "ga", "de", "ni",
            "wo", "and", "or", "story", "stories", "manga", "manhwa", "manhua",
            "comic", "comics", "novel", "webtoon", "online", "read", "series",
            "new", "san", "sama", "chan", "kun", "senpai",
            // Manhwa/manga-domain words that recur across hundreds of
            // titles — matching on "leveling" is weak evidence.
            "solo", "level", "leveling", "max", "rise", "rising", "god",
            "gods", "return", "king", "tower", "dungeon", "hunter", "player",
            "academy", "villain", "hero", "sword", "world",
        )

        /**
         * Format/edition suffixes that don't change identity —
         * "Solo Leveling - Season 1" is still Solo Leveling. Stripped
         * before both token scoring and negative-rule extras.
         */
        private val BENIGN_TOKENS = setOf(
            "season", "part", "pt", "vol", "volume", "webtoon", "manhwa",
            "manhua", "manga", "comic", "official", "colored", "colour",
            "color", "digital", "version", "edition", "complete", "full",
            "uncensored", "simulpub", "tapas",
        )

        /** Markers that signal a *different* work (spin-off/derivative). */
        private val SPINOFF_MARKERS = setOf(
            "arise", "ragnarok", "gaiden", "kai", "spin", "spinoff",
            "anthology", "omnibus", "after", "special", "extra", "movie",
            "re", "rebirth", "reboot", "another", "shin", "zero", "if",
        )

        private val ROMAN_NUMERALS = setOf("ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x")
        private val MANGA_TYPES = setOf("manga", "manhwa", "manhua", "comic", "webtoon")
    }
}
