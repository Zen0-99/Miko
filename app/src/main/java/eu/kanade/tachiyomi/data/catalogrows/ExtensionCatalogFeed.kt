package eu.kanade.tachiyomi.data.catalogrows

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.entries.shows.model.AnimeCover
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager

/**
 * Extension-source catalog backend for user-built home rows (Phase 18) —
 * the popular/latest page-1 feed of a single source, routed by the spec's
 * medium to the matching source manager.
 *
 * Uninstalled extensions surface honestly: the stub a source manager
 * returns for a missing extension is NOT a catalogue source, so the cast
 * fails and [ExtensionUnavailableException] renders the "extension isn't
 * installed" row — the spec's recorded `pkgName` stays authored metadata
 * (a reinstall under a different package keeps serving its own source id,
 * which is the row the user asked for anyway).
 *
 * A LATEST spec against a `supportsLatest == false` source throws
 * [FeedNotImplementedException] → the "unsupported" row state.
 *
 * Sub-type + genre facets go through the source's OWN `getFilterList()`:
 * `feed.kindsCsv` keywords are matched against type/format filter
 * options (with a genre/tag-group fallback — plenty of sources model
 * "Webtoon" as a genre tag, not a Type filter) and
 * `spec.genreNames`/`excludedGenreNames` against genre groups — the
 * same vocabulary the builder's facet pills offer, so a picked pill
 * always maps back to the filter it came from. Applying any constraint
 * turns the fetch into a filtered browse (`getSearch*` with an empty
 * query — the browse-screen idiom; novel sources get their filtered
 * popular/latest overloads so the listing kind is preserved).
 * `feed.kindStrict` decides what an inexpressible `kindsCsv` does:
 * strict narrows-or-fails ([FeedNotImplementedException] — a "Webtoons"
 * row that silently mixes manga back in is exactly the bug this
 * avoids); soft degrades to the unfiltered listing for baseline kinds
 * on sources with no type vocabulary at all.
 *
 * Rows are tap-time materialized: the listing never inserts entries, the
 * [RowTarget.ExtensionEntry] carries `sourceId` + `url` for the screen
 * model's `networkToLocal*` hop. Covers still carry `sourceId` so the
 * image fetcher can inject the source's headers.
 */
class ExtensionCatalogFeed(
    private val mangaSourceManager: MangaSourceManager,
    private val animeSourceManager: AnimeSourceManager,
    private val novelSourceManager: NovelSourceManager,
) {

    /**
     * The source's own genre vocabulary for the builder's facet pills —
     * names lifted from its `getFilterList()` genre/tag/category groups
     * and selects, so a picked pill maps back onto the very filter
     * entry the fetch applies. Empty when the source declares none.
     */
    fun genreOptions(medium: String, sourceId: Long): List<String> = when (medium) {
        WorkIdResolver.MEDIUM_MANGA ->
            (mangaSourceManager.get(sourceId) as? CatalogueSource)
                ?.getFilterList()?.let(::mangaGenreOptions)
        WorkIdResolver.MEDIUM_SHOWS ->
            (animeSourceManager.get(sourceId) as? AnimeCatalogueSource)
                ?.getFilterList()?.let(::animeGenreOptions)
        else ->
            (novelSourceManager.get(sourceId) as? NovelCatalogueSource)
                ?.getFilterList()?.let(::novelGenreOptions)
    }.orEmpty().distinct().sortedBy { it.lowercase() }

    suspend fun fetch(spec: CatalogRowSpec, feed: Feed.Extension): List<RowItem> =
        when (spec.medium) {
            WorkIdResolver.MEDIUM_MANGA -> fetchManga(spec, feed)
            WorkIdResolver.MEDIUM_SHOWS -> fetchShows(spec, feed)
            else -> fetchNovels(spec, feed)
        }

    private suspend fun fetchManga(spec: CatalogRowSpec, feed: Feed.Extension): List<RowItem> {
        val source = mangaSourceManager.get(feed.sourceId) as? CatalogueSource
            ?: throw ExtensionUnavailableException(feed.sourceId)
        if (feed.kind == ExtKind.LATEST && !source.supportsLatest) {
            throw FeedNotImplementedException(feed)
        }
        val filters = if (spec.hasSourceConstraints(feed)) {
            runCatching { source.getFilterList() }.getOrElse { FilterList(emptyList()) }
        } else {
            FilterList(emptyList())
        }
        val kindApplied = applyMangaKindFilter(filters, feed.kindKeywords())
        if (!kindApplied && feed.kindKeywords() != null && feed.kindStrict) {
            throw FeedNotImplementedException(feed)
        }
        val genresApplied = applyMangaGenreFilters(
            filters, spec.genreNames, spec.excludedGenreNames,
        )
        val page = if (kindApplied || genresApplied) {
            source.getSearchManga(1, "", filters)
        } else if (feed.kind == ExtKind.LATEST) {
            source.getLatestUpdates(1)
        } else {
            source.getPopularManga(1)
        }
        return page.mangas
            .filter { genresApplied || matchesGenreNames(spec.genreNames, it.genre) }
            .map { sm ->
                RowItem(
                    title = sm.title,
                    coverUrl = MangaCover(0L, feed.sourceId, false, sm.thumbnail_url, 0L),
                    // Listing rows carry no chapter data — a count would
                    // need a detail call per card; the source name the
                    // subtitle used to carry adds nothing either.
                    subtitle = null,
                    target = RowTarget.ExtensionEntry(feed.sourceId, sm.url, spec.medium),
                )
            }
    }

    private suspend fun fetchShows(spec: CatalogRowSpec, feed: Feed.Extension): List<RowItem> {
        val source = animeSourceManager.get(feed.sourceId) as? AnimeCatalogueSource
            ?: throw ExtensionUnavailableException(feed.sourceId)
        if (feed.kind == ExtKind.LATEST && !source.supportsLatest) {
            throw FeedNotImplementedException(feed)
        }
        val filters = if (spec.hasSourceConstraints(feed)) {
            runCatching { source.getFilterList() }.getOrElse { AnimeFilterList(emptyList()) }
        } else {
            AnimeFilterList(emptyList())
        }
        val kindApplied = applyAnimeKindFilter(filters, feed.kindKeywords())
        if (!kindApplied && feed.kindKeywords() != null && feed.kindStrict) {
            throw FeedNotImplementedException(feed)
        }
        val genresApplied = applyAnimeGenreFilters(
            filters, spec.genreNames, spec.excludedGenreNames,
        )
        val page = if (kindApplied || genresApplied) {
            source.getSearchAnime(1, "", filters)
        } else if (feed.kind == ExtKind.LATEST) {
            source.getLatestUpdates(1)
        } else {
            source.getPopularAnime(1)
        }
        return page.animes
            .filter { genresApplied || matchesGenreNames(spec.genreNames, it.genre) }
            .map { sa ->
                RowItem(
                    title = sa.title,
                    coverUrl = AnimeCover(0L, feed.sourceId, false, sa.thumbnail_url, 0L),
                    subtitle = null,
                    target = RowTarget.ExtensionEntry(feed.sourceId, sa.url, spec.medium),
                )
            }
    }

    private suspend fun fetchNovels(spec: CatalogRowSpec, feed: Feed.Extension): List<RowItem> {
        val source = novelSourceManager.get(feed.sourceId) as? NovelCatalogueSource
            ?: throw ExtensionUnavailableException(feed.sourceId)
        if (feed.kind == ExtKind.LATEST && !source.supportsLatest) {
            throw FeedNotImplementedException(feed)
        }
        val filters = if (spec.hasSourceConstraints(feed)) {
            runCatching { source.getFilterList() }.getOrElse { NovelFilterList(emptyList()) }
        } else {
            NovelFilterList(emptyList())
        }
        val kindApplied = applyNovelKindFilter(filters, feed.kindKeywords())
        if (!kindApplied && feed.kindKeywords() != null && feed.kindStrict) {
            throw FeedNotImplementedException(feed)
        }
        val genresApplied = applyNovelGenreFilters(
            filters, spec.genreNames, spec.excludedGenreNames,
        )
        val page = if (kindApplied || genresApplied) {
            // The novel API's filtered overloads keep the listing kind.
            when (feed.kind) {
                ExtKind.LATEST -> source.getLatestUpdates(1, filters)
                ExtKind.POPULAR -> source.getPopularNovels(1, filters)
            }
        } else if (feed.kind == ExtKind.LATEST) {
            source.getLatestUpdates(1)
        } else {
            source.getPopularNovels(1)
        }
        return page.novels
            .filter { genresApplied || matchesGenreNames(spec.genreNames, it.genre) }
            .map { sn ->
                RowItem(
                    title = sn.title,
                    coverUrl = NovelCover(0L, feed.sourceId, false, sn.thumbnail_url, 0L),
                    subtitle = null,
                    target = RowTarget.ExtensionEntry(feed.sourceId, sn.url, spec.medium),
                )
            }
    }

}

/** Filter names that describe what KIND of work an item is. */
private val TYPE_FILTER_RE =
    Regex("""type|format|kind|demographic|country of origin|content type""", RegexOption.IGNORE_CASE)

/** Filter names that hold the source's genre/tag vocabulary. */
private val GENRE_FILTER_RE =
    Regex("""genre|tag|categor""", RegexOption.IGNORE_CASE)

private fun Feed.Extension.kindKeywords(): List<String>? =
    kindsCsv?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }

/** True when the spec carries any source-side constraint — only then is
 *  `getFilterList()` worth building (it's a source call; the unfiltered
 *  popular/latest path shouldn't pay for it). */
private fun CatalogRowSpec.hasSourceConstraints(feed: Feed.Extension): Boolean =
    feed.kindKeywords() != null || genreNames.isNotEmpty() || excludedGenreNames.isNotEmpty()

/** Option/child label ↔ vocab keyword match. */
private fun matchesKeyword(label: String, keywords: List<String>): Boolean =
    keywords.any { label.contains(it, ignoreCase = true) }

private fun matchesName(label: String, wanted: List<String>): Boolean =
    wanted.any { label.equals(it, ignoreCase = true) || label.contains(it, ignoreCase = true) }

/** `genreNames` contains-match on an extension `S*.genre` CSV field —
 *  legacy fallback for specs authored before source-side filters. */
internal fun matchesGenreNames(wanted: List<String>, genreCsv: String?): Boolean =
    wanted.isEmpty() ||
        (genreCsv ?: "").split(',').map { it.trim() }
            .any { g -> wanted.any { g.contains(it, ignoreCase = true) } }

// --- Genre vocabulary extraction (builder pills read these) ---

private fun mangaGenreOptions(filters: FilterList): List<String> = filters.flatMap { f ->
    when {
        f is Filter.Group<*> && GENRE_FILTER_RE.containsMatchIn(f.name) ->
            f.state.mapNotNull { (it as? Filter<*>)?.name }
        f is Filter.Select<*> && GENRE_FILTER_RE.containsMatchIn(f.name) ->
            f.values.map { it.toString() }
        else -> emptyList()
    }
}

private fun animeGenreOptions(filters: AnimeFilterList): List<String> = filters.flatMap { f ->
    when {
        f is AnimeFilter.Group<*> && GENRE_FILTER_RE.containsMatchIn(f.name) ->
            f.state.mapNotNull { (it as? AnimeFilter<*>)?.name }
        f is AnimeFilter.Select<*> && GENRE_FILTER_RE.containsMatchIn(f.name) ->
            f.values.map { it.toString() }
        else -> emptyList()
    }
}

private fun novelGenreOptions(filters: NovelFilterList): List<String> = filters.flatMap { f ->
    when {
        f is NovelFilter.Group<*> && GENRE_FILTER_RE.containsMatchIn(f.name) ->
            f.state.mapNotNull { (it as? NovelFilter<*>)?.name }
        f is NovelFilter.Select<*> && GENRE_FILTER_RE.containsMatchIn(f.name) ->
            f.values.map { it.toString() }
        else -> emptyList()
    }
}

// --- Manga FilterList application ---

private fun applyMangaKindFilter(filters: FilterList, keywords: List<String>?): Boolean {
    if (keywords == null) return false
    // Type/format-named filters first; a format modelled as a genre tag
    // ("Webtoon" inside "Genres") is just as real — fall back to it.
    var applied = false
    for (nameRe in listOf(TYPE_FILTER_RE, GENRE_FILTER_RE)) {
        if (applied) break
        filters.forEach { filter ->
            when (filter) {
                is Filter.Select<*> -> if (nameRe.containsMatchIn(filter.name)) {
                    filter.values.indexOfFirst { v -> matchesKeyword(v.toString(), keywords) }
                        .takeIf { it >= 0 }
                        ?.let { filter.state = it; applied = true }
                }
                is Filter.Group<*> -> if (nameRe.containsMatchIn(filter.name)) {
                    filter.state.filterIsInstance<Filter.CheckBox>()
                        .filter { matchesKeyword(it.name, keywords) }
                        .forEach { it.state = true; applied = true }
                    filter.state.filterIsInstance<Filter.TriState>()
                        .filter { matchesKeyword(it.name, keywords) }
                        .forEach { it.state = Filter.TriState.STATE_INCLUDE; applied = true }
                }
                else -> Unit
            }
        }
    }
    return applied
}

// --- Anime AnimeFilterList application ---

private fun applyMangaGenreFilters(
    filters: FilterList,
    include: List<String>,
    exclude: List<String>,
): Boolean {
    var applied = false
    filters.forEach { filter ->
        when (filter) {
            is Filter.Group<*> -> if (GENRE_FILTER_RE.containsMatchIn(filter.name)) {
                filter.state.forEach { child ->
                    when (child) {
                        is Filter.CheckBox -> if (matchesName(child.name, include)) {
                            child.state = true; applied = true
                        }
                        is Filter.TriState -> when {
                            matchesName(child.name, include) -> {
                                child.state = Filter.TriState.STATE_INCLUDE; applied = true
                            }
                            matchesName(child.name, exclude) -> {
                                child.state = Filter.TriState.STATE_EXCLUDE; applied = true
                            }
                        }
                        else -> Unit
                    }
                }
            }
            is Filter.Select<*> -> if (GENRE_FILTER_RE.containsMatchIn(filter.name)) {
                filter.values.indexOfFirst { v -> matchesName(v.toString(), include) }
                    .takeIf { it >= 0 }
                    ?.let { filter.state = it; applied = true }
            }
            else -> Unit
        }
    }
    return applied
}

// --- Anime AnimeFilterList application ---

private fun applyAnimeKindFilter(filters: AnimeFilterList, keywords: List<String>?): Boolean {
    if (keywords == null) return false
    var applied = false
    for (nameRe in listOf(TYPE_FILTER_RE, GENRE_FILTER_RE)) {
        if (applied) break
        filters.forEach { filter ->
            when (filter) {
                is AnimeFilter.Select<*> -> if (nameRe.containsMatchIn(filter.name)) {
                    filter.values.indexOfFirst { v -> matchesKeyword(v.toString(), keywords) }
                        .takeIf { it >= 0 }
                        ?.let { filter.state = it; applied = true }
                }
                is AnimeFilter.Group<*> -> if (nameRe.containsMatchIn(filter.name)) {
                    filter.state.filterIsInstance<AnimeFilter.CheckBox>()
                        .filter { matchesKeyword(it.name, keywords) }
                        .forEach { it.state = true; applied = true }
                    filter.state.filterIsInstance<AnimeFilter.TriState>()
                        .filter { matchesKeyword(it.name, keywords) }
                        .forEach { it.state = AnimeFilter.TriState.STATE_INCLUDE; applied = true }
                }
                else -> Unit
            }
        }
    }
    return applied
}

private fun applyAnimeGenreFilters(
    filters: AnimeFilterList,
    include: List<String>,
    exclude: List<String>,
): Boolean {
    var applied = false
    filters.forEach { filter ->
        when (filter) {
            is AnimeFilter.Group<*> -> if (GENRE_FILTER_RE.containsMatchIn(filter.name)) {
                filter.state.forEach { child ->
                    when (child) {
                        is AnimeFilter.CheckBox -> if (matchesName(child.name, include)) {
                            child.state = true; applied = true
                        }
                        is AnimeFilter.TriState -> when {
                            matchesName(child.name, include) -> {
                                child.state = AnimeFilter.TriState.STATE_INCLUDE; applied = true
                            }
                            matchesName(child.name, exclude) -> {
                                child.state = AnimeFilter.TriState.STATE_EXCLUDE; applied = true
                            }
                        }
                        else -> Unit
                    }
                }
            }
            is AnimeFilter.Select<*> -> if (GENRE_FILTER_RE.containsMatchIn(filter.name)) {
                filter.values.indexOfFirst { v -> matchesName(v.toString(), include) }
                    .takeIf { it >= 0 }
                    ?.let { filter.state = it; applied = true }
            }
            else -> Unit
        }
    }
    return applied
}

// --- Novel NovelFilterList application ---

private fun applyNovelKindFilter(filters: NovelFilterList, keywords: List<String>?): Boolean {
    if (keywords == null) return false
    var applied = false
    for (nameRe in listOf(TYPE_FILTER_RE, GENRE_FILTER_RE)) {
        if (applied) break
        filters.forEach { filter ->
            when (filter) {
                is NovelFilter.Select<*> -> if (nameRe.containsMatchIn(filter.name)) {
                    filter.values.indexOfFirst { v -> matchesKeyword(v.toString(), keywords) }
                        .takeIf { it >= 0 }
                        ?.let { filter.state = it; applied = true }
                }
                is NovelFilter.Group<*> -> if (nameRe.containsMatchIn(filter.name)) {
                    filter.state.filterIsInstance<NovelFilter.CheckBox>()
                        .filter { matchesKeyword(it.name, keywords) }
                        .forEach { it.state = true; applied = true }
                    filter.state.filterIsInstance<NovelFilter.TriState>()
                        .filter { matchesKeyword(it.name, keywords) }
                        .forEach { it.state = NovelFilter.TriState.STATE_INCLUDE; applied = true }
                }
                else -> Unit
            }
        }
    }
    return applied
}

private fun applyNovelGenreFilters(
    filters: NovelFilterList,
    include: List<String>,
    exclude: List<String>,
): Boolean {
    var applied = false
    filters.forEach { filter ->
        when (filter) {
            is NovelFilter.Group<*> -> if (GENRE_FILTER_RE.containsMatchIn(filter.name)) {
                filter.state.forEach { child ->
                    when (child) {
                        is NovelFilter.CheckBox -> if (matchesName(child.name, include)) {
                            child.state = true; applied = true
                        }
                        is NovelFilter.TriState -> when {
                            matchesName(child.name, include) -> {
                                child.state = NovelFilter.TriState.STATE_INCLUDE; applied = true
                            }
                            matchesName(child.name, exclude) -> {
                                child.state = NovelFilter.TriState.STATE_EXCLUDE; applied = true
                            }
                        }
                        else -> Unit
                    }
                }
            }
            is NovelFilter.Select<*> -> if (GENRE_FILTER_RE.containsMatchIn(filter.name)) {
                filter.values.indexOfFirst { v -> matchesName(v.toString(), include) }
                    .takeIf { it >= 0 }
                    ?.let { filter.state = it; applied = true }
            }
            else -> Unit
        }
    }
    return applied
}
