package eu.kanade.tachiyomi.ui.updates.novel

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.items.chapter.interactor.SetNovelReadStatus
import eu.kanade.presentation.updates.novel.NovelUpdatesUiModel
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.items.chapter.interactor.GetNovelChapter
import tachiyomi.domain.items.chapter.interactor.UpdateNovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapterUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.updates.novel.interactor.GetNovelUpdates
import tachiyomi.domain.updates.novel.model.NovelUpdatesWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Clock

class NovelUpdatesScreenModel(
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val downloadManager: NovelDownloadManager = Injekt.get(),
    private val downloadCache: NovelDownloadCache = Injekt.get(),
    private val updateChapter: UpdateNovelChapter = Injekt.get(),
    private val setReadStatus: SetNovelReadStatus = Injekt.get(),
    private val getUpdates: GetNovelUpdates = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val getChapter: GetNovelChapter = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<NovelUpdatesScreenModel.State>(State()) {

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events: Flow<Event> = _events.receiveAsFlow()

    val lastUpdated by libraryPreferences.lastUpdatedTimestamp().asState(screenModelScope)

    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    private val selectedChapterIds: HashSet<Long> = HashSet()

    init {
        screenModelScope.launchIO {
            val limit = Clock.System.now().minus(3, DateTimeUnit.MONTH, TimeZone.currentSystemDefault())

            combine(
                getUpdates.subscribe(limit).distinctUntilChanged(),
                downloadCache.changes,
                downloadManager.queueState,
            ) { updates, _, _ -> updates }
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(Event.InternalError)
                }
                .collectLatest { updates ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            items = updates.toUpdateItems(),
                        )
                    }
                }
        }

        screenModelScope.launchIO {
            merge(downloadManager.statusFlow(), downloadManager.progressFlow())
                .catch { logcat(LogPriority.ERROR, it) }
                .collect(this@NovelUpdatesScreenModel::updateDownloadState)
        }
    }

    private fun List<NovelUpdatesWithRelations>.toUpdateItems(): PersistentList<NovelUpdatesItem> {
        return this
            .map { update ->
                val activeDownload = downloadManager.getQueuedDownloadOrNull(update.chapterId)
                val downloaded = downloadManager.isChapterDownloaded(
                    update.chapterName,
                    update.scanlator,
                    update.novelTitle,
                    update.sourceId,
                )
                val downloadState = when {
                    activeDownload != null -> activeDownload.status
                    downloaded -> NovelDownload.State.DOWNLOADED
                    else -> NovelDownload.State.NOT_DOWNLOADED
                }
                NovelUpdatesItem(
                    update = update,
                    downloadStateProvider = { downloadState },
                    downloadProgressProvider = { activeDownload?.progress ?: 0 },
                    selected = update.chapterId in selectedChapterIds,
                )
            }
            .toPersistentList()
    }

    fun updateLibrary(): Boolean {
        val started = NovelLibraryUpdateJob.startNow(Injekt.get<Application>())
        screenModelScope.launch {
            _events.send(Event.LibraryUpdateTriggered(started))
        }
        return started
    }

    private fun updateDownloadState(download: NovelDownload) {
        mutableState.update { state ->
            val newItems = state.items.mutate { list ->
                val modifiedIndex = list.indexOfFirst { it.update.chapterId == download.chapter.id }
                if (modifiedIndex < 0) return@mutate

                val item = list[modifiedIndex]
                list[modifiedIndex] = item.copy(
                    downloadStateProvider = { download.status },
                    downloadProgressProvider = { download.progress },
                )
            }
            state.copy(items = newItems)
        }
    }

    fun downloadChapters(items: List<NovelUpdatesItem>) {
        if (items.isEmpty()) return
        screenModelScope.launch {
            downloadChaptersInternal(items)
            if (items.any { it.downloadStateProvider() == NovelDownload.State.ERROR }) {
                downloadManager.startDownloads()
            }
            toggleAllSelection(false)
        }
    }

    private fun startDownloadingNow(chapterId: Long) {
        downloadManager.startDownloadNow(chapterId)
    }

    private fun cancelDownload(chapterId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(chapterId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = NovelDownload.State.NOT_DOWNLOADED })
    }

    fun markUpdatesRead(updates: List<NovelUpdatesItem>, read: Boolean) {
        screenModelScope.launchIO {
            setReadStatus.await(
                read = read,
                chapters = updates
                    .mapNotNull { getChapter.await(it.update.chapterId) }
                    .toTypedArray(),
            )
        }
        toggleAllSelection(false)
    }

    fun bookmarkUpdates(updates: List<NovelUpdatesItem>, bookmark: Boolean) {
        screenModelScope.launchIO {
            updates
                .filterNot { it.update.bookmark == bookmark }
                .map { NovelChapterUpdate(id = it.update.chapterId, bookmark = bookmark) }
                .let { updateChapter.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    private fun downloadChaptersInternal(updatesItem: List<NovelUpdatesItem>) {
        screenModelScope.launchNonCancellable {
            val groupedUpdates = updatesItem.groupBy { it.update.novelId }.values
            for (updates in groupedUpdates) {
                val novelId = updates.first().update.novelId
                val novel = getNovel.await(novelId) ?: continue
                sourceManager.get(novel.source) ?: continue
                val chapters = updates.mapNotNull { getChapter.await(it.update.chapterId) }
                downloadManager.downloadChapters(novel, chapters)
            }
        }
    }

    fun showConfirmDeleteChapters(updatesItem: List<NovelUpdatesItem>) {
        setDialog(Dialog.DeleteConfirmation(updatesItem))
    }

    fun deleteChapters(updatesItem: List<NovelUpdatesItem>) {
        screenModelScope.launchNonCancellable {
            updatesItem
                .groupBy { it.update.novelId }
                .entries
                .forEach { (novelId, updates) ->
                    val novel = getNovel.await(novelId) ?: return@forEach
                    val source = sourceManager.get(novel.source) ?: return@forEach
                    val chapters = updates.mapNotNull { getChapter.await(it.update.chapterId) }
                    downloadManager.deleteChapters(chapters, novel, source)
                }
        }
        toggleAllSelection(false)
    }

    fun toggleSelection(
        item: NovelUpdatesItem,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        mutableState.update { state ->
            val newItems = state.items.toMutableList().apply {
                val selectedIndex = indexOfFirst { it.update.chapterId == item.update.chapterId }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if (selectedItem.selected == selected) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedChapterIds.addOrRemove(item.update.chapterId, selected)

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1..<selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1)..<selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inbetweenItem = get(it)
                            if (!inbetweenItem.selected) {
                                selectedChapterIds.add(inbetweenItem.update.chapterId)
                                set(it, inbetweenItem.copy(selected = true))
                            }
                        }
                    }
                } else if (userSelected && !fromLongPress) {
                    if (!selected) {
                        if (selectedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (selectedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (selectedIndex < selectedPositions[0]) {
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            selectedPositions[1] = selectedIndex
                        }
                    }
                }
            }
            state.copy(items = newItems.toPersistentList())
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        mutableState.update { state ->
            val newItems = state.items.map {
                selectedChapterIds.addOrRemove(it.update.chapterId, selected)
                it.copy(selected = selected)
            }
            state.copy(items = newItems.toPersistentList())
        }

        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun invertSelection() {
        mutableState.update { state ->
            val newItems = state.items.map {
                selectedChapterIds.addOrRemove(it.update.chapterId, !it.selected)
                it.copy(selected = !it.selected)
            }
            state.copy(items = newItems.toPersistentList())
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun resetNewUpdatesCount() {
        libraryPreferences.newNovelUpdatesCount().set(0)
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: PersistentList<NovelUpdatesItem> = persistentListOf(),
        val dialog: Dialog? = null,
    ) {
        val selected = items.filter { it.selected }
        val selectionMode = selected.isNotEmpty()

        fun getUiModel(): List<NovelUpdatesUiModel> {
            return items
                .map { NovelUpdatesUiModel.Item(it) }
                .insertSeparators { before, after ->
                    val beforeDate = before?.item?.update?.dateFetch?.toLocalDate()
                    val afterDate = after?.item?.update?.dateFetch?.toLocalDate()
                    when {
                        beforeDate != afterDate && afterDate != null -> NovelUpdatesUiModel.Header(afterDate)
                        else -> null
                    }
                }
        }
    }

    sealed interface Dialog {
        data class DeleteConfirmation(val toDelete: List<NovelUpdatesItem>) : Dialog
    }

    sealed interface Event {
        data object InternalError : Event
        data class LibraryUpdateTriggered(val started: Boolean) : Event
    }
}

@Immutable
data class NovelUpdatesItem(
    val update: NovelUpdatesWithRelations,
    val downloadStateProvider: () -> NovelDownload.State,
    val downloadProgressProvider: () -> Int,
    val selected: Boolean = false,
)
