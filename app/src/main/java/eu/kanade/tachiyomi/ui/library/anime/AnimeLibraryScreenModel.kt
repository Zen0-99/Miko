package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.core.util.fastFilterNot
import eu.kanade.core.util.fastPartition
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.episode.getNextUnseen
import eu.kanade.tachiyomi.util.removeBackgrounds
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.readingorder.interactor.AddReadingOrderEdge
import tachiyomi.domain.readingorder.interactor.AddReadingOrderNode
import tachiyomi.domain.readingorder.interactor.CreateReadingOrder
import tachiyomi.domain.readingorder.interactor.GetReadingOrders
import tachiyomi.domain.readingorder.interactor.GetReadingOrderNodes
import tachiyomi.domain.readingorder.interactor.GetReadingOrderEdges
import tachiyomi.domain.readingorder.interactor.GetReadingOrderProgress
import tachiyomi.domain.readingorder.interactor.AutoRemoveCompletedReadingOrderEntries
import tachiyomi.domain.readingorder.interactor.RemoveReadingOrderNode
import tachiyomi.domain.readingorder.interactor.DeleteReadingOrder
import tachiyomi.domain.readingorder.interactor.UpdateReadingOrder
import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.domain.collection.anime.interactor.GetAnimeCustomOrder
import tachiyomi.domain.collection.anime.interactor.GetVisibleAnimeCollections
import tachiyomi.domain.collection.anime.interactor.SetAnimeCollections
import tachiyomi.domain.collection.anime.interactor.SetAnimeCustomOrder
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.applyFilter
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.anime.model.sort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroupMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

/**
 * Typealias for the library anime, using the collection as keys, and list of anime as values.
 */
typealias AnimeLibraryMap = Map<Collection, List<AnimeLibraryItem>>

class AnimeLibraryScreenModel(
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getCollections: GetVisibleAnimeCollections = Injekt.get(),
    private val getAnimeCustomOrder: GetAnimeCustomOrder = Injekt.get(),
    private val getTracksPerAnime: GetTracksPerAnime = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val setAnimeCollections: SetAnimeCollections = Injekt.get(),
    private val setAnimeCustomOrder: SetAnimeCustomOrder = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: AnimeCoverCache = Injekt.get(),
    private val backgroundCache: AnimeBackgroundCache = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
    private val downloadCache: AnimeDownloadCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val createReadingOrder: CreateReadingOrder = Injekt.get(),
    private val addReadingOrderNode: AddReadingOrderNode = Injekt.get(),
    private val addReadingOrderEdge: AddReadingOrderEdge = Injekt.get(),
    private val getReadingOrders: GetReadingOrders = Injekt.get(),
    private val getReadingOrderNodes: GetReadingOrderNodes = Injekt.get(),
    private val getReadingOrderEdges: GetReadingOrderEdges = Injekt.get(),
    private val getReadingOrderProgress: GetReadingOrderProgress = Injekt.get(),
    private val autoRemoveCompleted: AutoRemoveCompletedReadingOrderEntries = Injekt.get(),
    private val removeReadingOrderNode: RemoveReadingOrderNode = Injekt.get(),
    private val deleteReadingOrderInteractor: DeleteReadingOrder = Injekt.get(),
    private val updateReadingOrder: UpdateReadingOrder = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getAnimeFavorites: GetAnimeFavorites = Injekt.get(),
) : StateScreenModel<AnimeLibraryScreenModel.State>(State()) {

    var activeCollectionIndex: Int by libraryPreferences.lastUsedAnimeCollection().asState(
        screenModelScope,
    )

    init {
        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.debounce(SEARCH_DEBOUNCE_MILLIS),
                getLibraryFlow(),
                getTracksPerAnime.subscribe(),
                getTrackingFilterFlow(),
                downloadCache.changes,
            ) { searchQuery, library, tracks, trackingFilter, _ ->
                library
                    .applyFilters(tracks, trackingFilter)
                    .applySort(tracks, trackingFilter.keys)
                    .mapValues { (_, value) ->
                        if (searchQuery != null) {
                            value.filter { it.matches(searchQuery) }
                        } else {
                            value
                        }
                    }
            }
                .collectLatest {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            library = it,
                        )
                    }
                }
        }

        combine(
            libraryPreferences.collectionTabs().changes(),
            libraryPreferences.collectionNumberOfItems().changes(),
            libraryPreferences.showContinueViewingButton().changes(),
            libraryPreferences.showLibraryTitle().changes(),
        ) { a, b, c, d -> arrayOf(a, b, c, d) }
            .onEach { values ->
                mutableState.update { state ->
                    state.copy(
                        showCollectionTabs = values[0] as Boolean,
                        showAnimeCount = values[1] as Boolean,
                        showAnimeContinueButton = values[2] as Boolean,
                        showLibraryTitle = values[3] as Boolean,
                    )
                }
            }
            .launchIn(screenModelScope)

        combine(
            getAnimelibItemPreferencesFlow(),
            getTrackingFilterFlow(),
        ) { prefs, trackFilter ->
            (
                listOf(
                    prefs.filterDownloaded,
                    prefs.filterUnseen,
                    prefs.filterStarted,
                    prefs.filterBookmarked,
                    prefs.filterCompleted,
                    prefs.filterIntervalCustom,
                ) + trackFilter.values
                ).any { it != TriState.DISABLED }
        }
            .distinctUntilChanged()
            .onEach {
                mutableState.update { state ->
                    state.copy(hasActiveFilters = it)
                }
            }
            .launchIn(screenModelScope)

        screenModelScope.launchIO {
            loadSavedReadingOrderLayers()
        }
    }

    private suspend fun AnimeLibraryMap.applyFilters(
        trackMap: Map<Long, List<AnimeTrack>>,
        trackingFilter: Map<Long, TriState>,
    ): AnimeLibraryMap {
        val prefs = getAnimelibItemPreferencesFlow().first()
        val downloadedOnly = prefs.globalFilterDownloaded
        val skipOutsideReleasePeriod = prefs.skipOutsideReleasePeriod
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else prefs.filterDownloaded
        val filterUnseen = prefs.filterUnseen
        val filterStarted = prefs.filterStarted
        val filterBookmarked = prefs.filterBookmarked
        val filterCompleted = prefs.filterCompleted
        val filterIntervalCustom = prefs.filterIntervalCustom

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

        val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        val filterFnDownloaded: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) {
                it.libraryAnime.anime.isLocal() ||
                    it.downloadCount > 0 ||
                    downloadManager.getDownloadCount(it.libraryAnime.anime) > 0
            }
        }

        val filterFnUnseen: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterUnseen) { it.libraryAnime.unseenCount > 0 }
        }

        val filterFnStarted: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryAnime.hasStarted }
        }

        val filterFnBookmarked: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryAnime.hasBookmarks }
        }

        val filterFnCompleted: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryAnime.anime.status.toInt() == SAnime.COMPLETED }
        }

        val filterFnIntervalCustom: (AnimeLibraryItem) -> Boolean = {
            if (skipOutsideReleasePeriod) {
                applyFilter(filterIntervalCustom) { it.libraryAnime.anime.fetchInterval < 0 }
            } else {
                true
            }
        }

        val filterFnTracking: (AnimeLibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val animeTracks = trackMap
                .mapValues { entry -> entry.value.map { it.trackerId } }[item.libraryAnime.id]
                .orEmpty()

            val isExcluded = excludedTracks.isNotEmpty() && animeTracks.fastAny { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || animeTracks.fastAny { it in includedTracks }

            !isExcluded && isIncluded
        }

        val filterFn: (AnimeLibraryItem) -> Boolean = {
            filterFnDownloaded(it) &&
                filterFnUnseen(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnTracking(it)
        }

        return mapValues { (_, value) -> value.fastFilter(filterFn) }
    }

    private suspend fun AnimeLibraryMap.applySort(
        trackMap: Map<Long, List<AnimeTrack>>,
        loggedInTrackerIds: Set<Long>,
    ): AnimeLibraryMap {
        val sortAlphabetically: (AnimeLibraryItem, AnimeLibraryItem) -> Int = { i1, i2 ->
            i1.libraryAnime.anime.title.lowercase().compareToWithCollator(i2.libraryAnime.anime.title.lowercase())
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            val trackerMap = trackerManager.getAll(loggedInTrackerIds).associateBy { e -> e.id }
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else ->
                        entry.value
                            .mapNotNull { trackerMap[it.trackerId]?.animeService?.get10PointScore(it) }
                            .average()
                }
            }
        }

        fun AnimeLibrarySort.comparator(): Comparator<AnimeLibraryItem> = Comparator { i1, i2 ->
            when (this.type) {
                AnimeLibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(i1, i2)
                }
                AnimeLibrarySort.Type.LastSeen -> {
                    i1.libraryAnime.lastSeen.compareTo(i2.libraryAnime.lastSeen)
                }
                AnimeLibrarySort.Type.LastUpdate -> {
                    i1.libraryAnime.anime.lastUpdate.compareTo(i2.libraryAnime.anime.lastUpdate)
                }
                AnimeLibrarySort.Type.UnseenCount -> when {
                    // Ensure unseen content comes first
                    i1.libraryAnime.unseenCount == i2.libraryAnime.unseenCount -> 0
                    i1.libraryAnime.unseenCount == 0L -> if (this.isAscending) 1 else -1
                    i2.libraryAnime.unseenCount == 0L -> if (this.isAscending) -1 else 1
                    else -> i1.libraryAnime.unseenCount.compareTo(i2.libraryAnime.unseenCount)
                }
                AnimeLibrarySort.Type.TotalEpisodes -> {
                    i1.libraryAnime.totalCount.compareTo(i2.libraryAnime.totalCount)
                }
                AnimeLibrarySort.Type.LatestEpisode -> {
                    i1.libraryAnime.latestUpload.compareTo(i2.libraryAnime.latestUpload)
                }
                AnimeLibrarySort.Type.EpisodeFetchDate -> {
                    i1.libraryAnime.episodeFetchedAt.compareTo(i2.libraryAnime.episodeFetchedAt)
                }
                AnimeLibrarySort.Type.DateAdded -> {
                    i1.libraryAnime.anime.dateAdded.compareTo(i2.libraryAnime.anime.dateAdded)
                }
                AnimeLibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[i1.libraryAnime.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[i2.libraryAnime.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
                AnimeLibrarySort.Type.AiringTime -> when {
                    i1.libraryAnime.unseenCount != i2.libraryAnime.unseenCount ->
                        i1.libraryAnime.unseenCount.compareTo(i2.libraryAnime.unseenCount)
                    i1.libraryAnime.anime.nextEpisodeAiringAt == i2.libraryAnime.anime.nextEpisodeAiringAt -> 0
                    i1.libraryAnime.anime.nextEpisodeAiringAt == 0L -> if (this.isAscending) 1 else -1
                    i2.libraryAnime.anime.nextEpisodeAiringAt == 0L -> if (this.isAscending) -1 else 1
                    else -> i1.libraryAnime.anime.nextEpisodeAiringAt.compareTo(
                        i2.libraryAnime.anime.nextEpisodeAiringAt,
                    )
                }
                AnimeLibrarySort.Type.Random -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
                AnimeLibrarySort.Type.CustomOrder -> {
                    error("CustomOrder is handled separately")
                }
                AnimeLibrarySort.Type.ReadingOrder -> {
                    val layer1 = state.value.savedReadingOrderLayers[i1.libraryAnime.id] ?: Int.MAX_VALUE
                    val layer2 = state.value.savedReadingOrderLayers[i2.libraryAnime.id] ?: Int.MAX_VALUE
                    layer1.compareTo(layer2)
                }
            }
        }

        return mapValues { (key, value) ->
            if (key.sort.type == AnimeLibrarySort.Type.Random) {
                return@mapValues value.shuffled(Random(libraryPreferences.randomAnimeSortSeed().get()))
            }

            if (key.sort.type == AnimeLibrarySort.Type.CustomOrder) {
                val order = getAnimeCustomOrder.await(key.id)
                val positionMap = order.withIndex().associate { it.value to it.index }
                val unorderedIndex = order.size
                return@mapValues value.sortedWith(
                    Comparator { i1, i2 ->
                        val pos1 = positionMap[i1.libraryAnime.id] ?: unorderedIndex
                        val pos2 = positionMap[i2.libraryAnime.id] ?: unorderedIndex
                        if (pos1 != pos2) {
                            pos1.compareTo(pos2)
                        } else {
                            sortAlphabetically(i1, i2)
                        }
                    },
                )
            }

            val comparator = key.sort.comparator()
                .let { if (key.sort.isAscending) it else it.reversed() }
                .thenComparator(sortAlphabetically)

            value.sortedWith(comparator)
        }
    }

    private fun getAnimelibItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge().changes(),
            libraryPreferences.unreadBadge().changes(),
            libraryPreferences.localBadge().changes(),
            libraryPreferences.languageBadge().changes(),
            libraryPreferences.autoUpdateItemRestrictions().changes(),

            preferences.downloadedOnly().changes(),
            libraryPreferences.filterDownloadedAnime().changes(),
            libraryPreferences.filterUnseen().changes(),
            libraryPreferences.filterStartedAnime().changes(),
            libraryPreferences.filterBookmarkedAnime().changes(),
            libraryPreferences.filterCompletedAnime().changes(),
            libraryPreferences.filterIntervalCustom().changes(),
            transform = {
                ItemPreferences(
                    downloadBadge = it[0] as Boolean,
                    unseenBadge = it[1] as Boolean,
                    localBadge = it[2] as Boolean,
                    languageBadge = it[3] as Boolean,
                    skipOutsideReleasePeriod = LibraryPreferences.ENTRY_OUTSIDE_RELEASE_PERIOD in (it[4] as Set<*>),
                    globalFilterDownloaded = it[5] as Boolean,
                    filterDownloaded = it[6] as TriState,
                    filterUnseen = it[7] as TriState,
                    filterStarted = it[8] as TriState,
                    filterBookmarked = it[9] as TriState,
                    filterCompleted = it[10] as TriState,
                    filterIntervalCustom = it[11] as TriState,
                )
            },
        )
    }

    /**
     * Get the collections and all its anime from the database.
     */
    private fun getLibraryFlow(): Flow<AnimeLibraryMap> {
        val animelibAnimesFlow = combine(
            getLibraryAnime.subscribe(),
            getAnimelibItemPreferencesFlow(),
            downloadCache.changes,
        ) { animelibAnimeList, prefs, _ ->
            animelibAnimeList
                .map { animelibAnime ->
                    // Display mode based on user preference: take it from global library setting or collection
                    AnimeLibraryItem(
                        animelibAnime,
                        downloadCount = if (prefs.downloadBadge) {
                            downloadManager.getDownloadCount(animelibAnime.anime).toLong()
                        } else {
                            0
                        },
                        unseenCount = if (prefs.unseenBadge) animelibAnime.unseenCount else 0,
                        isLocal = if (prefs.localBadge) animelibAnime.anime.isLocal() else false,
                        sourceLanguage = if (prefs.languageBadge) {
                            sourceManager.getOrStub(animelibAnime.anime.source).lang
                        } else {
                            ""
                        },
                    )
                }
                .groupBy { it.libraryAnime.collection }
        }

        return combine(
            getCollections.subscribe(),
            animelibAnimesFlow,
            libraryPreferences.groupLibraryBy().changes(),
            getTracksPerAnime.subscribe(),
            trackerManager.loggedInTrackersFlow(),
        ) { collections, animelibAnime, groupMode, tracks, loggedInTrackers ->
            if (groupMode == LibraryGroupMode.BY_DEFAULT) {
                // Original category-based grouping
                val displayCollections = if (animelibAnime.isNotEmpty() && !animelibAnime.containsKey(0)) {
                    collections.fastFilterNot { it.isSystemCollection }
                } else {
                    collections
                }
                displayCollections.associateWith { animelibAnime[it.id].orEmpty() }
            } else {
                // Regroup by selected criterion
                val allItems = animelibAnime.values.flatten()
                groupLibraryItemsByMode(allItems, groupMode, tracks, loggedInTrackers)
            }
        }
    }

    /**
     * Groups library items by the selected group mode, creating synthetic Collection objects.
     */
    private fun groupLibraryItemsByMode(
        items: List<AnimeLibraryItem>,
        groupMode: Int,
        tracks: Map<Long, List<AnimeTrack>>,
        loggedInTrackers: List<eu.kanade.tachiyomi.data.track.Tracker>,
    ): AnimeLibraryMap {
        val context = preferences.context
        val unknown = context.stringResource(MR.strings.unknown)

        val groupNames: Map<String, List<AnimeLibraryItem>> = when (groupMode) {
            LibraryGroupMode.UNGROUPED -> {
                mapOf(ungroupedName to items)
            }
            LibraryGroupMode.BY_TAG -> {
                items.flatMap { item ->
                    val tags = item.libraryAnime.anime.genre
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?.distinct()
                    if (tags.isNullOrEmpty()) {
                        listOf(unknown to item)
                    } else {
                        tags.map { it to item }
                    }
                }.groupBy({ it.first }, { it.second })
            }
            LibraryGroupMode.BY_SOURCE -> {
                items.groupBy { item ->
                    val source = sourceManager.getOrStub(item.libraryAnime.anime.source)
                    source.name
                }
            }
            LibraryGroupMode.BY_STATUS -> {
                items.groupBy { item ->
                    statusToString(item.libraryAnime.anime.status, context)
                }
            }
            LibraryGroupMode.BY_TRACK_STATUS -> {
                items.groupBy { item ->
                    val animeTracks = tracks[item.libraryAnime.anime.id].orEmpty()
                    val track = animeTracks.find { track ->
                        loggedInTrackers.any { it.id == track.trackerId }
                    }
                    val service = loggedInTrackers.find { it.id == track?.trackerId }
                    if (track != null && service != null) {
                        service.animeService.getStatusForAnime(track.status)?.let {
                            context.stringResource(it)
                        } ?: unknown
                    } else {
                        context.stringResource(MR.strings.not_tracked)
                    }
                }
            }
            LibraryGroupMode.BY_AUTHOR -> {
                items.flatMap { item ->
                    val anime = item.libraryAnime.anime
                    val authors = listOfNotNull(
                        anime.author?.takeUnless { it.isBlank() },
                        anime.artist?.takeUnless { it.isBlank() },
                    ).flatMap {
                        it.split(",", "/", " x ", " - ")
                            .map { name -> name.trim() }
                            .filter { name -> name.isNotBlank() }
                    }.distinct()
                    if (authors.isEmpty()) {
                        listOf(unknown to item)
                    } else {
                        authors.map { it to item }
                    }
                }.groupBy({ it.first }, { it.second })
            }
            LibraryGroupMode.BY_LANGUAGE -> {
                items.groupBy { item ->
                    val lang = sourceManager.getOrStub(item.libraryAnime.anime.source).lang
                    if (lang.isBlank()) unknown else lang
                }
            }
            else -> mapOf(ungroupedName to items)
        }

        // Create synthetic Collection objects, sorted alphabetically
        return groupNames.entries
            .sortedBy { it.key.lowercase() }
            .mapIndexed { index, (name, groupedItems) ->
                val syntheticCollection = Collection(
                    id = syntheticCollectionId(name),
                    name = name,
                    order = index.toLong(),
                    flags = 0L,
                    hidden = false,
                )
                syntheticCollection to groupedItems
            }
            .toMap()
    }

    private val ungroupedName: String = "All"

    private fun syntheticCollectionId(name: String): Long {
        // Use negative hash to distinguish from real category IDs
        return -(name.hashCode().toLong() and 0x7FFFFFFFL)
    }

    private fun statusToString(status: Long, context: android.content.Context): String {
        val resId = when (status.toInt()) {
            SAnime.ONGOING -> MR.strings.ongoing
            SAnime.COMPLETED -> MR.strings.completed
            SAnime.LICENSED -> MR.strings.licensed
            SAnime.PUBLISHING_FINISHED -> MR.strings.publishing_finished
            SAnime.CANCELLED -> MR.strings.cancelled
            SAnime.ON_HIATUS -> MR.strings.on_hiatus
            else -> MR.strings.unknown
        }
        return context.stringResource(resId)
    }

    /**
     * Flow of tracking filter preferences
     *
     * @return map of track id with the filter value
     */
    private fun getTrackingFilterFlow(): Flow<Map<Long, TriState>> {
        return trackerManager.loggedInTrackersFlow().flatMapLatest { loggedInTrackers ->
            if (loggedInTrackers.isEmpty()) return@flatMapLatest flowOf(emptyMap())

            val prefFlows = loggedInTrackers.map { tracker ->
                libraryPreferences.filterTrackedAnime(tracker.id.toInt()).changes()
            }
            combine(prefFlows) {
                loggedInTrackers
                    .mapIndexed { index, tracker -> tracker.id to it[index] }
                    .toMap()
            }
        }
    }

    /**
     * Returns the common collections for the given list of anime.
     *
     * @param animes the list of anime.
     */
    private suspend fun getCommonCollections(animes: List<Anime>): Set<Collection> {
        if (animes.isEmpty()) return emptySet()
        return animes
            .map { getCollections.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    suspend fun getNextUnseenEpisode(anime: Anime): Episode? {
        return getEpisodesByAnimeId.await(anime.id).getNextUnseen(anime, downloadManager)
    }

    /**
     * Returns the mix (non-common) collections for the given list of anime.
     *
     * @param animes the list of anime.
     */
    private suspend fun getMixCollections(animes: List<Anime>): Set<Collection> {
        if (animes.isEmpty()) return emptySet()
        val nimeCollections = animes.map { getCollections.await(it.id).toSet() }
        val common = nimeCollections.reduce { set1, set2 -> set1.intersect(set2) }
        return nimeCollections.flatten().distinct().subtract(common)
    }

    fun runDownloadActionSelection(action: DownloadAction) {
        val selection = state.value.selection
        val animes = selection.map { it.anime }.toList()
        when (action) {
            DownloadAction.NEXT_1_ITEM -> downloadUnseenEpisodes(animes, 1)
            DownloadAction.NEXT_5_ITEMS -> downloadUnseenEpisodes(animes, 5)
            DownloadAction.NEXT_10_ITEMS -> downloadUnseenEpisodes(animes, 10)
            DownloadAction.NEXT_25_ITEMS -> downloadUnseenEpisodes(animes, 25)
            DownloadAction.UNVIEWED_ITEMS -> downloadUnseenEpisodes(animes, null)
        }
        clearSelection()
    }

    /**
     * Queues the amount specified of unseen episodes from the list of animes given.
     *
     * @param animes the list of anime.
     * @param amount the amount to queue or null to queue all
     */
    private fun downloadUnseenEpisodes(animes: List<Anime>, amount: Int?) {
        screenModelScope.launchNonCancellable {
            animes.forEach { anime ->
                val episodes = getNextEpisodes.await(anime.id)
                    .fastFilterNot { episode ->
                        downloadManager.getQueuedDownloadOrNull(episode.id) != null ||
                            downloadManager.isEpisodeDownloaded(
                                episode.name,
                                episode.scanlator,
                                anime.title,
                                anime.source,
                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadEpisodes(anime, episodes)
            }
        }
    }

    /**
     * Marks animes' episodes seen status.
     */
    fun markSeenSelection(seen: Boolean) {
        val animes = state.value.selection.toList()
        screenModelScope.launchNonCancellable {
            animes.forEach { anime ->
                setSeenStatus.await(
                    anime = anime.anime,
                    seen = seen,
                )
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected anime.
     *
     * @param animeList the list of anime to delete.
     * @param deleteFromLibrary whether to delete anime from library.
     * @param deleteEpisodes whether to delete downloaded episodes.
     */
    fun removeAnimes(animeList: List<Anime>, deleteFromLibrary: Boolean, deleteEpisodes: Boolean) {
        screenModelScope.launchNonCancellable {
            val animeToDelete = animeList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = animeToDelete.map {
                    it.removeCovers(coverCache)
                    it.removeBackgrounds(backgroundCache)
                    AnimeUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateAnime.awaitAll(toDelete)
            }

            if (deleteEpisodes) {
                animeToDelete.forEach { anime ->
                    val source = sourceManager.get(anime.source) as? AnimeHttpSource
                    if (source != null) {
                        downloadManager.deleteAnime(anime, source)
                    }
                }
            }
        }
    }

    /**
     * Bulk update collections of anime using old and new common collections.
     *
     * @param animeList the list of anime to move.
     * @param addCollections the collections to add for all animes.
     * @param removeCollections the collections to remove in all animes.
     */
    fun setAnimeCollections(
        animeList: List<Anime>,
        addCollections: List<Long>,
        removeCollections: List<Long>,
    ) {
        screenModelScope.launchNonCancellable {
            animeList.forEach { anime ->
                val collectionIds = getCollections.await(anime.id)
                    .map { it.id }
                    .subtract(removeCollections.toSet())
                    .plus(addCollections)
                    .toList()

                setAnimeCollections.await(anime.id, collectionIds)
            }
        }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode().asState(screenModelScope)
    }

    fun getColumnsPreferenceForCurrentOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (
            if (isLandscape) {
                libraryPreferences.animeLandscapeColumns()
            } else {
                libraryPreferences.animePortraitColumns()
            }
            ).asState(
            screenModelScope,
        )
    }

    suspend fun getRandomAnimelibItemForCurrentCollection(): AnimeLibraryItem? {
        if (state.value.collections.isEmpty()) return null

        return withIOContext {
            state.value
                .getAnimelibItemsByCollectionId(state.value.collections[activeCollectionIndex].id)
                ?.randomOrNull()
        }
    }

    suspend fun updateCustomOrder(collectionId: Long, animeIds: List<Long>) {
        withIOContext { setAnimeCustomOrder.await(collectionId, animeIds) }
    }

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun clearSelection() {
        mutableState.update {
            it.copy(
                selection = persistentListOf(),
                readingOrderMode = false,
                readingOrderLayers = persistentListOf(),
                readingOrderCurrentLayer = 0,
                editingReadingOrderId = null,
                readingOrderName = "",
            )
        }
    }

    fun toggleSelection(anime: LibraryAnime) {
        val state = this.state.value
        if (!state.readingOrderMode) {
            mutableState.update { s ->
                val newSelection = s.selection.mutate { list ->
                    if (list.fastAny { it.id == anime.id }) {
                        list.removeAll { it.id == anime.id }
                    } else {
                        list.add(anime)
                    }
                }
                s.copy(selection = newSelection)
            }
            return
        }
        val isRemoving = state.selection.fastAny { it.id == anime.id }
        if (isRemoving && state.editingReadingOrderId != null) {
            val isInOrder = state.readingOrderLayers.flatten().fastAny { it.id == anime.id } ||
                state.selection.fastAny { it.id == anime.id }
            if (isInOrder) {
                mutableState.update { it.copy(dialog = Dialog.ReadingOrderRemoveConfirm(anime)) }
                return
            }
        }
        val existingDepth = state.readingOrderLayers.indexOfFirst { layer ->
            layer.fastAny { it.id == anime.id }
        }
        if (existingDepth >= 0 && existingDepth != state.readingOrderCurrentLayer) {
            mutableState.update {
                it.copy(dialog = Dialog.ReadingOrderMoveDepth(anime, existingDepth + 1, state.readingOrderCurrentLayer + 1))
            }
            return
        }
        mutableState.update { s ->
            val newSelection = s.selection.mutate { list ->
                if (list.fastAny { it.id == anime.id }) {
                    list.removeAll { it.id == anime.id }
                } else {
                    list.add(anime)
                }
            }
            s.copy(selection = newSelection)
        }
    }

    fun confirmMoveEntryDepth(anime: LibraryAnime) {
        mutableState.update { s ->
            val newLayers = s.readingOrderLayers.mapIndexed { index, layer ->
                if (index == s.readingOrderCurrentLayer) {
                    layer.mutate { l ->
                        if (!l.fastAny { it.id == anime.id }) l.add(anime)
                    }
                } else {
                    layer.mutate { l -> l.removeAll { it.id == anime.id } }
                }
            }.toPersistentList()
            val compactedLayers = compactLayers(newLayers)
            s.copy(readingOrderLayers = compactedLayers, dialog = null)
        }
    }

    private fun compactLayers(layers: PersistentList<PersistentList<LibraryAnime>>): PersistentList<PersistentList<LibraryAnime>> {
        return layers.filter { it.isNotEmpty() }.toPersistentList()
    }

    fun confirmRemoveFromReadingOrder(anime: LibraryAnime) {
        val state = this.state.value
        val orderId = state.editingReadingOrderId ?: return
        screenModelScope.launchNonCancellable {
            withIOContext {
                removeReadingOrderNode.await(orderId, anime.anime.id)
            }
            withUIContext {
                mutableState.update { s ->
                    val newSelection = s.selection.mutate { list ->
                        list.removeAll { it.id == anime.id }
                    }
                    val newLayers = s.readingOrderLayers.map { layer ->
                        layer.mutate { l -> l.removeAll { it.id == anime.id } }
                    }.toPersistentList()
                    s.copy(selection = newSelection, readingOrderLayers = newLayers, dialog = null)
                }
                loadSavedReadingOrderLayers()
            }
        }
    }

    fun cancelRemoveDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun showReadingOrderDialog() {
        screenModelScope.launchIO {
            val orders = getReadingOrders.await("anime")
            withUIContext {
                mutableState.update { it.copy(dialog = Dialog.ReadingOrderPicker(orders)) }
            }
        }
    }

    fun confirmDeleteReadingOrder(order: ReadingOrder) {
        screenModelScope.launchNonCancellable {
            withIOContext {
                deleteReadingOrderInteractor.await(order.id)
            }
            withUIContext {
                mutableState.update { it.copy(dialog = null) }
                loadSavedReadingOrderLayers()
            }
        }
    }

    fun confirmEditReadingOrder(order: ReadingOrder, newName: String) {
        screenModelScope.launchNonCancellable {
            withIOContext {
                updateReadingOrder.await(order.id, newName, null)
            }
            withUIContext {
                mutableState.update { it.copy(dialog = null) }
                loadSavedReadingOrderLayers()
            }
        }
    }

    suspend fun exportReadingOrder(order: ReadingOrder): String {
        val nodes = getReadingOrderNodes.await(order.id)
        val edges = getReadingOrderEdges.await(order.id)
        val entryTitles = mutableMapOf<Long, String>()
        val entryUrls = mutableMapOf<Long, String>()
        for (node in nodes) {
            val anime = getAnime.await(node.entryId)
            if (anime != null) {
                entryTitles[node.entryId] = anime.title
                entryUrls[node.entryId] = anime.url
            }
        }
        val nodesJson = nodes.joinToString(",") { node ->
            """{"entryId":${node.entryId},"position":${node.position},"title":"${entryTitles[node.entryId]?.replace("\"", "\\\"") ?: ""}","url":"${entryUrls[node.entryId]?.replace("\"", "\\\"") ?: ""}"}"""
        }
        val edgesJson = edges.joinToString(",") { edge ->
            """{"fromEntryId":${edge.fromEntryId},"toEntryId":${edge.toEntryId}}"""
        }
        val desc = order.description
        val descJson = if (desc != null) "\"${desc.replace("\"", "\\\"")}\"" else "null"
        return """{"name":"${order.name.replace("\"", "\\\"")}","description":$descJson,"entryKind":"${order.entryKind}","nodes":[$nodesJson],"edges":[$edgesJson]}"""
    }

    suspend fun importReadingOrder(json: String): Boolean {
        return withIOContext {
            try {
                val nameMatch = Regex(""""name"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(json)
                val name = nameMatch?.groupValues?.get(1)?.replace("\\\"", "\"") ?: "Imported Reading Order"
                val nodeRegex = Regex("""\{"entryId":(\d+),"position":\d+,"title":"((?:[^"\\]|\\.)*)","url":"((?:[^"\\]|\\.)*)"\}""")
                val nodes = nodeRegex.findAll(json).map { match ->
                    match.groupValues[1].toLong() to match.groupValues[2].replace("\\\"", "\"")
                }.toList()
                val edgeRegex = Regex("""\{"fromEntryId":(\d+),"toEntryId":(\d+)\}""")
                val edges = edgeRegex.findAll(json).map { match ->
                    match.groupValues[1].toLong() to match.groupValues[2].toLong()
                }.toList()
                val favorites = getAnimeFavorites.await()
                val titleToId = favorites.associateBy { it.title.lowercase() }
                val orderId = createReadingOrder.await(name, null, "anime")
                val origIdToNewId = mutableMapOf<Long, Long>()
                for ((origEntryId, title) in nodes) {
                    val anime = titleToId[title.lowercase()]
                    if (anime != null) {
                        addReadingOrderNode.await(orderId, anime.id)
                        origIdToNewId[origEntryId] = anime.id
                    }
                }
                for ((fromOrigId, toOrigId) in edges) {
                    val fromId = origIdToNewId[fromOrigId] ?: continue
                    val toId = origIdToNewId[toOrigId] ?: continue
                    addReadingOrderEdge.await(orderId, fromId, toId)
                }
                withUIContext {
                    mutableState.update { it.copy(dialog = null) }
                    loadSavedReadingOrderLayers()
                }
                true
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                false
            }
        }
    }

    fun createNewReadingOrder(name: String) {
        mutableState.update {
            it.copy(
                dialog = null,
                readingOrderMode = true,
                readingOrderCurrentLayer = 0,
                readingOrderName = name,
                editingReadingOrderId = null,
                readingOrderLayers = persistentListOf(),
            )
        }
    }

    fun editExistingReadingOrder(orderId: Long) {
        screenModelScope.launchIO {
            val order = getReadingOrders.await(orderId) ?: return@launchIO
            val nodes = getReadingOrderNodes.await(orderId)
            val edges = getReadingOrderEdges.await(orderId)
            val entryIdsInOrder = nodes.map { it.entryId }.toSet()
            val libraryAnimeMap = state.value.library.values.flatten().associateBy { it.libraryAnime.anime.id }
            val layers = buildLayersFromEdges(nodes, edges, entryIdsInOrder, libraryAnimeMap)
            withUIContext {
                mutableState.update {
                    it.copy(
                        dialog = null,
                        readingOrderMode = true,
                        readingOrderCurrentLayer = 0,
                        readingOrderName = order.name,
                        editingReadingOrderId = orderId,
                        readingOrderLayers = layers,
                        selection = layers.getOrElse(0) { persistentListOf() },
                    )
                }
            }
        }
    }

    private fun buildLayersFromEdges(
        nodes: List<tachiyomi.domain.readingorder.model.ReadingOrderNode>,
        edges: List<tachiyomi.domain.readingorder.model.ReadingOrderEdge>,
        entryIdsInOrder: Set<Long>,
        libraryAnimeMap: Map<Long, AnimeLibraryItem>,
    ): PersistentList<PersistentList<LibraryAnime>> {
        val entryToLayer = mutableMapOf<Long, Int>()
        val remaining = entryIdsInOrder.toMutableSet()
        var layer = 0
        while (remaining.isNotEmpty()) {
            val layerItems = remaining.filter { id ->
                edges.none { it.toEntryId == id && it.fromEntryId in remaining }
            }
            if (layerItems.isEmpty()) {
                remaining.forEach { entryToLayer[it] = layer }
                break
            }
            layerItems.forEach { id ->
                entryToLayer[id] = layer
                remaining.remove(id)
            }
            layer++
        }
        val maxLayer = entryToLayer.values.maxOrNull() ?: 0
        val result = mutableListOf<PersistentList<LibraryAnime>>()
        for (i in 0..maxLayer) {
            val layerEntryIds = entryToLayer.entries.filter { it.value == i }.map { it.key }
            val layerAnime = layerEntryIds.mapNotNull { id ->
                libraryAnimeMap[id]?.libraryAnime
            }.toPersistentList()
            result.add(layerAnime)
        }
        return result.toPersistentList()
    }

    fun enterReadingOrderMode() {
        showReadingOrderDialog()
    }

    fun exitReadingOrderMode() {
        mutableState.update {
            it.copy(
                readingOrderMode = false,
                readingOrderLayers = persistentListOf(),
                readingOrderCurrentLayer = 0,
                selection = persistentListOf(),
                editingReadingOrderId = null,
                readingOrderName = "",
            )
        }
    }

    fun advanceReadingOrderLayer() {
        val state = this.state.value
        if (state.selection.isEmpty()) return
        mutableState.update { s ->
            val newLayers = s.readingOrderLayers.mutate { layers ->
                while (layers.size <= s.readingOrderCurrentLayer) {
                    layers.add(persistentListOf())
                }
                layers[s.readingOrderCurrentLayer] = s.selection
            }
            s.copy(
                readingOrderLayers = newLayers,
                readingOrderCurrentLayer = s.readingOrderCurrentLayer + 1,
                selection = persistentListOf(),
            )
        }
    }

    fun goBackReadingOrderLayer() {
        mutableState.update { state ->
            if (state.readingOrderCurrentLayer == 0) {
                return@update state.copy(
                    readingOrderMode = false,
                    readingOrderLayers = persistentListOf(),
                    readingOrderCurrentLayer = 0,
                    selection = persistentListOf(),
                    editingReadingOrderId = null,
                    readingOrderName = "",
                )
            }
            val prevLayer = state.readingOrderCurrentLayer - 1
            val restoredSelection = state.readingOrderLayers.getOrNull(prevLayer) ?: persistentListOf()
            val newLayers = state.readingOrderLayers.mutate { layers ->
                if (layers.size > prevLayer) layers.removeAt(prevLayer)
            }
            state.copy(
                readingOrderLayers = newLayers,
                readingOrderCurrentLayer = prevLayer,
                selection = restoredSelection,
            )
        }
    }

    fun saveReadingOrder() {
        val state = this.state.value
        val allLayers = state.readingOrderLayers.mutate { layers ->
            while (layers.size <= state.readingOrderCurrentLayer) {
                layers.add(persistentListOf())
            }
            layers[state.readingOrderCurrentLayer] = state.selection
        }.filter { it.isNotEmpty() }

        if (allLayers.isEmpty() || allLayers.size < 2) {
            if (allLayers.size < 2) {
                val editingId = state.editingReadingOrderId
                screenModelScope.launchNonCancellable {
                    if (editingId != null) {
                        withIOContext { deleteReadingOrderInteractor.await(editingId) }
                    }
                    withUIContext {
                        mutableState.update {
                            it.copy(
                                readingOrderMode = false,
                                readingOrderLayers = persistentListOf(),
                                readingOrderCurrentLayer = 0,
                                selection = persistentListOf(),
                                editingReadingOrderId = null,
                                readingOrderName = "",
                            )
                        }
                        loadSavedReadingOrderLayers()
                    }
                }
            } else {
                exitReadingOrderMode()
            }
            return
        }

        val name = state.readingOrderName.ifBlank {
            "Reading Order ${java.text.SimpleDateFormat.getDateTimeInstance().format(java.util.Date(System.currentTimeMillis()))}"
        }
        val editingId = state.editingReadingOrderId

        screenModelScope.launchNonCancellable {
            withIOContext {
                if (editingId != null) {
                    val existingNodes = getReadingOrderNodes.await(editingId)
                    existingNodes.forEach { node ->
                        removeReadingOrderNode.await(editingId, node.entryId)
                    }
                    val allAnime = allLayers.flatten().distinctBy { it.id }
                    allAnime.forEach { anime ->
                        addReadingOrderNode.await(editingId, anime.anime.id)
                    }
                    for (i in 0 until allLayers.size - 1) {
                        val fromLayer = allLayers[i]
                        val toLayer = allLayers[i + 1]
                        for (from in fromLayer) {
                            for (to in toLayer) {
                                if (from.id != to.id) {
                                    addReadingOrderEdge.await(editingId, from.anime.id, to.anime.id)
                                }
                            }
                        }
                    }
                } else {
                    val orderId = createReadingOrder.await(
                        name = name,
                        description = "",
                        entryKind = "anime",
                    )
                    val allAnime = allLayers.flatten().distinctBy { it.id }
                    allAnime.forEach { anime ->
                        addReadingOrderNode.await(orderId, anime.anime.id)
                    }
                    for (i in 0 until allLayers.size - 1) {
                        val fromLayer = allLayers[i]
                        val toLayer = allLayers[i + 1]
                        for (from in fromLayer) {
                            for (to in toLayer) {
                                if (from.id != to.id) {
                                    addReadingOrderEdge.await(orderId, from.anime.id, to.anime.id)
                                }
                            }
                        }
                    }
                }
            }
            withUIContext {
                val msg = if (editingId != null) "Reading order updated" else "Reading order \"$name\" created"
                mutableState.update {
                    it.copy(
                        readingOrderMode = false,
                        readingOrderLayers = persistentListOf(),
                        readingOrderCurrentLayer = 0,
                        selection = persistentListOf(),
                        editingReadingOrderId = null,
                        readingOrderName = "",
                        readingOrderSavedMessage = msg,
                    )
                }
                loadSavedReadingOrderLayers()
            }
        }
    }

    fun clearReadingOrderSavedMessage() {
        mutableState.update { it.copy(readingOrderSavedMessage = null) }
    }

    suspend fun loadSavedReadingOrderLayers() {
        autoRemoveCompleted.await()
        val orders = getReadingOrders.await("anime")
        val layerMap = mutableMapOf<Long, Int>()
        val lockedIds = mutableSetOf<Long>()
        for (order in orders) {
            val nodes = getReadingOrderNodes.await(order.id)
            val edges = getReadingOrderEdges.await(order.id)
            val progressList = getReadingOrderProgress.awaitAll(order.id)
            val completedIds = progressList.filter { it.completed }.map { it.entryId }.toSet()
            val entryIds = nodes.map { it.entryId }.toSet()
            val entryToLayer = mutableMapOf<Long, Int>()
            val remaining = entryIds.toMutableSet()
            var layer = 0
            while (remaining.isNotEmpty()) {
                val layerItems = remaining.filter { id ->
                    edges.none { it.toEntryId == id && it.fromEntryId in remaining }
                }
                if (layerItems.isEmpty()) {
                    remaining.forEach { entryToLayer[it] = layer + 1 }
                    break
                }
                layerItems.forEach { id ->
                    entryToLayer[id] = layer + 1
                    remaining.remove(id)
                }
                layer++
            }
            val maxLayer = entryToLayer.values.maxOrNull() ?: 0
            for (currentLayer in 2..maxLayer) {
                val hasIncompletePrereq = (1 until currentLayer).any { prereqLayer ->
                    entryToLayer.entries.filter { it.value == prereqLayer }.any { it.key !in completedIds }
                }
                if (hasIncompletePrereq) {
                    entryToLayer.entries.filter { it.value >= currentLayer }.forEach {
                        lockedIds.add(it.key)
                    }
                }
            }
            entryToLayer.forEach { (id, lyr) -> layerMap[id] = lyr }
        }
        mutableState.update {
            it.copy(
                savedReadingOrderLayers = layerMap,
                lockedReadingOrderEntryIds = lockedIds,
            )
        }
    }

    /**
     * Selects all nimes between and including the given anime and the last pressed anime from the
     * same collection as the given anime
     */
    fun toggleRangeSelection(anime: LibraryAnime) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelected?.collection != anime.collection) {
                    list.add(anime)
                    return@mutate
                }

                val items = state.getAnimelibItemsByCollectionId(anime.collection)
                    ?.fastMap { it.libraryAnime }.orEmpty()
                val lastAnimeIndex = items.indexOf(lastSelected)
                val curAnimeIndex = items.indexOf(anime)

                val selectedIds = list.fastMap { it.id }
                val selectionRange = when {
                    lastAnimeIndex < curAnimeIndex -> IntRange(lastAnimeIndex, curAnimeIndex)
                    curAnimeIndex < lastAnimeIndex -> IntRange(curAnimeIndex, lastAnimeIndex)
                    // We shouldn't reach this point
                    else -> return@mutate
                }
                val newSelections = selectionRange.mapNotNull { index ->
                    items[index].takeUnless { it.id in selectedIds }
                }
                list.addAll(newSelections)
            }
            state.copy(selection = newSelection)
        }
    }

    fun selectAll(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val collectionId = state.collections.getOrNull(index)?.id ?: -1
                val selectedIds = list.fastMap { it.id }
                state.getAnimelibItemsByCollectionId(collectionId)
                    ?.fastMapNotNull { item ->
                        item.libraryAnime.takeUnless { it.id in selectedIds }
                    }
                    ?.let { list.addAll(it) }
            }
            state.copy(selection = newSelection)
        }
    }

    fun invertSelection(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val collectionId = state.collections[index].id
                val items = state.getAnimelibItemsByCollectionId(collectionId)?.fastMap { it.libraryAnime }.orEmpty()
                val selectedIds = list.fastMap { it.id }
                val (toRemove, toAdd) = items.fastPartition { it.id in selectedIds }
                val toRemoveIds = toRemove.fastMap { it.id }
                list.removeAll { it.id in toRemoveIds }
                list.addAll(toAdd)
            }
            state.copy(selection = newSelection)
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun openChangeCollectionDialog() {
        screenModelScope.launchIO {
            // Create a copy of selected anime
            val animeList = state.value.selection.map { it.anime }

            // Hide the default collection because it has a different behavior than the ones from db.
            val collections = state.value.collections.filter { it.id != 0L }

            // Get indexes of the common collections to preselect.
            val common = getCommonCollections(animeList)
            // Get indexes of the mix collections to preselect.
            val mix = getMixCollections(animeList)
            val preselected = collections
                .map {
                    when (it) {
                        in common -> CheckboxState.State.Checked(it)
                        in mix -> CheckboxState.TriState.Exclude(it)
                        else -> CheckboxState.State.None(it)
                    }
                }
                .toImmutableList()
            mutableState.update { it.copy(dialog = Dialog.ChangeCollection(animeList, preselected)) }
        }
    }

    fun openDeleteAnimeDialog() {
        val nimeList = state.value.selection.map { it.anime }
        mutableState.update { it.copy(dialog = Dialog.DeleteAnime(nimeList)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCollection(
            val anime: List<Anime>,
            val initialSelection: ImmutableList<CheckboxState<Collection>>,
        ) : Dialog
        data class DeleteAnime(val anime: List<Anime>) : Dialog
        data class ReadingOrderPicker(val orders: List<ReadingOrder>) : Dialog
        data class ReadingOrderRemoveConfirm(val anime: LibraryAnime) : Dialog
        data class ReadingOrderMoveDepth(val anime: LibraryAnime, val fromDepth: Int, val toDepth: Int) : Dialog
    }

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val unseenBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,
        val skipOutsideReleasePeriod: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnseen: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterCompleted: TriState,
        val filterIntervalCustom: TriState,
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val library: AnimeLibraryMap = emptyMap(),
        val searchQuery: String? = null,
        val selection: PersistentList<LibraryAnime> = persistentListOf(),
        val hasActiveFilters: Boolean = false,
        val showCollectionTabs: Boolean = false,
        val showAnimeCount: Boolean = false,
        val showAnimeContinueButton: Boolean = false,
        val showLibraryTitle: Boolean = true,
        val dialog: Dialog? = null,
        val readingOrderMode: Boolean = false,
        val readingOrderLayers: PersistentList<PersistentList<LibraryAnime>> = persistentListOf(),
        val readingOrderCurrentLayer: Int = 0,
        val readingOrderName: String = "",
        val editingReadingOrderId: Long? = null,
        val savedReadingOrderLayers: Map<Long, Int> = emptyMap(),
        val lockedReadingOrderEntryIds: Set<Long> = emptySet(),
        val readingOrderSavedMessage: String? = null,
    ) {
        private val libraryCount by lazy {
            library.values
                .flatten()
                .fastDistinctBy { it.libraryAnime.anime.id }
                .size
        }

        val isLibraryEmpty by lazy { libraryCount == 0 }

        val selectionMode = selection.isNotEmpty() || readingOrderMode

        fun getReadingOrderLayer(animeId: Long): Int? {
            if (readingOrderMode) {
                readingOrderLayers.forEachIndexed { index, layer ->
                    if (layer.fastAny { it.id == animeId }) return index + 1
                }
                if (selection.fastAny { it.id == animeId }) return readingOrderCurrentLayer + 1
                return null
            }
            return savedReadingOrderLayers[animeId]
        }

        val collections = library.keys.toList()

        fun getAnimelibItemsByCollectionId(collectionId: Long): List<AnimeLibraryItem>? {
            return library.firstNotNullOfOrNull { (k, v) -> v.takeIf { k.id == collectionId } }
        }

        fun getAnimelibItemsByPage(page: Int): List<AnimeLibraryItem> {
            return library.values.toTypedArray().getOrNull(page).orEmpty()
        }

        fun getAnimeCountForCollection(collection: Collection): Int? {
            return if (showAnimeCount || !searchQuery.isNullOrEmpty()) library[collection]?.size else null
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCollectionTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val collection = collections.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val collectionName = collection.let {
                if (it.isSystemCollection) defaultCollectionTitle else it.name
            }
            // Subtitle is always the collection name so the user can see which
            // collection they're viewing, even when tabs are hidden.
            val subtitle = collectionName
            val count = when {
                !showAnimeCount -> null
                else -> getAnimeCountForCollection(collection)
            }

            return LibraryToolbarTitle(defaultTitle, subtitle, count)
        }
    }
}
