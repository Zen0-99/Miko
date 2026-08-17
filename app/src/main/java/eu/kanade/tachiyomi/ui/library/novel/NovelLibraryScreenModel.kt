package eu.kanade.tachiyomi.ui.library.novel

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
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
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
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.collection.novel.interactor.GetNovelCustomOrder
import tachiyomi.domain.collection.novel.interactor.GetVisibleNovelCollections
import tachiyomi.domain.collection.novel.interactor.SetNovelCollections
import tachiyomi.domain.collection.novel.interactor.SetNovelCustomOrder
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.GetNovelFavorites
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroupMode
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.novel.model.NovelLibrarySort
import tachiyomi.domain.library.novel.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
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
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.track.novel.interactor.GetTracksPerNovel
import tachiyomi.domain.track.novel.model.NovelTrack
import eu.kanade.domain.items.chapter.interactor.SetNovelReadStatus
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

typealias NovelLibraryMap = Map<Collection, List<NovelLibraryItem>>

class NovelLibraryScreenModel(
    private val getLibraryNovels: GetLibraryNovels = Injekt.get(),
    private val getCollections: GetVisibleNovelCollections = Injekt.get(),
    private val getNovelCustomOrder: GetNovelCustomOrder = Injekt.get(),
    private val setNovelCollections: SetNovelCollections = Injekt.get(),
    private val setNovelCustomOrder: SetNovelCustomOrder = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val downloadManager: NovelDownloadManager = Injekt.get(),
    private val downloadCache: NovelDownloadCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val getNovelWithChapters: GetNovelWithChapters = Injekt.get(),
    private val setReadStatus: SetNovelReadStatus = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val getNovelCollections: GetNovelCollections = Injekt.get(),
    private val getTracksPerNovel: GetTracksPerNovel = Injekt.get(),
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
    private val getNovel: GetNovel = Injekt.get(),
    private val getNovelFavorites: GetNovelFavorites = Injekt.get(),
) : StateScreenModel<NovelLibraryScreenModel.State>(State()) {

    var activeCollectionIndex: Int by libraryPreferences.lastUsedNovelCollection().asState(
        screenModelScope,
    )

    init {
        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.debounce(SEARCH_DEBOUNCE_MILLIS),
                getLibraryFlow(),
                getTracksPerNovel.subscribe(),
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
                        showNovelCount = values[1] as Boolean,
                        showNovelContinueButton = values[2] as Boolean,
                        showLibraryTitle = values[3] as Boolean,
                    )
                }
            }
            .launchIn(screenModelScope)

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

    private suspend fun NovelLibraryMap.applyFilters(
        trackMap: Map<Long, List<NovelTrack>>,
        trackingFilter: Map<Long, TriState>,
    ): NovelLibraryMap {
        val prefs = getLibraryItemPreferencesFlow().first()
        val downloadedOnly = prefs.globalFilterDownloaded
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else prefs.filterDownloaded
        val filterUnread = prefs.filterUnread
        val filterStarted = prefs.filterStarted
        val filterBookmarked = prefs.filterBookmarked
        val filterCompleted = prefs.filterCompleted

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()
        val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        val filterFnDownloaded: (NovelLibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) {
                it.downloadCount > 0 ||
                    downloadManager.getDownloadCount(it.libraryNovel.novel) > 0
            }
        }

        val filterFnUnread: (NovelLibraryItem) -> Boolean = {
            applyFilter(filterUnread) { it.libraryNovel.unreadCount > 0 }
        }

        val filterFnStarted: (NovelLibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryNovel.hasStarted }
        }

        val filterFnBookmarked: (NovelLibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryNovel.hasBookmarks }
        }

        val filterFnCompleted: (NovelLibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryNovel.novel.status == 2L }
        }

        val filterFnTracking: (NovelLibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val novelTracks = trackMap
                .mapValues { entry -> entry.value.map { it.trackerId } }[item.libraryNovel.id]
                .orEmpty()

            val isExcluded = excludedTracks.isNotEmpty() && novelTracks.fastAny { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || novelTracks.fastAny { it in includedTracks }

            !isExcluded && isIncluded
        }

        val filterFn: (NovelLibraryItem) -> Boolean = {
            filterFnDownloaded(it) &&
                filterFnUnread(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnTracking(it)
        }

        return mapValues { (_, value) -> value.fastFilter(filterFn) }
    }

    private suspend fun NovelLibraryMap.applySort(
        trackMap: Map<Long, List<NovelTrack>>,
        loggedInTrackerIds: Set<Long>,
    ): NovelLibraryMap {
        val sortAlphabetically: (NovelLibraryItem, NovelLibraryItem) -> Int = { i1, i2 ->
            i1.libraryNovel.novel.title.lowercase().compareToWithCollator(i2.libraryNovel.novel.title.lowercase())
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            val trackerMap = trackerManager.getAll(loggedInTrackerIds).associateBy { e -> e.id }
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else ->
                        entry.value
                            .filter { trackerMap[it.trackerId] != null }
                            .map { it.score }
                            .average()
                }
            }
        }

        fun NovelLibrarySort.comparator(): Comparator<NovelLibraryItem> = Comparator { i1, i2 ->
            when (this.type) {
                NovelLibrarySort.Type.Alphabetical -> sortAlphabetically(i1, i2)
                NovelLibrarySort.Type.LastRead -> i1.libraryNovel.lastRead.compareTo(i2.libraryNovel.lastRead)
                NovelLibrarySort.Type.LastUpdate -> i1.libraryNovel.novel.lastUpdate.compareTo(i2.libraryNovel.novel.lastUpdate)
                NovelLibrarySort.Type.UnreadCount -> when {
                    i1.libraryNovel.unreadCount == i2.libraryNovel.unreadCount -> 0
                    i1.libraryNovel.unreadCount == 0L -> if (this.isAscending) 1 else -1
                    i2.libraryNovel.unreadCount == 0L -> if (this.isAscending) -1 else 1
                    else -> i1.libraryNovel.unreadCount.compareTo(i2.libraryNovel.unreadCount)
                }
                NovelLibrarySort.Type.TotalChapters -> i1.libraryNovel.totalChapters.compareTo(i2.libraryNovel.totalChapters)
                NovelLibrarySort.Type.LatestChapter -> i1.libraryNovel.latestUpload.compareTo(i2.libraryNovel.latestUpload)
                NovelLibrarySort.Type.ChapterFetchDate -> i1.libraryNovel.chapterFetchedAt.compareTo(i2.libraryNovel.chapterFetchedAt)
                NovelLibrarySort.Type.DateAdded -> i1.libraryNovel.novel.dateAdded.compareTo(i2.libraryNovel.novel.dateAdded)
                NovelLibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[i1.libraryNovel.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[i2.libraryNovel.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
                NovelLibrarySort.Type.Random -> error("Why Are We Still Here? Just To Suffer?")
                NovelLibrarySort.Type.CustomOrder -> error("CustomOrder is handled separately")
                NovelLibrarySort.Type.ReadingOrder -> {
                    val layer1 = state.value.savedReadingOrderLayers[i1.libraryNovel.id] ?: Int.MAX_VALUE
                    val layer2 = state.value.savedReadingOrderLayers[i2.libraryNovel.id] ?: Int.MAX_VALUE
                    layer1.compareTo(layer2)
                }
            }
        }

        return mapValues { (key, value) ->
            if (key.sort.type == NovelLibrarySort.Type.Random) {
                return@mapValues value.shuffled(Random(libraryPreferences.randomNovelSortSeed().get()))
            }

            if (key.sort.type == NovelLibrarySort.Type.CustomOrder) {
                val order = getNovelCustomOrder.await(key.id)
                val positionMap = order.withIndex().associate { it.value to it.index }
                val unorderedIndex = order.size
                return@mapValues value.sortedWith(
                    Comparator { i1, i2 ->
                        val pos1 = positionMap[i1.libraryNovel.id] ?: unorderedIndex
                        val pos2 = positionMap[i2.libraryNovel.id] ?: unorderedIndex
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

            preferences.downloadedOnly().changes(),
            libraryPreferences.filterDownloadedNovel().changes(),
            libraryPreferences.filterUnreadNovel().changes(),
            libraryPreferences.filterStartedNovel().changes(),
            libraryPreferences.filterBookmarkedNovel().changes(),
            libraryPreferences.filterCompletedNovel().changes(),
        ) {
            ItemPreferences(
                downloadBadge = it[0] as Boolean,
                unreadBadge = it[1] as Boolean,
                localBadge = it[2] as Boolean,
                languageBadge = it[3] as Boolean,
                globalFilterDownloaded = it[4] as Boolean,
                filterDownloaded = it[5] as TriState,
                filterUnread = it[6] as TriState,
                filterStarted = it[7] as TriState,
                filterBookmarked = it[8] as TriState,
                filterCompleted = it[9] as TriState,
            )
        }
    }

    private fun getLibraryFlow(): Flow<NovelLibraryMap> {
        val libraryNovelsFlow = combine(
            getLibraryNovels.subscribe(),
            getLibraryItemPreferencesFlow(),
            downloadCache.changes,
        ) { libraryNovelList, prefs, _ ->
            libraryNovelList
                .map { libraryNovel ->
                    NovelLibraryItem(
                        libraryNovel,
                        downloadCount = if (prefs.downloadBadge) {
                            downloadManager.getDownloadCount(libraryNovel.novel).toLong()
                        } else {
                            0
                        },
                        unreadCount = if (prefs.unreadBadge) libraryNovel.unreadCount else 0,
                        isLocal = false,
                        sourceLanguage = if (prefs.languageBadge) {
                            sourceManager.getOrStub(libraryNovel.novel.source).lang
                        } else {
                            ""
                        },
                    )
                }
                .groupBy { it.libraryNovel.collection }
        }

        return combine(
            getCollections.subscribe(),
            libraryNovelsFlow,
            libraryPreferences.groupLibraryBy().changes(),
            getTracksPerNovel.subscribe(),
            trackerManager.loggedInNovelTrackersFlow(),
        ) { collections, libraryNovel, groupMode, tracks, loggedInTrackers ->
            if (groupMode == LibraryGroupMode.BY_DEFAULT) {
                // Original category-based grouping
                val displayCollections = if (libraryNovel.isNotEmpty() && !libraryNovel.containsKey(0)) {
                    collections.fastFilterNot { it.isSystemCollection }
                } else {
                    collections
                }
                displayCollections.associateWith { libraryNovel[it.id].orEmpty() }
            } else {
                // Regroup by selected criterion
                val allItems = libraryNovel.values.flatten()
                groupLibraryItemsByMode(allItems, groupMode, tracks, loggedInTrackers)
            }
        }
    }

    /**
     * Groups library items by the selected group mode, creating synthetic Collection objects.
     */
    private fun groupLibraryItemsByMode(
        items: List<NovelLibraryItem>,
        groupMode: Int,
        tracks: Map<Long, List<NovelTrack>>,
        loggedInTrackers: List<Tracker>,
    ): NovelLibraryMap {
        val context = preferences.context
        val unknown = context.stringResource(MR.strings.unknown)

        val groupNames: Map<String, List<NovelLibraryItem>> = when (groupMode) {
            LibraryGroupMode.UNGROUPED -> {
                mapOf(ungroupedName to items)
            }
            LibraryGroupMode.BY_TAG -> {
                items.flatMap { item ->
                    val tags = item.libraryNovel.novel.genre
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
                    val source = sourceManager.getOrStub(item.libraryNovel.novel.source)
                    source.name
                }
            }
            LibraryGroupMode.BY_STATUS -> {
                items.groupBy { item ->
                    statusToString(item.libraryNovel.novel.status, context)
                }
            }
            LibraryGroupMode.BY_TRACK_STATUS -> {
                items.groupBy { item ->
                    val novelTracks = tracks[item.libraryNovel.novel.id].orEmpty()
                    val track = novelTracks.find { track ->
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
                    val novel = item.libraryNovel.novel
                    val authors = listOfNotNull(
                        novel.author?.takeUnless { it.isBlank() },
                        novel.artist?.takeUnless { it.isBlank() },
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
                    val lang = sourceManager.getOrStub(item.libraryNovel.novel.source).lang
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
            SNovel.ONGOING -> MR.strings.ongoing
            SNovel.COMPLETED -> MR.strings.completed
            SNovel.LICENSED -> MR.strings.licensed
            SNovel.PUBLISHING_FINISHED -> MR.strings.publishing_finished
            SNovel.CANCELLED -> MR.strings.cancelled
            SNovel.ON_HIATUS -> MR.strings.on_hiatus
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
        return trackerManager.loggedInNovelTrackersFlow().flatMapLatest { loggedInTrackers ->
            if (loggedInTrackers.isEmpty()) return@flatMapLatest flowOf(emptyMap())

            val prefFlows = loggedInTrackers.map { tracker ->
                libraryPreferences.filterTrackedNovel(tracker.id.toInt()).changes()
            }
            combine(prefFlows) {
                loggedInTrackers
                    .mapIndexed { index, tracker -> tracker.id to it[index] }
                    .toMap()
            }
        }
    }

    fun runDownloadActionSelection(action: DownloadAction) {
        val selection = state.value.selection
        val novels = selection.map { it.novel }.toList()
        when (action) {
            DownloadAction.NEXT_1_ITEM -> downloadUnreadChapters(novels, 1)
            DownloadAction.NEXT_5_ITEMS -> downloadUnreadChapters(novels, 5)
            DownloadAction.NEXT_10_ITEMS -> downloadUnreadChapters(novels, 10)
            DownloadAction.NEXT_25_ITEMS -> downloadUnreadChapters(novels, 25)
            DownloadAction.UNVIEWED_ITEMS -> downloadUnreadChapters(novels, null)
        }
        clearSelection()
    }

    fun markReadSelection(read: Boolean) {
        val selection = state.value.selection
        screenModelScope.launchNonCancellable {
            selection.forEach { novel ->
                val chapters = getNovelWithChapters.awaitChapters(novel.id)
                setReadStatus.await(read, *chapters.toTypedArray())
            }
        }
        clearSelection()
    }

    suspend fun getNextUnreadChapter(novel: Novel): tachiyomi.domain.items.chapter.model.NovelChapter? {
        val chapters = getNovelWithChapters.awaitChapters(novel.id)
        return chapters
            .sortedBy { it.sourceOrder }
            .firstOrNull { !it.read }
    }

    private fun downloadUnreadChapters(novels: List<Novel>, amount: Int?) {
        screenModelScope.launchNonCancellable {
            novels.forEach { novel ->
                val chapters = getNovelWithChapters.awaitChapters(novel.id)
                // Sort ascending by sourceOrder so we always start from the
                // oldest unread chapter, regardless of the user's display sort.
                // Filter out chapters that are already downloaded or queued.
                val toDownload = chapters
                    .sortedBy { it.sourceOrder }
                    .filter { !it.read }
                    .filterNot { chapter ->
                        downloadManager.getQueuedDownloadOrNull(chapter.id) != null ||
                            downloadManager.isChapterDownloaded(
                                chapter.name,
                                chapter.scanlator,
                                novel.title,
                                novel.source,
                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }
                downloadManager.downloadChapters(novel, toDownload)
            }
        }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode().asState(screenModelScope)
    }

    fun getColumnsPreferenceForCurrentOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (
            if (isLandscape) {
                libraryPreferences.novelLandscapeColumns()
            } else {
                libraryPreferences.novelPortraitColumns()
            }
            ).asState(
            screenModelScope,
        )
    }

    suspend fun getRandomLibraryItemForCurrentCollection(): NovelLibraryItem? {
        if (state.value.collections.isEmpty()) return null
        return withIOContext {
            state.value
                .getLibraryItemsByCollectionId(state.value.collections[activeCollectionIndex].id)
                ?.randomOrNull()
        }
    }

    suspend fun updateCustomOrder(collectionId: Long, novelIds: List<Long>) {
        withIOContext { setNovelCustomOrder.await(collectionId, novelIds) }
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
                    val allNovels = allLayers.flatten().distinctBy { it.id }
                    allNovels.forEach { novel ->
                        addReadingOrderNode.await(editingId, novel.novel.id)
                    }
                    for (i in 0 until allLayers.size - 1) {
                        val fromLayer = allLayers[i]
                        val toLayer = allLayers[i + 1]
                        for (from in fromLayer) {
                            for (to in toLayer) {
                                if (from.id != to.id) {
                                    addReadingOrderEdge.await(editingId, from.novel.id, to.novel.id)
                                }
                            }
                        }
                    }
                } else {
                    val orderId = createReadingOrder.await(
                        name = name,
                        description = "",
                        entryKind = "novel",
                    )
                    val allNovels = allLayers.flatten().distinctBy { it.id }
                    allNovels.forEach { novel ->
                        addReadingOrderNode.await(orderId, novel.novel.id)
                    }
                    for (i in 0 until allLayers.size - 1) {
                        val fromLayer = allLayers[i]
                        val toLayer = allLayers[i + 1]
                        for (from in fromLayer) {
                            for (to in toLayer) {
                                if (from.id != to.id) {
                                    addReadingOrderEdge.await(orderId, from.novel.id, to.novel.id)
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
        val orders = getReadingOrders.await("novel")
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

    fun toggleSelection(novel: LibraryNovel) {
        val state = this.state.value
        if (!state.readingOrderMode) {
            mutableState.update { s ->
                val newSelection = s.selection.mutate { list ->
                    if (list.fastAny { it.id == novel.id }) {
                        list.removeAll { it.id == novel.id }
                    } else {
                        list.add(novel)
                    }
                }
                s.copy(selection = newSelection)
            }
            return
        }
        val isRemoving = state.selection.fastAny { it.id == novel.id }
        if (isRemoving && state.editingReadingOrderId != null) {
            val isInOrder = state.readingOrderLayers.flatten().fastAny { it.id == novel.id } ||
                state.selection.fastAny { it.id == novel.id }
            if (isInOrder) {
                mutableState.update { it.copy(dialog = Dialog.ReadingOrderRemoveConfirm(novel)) }
                return
            }
        }
        val existingDepth = state.readingOrderLayers.indexOfFirst { layer ->
            layer.fastAny { it.id == novel.id }
        }
        if (existingDepth >= 0 && existingDepth != state.readingOrderCurrentLayer) {
            mutableState.update {
                it.copy(dialog = Dialog.ReadingOrderMoveDepth(novel, existingDepth + 1, state.readingOrderCurrentLayer + 1))
            }
            return
        }
        mutableState.update { s ->
            val newSelection = s.selection.mutate { list ->
                if (list.fastAny { it.id == novel.id }) {
                    list.removeAll { it.id == novel.id }
                } else {
                    list.add(novel)
                }
            }
            s.copy(selection = newSelection)
        }
    }

    fun confirmMoveEntryDepth(novel: LibraryNovel) {
        mutableState.update { s ->
            val newLayers = s.readingOrderLayers.mapIndexed { index, layer ->
                if (index == s.readingOrderCurrentLayer) {
                    layer.mutate { l ->
                        if (!l.fastAny { it.id == novel.id }) l.add(novel)
                    }
                } else {
                    layer.mutate { l -> l.removeAll { it.id == novel.id } }
                }
            }.toPersistentList()
            val compactedLayers = compactLayers(newLayers)
            s.copy(readingOrderLayers = compactedLayers, dialog = null)
        }
    }

    private fun compactLayers(layers: PersistentList<PersistentList<LibraryNovel>>): PersistentList<PersistentList<LibraryNovel>> {
        return layers.filter { it.isNotEmpty() }.toPersistentList()
    }

    fun confirmRemoveFromReadingOrder(novel: LibraryNovel) {
        val state = this.state.value
        val orderId = state.editingReadingOrderId ?: return
        screenModelScope.launchNonCancellable {
            withIOContext {
                removeReadingOrderNode.await(orderId, novel.novel.id)
            }
            withUIContext {
                mutableState.update { s ->
                    val newSelection = s.selection.mutate { list ->
                        list.removeAll { it.id == novel.id }
                    }
                    val newLayers = s.readingOrderLayers.map { layer ->
                        layer.mutate { l -> l.removeAll { it.id == novel.id } }
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
            val orders = getReadingOrders.await("novel")
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
            val novel = getNovel.await(node.entryId)
            if (novel != null) {
                entryTitles[node.entryId] = novel.title
                entryUrls[node.entryId] = novel.url
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
                val favorites = getNovelFavorites.await()
                val titleToId = favorites.associateBy { it.title.lowercase() }
                val orderId = createReadingOrder.await(name, null, "novel")
                val origIdToNewId = mutableMapOf<Long, Long>()
                for ((origEntryId, title) in nodes) {
                    val novel = titleToId[title.lowercase()]
                    if (novel != null) {
                        addReadingOrderNode.await(orderId, novel.id)
                        origIdToNewId[origEntryId] = novel.id
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
            val libraryNovelMap = state.value.library.values.flatten().associateBy { it.libraryNovel.novel.id }
            val layers = buildLayersFromEdges(nodes, edges, entryIdsInOrder, libraryNovelMap)
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
        libraryNovelMap: Map<Long, NovelLibraryItem>,
    ): PersistentList<PersistentList<LibraryNovel>> {
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
        val result = mutableListOf<PersistentList<LibraryNovel>>()
        for (i in 0..maxLayer) {
            val layerEntryIds = entryToLayer.entries.filter { it.value == i }.map { it.key }
            val layerNovel = layerEntryIds.mapNotNull { id ->
                libraryNovelMap[id]?.libraryNovel
            }.toPersistentList()
            result.add(layerNovel)
        }
        return result.toPersistentList()
    }

    fun toggleRangeSelection(novel: LibraryNovel) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelected?.collection != novel.collection) {
                    list.add(novel)
                    return@mutate
                }

                val items = state.getLibraryItemsByCollectionId(novel.collection)
                    ?.fastMap { it.libraryNovel }.orEmpty()
                val lastNovelIndex = items.indexOf(lastSelected)
                val curNovelIndex = items.indexOf(novel)

                val selectedIds = list.fastMap { it.id }
                val selectionRange = when {
                    lastNovelIndex < curNovelIndex -> IntRange(lastNovelIndex, curNovelIndex)
                    curNovelIndex < lastNovelIndex -> IntRange(curNovelIndex, lastNovelIndex)
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
                        item.libraryNovel.takeUnless { it.id in selectedIds }
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
                val items = state.getLibraryItemsByCollectionId(collectionId)?.fastMap { it.libraryNovel }.orEmpty()
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
            val novelList = state.value.selection.map { it.novel }
            val collections = state.value.collections.filter { it.id != 0L }
            val preselected = collections
                .map { CheckboxState.State.None(it) }
                .toImmutableList()
            mutableState.update { it.copy(dialog = Dialog.ChangeCollection(novelList, preselected)) }
        }
    }

    fun openDeleteNovelDialog() {
        val novelList = state.value.selection.map { it.novel }
        mutableState.update { it.copy(dialog = Dialog.DeleteNovel(novelList)) }
    }

    fun removeNovels(novelList: List<Novel>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        screenModelScope.launchNonCancellable {
            val novelsToDelete = novelList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = novelsToDelete.map {
                    NovelUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateNovel.awaitAll(toDelete)
            }

            if (deleteChapters) {
                novelsToDelete.forEach { novel ->
                    val source = sourceManager.get(novel.source) as? NovelHttpSource
                    if (source != null) {
                        downloadManager.deleteNovel(novel, source)
                    }
                }
            }
        }
    }

    fun setNovelCollections(
        novelList: List<Novel>,
        addCollections: List<Long>,
        removeCollections: List<Long>,
    ) {
        screenModelScope.launchNonCancellable {
            novelList.forEach { novel ->
                val collectionIds = getNovelCollections.await(novel.id)
                    .map { it.id }
                    .subtract(removeCollections.toSet())
                    .plus(addCollections)
                    .toList()

                setNovelCollections.await(novel.id, collectionIds)
            }
        }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCollection(
            val novels: List<Novel>,
            val initialSelection: ImmutableList<CheckboxState<Collection>>,
        ) : Dialog
        data class DeleteNovel(val novels: List<Novel>) : Dialog
        data class ReadingOrderPicker(val orders: List<ReadingOrder>) : Dialog
        data class ReadingOrderRemoveConfirm(val novel: LibraryNovel) : Dialog
        data class ReadingOrderMoveDepth(val novel: LibraryNovel, val fromDepth: Int, val toDepth: Int) : Dialog
    }

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val unreadBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,
        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnread: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterCompleted: TriState,
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val library: NovelLibraryMap = emptyMap(),
        val searchQuery: String? = null,
        val selection: PersistentList<LibraryNovel> = persistentListOf(),
        val hasActiveFilters: Boolean = false,
        val showCollectionTabs: Boolean = false,
        val showNovelCount: Boolean = false,
        val showNovelContinueButton: Boolean = false,
        val showLibraryTitle: Boolean = true,
        val dialog: Dialog? = null,
        val readingOrderMode: Boolean = false,
        val readingOrderLayers: PersistentList<PersistentList<LibraryNovel>> = persistentListOf(),
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
                .fastDistinctBy { it.libraryNovel.novel.id }
                .size
        }

        val isLibraryEmpty by lazy { libraryCount == 0 }

        val selectionMode = selection.isNotEmpty() || readingOrderMode

        val collections = library.keys.toList()

        /**
         * Returns the 1-indexed reading-order layer number for a novel, or null
         * if the novel is not part of any layer (or RO mode is off).
         */
        fun getReadingOrderLayer(novelId: Long): Int? {
            if (readingOrderMode) {
                readingOrderLayers.forEachIndexed { index, layer ->
                    if (layer.fastAny { it.id == novelId }) return index + 1
                }
                if (selection.fastAny { it.id == novelId }) return readingOrderCurrentLayer + 1
                return null
            }
            return savedReadingOrderLayers[novelId]
        }

        fun getLibraryItemsByCollectionId(collectionId: Long): List<NovelLibraryItem>? {
            return library.firstNotNullOfOrNull { (k, v) -> v.takeIf { k.id == collectionId } }
        }

        fun getLibraryItemsByPage(page: Int): List<NovelLibraryItem> {
            return library.values.toTypedArray().getOrNull(page).orEmpty()
        }

        fun getNovelCountForCollection(collection: Collection): Int? {
            return if (showNovelCount || !searchQuery.isNullOrEmpty()) library[collection]?.size else null
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
                !showNovelCount -> null
                else -> getNovelCountForCollection(collection)
            }

            return LibraryToolbarTitle(defaultTitle, subtitle, count)
        }
    }
}
