package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import logcat.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Reader fan-out (Phase 13-04) — the `StreamResolver` equivalent for
 * text media: canon chapter → best installed carrier → `getPageList`,
 * walking candidates on failure.
 *
 * Candidates come from the canon row's `refs` ∩ installed golden
 * sources. Ordering: last-working source for this work (stickiness) →
 * tier (S→A→B) → remaining refs order. On success the carrier is
 * remembered so a dead/removed source doesn't get re-probed every
 * chapter.
 *
 * Note: `refs` only exists for feeds that contributed to canonization.
 * A B/C source that carries the chapter but never fed canon won't be
 * a candidate — alignment-based expansion (bind any installed source's
 * own chapter list onto canon numbers) is the documented follow-up.
 */
class CanonicalChapterRouter(
    private val chapterMap: CanonicalChapterMap = Injekt.get(),
    private val goldenSources: GoldenSources = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
) {

    data class Candidate(
        val sourceId: Long,
        val pkgName: String,
        val tier: String,
        val chapterUrl: String,
    )

    data class Resolved(
        val source: HttpSource,
        val chapter: SChapter,
        val pages: List<Page>,
        val pkgName: String,
    )

    /** workId → last-working carrier pkgName. Process-scoped. */
    private val lastWorking = ConcurrentHashMap<String, String>()

    /** Ordered candidates for a canonical row — refs ∩ installed. */
    suspend fun candidates(workId: String, seq: Int): List<Candidate> =
        candidatesFor(workId, seq, WorkIdResolver.MEDIUM_MANGA) { sourceManager.get(it) != null }

    /** Ordered candidates for a canonical book/novel row — refs ∩ installed. */
    suspend fun novelCandidates(workId: String, seq: Int): List<Candidate> =
        candidatesFor(workId, seq, WorkIdResolver.MEDIUM_BOOKS) { novelSourceManager.get(it) != null }

    private suspend fun candidatesFor(
        workId: String,
        seq: Int,
        medium: String,
        isInstalled: (Long) -> Boolean,
    ): List<Candidate> {
        val row = chapterMap.forWork(workId).firstOrNull { it.seq == seq } ?: return emptyList()
        val byPkg = goldenSources.forWork(workId, medium).associateBy { it.pkgName }
        val sticky = lastWorking[workId]
        return row.refs.mapNotNull { (pkg, url) ->
            val g = byPkg[pkg] ?: return@mapNotNull null
            val sourceId = g.sourceIds.firstOrNull(isInstalled)
                ?: return@mapNotNull null
            Candidate(sourceId, pkg, g.tier, url)
        }.sortedWith(
            compareBy(
                { if (it.pkgName == sticky) 0 else 1 },
                { tierRank(it.tier) },
            ),
        )
    }

    /**
     * Walk candidates, fetching `getPageList` on each until one returns
     * pages. Returns the winning source + routed chapter + page list,
     * or null when no installed carrier serves the chapter.
     */
    suspend fun resolve(workId: String, seq: Int, label: String): Resolved? {
        for (c in candidates(workId, seq)) {
            val src = sourceManager.get(c.sourceId) as? HttpSource ?: continue
            val sch = SChapter.create().apply {
                url = c.chapterUrl
                name = label
            }
            val pages = runCatching { src.getPageList(sch) }
                .onFailure { logcat { "canonRouter $workId@${c.pkgName}: ${it.message}" } }
                .getOrNull()
                ?: continue
            if (pages.isEmpty()) continue
            lastWorking[workId] = c.pkgName
            return Resolved(src, sch, pages, c.pkgName)
        }
        return null
    }

    /** Read entry-point: resolve a materialized canon chapter row. */
    suspend fun resolveForRead(manga: Manga, chapterUrl: String, label: String): Resolved? {
        val workId = CanonicalCatalog.workIdFromMangaUrl(manga.url) ?: return null
        val seq = seqFromCanonUrl(workId, chapterUrl) ?: return null
        return resolve(workId, seq, label)
    }

    /**
     * Novel counterpart of [resolveForRead]: canon chapter → best
     * installed novel carrier → `getChapterText`, walking candidates
     * on failure. Returns the chapter text, or null when no installed
     * carrier serves it.
     */
    suspend fun resolveNovelText(novel: Novel, chapterUrl: String, label: String): String? {
        val workId = CanonicalCatalog.workIdFromNovelUrl(novel.url) ?: return null
        val seq = seqFromCanonUrl(workId, chapterUrl) ?: return null
        for (c in novelCandidates(workId, seq)) {
            val src = novelSourceManager.get(c.sourceId) as? NovelCatalogueSource ?: continue
            val sch = SNovelChapter.create().apply {
                url = c.chapterUrl
                name = label
            }
            val text = runCatching { src.getChapterText(sch) }
                .onFailure { logcat { "canonRouter(novel) $workId@${c.pkgName}: ${it.message}" } }
                .getOrNull()
                ?: continue
            if (text.isBlank()) continue
            lastWorking[workId] = c.pkgName
            return text
        }
        return null
    }

    companion object {
        fun isCanonUrl(url: String) = url.startsWith("canon:")

        /** `canon:{workId}:{seq}` → seq. */
        fun seqFromCanonUrl(workId: String, url: String): Int? =
            url.removePrefix("canon:$workId:").toIntOrNull()

        private fun tierRank(tier: String) = when (tier.uppercase()) {
            "S" -> 0
            "A" -> 1
            "B" -> 2
            "C" -> 3
            else -> 4
        }
    }
}
