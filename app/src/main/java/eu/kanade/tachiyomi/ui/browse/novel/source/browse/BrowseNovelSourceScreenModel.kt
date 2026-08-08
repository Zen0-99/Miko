package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import android.util.Log
import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.domain.savedsearches.novel.NovelFilterSerializer
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.collection.novel.interactor.SetNovelCollections
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.novel.interactor.GetRemoteNovel
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.savedsearches.novel.interactor.DeleteNovelSavedSearch
import tachiyomi.domain.savedsearches.novel.interactor.GetNovelSavedSearches
import tachiyomi.domain.savedsearches.novel.interactor.InsertNovelSavedSearch
import tachiyomi.domain.savedsearches.model.SavedSearch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BrowseNovelSourceScreenModel(
    private val sourceId: Long,
    listingQuery: String?,
    sourceManager: NovelSourceManager = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val getRemoteNovel: GetRemoteNovel = Injekt.get(),
    private val getCollections: GetNovelCollections = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val setNovelCollections: SetNovelCollections = Injekt.get(),
    private val getSavedSearches: GetNovelSavedSearches = Injekt.get(),
    private val insertSavedSearch: InsertNovelSavedSearch = Injekt.get(),
    private val deleteSavedSearch: DeleteNovelSavedSearch = Injekt.get(),
    private val filterSerializer: NovelFilterSerializer = Injekt.get(),
) : StateScreenModel<BrowseNovelSourceScreenModel.State>(State(Listing.valueOf(listingQuery))) {

    var displayMode by sourcePreferences.sourceDisplayMode().asState(screenModelScope)

    val source = sourceManager.getOrStub(sourceId)

    init {
        Log.d("NovelSearch", "[BrowseNovelSourceScreenModel] init - sourceId=$sourceId, source=${source.name} (id=${source.id}), isCatalogueSource=${source is NovelCatalogueSource}, isStub=${source is tachiyomi.domain.source.novel.model.StubNovelSource}, listingQuery='$listingQuery'")
    }

    val savedSearchesFlow = getSavedSearches.subscribe(sourceId)
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyList())

    init {
        if (source is NovelCatalogueSource) {
            mutableState.update {
                var query: String? = null
                var listing = it.listing

                if (listing is Listing.Search) {
                    query = listing.query
                    listing = Listing.Search(query, source.getFilterList())
                }

                it.copy(
                    listing = listing,
                    filters = source.getFilterList(),
                    toolbarQuery = query,
                )
            }
            Log.d("NovelSearch", "[BrowseNovelSourceScreenModel] init - initialized listing for catalogue source: listing=${state.value.listing}, toolbarQuery='${state.value.toolbarQuery}'")
        } else {
            Log.w("NovelSearch", "[BrowseNovelSourceScreenModel] init - source is NOT a NovelCatalogueSource, listing not initialized")
        }

        if (!sourcePreferences.incognitoNovelExtensions().isSet()) {
            sourcePreferences.lastUsedNovelSource().set(source.id)
        }
    }

    val novelPagerFlowFlow = state.map { it.listing }
        .distinctUntilChanged()
        .map { listing ->
            val query = if (listing is Listing.Search) listing.query else null
            val filters = if (listing is Listing.Search) listing.filters else NovelFilterList()
            Pager(PagingConfig(pageSize = 25)) {
                getRemoteNovel.subscribe(sourceId, query ?: GetRemoteNovel.QUERY_POPULAR, filters)
            }.flow.map { pagingData ->
                pagingData.map {
                    networkToLocalNovel.await(it.toDomainNovel(sourceId))
                        .let { localNovel -> getNovel.subscribe(localNovel.url, localNovel.source) }
                        .filterNotNull()
                        .stateIn(ioCoroutineScope)
                }
            }
                .cachedIn(ioCoroutineScope)
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyFlow())

    /**
     * Whether this source supports incremental search results.
     * When true, [incrementalBrowseFlow] uses [NovelCatalogueSource.searchFlow]
     * for search listings (batch-level incremental). All other listings use
     * page-by-page fetching.
     */
    val supportsIncrementalSearch: Boolean
        get() = source is NovelCatalogueSource && source.supportsIncrementalSearch

    /**
     * Unified incremental browse flow for ALL listings (Popular, Latest, Search).
     *
     * Emits cumulative lists of novels, one novel at a time, as they're fetched
     * and converted. This drives the domino-style reveal animation in the UI.
     *
     * - **Search on incremental sources**: uses [NovelCatalogueSource.searchFlow]
     *   which emits batches as they're parsed from the response.
     * - **Everything else** (Popular, Latest, non-incremental search): calls the
     *   source's page-by-page methods directly, fetching one page at a time and
     *   converting each novel on that page one-by-one.
     *
     * In both cases, each novel is converted through [networkToLocalNovel] and
     * emitted individually, providing a steady stream to the animation.
     */
    val incrementalBrowseFlow = state
        .map { it.listing }
        .distinctUntilChanged()
        .debounce(300L)
        .transform { listing ->
            Log.d("NovelSearch", "[BrowseNovelSourceScreenModel] incrementalBrowseFlow - listing=$listing")
            val convertedNovels = mutableMapOf<String, Novel>()
            val accumulated = mutableListOf<Novel>()
            val catSource = source as? NovelCatalogueSource

            suspend fun convertAndEmit(sNovel: SNovel) {
                val novel = convertedNovels.getOrPut(sNovel.url) {
                    networkToLocalNovel.await(sNovel.toDomainNovel(sourceId))
                }
                if (accumulated.none { it.id == novel.id }) {
                    accumulated.add(novel)
                    emit(accumulated.toList())
                }
            }

            try {
                when {
                    // Search on incremental source: use searchFlow for batch-level incremental
                    listing is Listing.Search && !listing.query.isNullOrEmpty() && supportsIncrementalSearch -> {
                        getRemoteNovel.subscribeFlow(sourceId, listing.query!!).collect { batch ->
                            Log.d("NovelSearch", "[BrowseNovelSourceScreenModel] incrementalBrowseFlow - batch size=${batch.size}")
                            for (sNovel in batch) convertAndEmit(sNovel)
                        }
                    }
                    // Search on regular source: page-by-page
                    listing is Listing.Search && !listing.query.isNullOrEmpty() -> {
                        var page = 1
                        while (true) {
                            val result = catSource!!.getSearchNovels(page, listing.query!!, listing.filters)
                            for (sNovel in result.novels) convertAndEmit(sNovel)
                            if (!result.hasNextPage) break
                            page++
                        }
                    }
                    // Popular: page-by-page
                    listing is Listing.Popular -> {
                        var page = 1
                        while (true) {
                            val result = catSource!!.getPopularNovels(page)
                            for (sNovel in result.novels) convertAndEmit(sNovel)
                            if (!result.hasNextPage) break
                            page++
                        }
                    }
                    // Latest: page-by-page
                    listing is Listing.Latest -> {
                        var page = 1
                        while (true) {
                            val result = catSource!!.getLatestUpdates(page)
                            for (sNovel in result.novels) convertAndEmit(sNovel)
                            if (!result.hasNextPage) break
                            page++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NovelSearch", "[BrowseNovelSourceScreenModel] incrementalBrowseFlow - error", e)
                if (accumulated.isEmpty()) emit(emptyList())
            }
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, null)

    fun setListing(listing: Listing) {
        mutableState.update { it.copy(listing = listing, toolbarQuery = null) }
    }

    fun setFilters(filters: NovelFilterList) {
        mutableState.update { it.copy(filters = filters) }
    }

    fun resetFilters() {
        if (source is NovelCatalogueSource) {
            mutableState.update { it.copy(filters = source.getFilterList()) }
        }
    }

    fun search(query: String? = null, filters: NovelFilterList? = null) {
        Log.d("NovelSearch", "[BrowseNovelSourceScreenModel] search() - query='$query', filters=${filters?.size}, source=${source.name}")
        if (source !is NovelCatalogueSource) {
            Log.w("NovelSearch", "[BrowseNovelSourceScreenModel] search() - source is NOT a NovelCatalogueSource, returning early")
            return
        }

        val input = state.value.listing as? Listing.Search
            ?: Listing.Search(query = null, filters = source.getFilterList())

        mutableState.update {
            it.copy(
                listing = input.copy(
                    query = query ?: input.query,
                    filters = filters ?: input.filters,
                ),
                toolbarQuery = query ?: input.query,
            )
        }
        Log.d("NovelSearch", "[BrowseNovelSourceScreenModel] search() - updated listing to ${state.value.listing}, toolbarQuery='${state.value.toolbarQuery}'")
    }

    fun searchGenre(genre: String) {
        search(query = genre)
    }

    fun setToolbarQuery(query: String?) {
        mutableState.update { it.copy(toolbarQuery = query) }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun openFilterSheet() {
        setDialog(Dialog.Filter)
    }

    fun openSavedSearches() {
        setDialog(Dialog.SavedSearches)
    }

    fun saveSearch(name: String) {
        val query = state.value.toolbarQuery ?: ""
        val filtersJson = filterSerializer.encode(state.value.filters)
        screenModelScope.launchIO {
            insertSavedSearch.await(SavedSearch.create(sourceId, name, query, filtersJson))
        }
    }

    fun deleteSavedSearch(id: Long) {
        screenModelScope.launchIO {
            deleteSavedSearch.await(id)
        }
    }

    fun applySavedSearch(savedSearch: SavedSearch) {
        if (source !is NovelCatalogueSource) return
        val filters = filterSerializer.decode(source.getFilterList(), savedSearch.filtersJson)
        search(query = savedSearch.query, filters = filters)
        setDialog(null)
    }

    fun changeNovelFavorite(novel: Novel) {
        screenModelScope.launchIO {
            updateNovel.await(NovelUpdate(id = novel.id, favorite = !novel.favorite))
        }
    }

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.novelLandscapeColumns().get()
        } else {
            libraryPreferences.novelPortraitColumns().get()
        }
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    fun getColumnsPreferenceForCurrentOrientation(orientation: Int): Int {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.novelLandscapeColumns().get()
        } else {
            libraryPreferences.novelPortraitColumns().get()
        }
        return if (columns == 0) 0 else columns
    }

    suspend fun getDuplicateLibraryNovel(novel: Novel): Novel? {
        return getNovel.subscribe(novel.url, novel.source).firstOrNull()
    }

    fun addFavorite(novel: Novel) {
        screenModelScope.launchIO {
            updateNovel.await(NovelUpdate(id = novel.id, favorite = true))
            setNovelCollections.await(novel.id, emptyList())
        }
    }

    sealed class Listing(open val query: String?, open val filters: NovelFilterList) {
        data object Popular : Listing(query = GetRemoteNovel.QUERY_POPULAR, filters = NovelFilterList())
        data object Latest : Listing(query = GetRemoteNovel.QUERY_LATEST, filters = NovelFilterList())
        data class Search(override val query: String?, override val filters: NovelFilterList) : Listing(
            query = query,
            filters = filters,
        )

        companion object {
            fun valueOf(query: String?): Listing {
                return when (query) {
                    GetRemoteNovel.QUERY_POPULAR -> Popular
                    GetRemoteNovel.QUERY_LATEST -> Latest
                    else -> Search(query = query, filters = NovelFilterList())
                }
            }
        }
    }

    sealed interface Dialog {
        data object Filter : Dialog
        data object SavedSearches : Dialog
        data class RemoveNovel(val novel: Novel) : Dialog
        data class AddDuplicateNovel(val novel: Novel, val duplicate: Novel) : Dialog
        data class ChangeNovelCollection(
            val novel: Novel,
            val initialSelection: ImmutableList<CheckboxState.State<Collection>>,
        ) : Dialog
        data class Migrate(val newNovel: Novel, val oldNovel: Novel) : Dialog
    }

    @Immutable
    data class State(
        val listing: Listing,
        val filters: NovelFilterList = NovelFilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
    }
}
