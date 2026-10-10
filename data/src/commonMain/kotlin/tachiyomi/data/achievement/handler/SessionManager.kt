package tachiyomi.data.achievement.handler

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.achievement.model.AchievementEvent
import kotlin.time.Clock

class SessionManager(
    private val eventBus: AchievementEventBus,
    private val featureCollector: FeatureUsageCollector,
) {
    private var startTime: Long = 0L
    private var startTimestamp: Long = 0L

    fun onSessionStart() {
        startTime = Clock.System.now().toEpochMilliseconds()
        startTimestamp = startTime

        val hourOfDay = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .hour
        featureCollector.onSessionStart(startTimestamp, hourOfDay)
        eventBus.tryEmit(AchievementEvent.AppStart(hourOfDay = hourOfDay))
    }

    fun onSessionEnd() {
        if (startTime == 0L) return

        val durationMs = Clock.System.now().toEpochMilliseconds() - startTime
        featureCollector.onSessionEnd(durationMs)
        eventBus.tryEmit(AchievementEvent.SessionEnd(durationMs))

        startTime = 0L
    }
}
