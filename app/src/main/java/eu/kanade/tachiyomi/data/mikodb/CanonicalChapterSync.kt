package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.tachiyomi.source.model.SChapter
import logcat.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
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
        if (added.isNotEmpty()) Injekt.get<CanonicalEntryUnifier>().invalidate()
        return added.size
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

        // Newest-first so sourceOrder 0 = latest — matches extension feeds.
        val now = System.currentTimeMillis()
        val newChapters = canon.sortedByDescending { it.seq }.mapIndexedNotNull { i, c ->
            val url = canonUrl(workId, c.seq)
            if (url in existingUrls) return@mapIndexedNotNull null
            NovelChapter.create().copy(
                novelId = novel.id,
                url = url,
                name = normalizeLabel(c.label, c.number),
                chapterNumber = c.number ?: 0.0,
                sourceOrder = i.toLong(),
                dateFetch = now,
                dateUpload = c.dateUpload ?: 0L,
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

    companion object {
        /** Stable per-row key — never a source URL. */
        fun canonUrl(workId: String, seq: Int) = "canon:$workId:$seq"

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
