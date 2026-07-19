package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// TODO: Port from Tadami - these are stub models for backup support.
// The full implementations require Tadami-specific stats models
// (StatsCalculations, WatchProgress, customStatus, etc.) that don't
// exist in the aniyomi-fork yet.

@Serializable
data class BackupUserProfile(
    @ProtoNumber(1) val username: String? = null,
    @ProtoNumber(2) val level: Int = 0,
    @ProtoNumber(3) val totalXP: Int = 0,
    @ProtoNumber(4) val currentXP: Int = 0,
    @ProtoNumber(5) val xpToNextLevel: Int = 0,
    @ProtoNumber(6) val titles: List<String> = emptyList(),
    @ProtoNumber(7) val badges: List<String> = emptyList(),
    @ProtoNumber(8) val unlockedThemes: List<String> = emptyList(),
    @ProtoNumber(9) val achievementsUnlocked: Int = 0,
    @ProtoNumber(10) val totalAchievements: Int = 0,
    @ProtoNumber(11) val joinDate: Long = 0L,
) {
    // TODO: Port from Tadami - implement toUserProfile() when UserProfile model is available
    companion object {
        fun fromUserProfile(profile: tachiyomi.domain.achievement.model.UserProfile): BackupUserProfile {
            return BackupUserProfile(
                username = profile.username,
                level = profile.level,
                totalXP = profile.totalXP,
                currentXP = profile.currentXP,
                xpToNextLevel = profile.xpToNextLevel,
                titles = profile.titles,
                badges = profile.badges,
                unlockedThemes = profile.unlockedThemes,
                achievementsUnlocked = profile.achievementsUnlocked,
                totalAchievements = profile.totalAchievements,
                joinDate = profile.joinDate,
            )
        }
    }

    fun toUserProfile(): tachiyomi.domain.achievement.model.UserProfile {
        return tachiyomi.domain.achievement.model.UserProfile(
            username = username,
            level = level,
            totalXP = totalXP,
            currentXP = currentXP,
            xpToNextLevel = xpToNextLevel,
            titles = titles,
            badges = badges,
            unlockedThemes = unlockedThemes,
            achievementsUnlocked = achievementsUnlocked,
            totalAchievements = totalAchievements,
            joinDate = joinDate,
        )
    }
}

@Serializable
data class BackupDayActivity(
    @ProtoNumber(1) val date: String = "",
    @ProtoNumber(2) val level: Int = 0,
    @ProtoNumber(3) val type: Int = 0,
    @ProtoNumber(4) val chaptersRead: Int = 0,
    @ProtoNumber(5) val episodesWatched: Int = 0,
    @ProtoNumber(6) val appOpens: Int = 0,
    @ProtoNumber(7) val achievementsUnlocked: Int = 0,
    @ProtoNumber(8) val durationMs: Long = 0L,
) {
    // TODO: Port from Tadami - implement fromDatabaseRecord when database schema is finalized
    companion object {
        fun fromDatabaseRecord(
            date: java.time.LocalDate,
            level: Int,
            type: tachiyomi.domain.achievement.model.ActivityType,
            chaptersRead: Int,
            episodesWatched: Int,
            appOpens: Int,
            achievementsUnlocked: Int,
            durationMs: Long,
        ): BackupDayActivity {
            return BackupDayActivity(
                date = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                level = level,
                type = type.ordinal,
                chaptersRead = chaptersRead,
                episodesWatched = episodesWatched,
                appOpens = appOpens,
                achievementsUnlocked = achievementsUnlocked,
                durationMs = durationMs,
            )
        }
    }

    fun toDatabaseParams(): DayActivityParams {
        return DayActivityParams(
            date = date,
            chaptersRead = chaptersRead,
            episodesWatched = episodesWatched,
            appOpens = appOpens,
            achievementsUnlocked = achievementsUnlocked,
            durationMs = durationMs,
        )
    }
}

data class DayActivityParams(
    val date: String,
    val chaptersRead: Int,
    val episodesWatched: Int,
    val appOpens: Int,
    val achievementsUnlocked: Int,
    val durationMs: Long,
)

@Serializable
data class BackupStats(
    @ProtoNumber(1) val mangaLibraryCount: Int = 0,
    @ProtoNumber(2) val mangaCompletedCount: Int = 0,
    @ProtoNumber(3) val mangaTotalReadDuration: Long = 0L,
    @ProtoNumber(4) val mangaStartedCount: Int = 0,
    @ProtoNumber(5) val mangaLocalCount: Int = 0,
    @ProtoNumber(6) val chaptersTotalCount: Int = 0,
    @ProtoNumber(7) val chaptersReadCount: Int = 0,
    @ProtoNumber(8) val chaptersDownloadedCount: Int = 0,
    @ProtoNumber(9) val mangaGlobalUpdateCount: Int = 0,
    @ProtoNumber(10) val animeLibraryCount: Int = 0,
    @ProtoNumber(11) val animeCompletedCount: Int = 0,
    @ProtoNumber(12) val animeTotalSeenDuration: Long = 0L,
    @ProtoNumber(13) val animeStartedCount: Int = 0,
    @ProtoNumber(14) val animeLocalCount: Int = 0,
    @ProtoNumber(15) val episodesTotalCount: Int = 0,
    @ProtoNumber(16) val episodesWatchedCount: Int = 0,
    @ProtoNumber(17) val episodesDownloadedCount: Int = 0,
    @ProtoNumber(18) val animeGlobalUpdateCount: Int = 0,
    @ProtoNumber(19) val trackedTitleCount: Int = 0,
    @ProtoNumber(20) val meanScore: Double = 0.0,
    @ProtoNumber(21) val trackerCount: Int = 0,
)
