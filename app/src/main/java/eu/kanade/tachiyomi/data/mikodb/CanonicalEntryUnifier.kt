package eu.kanade.tachiyomi.data.mikodb

import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository
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
    private val chapterRepository: ChapterRepository,
    private val novelChapterRepository: NovelChapterRepository,
) {

    // entryId -> canonical Manga/Novel, or NoCanonical for "checked, no
    // mapping". ConcurrentHashMap can't hold nulls, so the sentinel is
    // required — getOrPut with a null result crashes with NPE.
    private val canonicalCache = ConcurrentHashMap<Long, Any>()

    /**
     * Returns the canonical [Manga] row for [manga], or null if the entry
     * is already canonical, unmapped, ambiguously mapped, or no canonical
     * row was materialized yet.
     *
     * Redirect is gated on the canonical row actually being serviceable:
     * it must carry synced chapter rows. A materialized-but-empty stub
     * would otherwise hijack a working extension entry and show "0
     * chapters" — the stub is strictly worse than the extension row.
     * Favorites are never redirected either way: read state, tracking
     * and downloads live on the extension entry and do not transfer.
     */
    suspend fun resolve(manga: Manga): Manga? {
        if (manga.favorite ||
            manga.source == CanonicalCatalog.CANONICAL_SOURCE_ID ||
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
                )?.takeIf { canonical ->
                    chapterRepository.getChapterByMangaId(canonical.id).isNotEmpty()
                }?.also { canonical ->
                    preferExtensionCover(manga, canonical)
                }
            }
        } as? Manga
    }

    /**
     * Local-only variant of [resolve] — identifies the work purely from
     * `works_cache` (never the Worker, never a mint). A canonical row can
     * only exist for a work already materialized into the cache, so a
     * local miss means there is nothing to redirect to. Used where the
     * canonical id is needed without blocking on network (home heroes).
     *
     * Positive verdicts feed [canonicalCache] — a local hit is identical
     * to what [resolve] would return. Misses are NOT cached: the cache
     * can gain the work later, unlike a full-resolve verdict.
     */
    suspend fun resolveLocal(manga: Manga): Manga? {
        if (manga.favorite ||
            manga.source == CanonicalCatalog.CANONICAL_SOURCE_ID ||
            CanonicalCatalog.isCanonicalUrl(manga.url)
        ) {
            return null
        }
        when (val cached = canonicalCache[manga.id]) {
            is Manga -> return cached
            NoCanonical -> return null
        }
        val canonical = resolveLocalWorkId(
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
            )?.takeIf { row ->
                chapterRepository.getChapterByMangaId(row.id).isNotEmpty()
            }
        }
        if (canonical != null) {
            canonicalCache[manga.id] = canonical
            preferExtensionCover(manga, canonical)
        }
        return canonical
    }

    /** Same as [resolveLocal] for novels and books (`mk:ol:` works). */
    suspend fun resolveLocal(novel: Novel): Novel? {
        if (novel.favorite ||
            novel.source == CanonicalCatalog.CANONICAL_SOURCE_ID ||
            CanonicalCatalog.isCanonicalUrl(novel.url)
        ) {
            return null
        }
        when (val cached = canonicalCache[novel.id]) {
            is Novel -> return cached
            NoCanonical -> return null
        }
        val canonical = resolveLocalWorkId(
            WorkIdResolver.Candidate(
                medium = WorkIdResolver.MEDIUM_BOOKS,
                title = novel.title,
                url = novel.url,
                author = novel.author,
                isbn = WorkIdResolver.isbnFromGenre(novel.genre),
                isAudiobook = novel.isAudiobook,
            ),
        )?.let { workId ->
            novelRepository.getNovelByUrlAndSourceId(
                CanonicalCatalog.NOVEL_URL_PREFIX + workId,
                CanonicalCatalog.CANONICAL_SOURCE_ID,
            )?.takeIf { row ->
                novelChapterRepository.getNovelChaptersByNovelId(row.id).isNotEmpty()
            }
        }
        if (canonical != null) {
            canonicalCache[novel.id] = canonical
            preferExtensionCover(novel, canonical)
        }
        return canonical
    }

    /** Same as [resolve] for novels and books (`mk:ol:` works). */
    suspend fun resolve(novel: Novel): Novel? {
        if (novel.favorite ||
            novel.source == CanonicalCatalog.CANONICAL_SOURCE_ID ||
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
                    isAudiobook = novel.isAudiobook,
                ),
            )?.let { workId ->
                novelRepository.getNovelByUrlAndSourceId(
                    CanonicalCatalog.NOVEL_URL_PREFIX + workId,
                    CanonicalCatalog.CANONICAL_SOURCE_ID,
                )?.takeIf { canonical ->
                    novelChapterRepository.getNovelChaptersByNovelId(canonical.id).isNotEmpty()
                }?.also { canonical ->
                    preferExtensionCover(novel, canonical)
                }
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

    /**
     * Redirect swaps the entry wholesale — the canonical row would show
     * its own (usually lower-quality seeded) cover even when the
     * extension entry carried recognized hi-res art. Carry the better
     * URL over so the redirect doesn't visibly downgrade the cover.
     * Non-preferred extension covers never win: the canonical seed is
     * already the better art in that case.
     */
    private suspend fun preferExtensionCover(from: Manga, to: Manga) {
        if (eu.kanade.tachiyomi.data.cover.HiResCoverResolver.isPreferredCoverUrl(from.thumbnailUrl) &&
            !eu.kanade.tachiyomi.data.cover.HiResCoverResolver.isPreferredCoverUrl(to.thumbnailUrl)
        ) {
            mangaRepository.updateManga(
                tachiyomi.domain.entries.manga.model.MangaUpdate(
                    id = to.id,
                    thumbnailUrl = from.thumbnailUrl,
                    coverLastModified = java.time.Instant.now().toEpochMilli(),
                ),
            )
        }
    }

    private suspend fun preferExtensionCover(from: Novel, to: Novel) {
        if (eu.kanade.tachiyomi.data.cover.HiResCoverResolver.isPreferredCoverUrl(from.thumbnailUrl) &&
            !eu.kanade.tachiyomi.data.cover.HiResCoverResolver.isPreferredCoverUrl(to.thumbnailUrl)
        ) {
            novelRepository.updateNovel(
                tachiyomi.domain.entries.novel.model.NovelUpdate(
                    id = to.id,
                    thumbnailUrl = from.thumbnailUrl,
                    coverLastModified = java.time.Instant.now().toEpochMilli(),
                ),
            )
        }
    }

    /** Clears memoized verdicts (e.g. after a canonical row materializes). */
    fun invalidate() = canonicalCache.clear()

    private suspend fun resolveLocalWorkId(candidate: WorkIdResolver.Candidate): String? {
        val resolved = try {
            workIdResolver.resolveLocal(candidate)
        } catch (_: Exception) {
            null
        } ?: return null
        return resolved.value.takeUnless { resolved.ambiguous }
    }

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
