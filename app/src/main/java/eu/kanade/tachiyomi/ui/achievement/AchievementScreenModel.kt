package eu.kanade.tachiyomi.ui.achievement

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.achievement.AchievementRepository
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.UserProfile
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Screen model for the achievements screen.
 */
class AchievementScreenModel(
    private val repository: AchievementRepository = Injekt.get(),
) : StateScreenModel<AchievementScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val achievements: List<Achievement> = emptyList(),
        val progress: Map<String, AchievementProgress> = emptyMap(),
        val userProfile: UserProfile? = null,
        val unlockedCount: Int = 0,
        val totalCount: Int = 0,
        val selectedCategory: tachiyomi.domain.achievement.model.AchievementCategory? = null,
    )

    init {
        loadAchievements()
    }

    fun loadAchievements() {
        screenModelScope.launch {
            val achievements = repository.getAllAchievements()
            val allProgress = repository.getAllProgress()
            val profile = repository.getUserProfile()
            val unlocked = allProgress.count { it.isUnlocked }

            mutableState.update {
                State(
                    isLoading = false,
                    achievements = achievements,
                    progress = allProgress.associateBy { it.achievementId },
                    userProfile = profile,
                    unlockedCount = unlocked,
                    totalCount = achievements.size,
                    selectedCategory = null,
                )
            }
        }
    }

    fun filterByCategory(category: tachiyomi.domain.achievement.model.AchievementCategory?) {
        mutableState.update { it.copy(selectedCategory = category) }
    }
}
