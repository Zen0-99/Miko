package eu.kanade.presentation.achievement.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AchievementColors
import eu.kanade.presentation.theme.achievementLabelColor
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Aurora-themed statistics comparison card with glassmorphism effect
 * Compares current month stats with previous month
 */
@Composable
fun AchievementStatsComparison(
    currentMonth: MonthStats,
    previousMonth: MonthStats,
    modifier: Modifier = Modifier,
) {
    val timeStrings = achievementTimeStrings()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header
        Text(
            text = stringResource(AYMR.strings.achievement_comparison_title).uppercase(),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )

        // Single surface — matches ExtensionCard style (no double border)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 1.dp,
        ) {
            val dividerColor = MaterialTheme.colorScheme.outlineVariant
            // Draw grid dividers on the background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // Horizontal divider
                        drawLine(
                            color = dividerColor,
                            start = Offset(0f, size.height * 0.5f),
                            end = Offset(size.width, size.height * 0.5f),
                            strokeWidth = 1.dp.toPx(),
                        )
                        // Vertical divider
                        drawLine(
                            color = dividerColor,
                            start = Offset(size.width * 0.5f, 0f),
                            end = Offset(size.width * 0.5f, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    .padding(8.dp),
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatItem(
                            label = stringResource(AYMR.strings.achievement_stat_chapters_read),
                            currentValue = currentMonth.chaptersRead,
                            previousValue = previousMonth.chaptersRead,
                            modifier = Modifier.weight(1f),
                        )
                        StatItem(
                            label = stringResource(AYMR.strings.achievement_stat_episodes_watched),
                            currentValue = currentMonth.episodesWatched,
                            previousValue = previousMonth.episodesWatched,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatItem(
                            label = stringResource(AYMR.strings.achievement_stat_app_time),
                            currentValue = currentMonth.timeInAppMinutes,
                            previousValue = previousMonth.timeInAppMinutes,
                            isTimeValue = true,
                            timeStrings = timeStrings,
                            modifier = Modifier.weight(1f),
                        )
                        StatItem(
                            label = stringResource(AYMR.strings.achievement_stat_unlocked),
                            currentValue = currentMonth.achievementsUnlocked,
                            previousValue = previousMonth.achievementsUnlocked,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual stat item with icon, value and change indicator
 */
@Composable
private fun StatItem(
    label: String,
    currentValue: Int,
    previousValue: Int,
    isTimeValue: Boolean = false,
    timeStrings: AchievementTimeStrings? = null,
    modifier: Modifier = Modifier,
) {
    val percentageChange = if (previousValue > 0) {
        ((currentValue - previousValue).toFloat() / previousValue * 100).toInt()
    } else if (currentValue > 0) {
        100
    } else {
        0
    }

    val isIncrease = currentValue >= previousValue
    val changeColor = if (isIncrease) AchievementColors.Success else MaterialTheme.colorScheme.error

    val valueString = if (isTimeValue) {
        val strings = requireNotNull(timeStrings) {
            "timeStrings must be provided for time-based stats"
        }
        val hours = currentValue / 60
        val minutes = currentValue % 60
        formatAchievementTimeMinutes(
            currentValue,
            hoursMinutesText = stringResource(strings.hoursMinutes, hours, minutes),
            hoursText = stringResource(strings.hours, hours),
            minutesText = stringResource(strings.minutes, minutes),
        )
    } else {
        currentValue.toString()
    }

    Column(
        modifier = modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            color = achievementLabelColor(),
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = valueString,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (previousValue > 0 || currentValue > 0) {
                val prefix = if (isIncrease) "+" else "-"
                Text(
                    text = "$prefix${kotlin.math.abs(percentageChange)}%",
                    color = changeColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(changeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AchievementStatsComparisonPreview() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        AchievementStatsComparison(
            currentMonth = MonthStats(
                chaptersRead = 127,
                episodesWatched = 45,
                timeInAppMinutes = 2340,
                achievementsUnlocked = 8,
            ),
            previousMonth = MonthStats(
                chaptersRead = 98,
                episodesWatched = 62,
                timeInAppMinutes = 1890,
                achievementsUnlocked = 5,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AchievementStatsComparisonLightPreview() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        AchievementStatsComparison(
            currentMonth = MonthStats(
                chaptersRead = 85,
                episodesWatched = 32,
                timeInAppMinutes = 1560,
                achievementsUnlocked = 12,
            ),
            previousMonth = MonthStats(
                chaptersRead = 120,
                episodesWatched = 28,
                timeInAppMinutes = 1800,
                achievementsUnlocked = 8,
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun AchievementStatsComparisonUniformPreview() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        AchievementStatsComparison(
            currentMonth = MonthStats(
                chaptersRead = 9999,
                episodesWatched = 1,
                timeInAppMinutes = 9999,
                achievementsUnlocked = 999,
            ),
            previousMonth = MonthStats(
                chaptersRead = 5000,
                episodesWatched = 1,
                timeInAppMinutes = 5000,
                achievementsUnlocked = 500,
            ),
        )
    }
}
