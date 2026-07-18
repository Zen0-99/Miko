package eu.kanade.tachiyomi.data.achievement

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.model.AchievementProgress

/**
 * Central handler that subscribes to [AchievementEventBus] events and
 * updates achievement progress in the repository.
 *
 * This is the skeleton implementation. Full implementation requires:
 * - AchievementRepository for DB access
 * - Rule-based evaluation (QuantityRule, DiversityRule, StreakRule, etc.)
 * - UI notification on unlock
 */
class AchievementHandler(
    private val eventBus: AchievementEventBus,
    private val repository: AchievementRepository,
    private val pointsManager: PointsManager = PointsManager(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            eventBus.events.collect { event ->
                processEvent(event)
            }
        }
    }

    private suspend fun processEvent(event: AchievementEvent) {
        try {
            when (event) {
                is AchievementEvent.ChapterRead -> {
                    handleQuantityEvent(
                        category = AchievementCategory.MANGA,
                        increment = 1,
                    )
                }
                is AchievementEvent.EpisodeWatched -> {
                    handleQuantityEvent(
                        category = AchievementCategory.ANIME,
                        increment = 1,
                    )
                }
                is AchievementEvent.NovelChapterRead -> {
                    handleQuantityEvent(
                        category = AchievementCategory.NOVEL,
                        increment = 1,
                    )
                }
                is AchievementEvent.LibraryAdded -> {
                    handleLibraryEvent(event.type, increment = 1)
                }
                is AchievementEvent.MediaCompleted -> {
                    handleCompletionEvent(event.type)
                }
                is AchievementEvent.AppStart -> {
                    handleAppStartEvent(event.hourOfDay)
                }
                is AchievementEvent.FeatureUsed -> {
                    handleFeatureEvent(event.feature)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Achievement event processing failed" }
        }
    }

    private suspend fun handleQuantityEvent(
        category: AchievementCategory,
        increment: Int,
    ) {
        val quantityAchievements = repository.getAchievementsByType(
            tachiyomi.domain.achievement.model.AchievementType.QUANTITY,
        )
        for (achievement in quantityAchievements) {
            if (achievement.category != category && achievement.category != AchievementCategory.BOTH) continue
            val progress = repository.getProgress(achievement.id) ?: continue
            if (progress.isUnlocked) continue
            val newProgress = progress.progress + increment
            val threshold = achievement.threshold ?: continue
            val updated = progress.copy(
                progress = newProgress,
                maxProgress = threshold,
                isUnlocked = newProgress >= threshold,
                unlockedAt = if (newProgress >= threshold) System.currentTimeMillis() else null,
                lastUpdated = System.currentTimeMillis(),
            )
            repository.updateProgress(updated)
            if (updated.isUnlocked) {
                awardPoints(achievement.points)
                logcat(LogPriority.INFO) { "Achievement unlocked: ${achievement.title}" }
            }
        }
    }

    private suspend fun handleLibraryEvent(
        category: AchievementCategory,
        increment: Int,
    ) {
        val libraryAchievements = repository.getAchievementsByType(
            tachiyomi.domain.achievement.model.AchievementType.LIBRARY,
        )
        for (achievement in libraryAchievements) {
            if (achievement.category != category && achievement.category != AchievementCategory.BOTH) continue
            val progress = repository.getProgress(achievement.id) ?: continue
            if (progress.isUnlocked) continue
            val newProgress = progress.progress + increment
            val threshold = achievement.threshold ?: continue
            val updated = progress.copy(
                progress = newProgress,
                maxProgress = threshold,
                isUnlocked = newProgress >= threshold,
                unlockedAt = if (newProgress >= threshold) System.currentTimeMillis() else null,
                lastUpdated = System.currentTimeMillis(),
            )
            repository.updateProgress(updated)
            if (updated.isUnlocked) {
                awardPoints(achievement.points)
            }
        }
    }

    private suspend fun handleCompletionEvent(category: AchievementCategory) {
        val eventAchievements = repository.getAchievementsByType(
            tachiyomi.domain.achievement.model.AchievementType.EVENT,
        )
        for (achievement in eventAchievements) {
            if (achievement.category != category && achievement.category != AchievementCategory.BOTH) continue
            val progress = repository.getProgress(achievement.id) ?: continue
            if (progress.isUnlocked) continue
            val updated = progress.copy(
                progress = 1,
                maxProgress = 1,
                isUnlocked = true,
                unlockedAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis(),
            )
            repository.updateProgress(updated)
            awardPoints(achievement.points)
        }
    }

    private suspend fun handleAppStartEvent(hourOfDay: Int) {
        // Time-based achievements (night owl, early bird)
        val timeAchievements = repository.getAchievementsByType(
            tachiyomi.domain.achievement.model.AchievementType.TIME_BASED,
        )
        for (achievement in timeAchievements) {
            val progress = repository.getProgress(achievement.id) ?: continue
            if (progress.isUnlocked) continue
            // Check if hour matches achievement criteria (stored in tags)
            val matches = achievement.tags.any { tag ->
                when (tag) {
                    "night_owl" -> hourOfDay in 0..4
                    "early_bird" -> hourOfDay in 5..8
                    else -> false
                }
            }
            if (matches) {
                val updated = progress.copy(
                    progress = progress.progress + 1,
                    isUnlocked = progress.progress + 1 >= (achievement.threshold ?: 1),
                    unlockedAt = if (progress.progress + 1 >= (achievement.threshold ?: 1))
                        System.currentTimeMillis() else null,
                    lastUpdated = System.currentTimeMillis(),
                )
                repository.updateProgress(updated)
                if (updated.isUnlocked) awardPoints(achievement.points)
            }
        }
    }

    private suspend fun handleFeatureEvent(feature: String) {
        val featureAchievements = repository.getAchievementsByType(
            tachiyomi.domain.achievement.model.AchievementType.FEATURE_BASED,
        )
        for (achievement in featureAchievements) {
            if (achievement.id != "feature_$feature") continue
            val progress = repository.getProgress(achievement.id) ?: continue
            if (progress.isUnlocked) continue
            val newProgress = progress.progress + 1
            val threshold = achievement.threshold ?: 1
            val updated = progress.copy(
                progress = newProgress,
                maxProgress = threshold,
                isUnlocked = newProgress >= threshold,
                unlockedAt = if (newProgress >= threshold) System.currentTimeMillis() else null,
                lastUpdated = System.currentTimeMillis(),
            )
            repository.updateProgress(updated)
            if (updated.isUnlocked) awardPoints(achievement.points)
        }
    }

    private suspend fun awardPoints(points: Int) {
        if (points <= 0) return
        val profile = repository.getUserProfile() ?: return
        val updated = pointsManager.addXp(profile, points)
        repository.updateUserProfile(updated)
    }

    /**
     * Track a feature usage event.
     */
    fun trackFeatureUsed(feature: String) {
        eventBus.tryEmit(AchievementEvent.FeatureUsed(feature))
    }
}
