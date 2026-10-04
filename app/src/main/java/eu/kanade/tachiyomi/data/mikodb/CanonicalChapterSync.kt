package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.tachiyomi.source.model.SChapter
import logcat.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapterUpdate
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Canonical chapter materialization (Phase 13-03) — turns the worker's
 * merged canonical sequence into ordinary `chapter` DB rows on the
 * `miko:`-sourced entry, via the standard [SyncChaptersWithSource]
 * pipeline.
 *
 * Materializing (rather than a parallel UI list) gets chapter grouping,
 * read/bookmark state, filters, swipe actions, and `:cN` thread binding
 * through [eu.kanade.tachiyomi.data.comments.ChapterBinder] for free.
 *
 * `scanlator` stays null on canonical rows: carrier provenance lives in
 * the canon `refs`/router ([CanonicalChapterRouter]) and must not leak
 * raw extension package names onto chapter cards. A chapter can be
 * carried by several sources at once — no single label is honest.
 *
 * Chapter `url` is `canon:{seq}` — the stable merge position, never a
 * source URL. The reader fan-out (13-04) resolves a carrier at read
 * time via `refs`.
 */
class CanonicalChapterSync(
    private val chapterMap: CanonicalChapterMap = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
) {

    /** Sync a canonical entry's chapter rows from the canon sequence.
     * Returns rows synced (0 when no canon sequence exists yet). */
    suspend fun sync(manga: Manga, workId: String, medium: String = "manga"): Int {
        val canon = chapterMap.forWork(workId)
        if (canon.isEmpty()) return 0
        realignCanonRows(manga.id, workId, canon)
        val sChapters = toSChapters(canon, workId)
        val source = sourceManager.getOrStub(manga.source)
        val added = runCatching {
            syncChaptersWithSource.await(sChapters, manga, source)
        }.onFailure { e ->
            logcat { "canonChapterSync $workId: ${e.message}" }
        }.getOrElse { return -1 }
        repairCanonRows(manga.id, canon)
        // This stub is now serviceable — entries that cached "no
        // canonical" while it was chapter-less must re-evaluate.
        if (added.isNotEmpty()) {
            inheritReadBuckets(manga.id, added)
            Injekt.get<CanonicalEntryUnifier>().invalidate()
        }
        return added.size
    }

    /**
     * Read-state inherit for late-arriving canon rows: provider
     * enrichment can add a carrier's split rows (12.1/12.2) after the
     * reader already consumed chapter 12 — a new row counts as read
     * when its whole-number bucket is already read locally. Kind-gated
     * to CHAPTER so numbered extras (omakes, a bonus "12.5") still
     * surface as new content.
     */
    private suspend fun inheritReadBuckets(mangaId: Long, added: List<Chapter>) {
        val addedIds = added.mapTo(HashSet()) { it.id }
        val readBuckets = chapterRepository.getChapterByMangaId(mangaId)
            .asSequence()
            .filter { it.read && it.id !in addedIds }
            .mapTo(HashSet()) { kotlin.math.floor(it.chapterNumber) }
        if (readBuckets.isEmpty()) return
        val updates = added.mapNotNull { ch ->
            if (ch.read || kotlin.math.floor(ch.chapterNumber) !in readBuckets) {
                return@mapNotNull null
            }
            if (ChapterNumberParser.parse(ch.name).kind != ChapterNumberParser.Kind.CHAPTER) {
                return@mapNotNull null
            }
            ChapterUpdate(id = ch.id, read = true)
        }
        if (updates.isNotEmpty()) {
            runCatching { chapterRepository.updateAllChapters(updates) }
                .onFailure { logcat { "canonChapterSync inherit: ${it.message}" } }
        }
    }

    /**
     * Repair rows materialized by earlier revisions: the old pipeline
     * stamped `date_upload = now` on every chapter, wrote carrier
     * package names into `scanlator`, and kept raw source labels.
     * `SyncChaptersWithSource` coalesce-updates cannot null those back
     * out, so canon rows are reconciled here against the current canon
     * values.
     */
    private suspend fun repairCanonRows(
        mangaId: Long,
        canon: List<CanonicalChapterMap.CanonChapter>,
    ) {
        val bySeq = canon.associateBy { it.seq }
        val fixes = chapterRepository.getChapterByMangaId(mangaId)
            .mapNotNull { row ->
                val seq = row.url.removePrefix("canon:").substringAfterLast(':')
                    .toIntOrNull()
                    ?.takeIf { row.url.startsWith("canon:") } ?: return@mapNotNull null
                val c = bySeq[seq] ?: return@mapNotNull null
                val wantName = normalizeLabel(c.label, c.number)
                val wantDate = c.dateUpload ?: 0L
                if (row.name != wantName || row.dateUpload != wantDate || !row.scanlator.isNullOrEmpty()) {
                    ChapterUpdate(
                        id = row.id,
                        name = wantName,
                        dateUpload = wantDate,
                        // scanlator is cleared via "" — ChapterUpdate's
                        // coalesce semantics cannot store null.
                        scanlator = "",
                    )
                } else {
                    null
                }
            }
        if (fixes.isNotEmpty()) {
            runCatching { chapterRepository.updateAllChapters(fixes) }
                .onFailure { logcat { "canonChapterSync repair: ${it.message}" } }
        }
    }

    /**
     * Seq numbers are merge positions, not identity — a re-canonize that
     * adds/reorders carrier feeds renumbers them, and the row key
     * (`canon:{workId}:{seq}`) is what [SyncChaptersWithSource] diffs on.
     * Without realignment every shifted row looks "removed", gets deleted
     * and re-added: read/bookmark survive the number-restore, but
     * `lastPageRead`, history, and row ids are lost and the list visibly
     * empties mid-sync. Re-pointing the url in place keeps the row (and
     * all its state) while still tracking the canonical position.
     */
    private suspend fun realignCanonRows(
        mangaId: Long,
        workId: String,
        canon: List<CanonicalChapterMap.CanonChapter>,
    ) {
        val rows = chapterRepository.getChapterByMangaId(mangaId)
        val realigns = realignUpdates(
            canon,
            rows.mapNotNull { row ->
                if (!row.url.startsWith("canon:")) return@mapNotNull null
                Triple(row.id, row.url, row.name)
            },
            workId,
            chapterMap::bind,
        )
        if (realigns.isEmpty()) return
        runCatching {
            chapterRepository.updateAllChapters(realigns.map { ChapterUpdate(id = it.first, url = it.second) })
        }.onFailure { logcat { "canonChapterSync realign: ${it.message}" } }
    }

    private suspend fun realignNovelRows(
        novelId: Long,
        workId: String,
        canon: List<CanonicalChapterMap.CanonChapter>,
    ) {
        val rows = novelChapterRepository.getNovelChaptersByNovelId(novelId)
        val realigns = realignUpdates(
            canon,
            rows.mapNotNull { row ->
                if (!row.url.startsWith("canon:")) return@mapNotNull null
                Triple(row.id, row.url, row.name)
            },
            workId,
            chapterMap::bind,
        )
        if (realigns.isEmpty()) return
        runCatching {
            novelChapterRepository.updateAllNovelChapters(
                realigns.map { NovelChapterUpdate(id = it.first, url = it.second) },
            )
        }.onFailure { logcat { "canonChapterSync novel realign: ${it.message}" } }
    }

    /**
     * Novel counterpart of [sync]: materializes canon rows as
     * [NovelChapter] rows on the canonical novel entry so its screen
     * has a chapter list. Novels have no SyncChaptersWithSource
     * equivalent — missing canon urls are inserted directly; existing
     * rows (canon or extension-fed) are left alone. `scanlator` stays
     * null — carrier provenance lives in the canon refs.
     */
    suspend fun syncNovel(novel: Novel, workId: String): Int {
        val canon = chapterMap.forWork(workId)
        if (canon.isEmpty()) return 0
        realignNovelRows(novel.id, workId, canon)
        val existing = novelChapterRepository.getNovelChaptersByNovelId(novel.id)
        val existingUrls = existing.mapTo(HashSet()) { it.url }

        // Repair rows stamped by the old pipeline (pkg scanlator, fake
        // upload date, raw label) — insert-only below can't fix them.
        val bySeq = canon.associateBy { it.seq }
        val fixes = existing.mapNotNull { row ->
            val seq = row.url.removePrefix("canon:").substringAfterLast(':')
                .toIntOrNull()
                ?.takeIf { row.url.startsWith("canon:") } ?: return@mapNotNull null
            val c = bySeq[seq] ?: return@mapNotNull null
            val wantName = normalizeLabel(c.label, c.number)
            val wantDate = c.dateUpload ?: 0L
            if (row.name != wantName || row.dateUpload != wantDate || !row.scanlator.isNullOrEmpty()) {
                NovelChapterUpdate(
                    id = row.id,
                    name = wantName,
                    dateUpload = wantDate,
                    scanlator = "",
                )
            } else {
                null
            }
        }
        if (fixes.isNotEmpty()) {
            runCatching { novelChapterRepository.updateAllNovelChapters(fixes) }
                .onFailure { logcat { "canonChapterSync novel repair: ${it.message}" } }
        }

        // Whole-number buckets already read — carrier split rows
        // (12.1/12.2) materialized after the reader consumed chapter
        // 12 inherit read instead of surfacing as new. Kind-gated to
        // CHAPTER: numbered extras still count as new content.
        val readBuckets = existing.asSequence()
            .filter { it.read }
            .mapTo(HashSet()) { kotlin.math.floor(it.chapterNumber) }

        // Newest-first so sourceOrder 0 = latest — matches extension feeds.
        val now = System.currentTimeMillis()
        val newChapters = canon.sortedByDescending { it.seq }.mapIndexedNotNull { i, c ->
            val url = canonUrl(workId, c.seq)
            if (url in existingUrls) return@mapIndexedNotNull null
            val inheritsRead = c.kind == ChapterNumberParser.Kind.CHAPTER &&
                c.number != null &&
                kotlin.math.floor(c.number!!) in readBuckets
            NovelChapter.create().copy(
                novelId = novel.id,
                url = url,
                name = normalizeLabel(c.label, c.number),
                chapterNumber = c.number ?: 0.0,
                sourceOrder = i.toLong(),
                dateFetch = now,
                dateUpload = c.dateUpload ?: 0L,
                read = inheritsRead,
                scanlator = null,
            )
        }
        if (newChapters.isNotEmpty()) {
            runCatching { novelChapterRepository.addAllNovelChapters(newChapters) }
                .onFailure { e ->
                    logcat { "canonChapterSync novel $workId: ${e.message}" }
                    return 0
                }
            Injekt.get<CanonicalEntryUnifier>().invalidate()
        }
        return newChapters.size
    }

    /**
     * Device-side canon materialization: the favorite's own cached
     * extension chapters become the local `canon:` sequence. Minting
     * must not depend on the worker accepting a feed — a dead source's
     * pkg may be absent from every repo catalog and the identity gate
     * may legitimately reject a divergent title.
     *
     * Rows are born with the donor's state (`read`/`bookmark`/
     * `lastPageRead`/`dateFetch` copied at insert): minting must never
     * surface an intermediate all-unread frame, and the later
     * `transferFavorite` waterline only reinforces what's already set.
     * When a real carrier canonizes the work, [realignCanonRows]
     * rebinds these rows onto server seqs.
     *
     * Returns rows written (0 when nothing was written).
     */
    suspend fun syncLocal(manga: Manga, workId: String, sourceChapters: List<Chapter>): Int {
        if (sourceChapters.isEmpty()) return 0
        val seeds = localCanonSeeds(sourceChapters, { it.name }, { it.sourceOrder })
        if (seeds.isEmpty()) return 0
        val existingUrls = chapterRepository.getChapterByMangaId(manga.id)
            .map { it.url }.toHashSet()
        val donorReadBuckets = sourceChapters.asSequence()
            .filter { it.read }
            .mapTo(HashSet()) { kotlin.math.floor(it.chapterNumber) }
        val now = System.currentTimeMillis()
        // seeds are oldest-first (seq chronological); rows insert
        // newest-first so sourceOrder 0 = latest, like source feeds.
        val newRows = seeds.asReversed().mapIndexedNotNull { i, s ->
            val url = canonUrl(workId, s.seq)
            if (url in existingUrls) return@mapIndexedNotNull null
            Chapter.create().copy(
                mangaId = manga.id,
                url = url,
                name = s.name,
                chapterNumber = s.number,
                sourceOrder = i.toLong(),
                dateFetch = if (s.src.dateFetch > 0) s.src.dateFetch else now,
                dateUpload = s.src.dateUpload,
                // Whole-bucket inherit: a donor "12" read covers the
                // donor's own "12.1"/"12.2" splits too — minting must
                // not resurrect them as unread.
                read = s.src.read ||
                    (
                        s.kind == ChapterNumberParser.Kind.CHAPTER &&
                            kotlin.math.floor(s.number) in donorReadBuckets
                        ),
                bookmark = s.src.bookmark,
                lastPageRead = s.src.lastPageRead,
                scanlator = null,
            )
        }
        if (newRows.isNotEmpty()) {
            runCatching { chapterRepository.addAllChapters(newRows) }
                .onFailure { e ->
                    logcat { "localCanon $workId: ${e.message}" }
                    return 0
                }
            Injekt.get<CanonicalEntryUnifier>().invalidate()
        }
        return newRows.size
    }

    /** Same as [syncLocal] for novels/books — `lastCharRead` carries position. */
    suspend fun syncLocalNovel(novel: Novel, workId: String, sourceChapters: List<NovelChapter>): Int {
        if (sourceChapters.isEmpty()) return 0
        val seeds = localCanonSeeds(sourceChapters, { it.name }, { it.sourceOrder })
        if (seeds.isEmpty()) return 0
        val existingUrls = novelChapterRepository.getNovelChaptersByNovelId(novel.id)
            .map { it.url }.toHashSet()
        val donorReadBuckets = sourceChapters.asSequence()
            .filter { it.read }
            .mapTo(HashSet()) { kotlin.math.floor(it.chapterNumber) }
        val now = System.currentTimeMillis()
        val newRows = seeds.asReversed().mapIndexedNotNull { i, s ->
            val url = canonUrl(workId, s.seq)
            if (url in existingUrls) return@mapIndexedNotNull null
            NovelChapter.create().copy(
                novelId = novel.id,
                url = url,
                name = s.name,
                chapterNumber = s.number,
                sourceOrder = i.toLong(),
                dateFetch = if (s.src.dateFetch > 0) s.src.dateFetch else now,
                dateUpload = s.src.dateUpload,
                read = s.src.read ||
                    (
                        s.kind == ChapterNumberParser.Kind.CHAPTER &&
                            kotlin.math.floor(s.number) in donorReadBuckets
                        ),
                bookmark = s.src.bookmark,
                lastCharRead = s.src.lastCharRead,
                scanlator = null,
            )
        }
        if (newRows.isNotEmpty()) {
            runCatching { novelChapterRepository.addAllNovelChapters(newRows) }
                .onFailure { e ->
                    logcat { "localCanon novel $workId: ${e.message}" }
                    return 0
                }
            Injekt.get<CanonicalEntryUnifier>().invalidate()
        }
        return newRows.size
    }

    /** A canon row seeding from one donor chapter — identity + label. */
    private class CanonSeed<T>(
        val src: T,
        val seq: Int,
        val name: String,
        val number: Double,
        val kind: ChapterNumberParser.Kind,
    )

    /**
     * Build the local canon sequence from a favorite's cached chapter
     * rows, oldest→newest (sourceOrder is newest-first → descending
     * sort): seq = chronological index, deduped on parsed identity
     * (number + part + kind) so doubled carrier lists collapse the same
     * way they do in [CanonicalChapterMap.forWork]. Rows that don't
     * parse keep their slot — unparseable ≠ duplicate.
     */
    private fun <T> localCanonSeeds(
        chapters: List<T>,
        nameOf: (T) -> String,
        sourceOrderOf: (T) -> Long,
    ): List<CanonSeed<T>> {
        val seen = HashSet<Triple<Double, Int?, ChapterNumberParser.Kind>>()
        return chapters
            .sortedByDescending { sourceOrderOf(it) }
            .mapNotNull { ch ->
                val p = ChapterNumberParser.parse(nameOf(ch))
                if (p.number != null && !seen.add(Triple(p.number, p.part, p.kind))) {
                    return@mapNotNull null
                }
                ch to p
            }
            .mapIndexed { seq, (ch, p) ->
                CanonSeed(
                    src = ch,
                    seq = seq,
                    name = normalizeLabel(nameOf(ch), p.number),
                    number = p.number ?: 0.0,
                    kind = p.kind,
                )
            }
    }

    companion object {
        /** Stable per-row key — never a source URL. */
        fun canonUrl(workId: String, seq: Int) = "canon:$workId:$seq"

        /**
         * Bind each existing `canon:` row to its canon counterpart by
         * parsed name; when the counterpart's seq differs from the url's,
         * emit `(rowId, newUrl)`. One canon row per row — first bind wins
         * so two stale rows can't claim the same url.
         */
        internal fun realignUpdates(
            canon: List<CanonicalChapterMap.CanonChapter>,
            rows: List<Triple<Long, String, String>>,
            workId: String,
            bind: (String, List<CanonicalChapterMap.CanonChapter>) -> CanonicalChapterMap.CanonChapter?,
        ): List<Pair<Long, String>> {
            val claimedUrls = HashSet<String>()
            return rows.mapNotNull { (id, url, name) ->
                val c = bind(name, canon) ?: return@mapNotNull null
                val newUrl = canonUrl(workId, c.seq)
                if (newUrl == url || !claimedUrls.add(newUrl)) return@mapNotNull null
                id to newUrl
            }
        }

        /**
         * Map canonical rows to SChapters, newest-first like a real
         * source feed (`sourceOrder` 0 = newest). `scanlator` stays
         * null — carrier provenance belongs to the canon refs, not the
         * chapter card. `date_upload` carries the earliest carrier
         * upload date when the worker publishes one; 0 renders as
         * "N/A" rather than a fake "now".
         */
        fun toSChapters(
            canon: List<CanonicalChapterMap.CanonChapter>,
            workId: String = "",
        ): List<SChapter> = canon.asReversed().map { c ->
            SChapter.create().apply {
                url = canonUrl(workId, c.seq)
                name = normalizeLabel(c.label, c.number)
                chapter_number = (c.number ?: 0.0).toFloat()
                scanlator = null
                date_upload = c.dateUpload ?: 0L
            }
        }

        /** Trailing `(ch. N)` / `[Chapter N]` / `(Ep N)` markers —
         *  captured number is group 1. */
        private val TRAILING_NUM_MARKER = Regex(
            """[\s\-–—]*[(\[]\s*(?:ch\.?|chapter|ep\.?|episode|no\.?)\s*(\d+(?:\.\d+)?)\s*[)\]]\s*$""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Trim a source label's redundant trailing chapter marker when
         * it merely repeats the canonical number:
         *   "Ep. 1 - Beginning of the End (ch. 1)" → "Ep. 1 - Beginning of the End"
         * A marker whose number differs from the canonical number is
         * real information (e.g. source numbering offset) and is kept.
         */
        fun normalizeLabel(label: String, number: Double?): String {
            if (number == null) return label
            val m = TRAILING_NUM_MARKER.find(label) ?: return label
            val n = m.groupValues[1].toDoubleOrNull() ?: return label
            return if (n == number) {
                label.substring(0, m.range.first).trimEnd().ifEmpty { label }
            } else {
                label
            }
        }
    }
}
