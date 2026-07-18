package eu.kanade.tachiyomi.data.achievement

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import tachiyomi.domain.achievement.model.AchievementEvent

/**
 * SharedFlow-based event bus for achievement events.
 *
 * Events are emitted from various parts of the app (chapter read, episode watched,
 * library added, etc.) and consumed by [AchievementHandler].
 */
class AchievementEventBus {
    private val _events = MutableSharedFlow<AchievementEvent>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<AchievementEvent> = _events.asSharedFlow()

    suspend fun emit(event: AchievementEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: AchievementEvent): Boolean {
        return _events.tryEmit(event)
    }
}
