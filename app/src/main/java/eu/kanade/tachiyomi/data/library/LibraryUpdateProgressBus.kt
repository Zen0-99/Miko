package eu.kanade.tachiyomi.data.library

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide bus for library update progress, consumed by the in-app overlay
 * (LibraryUpdateProgressOverlay) and the Fetching tab. The 3 library update jobs
 * (Manga/Anime/Novel) publish state here; the UI subscribes.
 *
 * Pause/resume is cooperative: the UI calls [requestPause] / [requestResume], and
 * the running job checks [isPauseRequested] between entries. Pause cancels the job
 * (WorkManager has no native pause), but the checkpoint of already-processed entry
 * IDs is preserved so a subsequent [resumeFromCheckpoint] can skip them.
 */
object LibraryUpdateProgressBus {

    private val _state = MutableStateFlow<LibraryUpdateProgress>(LibraryUpdateProgress.Idle)
    val state: StateFlow<LibraryUpdateProgress> = _state.asStateFlow()

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    private val pauseRequested = AtomicReference(false)
    private val cancelRequested = AtomicReference(false)
    private val checkpointMutex = Mutex()

    // Store the total entries for the current run so completeRun can
    // report the correct total (avoids the "0/0" flash when the
    // checkpoint is cleared before completion is published).
    private var currentRunTotal: Int = 0

    /**
     * The set of entry IDs already processed in the current run, used for resume.
     * Cleared when a run completes (success or fully cancelled).
     */
    private val _checkpoint = mutableSetOf<Long>()
    val checkpoint: Set<Long> get() = synchronized(_checkpoint) { _checkpoint.toSet() }

    // ---- Job-side API (called from Manga/Anime/NovelLibraryUpdateJob) ----

    fun startRun(total: Int, source: String, isResume: Boolean = false) {
        pauseRequested.set(false)
        cancelRequested.set(false)
        currentRunTotal = total
        if (!isResume) {
            synchronized(_checkpoint) { _checkpoint.clear() }
            // Clear persisted failed fetches from previous runs so the
            // Fetching tab shows only the current run's failures.
            kotlinx.coroutines.GlobalScope.launch {
                FailedFetchStore.clearAll()
            }
        }
        _state.value = LibraryUpdateProgress.Running(
            totalEntries = total,
            processedEntries = if (isResume) checkpoint.size else 0,
            currentlyUpdating = emptyList(),
            failedSoFar = emptyList(),
            source = source,
            isPaused = false,
        )
    }

    fun updateProgress(
        processed: Int,
        currentlyUpdating: List<EntryRef>,
        failedSoFar: List<FailedEntry>,
        totalEntries: Int,
        source: String,
    ) {
        val current = _state.value
        if (current is LibraryUpdateProgress.Running) {
            _state.value = current.copy(
                processedEntries = processed,
                currentlyUpdating = currentlyUpdating,
                failedSoFar = failedSoFar,
                totalEntries = totalEntries,
            )
        } else {
            _state.value = LibraryUpdateProgress.Running(
                totalEntries = totalEntries,
                processedEntries = processed,
                currentlyUpdating = currentlyUpdating,
                failedSoFar = failedSoFar,
                source = source,
                isPaused = false,
            )
        }
    }

    fun markProcessed(entryId: Long) {
        synchronized(_checkpoint) { _checkpoint.add(entryId) }
    }

    fun completeRun(failed: List<FailedEntry>, source: String) {
        val processed = synchronized(_checkpoint) { _checkpoint.size }
        val total = currentRunTotal
        _state.value = LibraryUpdateProgress.Completed(
            failed = failed,
            totalProcessed = processed,
            totalEntries = total,
            source = source,
        )
        synchronized(_checkpoint) { _checkpoint.clear() }
        currentRunTotal = 0
        pauseRequested.set(false)
        cancelRequested.set(false)
    }

    fun idle() {
        _state.value = LibraryUpdateProgress.Idle
        synchronized(_checkpoint) { _checkpoint.clear() }
        pauseRequested.set(false)
        cancelRequested.set(false)
    }

    fun isPauseRequested(): Boolean = pauseRequested.get()
    fun isCancelRequested(): Boolean = cancelRequested.get()

    /**
     * Returns the current checkpoint snapshot for resume. The caller should pass this
     * to [startNowWithResume] on the relevant job.
     */
    suspend fun resumeFromCheckpoint(): Set<Long> = checkpointMutex.withLock { checkpoint }

    // ---- UI-side API ----

    fun requestPause() {
        pauseRequested.set(true)
        _commands.tryEmit(Command.Pause)
    }

    fun requestResume() {
        pauseRequested.set(false)
        _commands.tryEmit(Command.Resume)
    }

    fun requestCancel() {
        cancelRequested.set(true)
        _commands.tryEmit(Command.Cancel)
    }

    /**
     * Resume a paused run by starting a new job of the same kind that skips
     * entries already in [checkpoint]. Called by the UI's resume button.
     * The source is read from the current [state]; if no run was paused, this
     * is a no-op.
     */
    fun resumeRun(context: android.content.Context) {
        val current = _state.value as? LibraryUpdateProgress.Running ?: return
        if (!current.isPaused) return
        val source = current.source
        pauseRequested.set(false)
        when (source) {
            "Manga" -> eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob.startNow(
                context,
                resumeFromCheckpoint = true,
            )
            "Anime" -> eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob.startNow(
                context,
                resumeFromCheckpoint = true,
            )
            "Novel" -> eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob.startNow(
                context,
                resumeFromCheckpoint = true,
            )
        }
    }

    sealed interface Command {
        object Pause : Command
        object Resume : Command
        object Cancel : Command
        /**
         * Emitted when the user taps "view failures" on the progress overlay.
         * Observed by [eu.kanade.tachiyomi.ui.home.HomeScreen] to switch to the
         * Updates tab (which renders the Fetching sub-tab).
         */
        object ViewFailures : Command
    }

    fun requestViewFailures() {
        _commands.tryEmit(Command.ViewFailures)
    }
}

sealed interface LibraryUpdateProgress {
    object Idle : LibraryUpdateProgress

    data class Running(
        val totalEntries: Int,
        val processedEntries: Int,
        val currentlyUpdating: List<EntryRef>,
        val failedSoFar: List<FailedEntry>,
        val source: String, // "Manga" / "Anime" / "Novel" / "All"
        val isPaused: Boolean = false,
    ) : LibraryUpdateProgress

    data class Completed(
        val failed: List<FailedEntry>,
        val totalProcessed: Int,
        val totalEntries: Int,
        val source: String,
    ) : LibraryUpdateProgress
}

data class EntryRef(
    val id: Long,
    val title: String,
    val sourceId: Long,
    val kind: tachiyomi.domain.library.model.EntryKind,
)

data class FailedEntry(
    val entry: EntryRef,
    val reason: String,
    val sourceName: String,
)
