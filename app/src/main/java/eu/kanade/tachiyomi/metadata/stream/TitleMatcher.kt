package eu.kanade.tachiyomi.metadata.stream

/**
 * Title matcher for stream resolution — ranks how well a source anime title
 * matches a Cinemeta (metadata) title.
 *
 * Uses a layered cascade inspired by am-algorithm, atm (Jaro-Winkler),
 * AnymeX (composite scoring), and comet/RTN (Stremio ecosystem):
 *
 * 1. Exact match (1.0) — normalized strings are equal
 * 2. Contains match (0.85) — one normalized string contains the other
 * 3. Token sort ratio (0.7–0.99) — sort words then Levenshtein (word order independence)
 * 4. Token set ratio (0.6–0.89) — dedup words then compare (handles repeats)
 * 5. Jaro-Winkler fallback (0.0–0.79) — prefix-weighted similarity
 *
 * All algorithms hand-rolled in pure Kotlin — no external dependencies.
 */
class TitleMatcher {

    data class Ranked<T>(
        val item: T,
        val score: Double,
        val matchedTitle: String,
    )

    /**
     * Score how well [candidate] matches [query]. Returns 0.0–1.0.
     * Runs the cascade — first match wins.
     */
    fun match(query: String, candidate: String): Double {
        val normQuery = normalize(query)
        val normCandidate = normalize(candidate)
        if (normQuery.isEmpty() || normCandidate.isEmpty()) return 0.0

        // 1. Exact match
        if (normQuery == normCandidate) return 1.0

        // 2. Contains match
        if (normQuery in normCandidate || normCandidate in normQuery) return 0.85

        // 3. Token sort ratio — sort words alphabetically, then Levenshtein ratio
        val tokenSortScore = tokenSortRatio(normQuery, normCandidate)
        if (tokenSortScore >= 0.7) return tokenSortScore

        // 4. Token set ratio — dedup words, compare sets
        val tokenSetScore = tokenSetRatio(normQuery, normCandidate)
        if (tokenSetScore >= 0.6) return maxOf(tokenSortScore, tokenSetScore)

        // 5. Jaro-Winkler fallback — prefix-weighted similarity
        val jwScore = jaroWinkler(normQuery, normCandidate)
        return maxOf(tokenSortScore, tokenSetScore, jwScore)
    }

    /**
     * Rank candidates by match score descending. Filters out scores below [threshold].
     */
    fun <T> rank(
        query: String,
        candidates: List<Pair<String, T>>,
        threshold: Double = 0.5,
    ): List<Ranked<T>> {
        return candidates
            .map { (title, item) -> Ranked(item, match(query, title), title) }
            .filter { it.score >= threshold }
            .sortedByDescending { it.score }
    }

    // ── Normalization ──────────────────────────────────────────────────

    /**
     * Normalize a title for comparison:
     * - lowercase
     * - remove punctuation
     * - remove season/episode markers (S1, Season 2, EP12, 2nd season)
     * - remove version/quality suffixes (v2, BD, WEB-DL, dub, sub, ova, ona)
     * - remove articles (the, a, an)
     * - collapse whitespace
     */
    fun normalize(title: String): String {
        var s = title.lowercase().trim()

        // Remove season/episode markers
        s = s.replace(Regex("\\b(season|s)\\s*\\d+\\b"), " ")
        s = s.replace(Regex("\\b\\d+(st|nd|rd|th)\\s+season\\b"), " ")
        s = s.replace(Regex("\\bep?\\s*\\d+\\b"), " ")

        // Remove version/quality suffixes
        s = s.replace(Regex("\\b(v\\d+|bd|web-dl|webrip|dub|sub|ova|ona|special|complete)\\b"), " ")

        // Remove articles
        s = s.replace(Regex("\\b(the|a|an)\\b"), " ")

        // Remove punctuation
        s = s.replace(Regex("[^a-z0-9\\s]"), " ")

        // Collapse whitespace
        s = s.replace(Regex("\\s+"), " ").trim()

        return s
    }

    // ── Levenshtein ────────────────────────────────────────────────────

    /**
     * Levenshtein edit distance (Wagner-Fischer, 2-row DP).
     * O(m*n) time, O(m) space.
     */
    private fun levenshtein(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val (a, b) = if (s1.length <= s2.length) s1 to s2 else s2 to s1
        var prev = IntArray(a.length + 1) { it }
        var curr = IntArray(a.length + 1)

        for (j in 1..b.length) {
            curr[0] = j
            for (i in 1..a.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[i] = minOf(
                    prev[i] + 1,        // deletion
                    curr[i - 1] + 1,    // insertion
                    prev[i - 1] + cost, // substitution
                )
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[a.length]
    }

    /**
     * Levenshtein ratio: 1.0 - (distance / maxLen).
     */
    private fun levenshteinRatio(s1: String, s2: String): Double {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        return 1.0 - (levenshtein(s1, s2).toDouble() / maxLen)
    }

    // ── Token Sort Ratio ───────────────────────────────────────────────

    /**
     * Sort tokens alphabetically, join, then Levenshtein ratio.
     * Handles word order: "Attack Titan On" → "Attack On Titan".
     */
    private fun tokenSortRatio(s1: String, s2: String): Double {
        val sorted1 = s1.split(" ").sorted().joinToString(" ")
        val sorted2 = s2.split(" ").sorted().joinToString(" ")
        return levenshteinRatio(sorted1, sorted2)
    }

    // ── Token Set Ratio ────────────────────────────────────────────────

    /**
     * Compare token sets (dedup). Handles repeated words:
     * "Bleach Bleach" → "Bleach".
     *
     * Uses Jaccard-like ratio: |intersection| / |union|.
     */
    private fun tokenSetRatio(s1: String, s2: String): Double {
        val set1 = s1.split(" ").toSet()
        val set2 = s2.split(" ").toSet()
        val intersection = set1.intersect(set2)
        val union = set1.union(set2)
        if (union.isEmpty()) return 1.0
        return intersection.size.toDouble() / union.size
    }

    // ── Jaro-Winkler ───────────────────────────────────────────────────

    /**
     * Jaro-Winkler similarity with prefix bonus.
     * Better than Levenshtein for titles sharing prefixes
     * ("Bleach" vs "Bleach TYBW").
     *
     * λ = 0.1 (prefix scaling), max prefix = 4 chars.
     */
    private fun jaroWinkler(s1: String, s2: String): Double {
        val jaro = jaro(s1, s2)
        if (jaro < 0.7) return jaro // Winkler only boosts high Jaro scores

        // Common prefix length (up to 4)
        val prefixLen = (0 until minOf(4, s1.length, s2.length))
            .takeWhile { s1[it] == s2[it] }
            .count()

        return jaro + (prefixLen * 0.1 * (1.0 - jaro))
    }

    /**
     * Jaro similarity.
     */
    private fun jaro(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val matchDistance = maxOf(s1.length, s2.length) / 2 - 1
        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)
        var matches = 0

        for (i in s1.indices) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        // Count transpositions
        var transpositions = 0
        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }
        transpositions /= 2

        val m = matches.toDouble()
        return ((m / s1.length) + (m / s2.length) + ((m - transpositions) / m)) / 3.0
    }
}
