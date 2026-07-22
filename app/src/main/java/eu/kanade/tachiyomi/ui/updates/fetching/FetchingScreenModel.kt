package eu.kanade.tachiyomi.ui.updates.fetching

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgress
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.library.interactor.ClearFailedFetches
import tachiyomi.domain.library.interactor.DeleteFailedFetch
import tachiyomi.domain.library.interactor.GetFailedFetches
import tachiyomi.domain.library.model.EntryKind
import tachiyomi.domain.library.model.FailedFetch
import tachiyomi.domain.entries.EntryCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FetchingScreenModel(
    private val getFailedFetches: GetFailedFetches = Injekt.get(),
    private val deleteFailedFetch: DeleteFailedFetch = Injekt.get(),
    private val clearFailedFetches: ClearFailedFetches = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
) : StateScreenModel<FetchingState>(FetchingState.Loading) {

    private val _dialog = MutableStateFlow<Dialog?>(null)
    val dialog: StateFlow<Dialog?> = _dialog.asStateFlow()

    init {
        screenModelScope.launch {
            combine(
                getFailedFetches.subscribe(),
                LibraryUpdateProgressBus.state,
            ) { failed, progress ->
                val enriched = mutableListOf<FailedFetchUi>()
                for (ff in failed) {
                    enrich(ff)?.let { enriched.add(it) }
                }
                FetchingState.Ready(
                    failedFetches = enriched,
                    progress = progress,
                )
            }.collectLatest { mutableState.value = it }
        }
    }

    private suspend fun enrich(ff: FailedFetch): FailedFetchUi? {
        val cover: EntryCover? = when (ff.entryKind) {
            EntryKind.MANGA -> getManga.await(ff.entryId)?.asMangaCover()
            EntryKind.ANIME -> getAnime.await(ff.entryId)?.asAnimeCover()
            EntryKind.NOVEL -> getNovel.await(ff.entryId)?.asNovelCover()
        }
        return FailedFetchUi(
            id = ff.id,
            entryId = ff.entryId,
            entryKind = ff.entryKind,
            title = ff.title,
            cover = cover,
            sourceId = ff.sourceId,
            sourceName = ff.sourceName,
            reason = ff.reason,
            timestamp = ff.timestamp,
        )
    }

    fun setDialog(dialog: Dialog?) {
        _dialog.value = dialog
    }

    fun clearAll() {
        screenModelScope.launch {
            clearFailedFetches.await()
            setDialog(null)
        }
    }

    fun deleteById(id: Long) {
        screenModelScope.launch { deleteFailedFetch.awaitById(id) }
    }

    fun deleteByReason(reason: String) {
        screenModelScope.launch { deleteFailedFetch.awaitByReason(reason) }
    }

    fun pause() = LibraryUpdateProgressBus.requestPause()
    fun resume(context: android.content.Context) = LibraryUpdateProgressBus.resumeRun(context)
    fun cancel() = LibraryUpdateProgressBus.requestCancel()

    sealed interface Dialog {
        object ClearAllConfirmation : Dialog
    }
}

sealed interface FetchingState {
    object Loading : FetchingState
    data class Ready(
        val failedFetches: List<FailedFetchUi>,
        val progress: LibraryUpdateProgress,
    ) : FetchingState
}

@Immutable
data class FailedFetchUi(
    val id: Long,
    val entryId: Long,
    val entryKind: EntryKind,
    val title: String,
    val cover: EntryCover?,
    val sourceId: Long,
    val sourceName: String,
    val reason: String,
    val timestamp: Long,
)

@Immutable
data class FailedFetchGroup(
    val reason: String,
    val entries: List<FailedFetchUi>,
)
