package eu.kanade.tachiyomi.data.mikodb

import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import java.util.concurrent.ConcurrentHashMap

/**
 * Unifies extension-source manga/novel entries with their canonical
 * `miko:`-URL row (NCM-007 / Phase 12-02) — the text-media counterpart
 * of [eu.kanade.tachiyomi.metadata.stream.CinemetaEntryUnifier].
 *
 * Extensions materialize their own rows when browsing — so "Solo
 * Leveling" can exist once per extension. When the user opens one of
 * those duplicates and a canonical row was already materialized (via the
 * canonical catalog), the detail screen redirects to the canonical entry:
 * one entry per real work.
 *
 * Unlike the anime unifier this does NOT auto-materialize: a canonical
 * row only exists after catalog materialization (12-01) so identity
 * never depends on a mid-open network call. LOCAL_MINT and ambiguous
 * resolutions never redirect — a guessed work id must not collapse
 * distinct extension entries onto one row.
 */
class CanonicalEntryUnifier(
    private val mangaRepository: MangaRepository,
    private val novelRepository: NovelRepository,
    private val workIdResolver: WorkIdResolver,
) {

    // entryId -> canonical Manga/Novel, or NoCanonical for "checked, no
    // mapping". ConcurrentHashMap can't hold nulls, so the sentinel is
    // required — getOrPut with a null result crashes with NPE.
    private val canonicalCache = ConcurrentHashMap<Long, Any>()

    /**
     * Returns the canonical [Manga] row for [manga], or null if the entry
     * is already canonical, unmapped, ambiguously mapped, or no canonical
     * row was materialized yet.
     */
    suspend fun resolve(manga: Manga): Manga? {
        if (manga.source == CanonicalCatalog.CANONICAL_SOURCE_ID ||
            CanonicalCatalog.isCanonicalUrl(manga.url)
        ) {
            return null
        }
        return cachedOrResolve(manga.id) {
            resolveWorkId(
                WorkIdResolver.Candidate(
                    medium = WorkIdResolver.MEDIUM_MANGA,
                    title = manga.title,
                    url = manga.url,
                    author = manga.author,
                ),
            )?.let { workId ->
                mangaRepository.getMangaByUrlAndSourceId(
                    CanonicalCatalog.MANGA_URL_PREFIX + workId,
                    CanonicalCatalog.CANONICAL_SOURCE_ID,
                )
            }
        } as? Manga
    }

    /** Same as [resolve] for novels and books (`mk:ol:` works). */
    suspend fun resolve(novel: Novel): Novel? {
        if (novel.source == CanonicalCatalog.CANONICAL_SOURCE_ID ||
            CanonicalCatalog.isCanonicalUrl(novel.url)
        ) {
            return null
        }
        return cachedOrResolve(novel.id) {
            resolveWorkId(
                WorkIdResolver.Candidate(
                    medium = WorkIdResolver.MEDIUM_BOOKS,
                    title = novel.title,
                    url = novel.url,
                    author = novel.author,
                    isbn = WorkIdResolver.isbnFromGenre(novel.genre),
                ),
            )?.let { workId ->
                novelRepository.getNovelByUrlAndSourceId(
                    CanonicalCatalog.NOVEL_URL_PREFIX + workId,
                    CanonicalCatalog.CANONICAL_SOURCE_ID,
                )
            }
        } as? Novel
    }

    /**
     * Substitute a history item's display fields with its canonical
     * entry's — extension-source history rows would otherwise show the
     * extension's title/cover. The row's ids stay untouched so
     * delete/navigation semantics are preserved.
     */
    suspend fun unify(item: MangaHistoryWithRelations): MangaHistoryWithRelations {
        if (item.coverData.sourceId == CanonicalCatalog.CANONICAL_SOURCE_ID) return item
        val canonical = resolve(mangaRepository.getMangaById(item.mangaId)) ?: return item
        return item.copy(
            title = canonical.title,
            coverData = canonical.asMangaCover(),
        )
    }

    suspend fun unify(item: NovelHistoryWithRelations): NovelHistoryWithRelations {
        if (item.coverData.sourceId == CanonicalCatalog.CANONICAL_SOURCE_ID) return item
        val canonical = resolve(novelRepository.getNovelById(item.novelId)) ?: return item
        return item.copy(
            title = canonical.title,
            coverData = canonical.asNovelCover(),
        )
    }

    /** Clears memoized verdicts (e.g. after a canonical row materializes). */
    fun invalidate() = canonicalCache.clear()

    private suspend fun resolveWorkId(candidate: WorkIdResolver.Candidate): String? {
        val resolved = try {
            workIdResolver.resolve(candidate)
        } catch (_: Exception) {
            null
        } ?: return null
        if (resolved.ambiguous ||
            resolved.confidence == WorkIdResolver.Confidence.LOCAL_MINT
        ) {
            return null
        }
        return resolved.value
    }

    private suspend inline fun cachedOrResolve(
        entryId: Long,
        crossinline resolve: suspend () -> Any?,
    ): Any? {
        if (canonicalCache.containsKey(entryId)) {
            return canonicalCache[entryId]
        }
        val resolved = resolve()
        canonicalCache[entryId] = resolved ?: NoCanonical
        return resolved
    }

    private object NoCanonical
}
