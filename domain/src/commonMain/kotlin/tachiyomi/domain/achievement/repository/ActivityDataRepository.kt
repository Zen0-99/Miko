package tachiyomi.domain.achievement.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.domain.achievement.model.YearMonth

interface ActivityDataRepository {
    fun getActivityData(days: Int = 365): Flow<List<DayActivity>>
    suspend fun getMonthStats(year: Int, month: Int): MonthStats
    suspend fun getCurrentMonthStats(): MonthStats
    suspend fun getPreviousMonthStats(): MonthStats
    suspend fun recordReading(id: Long, chaptersCount: Int, durationMs: Long = 0)
    suspend fun recordWatching(id: Long, episodesCount: Int, durationMs: Long = 0)
    suspend fun recordAppSession(durationMs: Long)
    suspend fun recordAppOpen()
    suspend fun recordAchievementUnlock()
    suspend fun getLastTwelveMonthsStats(): List<Pair<YearMonth, MonthStats>>

    // Backup/Restore support
    suspend fun upsertActivityData(
        date: LocalDate,
        chaptersRead: Int,
        episodesWatched: Int,
        appOpens: Int,
        achievementsUnlocked: Int,
        durationMs: Long,
    )
    suspend fun deleteAllActivityData()
}
