package eu.kanade.tachiyomi.ui.achievement

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.achievement.components.AchievementBannerManager
import eu.kanade.presentation.achievement.screenmodel.AchievementScreenModel
import eu.kanade.presentation.achievement.ui.AchievementScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.main.MainActivity
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementRarity
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

data object AchievementsTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            return TabOptions(
                index = 9u,
                title = stringResource(AYMR.strings.label_achievements),
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current

        val screenModel = rememberScreenModel { AchievementScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()

        AchievementScreen(
            state = state,
            onClickBack = { /* Handled by navigation */ },
            onCategoryChanged = { category -> screenModel.onCategoryChanged(category) },
            onAchievementClick = { achievement ->
                screenModel.onAchievementClick(achievement)
            },
            onDialogDismiss = {
                screenModel.onDialogDismiss()
            },
            onLocaleChanged = {
                screenModel.refreshAchievements()
            },
            onTestAchievement = {
                AchievementBannerManager.showAchievement(
                    Achievement(
                        id = "test_popup",
                        type = AchievementType.FEATURE_BASED,
                        category = AchievementCategory.SECRET,
                        points = 25,
                        title = "Test Achievement",
                        description = "This is a fake achievement for visual testing.",
                        badgeIcon = "ic_badge_default",
                        rarity = AchievementRarity.RARE,
                    ),
                )
            },
            modifier = Modifier.fillMaxSize(),
        )

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
