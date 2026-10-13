package miko.ui.achievements

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.achievement.handler.SessionManager
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import kotlin.time.Clock

/**
 * Port of Android's app-lifecycle session accounting (`App.onStart`/`onStop`):
 * `SessionManager` bookkeeping, today's activity_log row, a 60s flush of
 * foreground time into the activity repository, and a final flush +
 * `SessionEnd` event when the app goes to background.
 *
 * Driven by the shared shell's lifecycle observer — Compose Multiplatform's
 * `LocalLifecycleOwner` emits the same ON_START/ON_STOP pair on iOS that
 * `ProcessLifecycleOwner` produces on Android.
 */
class AchievementSessionTracker(
    private val eventBus: AchievementEventBus,
    private val sessionManager: SessionManager,
    private val activityDataRepository: ActivityDataRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var sessionStartMs = 0L
    private var lastFlushMs = 0L
    private var sessionFlushJob: Job? = null

    fun onAppStart() {
        sessionStartMs = Clock.System.now().toEpochMilliseconds()
        lastFlushMs = sessionStartMs
        sessionManager.onSessionStart()

        // Ensure an activity_log row exists for today and start periodic flushing
        scope.launch {
            try {
                activityDataRepository.recordAppOpen()
            } catch (_: Exception) {
            }
        }
        sessionFlushJob = scope.launch {
            while (true) {
                delay(60_000L) // flush every 60 seconds
                flushSessionTime()
            }
        }
    }

    fun onAppStop() {
        sessionFlushJob?.cancel()
        sessionFlushJob = null
        // Emit SessionEnd event for achievements system and persist remaining session duration
        if (sessionStartMs > 0L) {
            // Flush remaining session time in a NonCancellable coroutine so
            // the write completes even as the app goes to background.
            scope.launch {
                flushSessionTime()
            }
            val durationMs = Clock.System.now().toEpochMilliseconds() - sessionStartMs
            eventBus.tryEmit(AchievementEvent.SessionEnd(durationMs = durationMs))
            sessionStartMs = 0L
        }
        sessionManager.onSessionEnd()
    }

    /**
     * Uses [NonCancellable] so the write completes even when the app is going
     * to background.
     */
    private suspend fun flushSessionTime() {
        val now = Clock.System.now().toEpochMilliseconds()
        if (sessionStartMs <= 0L || lastFlushMs <= 0L) return
        val delta = now - lastFlushMs
        lastFlushMs = now
        if (delta <= 0L) return
        withContext(NonCancellable) {
            try {
                activityDataRepository.recordAppSession(delta)
            } catch (_: Exception) {
            }
        }
    }
}
