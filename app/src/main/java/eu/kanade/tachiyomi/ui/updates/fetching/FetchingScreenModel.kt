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
import tachiyomi.domain.entries.EntryCover
import tachiyomi.domain.entries.shows.interactor.GetAnime
import tachiyomi.domain.entries.shows.model.asAnimeCover
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.library.interactor.ClearFailedFetches
import tachiyomi.domain.library.interactor.DeleteFailedFetch
import tachiyomi.domain.library.interactor.GetFailedFetches
import tachiyomi.domain.library.model.EntryKind
import tachiyomi.domain.library.model.FailedFetch
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
                // Persisted failures from the database
                for (ff in failed) {
                    enrich(ff)?.let { enriched.add(it) }
                }
                // Live failures from the current run (not yet persisted to DB).
                // These appear in the fetching tab in real-time as they fail.
                if (progress is LibraryUpdateProgress.Running) {
                    val existingEntryIds = enriched.map { it.entryId }.toHashSet()
                    for (fe in progress.failedSoFar) {
                        if (fe.entry.id in existingEntryIds) continue
                        enrichLive(fe)?.let { enriched.add(it) }
                    }
                }
                FetchingState.Ready(
                    failedFetches = enriched,
                    progress = progress,
                )
            }.collectLatest { mutableState.value = it }
        }
    }

    private suspend fun enrich(ff: FailedFetch): FailedFetchUi? {
        val entry: Pair<EntryCover, Long>? = when (ff.entryKind) {
            EntryKind.MANGA -> getManga.await(ff.entryId)?.let { it.asMangaCover() to it.source }
            EntryKind.SHOWS -> getAnime.await(ff.entryId)?.let { it.asAnimeCover() to it.source }
            EntryKind.NOVEL -> getNovel.await(ff.entryId)?.let { it.asNovelCover() to it.source }
        }
        // Drop the record when the entry was deleted or migrated to another
        // source since the failure — the row no longer describes reality.
        if (entry == null || entry.second != ff.sourceId) {
            deleteFailedFetch.awaitById(ff.id)
            return null
        }
        val cover: EntryCover = entry.first
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

    private suspend fun enrichLive(fe: eu.kanade.tachiyomi.data.library.FailedEntry): FailedFetchUi? {
        val entry: Pair<EntryCover, Long>? = when (fe.entry.kind) {
            EntryKind.MANGA -> getManga.await(fe.entry.id)?.let { it.asMangaCover() to it.source }
            EntryKind.SHOWS -> getAnime.await(fe.entry.id)?.let { it.asAnimeCover() to it.source }
            EntryKind.NOVEL -> getNovel.await(fe.entry.id)?.let { it.asNovelCover() to it.source }
        }
        // Live rows aren't persisted — just hide once the entry moved on.
        if (entry == null || entry.second != fe.entry.sourceId) return null
        val cover: EntryCover = entry.first
        return FailedFetchUi(
            id = -(fe.entry.id), // Negative ID to distinguish live entries from DB ones
            entryId = fe.entry.id,
            entryKind = fe.entry.kind,
            title = fe.entry.title,
            cover = cover,
            sourceId = fe.entry.sourceId,
            sourceName = fe.sourceName,
            reason = fe.reason,
            timestamp = System.currentTimeMillis(),
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
