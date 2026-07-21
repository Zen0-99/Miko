package eu.kanade.tachiyomi.ui.history.novel

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.util.lang.toLocalDate
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.history.novel.interactor.GetNovelHistory
import tachiyomi.domain.history.novel.interactor.GetNextNovelChapters
import tachiyomi.domain.history.novel.interactor.RemoveNovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.items.chapter.model.NovelChapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelHistoryScreenModel(
    private val getHistory: GetNovelHistory = Injekt.get(),
    private val getNextChapters: GetNextNovelChapters = Injekt.get(),
    private val removeHistory: RemoveNovelHistory = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<NovelHistoryScreenModel.State>(State()) {

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val _query: MutableStateFlow<String?> = MutableStateFlow(null)
    val query: StateFlow<String?> = _query.asStateFlow()

    init {
        screenModelScope.launchIO {
            _query.collectLatest { query ->
                getHistory.subscribe(query ?: "")
                    .distinctUntilChanged()
                    .catch { error ->
                        logcat(LogPriority.ERROR, error)
                        _events.send(Event.InternalError)
                    }
                    .map { it.toHistoryUiModels() }
                    .flowOn(Dispatchers.IO)
                    .collect { newList -> mutableState.update { it.copy(list = newList) } }
            }
        }
    }

    fun search(query: String?) {
        screenModelScope.launchIO {
            _query.emit(query)
        }
    }

    private fun List<NovelHistoryWithRelations>.toHistoryUiModels(): List<NovelHistoryUiModel> {
        // Group consecutive entries from the same novel whose readAt timestamps
        // fall within BATCH_WINDOW_MS. If a group has >= BATCH_MIN_SIZE entries,
        // collapse it into a single NovelHistoryUiModel.Batch; otherwise emit
        // individual items. Date headers are inserted between groups/items that
        // cross a day boundary.
        val BATCH_WINDOW_MS = 60_000L // 1 minute
        val BATCH_MIN_SIZE = 5

        if (isEmpty()) return emptyList()

        // First pass: build groups of consecutive same-novel entries within the window.
        data class Group(
            val novelId: Long,
            val entries: List<NovelHistoryWithRelations>,
        )

        val groups = mutableListOf<Group>()
        var current: MutableList<NovelHistoryWithRelations>? = null
        var currentNovelId: Long? = null
        var windowStart: Long = 0L
        for (entry in this) {
            val t = entry.readAt?.time ?: 0L
            if (current != null && entry.novelId == currentNovelId && t - windowStart <= BATCH_WINDOW_MS) {
                current!!.add(entry)
            } else {
                if (current != null) {
                    groups.add(Group(currentNovelId!!, current!!))
                }
                current = mutableListOf(entry)
                currentNovelId = entry.novelId
                windowStart = t
            }
        }
        if (current != null) groups.add(Group(currentNovelId!!, current!!))

        // Second pass: build the final UI model list with headers.
        val result = mutableListOf<NovelHistoryUiModel>()
        var lastDate: LocalDate? = null
        groups.forEach { group ->
            val entries = group.entries
            val first = entries.first()
            val last = entries.last()
            val firstDate = first.readAt?.time?.toLocalDate()
            if (firstDate != null && firstDate != lastDate) {
                result.add(NovelHistoryUiModel.Header(firstDate))
                lastDate = firstDate
            }
            if (entries.size >= BATCH_MIN_SIZE) {
                result.add(
                    NovelHistoryUiModel.Batch(
                        novelId = group.novelId,
                        title = first.title,
                        firstChapter = first.chapterNumber,
                        lastChapter = last.chapterNumber,
                        firstReadAt = first.readAt ?: java.util.Date(0),
                        lastReadAt = last.readAt ?: java.util.Date(0),
                        coverData = first.coverData,
                        historyIds = entries.map { it.id },
                    ),
                )
            } else {
                entries.forEach { result.add(NovelHistoryUiModel.Item(it)) }
            }
        }
        return result
    }

    suspend fun getNextChapter(): NovelChapter? {
        return withIOContext { getNextChapters.await(onlyUnread = false).firstOrNull() }
    }

    fun getNextChapterForNovel(novelId: Long, chapterId: Long) {
        screenModelScope.launchIO {
            sendNextChapterEvent(getNextChapters.await(novelId, chapterId, onlyUnread = false))
        }
    }

    private suspend fun sendNextChapterEvent(chapters: List<NovelChapter>) {
        val chapter = chapters.firstOrNull()
        _events.send(Event.OpenChapter(chapter))
    }

    fun removeFromHistory(history: NovelHistoryWithRelations) {
        screenModelScope.launchIO {
            removeHistory.await(history)
        }
    }

    fun removeAllFromHistory(novelId: Long) {
        screenModelScope.launchIO {
            removeHistory.await(novelId)
        }
    }

    fun removeAllHistory() {
        screenModelScope.launchIO {
            val result = removeHistory.awaitAll()
            if (!result) return@launchIO
            _events.send(Event.HistoryCleared)
        }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val list: List<NovelHistoryUiModel>? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object DeleteAll : Dialog
        data class Delete(val history: NovelHistoryWithRelations) : Dialog
    }

    sealed interface Event {
        data class OpenChapter(val chapter: NovelChapter?) : Event
        data object InternalError : Event
        data object HistoryCleared : Event
    }
}
