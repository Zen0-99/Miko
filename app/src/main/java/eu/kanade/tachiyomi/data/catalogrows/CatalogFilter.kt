package eu.kanade.tachiyomi.data.catalogrows

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.core.common.preference.TriState

/**
 * Manga/books catalog filters (22-01) — the `/v1/works/browse` facet
 * set, parallel to [DiscoveryFilter]'s TMDB shows facets. Genres are
 * string-named (the worker's tag vocabulary), so the TriState map keys
 * on the genre name itself rather than a numeric id.
 *
 * The filter feeds the worker's `genre`/`exclude_genre`/`year_from`/
 * `year_to`/`min_rating` params; it never affects the canonical-map
 * search path.
 */
data class CatalogFilter(
    val genreStates: PersistentMap<String, TriState> = persistentMapOf(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    /** Rating floor on the worker's 0–100 MangaBaka scale. */
    val ratingGte: Double? = null,
) {
    val includeGenres: List<String>
        get() = genreStates.filterValues { it == TriState.ENABLED_IS }.keys.sorted()

    val excludeGenres: List<String>
        get() = genreStates.filterValues { it == TriState.ENABLED_NOT }.keys.sorted()

    val isActive: Boolean
        get() = genreStates.values.any { it != TriState.DISABLED } ||
            yearFrom != null ||
            yearTo != null ||
            ratingGte != null

    fun withGenreState(genre: String, state: TriState): CatalogFilter =
        copy(genreStates = genreStates.put(genre, state))

    /**
     * Stable key fragment namespacing the feed's disk/memory caches —
     * a filtered feed must never render into the unfiltered entry.
     * Genres can't contain `|`/`,`/`-` in the worker vocabulary, so the
     * segments stay parseable.
     */
    fun toFeedKey(): String = buildString {
        fun seg(tag: String, value: String) {
            if (isNotEmpty()) append(';')
            append(tag).append(value)
        }
        includeGenres.takeIf { it.isNotEmpty() }?.let { seg("g", it.joinToString(",")) }
        excludeGenres.takeIf { it.isNotEmpty() }?.let { seg("x", it.joinToString(",")) }
        yearFrom?.let { seg("f", it.toString()) }
        yearTo?.let { seg("t", it.toString()) }
        ratingGte?.let { seg("r", it.toInt().toString()) }
    }
}
