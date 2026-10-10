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
     * Returns the added rows (empty when no canon sequence exists yet). */
    suspend fun sync(manga: Manga, workId: String, medium: String = "manga"): List<Chapter> {
        val canon = chapterMap.forWork(workId)
        if (canon.isEmpty()) return emptyList()
        realignCanonRows(manga.id, workId, canon)
        val sChapters = toSChapters(canon, workId)
        val source = sourceManager.getOrStub(manga.source)
        val added = runCatching {
            syncChaptersWithSource.await(sChapters, manga, source)
        }.onFailure { e ->
            logcat { "canonChapterSync $workId: ${e.message}" }
        }.getOrElse { return emptyList() }
        repairCanonRows(manga.id, canon)
        // This stub is now serviceable — entries that cached "no
        // canonical" while it was chapter-less must re-evaluate.
        if (added.isNotEmpty()) {
            inheritReadBuckets(manga.id, added)
            Injekt.get<CanonicalEntryUnifier>().invalidate()
        }
        return added
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
        val plan = realignUpdates(
            canon,
            rows.mapNotNull { row ->
                if (!row.url.startsWith("canon:")) return@mapNotNull null
                Triple(row.id, row.url, row.name)
            },
            workId,
            chapterMap::bind,
        )
        if (plan.updates.isNotEmpty()) {
            runCatching {
                chapterRepository.updateAllChapters(plan.updates.map { ChapterUpdate(id = it.first, url = it.second) })
            }.onFailure { logcat { "canonChapterSync realign: ${it.message}" } }
        }
        if (plan.orphans.isEmpty()) return
        val byId = rows.associateBy { it.id }
        val merges = plan.orphans.mapNotNull { (loserId, winnerId) ->
            val loser = byId[loserId] ?: return@mapNotNull null
            val winner = byId[winnerId] ?: return@mapNotNull null
            val read = winner.read || loser.read
            val bookmark = winner.bookmark || loser.bookmark
            val lastPage = maxOf(winner.lastPageRead, loser.lastPageRead)
            val dateFetch = minOf(winner.dateFetch, loser.dateFetch)
            if (read == winner.read &&
                bookmark == winner.bookmark &&
                lastPage == winner.lastPageRead &&
                dateFetch == winner.dateFetch
            ) {
                null
            } else {
                ChapterUpdate(
                    id = winnerId,
                    read = read,
                    bookmark = bookmark,
                    lastPageRead = lastPage,
                    dateFetch = dateFetch,
                )
            }
        }
        runCatching {
            if (merges.isNotEmpty()) chapterRepository.updateAllChapters(merges)
            chapterRepository.removeChaptersWithIds(plan.orphans.map { it.first })
        }.onFailure { logcat { "canonChapterSync orphan cleanup: ${it.message}" } }
    }

    private suspend fun realignNovelRows(
        novelId: Long,
        workId: String,
        canon: List<CanonicalChapterMap.CanonChapter>,
    ) {
        val rows = novelChapterRepository.getNovelChaptersByNovelId(novelId)
        val plan = realignUpdates(
            canon,
            rows.mapNotNull { row ->
                if (!row.url.startsWith("canon:")) return@mapNotNull null
                Triple(row.id, row.url, row.name)
            },
            workId,
            chapterMap::bind,
        )
        if (plan.updates.isNotEmpty()) {
            runCatching {
                novelChapterRepository.updateAllNovelChapters(
                    plan.updates.map { NovelChapterUpdate(id = it.first, url = it.second) },
                )
            }.onFailure { logcat { "canonChapterSync novel realign: ${it.message}" } }
        }
        if (plan.orphans.isEmpty()) return
        val byId = rows.associateBy { it.id }
        val merges = plan.orphans.mapNotNull { (loserId, winnerId) ->
            val loser = byId[loserId] ?: return@mapNotNull null
            val winner = byId[winnerId] ?: return@mapNotNull null
            val read = winner.read || loser.read
            val bookmark = winner.bookmark || loser.bookmark
            val lastChar = maxOf(winner.lastCharRead, loser.lastCharRead)
            val dateFetch = minOf(winner.dateFetch, loser.dateFetch)
            if (read == winner.read &&
                bookmark == winner.bookmark &&
                lastChar == winner.lastCharRead &&
                dateFetch == winner.dateFetch
            ) {
                null
            } else {
                NovelChapterUpdate(
                    id = winnerId,
                    read = read,
                    bookmark = bookmark,
                    lastCharRead = lastChar,
                    dateFetch = dateFetch,
                )
            }
        }
        runCatching {
            if (merges.isNotEmpty()) novelChapterRepository.updateAllNovelChapters(merges)
            novelChapterRepository.removeChaptersWithIds(plan.orphans.map { it.first })
        }.onFailure { logcat { "canonChapterSync novel orphan cleanup: ${it.message}" } }
    }

    /**
     * Novel counterpart of [sync]: materializes canon rows as
     * [NovelChapter] rows on the canonical novel entry so its screen
     * has a chapter list. Novels have no SyncChaptersWithSource
     * equivalent — missing canon urls are inserted directly; existing
     * rows (canon or extension-fed) are left alone. `scanlator` stays
     * null — carrier provenance lives in the canon refs.
     */
    suspend fun syncNovel(novel: Novel, workId: String): List<NovelChapter> {
        val canon = chapterMap.forWork(workId)
        if (canon.isEmpty()) return emptyList()
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
                    return emptyList()
                }
            Injekt.get<CanonicalEntryUnifier>().invalidate()
        }
        return newChapters
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

    /**
     * Repair pass for local canon rows materialized by a stale parser:
     * rows re-parse their stored name against the donor feed — a row
     * whose number moved gets the donor's state re-copied (that
     * generation's read flags were painted by broken numbering, and the
     * donor copy is the only trustworthy state), while rows already
     * correct keep post-mint progress. Donor rows absent from canon
     * entirely (e.g. collapsed `exN` extras) are inserted under a
     * `canon:{wid}:r{seq}` url so they never collide with existing seq
     * slots; realign still re-binds them by name on promotion.
     */
    suspend fun reconcileLocal(manga: Manga, workId: String, donorChapters: List<Chapter>): Int {
        if (donorChapters.isEmpty()) return 0
        val seeds = localCanonSeeds(donorChapters, { it.name }, { it.sourceOrder })
        if (seeds.isEmpty()) return 0
        val existing = chapterRepository.getChapterByMangaId(manga.id)
            .filter { it.url.startsWith("canon:") }
        if (existing.isEmpty()) return 0
        val orderPos = HashMap<String, Long>(seeds.size)
        val seedByName = HashMap<String, CanonSeed<Chapter>>(seeds.size)
        seeds.forEachIndexed { seq, s ->
            orderPos[s.name] = (seeds.size - 1 - seq).toLong()
            seedByName.putIfAbsent(s.name, s)
        }
        val updates = existing.mapNotNull { row ->
            val s = seedByName[row.name] ?: return@mapNotNull null
            val stale = s.number != row.chapterNumber
            val so = orderPos.getValue(row.name)
            if (!stale && row.sourceOrder == so) return@mapNotNull null
            ChapterUpdate(
                id = row.id,
                chapterNumber = s.number.takeIf { stale },
                sourceOrder = so,
                read = s.src.read.takeIf { stale },
                bookmark = s.src.bookmark.takeIf { stale },
                lastPageRead = s.src.lastPageRead.takeIf { stale },
            )
        }
        if (updates.isNotEmpty()) {
            runCatching { chapterRepository.updateAllChapters(updates) }
                .onFailure { logcat { "localCanon reconcile $workId: ${it.message}" } }
        }
        val missing = insertMissingSeeds(seeds, existing.mapTo(HashSet()) { it.name }, orderPos, workId) { s, url, so ->
            Chapter.create().copy(
                mangaId = manga.id,
                url = url,
                name = s.name,
                chapterNumber = s.number,
                sourceOrder = so,
                dateFetch = s.src.dateFetch.takeIf { it > 0 } ?: System.currentTimeMillis(),
                dateUpload = s.src.dateUpload,
                read = s.src.read,
                bookmark = s.src.bookmark,
                lastPageRead = s.src.lastPageRead,
                scanlator = null,
            )
        }
        if (missing.isNotEmpty()) {
            runCatching { chapterRepository.addAllChapters(missing) }
                .onFailure { e ->
                    logcat { "localCanon reconcile insert $workId: ${e.message}" }
                    return updates.size
                }
        }
        if (updates.isNotEmpty() || missing.isNotEmpty()) {
            Injekt.get<CanonicalEntryUnifier>().invalidate()
            logcat { "localCanon reconcile $workId: ${updates.size} fixed, ${missing.size} restored" }
        }
        return updates.size + missing.size
    }

    /** Same as [reconcileLocal] for novels/books — `lastCharRead` carries position. */
    suspend fun reconcileLocalNovel(novel: Novel, workId: String, donorChapters: List<NovelChapter>): Int {
        if (donorChapters.isEmpty()) return 0
        val seeds = localCanonSeeds(donorChapters, { it.name }, { it.sourceOrder })
        if (seeds.isEmpty()) return 0
        val existing = novelChapterRepository.getNovelChaptersByNovelId(novel.id)
            .filter { it.url.startsWith("canon:") }
        if (existing.isEmpty()) return 0
        val orderPos = HashMap<String, Long>(seeds.size)
        val seedByName = HashMap<String, CanonSeed<NovelChapter>>(seeds.size)
        seeds.forEachIndexed { seq, s ->
            orderPos[s.name] = (seeds.size - 1 - seq).toLong()
            seedByName.putIfAbsent(s.name, s)
        }
        val updates = existing.mapNotNull { row ->
            val s = seedByName[row.name] ?: return@mapNotNull null
            val stale = s.number != row.chapterNumber
            val so = orderPos.getValue(row.name)
            if (!stale && row.sourceOrder == so) return@mapNotNull null
            NovelChapterUpdate(
                id = row.id,
                chapterNumber = s.number.takeIf { stale },
                sourceOrder = so,
                read = s.src.read.takeIf { stale },
                bookmark = s.src.bookmark.takeIf { stale },
                lastCharRead = s.src.lastCharRead.takeIf { stale },
            )
        }
        if (updates.isNotEmpty()) {
            runCatching { novelChapterRepository.updateAllNovelChapters(updates) }
                .onFailure { logcat { "localCanon reconcile novel $workId: ${it.message}" } }
        }
        val missing = insertMissingSeeds(seeds, existing.mapTo(HashSet()) { it.name }, orderPos, workId) { s, url, so ->
            NovelChapter.create().copy(
                novelId = novel.id,
                url = url,
                name = s.name,
                chapterNumber = s.number,
                sourceOrder = so,
                dateFetch = s.src.dateFetch.takeIf { it > 0 } ?: System.currentTimeMillis(),
                dateUpload = s.src.dateUpload,
                read = s.src.read,
                bookmark = s.src.bookmark,
                lastCharRead = s.src.lastCharRead,
                scanlator = null,
            )
        }
        if (missing.isNotEmpty()) {
            runCatching { novelChapterRepository.addAllNovelChapters(missing) }
                .onFailure { e ->
                    logcat { "localCanon reconcile novel insert $workId: ${e.message}" }
                    return updates.size
                }
        }
        if (updates.isNotEmpty() || missing.isNotEmpty()) {
            Injekt.get<CanonicalEntryUnifier>().invalidate()
            logcat { "localCanon reconcile novel $workId: ${updates.size} fixed, ${missing.size} restored" }
        }
        return updates.size + missing.size
    }

    /** Seeds whose name has no existing canon row → insert rows under
     *  the `:r{seq}` url namespace, at the seed's own list position. */
    private fun <T, R> insertMissingSeeds(
        seeds: List<CanonSeed<T>>,
        existingNames: HashSet<String>,
        orderPos: HashMap<String, Long>,
        workId: String,
        build: (CanonSeed<T>, String, Long) -> R,
    ): List<R> = seeds.mapNotNull { s ->
        if (s.name in existingNames) return@mapNotNull null
        build(s, "canon:$workId:r${s.seq}", orderPos.getValue(s.name))
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

        /** Result of a realign pass — url moves plus the rows that lost
         *  a seq claim and must be merged away by the caller. */
        internal data class RealignPlan(
            /** (rowId, newUrl) — winner rows whose url moves to their
             *  bound seq. */
            val updates: List<Pair<Long, String>>,
            /** (loserId, winnerId) — rows that bound to (or already sat
             *  on) a seq url another row claims; losers are true
             *  duplicates — caller merges their read/bookmark/position
             *  state into the winner, then deletes them. */
            val orphans: List<Pair<Long, Long>>,
        ) {
            fun isEmpty() = updates.isEmpty() && orphans.isEmpty()
        }

        /**
         * Bind each existing `canon:` row to its canon counterpart by
         * parsed name and resolve claims per target url — one row per
         * seq, guaranteed. A row already sitting on the target url wins
         * the claim; the first bound row wins otherwise. Every other
         * row pointing at the same seq — including rows whose url
         * already equals the target (the old early-return skipped them
         * without claiming, so a stale sibling could take the url and
         * leave two rows bound to one seq forever) — becomes an orphan.
         * Rows that fail to bind but physically sit on a claimed url
         * collide on materialization, so they orphan too.
         */
        internal fun realignUpdates(
            canon: List<CanonicalChapterMap.CanonChapter>,
            rows: List<Triple<Long, String, String>>,
            workId: String,
            bind: (String, List<CanonicalChapterMap.CanonChapter>) -> CanonicalChapterMap.CanonChapter?,
        ): RealignPlan {
            val bound = rows.mapNotNull { (id, url, name) ->
                val c = bind(name, canon) ?: return@mapNotNull null
                Triple(id, url, canonUrl(workId, c.seq))
            }
            val updates = ArrayList<Pair<Long, String>>(bound.size)
            val orphans = ArrayList<Pair<Long, Long>>()
            val claimed = HashMap<String, Long>()
            bound.groupBy { it.third }.forEach { (target, group) ->
                val winner = group.firstOrNull { it.second == target } ?: group.first()
                claimed[target] = winner.first
                if (winner.second != target) updates += winner.first to target
                group.forEach {
                    if (it.first != winner.first) orphans += it.first to winner.first
                }
            }
            val boundIds = bound.mapTo(HashSet()) { it.first }
            rows.forEach { (id, url, _) ->
                if (id in boundIds) return@forEach
                claimed[url]?.let { winner -> orphans += id to winner }
            }
            return RealignPlan(updates, orphans)
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
