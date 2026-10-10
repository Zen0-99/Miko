package tachiyomi.domain.achievement.model

import tachiyomi.domain.achievement.model.AchievementCategory
import kotlin.time.Clock

sealed class AchievementEvent {
    abstract val timestamp: Long

    data class ChapterRead(
        val mangaId: Long,
        val chapterNumber: Int,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : AchievementEvent()

    data class NovelChapterRead(
        val novelId: Long,
        val chapterNumber: Int,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : AchievementEvent()

    data class EpisodeWatched(
        val animeId: Long,
        val episodeNumber: Int,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : AchievementEvent()

    data class LibraryAdded(
        val entryId: Long,
        val type: AchievementCategory,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : AchievementEvent()

    data class LibraryRemoved(
        val entryId: Long,
        val type: AchievementCategory,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : AchievementEvent()

    data class MangaCompleted(
        val mangaId: Long,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : AchievementEvent()

    data class AnimeCompleted(
        val animeId: Long,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : AchievementEvent()

    data class NovelCompleted(
        val novelId: Long,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : AchievementEvent()

    data class SessionEnd(
        val durationMs: Long,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : AchievementEvent()

    data class AppStart(
        val hourOfDay: Int,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : AchievementEvent()

    data class FeatureUsed(
        val feature: Feature,
        val count: Int = 1,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : AchievementEvent()

    enum class Feature {
        SEARCH,
        ADVANCED_SEARCH,
        FILTER,
        DOWNLOAD,
        BACKUP,
        RESTORE,
        SETTINGS,
        STATS,
        THEME_CHANGE,
        LOGO_CLICK,
        SECRET_HALL_UNLOCKED,
    }
}
