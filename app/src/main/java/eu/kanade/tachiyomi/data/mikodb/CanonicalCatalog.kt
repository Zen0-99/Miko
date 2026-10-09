package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository
import uy.kohesive.injekt.injectLazy

/**
 * Canonical catalog for manga/novel entries (NCM-007 / Phase 12-01) — the
 * `miko-manga-map.db` counterpart of browsing the Cinemeta catalog.
 *
 * Search resolves normalized titles against the map (plus the works_cache
 * delta for lazily-minted works); materialization inserts a canonical
 * entry row under [CANONICAL_SOURCE_ID] with a `miko:{medium}:{workId}`
 * URL. These rows carry no chapters themselves — plan 12-02's unifier
 * routes their detail screens to provider-linked extension sources.
 *
 * Mediums never mix: a manga-medium search is type-filtered so a novel
 * work with the same title stays a distinct canonical entry.
 */
class CanonicalCatalog {

    private val bridge: MangaNovelIdBridge by injectLazy()
    private val resolver: WorkIdResolver by injectLazy()
    private val networkToLocalManga: NetworkToLocalManga by injectLazy()
    private val networkToLocalNovel: NetworkToLocalNovel by injectLazy()
    private val mangaRepository: MangaRepository by injectLazy()
    private val novelRepository: NovelRepository by injectLazy()

    /**
     * Ranked catalog hits for [medium] ("manga" | "books"). [kindsCsv]
     * narrows to a sub-type pill's kinds ("manhwa,manhua,webtoon,…"),
     * intersected with the medium's allowed types — a pill can only
     * scope inside its own carrier.
     */
    suspend fun search(
        query: String,
        medium: String,
        kindsCsv: String? = null,
        limit: Int = 60,
    ): List<MangaNovelIdBridge.WorkRow> {
        val allowed = WorkIdResolver.allowedTypes(medium)
        val scoped = kindsCsv?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?.intersect(allowed)
            ?.takeIf { it.isNotEmpty() }
        return bridge.searchWorks(query, scoped ?: allowed, limit)
    }

    /**
     * Insert (or reuse) the canonical manga row for [work] and return its
     * database id for navigation. The resolved work is also persisted to
     * the `works_cache` delta so identity survives map-artifact removal.
     */
    suspend fun materializeMangaId(work: MangaNovelIdBridge.WorkRow): Long {
        bridge.upsert(work)
        val manga = Manga.create().copy(
            url = "$MANGA_URL_PREFIX${work.mikoWorkId}",
            title = TitleNormalizer.displayTitle(work.title),
            author = work.author,
            description = work.description,
            // Genres come only from real catalog/enrichment data — the
            // work type ("oel", "manga") is taxonomy, not a genre tag.
            genre = work.genres?.takeIf { it.isNotEmpty() }.orEmpty(),
            thumbnailUrl = work.coverUrl,
            status = statusOf(work.status, manga = true),
            source = CANONICAL_SOURCE_ID,
            initialized = true,
        )
        return networkToLocalManga.await(manga).id
    }

    /** Same as [materializeMangaId] for novels and books (`mk:ol:`). */
    suspend fun materializeNovelId(work: MangaNovelIdBridge.WorkRow): Long {
        bridge.upsert(work)
        val novel = Novel.create().copy(
            url = "$NOVEL_URL_PREFIX${work.mikoWorkId}",
            title = TitleNormalizer.displayTitle(work.title),
            author = work.author,
            description = work.description,
            genre = work.genres?.takeIf { it.isNotEmpty() }.orEmpty(),
            thumbnailUrl = work.coverUrl,
            status = statusOf(work.status, manga = false),
            source = CANONICAL_SOURCE_ID,
            initialized = true,
        )
        return networkToLocalNovel.await(novel).id
    }

    /**
     * Local library rows bound to [works], keyed by `mikoWorkId`.
     *
     * Two bind paths:
     *  - Favorited canonical rows (`miko:` URL, source [CANONICAL_SOURCE_ID])
     *    bind by URL-carried work id — exact, no title guesswork. These
     *    rows ARE the library entry; the badge and tap-through land on
     *    the canonical row itself.
     *  - Favorited extension entries bind on normalized title (+ author
     *    containment when both sides carry one — extension author strings
     *    differ in shape).
     *
     * The map values are the bound library entries — taps on bound cards
     * open the library entry (not a fresh canonical fork), and bound
     * cards show the library row's cover so discovery and library agree.
     */
    suspend fun localLibraryEntryIds(
        works: List<MangaNovelIdBridge.WorkRow>,
        medium: String,
    ): Map<String, BoundEntry> {
        if (works.isEmpty()) return emptyMap()
        val isBooks = medium == WorkIdResolver.MEDIUM_BOOKS
        val bound = HashMap<String, BoundEntry>()
        val workIds = works.mapTo(HashSet()) { it.mikoWorkId }

        // Canonical favorites — the miko: URL carries the work id.
        val canonicalRows: List<BoundRow> = if (isBooks) {
            novelRepository.getNovelFavorites()
                .filter { it.source == CANONICAL_SOURCE_ID }
                .map { BoundRow(it.id, it.title, it.author, it.url, it.thumbnailUrl) }
        } else {
            mangaRepository.getMangaFavorites()
                .filter { it.source == CANONICAL_SOURCE_ID }
                .map { BoundRow(it.id, it.title, it.author, it.url, it.thumbnailUrl) }
        }
        canonicalRows.forEach { row ->
            val workId = if (isBooks) {
                workIdFromNovelUrl(row.url)
            } else {
                workIdFromMangaUrl(row.url)
            }
            if (workId != null && workId in workIds) bound[workId] = BoundEntry(row.id, row.coverUrl)
        }

        val favorites = if (isBooks) {
            novelRepository.getNovelFavorites()
                .filter { it.source != CANONICAL_SOURCE_ID }
                .map { BoundRow(it.id, it.title, it.author, it.url, it.thumbnailUrl) }
        } else {
            mangaRepository.getMangaFavorites()
                .filter { it.source != CANONICAL_SOURCE_ID }
                .map { BoundRow(it.id, it.title, it.author, it.url, it.thumbnailUrl) }
        }
        if (favorites.isEmpty()) return bound

        // Identity bind: the favorite row's URL-carried provider id (or
        // its works_cache title row) resolves to the SAME canonical work
        // id the feed carries — this catches the title-variant cases the
        // normalized-title pass misses ("Bleach (Colored)", "ONE
        // PUNCH-MAN", romanized alternates). Local-only resolve: no
        // Worker round-trip, no mints — misses simply fall through.
        if (favorites.isNotEmpty()) {
            for (row in favorites) {
                val workId = resolver.resolveLocal(
                    WorkIdResolver.Candidate(
                        medium = medium,
                        title = row.title.orEmpty(),
                        url = row.url,
                        author = row.author,
                        isBook = isBooks,
                        // 22-06 (B): BoundRow carries no viewer flags —
                        // the persisted scheme url is the audio signal.
                        isAudiobook = isBooks &&
                            eu.kanade.tachiyomi.source.novel.AudioChapterSchemes
                                .isAudioNovelUrl(row.url),
                    ),
                )?.takeUnless { it.ambiguous }?.value ?: continue
                if (workId in workIds && workId !in bound) bound[workId] = BoundEntry(row.id, row.coverUrl)
            }
        }

        for (work in works) {
            // A canonical favorite already bound by work id wins over a
            // title-matched extension duplicate.
            if (work.mikoWorkId in bound) continue
            val wanted = listOfNotNull(work.title, work.romanizedTitle, work.nativeTitle)
                .map(TitleNormalizer::normalize)
                .filter { it.isNotEmpty() }
                .toSet()
            if (wanted.isEmpty()) continue
            val workAuthor = TitleNormalizer.normalize(work.author)
            val hit = favorites.firstOrNull { row ->
                TitleNormalizer.normalize(row.title) in wanted &&
                    authorFits(row.author, workAuthor)
            } ?: continue
            bound[work.mikoWorkId] = BoundEntry(hit.id, hit.coverUrl)
        }
        return bound
    }

    /**
     * Covers held by materialized canonical entry rows (`miko:` url,
     * [CANONICAL_SOURCE_ID]) for [workIds] — favorited or not. A detail
     * visit can heal the row's thumbnail (`CanonicalPipelineRunner`
     * backfills dead/blank covers from the carrier) after the feed
     * snapshot was taken; discovery surfaces re-read these on resume so
     * the grid picks the healed art up.
     */
    suspend fun canonicalEntryCovers(
        workIds: Collection<String>,
        medium: String,
    ): Map<String, String> {
        if (workIds.isEmpty()) return emptyMap()
        val isBooks = medium == WorkIdResolver.MEDIUM_BOOKS
        val prefix = if (isBooks) NOVEL_URL_PREFIX else MANGA_URL_PREFIX
        val covers = HashMap<String, String>(workIds.size)
        for (workId in workIds) {
            val url = prefix + workId
            val cover = if (isBooks) {
                novelRepository.getNovelByUrlAndSourceId(url, CANONICAL_SOURCE_ID)?.thumbnailUrl
            } else {
                mangaRepository.getMangaByUrlAndSourceId(url, CANONICAL_SOURCE_ID)?.thumbnailUrl
            }
            if (!cover.isNullOrBlank()) covers[workId] = cover
        }
        return covers
    }

    /** A library entry bound to a discovery work — row id + its cover so
     *  bound cards render the same art the library shows. */
    data class BoundEntry(val entryId: Long, val coverUrl: String?)

    private class BoundRow(
        val id: Long,
        val title: String?,
        val author: String?,
        val url: String,
        val coverUrl: String?,
    )

    companion object {
        /**
         * Canonical rows live under source id 0 in the manga/novel tables —
         * the same convention Cinemeta uses for anime. The `miko:` URL
         * scheme distinguishes canonical rows from stub-source rows.
         */
        const val CANONICAL_SOURCE_ID = 0L
        const val MANGA_URL_PREFIX = "miko:manga:"
        const val NOVEL_URL_PREFIX = "miko:novel:"

        fun isCanonicalUrl(url: String) =
            url.startsWith(MANGA_URL_PREFIX) || url.startsWith(NOVEL_URL_PREFIX)

        fun workIdFromMangaUrl(url: String) =
            url.removePrefix(MANGA_URL_PREFIX).takeIf { it != url && it.isNotBlank() }

        fun workIdFromNovelUrl(url: String) =
            url.removePrefix(NOVEL_URL_PREFIX).takeIf { it != url && it.isNotBlank() }

        /** MangaBaka status vocabulary → source-api status constants. */
        internal fun statusOf(status: String?, manga: Boolean): Long {
            val unknown = if (manga) SManga.UNKNOWN else SNovel.UNKNOWN
            val s = status?.lowercase() ?: return unknown.toLong()
            fun const(ongoing: Int, completed: Int, hiatus: Int, cancelled: Int) = when {
                "releas" in s || "ongoing" in s || "publish" in s -> ongoing
                "complet" in s || "finish" in s || "ended" in s -> completed
                "hiat" in s || "pause" in s -> hiatus
                "cancel" in s || "discontinu" in s || "drop" in s -> cancelled
                else -> unknown
            }
            return if (manga) {
                const(SManga.ONGOING, SManga.COMPLETED, SManga.ON_HIATUS, SManga.CANCELLED).toLong()
            } else {
                const(SNovel.ONGOING, SNovel.COMPLETED, SNovel.ON_HIATUS, SNovel.CANCELLED).toLong()
            }
        }

        /**
         * Library-row author → work-author compatibility gate. Either side
         * blank fits; ordered-string containment fits; otherwise compare
         * token bags so name-order flips ("KUBO Tite" vs "Tite Kubo") and
         * co-author subsets ("MURATA Yuusuke, ONE" vs "ONE") still bind.
         */
        internal fun authorFits(entryAuthor: String?, workAuthor: String): Boolean {
            if (workAuthor.isEmpty() || entryAuthor.isNullOrBlank()) return true
            val entry = TitleNormalizer.normalize(entryAuthor)
            if (entry == workAuthor || entry.contains(workAuthor) || workAuthor.contains(entry)) {
                return true
            }
            val entryTokens = entry.split(' ').filter(String::isNotBlank).toSet()
            val workTokens = workAuthor.split(' ').filter(String::isNotBlank).toSet()
            return entryTokens.isNotEmpty() && workTokens.isNotEmpty() &&
                (entryTokens.containsAll(workTokens) || workTokens.containsAll(entryTokens))
        }
    }
}
