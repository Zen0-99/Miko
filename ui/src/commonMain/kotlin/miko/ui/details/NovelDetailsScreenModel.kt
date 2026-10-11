package miko.ui.details

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import miko.ui.MikoUiGraph
import miko.ui.model.toDomainNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapterUpdate

/**
 * Entry details: remote novel details + chapter list, local favorite state,
 * local chapter read state, and the continue/mark-all affordances Android's
 * `EntryContinueRow` drives off of.
 */
class NovelDetailsScreenModel(
    private val graph: MikoUiGraph,
    private val sourceId: Long,
    private val novelUrl: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class State(
        val sourceName: String = "",
        val novel: SNovel? = null,
        val local: Novel? = null,
        val chapters: List<SNovelChapter> = emptyList(),
        val readUrls: Set<String> = emptySet(),
        val favorite: Boolean = false,
        val detailsLoading: Boolean = true,
        val chaptersLoading: Boolean = true,
        val error: String? = null,
    ) {
        /** First unread chapter — Android's `nextContinueChapter`. */
        val nextChapter: SNovelChapter?
            get() = chapters.firstOrNull { it.url !in readUrls }

        val allChaptersRead: Boolean
            get() = chapters.isNotEmpty() && chapters.all { it.url in readUrls }

        val hasStarted: Boolean
            get() = readUrls.isNotEmpty()
    }

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var chaptersJob: Job? = null

    private val source: NovelCatalogueSource?
        get() = graph.novelSourceManager.get(sourceId) as? NovelCatalogueSource

    init {
        scope.launch {
            // Favorite/library state streams from the local row — the toggle
            // materializes one via networkToLocalNovel when absent.
            graph.getNovel.subscribe(novelUrl, sourceId).collect { local ->
                _state.value = _state.value.copy(
                    local = local,
                    favorite = local?.favorite == true,
                )
                if (local != null) subscribeLocalChapters(local.id)
            }
        }
        refresh()
    }

    private fun subscribeLocalChapters(novelId: Long) {
        if (chaptersJob != null) return
        chaptersJob = scope.launch {
            graph.novelChapterRepository
                .getNovelChaptersByNovelIdAsFlow(novelId)
                .collect { chapters ->
                    _state.value = _state.value.copy(
                        readUrls = chapters.filter { it.read }
                            .mapTo(HashSet()) { it.url },
                    )
                }
        }
    }

    fun refresh() {
        scope.launch { loadDetails() }
        scope.launch { loadChapters() }
    }

    private suspend fun loadDetails() {
        val src = source ?: run {
            _state.value = _state.value.copy(
                detailsLoading = false,
                error = "Source unavailable",
            )
            return
        }
        _state.value = _state.value.copy(sourceName = src.name)
        val stub = SNovel.create().apply {
            url = novelUrl
            title = _state.value.local?.title ?: novelUrl
        }
        val result = runCatching { src.getNovelDetails(stub) }
        result.fold(
            onSuccess = { novel ->
                _state.value = _state.value.copy(novel = novel, detailsLoading = false)
            },
            onFailure = {
                _state.value = _state.value.copy(
                    detailsLoading = false,
                    error = it.message ?: "Failed to load details",
                )
            },
        )
    }

    private suspend fun loadChapters() {
        val src = source ?: run {
            _state.value = _state.value.copy(chaptersLoading = false)
            return
        }
        val base = _state.value.novel ?: SNovel.create().apply {
            url = novelUrl
            title = novelUrl
        }
        val result = runCatching { src.getChapterList(base) }
        result.fold(
            onSuccess = { chapters ->
                _state.value = _state.value.copy(
                    chapters = chapters,
                    chaptersLoading = false,
                )
                // Keep local rows in step with the remote list — the
                // favorite-time seed misses chapters added afterwards.
                _state.value.local?.let { local ->
                    if (local.favorite) syncLocalChapters(local.id, chapters)
                }
            },
            onFailure = {
                _state.value = _state.value.copy(
                    chaptersLoading = false,
                    error = _state.value.error ?: (it.message ?: "Failed to load chapters"),
                )
            },
        )
    }

    private suspend fun syncLocalChapters(novelId: Long, remote: List<SNovelChapter>) {
        val existing = graph.getNovelChaptersByNovelId.await(novelId)
        val existingUrls = existing.mapTo(HashSet()) { it.url }
        val missing = remote.mapIndexedNotNull { index, ch ->
            if (ch.url in existingUrls) {
                null
            } else {
                NovelChapter.create().copy(
                    novelId = novelId,
                    url = ch.url,
                    name = ch.name,
                    dateUpload = ch.date_upload,
                    chapterNumber = ch.chapter_number.toDouble(),
                    scanlator = ch.scanlator,
                    sourceOrder = index.toLong(),
                )
            }
        }
        if (missing.isNotEmpty()) graph.novelChapterRepository.addAllNovelChapters(missing)
    }

    fun toggleFavorite() {
        scope.launch {
            val current = _state.value
            val snovel = current.novel ?: return@launch
            // Materialize (or refresh) the local row, then flip the flag.
            val local = graph.networkToLocalNovel.await(snovel.toDomainNovel(sourceId))
            val target = !current.favorite
            graph.novelRepository.updateNovelFavorite(local.id, target)
            if (target) {
                syncLocalChapters(local.id, current.chapters)
            }
        }
    }

    /**
     * Android's mark-all toggle — flips every chapter to read, or back to
     * unread when [State.allChaptersRead]. Only meaningful once the novel is
     * in the library (local rows exist).
     */
    fun toggleAllChaptersRead() {
        scope.launch {
            val local = _state.value.local ?: return@launch
            val target = !_state.value.allChaptersRead
            val updates = graph.getNovelChaptersByNovelId.await(local.id)
                .map { NovelChapterUpdate(id = it.id, read = target) }
            if (updates.isNotEmpty()) {
                graph.novelChapterRepository.updateAllNovelChapters(updates)
            }
        }
    }

    /** Mark a single chapter read/unread — the reader calls this on open. */
    fun markChapter(chapterUrl: String, read: Boolean) {
        scope.launch {
            val local = _state.value.local ?: return@launch
            val chapter = graph.getNovelChaptersByNovelId.await(local.id)
                .firstOrNull { it.url == chapterUrl }
                ?: return@launch
            graph.novelChapterRepository.updateNovelChapter(
                NovelChapterUpdate(id = chapter.id, read = read),
            )
        }
    }

    fun dispose() = scope.cancel()
}
