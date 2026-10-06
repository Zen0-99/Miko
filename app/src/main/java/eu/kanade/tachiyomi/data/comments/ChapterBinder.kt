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
 * 19-04 adds the canonical episode keyspace (D-06), mirroring the
 * worker `episodemap.ts` merge keys:
 *
 *   shows:mk:imdb:tt0944947:e26    — main canon row, ABSOLUTE number
 *   shows:mk:imdb:tt0944947:s0e3   — special/NC canon row,
 *                                    (season ?: 0, epInSeason) scoped
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

    /** Episode-scope thread ID for shows — the legacy raw-number key,
     *  kept as the fallback when no canonical row binds (D-06). */
    fun bindEpisode(workId: String, episodeNumber: Float): String =
        "shows:$workId:e${formatNumber(episodeNumber)}"

    /**
     * Canonical episode thread ID for a bound
     * [CanonicalEpisodeMap.CanonEpisode] row — the worker's merge-key
     * identity restated as a comment thread:
     *
     *   mains (kind 'episode'):       `shows:<w>:e<abs ?? number>`
     *   specials/NC:                  `shows:<w>:s<season ?: 0>e<epInSeason ?: number>`
     *
     * Null when the row carries no usable coordinate — callers fall
     * back to [bindEpisode]'s raw key so threads stay usable while
     * canon is absent, offline, or stale.
     */
    fun bindEpisodeCanon(
        workId: String,
        kind: String,
        abs: Int?,
        season: Int?,
        epInSeason: Int?,
        number: Double? = null,
    ): String? {
        if (kind == "episode") {
            val n = abs?.toFloat() ?: number?.toFloat() ?: return null
            return "shows:$workId:e${formatNumber(n)}"
        }
        val e = epInSeason?.toFloat() ?: number?.toFloat() ?: return null
        return "shows:$workId:s${season ?: 0}e${formatNumber(e)}"
    }

    /**
     * "Season N" prefix of a Cinemeta episode scanlator —
     * "Season 2|★8.1" → 2, "Season 0" → 0. Non-season scanlators
     * (release-group names) and null → null; unlike the UI's
     * `parseCinemetaSeason` helpers this never defaults to 1 — the
     * caller chooses the fallback.
     */
    fun parseSeason(scanlator: String?): Int? {
        val head = scanlator?.substringBefore('|')?.trim()
            ?.takeIf { it.startsWith("Season ") }
            ?: return null
        return head.removePrefix("Season ").trim().toIntOrNull()
    }

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
