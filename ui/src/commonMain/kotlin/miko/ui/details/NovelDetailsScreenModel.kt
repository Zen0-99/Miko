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

/**
 * Entry details: remote novel details + chapter list, local favorite state,
 * and the favorite toggle that materializes the entry (and its chapters)
 * into the database.
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
        val favorite: Boolean = false,
        val detailsLoading: Boolean = true,
        val chaptersLoading: Boolean = true,
        val error: String? = null,
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

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
            }
        }
        refresh()
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
            },
            onFailure = {
                _state.value = _state.value.copy(
                    chaptersLoading = false,
                    error = _state.value.error ?: (it.message ?: "Failed to load chapters"),
                )
            },
        )
    }

    fun toggleFavorite() {
        scope.launch {
            val current = _state.value
            val snovel = current.novel ?: return@launch
            // Materialize (or refresh) the local row, then flip the flag.
            val local = graph.networkToLocalNovel.await(snovel.toDomainNovel(sourceId))
            val target = !current.favorite
            graph.novelRepository.updateNovelFavorite(local.id, target)
            if (target && current.chapters.isNotEmpty()) {
                persistChapters(local.id, current.chapters)
            }
        }
    }

    private suspend fun persistChapters(novelId: Long, chapters: List<SNovelChapter>) {
        val existing = graph.getNovelChaptersByNovelId.await(novelId)
        if (existing.isNotEmpty()) return
        val rows = chapters.mapIndexed { index, ch ->
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
        graph.novelChapterRepository.addAllNovelChapters(rows)
    }

    fun dispose() = scope.cancel()
}
