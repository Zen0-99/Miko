package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.tachiyomi.metadata.tmdb.TmdbDiscover
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Discovery surface state for [DiscoveryTab] (Phase 15-05, extended by
 * 15-06).
 *
 * The tab is keyed by [Carrier] — the active carrier is derived from
 * `UiPreferences.contentMode()` via [Carrier.fromContentMode] and selects
 * which catalog surface renders (Cinemeta for shows, canonical catalog for
 * manga/books). This model owns the UI-level state that must survive tab
 * disposal: the shared-top-bar search query/submission and the per-carrier
 * sub-type pill selection (consumed by the carrier pills / filter state
 * landing in 15-06; the shows Movies/Series toggle itself lives inside
 * `CinemetaBrowseScreenModel`).
 *
 * 15-06 adds the per-carrier [DiscoveryFilter] set plus the TMDB
 * `discover` paging state for the shows carrier. Filter state is
 * in-memory only (RESEARCH §4 — NOT `LibraryPreferences`); every
 * facet/type change funnels through [onDiscoverInputsChanged], which
 * debounces (~300 ms) so chip toggles don't fire a request per tap.
 * manga/books keep their per-source filters — the map keying exists for
 * them but only SHOWS drives TMDB calls.
 */
class DiscoveryScreenModel(
    private val tmdbDiscover: TmdbDiscover = Injekt.get(),
    private val debounceMs: Long = FILTER_DEBOUNCE_MS,
) : StateScreenModel<DiscoveryScreenModel.State>(State()) {

    @Immutable
    data class State(
        /** Non-null while the shared top-bar search field is open. */
        val searchQuery: String? = null,
        /** True once the user has submitted the query (IME search). */
        val searchSubmitted: Boolean = false,
        /** Last selected sub-type pill index per carrier. */
        val subTypeSelections: PersistentMap<Carrier, Int> = persistentMapOf(),
        /** Facet filters per carrier — only SHOWS is consumed (TMDB). */
        val filters: PersistentMap<Carrier, DiscoveryFilter> = persistentMapOf(),
        /** TMDB discover surface — populated while a shows filter is active. */
        val discoverItems: List<TmdbDiscover.Item> = emptyList(),
        val discoverPage: Int = 0,
        val discoverHasMore: Boolean = true,
        val discoverLoading: Boolean = false,
        val discoverError: Boolean = false,
        /** No usable TMDB key — filtered surface shows the BYOK notice. */
        val discoverKeyMissing: Boolean = false,
    )

    private var reloadJob: Job? = null

    fun setSearchQuery(query: String?) {
        // Typing un-submits — catalog results only swap in on submit; closing
        // the search field (null) also clears the submitted flag so reopening
        // starts from the idle catalog.
        mutableState.update { it.copy(searchQuery = query, searchSubmitted = false) }
    }

    fun setSearchSubmitted(submitted: Boolean) {
        mutableState.update { it.copy(searchSubmitted = submitted) }
    }

    fun selectSubType(carrier: Carrier, index: Int) {
        mutableState.update {
            it.copy(subTypeSelections = it.subTypeSelections.put(carrier, index))
        }
    }

    fun filterFor(carrier: Carrier): DiscoveryFilter =
        state.value.filters[carrier] ?: DiscoveryFilter()

    /**
     * Replace a carrier's filter set. The reload itself is driven by the
     * composable via [onDiscoverInputsChanged] (single funnel: filter,
     * media-type and sheet edits all debounce through it).
     */
    fun setFilter(carrier: Carrier, filter: DiscoveryFilter) {
        mutableState.update { it.copy(filters = it.filters.put(carrier, filter)) }
    }

    /**
     * React to a facet/media-type change for the shows surface: when the
     * shows filter is inactive the TMDB surface resets; when active a
     * debounced page-1 reload is scheduled (cancels any pending reload).
     */
    fun onDiscoverInputsChanged(type: MediaType) {
        val filter = filterFor(Carrier.SHOWS)
        if (!filter.isActive) {
            reloadJob?.cancel()
            resetDiscover()
            return
        }
        reloadJob?.cancel()
        reloadJob = screenModelScope.launch {
            delay(debounceMs)
            loadDiscoverPage(type, filter, page = 1, append = false)
        }
    }

    /** Next page for the current facet set — no-op while a load runs. */
    fun loadNextDiscoverPage(type: MediaType) {
        val s = state.value
        if (s.discoverLoading || !s.discoverHasMore) return
        val filter = filterFor(Carrier.SHOWS)
        if (!filter.isActive) return
        screenModelScope.launch {
            loadDiscoverPage(type, filter, s.discoverPage + 1, append = true)
        }
    }

    /** TMDB → IMDB link for a tapped discover result (null = non-navigable). */
    suspend fun resolveImdbId(type: MediaType, tmdbId: Long): String? =
        tmdbDiscover.imdbId(type, tmdbId)

    private fun resetDiscover() {
        mutableState.update {
            it.copy(
                discoverItems = emptyList(),
                discoverPage = 0,
                discoverHasMore = true,
                discoverLoading = false,
                discoverError = false,
                discoverKeyMissing = false,
            )
        }
    }

    private suspend fun loadDiscoverPage(
        type: MediaType,
        filter: DiscoveryFilter,
        page: Int,
        append: Boolean,
    ) {
        if (!tmdbDiscover.hasKey()) {
            mutableState.update {
                it.copy(
                    discoverItems = emptyList(),
                    discoverLoading = false,
                    discoverError = false,
                    discoverKeyMissing = true,
                    discoverHasMore = false,
                )
            }
            return
        }
        mutableState.update { it.copy(discoverLoading = true, discoverError = false, discoverKeyMissing = false) }
        val result = tmdbDiscover.discover(type, filter.toQuery(type), page)
        if (result == null) {
            mutableState.update {
                it.copy(discoverLoading = false, discoverError = true)
            }
            return
        }
        mutableState.update { s ->
            val merged = if (append) {
                val seen = s.discoverItems.mapTo(HashSet()) { it.id }
                s.discoverItems + result.items.filter { it.id !in seen }
            } else {
                result.items
            }
            s.copy(
                discoverItems = merged,
                discoverPage = result.page,
                discoverHasMore = result.hasMore,
                discoverLoading = false,
                discoverError = false,
            )
        }
    }

    companion object {
        const val FILTER_DEBOUNCE_MS = 300L
    }
}
