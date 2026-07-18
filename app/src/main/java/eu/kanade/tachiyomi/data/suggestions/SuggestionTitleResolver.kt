package eu.kanade.tachiyomi.data.suggestions

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Resolves title candidates for suggestion seed building and scores alias matches.
 *
 * Scoring contract:
 * - Exact match after case-folding       → 100
 * - One is a prefix of the other         → 75
 * - One contains the other               → 50
 * - Token overlap (Jaccard × 50)         → 0..49
 */
object SuggestionTitleResolver {

    fun parseSlugTitle(url: String): String? {
        val lastSegment = url.substringBefore("?").substringAfterLast("/").trim()
        if (lastSegment.isBlank()) return null
        val slug = if (lastSegment.contains("--")) {
            lastSegment.substringAfter("--")
        } else {
            lastSegment
        }
        if (slug.all { it.isDigit() }) return null

        return slug.replace("-", " ")
            .replace("_", " ")
            .trim()
            .ifBlank { null }
    }

    fun resolveCandidates(
        title: String,
        description: String?,
        url: String? = null,
    ): List<String> = buildList {
        add(title)
        url?.let { parseSlugTitle(it)?.let { add(it) } }
    }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .also { candidates ->
            logcat(LogPriority.DEBUG) { "SuggestionTitleResolver: resolved ${candidates.size} candidates for '$title'" }
        }

    fun scoreMatch(candidate: String, target: String): Int {
        val c = candidate.lowercase().trim()
        val t = target.lowercase().trim()
        if (c == t) return 100
        if (c.startsWith(t) || t.startsWith(c)) return 75
        if (c.contains(t) || t.contains(c)) return 50

        val cTokens = c.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        val tTokens = t.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        if (cTokens.isEmpty() || tTokens.isEmpty()) return 0

        val intersection = cTokens.intersect(tTokens)
        val ratio = intersection.size.toDouble() / maxOf(cTokens.size, tTokens.size).toDouble()
        return (ratio * 50).toInt()
    }

    private val volumeChapterRegex =
        Regex(
            """(?i)\b(vol|volume|ch|chapter|season|part|book|tome|s\d+|том|часть|книга|глава|сезон|т)\b\s*\.?\s*\d+""",
        )
    private val nonAlphanumericRegex = Regex("""[^\p{L}\p{N}\s-]""")
    private val consecutiveSpacesRegex = Regex(" +")

    fun cleanTitle(title: String): String {
        val preTitle = title.lowercase()
        var cleanedTitle = removeTextInBrackets(preTitle)
        cleanedTitle = cleanedTitle.replace(volumeChapterRegex, " ")
        cleanedTitle = cleanedTitle.replace(nonAlphanumericRegex, " ")
        return cleanedTitle.trim().replace(consecutiveSpacesRegex, " ")
    }

    private fun removeTextInBrackets(text: String): String {
        var depth = 0
        return buildString {
            for (char in text) {
                when (char) {
                    '(', '[', '<', '{' -> depth++
                    ')', ']', '>', '}' -> if (depth > 0) depth--
                    else -> if (depth == 0) append(char)
                }
            }
        }
    }

    fun isFranchiseDuplicate(titleA: String, titleB: String): Boolean {
        val cleanA = cleanTitle(titleA)
        val cleanB = cleanTitle(titleB)
        if (cleanA == cleanB) return true
        // Check if one is a sequel/spin-off of the other
        val shorter = if (cleanA.length < cleanB.length) cleanA else cleanB
        val longer = if (cleanA.length < cleanB.length) cleanB else cleanA
        if (longer.startsWith("$shorter ") && longer.drop(shorter.length + 1).let { suffix ->
            suffix.matches(Regex("""(?i)(ragnarok|sequel|prequel|spin.?off|side.?story|continuation|\d+|ii|iii|iv|v|final|last|next|new|the \w+)"""))
            }
        ) {
            return true
        }
        return false
    }
}
