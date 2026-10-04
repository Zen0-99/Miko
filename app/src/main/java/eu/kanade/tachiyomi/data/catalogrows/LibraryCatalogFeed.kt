package eu.kanade.tachiyomi.data.catalogrows

import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.entries.shows.interactor.GetLibraryAnime
import tachiyomi.domain.entries.shows.model.asAnimeCover

/**
 * Local-library catalog backend for user-built home rows (Phase 18) —
 * rows derived from the user's own library, so no network at all.
 *
 * `genreNames` filter is a case-insensitive contains-match over the
 * entry's genre list; `sort` parses to [LibrarySort] with
 * [LibrarySort.RECENTLY_ADDED] as the fallback for unknown persisted
 * names. Targets are plain detail screens (no `fromSource` — the entries
 * are already local).
 */
class LibraryCatalogFeed(
    private val getLibraryManga: GetLibraryManga,
    private val getLibraryAnime: GetLibraryAnime,
    private val getLibraryNovels: GetLibraryNovels,
) {

    suspend fun fetch(spec: CatalogRowSpec): List<RowItem> = when (spec.medium) {
        WorkIdResolver.MEDIUM_MANGA -> fetchManga(spec)
        WorkIdResolver.MEDIUM_SHOWS -> fetchShows(spec)
        else -> fetchNovels(spec)
    }

    /**
     * The builder's facet-pill vocabulary for a library row — the
     * genres actually present on this medium's entries, so a pill is
     * always backed by real content.
     */
    suspend fun genreOptions(medium: String): List<String> = when (medium) {
        WorkIdResolver.MEDIUM_MANGA ->
            getLibraryManga.await().flatMap { it.manga.genre.orEmpty() }
        WorkIdResolver.MEDIUM_SHOWS ->
            getLibraryAnime.await().flatMap { it.anime.genre.orEmpty() }
        else ->
            getLibraryNovels.await().flatMap { it.novel.genre.orEmpty() }
    }.map { it.trim() }.filter { it.isNotEmpty() }
        .distinct().sortedBy { it.lowercase() }

    private suspend fun fetchManga(spec: CatalogRowSpec): List<RowItem> {
        val sort = parseLibrarySort(spec.sort)
        return getLibraryManga.await()
            .filter { lib ->
                spec.genreNames.isEmpty() ||
                    lib.manga.genre?.any { g ->
                        spec.genreNames.any { g.contains(it, ignoreCase = true) }
                    } == true
            }
            .let { list ->
                when (sort) {
                    LibrarySort.TITLE -> list.sortedBy { it.manga.title }
                    LibrarySort.RECENTLY_ADDED -> list.sortedByDescending { it.manga.dateAdded }
                    LibrarySort.LAST_READ -> list.sortedByDescending { it.lastRead }
                }
            }
            .take(ROW_ITEM_LIMIT)
            .map { lib ->
                RowItem(
                    title = lib.manga.title,
                    coverUrl = lib.manga.asMangaCover(),
                    subtitle = "${lib.totalChapters} ch",
                    target = RowTarget.LibraryEntry(lib.manga.id, spec.medium),
                    mintKey = "lib:${spec.medium}:${lib.manga.id}",
                )
            }
    }

    private suspend fun fetchShows(spec: CatalogRowSpec): List<RowItem> {
        val sort = parseLibrarySort(spec.sort)
        return getLibraryAnime.await()
            .filter { lib ->
                spec.genreNames.isEmpty() ||
                    lib.anime.genre?.any { g ->
                        spec.genreNames.any { g.contains(it, ignoreCase = true) }
                    } == true
            }
            .let { list ->
                when (sort) {
                    LibrarySort.TITLE -> list.sortedBy { it.anime.title }
                    LibrarySort.RECENTLY_ADDED -> list.sortedByDescending { it.anime.dateAdded }
                    LibrarySort.LAST_READ -> list.sortedByDescending { it.lastSeen }
                }
            }
            .take(ROW_ITEM_LIMIT)
            .map { lib ->
                RowItem(
                    title = lib.anime.title,
                    coverUrl = lib.anime.asAnimeCover(),
                    subtitle = "${lib.totalCount} ep",
                    target = RowTarget.LibraryEntry(lib.anime.id, spec.medium),
                    mintKey = "lib:${spec.medium}:${lib.anime.id}",
                )
            }
    }

    private suspend fun fetchNovels(spec: CatalogRowSpec): List<RowItem> {
        val sort = parseLibrarySort(spec.sort)
        return getLibraryNovels.await()
            .filter { lib ->
                spec.genreNames.isEmpty() ||
                    lib.novel.genre?.any { g ->
                        spec.genreNames.any { g.contains(it, ignoreCase = true) }
                    } == true
            }
            .let { list ->
                when (sort) {
                    LibrarySort.TITLE -> list.sortedBy { it.novel.title }
                    LibrarySort.RECENTLY_ADDED -> list.sortedByDescending { it.novel.dateAdded }
                    LibrarySort.LAST_READ -> list.sortedByDescending { it.lastRead }
                }
            }
            .take(ROW_ITEM_LIMIT)
            .map { lib ->
                RowItem(
                    title = lib.novel.title,
                    coverUrl = lib.novel.asNovelCover(),
                    subtitle = "${lib.totalChapters} ch",
                    target = RowTarget.LibraryEntry(lib.novel.id, spec.medium),
                    mintKey = "lib:${spec.medium}:${lib.novel.id}",
                )
            }
    }

    companion object {
        /** Page-1 library rows render at most this many cards. */
        internal const val ROW_ITEM_LIMIT = 20
    }
}

/** Persisted `spec.sort` → [LibrarySort]; unknown names fall back. */
internal fun parseLibrarySort(sort: String): LibrarySort =
    runCatching { LibrarySort.valueOf(sort) }.getOrDefault(LibrarySort.RECENTLY_ADDED)
