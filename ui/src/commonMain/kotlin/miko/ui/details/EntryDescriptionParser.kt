package miko.ui.details

/**
 * Strips provider-stat noise out of extension descriptions and recovers
 * what it carries. Scraped sources (Asura & co.) append lines like
 * `Rank: #6 - Rating 9,75 - Bookmarks 78.7K` and
 * `Alternative titles: A; B; C` (or a bare label heading a bulleted
 * list) to the synopsis — the rating belongs in the info box's rating
 * slot, and the whole alt-titles block leaves free text.
 *
 * Matching is deliberately strict: a stat token needs a known key AND a
 * numeric/# value, and a line must consist entirely of such tokens
 * (single token) or tokens joined by separators (cluster). Prose like
 * "an S rank hero" or "the rating system" never matches.
 */
object EntryDescriptionParser {

    data class Parsed(
        val description: String,
        /** Normalized rating text, e.g. "9.75" (comma decimal fixed). */
        val rating: String? = null,
        /** Raw rank token value, e.g. "#6" → "6". */
        val rank: String? = null,
        /** Raw joined alt-titles list, e.g. "A / B / C". */
        val altTitles: String? = null,
    )

    private const val STAT_KEY =
        "rank|rating|score|bookmark\\w*|comment\\w*|views|follows|followers|likes|reads|popularity"
    private const val STAT_VALUE = "#?~?[\\d.,]+[KMBkmb%]?(?:\\s*/\\s*\\d+)?|n/?a"
    private const val TOKEN = "(?:$STAT_KEY)\\s*[:#]?\\s*(?:$STAT_VALUE)"

    // A line that is nothing but stat tokens joined by separators —
    // "Rank: #6 - Rating 9,75 - Bookmarks 78.7K". Also covers a bare
    // single-token line ("Rating: 9.75") since the list may be len=1.
    private val STATS_LINE = Regex(
        "(?im)^[\\t ]*$TOKEN(?:[\\t ]*[-–—|·•/;,][\\t ]*$TOKEN)*[\\t ]*\\.?[\\t ]*$",
    )

    // Mid-line cluster: ≥2 stat tokens joined by separators is distinct
    // enough to strip even when not line-anchored.
    private val STATS_CLUSTER = Regex(
        "(?i)$TOKEN(?:[\\t ]*[-–—|·•/;,][\\t ]*$TOKEN)+",
    )

    private val RATING_VALUE = Regex("(?i)(?:rating|score)\\s*[:#]?\\s*($STAT_VALUE)")

    private val ALT_TITLE_LABEL =
        "alternative titles?|alt\\.? titles?|also known as|a\\.?k\\.?a\\.?|associated names?|other names?"

    // "Alternative titles: A, B, C" — the list runs to end of line.
    // A bare "Alternative titles:" (group 1 empty) introduces a block
    // list on the following lines instead.
    private val ALT_LABEL_LINE = Regex(
        "(?i)^[\\t ]*(?:$ALT_TITLE_LABEL)\\s*[:：]\\s*(.*?)[\\t ]*$",
    )

    // Label alone on the line, no colon.
    private val ALT_LABEL_BARE = Regex(
        "(?i)^[\\t ]*(?:$ALT_TITLE_LABEL)[\\t ]*$",
    )

    // List-item markers sources emit under a bare label ("- Title").
    private val BULLET_PREFIX = Regex("^[-*•·◦▪–—][\\t ]+")

    // A bare (unbulleted) continuation line still counts as a title
    // entry only while it stays short and doesn't end like a sentence —
    // a prose paragraph ending in . ! ? … resumes the description.
    private const val TITLE_LINE_MAX = 100
    private val PROSE_END = Regex("""[.!?…]['"’”)\]}]*$""")

    fun parse(raw: String?): Parsed {
        if (raw.isNullOrBlank()) return Parsed(raw.orEmpty())

        var rating: String? = null
        var rank: String? = null
        var altTitles: String? = null

        // Pass 1 — alt-titles blocks: the label line (with or without an
        // inline list) plus the list lines under it. Line-based because
        // sources emit "Label:" then one "- Title" per line; a regex
        // replace can't consume that variable-length block cleanly.
        val titles = mutableListOf<String>()
        val kept = mutableListOf<String>()
        val lines = raw.split('\n')
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val label = ALT_LABEL_LINE.matchEntire(line.trim())
            if (label == null && !ALT_LABEL_BARE.matches(line.trim())) {
                kept += line
                i++
                continue
            }
            label?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { titles += it }
            i++
            // Consume list items under the label — bulleted items always;
            // bare lines only while they're short and non-sentence-like.
            // The block ends at a blank line or resuming prose.
            while (i < lines.size) {
                val item = lines[i].trim()
                if (item.isEmpty()) break
                val bulleted = BULLET_PREFIX.containsMatchIn(item)
                if (!bulleted && (item.length > TITLE_LINE_MAX || PROSE_END.containsMatchIn(item))) {
                    break
                }
                item.replace(BULLET_PREFIX, "").takeIf { it.isNotEmpty() }
                    ?.let { titles += it }
                i++
            }
        }
        if (titles.isNotEmpty()) altTitles = titles.joinToString("; ")
        var text = kept.joinToString("\n")

        // Rating survives the strip: capture before removing the line.
        text = STATS_LINE.replace(text) { m ->
            val line = m.value
            if (rating == null) {
                rating = RATING_VALUE.find(line)?.groupValues?.get(1)
                    ?.replace(',', '.')
            }
            if (rank == null) {
                rank = Regex("(?i)rank\\s*[:#]?\\s*($STAT_VALUE)").find(line)
                    ?.groupValues?.get(1)?.removePrefix("#")
            }
            ""
        }
        text = STATS_CLUSTER.replace(text) { m ->
            if (rating == null) {
                rating = RATING_VALUE.find(m.value)?.groupValues?.get(1)
                    ?.replace(',', '.')
            }
            ""
        }

        // Collapse the blank-line runs the strips leave behind.
        val cleaned = text
            .replace(Regex("\\n{3,}"), "\n\n")
            .replace(Regex("[ \\t]+\\n"), "\n")
            .trim()

        return Parsed(cleaned, rating, rank, altTitles)
    }
}
