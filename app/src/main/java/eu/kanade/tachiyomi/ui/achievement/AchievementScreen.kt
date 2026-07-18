package eu.kanade.tachiyomi.ui.achievement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementRarity
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

object AchievementScreen : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { AchievementScreenModel() }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = {
                AppBar(
                    title = "Achievements",
                    navigateUp = navigator::pop,
                )
            },
        ) { padding ->
            if (state.isLoading) {
                LoadingScreen()
                return@Scaffold
            }
            if (state.achievements.isEmpty()) {
                EmptyScreen(message = "No achievements available")
                return@Scaffold
            }

            val filtered = if (state.selectedCategory != null) {
                state.achievements.filter { it.category == state.selectedCategory }
            } else {
                state.achievements
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Profile header
                item {
                    ProfileHeader(
                        level = state.userProfile?.level ?: 1,
                        currentXp = state.userProfile?.currentXp ?: 0,
                        xpToNext = state.userProfile?.xpToNextLevel ?: 100,
                        totalXp = state.userProfile?.totalXp ?: 0,
                        unlockedCount = state.unlockedCount,
                        totalCount = state.totalCount,
                    )
                }

                // Achievement list
                filtered.forEach { achievement ->
                    item(key = achievement.id) {
                        val progress = state.progress[achievement.id]
                        AchievementCard(achievement, progress)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    level: Int,
    currentXp: Int,
    xpToNext: Int,
    totalXp: Int,
    unlockedCount: Int,
    totalCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Level $level",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$unlockedCount / $totalCount unlocked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            LinearProgressIndicator(
                progress = { if (xpToNext > 0) currentXp.toFloat() / xpToNext else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "$currentXp / $xpToNext XP to next level ($totalXp total)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: Achievement,
    progress: AchievementProgress?,
) {
    val isUnlocked = progress?.isUnlocked ?: false
    val currentProgress = progress?.progress ?: 0
    val maxProgress = achievement.threshold ?: progress?.maxProgress ?: 100

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Badge icon placeholder
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = if (isUnlocked) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = rarityEmoji(achievement.rarity),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (achievement.isSecret && !isUnlocked) "???" else achievement.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (achievement.isSecret && !isUnlocked) {
                        achievement.hint ?: "Hidden achievement"
                    } else {
                        achievement.description ?: ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!isUnlocked && maxProgress > 1) {
                    LinearProgressIndicator(
                        progress = {
                            (currentProgress.toFloat() / maxProgress).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "$currentProgress / $maxProgress",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                } else if (isUnlocked) {
                    Text(
                        text = "Unlocked • +${achievement.points} XP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

private fun rarityEmoji(rarity: AchievementRarity): String {
    return when (rarity) {
        AchievementRarity.COMMON -> "★"
        AchievementRarity.UNCOMMON -> "★★"
        AchievementRarity.RARE -> "★★★"
        AchievementRarity.EPIC -> "★★★★"
        AchievementRarity.LEGENDARY -> "👑"
    }
}
