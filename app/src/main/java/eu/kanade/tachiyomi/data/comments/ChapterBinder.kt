package eu.kanade.tachiyomi.data.comments

import eu.kanade.domain.ui.model.Carrier
import eu.kanade.tachiyomi.util.chapter.ChapterNameGroup

/**
 * Canonical chapter-level thread IDs (NCM-002):
 *
 *   manga:mk:mb:531343:c142        — ungrouped chapter
 *   manga:mk:mb:531343:v4c30       — "Volume 4" + chapter 30
 *   books:mk:m:ab12:b2c7           — "Book 2" + chapter 7
 *   shows:mk:imdb:tt0944947:e5     — episode scope (shows skip alignment)
 *
 * Server-side THREAD_RE `^(shows|manga|books):[^\s]{1,200}$` already
 * accepts these suffixes, so chapter threads need no protocol change.
 * Chapters whose number can't be resolved produce null — callers hide
 * the chapter-comment entry point for that item.
 */
object ChapterBinder {

    private val groupPrefixes = mapOf(
        "Volume" to "v",
        "Book" to "b",
        "Season" to "s",
        "Part" to "p",
        "Arc" to "a",
        "Act" to "t",
    )

    private val chapterNumberRegex = Regex(
        """\bch(?:apter)?\.?\s*[-–—#:]?\s*(\d+(?:\.\d+)?)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * "Volume 4" → "v4", "Book 2" → "b2". Returns null for labels that
     * don't match the canonical "Noun <number>" shape
     * [ChapterNameGroup.parse] produces.
     */
    fun groupCode(groupLabel: String?): String? {
        if (groupLabel == null) return null
        val noun = groupLabel.substringBeforeLast(' ')
        val number = groupLabel.substringAfterLast(' ')
        val prefix = groupPrefixes[noun] ?: return null
        if (number.toFloatOrNull() == null) return null
        return prefix + formatNumber(number.toFloat())
    }

    /**
     * Canonical chapter thread ID, or null when no usable chapter number
     * exists. [chapterNumber] ≤ 0 falls back to extracting a number from
     * [chapterName] ("Chapter 9" → 9f).
     */
    fun bind(carrier: Carrier, workId: String, chapterName: String, chapterNumber: Float): String? {
        val number = if (chapterNumber > 0f) {
            chapterNumber
        } else {
            numberFromName(chapterName)
                ?: return null
        }
        val group = groupCode(ChapterNameGroup.parse(chapterName))
        return buildString {
            append(carrier.medium).append(':').append(workId).append(':')
            if (group != null) append(group)
            append('c').append(formatNumber(number))
        }
    }

    /** Episode-scope thread ID for shows. */
    fun bindEpisode(workId: String, episodeNumber: Float): String =
        "shows:$workId:e${formatNumber(episodeNumber)}"

    /** "Chapter 9" → 9f; null when no chapter marker is present. */
    fun numberFromName(name: String): Float? =
        chapterNumberRegex.find(name)?.groupValues?.get(1)?.toFloatOrNull()

    /** 142.0 → "142", 142.5 → "142.5". */
    internal fun formatNumber(number: Float): String =
        if (number == number.toLong().toFloat()) {
            number.toLong().toString()
        } else {
            number.toString().trimEnd('0').trimEnd('.')
        }
}
