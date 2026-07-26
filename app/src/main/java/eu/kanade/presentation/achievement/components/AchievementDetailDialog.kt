package eu.kanade.presentation.achievement.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.achievement.utils.AchievementRevealHelper
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.theme.achievementLabelColor
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Aurora-themed Achievement Detail Dialog.
 *
 * Layout:
 * - Header: badge on left, title + XP on right (like the list card detail view)
 * - Description
 * - Progress section: goal text, x/y left + % right, progress bar below
 * - Unlock date (if unlocked)
 * - Close button
 */
@Composable
fun AchievementDetailDialog(
    achievement: Achievement,
    progress: AchievementProgress?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    unlockableManager: UnlockableManager = Injekt.get(),
) {
    val isUnlocked = progress?.isUnlocked == true
    val scrollState = rememberScrollState()

    val glowIntensity by animateFloatAsState(
        targetValue = if (isUnlocked) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "glow_intensity",
    )

    AdaptiveSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f),
    ) {
        val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        val backgroundColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
        val glowColor = MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(surfaceColor, backgroundColor),
                    ),
                )
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.15f * glowIntensity),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2, 0f),
                            radius = size.width * 0.8f,
                        ),
                    )
                }
                .padding(top = 24.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header: badge on left, title + XP on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Badge with glow
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isUnlocked) {
                            val primaryGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            val secondaryGlow = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .drawBehind {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(primaryGlow, secondaryGlow, Color.Transparent),
                                            ),
                                            radius = size.minDimension / 2,
                                        )
                                    },
                            )
                        }
                        if (achievement.isHidden && !isUnlocked) {
                            HiddenBadgeLarge()
                        } else {
                            AchievementIcon(
                                achievement = achievement,
                                isUnlocked = isUnlocked,
                                modifier = Modifier.size(64.dp),
                                size = 64.dp,
                                useHexagonShape = true,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Title + XP
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = AchievementRevealHelper.getDisplayName(achievement, progress),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isUnlocked) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (achievement.points > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(AYMR.strings.achievement_points, achievement.points),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                // Description
                val displayDesc = if (achievement.isHidden && !isUnlocked) {
                    AchievementRevealHelper.getDisplayDescription(
                        achievement = achievement,
                        progress = progress,
                        vaguePrefix = stringResource(AYMR.strings.achievement_hint_vague_prefix),
                        directPrefix = stringResource(AYMR.strings.achievement_hint_direct_prefix),
                        obviousPrefix = stringResource(AYMR.strings.achievement_hint_obvious_prefix),
                        cluePrefix = stringResource(AYMR.strings.achievement_clue_prefix),
                    )
                } else {
                    achievement.description
                }

                if (!displayDesc.isNullOrBlank()) {
                    Text(
                        text = displayDesc,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp,
                    )
                }

                // Progress section (only if locked and has progress)
                if (progress != null && !progress.isUnlocked) {
                    ProgressSection(progress, achievement)
                }

                // Unlock date
                val unlockedAt = progress?.unlockedAt
                if (unlockedAt != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    AYMR.strings.achievement_unlocked_at,
                                    formatDate(unlockedAt),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Progress section with goal text, x/y + % layout, and progress bar.
 */
@Composable
private fun ProgressSection(
    progress: AchievementProgress,
    achievement: Achievement,
) {
    val max = achievement.threshold ?: progress.maxProgress
    val progressFraction = (progress.progress.toFloat() / max).coerceIn(0f, 1f)
    val percentText = "${(progressFraction * 100).toInt()}%"

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Progress label
        Text(
            text = stringResource(AYMR.strings.achievement_progress_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            letterSpacing = 2.sp,
        )

        // x/y left, % right — in-line
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${progress.progress} / $max",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = percentText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.05f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                            ),
                        ),
                    )
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
    }
}

/**
 * Hidden badge large variant with scanline effect
 */
@Composable
private fun HiddenBadgeLarge(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = achievementLabelColor(),
            modifier = Modifier.size(32.dp),
        )

        Column(
            modifier = Modifier.matchParentSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(8) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .alpha(0.3f)
                        .background(
                            if (index % 2 == 0) {
                                Color.White.copy(alpha = 0.05f)
                            } else {
                                Color.Transparent
                            },
                        ),
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
}
