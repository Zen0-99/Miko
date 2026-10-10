package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.tachiyomi.data.catalogrows.CatalogFilter
import eu.kanade.tachiyomi.data.mikodb.SeenReporter
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.data.mikodb.WorksBrowseApi
import eu.kanade.tachiyomi.metadata.tmdb.TmdbDiscover
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

/**
 * Discovery surface state for [DiscoveryTab] (Phase 15-05, extended by
 * 15-06).
 *
 * The tab is keyed by [Carrier] — the active carrier is derived from
 * `UiPreferences.contentMode()` via [Carrier.fromContentMode] and selects
 * which catalog surface renders (Cinemeta for shows, canonical catalog for
 * manga/books). This model owns the UI-level state that must survive tab
 * disposal: the shared-top-bar search query/submission, the per-carrier
 * sub-type selection (consumed by the Type/Filter sheet; the shows
 * Movies/Series toggle itself lives inside `CinemetaBrowseScreenModel`),
 * and the per-carrier catalog-feed sort.
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
        /** `/v1/works/browse` sort per carrier (manga/books Filter tab). */
        val browseSorts: PersistentMap<Carrier, DiscoverySort> = persistentMapOf(),
        /** `/v1/works/browse` facet filters per carrier (22-01) — the
         *  manga/books counterpart of [filters] (TMDB shows). */
        val catalogFilters: PersistentMap<Carrier, CatalogFilter> = persistentMapOf(),
        /** Genre vocabulary per medium (`/v1/genres`) for the filter
         *  sheet's chips — fetched lazily on first sheet open. */
        val catalogGenres: PersistentMap<String, List<String>> = persistentMapOf(),
        /** Type/Filter sheet visibility — hoisted so the trigger rows and
         *  the sheet (mounted at the tab level) share the state. */
        val showTypeFilterSheet: Boolean = false,
        /** Tab the sheet opens on — 0 = Type, 1 = Filter. */
        val typeFilterSheetPage: Int = 0,
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

    /** Per-(type, filter) discover results — lets the pager's other page
     *  preview its own type and makes a type switch hydrate instantly. */
    private data class DiscoverCacheEntry(
        val items: List<TmdbDiscover.Item>,
        val page: Int,
        val hasMore: Boolean,
    )

    private val discoverCache = mutableMapOf<Pair<MediaType, DiscoveryFilter>, DiscoverCacheEntry>()

    /** Per-carrier catalog-feed sort (manga/books Filter tab). */
    fun browseSortFor(carrier: Carrier): DiscoverySort =
        state.value.browseSorts[carrier] ?: DiscoverySort.POPULARITY

    fun setBrowseSort(carrier: Carrier, sort: DiscoverySort) {
        mutableState.update { it.copy(browseSorts = it.browseSorts.put(carrier, sort)) }
    }

    /** Per-carrier browse facets (22-01). */
    fun catalogFilterFor(carrier: Carrier): CatalogFilter =
        state.value.catalogFilters[carrier] ?: CatalogFilter()

    fun setCatalogFilter(carrier: Carrier, filter: CatalogFilter) {
        mutableState.update { it.copy(catalogFilters = it.catalogFilters.put(carrier, filter)) }
    }

    /** Fetch the medium's genre vocabulary once — the filter sheet's
     *  chip set. Fetches on first call per medium; a worker outage
     *  caches an empty list rather than retry-storming the sheet. */
    fun ensureCatalogGenres(medium: String) {
        if (state.value.catalogGenres.containsKey(medium)) return
        screenModelScope.launchIO {
            val genres = worksBrowseApi.genres(medium)
            mutableState.update {
                it.copy(catalogGenres = it.catalogGenres.put(medium, genres))
            }
        }
    }

    // Mint-on-browse deps are lazy so unit tests can construct the model
    // with only a TmdbDiscover fake (Injekt is never touched until a
    // rendered item actually mints).
    private val workIdResolver: WorkIdResolver by injectLazy()
    private val seenReporter: SeenReporter by injectLazy()
    private val worksBrowseApi: WorksBrowseApi by injectLazy()

    /** Minted/reported ids this session — 'tmdb:{type}:{id}' keys. */
    private val mintedDiscoverIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val mintPermits = Semaphore(MINT_CONCURRENCY)

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

    fun openTypeFilterSheet(page: Int = 0) {
        mutableState.update {
            it.copy(showTypeFilterSheet = true, typeFilterSheetPage = page)
        }
    }

    fun setTypeFilterSheetVisible(visible: Boolean) {
        mutableState.update { it.copy(showTypeFilterSheet = visible) }
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
        val cached = discoverCache[type to filter]
        if (cached != null) {
            mutableState.update {
                it.copy(
                    discoverItems = cached.items,
                    discoverPage = cached.page,
                    discoverHasMore = cached.hasMore,
                    discoverLoading = false,
                    discoverError = false,
                )
            }
            return
        }
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

    /**
     * Mint-on-browse (Phase 15-07): TMDB discover rows aren't canonical
     * works, so each rendered item resolves once per session — the
     * `external_ids` lookup carries the imdb id into the resolver's
     * `cinemeta:{type}:{ttid}` path when there is one (mints the
     * `mk:imdb:` row server-side), otherwise the bare-title resolve
     * falls back to a local `mk:m:` mint. Either way the id is then
     * reported via [SeenReporter] — persisted in `seen_reported`, so a
     * work is POSTed at most once per install lifetime, not once per
     * session. Semaphore-capped, fire-and-forget — never blocks the UI.
     */
    fun onDiscoverItemRendered(item: TmdbDiscover.Item) {
        if (!mintedDiscoverIds.add("tmdb:${item.mediaType}:${item.id}")) return
        screenModelScope.launchIO {
            try {
                mintPermits.withPermit {
                    val kind = TmdbDiscover.cinemetaType(item.mediaType)
                    val imdb = tmdbDiscover.imdbId(item.mediaType, item.id)
                    val resolved = workIdResolver.resolve(
                        WorkIdResolver.Candidate(
                            medium = WorkIdResolver.MEDIUM_SHOWS,
                            title = item.title,
                            url = imdb?.let { "cinemeta:$kind:$it" },
                            kindHint = kind,
                        ),
                    ) ?: return@withPermit
                    seenReporter.report(resolved.value, resolved.medium)
                }
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG) { "discovery mint-on-browse failed for tmdb ${item.id}: ${e.message}" }
            }
        }
    }

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
        discoverCache[type to filter] = DiscoverCacheEntry(
            items = state.value.discoverItems,
            page = result.page,
            hasMore = result.hasMore,
        )
    }

    companion object {
        const val FILTER_DEBOUNCE_MS = 300L

        /** Mint-on-browse caps concurrent resolve calls. */
        private const val MINT_CONCURRENCY = 2
    }
}
