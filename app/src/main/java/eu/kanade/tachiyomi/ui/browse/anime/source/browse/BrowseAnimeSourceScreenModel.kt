package eu.kanade.tachiyomi.ui.browse.anime.source.browse

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
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.savedsearches.anime.AnimeFilterSerializer
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.util.removeBackgrounds
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.collection.anime.interactor.GetAnimeCollections
import tachiyomi.domain.collection.anime.interactor.SetAnimeCollections
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.toAnimeUpdate
import tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.savedsearches.anime.interactor.DeleteAnimeSavedSearch
import tachiyomi.domain.savedsearches.anime.interactor.GetAnimeSavedSearches
import tachiyomi.domain.savedsearches.anime.interactor.InsertAnimeSavedSearch
import tachiyomi.domain.savedsearches.model.SavedSearch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import eu.kanade.tachiyomi.animesource.model.AnimeFilter as AnimeSourceModelFilter

class BrowseAnimeSourceScreenModel(
    private val sourceId: Long,
    listingQuery: String?,
    sourceManager: AnimeSourceManager = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: AnimeCoverCache = Injekt.get(),
    private val backgroundCache: AnimeBackgroundCache = Injekt.get(),
    private val getRemoteAnime: GetRemoteAnime = Injekt.get(),
    private val getDuplicateAnimelibAnime: GetDuplicateLibraryAnime = Injekt.get(),
    private val getCollections: GetAnimeCollections = Injekt.get(),
    private val setAnimeCollections: SetAnimeCollections = Injekt.get(),
    private val setAnimeDefaultEpisodeFlags: SetAnimeDefaultEpisodeFlags = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val addTracks: AddAnimeTracks = Injekt.get(),
    private val getIncognitoState: GetAnimeIncognitoState = Injekt.get(),
    private val getSavedSearches: GetAnimeSavedSearches = Injekt.get(),
    private val insertSavedSearch: InsertAnimeSavedSearch = Injekt.get(),
    private val deleteSavedSearch: DeleteAnimeSavedSearch = Injekt.get(),
    private val filterSerializer: AnimeFilterSerializer = Injekt.get(),
) : StateScreenModel<BrowseAnimeSourceScreenModel.State>(State(Listing.valueOf(listingQuery))) {

    var displayMode by sourcePreferences.sourceDisplayMode().asState(screenModelScope)

    val source = sourceManager.getOrStub(sourceId)

    val savedSearchesFlow = getSavedSearches.subscribe(sourceId)
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyList())

    init {
        if (source is AnimeCatalogueSource) {
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
        }

        if (!getIncognitoState.await(source.id)) {
            sourcePreferences.lastUsedAnimeSource().set(source.id)
        }
    }

    /**
     * Flow of Pager flow tied to [State.listing]
     */
    private val hideInLibraryItems = sourcePreferences.hideInAnimeLibraryItems().get()
    val animePagerFlowFlow = state.map { it.listing }
        .distinctUntilChanged()
        .map { listing ->
            Pager(PagingConfig(pageSize = 25)) {
                getRemoteAnime.subscribe(sourceId, listing.query ?: "", listing.filters)
            }.flow.map { pagingData ->
                pagingData.map {
                    networkToLocalAnime.await(it.toDomainAnime(sourceId))
                        .let { localAnime -> getAnime.subscribe(localAnime.url, localAnime.source) }
                        .filterNotNull()
                        .stateIn(ioCoroutineScope)
                }
                    .filter { !hideInLibraryItems || !it.value.favorite }
            }
                .cachedIn(ioCoroutineScope)
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyFlow())

    /**
     * Incremental browse flow for ALL listings (Popular, Latest, Search).
     *
     * Emits cumulative lists of anime, one anime at a time, as they're fetched
     * and converted. This drives the domino-style reveal animation in the UI.
     */
    val incrementalBrowseFlow = state
        .map { it.listing }
        .distinctUntilChanged()
        .debounce(300L)
        .transform { listing ->
            val convertedAnime = mutableMapOf<String, Anime>()
            val accumulated = mutableListOf<Anime>()
            val catSource = source as? AnimeCatalogueSource

            suspend fun convertAndEmit(sAnime: SAnime) {
                val anime = convertedAnime.getOrPut(sAnime.url) {
                    networkToLocalAnime.await(sAnime.toDomainAnime(sourceId))
                }
                if (accumulated.none { it.id == anime.id }) {
                    accumulated.add(anime)
                    emit(accumulated.toList())
                }
            }

            try {
                when {
                    listing is Listing.Search && !listing.query.isNullOrEmpty() -> {
                        var page = 1
                        while (true) {
                            val result = catSource!!.getSearchAnime(page, listing.query!!, listing.filters)
                            for (sAnime in result.animes) convertAndEmit(sAnime)
                            if (!result.hasNextPage) break
                            page++
                        }
                    }
                    listing is Listing.Popular -> {
                        var page = 1
                        while (true) {
                            val result = catSource!!.getPopularAnime(page)
                            for (sAnime in result.animes) convertAndEmit(sAnime)
                            if (!result.hasNextPage) break
                            page++
                        }
                    }
                    listing is Listing.Latest -> {
                        var page = 1
                        while (true) {
                            val result = catSource!!.getLatestUpdates(page)
                            for (sAnime in result.animes) convertAndEmit(sAnime)
                            if (!result.hasNextPage) break
                            page++
                        }
                    }
                }
            } catch (e: Exception) {
                if (accumulated.isEmpty()) emit(emptyList())
            }
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, null)

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.animeLandscapeColumns()
        } else {
            libraryPreferences.animePortraitColumns()
        }.get()
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    // returns the number from the size slider
    fun getColumnsPreferenceForCurrentOrientation(orientation: Int): Int {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) {
            libraryPreferences.animeLandscapeColumns()
        } else {
            libraryPreferences.animePortraitColumns()
        }.get()
    }

    fun resetFilters() {
        if (source !is AnimeCatalogueSource) return

        mutableState.update { it.copy(filters = source.getFilterList()) }
    }

    fun setListing(listing: Listing) {
        mutableState.update { it.copy(listing = listing, toolbarQuery = null) }
    }

    fun setFilters(filters: AnimeFilterList) {
        if (source !is AnimeCatalogueSource) return

        mutableState.update {
            it.copy(
                filters = filters,
            )
        }
    }

    fun search(query: String? = null, filters: AnimeFilterList? = null) {
        if (source !is AnimeCatalogueSource) return

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
    }

    fun searchGenre(genreName: String) {
        if (source !is AnimeCatalogueSource) return

        val defaultFilters = source.getFilterList()
        var genreExists = false

        filter@ for (sourceFilter in defaultFilters) {
            if (sourceFilter is AnimeSourceModelFilter.Group<*>) {
                for (filter in sourceFilter.state) {
                    if (filter is AnimeSourceModelFilter<*> && filter.name.equals(genreName, true)) {
                        when (filter) {
                            is AnimeSourceModelFilter.TriState -> filter.state = 1
                            is AnimeSourceModelFilter.CheckBox -> filter.state = true
                            else -> {}
                        }
                        genreExists = true
                        break@filter
                    }
                }
            } else if (sourceFilter is AnimeSourceModelFilter.Select<*>) {
                val index = sourceFilter.values.filterIsInstance<String>()
                    .indexOfFirst { it.equals(genreName, true) }

                if (index != -1) {
                    sourceFilter.state = index
                    genreExists = true
                    break
                }
            }
        }
        mutableState.update {
            val listing = if (genreExists) {
                Listing.Search(query = null, filters = defaultFilters)
            } else {
                Listing.Search(query = genreName, filters = defaultFilters)
            }
            it.copy(
                filters = defaultFilters,
                listing = listing,
                toolbarQuery = listing.query,
            )
        }
    }

    /**
     * Adds or removes an anime from the library.
     *
     * @param anime the anime to update.
     */
    fun changeAnimeFavorite(anime: Anime) {
        screenModelScope.launch {
            var new = anime.copy(
                favorite = !anime.favorite,
                dateAdded = when (anime.favorite) {
                    true -> 0
                    false -> Instant.now().toEpochMilli()
                },
            )

            if (!new.favorite) {
                new = new.removeCovers(coverCache)
                new = new.removeBackgrounds(backgroundCache)
            } else {
                setAnimeDefaultEpisodeFlags.await(anime)
                addTracks.bindEnhancedTrackers(anime, source)
            }

            updateAnime.await(new.toAnimeUpdate())
        }
    }

    fun addFavorite(anime: Anime) {
        screenModelScope.launch {
            val collections = getCollections()
            val defaultCollectionId = libraryPreferences.defaultAnimeCollection().get()
            val defaultCollection = collections.find { it.id == defaultCollectionId.toLong() }

            when {
                // Default collection set
                defaultCollection != null -> {
                    moveAnimeToCollections(anime, defaultCollection)

                    changeAnimeFavorite(anime)
                }
                // Automatic 'Default' or no collections
                defaultCollectionId == 0 || collections.isEmpty() -> {
                    moveAnimeToCollections(anime)

                    changeAnimeFavorite(anime)
                }

                // Choose a collection
                else -> {
                    val preselectedIds = getCollections.await(anime.id).map { it.id }
                    setDialog(
                        Dialog.ChangeAnimeCollection(
                            anime,
                            collections.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Get user collections.
     *
     * @return List of collections, not including the default collection
     */
    suspend fun getCollections(): List<Collection> {
        return getCollections.subscribe()
            .firstOrNull()
            ?.filterNot { it.isSystemCollection }
            .orEmpty()
    }

    suspend fun getDuplicateAnimelibAnime(anime: Anime): Anime? {
        return getDuplicateAnimelibAnime.await(anime).getOrNull(0)
    }

    private fun moveAnimeToCollections(anime: Anime, vararg collections: Collection) {
        moveAnimeToCollections(anime, collections.filter { it.id != 0L }.map { it.id })
    }

    fun moveAnimeToCollections(anime: Anime, collectionIds: List<Long>) {
        screenModelScope.launchIO {
            setAnimeCollections.await(
                animeId = anime.id,
                collectionIds = collectionIds.toList(),
            )
        }
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
        if (source !is AnimeCatalogueSource) return
        val filters = filterSerializer.decode(source.getFilterList(), savedSearch.filtersJson)
        search(query = savedSearch.query, filters = filters)
        setDialog(null)
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun setToolbarQuery(query: String?) {
        mutableState.update { it.copy(toolbarQuery = query) }
    }

    sealed class Listing(open val query: String?, open val filters: AnimeFilterList) {
        data object Popular : Listing(
            query = GetRemoteAnime.QUERY_POPULAR,
            filters = AnimeFilterList(),
        )
        data object Latest : Listing(
            query = GetRemoteAnime.QUERY_LATEST,
            filters = AnimeFilterList(),
        )
        data class Search(override val query: String?, override val filters: AnimeFilterList) : Listing(
            query = query,
            filters = filters,
        )

        companion object {
            fun valueOf(query: String?): Listing {
                return when (query) {
                    GetRemoteAnime.QUERY_POPULAR -> Popular
                    GetRemoteAnime.QUERY_LATEST -> Latest
                    else -> Search(query = query, filters = AnimeFilterList()) // filters are filled in later
                }
            }
        }
    }

    sealed interface Dialog {
        data object Filter : Dialog
        data object SavedSearches : Dialog
        data class RemoveAnime(val anime: Anime) : Dialog
        data class AddDuplicateAnime(val anime: Anime, val duplicate: Anime) : Dialog
        data class ChangeAnimeCollection(
            val anime: Anime,
            val initialSelection: ImmutableList<CheckboxState.State<Collection>>,
        ) : Dialog
        data class Migrate(val newAnime: Anime, val oldAnime: Anime) : Dialog
    }

    @Immutable
    data class State(
        val listing: Listing,
        val filters: AnimeFilterList = AnimeFilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
    }
}
