package eu.kanade.tachiyomi.data.mikodb

import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Canonical chapter map (Phase 12-04) — fetches a work's canonical
 * chapter sequence from the comments Worker, caches it, and binds a
 * source chapter name to its canonical row via [ChapterNumberParser].
 *
 * Chapter threads already use `{work}:c{number}` shape (Phase 10), so a
 * bound [CanonChapter.number] drops straight into thread construction.
 */
class CanonicalChapterMap(
    private val api: WorkIdentityApi = Injekt.get(),
) {

    data class CanonChapter(
        val seq: Int,
        val number: Double?,
        val part: Int?,
        val volume: Double?,
        val kind: ChapterNumberParser.Kind,
        val label: String,
        val confidence: String,
        val sources: List<String>,
        val refs: Map<String, String>,
        /** Earliest carrier upload date (epoch ms); null when unknown. */
        val dateUpload: Long? = null,
    )

    private companion object {
        const val CACHE_LIMIT = 64

        /** SWR window — a cached sequence older than this re-canonizes
         * in the background while still serving immediately. */
        const val STALE_AFTER_MS = 24L * 60 * 60 * 1000
    }

    /** Process-level LRU of canonical lists keyed by work id. */
    private val cache = object : LinkedHashMap<String, List<CanonChapter>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<CanonChapter>>?) =
            size > CACHE_LIMIT
    }

    /** Fetch timestamps for staleness checks (keyed alongside [cache]). */
    private val fetchedAt = HashMap<String, Long>()

    /** Canonical sequence for a work; empty list when never canonized or on failure. */
    suspend fun forWork(workId: String): List<CanonChapter> {
        synchronized(cache) { cache[workId] }?.let { return it }
        val dto = api.getChapters(workId) ?: return emptyList()
        val parsed = dto.chapters.map { c ->
            CanonChapter(
                seq = c.seq,
                number = c.number,
                part = c.part,
                volume = c.volume,
                kind = ChapterNumberParser.Kind.entries
                    .firstOrNull { it.wire == c.kind } ?: ChapterNumberParser.Kind.CHAPTER,
                label = c.label,
                confidence = c.confidence,
                sources = c.sources,
                refs = c.refs,
                dateUpload = c.date_upload,
            )
        }
        // Carrier feeds occasionally list one chapter twice under two
        // labels (MangaFire "Ch.5" + "Chapter 5"); the worker merges
        // both into canon as separate seqs → a doubled local list.
        // Dedupe on the parsed identity, first seq wins; rows with no
        // number can't be proven dupes and pass through.
        val seen = HashSet<Triple<Double, Int?, ChapterNumberParser.Kind>>()
        val list = parsed.filter { c ->
            val n = c.number ?: return@filter true
            seen.add(Triple(n, c.part, c.kind))
        }
        // Empty sequences are NOT cached — "no feed materialized yet",
        // not a fresh result — canonize must retry (a migrating
        // favorite's own source may be the feed that finally populates).
        if (list.isEmpty()) return emptyList()
        synchronized(cache) {
            cache[workId] = list
            fetchedAt[workId] = System.currentTimeMillis()
        }
        return list
    }

    /** True when the work has no cached sequence or it exceeds the SWR window. */
    fun isStale(workId: String): Boolean = synchronized(cache) {
        // An empty cached list is "no feed materialized yet", not a
        // fresh result — canonize must retry (a migrating favorite's
        // own source may be the feed that finally populates it).
        if (cache[workId].isNullOrEmpty()) return true
        val ts = fetchedAt[workId] ?: return true
        System.currentTimeMillis() - ts > STALE_AFTER_MS
    }

    /** Drop the cached list after a canonize rebuild. */
    fun invalidate(workId: String) {
        synchronized(cache) {
            cache.remove(workId)
            fetchedAt.remove(workId)
        }
    }

    /**
     * Bind a source chapter name to the canonical row it represents.
     * Returns the row, or null when the name is unparseable (`unknown`)
     * or no canonical row matches — unknowns never bind.
     */
    fun bind(chapterName: String, canon: List<CanonChapter>): CanonChapter? {
        val p = ChapterNumberParser.parse(chapterName)
        if (p.kind == ChapterNumberParser.Kind.UNKNOWN || p.kind == ChapterNumberParser.Kind.NOTICE) {
            return null
        }
        return canon.firstOrNull { c ->
            c.kind == p.kind && c.number == p.number && c.part == p.part
        } ?: canon.firstOrNull { c ->
            // Loose fallback: same number+part even if kind differs
            // (a "Chapter 30" source binds to an "extra/30" canon row).
            c.number != null && c.number == p.number && c.part == p.part
        }
    }
}
