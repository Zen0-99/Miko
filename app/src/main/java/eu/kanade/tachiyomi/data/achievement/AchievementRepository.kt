package eu.kanade.tachiyomi.data.achievement

import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.model.UserProfile

/**
 * Repository interface for achievements data access.
 *
 * Full implementation would back this with SQLDelight tables:
 * - achievements (definitions)
 * - achievement_progress (per-user progress)
 * - user_profile (XP, level)
 * - activity_log (daily activity tracking)
 */
interface AchievementRepository {

    /** Get all achievement definitions. */
    suspend fun getAllAchievements(): List<Achievement>

    /** Get achievements by type. */
    suspend fun getAchievementsByType(type: AchievementType): List<Achievement>

    /** Get achievements by category. */
    suspend fun getAchievementsByCategory(
        category: tachiyomi.domain.achievement.model.AchievementCategory,
    ): List<Achievement>

    /** Get progress for a specific achievement. */
    suspend fun getProgress(achievementId: String): AchievementProgress?

    /** Get all progress entries. */
    suspend fun getAllProgress(): List<AchievementProgress>

    /** Update or insert progress for an achievement. */
    suspend fun updateProgress(progress: AchievementProgress)

    /** Get the user profile (XP, level). */
    suspend fun getUserProfile(): UserProfile?

    /** Update the user profile. */
    suspend fun updateUserProfile(profile: UserProfile)

    /** Get count of unlocked achievements. */
    suspend fun getUnlockedCount(): Int
}

/**
 * In-memory implementation of [AchievementRepository].
 *
 * This is a skeleton that stores data in memory. A full implementation
 * would use SQLDelight with the achievements.sq schema.
 */
class InMemoryAchievementRepository(
    private val achievements: List<Achievement> = defaultAchievements,
) : AchievementRepository {

    private val progressMap = mutableMapOf<String, AchievementProgress>()
    private var userProfile: UserProfile = UserProfile()

    override suspend fun getAllAchievements(): List<Achievement> = achievements

    override suspend fun getAchievementsByType(type: AchievementType): List<Achievement> =
        achievements.filter { it.type == type }

    override suspend fun getAchievementsByCategory(
        category: tachiyomi.domain.achievement.model.AchievementCategory,
    ): List<Achievement> = achievements.filter { it.category == category }

    override suspend fun getProgress(achievementId: String): AchievementProgress? {
        return progressMap[achievementId] ?: AchievementProgress(
            achievementId = achievementId,
            maxProgress = achievements.find { it.id == achievementId }?.threshold ?: 100,
        )
    }

    override suspend fun getAllProgress(): List<AchievementProgress> {
        return achievements.map { getProgress(it.id)!! }
    }

    override suspend fun updateProgress(progress: AchievementProgress) {
        progressMap[progress.achievementId] = progress
    }

    override suspend fun getUserProfile(): UserProfile = userProfile

    override suspend fun updateUserProfile(profile: UserProfile) {
        userProfile = profile
    }

    override suspend fun getUnlockedCount(): Int {
        return progressMap.values.count { it.isUnlocked }
    }
}

/** Default achievement definitions — a starter set covering all 10 types. */
val defaultAchievements = listOf(
    // QUANTITY — Manga
    Achievement(
        id = "manga_100_chapters",
        type = AchievementType.QUANTITY,
        category = tachiyomi.domain.achievement.model.AchievementCategory.MANGA,
        threshold = 100,
        points = 50,
        title = "Centurion",
        description = "Read 100 manga chapters",
        rarity = tachiyomi.domain.achievement.model.AchievementRarity.UNCOMMON,
        tags = listOf("manga", "quantity"),
    ),
    Achievement(
        id = "manga_1000_chapters",
        type = AchievementType.QUANTITY,
        category = tachiyomi.domain.achievement.model.AchievementCategory.MANGA,
        threshold = 1000,
        points = 200,
        title = "Marathon Reader",
        description = "Read 1,000 manga chapters",
        rarity = tachiyomi.domain.achievement.model.AchievementRarity.RARE,
        tags = listOf("manga", "quantity"),
    ),
    // QUANTITY — Anime
    Achievement(
        id = "anime_100_episodes",
        type = AchievementType.QUANTITY,
        category = tachiyomi.domain.achievement.model.AchievementCategory.ANIME,
        threshold = 100,
        points = 50,
        title = "Binge Watcher",
        description = "Watch 100 anime episodes",
        rarity = tachiyomi.domain.achievement.model.AchievementRarity.UNCOMMON,
        tags = listOf("anime", "quantity"),
    ),
    // QUANTITY — Novel
    Achievement(
        id = "novel_50_chapters",
        type = AchievementType.QUANTITY,
        category = tachiyomi.domain.achievement.model.AchievementCategory.NOVEL,
        threshold = 50,
        points = 30,
        title = "Bookworm",
        description = "Read 50 novel chapters",
        rarity = tachiyomi.domain.achievement.model.AchievementRarity.UNCOMMON,
        tags = listOf("novel", "quantity"),
    ),
    // EVENT
    Achievement(
        id = "first_chapter",
        type = AchievementType.EVENT,
        category = tachiyomi.domain.achievement.model.AchievementCategory.MANGA,
        threshold = 1,
        points = 10,
        title = "First Steps",
        description = "Read your first manga chapter",
        rarity = tachiyomi.domain.achievement.model.AchievementRarity.COMMON,
        tags = listOf("manga", "event"),
    ),
    Achievement(
        id = "first_episode",
        type = AchievementType.EVENT,
        category = tachiyomi.domain.achievement.model.AchievementCategory.ANIME,
        threshold = 1,
        points = 10,
        title = "First Watch",
        description = "Watch your first anime episode",
        rarity = tachiyomi.domain.achievement.model.AchievementRarity.COMMON,
        tags = listOf("anime", "event"),
    ),
    // LIBRARY
    Achievement(
        id = "library_50",
        type = AchievementType.LIBRARY,
        category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
        threshold = 50,
        points = 100,
        title = "Collector",
        description = "Add 50 items to your library",
        rarity = tachiyomi.domain.achievement.model.AchievementRarity.RARE,
        tags = listOf("library"),
    ),
    // TIME_BASED
    Achievement(
        id = "night_owl",
        type = AchievementType.TIME_BASED,
        category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
        threshold = 10,
        points = 50,
        title = "Night Owl",
        description = "Use the app between midnight and 4 AM, 10 times",
        rarity = tachiyomi.domain.achievement.model.AchievementRarity.UNCOMMON,
        tags = listOf("night_owl"),
    ),
    Achievement(
        id = "early_bird",
        type = AchievementType.TIME_BASED,
        category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
        threshold = 10,
        points = 50,
        title = "Early Bird",
        description = "Use the app between 5 AM and 8 AM, 10 times",
        rarity = tachiyomi.domain.achievement.model.AchievementRarity.UNCOMMON,
        tags = listOf("early_bird"),
    ),
    // FEATURE_BASED
    Achievement(
        id = "feature_SEARCH",
        type = AchievementType.FEATURE_BASED,
        category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
        threshold = 100,
        points = 20,
        title = "Search Explorer",
        description = "Use search 100 times",
        rarity = tachiyomi.domain.achievement.model.AchievementRarity.COMMON,
        tags = listOf("feature", "search"),
    ),
    // SECRET
    Achievement(
        id = "secret_logo_click",
        type = AchievementType.SECRET,
        category = tachiyomi.domain.achievement.model.AchievementCategory.SECRET,
        threshold = 10,
        points = 100,
        title = "???",
        description = "A secret achievement. Keep exploring...",
        isSecret = true,
        isHidden = true,
        rarity = tachiyomi.domain.achievement.model.AchievementRarity.LEGENDARY,
        tags = listOf("secret"),
        hint = "Click the logo many times...",
    ),
)
