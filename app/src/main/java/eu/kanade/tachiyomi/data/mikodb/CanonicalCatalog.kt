package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
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
    private val networkToLocalManga: NetworkToLocalManga by injectLazy()
    private val networkToLocalNovel: NetworkToLocalNovel by injectLazy()

    /** Ranked catalog hits for [medium] ("manga" | "books"). */
    suspend fun search(
        query: String,
        medium: String,
        limit: Int = 60,
    ): List<MangaNovelIdBridge.WorkRow> =
        bridge.searchWorks(query, WorkIdResolver.allowedTypes(medium), limit)

    /**
     * Insert (or reuse) the canonical manga row for [work] and return its
     * database id for navigation. The resolved work is also persisted to
     * the `works_cache` delta so identity survives map-artifact removal.
     */
    suspend fun materializeMangaId(work: MangaNovelIdBridge.WorkRow): Long {
        bridge.upsert(work)
        val manga = Manga.create().copy(
            url = "$MANGA_URL_PREFIX${work.mikoWorkId}",
            title = work.title,
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
            title = work.title,
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
    }
}
