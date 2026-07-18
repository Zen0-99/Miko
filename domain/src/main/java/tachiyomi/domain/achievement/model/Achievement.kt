package tachiyomi.domain.achievement.model

/**
 * Achievement types matching Tadami's 10-type system.
 */
enum class AchievementType {
    QUANTITY,
    EVENT,
    DIVERSITY,
    STREAK,
    LIBRARY,
    META,
    BALANCED,
    SECRET,
    TIME_BASED,
    FEATURE_BASED,
}

/**
 * Achievement categories.
 */
enum class AchievementCategory {
    ANIME,
    MANGA,
    NOVEL,
    BOTH,
    SECRET,
}

/**
 * Achievement rarity levels.
 */
enum class AchievementRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
}

/**
 * A single achievement definition.
 */
data class Achievement(
    val id: String,
    val type: AchievementType,
    val category: AchievementCategory,
    val threshold: Int? = null,
    val points: Int = 0,
    val title: String,
    val description: String? = null,
    val badgeIcon: String? = null,
    val isHidden: Boolean = false,
    val isSecret: Boolean = false,
    val rarity: AchievementRarity = AchievementRarity.COMMON,
    val tags: List<String> = emptyList(),
    val hint: String? = null,
)

/**
 * Progress tracking for an achievement.
 */
data class AchievementProgress(
    val achievementId: String,
    val progress: Int = 0,
    val maxProgress: Int = 100,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
)

/**
 * User profile with XP, level, and unlock tracking.
 */
data class UserProfile(
    val userId: String = "default",
    val username: String? = null,
    val level: Int = 1,
    val currentXp: Int = 0,
    val xpToNextLevel: Int = 100,
    val totalXp: Int = 0,
    val achievementsUnlocked: Int = 0,
    val totalAchievements: Int = 0,
    val joinDate: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
)

/**
 * Events that can trigger achievement checks.
 */
sealed class AchievementEvent {
    abstract val timestamp: Long

    data class ChapterRead(
        val mangaId: Long,
        val chapterNumber: Int,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    data class EpisodeWatched(
        val animeId: Long,
        val episodeNumber: Int,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    data class NovelChapterRead(
        val novelId: Long,
        val chapterNumber: Int,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    data class LibraryAdded(
        val entryId: Long,
        val type: AchievementCategory,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    data class MediaCompleted(
        val id: Long,
        val type: AchievementCategory,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    data class AppStart(
        val hourOfDay: Int,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()

    data class FeatureUsed(
        val feature: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AchievementEvent()
}
