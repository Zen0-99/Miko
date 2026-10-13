package miko.ui.achievements

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.data.achievement.loader.SharedAchievementLoader
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.domain.achievement.model.UserPoints
import tachiyomi.domain.achievement.model.YearMonth
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import kotlin.time.Clock

/**
 * Port of Android's `AchievementScreenModel` — same combine over
 * achievements/progress/points/category/activity, with the Injekt
 * lookups replaced by constructor params (our shared convention).
 */
class AchievementScreenModel(
    private val repository: AchievementRepository,
    private val loader: SharedAchievementLoader,
    private val pointsManager: PointsManager,
    private val activityDataRepository: ActivityDataRepository,
    private val scope: CoroutineScope = CoroutineScope(Job() + Dispatchers.Default),
) {

    private val _state = MutableStateFlow<AchievementScreenState>(AchievementScreenState.Loading)
    val state: StateFlow<AchievementScreenState> = _state

    // Separate state for category to avoid being overwritten by combine
    private val _categoryState = MutableStateFlow(AchievementCategory.BOTH)
    val categoryState: StateFlow<AchievementCategory> = _categoryState

    init {
        scope.launch {
            combine(
                repository.getAll(),
                repository.getAllProgress(),
                pointsManager.subscribeToPoints(),
                categoryState,
                activityDataRepository.getActivityData(365),
            ) { achievements, progress, userPoints, selectedCategory, activityData ->
                val currentStats = activityDataRepository.getCurrentMonthStats()
                val previousStats = activityDataRepository.getPreviousMonthStats()
                val yearlyStats = activityDataRepository.getLastTwelveMonthsStats()

                AchievementScreenState.Success(
                    achievements = achievements,
                    progress = progress.associateBy { it.achievementId },
                    userPoints = userPoints,
                    selectedCategory = selectedCategory,
                    activityData = activityData,
                    yearlyStats = yearlyStats,
                    currentMonthStats = currentStats,
                    previousMonthStats = previousStats,
                )
            }.catch { error ->
                error.printStackTrace()
            }.collect { state ->
                _state.update { state }
            }
        }
    }

    fun onCategoryChanged(category: AchievementCategory) {
        _categoryState.value = category
    }

    fun refreshAchievements() {
        scope.launch {
            loader.loadAchievements()
        }
    }

    fun onAchievementClick(achievement: Achievement) {
        _state.update {
            when (it) {
                AchievementScreenState.Loading -> it
                is AchievementScreenState.Success -> it.copy(selectedAchievement = achievement)
            }
        }
    }

    fun onDialogDismiss() {
        _state.update {
            when (it) {
                AchievementScreenState.Loading -> it
                is AchievementScreenState.Success -> it.copy(selectedAchievement = null)
            }
        }
    }

    fun dispose() = scope.cancel()
}

@Immutable
data class UserLevelInfo(
    val level: Int,
    val rankName: String,
    val currentXp: Int,
    val requiredXpForNext: Int,
    val progressFraction: Float,
)

@Immutable
sealed interface AchievementScreenState {
    @Immutable
    data object Loading : AchievementScreenState

    @Immutable
    data class Success(
        val achievements: List<Achievement> = emptyList(),
        val progress: Map<String, AchievementProgress> = emptyMap(),
        val userPoints: UserPoints = UserPoints(),
        val selectedCategory: AchievementCategory = AchievementCategory.BOTH,
        val selectedAchievement: Achievement? = null,
        val activityData: List<DayActivity> = emptyList(),
        val yearlyStats: List<Pair<YearMonth, MonthStats>> = emptyList(),
        val currentMonthStats: MonthStats = MonthStats(0, 0, 0, 0),
        val previousMonthStats: MonthStats = MonthStats(0, 0, 0, 0),
    ) : AchievementScreenState {
        val levelInfo: UserLevelInfo
            get() = calculateLevelInfo(totalPoints)

        private fun calculateLevelInfo(totalPoints: Int): UserLevelInfo {
            var points = totalPoints
            var currentLevel = 1
            var requiredXpForNext = 1000

            while (points >= requiredXpForNext) {
                points -= requiredXpForNext
                currentLevel++
                requiredXpForNext = 1000 + (currentLevel - 1) * 500
            }

            val rankName = when (currentLevel) {
                in 1..5 -> "Novice Reader"
                in 6..10 -> "Avid Reader"
                in 11..15 -> "Ranobe Master"
                in 16..20 -> "Grandmaster"
                else -> "Legendary Scholar"
            }

            return UserLevelInfo(
                level = currentLevel,
                rankName = rankName,
                currentXp = points,
                requiredXpForNext = requiredXpForNext,
                progressFraction = points.toFloat() / requiredXpForNext,
            )
        }

        val filteredAchievements: List<Achievement>
            get() = when (selectedCategory) {
                AchievementCategory.BOTH -> achievements
                AchievementCategory.SHOWS -> achievements.filter {
                    it.category == AchievementCategory.SHOWS ||
                        it.category == AchievementCategory.BOTH
                }
                AchievementCategory.MANGA -> achievements.filter {
                    it.category == AchievementCategory.MANGA ||
                        it.category == AchievementCategory.BOTH
                }
                AchievementCategory.NOVEL -> achievements.filter {
                    it.category == AchievementCategory.NOVEL ||
                        it.category == AchievementCategory.BOTH
                }
                AchievementCategory.SECRET -> achievements.filter {
                    it.category == AchievementCategory.SECRET || it.isSecret
                }
            }

        val totalPoints: Int
            get() = userPoints.totalPoints

        val unlockedCount: Int
            get() = progress.count { it.value.isUnlocked }

        val totalCount: Int
            get() = achievements.size

        val currentStreak: Int
            get() = calculateCurrentStreak(activityData)

        private fun calculateCurrentStreak(activities: List<DayActivity>): Int {
            var streak = 0
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            for (i in 0 until activities.size) {
                val date = today.minus(i, DateTimeUnit.DAY)
                val activity = activities.find { it.date == date }

                if (activity != null && activity.level > 0) {
                    streak++
                } else {
                    break
                }
            }

            return streak
        }
    }
}
