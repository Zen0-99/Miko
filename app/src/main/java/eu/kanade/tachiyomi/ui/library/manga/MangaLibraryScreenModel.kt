package eu.kanade.tachiyomi.ui.library.manga

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
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.getNextUnread
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
import tachiyomi.domain.library.model.LibraryGroupMode
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import logcat.LogPriority
import tachiyomi.domain.readingorder.interactor.AddReadingOrderEdge
import tachiyomi.domain.readingorder.interactor.AddReadingOrderNode
import tachiyomi.domain.readingorder.interactor.CreateReadingOrder
import tachiyomi.domain.readingorder.interactor.GetReadingOrders
import tachiyomi.domain.readingorder.interactor.GetReadingOrderNodes
import tachiyomi.domain.readingorder.interactor.GetReadingOrderEdges
import tachiyomi.domain.readingorder.interactor.AutoRemoveCompletedReadingOrderEntries
import tachiyomi.domain.readingorder.interactor.GetReadingOrderProgress
import tachiyomi.domain.readingorder.interactor.RemoveReadingOrderNode
import tachiyomi.domain.readingorder.interactor.DeleteReadingOrder
import tachiyomi.domain.readingorder.interactor.UpdateReadingOrder
import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.domain.collection.manga.interactor.GetMangaCustomOrder
import tachiyomi.domain.collection.manga.interactor.GetVisibleMangaCollections
import tachiyomi.domain.collection.manga.interactor.SetMangaCollections
import tachiyomi.domain.collection.manga.interactor.SetMangaCustomOrder
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.history.manga.interactor.GetNextChapters
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.manga.model.sort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetTracksPerManga
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.source.local.entries.manga.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

/**
 * Typealias for the library manga, using the collection as keys, and list of manga as values.
 */
typealias MangaLibraryMap = Map<Collection, List<MangaLibraryItem>>

class MangaLibraryScreenModel(
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getMangaFavorites: GetMangaFavorites = Injekt.get(),
    private val getCollections: GetVisibleMangaCollections = Injekt.get(),
    private val getMangaCustomOrder: GetMangaCustomOrder = Injekt.get(),
    private val getTracksPerManga: GetTracksPerManga = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val setMangaCollections: SetMangaCollections = Injekt.get(),
    private val setMangaCustomOrder: SetMangaCustomOrder = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: MangaCoverCache = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val downloadManager: MangaDownloadManager = Injekt.get(),
    private val downloadCache: MangaDownloadCache = Injekt.get(),
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
) : StateScreenModel<MangaLibraryScreenModel.State>(State()) {

    var activeCollectionIndex: Int by libraryPreferences.lastUsedMangaCollection().asState(
        screenModelScope,
    )

    init {
        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.debounce(SEARCH_DEBOUNCE_MILLIS),
                getLibraryFlow(),
                getTracksPerManga.subscribe(),
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
                        showMangaCount = values[1] as Boolean,
                        showMangaContinueButton = values[2] as Boolean,
                        showLibraryTitle = values[3] as Boolean,
                    )
                }
            }
            .launchIn(screenModelScope)

        combine(
            libraryPreferences.showListAuthor().changes(),
            libraryPreferences.showListStatus().changes(),
        ) { a, b -> Pair(a, b) }
            .onEach { (showAuthor, showStatus) ->
                mutableState.update { state ->
                    state.copy(
                        showListAuthor = showAuthor,
                        showListStatus = showStatus,
                    )
                }
            }
            .launchIn(screenModelScope)

        libraryPreferences.collectionDisplayMode().changes()
            .onEach { mode ->
                mutableState.update { state ->
                    state.copy(collectionDisplayMode = mode)
                }
            }
            .launchIn(screenModelScope)

        screenModelScope.launchIO {
            loadSavedReadingOrderLayers()
        }

        combine(
            getLibraryItemPreferencesFlow(),
            getTrackingFilterFlow(),
        ) { prefs, trackFilter ->
            (
                listOf(
                    prefs.filterDownloaded,
                    prefs.filterUnread,
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
    }

    private suspend fun MangaLibraryMap.applyFilters(
        trackMap: Map<Long, List<MangaTrack>>,
        trackingFilter: Map<Long, TriState>,
    ): MangaLibraryMap {
        val prefs = getLibraryItemPreferencesFlow().first()
        val downloadedOnly = prefs.globalFilterDownloaded
        val skipOutsideReleasePeriod = prefs.skipOutsideReleasePeriod
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else prefs.filterDownloaded
        val filterUnread = prefs.filterUnread
        val filterStarted = prefs.filterStarted
        val filterBookmarked = prefs.filterBookmarked
        val filterCompleted = prefs.filterCompleted
        val filterIntervalCustom = prefs.filterIntervalCustom

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

        val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        val filterFnDownloaded: (MangaLibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) {
                it.libraryManga.manga.isLocal() ||
                    it.downloadCount > 0 ||
                    downloadManager.getDownloadCount(it.libraryManga.manga) > 0
            }
        }

        val filterFnUnread: (MangaLibraryItem) -> Boolean = {
            applyFilter(filterUnread) { it.libraryManga.unreadCount > 0 }
        }

        val filterFnStarted: (MangaLibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryManga.hasStarted }
        }

        val filterFnBookmarked: (MangaLibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryManga.hasBookmarks }
        }

        val filterFnCompleted: (MangaLibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryManga.manga.status.toInt() == SManga.COMPLETED }
        }

        val filterFnIntervalCustom: (MangaLibraryItem) -> Boolean = {
            if (skipOutsideReleasePeriod) {
                applyFilter(filterIntervalCustom) { it.libraryManga.manga.fetchInterval < 0 }
            } else {
                true
            }
        }

        val filterFnTracking: (MangaLibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val mangaTracks = trackMap
                .mapValues { entry -> entry.value.map { it.trackerId } }[item.libraryManga.id]
                .orEmpty()

            val isExcluded = excludedTracks.isNotEmpty() && mangaTracks.fastAny { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || mangaTracks.fastAny { it in includedTracks }

            !isExcluded && isIncluded
        }

        val filterFn: (MangaLibraryItem) -> Boolean = {
            filterFnDownloaded(it) &&
                filterFnUnread(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnTracking(it)
        }

        return mapValues { (_, value) -> value.fastFilter(filterFn) }
    }

    private suspend fun MangaLibraryMap.applySort(
        trackMap: Map<Long, List<MangaTrack>>,
        loggedInTrackerIds: Set<Long>,
    ): MangaLibraryMap {
        val sortAlphabetically: (MangaLibraryItem, MangaLibraryItem) -> Int = { i1, i2 ->
            i1.libraryManga.manga.title.lowercase().compareToWithCollator(i2.libraryManga.manga.title.lowercase())
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            val trackerMap = trackerManager.getAll(loggedInTrackerIds).associateBy { e -> e.id }
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else ->
                        entry.value
                            .mapNotNull { trackerMap[it.trackerId]?.mangaService?.get10PointScore(it) }
                            .average()
                }
            }
        }

        fun MangaLibrarySort.comparator(): Comparator<MangaLibraryItem> = Comparator { i1, i2 ->
            when (this.type) {
                MangaLibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(i1, i2)
                }
                MangaLibrarySort.Type.LastRead -> {
                    i1.libraryManga.lastRead.compareTo(i2.libraryManga.lastRead)
                }
                MangaLibrarySort.Type.LastUpdate -> {
                    i1.libraryManga.manga.lastUpdate.compareTo(i2.libraryManga.manga.lastUpdate)
                }
                MangaLibrarySort.Type.UnreadCount -> when {
                    // Ensure unread content comes first
                    i1.libraryManga.unreadCount == i2.libraryManga.unreadCount -> 0
                    i1.libraryManga.unreadCount == 0L -> if (this.isAscending) 1 else -1
                    i2.libraryManga.unreadCount == 0L -> if (this.isAscending) -1 else 1
                    else -> i1.libraryManga.unreadCount.compareTo(i2.libraryManga.unreadCount)
                }
                MangaLibrarySort.Type.TotalChapters -> {
                    i1.libraryManga.totalChapters.compareTo(i2.libraryManga.totalChapters)
                }
                MangaLibrarySort.Type.LatestChapter -> {
                    i1.libraryManga.latestUpload.compareTo(i2.libraryManga.latestUpload)
                }
                MangaLibrarySort.Type.ChapterFetchDate -> {
                    i1.libraryManga.chapterFetchedAt.compareTo(i2.libraryManga.chapterFetchedAt)
                }
                MangaLibrarySort.Type.DateAdded -> {
                    i1.libraryManga.manga.dateAdded.compareTo(i2.libraryManga.manga.dateAdded)
                }
                MangaLibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[i1.libraryManga.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[i2.libraryManga.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
                MangaLibrarySort.Type.Random -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
                MangaLibrarySort.Type.CustomOrder -> {
                    error("CustomOrder is handled separately")
                }
                MangaLibrarySort.Type.ReadingOrder -> {
                    val layer1 = state.value.savedReadingOrderLayers[i1.libraryManga.id] ?: Int.MAX_VALUE
                    val layer2 = state.value.savedReadingOrderLayers[i2.libraryManga.id] ?: Int.MAX_VALUE
                    layer1.compareTo(layer2)
                }
            }
        }

        return mapValues { (key, value) ->
            if (key.sort.type == MangaLibrarySort.Type.Random) {
                return@mapValues value.shuffled(Random(libraryPreferences.randomMangaSortSeed().get()))
            }

            if (key.sort.type == MangaLibrarySort.Type.CustomOrder) {
                val order = getMangaCustomOrder.await(key.id)
                val positionMap = order.withIndex().associate { it.value to it.index }
                val unorderedIndex = order.size
                return@mapValues value.sortedWith(
                    Comparator { i1, i2 ->
                        val pos1 = positionMap[i1.libraryManga.id] ?: unorderedIndex
                        val pos2 = positionMap[i2.libraryManga.id] ?: unorderedIndex
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

    private fun getLibraryItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge().changes(),
            libraryPreferences.unreadBadge().changes(),
            libraryPreferences.localBadge().changes(),
            libraryPreferences.languageBadge().changes(),
            libraryPreferences.autoUpdateItemRestrictions().changes(),

            preferences.downloadedOnly().changes(),
            libraryPreferences.filterDownloadedManga().changes(),
            libraryPreferences.filterUnread().changes(),
            libraryPreferences.filterStartedManga().changes(),
            libraryPreferences.filterBookmarkedManga().changes(),
            libraryPreferences.filterCompletedManga().changes(),
            libraryPreferences.filterIntervalCustom().changes(),
        ) {
            ItemPreferences(
                downloadBadge = it[0] as Boolean,
                unreadBadge = it[1] as Boolean,
                localBadge = it[2] as Boolean,
                languageBadge = it[3] as Boolean,
                skipOutsideReleasePeriod = LibraryPreferences.ENTRY_OUTSIDE_RELEASE_PERIOD in (it[4] as Set<*>),
                globalFilterDownloaded = it[5] as Boolean,
                filterDownloaded = it[6] as TriState,
                filterUnread = it[7] as TriState,
                filterStarted = it[8] as TriState,
                filterBookmarked = it[9] as TriState,
                filterCompleted = it[10] as TriState,
                filterIntervalCustom = it[11] as TriState,
            )
        }
    }

    /**
     * Get the collections and all its manga from the database.
     */
    private fun getLibraryFlow(): Flow<MangaLibraryMap> {
        val libraryMangasFlow = combine(
            getLibraryManga.subscribe(),
            getLibraryItemPreferencesFlow(),
            downloadCache.changes,
        ) { libraryMangaList, prefs, _ ->
            libraryMangaList
                .map { libraryManga ->
                    MangaLibraryItem(
                        libraryManga,
                        downloadCount = if (prefs.downloadBadge) {
                            downloadManager.getDownloadCount(libraryManga.manga).toLong()
                        } else {
                            0
                        },
                        unreadCount = if (prefs.unreadBadge) libraryManga.unreadCount else 0,
                        isLocal = if (prefs.localBadge) libraryManga.manga.isLocal() else false,
                        sourceLanguage = if (prefs.languageBadge) {
                            sourceManager.getOrStub(libraryManga.manga.source).lang
                        } else {
                            ""
                        },
                        pinned = false,
                    )
                }
                .groupBy { it.libraryManga.collection }
        }

        return combine(
            getCollections.subscribe(),
            libraryMangasFlow,
            libraryPreferences.groupLibraryBy().changes(),
            getTracksPerManga.subscribe(),
            trackerManager.loggedInTrackersFlow(),
        ) { collections, libraryManga, groupMode, tracks, loggedInTrackers ->
            if (groupMode == LibraryGroupMode.BY_DEFAULT) {
                // Original category-based grouping
                val displayCollections = if (libraryManga.isNotEmpty() && !libraryManga.containsKey(0)) {
                    collections.fastFilterNot { it.isSystemCollection }
                } else {
                    collections
                }
                displayCollections.associateWith { libraryManga[it.id].orEmpty() }
            } else {
                // Regroup by selected criterion
                val allItems = libraryManga.values.flatten()
                groupLibraryItemsByMode(allItems, groupMode, tracks, loggedInTrackers)
            }
        }
    }

    /**
     * Groups library items by the selected group mode, creating synthetic Collection objects.
     */
    private fun groupLibraryItemsByMode(
        items: List<MangaLibraryItem>,
        groupMode: Int,
        tracks: Map<Long, List<MangaTrack>>,
        loggedInTrackers: List<eu.kanade.tachiyomi.data.track.Tracker>,
    ): MangaLibraryMap {
        val context = preferences.context
        val unknown = context.stringResource(MR.strings.unknown)

        val groupNames: Map<String, List<MangaLibraryItem>> = when (groupMode) {
            LibraryGroupMode.UNGROUPED -> {
                mapOf(ungroupedName to items)
            }
            LibraryGroupMode.BY_TAG -> {
                items.flatMap { item ->
                    val tags = item.libraryManga.manga.genre
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
                    val source = sourceManager.getOrStub(item.libraryManga.manga.source)
                    source.name
                }
            }
            LibraryGroupMode.BY_STATUS -> {
                items.groupBy { item ->
                    statusToString(item.libraryManga.manga.status, context)
                }
            }
            LibraryGroupMode.BY_TRACK_STATUS -> {
                items.groupBy { item ->
                    val mangaTracks = tracks[item.libraryManga.manga.id].orEmpty()
                    val track = mangaTracks.find { track ->
                        loggedInTrackers.any { it.id == track.trackerId }
                    }
                    val service = loggedInTrackers.find { it.id == track?.trackerId }
                    if (track != null && service != null) {
                        service.mangaService.getStatusForManga(track.status)?.let {
                            context.stringResource(it)
                        } ?: unknown
                    } else {
                        context.stringResource(MR.strings.not_tracked)
                    }
                }
            }
            LibraryGroupMode.BY_AUTHOR -> {
                items.flatMap { item ->
                    val manga = item.libraryManga.manga
                    val authors = listOfNotNull(
                        manga.author?.takeUnless { it.isBlank() },
                        manga.artist?.takeUnless { it.isBlank() },
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
                    val lang = sourceManager.getOrStub(item.libraryManga.manga.source).lang
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
            SManga.ONGOING -> MR.strings.ongoing
            SManga.COMPLETED -> MR.strings.completed
            SManga.LICENSED -> MR.strings.licensed
            SManga.PUBLISHING_FINISHED -> MR.strings.publishing_finished
            SManga.CANCELLED -> MR.strings.cancelled
            SManga.ON_HIATUS -> MR.strings.on_hiatus
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
                libraryPreferences.filterTrackedManga(tracker.id.toInt()).changes()
            }
            combine(prefFlows) {
                loggedInTrackers
                    .mapIndexed { index, tracker -> tracker.id to it[index] }
                    .toMap()
            }
        }
    }

    /**
     * Returns the common collections for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getCommonCollections(mangas: List<Manga>): Set<Collection> {
        if (mangas.isEmpty()) return emptySet()
        return mangas
            .map { getCollections.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    suspend fun getNextUnreadChapter(manga: Manga): Chapter? {
        return getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true).getNextUnread(manga, downloadManager)
    }

    /**
     * Returns the mix (non-common) collections for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getMixCollections(mangas: List<Manga>): Set<Collection> {
        if (mangas.isEmpty()) return emptySet()
        val mangaCollections = mangas.map { getCollections.await(it.id).toSet() }
        val common = mangaCollections.reduce { set1, set2 -> set1.intersect(set2) }
        return mangaCollections.flatten().distinct().subtract(common)
    }

    fun runDownloadActionSelection(action: DownloadAction) {
        val selection = state.value.selection
        val mangas = selection.map { it.manga }.toList()
        when (action) {
            DownloadAction.NEXT_1_ITEM -> downloadUnreadChapters(mangas, 1)
            DownloadAction.NEXT_5_ITEMS -> downloadUnreadChapters(mangas, 5)
            DownloadAction.NEXT_10_ITEMS -> downloadUnreadChapters(mangas, 10)
            DownloadAction.NEXT_25_ITEMS -> downloadUnreadChapters(mangas, 25)
            DownloadAction.UNVIEWED_ITEMS -> downloadUnreadChapters(mangas, null)
        }
        clearSelection()
    }

    /**
     * Queues the amount specified of unread chapters from the list of mangas given.
     *
     * @param mangas the list of manga.
     * @param amount the amount to queue or null to queue all
     */
    private fun downloadUnreadChapters(mangas: List<Manga>, amount: Int?) {
        screenModelScope.launchNonCancellable {
            mangas.forEach { manga ->
                val chapters = getNextChapters.await(manga.id)
                    .fastFilterNot { chapter ->
                        downloadManager.getQueuedDownloadOrNull(chapter.id) != null ||
                            downloadManager.isChapterDownloaded(
                                chapter.name,
                                chapter.scanlator,
                                manga.title,
                                manga.source,
                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadChapters(manga, chapters)
            }
        }
    }

    /**
     * Marks mangas' chapters read status.
     */
    fun markReadSelection(read: Boolean) {
        val mangas = state.value.selection.toList()
        screenModelScope.launchNonCancellable {
            mangas.forEach { manga ->
                setReadStatus.await(
                    manga = manga.manga,
                    read = read,
                )
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected manga.
     *
     * @param mangaList the list of manga to delete.
     * @param deleteFromLibrary whether to delete manga from library.
     * @param deleteChapters whether to delete downloaded chapters.
     */
    fun removeMangas(mangaList: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        screenModelScope.launchNonCancellable {
            val mangaToDelete = mangaList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = mangaToDelete.map {
                    it.removeCovers(coverCache)
                    MangaUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateManga.awaitAll(toDelete)
            }

            if (deleteChapters) {
                mangaToDelete.forEach { manga ->
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        downloadManager.deleteManga(manga, source)
                    }
                }
            }
        }
    }

    /**
     * Bulk update collections of manga using old and new common collections.
     *
     * @param mangaList the list of manga to move.
     * @param addCollections the collections to add for all mangas.
     * @param removeCollections the collections to remove in all mangas.
     */
    fun setMangaCollections(
        mangaList: List<Manga>,
        addCollections: List<Long>,
        removeCollections: List<Long>,
    ) {
        screenModelScope.launchNonCancellable {
            mangaList.forEach { manga ->
                val collectionIds = getCollections.await(manga.id)
                    .map { it.id }
                    .subtract(removeCollections.toSet())
                    .plus(addCollections)
                    .toList()

                setMangaCollections.await(manga.id, collectionIds)
            }
        }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode().asState(screenModelScope)
    }

    fun getColumnsPreferenceForCurrentOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (
            if (isLandscape) {
                libraryPreferences.mangaLandscapeColumns()
            } else {
                libraryPreferences.mangaPortraitColumns()
            }
            ).asState(
            screenModelScope,
        )
    }

    suspend fun getRandomLibraryItemForCurrentCollection(): MangaLibraryItem? {
        if (state.value.collections.isEmpty()) return null

        return withIOContext {
            state.value
                .getLibraryItemsByCollectionId(state.value.collections[activeCollectionIndex].id)
                ?.randomOrNull()
        }
    }

    suspend fun updateCustomOrder(collectionId: Long, mangaIds: List<Long>) {
        withIOContext { setMangaCustomOrder.await(collectionId, mangaIds) }
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

    fun toggleSelection(manga: LibraryManga) {
        val state = this.state.value
        if (!state.readingOrderMode) {
            mutableState.update { s ->
                val newSelection = s.selection.mutate { list ->
                    if (list.fastAny { it.id == manga.id }) {
                        list.removeAll { it.id == manga.id }
                    } else {
                        list.add(manga)
                    }
                }
                s.copy(selection = newSelection)
            }
            return
        }
        val isRemoving = state.selection.fastAny { it.id == manga.id }
        if (isRemoving && state.editingReadingOrderId != null) {
            val isInOrder = state.readingOrderLayers.flatten().fastAny { it.id == manga.id } ||
                state.selection.fastAny { it.id == manga.id }
            if (isInOrder) {
                mutableState.update { it.copy(dialog = Dialog.ReadingOrderRemoveConfirm(manga)) }
                return
            }
        }
        val existingDepth = state.readingOrderLayers.indexOfFirst { layer ->
            layer.fastAny { it.id == manga.id }
        }
        if (existingDepth >= 0 && existingDepth != state.readingOrderCurrentLayer) {
            mutableState.update {
                it.copy(dialog = Dialog.ReadingOrderMoveDepth(manga, existingDepth + 1, state.readingOrderCurrentLayer + 1))
            }
            return
        }
        mutableState.update { s ->
            val newSelection = s.selection.mutate { list ->
                if (list.fastAny { it.id == manga.id }) {
                    list.removeAll { it.id == manga.id }
                } else {
                    list.add(manga)
                }
            }
            s.copy(selection = newSelection)
        }
    }

    fun confirmMoveEntryDepth(manga: LibraryManga) {
        mutableState.update { s ->
            val newLayers = s.readingOrderLayers.mapIndexed { index, layer ->
                if (index == s.readingOrderCurrentLayer) {
                    layer.mutate { l ->
                        if (!l.fastAny { it.id == manga.id }) l.add(manga)
                    }
                } else {
                    layer.mutate { l -> l.removeAll { it.id == manga.id } }
                }
            }.toPersistentList()
            val compactedLayers = compactLayers(newLayers)
            s.copy(readingOrderLayers = compactedLayers, dialog = null)
        }
    }

    private fun compactLayers(layers: PersistentList<PersistentList<LibraryManga>>): PersistentList<PersistentList<LibraryManga>> {
        return layers.filter { it.isNotEmpty() }.toPersistentList()
    }

    fun confirmRemoveFromReadingOrder(manga: LibraryManga) {
        val state = this.state.value
        val orderId = state.editingReadingOrderId ?: return
        screenModelScope.launchNonCancellable {
            withIOContext {
                removeReadingOrderNode.await(orderId, manga.manga.id)
            }
            withUIContext {
                mutableState.update { s ->
                    val newSelection = s.selection.mutate { list ->
                        list.removeAll { it.id == manga.id }
                    }
                    val newLayers = s.readingOrderLayers.map { layer ->
                        layer.mutate { l -> l.removeAll { it.id == manga.id } }
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
            val orders = getReadingOrders.await("manga")
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
            val manga = getManga.await(node.entryId)
            if (manga != null) {
                entryTitles[node.entryId] = manga.title
                entryUrls[node.entryId] = manga.url
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
                val favorites = getMangaFavorites.await()
                val titleToId = favorites.associateBy { it.title.lowercase() }
                val orderId = createReadingOrder.await(name, null, "manga")
                val origIdToNewId = mutableMapOf<Long, Long>()
                for ((origEntryId, title) in nodes) {
                    val manga = titleToId[title.lowercase()]
                    if (manga != null) {
                        addReadingOrderNode.await(orderId, manga.id)
                        origIdToNewId[origEntryId] = manga.id
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
            val libraryMangaMap = state.value.library.values.flatten().associateBy { it.libraryManga.manga.id }
            val layers = buildLayersFromEdges(nodes, edges, entryIdsInOrder, libraryMangaMap)
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
        libraryMangaMap: Map<Long, MangaLibraryItem>,
    ): PersistentList<PersistentList<LibraryManga>> {
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
        val result = mutableListOf<PersistentList<LibraryManga>>()
        for (i in 0..maxLayer) {
            val layerEntryIds = entryToLayer.entries.filter { it.value == i }.map { it.key }
            val layerManga = layerEntryIds.mapNotNull { id ->
                libraryMangaMap[id]?.libraryManga
            }.toPersistentList()
            result.add(layerManga)
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
                    val allManga = allLayers.flatten().distinctBy { it.id }
                    allManga.forEach { manga ->
                        addReadingOrderNode.await(editingId, manga.manga.id)
                    }
                    for (i in 0 until allLayers.size - 1) {
                        val fromLayer = allLayers[i]
                        val toLayer = allLayers[i + 1]
                        for (from in fromLayer) {
                            for (to in toLayer) {
                                if (from.id != to.id) {
                                    addReadingOrderEdge.await(editingId, from.manga.id, to.manga.id)
                                }
                            }
                        }
                    }
                } else {
                    val orderId = createReadingOrder.await(
                        name = name,
                        description = "",
                        entryKind = "manga",
                    )
                    val allManga = allLayers.flatten().distinctBy { it.id }
                    allManga.forEach { manga ->
                        addReadingOrderNode.await(orderId, manga.manga.id)
                    }
                    for (i in 0 until allLayers.size - 1) {
                        val fromLayer = allLayers[i]
                        val toLayer = allLayers[i + 1]
                        for (from in fromLayer) {
                            for (to in toLayer) {
                                if (from.id != to.id) {
                                    addReadingOrderEdge.await(orderId, from.manga.id, to.manga.id)
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
        val orders = getReadingOrders.await("manga")
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
     * Selects all mangas between and including the given manga and the last pressed manga from the
     * same collection as the given manga
     */
    fun toggleRangeSelection(manga: LibraryManga) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelected?.collection != manga.collection) {
                    list.add(manga)
                    return@mutate
                }

                val items = state.getLibraryItemsByCollectionId(manga.collection)
                    ?.fastMap { it.libraryManga }.orEmpty()
                val lastMangaIndex = items.indexOf(lastSelected)
                val curMangaIndex = items.indexOf(manga)

                val selectedIds = list.fastMap { it.id }
                val selectionRange = when {
                    lastMangaIndex < curMangaIndex -> IntRange(lastMangaIndex, curMangaIndex)
                    curMangaIndex < lastMangaIndex -> IntRange(curMangaIndex, lastMangaIndex)
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
                state.getLibraryItemsByCollectionId(collectionId)
                    ?.fastMapNotNull { item ->
                        item.libraryManga.takeUnless { it.id in selectedIds }
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
                val items = state.getLibraryItemsByCollectionId(collectionId)?.fastMap { it.libraryManga }.orEmpty()
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
            // Create a copy of selected manga
            val mangaList = state.value.selection.map { it.manga }

            // Hide the default collection because it has a different behavior than the ones from db.
            val collections = state.value.collections.filter { it.id != 0L }

            // Get indexes of the common collections to preselect.
            val common = getCommonCollections(mangaList)
            // Get indexes of the mix collections to preselect.
            val mix = getMixCollections(mangaList)
            val preselected = collections
                .map {
                    when (it) {
                        in common -> CheckboxState.State.Checked(it)
                        in mix -> CheckboxState.TriState.Exclude(it)
                        else -> CheckboxState.State.None(it)
                    }
                }
                .toImmutableList()
            mutableState.update { it.copy(dialog = Dialog.ChangeCollection(mangaList, preselected)) }
        }
    }

    fun openDeleteMangaDialog() {
        val mangaList = state.value.selection.map { it.manga }
        mutableState.update { it.copy(dialog = Dialog.DeleteManga(mangaList)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCollection(
            val manga: List<Manga>,
            val initialSelection: ImmutableList<CheckboxState<Collection>>,
        ) : Dialog
        data class DeleteManga(val manga: List<Manga>) : Dialog
        data class ReadingOrderPicker(val orders: List<ReadingOrder>) : Dialog
        data class ReadingOrderRemoveConfirm(val manga: LibraryManga) : Dialog
        data class ReadingOrderMoveDepth(val manga: LibraryManga, val fromDepth: Int, val toDepth: Int) : Dialog
    }

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val unreadBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,
        val skipOutsideReleasePeriod: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnread: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterCompleted: TriState,
        val filterIntervalCustom: TriState,
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val library: MangaLibraryMap = emptyMap(),
        val searchQuery: String? = null,
        val selection: PersistentList<LibraryManga> = persistentListOf(),
        val hasActiveFilters: Boolean = false,
        val showCollectionTabs: Boolean = false,
        val showMangaCount: Boolean = false,
        val showMangaContinueButton: Boolean = false,
        val showLibraryTitle: Boolean = true,
        val showListAuthor: Boolean = false,
        val showListStatus: Boolean = false,
        val collectionDisplayMode: tachiyomi.domain.library.model.LibraryCollectionDisplay =
            tachiyomi.domain.library.model.LibraryCollectionDisplay.TABBED,
        val dialog: Dialog? = null,
        val readingOrderMode: Boolean = false,
        val readingOrderLayers: PersistentList<PersistentList<LibraryManga>> = persistentListOf(),
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
                .fastDistinctBy { it.libraryManga.manga.id }
                .size
        }

        val isLibraryEmpty by lazy { libraryCount == 0 }

        val selectionMode = selection.isNotEmpty() || readingOrderMode

        fun getReadingOrderLayer(mangaId: Long): Int? {
            if (readingOrderMode) {
                readingOrderLayers.forEachIndexed { index, layer ->
                    if (layer.fastAny { it.id == mangaId }) return index + 1
                }
                if (selection.fastAny { it.id == mangaId }) return readingOrderCurrentLayer + 1
                return null
            }
            return savedReadingOrderLayers[mangaId]
        }

        val collections = library.keys.toList()

        fun getLibraryItemsByCollectionId(collectionId: Long): List<MangaLibraryItem>? {
            return library.firstNotNullOfOrNull { (k, v) -> v.takeIf { k.id == collectionId } }
        }

        fun getLibraryItemsByPage(page: Int): List<MangaLibraryItem> {
            return library.values.toTypedArray().getOrNull(page).orEmpty()
        }

        fun getMangaCountForCollection(collection: Collection): Int? {
            return if (showMangaCount || !searchQuery.isNullOrEmpty()) library[collection]?.size else null
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
                !showMangaCount -> null
                else -> getMangaCountForCollection(collection)
            }

            return LibraryToolbarTitle(defaultTitle, subtitle, count)
        }
    }
}
