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
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.GetVisibleNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.novel.model.NovelLibrarySort
import tachiyomi.domain.library.novel.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import eu.kanade.domain.items.chapter.interactor.SetNovelReadStatus
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

typealias NovelLibraryMap = Map<Category, List<NovelLibraryItem>>

class NovelLibraryScreenModel(
    private val getLibraryNovels: GetLibraryNovels = Injekt.get(),
    private val getCategories: GetVisibleNovelCategories = Injekt.get(),
    private val setNovelCategories: SetNovelCategories = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val downloadManager: NovelDownloadManager = Injekt.get(),
    private val downloadCache: NovelDownloadCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val getNovelWithChapters: GetNovelWithChapters = Injekt.get(),
    private val setReadStatus: SetNovelReadStatus = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
) : StateScreenModel<NovelLibraryScreenModel.State>(State()) {

    var activeCategoryIndex: Int by libraryPreferences.lastUsedNovelCategory().asState(
        screenModelScope,
    )

    init {
        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.debounce(SEARCH_DEBOUNCE_MILLIS),
                getLibraryFlow(),
                getTrackingFilterFlow(),
                downloadCache.changes,
            ) { searchQuery, library, trackingFilter, _ ->
                library
                    .applyFilters(trackingFilter)
                    .applySort(trackingFilter.keys)
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
            libraryPreferences.categoryTabs().changes(),
            libraryPreferences.categoryNumberOfItems().changes(),
            libraryPreferences.showContinueViewingButton().changes(),
        ) { a, b, c -> arrayOf(a, b, c) }
            .onEach { (showCategoryTabs, showNovelCount, showNovelContinueButton) ->
                mutableState.update { state ->
                    state.copy(
                        showCategoryTabs = showCategoryTabs,
                        showNovelCount = showNovelCount,
                        showNovelContinueButton = showNovelContinueButton,
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
    }

    private suspend fun NovelLibraryMap.applyFilters(
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
            true // TODO: Wire tracking when novel trackers exist
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

    private fun NovelLibraryMap.applySort(
        loggedInTrackerIds: Set<Long>,
    ): NovelLibraryMap {
        val sortAlphabetically: (NovelLibraryItem, NovelLibraryItem) -> Int = { i1, i2 ->
            i1.libraryNovel.novel.title.lowercase().compareToWithCollator(i2.libraryNovel.novel.title.lowercase())
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
                NovelLibrarySort.Type.TrackerMean -> 0
                NovelLibrarySort.Type.Random -> error("Why Are We Still Here? Just To Suffer?")
            }
        }

        return mapValues { (key, value) ->
            if (key.sort.type == NovelLibrarySort.Type.Random) {
                return@mapValues value.shuffled(Random(libraryPreferences.randomNovelSortSeed().get()))
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
                .groupBy { it.libraryNovel.category }
        }

        return combine(getCategories.subscribe(), libraryNovelsFlow) { categories, libraryNovel ->
            val displayCategories = if (libraryNovel.isNotEmpty() && !libraryNovel.containsKey(0)) {
                categories.fastFilterNot { it.isSystemCategory }
            } else {
                categories
            }

            displayCategories.associateWith { libraryNovel[it.id].orEmpty() }
        }
    }

    private fun getTrackingFilterFlow(): Flow<Map<Long, TriState>> {
        return trackerManager.loggedInTrackersFlow().flatMapLatest { loggedInTrackers ->
            if (loggedInTrackers.isEmpty()) return@flatMapLatest flowOf(emptyMap())
            flowOf(emptyMap())
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

    private fun downloadUnreadChapters(novels: List<Novel>, amount: Int?) {
        screenModelScope.launchNonCancellable {
            novels.forEach { novel ->
                val chapters = getNovelWithChapters.awaitChapters(novel.id)
                val toDownload = if (amount != null) {
                    chapters.filter { !it.read }.take(amount)
                } else {
                    chapters.filter { !it.read }
                }
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

    suspend fun getRandomLibraryItemForCurrentCategory(): NovelLibraryItem? {
        if (state.value.categories.isEmpty()) return null
        return withIOContext {
            state.value
                .getLibraryItemsByCategoryId(state.value.categories[activeCategoryIndex].id)
                ?.randomOrNull()
        }
    }

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = persistentListOf()) }
    }

    fun toggleSelection(novel: LibraryNovel) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                if (list.fastAny { it.id == novel.id }) {
                    list.removeAll { it.id == novel.id }
                } else {
                    list.add(novel)
                }
            }
            state.copy(selection = newSelection)
        }
    }

    fun toggleRangeSelection(novel: LibraryNovel) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelected?.category != novel.category) {
                    list.add(novel)
                    return@mutate
                }

                val items = state.getLibraryItemsByCategoryId(novel.category)
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
                val categoryId = state.categories.getOrNull(index)?.id ?: -1
                val selectedIds = list.fastMap { it.id }
                state.getLibraryItemsByCategoryId(categoryId)
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
                val categoryId = state.categories[index].id
                val items = state.getLibraryItemsByCategoryId(categoryId)?.fastMap { it.libraryNovel }.orEmpty()
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

    fun openChangeCategoryDialog() {
        screenModelScope.launchIO {
            val novelList = state.value.selection.map { it.novel }
            val categories = state.value.categories.filter { it.id != 0L }
            val preselected = categories
                .map { CheckboxState.State.None(it) }
                .toImmutableList()
            mutableState.update { it.copy(dialog = Dialog.ChangeCategory(novelList, preselected)) }
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

    fun setNovelCategories(
        novelList: List<Novel>,
        addCategories: List<Long>,
        removeCategories: List<Long>,
    ) {
        screenModelScope.launchNonCancellable {
            novelList.forEach { novel ->
                val categoryIds = getNovelCategories.await(novel.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                setNovelCategories.await(novel.id, categoryIds)
            }
        }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCategory(
            val novels: List<Novel>,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteNovel(val novels: List<Novel>) : Dialog
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
        val showCategoryTabs: Boolean = false,
        val showNovelCount: Boolean = false,
        val showNovelContinueButton: Boolean = false,
        val dialog: Dialog? = null,
    ) {
        private val libraryCount by lazy {
            library.values
                .flatten()
                .fastDistinctBy { it.libraryNovel.novel.id }
                .size
        }

        val isLibraryEmpty by lazy { libraryCount == 0 }

        val selectionMode = selection.isNotEmpty()

        val categories = library.keys.toList()

        fun getLibraryItemsByCategoryId(categoryId: Long): List<NovelLibraryItem>? {
            return library.firstNotNullOfOrNull { (k, v) -> v.takeIf { k.id == categoryId } }
        }

        fun getLibraryItemsByPage(page: Int): List<NovelLibraryItem> {
            return library.values.toTypedArray().getOrNull(page).orEmpty()
        }

        fun getNovelCountForCategory(category: Category): Int? {
            return if (showNovelCount || !searchQuery.isNullOrEmpty()) library[category]?.size else null
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCategoryTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val category = categories.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val categoryName = category.let {
                if (it.isSystemCategory) defaultCategoryTitle else it.name
            }
            // Title is always "Library"; subtitle is the current category name
            // only shown when there is more than one category.
            val subtitle = if (categories.size > 1) categoryName else null
            val count = when {
                !showNovelCount -> null
                !showCategoryTabs -> getNovelCountForCategory(category)
                else -> libraryCount
            }

            return LibraryToolbarTitle(defaultTitle, subtitle, count)
        }
    }
}
