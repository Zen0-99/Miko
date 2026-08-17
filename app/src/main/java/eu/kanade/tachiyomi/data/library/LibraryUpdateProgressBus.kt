package eu.kanade.tachiyomi.data.library

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide bus for library update progress, consumed by the in-app overlay
 * (LibraryUpdateProgressOverlay) and the Fetching tab. The 3 library update jobs
 * (Manga/Anime/Novel) publish state here; the UI subscribes.
 *
 * Supports multiple concurrent sources: [states] exposes a map keyed by source
 * name ("Manga" / "Anime" / "Novel") so the overlay can show stacked progress
 * sections when the user refreshes multiple modes simultaneously. [state] is
 * kept for backward compat (returns the first Running or Completed entry).
 *
 * Cancel is cooperative: the UI calls [requestCancel], and the running job
 * checks [isCancelRequested] between entries. The job's try/finally ensures
 * [completeRun] is called even on cancellation so the overlay doesn't get
 * stuck in Running state.
 *
 * All `_states` map mutations are synchronized via [statesLock] to prevent
 * read-modify-write races when multiple sources call [updateProgress]
 * concurrently.
 */
object LibraryUpdateProgressBus {

    private val _state = MutableStateFlow<LibraryUpdateProgress>(LibraryUpdateProgress.Idle)
    val state: StateFlow<LibraryUpdateProgress> = _state.asStateFlow()

    // Multi-source map: source name -> progress state. Allows the overlay to
    // show stacked progress sections when multiple modes refresh simultaneously.
    private val _states = MutableStateFlow<Map<String, LibraryUpdateProgress>>(emptyMap())
    val states: StateFlow<Map<String, LibraryUpdateProgress>> = _states.asStateFlow()

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    private val cancelRequested = AtomicReference(false)

    // Per-source cancel flags: allows cancelling only one mode (manga/anime/novel)
    // without affecting the others.
    private val cancelRequestedBySource = mutableMapOf<String, Boolean>()

    // Incremented on every startRun call. The overlay observes this to
    // un-dismiss itself when a new run starts (e.g. user swiped away the
    // overlay, then pulled to refresh again).
    private val _runGeneration = MutableStateFlow(0)
    val runGeneration: StateFlow<Int> = _runGeneration.asStateFlow()

    // Incremented on every refreshRequest call — even when startNow returns
    // false because the job is already running. This lets the overlay
    // un-dismiss itself when the user pulls to refresh while a job is
    // already running and the overlay was swiped away.
    private val _refreshRequested = MutableStateFlow(0)
    val refreshRequested: StateFlow<Int> = _refreshRequested.asStateFlow()

    // Tracks whether a modal bottom sheet (GroupBy, DisplayOptions, etc.) is
    // currently open. The overlay hides itself when this is true so the sheet
    // appears above the overlay without z-order conflicts.
    private val _sheetVisible = MutableStateFlow(false)
    val sheetVisible: StateFlow<Boolean> = _sheetVisible.asStateFlow()

    fun setSheetVisible(visible: Boolean) {
        _sheetVisible.value = visible
    }

    // Synchronizes all _states map read-modify-write operations to prevent
    // concurrent updates from overwriting each other. Uses a plain Object lock
    // with synchronized() blocks instead of a coroutine Mutex because these
    // methods are called from non-suspending job code and must block until the
    // lock is acquired — tryLock() would silently skip synchronization.
    private val statesLock = Object()

    // Store per-source total entries so completeRun can report the correct
    // total even when multiple sources run concurrently (e.g. backup restore
    // restoring Manga + Anime + Novel in parallel).
    private val currentRunTotals = mutableMapOf<String, Int>()

    // ---- Job-side API (called from Manga/Anime/NovelLibraryUpdateJob) ----

    fun startRun(total: Int, source: String) {
        Log.d("ProgressBus", "startRun: source=$source total=$total gen=${_runGeneration.value}")
        cancelRequested.set(false)
        synchronized(cancelRequestedBySource) { cancelRequestedBySource.remove(source) }
        _runGeneration.value = _runGeneration.value + 1
        synchronized(currentRunTotals) { currentRunTotals[source] = total }
        // Clear persisted failed fetches from previous runs so the
        // Fetching tab shows only the current run's failures.
        kotlinx.coroutines.GlobalScope.launch {
            FailedFetchStore.clearAll()
        }
        val running = LibraryUpdateProgress.Running(
            totalEntries = total,
            processedEntries = 0,
            currentlyUpdating = emptyList(),
            failedSoFar = emptyList(),
            source = source,
        )
        _state.value = running

        // Update multi-source map atomically: accumulate totals if the same
        // source is already running (e.g. refreshing Action collection then
        // Comedy collection in the same mode → x/35).
        synchronized(statesLock) {
            val currentMap = _states.value
            val existing = currentMap[source] as? LibraryUpdateProgress.Running
            val updated = if (existing != null) {
                existing.copy(totalEntries = existing.totalEntries + total)
            } else {
                running
            }
            _states.value = currentMap + (source to updated)
        }
    }

    fun updateProgress(
        processed: Int,
        currentlyUpdating: List<EntryRef>,
        failedSoFar: List<FailedEntry>,
        totalEntries: Int,
        source: String,
    ) {
        Log.d("ProgressBus", "updateProgress: source=$source processed=$processed total=$totalEntries titles=${currentlyUpdating.map { it.title }}")
        // Update single state flow (backward compat) — only if the current
        // state belongs to this source, to prevent cross-source contamination.
        val current = _state.value
        if (current is LibraryUpdateProgress.Running && current.source == source) {
            _state.value = current.copy(
                processedEntries = processed,
                currentlyUpdating = currentlyUpdating,
                failedSoFar = failedSoFar,
                totalEntries = totalEntries,
            )
        } else if (current !is LibraryUpdateProgress.Running) {
            _state.value = LibraryUpdateProgress.Running(
                totalEntries = totalEntries,
                processedEntries = processed,
                currentlyUpdating = currentlyUpdating,
                failedSoFar = failedSoFar,
                source = source,
            )
        }

        // Update multi-source map atomically to prevent race conditions.
        // Use max(processed, existing.processed) to ensure progress never
        // goes backwards when out-of-order completions cause an old
        // publishState() to arrive after a newer one.
        // Preserve the accumulated total from startRun (which sums totals
        // when the same mode is refreshed multiple times).
        synchronized(statesLock) {
            val currentMap = _states.value
            val existing = currentMap[source] as? LibraryUpdateProgress.Running
            val updated = if (existing != null) {
                existing.copy(
                    processedEntries = maxOf(processed, existing.processedEntries),
                    currentlyUpdating = currentlyUpdating,
                    failedSoFar = failedSoFar,
                    totalEntries = maxOf(totalEntries, existing.totalEntries),
                )
            } else {
                LibraryUpdateProgress.Running(
                    totalEntries = totalEntries,
                    processedEntries = processed,
                    currentlyUpdating = currentlyUpdating,
                    failedSoFar = failedSoFar,
                    source = source,
                )
            }
            _states.value = currentMap + (source to updated)
        }
    }

    fun completeRun(failed: List<FailedEntry>, source: String) {
        Log.d("ProgressBus", "completeRun: source=$source failed=${failed.size}")
        synchronized(cancelRequestedBySource) { cancelRequestedBySource.remove(source) }
        val runningProcessed = (_states.value[source] as? LibraryUpdateProgress.Running)?.processedEntries ?: 0
        val total = synchronized(currentRunTotals) { currentRunTotals[source] ?: 0 }
        val completed = LibraryUpdateProgress.Completed(
            failed = failed,
            totalProcessed = runningProcessed,
            totalEntries = total,
            source = source,
        )
        _state.value = completed
        synchronized(statesLock) {
            _states.value = _states.value + (source to completed)
        }
        synchronized(currentRunTotals) { currentRunTotals.remove(source) }
        cancelRequested.set(false)
    }

    fun idle() {
        _state.value = LibraryUpdateProgress.Idle
        _states.value = emptyMap()
        synchronized(currentRunTotals) { currentRunTotals.clear() }
        cancelRequested.set(false)
    }

    /**
     * Remove a source from the multi-source map. Called by the overlay after
     * a completed source has been shown for the auto-dismiss delay.
     */
    fun removeSource(source: String) {
        synchronized(statesLock) {
            _states.value = _states.value - source
        }
    }

    fun isCancelRequested(): Boolean = cancelRequested.get()

    /**
     * Check if cancel was requested for a specific source ("Manga" / "Anime" /
     * "Novel"). Jobs should check this between entries and before network calls
     * for per-mode cancellation.
     */
    fun isCancelRequested(source: String): Boolean {
        return cancelRequested.get() || synchronized(cancelRequestedBySource) {
            cancelRequestedBySource[source] == true
        }
    }

    // ---- UI-side API ----

    /**
     * Called by the library tab when the user pulls to refresh, regardless
     * of whether [startRun] is actually called (the job may already be
     * running, in which case startNow returns false). The overlay observes
     * this to un-dismiss itself.
     */
    fun refreshRequested() {
        Log.d("ProgressBus", "refreshRequested: gen=${_refreshRequested.value}")
        _refreshRequested.value = _refreshRequested.value + 1
    }

    /**
     * Request cancellation of all running library update jobs. Sets the
     * cooperative cancel flag (checked by jobs between entries and before
     * network calls), emits [Command.Cancel] (observed by the overlay to
     * hide instantly), and cancels the progress notification immediately
     * so the user sees an instant UI response.
     *
     * Pass the [context] so we can cancel the notification. If context is
     * null (e.g. called from a pure Kotlin context), the notification is
     * not cancelled — the job's finally block will handle it.
     */
    fun requestCancel(context: Context? = null) {
        Log.d("ProgressBus", "requestCancel (all)")
        cancelRequested.set(true)
        _commands.tryEmit(Command.Cancel)
        context?.cancelNotification(Notifications.ID_LIBRARY_PROGRESS)
    }

    /**
     * Request cancellation of a specific source only (e.g. "Manga" but not
     * "Anime" or "Novel"). Emits [Command.CancelSource] with the source name
     * so the overlay can hide just that section. The job for that source
     * checks [isCancelRequested] with its source name.
     */
    fun requestCancelSource(source: String, context: Context? = null) {
        Log.d("ProgressBus", "requestCancelSource: source=$source")
        synchronized(cancelRequestedBySource) {
            cancelRequestedBySource[source] = true
        }
        _commands.tryEmit(Command.CancelSource(source))
        // If no other sources are running, cancel the notification too
        val otherSourcesRunning = _states.value.any { (key, state) ->
            key != source && state is LibraryUpdateProgress.Running
        }
        if (!otherSourcesRunning) {
            context?.cancelNotification(Notifications.ID_LIBRARY_PROGRESS)
        }
    }

    sealed interface Command {
        object Cancel : Command
        /** Cancel only a specific source (e.g. "Manga" but not "Anime") */
        data class CancelSource(val source: String) : Command
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
