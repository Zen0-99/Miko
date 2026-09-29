package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.tachiyomi.source.model.SChapter
import logcat.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter
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
 * Per-chapter availability rides in `scanlator`: the installed sources
 * that carry the row (canon `refs` ∩ installed golden packages). Empty
 * scanlator = canonical row not yet served by any installed source.
 *
 * Chapter `url` is `canon:{seq}` — the stable merge position, never a
 * source URL. The reader fan-out (13-04) resolves a carrier at read
 * time via `refs`.
 */
class CanonicalChapterSync(
    private val chapterMap: CanonicalChapterMap = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val goldenSources: GoldenSources = Injekt.get(),
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
) {

    /** Sync a canonical entry's chapter rows from the canon sequence.
     * Returns rows synced (0 when no canon sequence exists yet). */
    suspend fun sync(manga: Manga, workId: String, medium: String = "manga"): Int {
        val canon = chapterMap.forWork(workId)
        if (canon.isEmpty()) return 0
        val installed = goldenSources.forWork(workId, medium)
            .map { it.pkgName }
            .toSet()
        val sChapters = toSChapters(canon, installed, workId)
        val source = sourceManager.getOrStub(manga.source)
        val added = runCatching {
            syncChaptersWithSource.await(sChapters, manga, source)
        }.onFailure { e ->
            logcat { "canonChapterSync $workId: ${e.message}" }
        }.getOrElse { return -1 }
        // This stub is now serviceable — entries that cached "no
        // canonical" while it was chapter-less must re-evaluate.
        if (added.isNotEmpty()) Injekt.get<CanonicalEntryUnifier>().invalidate()
        return added.size
    }

    /**
     * Novel counterpart of [sync]: materializes canon rows as
     * [NovelChapter] rows on the canonical novel entry so its screen
     * has a chapter list. Novels have no SyncChaptersWithSource
     * equivalent — missing canon urls are inserted directly; existing
     * rows (canon or extension-fed) are left alone. `scanlator` carries
     * the installed carrier list so the reader can fan out per chapter.
     */
    suspend fun syncNovel(novel: Novel, workId: String): Int {
        val canon = chapterMap.forWork(workId)
        if (canon.isEmpty()) return 0
        val installed = goldenSources.forWork(workId, WorkIdResolver.MEDIUM_BOOKS)
            .map { it.pkgName }
            .toSet()
        val existingUrls = novelChapterRepository.getNovelChaptersByNovelId(novel.id)
            .mapTo(HashSet()) { it.url }

        // Newest-first so sourceOrder 0 = latest — matches extension feeds.
        val now = System.currentTimeMillis()
        val newChapters = canon.sortedByDescending { it.seq }.mapIndexedNotNull { i, c ->
            val url = canonUrl(workId, c.seq)
            if (url in existingUrls) return@mapIndexedNotNull null
            NovelChapter.create().copy(
                novelId = novel.id,
                url = url,
                name = c.label,
                chapterNumber = c.number ?: 0.0,
                sourceOrder = i.toLong(),
                dateFetch = now,
                dateUpload = 0L,
                scanlator = c.refs.keys
                    .filter { it in installed }
                    .joinToString(", ")
                    .ifBlank { null },
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
         * source feed (`sourceOrder` 0 = newest). [installed] is the set
         * of installed golden package names; the `scanlator` field
         * carries the installed carriers of each row.
         */
        fun toSChapters(
            canon: List<CanonicalChapterMap.CanonChapter>,
            installed: Set<String>,
            workId: String = "",
        ): List<SChapter> = canon.asReversed().map { c ->
            val carriers = c.refs.keys.filter { it in installed }
            SChapter.create().apply {
                url = canonUrl(workId, c.seq)
                name = c.label
                chapter_number = (c.number ?: 0.0).toFloat()
                scanlator = carriers.joinToString(", ").ifBlank { null }
                date_upload = 0L
            }
        }
    }
}
