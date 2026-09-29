package eu.kanade.tachiyomi.util.chapter

/**
 * Parses a leading volume/book/season-style marker out of a chapter name so
 * chapters can be grouped under pill filters on the detail view —
 * "Vol.4 Ch.30: Title" -> "Volume 4", "Book 2 - Interlude" -> "Book 2".
 *
 * The marker must be at the start of the name; chapters without one return
 * null and fall into the caller-defined ungrouped bucket.
 */
object ChapterNameGroup {

    private val groupRegex = Regex(
        """^\s*(?:ch(?:apter)?\.?\s*)?(vol(?:ume)?\.?|book|season|part|arc|act)\s*[-–—#:]?\s*(\d+(?:\.\d+)?)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val nounMap = mapOf(
        "vol" to "Volume",
        "vol." to "Volume",
        "volume" to "Volume",
        "book" to "Book",
        "season" to "Season",
        "part" to "Part",
        "arc" to "Arc",
        "act" to "Act",
    )

    /** Canonical label like "Volume 4", or null when the name has no marker. */
    fun parse(chapterName: String): String? {
        val match = groupRegex.find(chapterName) ?: return null
        val noun = nounMap[match.groupValues[1].lowercase()] ?: return null
        val number = match.groupValues[2].removeSuffix(".0")
        return "$noun $number"
    }

    /**
     * Ordered distinct group labels for a chapter list, ascending by number
     * (matching the anime season-pill ordering). All-or-nothing: grouping
     * only engages when EVERY chapter name carries a marker — a source that
     * labels just one chapter "Vol.1 Ch.5" would strand it in a pill of its
     * own, so partial coverage disables the row entirely. Returns empty
     * when nothing parses or coverage is incomplete, and a single label
     * when only one group exists — callers hide the row either way.
     */
    fun computeGroups(chapterNames: List<String>): List<String> {
        val parsed = chapterNames.map { parse(it) }
        if (parsed.isEmpty() || parsed.any { it == null }) return emptyList()
        return parsed.filterNotNull()
            .distinct()
            .sortedWith(
                compareBy(
                    { it.substringAfterLast(' ').toFloatOrNull() ?: Float.MAX_VALUE },
                    { it.substringBeforeLast(' ') },
                ),
            )
    }
}
