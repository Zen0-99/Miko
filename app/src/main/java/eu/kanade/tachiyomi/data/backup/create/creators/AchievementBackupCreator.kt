package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupAchievement
import eu.kanade.tachiyomi.data.backup.models.BackupDayActivity
import eu.kanade.tachiyomi.data.backup.models.BackupStats
import eu.kanade.tachiyomi.data.backup.models.BackupUserProfile
import kotlinx.coroutines.flow.first
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.UserProfileManager
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AchievementBackupCreator(
    private val achievementRepository: AchievementRepository = Injekt.get(),
    private val activityDataRepository: ActivityDataRepository = Injekt.get(),
    private val achievementsDatabase: AchievementsDatabase = Injekt.get(),
    private val userProfileManager: UserProfileManager = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val mangaHandler: MangaDatabaseHandler = Injekt.get(),
    private val animeHandler: AnimeDatabaseHandler = Injekt.get(),
) {

    /**
     * Backup all achievement data including:
     * - Achievements with progress
     * - User profile
     * - Activity log (last 365 days)
     * - Full statistics
     */
    suspend operator fun invoke(options: eu.kanade.tachiyomi.data.backup.create.BackupOptions): AchievementBackupData {
        // TODO: Port from Tadami - BackupOptions doesn't have achievements/stats flags yet.
        // For now, always backup achievements and stats.
        val achievements = backupAchievements()
        val userProfile = backupUserProfile()
        val activityLog = backupActivityLog()
        // TODO: Port from Tadami - stats backup requires StatsCalculations, WatchProgress,
        // and customStatus which don't exist in the aniyomi-fork yet.
        val stats = backupStats()

        return AchievementBackupData(
            achievements = achievements,
            userProfile = userProfile,
            activityLog = activityLog,
            stats = stats,
        )
    }

    /**
     * Backup all achievements with their progress
     */
    private suspend fun backupAchievements(): List<BackupAchievement> {
        return try {
            val achievements = achievementRepository.getAll().first()
            val progressList = achievementRepository.getAllProgress().first()

            val progressMap = progressList.associateBy { it.achievementId }

            achievements.map { achievement ->
                val progress = progressMap[achievement.id]
                BackupAchievement.fromAchievement(achievement, progress)
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error backing up achievements" }
            emptyList()
        }
    }

    /**
     * Backup user profile
     */
    private suspend fun backupUserProfile(): BackupUserProfile? {
        return try {
            val profile = userProfileManager.getCurrentProfile()
            BackupUserProfile.fromUserProfile(profile)
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error backing up user profile" }
            null
        }
    }

    /**
     * Backup activity log for the last 365 days with full metrics
     * Uses direct database access to get detailed data (chapters, episodes, app opens, etc.)
     * instead of simplified DayActivity model
     */
    private suspend fun backupActivityLog(): List<BackupDayActivity> {
        return try {
            val today = java.time.LocalDate.now()
            val startDate = today.minusDays(364) // 365 days including today
            val startDateStr = startDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            val endDateStr = today.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

            // Get raw activity log records from database with all metrics
            val records = achievementsDatabase.activityLogQueries
                .getActivityForDateRange(startDateStr, endDateStr)
                .executeAsList()

            logcat { "[BACKUP] Backing up ${records.size} activity log records" }

            records.map { record ->
                BackupDayActivity.fromDatabaseRecord(
                    date = java.time.LocalDate.parse(record.date),
                    level = record.level.toInt(),
                    type = tachiyomi.domain.achievement.model.ActivityType.entries.getOrElse(record.type.toInt()) {
                        tachiyomi.domain.achievement.model.ActivityType.APP_OPEN
                    },
                    chaptersRead = record.chapters_read.toInt(),
                    episodesWatched = record.episodes_watched.toInt(),
                    appOpens = record.app_opens.toInt(),
                    achievementsUnlocked = record.achievements_unlocked.toInt(),
                    durationMs = record.duration_ms,
                )
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error backing up activity log" }
            emptyList()
        }
    }

    /**
     * Backup full statistics from database.
     * Simplified version using repository APIs and direct query access.
     */
    private suspend fun backupStats(): BackupStats? {
        return try {
            val libraryManga = mangaRepository.getLibraryManga()
            val libraryAnime = animeRepository.getLibraryAnime()

            val mangaCompleted = libraryManga.count { it.manga.status == 2L }
            val animeCompleted = libraryAnime.count { it.anime.status == 2L }

            val chaptersRead = mangaHandler.awaitOneOrNull { chaptersQueries.getTotalReadChapterCount() }?.toInt() ?: 0
            val episodesWatched = animeHandler.awaitOneOrNull { episodesQueries.getTotalSeenEpisodeCount() }?.toInt() ?: 0

            val mangaReadDuration = mangaHandler.awaitOneOrNull { historyQueries.getReadDuration() } ?: 0L

            BackupStats(
                mangaLibraryCount = libraryManga.size,
                mangaCompletedCount = mangaCompleted,
                mangaTotalReadDuration = mangaReadDuration,
                chaptersReadCount = chaptersRead,
                animeLibraryCount = libraryAnime.size,
                animeCompletedCount = animeCompleted,
                episodesWatchedCount = episodesWatched,
            )
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error backing up stats" }
            null
        }
    }
}

/**
 * Container for all achievement backup data
 */
data class AchievementBackupData(
    val achievements: List<BackupAchievement>,
    val userProfile: BackupUserProfile?,
    val activityLog: List<BackupDayActivity>,
    val stats: BackupStats?,
)
