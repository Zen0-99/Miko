package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.runtime.Immutable
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.metadata.tmdb.TmdbDiscover
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.aniyomi.AYMR

/**
 * Sort facet for the Discovery sheet — the enum carries the TMDB
 * `sort_by` string per media type (movie dates are `primary_release`,
 * tv dates `first_air_date`).
 */
enum class DiscoverySort(
    val labelRes: StringResource,
    private val movieParam: String,
    private val tvParam: String,
) {
    POPULARITY(AYMR.strings.discovery_sort_popularity, "popularity.desc", "popularity.desc"),
    RATING(AYMR.strings.discovery_sort_rating, "vote_average.desc", "vote_average.desc"),
    NEWEST(AYMR.strings.discovery_sort_newest, "primary_release_date.desc", "first_air_date.desc"),
    ;

    fun param(type: MediaType): String = when (type) {
        MediaType.MOVIE -> movieParam
        MediaType.TV -> tvParam
    }

    /** `/v1/works/browse` `sort` param for the canonical catalog feeds —
     *  the enum names already match the worker's vocabulary. */
    val catalogParam: String
        get() = name.lowercase()
}

/**
 * In-memory TMDB facet state for the Discovery surface (Phase 15-06) —
 * hoisted in `DiscoveryScreenModel`, deliberately NOT persisted to
 * `LibraryPreferences` (spec silent; persistence is a later decision).
 *
 * Tri-state maps: genre ids cycle include → `with_genres`, exclude →
 * `without_genres`; keyword ids feed `with_keywords`/`without_keywords`.
 * [isActive] gates whether the shows grid swaps Cinemeta catalog
 * results for TMDB discover results.
 */
@Immutable
data class DiscoveryFilter(
    val genreStates: PersistentMap<Int, TriState> = persistentMapOf(),
    val keywordStates: PersistentMap<Int, TriState> = persistentMapOf(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val voteAverageGte: Double? = null,
    val voteCountGte: Int? = null,
    val sort: DiscoverySort = DiscoverySort.POPULARITY,
) {
    /** Any facet off its default — a non-default sort counts (sorting
     *  only has meaning against the discover endpoint). */
    val isActive: Boolean
        get() = genreStates.values.any { it != TriState.DISABLED } ||
            keywordStates.values.any { it != TriState.DISABLED } ||
            yearFrom != null || yearTo != null ||
            voteAverageGte != null || voteCountGte != null ||
            sort != DiscoverySort.POPULARITY

    fun withGenreState(genreId: Int, state: TriState): DiscoveryFilter =
        copy(genreStates = genreStates.put(genreId, state))

    fun withKeywordState(keywordId: Int, state: TriState): DiscoveryFilter =
        copy(keywordStates = keywordStates.put(keywordId, state))

    fun toQuery(type: MediaType): TmdbDiscover.DiscoverQuery = TmdbDiscover.DiscoverQuery(
        withGenres = genreStates.filterValues { it == TriState.ENABLED_IS }.keys.sorted(),
        withoutGenres = genreStates.filterValues { it == TriState.ENABLED_NOT }.keys.sorted(),
        withKeywords = keywordStates.filterValues { it == TriState.ENABLED_IS }.keys.sorted(),
        withoutKeywords = keywordStates.filterValues { it == TriState.ENABLED_NOT }.keys.sorted(),
        yearGte = yearFrom,
        yearLte = yearTo,
        voteAverageGte = voteAverageGte,
        voteCountGte = voteCountGte,
        sortBy = sort.param(type),
    )
}
