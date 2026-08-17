package eu.kanade.presentation.achievement.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woowla.compose.icon.collections.tabler.Tabler
import com.woowla.compose.icon.collections.tabler.tabler.Filled
import com.woowla.compose.icon.collections.tabler.tabler.Outline
import com.woowla.compose.icon.collections.tabler.tabler.filled.Star
import com.woowla.compose.icon.collections.tabler.tabler.outline.QuestionMark
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementRarity
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generative achievement icon — a Compose-drawn hexagon whose content is
 * derived from [Achievement] metadata, themed via
 * `MaterialTheme.colorScheme.primary`.
 *
 * @param achievement The achievement to render
 * @param isUnlocked Whether the achievement is unlocked
 * @param modifier The modifier to be applied to the icon
 * @param size The size of the icon
 * @param showGlow Whether to draw the rarity glow behind the hexagon.
 *   Pass `false` when the caller already draws its own glow (e.g. the unlock
 *   banner) to avoid stacking.
 */
@Composable
fun AchievementIcon(
    achievement: Achievement,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    showGlow: Boolean = true,
) {
    val content = resolveIconContent(achievement)

    val primaryColor = MaterialTheme.colorScheme.primary
    val lockedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val strokeColor = if (isUnlocked) primaryColor else lockedColor

    val glowAlpha = if (showGlow && isUnlocked) achievement.rarity.glowAlpha() else 0f

    // Scale animation on unlock
    val unlockScale by animateFloatAsState(
        targetValue = if (isUnlocked) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "unlock_scale",
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(unlockScale),
        contentAlignment = Alignment.Center,
    ) {
        // Glow + hexagon stroke drawn behind content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val drawSize = this.size
                    val hexHeight = drawSize.width * 0.866f
                    val verticalOffset = (drawSize.height - hexHeight) / 2
                    val path = createHexagonPath(drawSize.width, verticalOffset)

                    // Rarity glow — radial wash behind the hexagon
                    if (glowAlpha > 0f) {
                        val glowRadius = drawSize.minDimension * 0.75f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = glowAlpha),
                                    primaryColor.copy(alpha = glowAlpha * 0.3f),
                                    Color.Transparent,
                                ),
                            ),
                            radius = glowRadius,
                            center = Offset(drawSize.width / 2f, drawSize.height / 2f),
                        )
                    }

                    // Optional faint fill for legendary/mythic unlocked
                    if (isUnlocked && (achievement.rarity == AchievementRarity.LEGENDARY ||
                            achievement.rarity == AchievementRarity.MYTHIC)
                    ) {
                        drawPath(
                            path = path,
                            color = primaryColor.copy(alpha = 0.06f),
                        )
                    }

                    // Hexagon stroke
                    val strokeWidthPx = drawSize.width * 0.052f
                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = Stroke(width = strokeWidthPx),
                    )
                },
        )

        // Content — glyph or secret mark
        val contentSize = size * 0.5f
        when (content) {
            is IconContent.Glyph -> {
                Icon(
                    imageVector = content.vector,
                    contentDescription = null,
                    tint = strokeColor,
                    modifier = Modifier.size(contentSize),
                )
            }
            is IconContent.Secret -> {
                Icon(
                    imageVector = Tabler.Outline.QuestionMark,
                    contentDescription = null,
                    tint = strokeColor,
                    modifier = Modifier.size(contentSize),
                )
            }
        }

        // Star pips — rarity rank insignia, below content
        val starCount = achievement.rarity.starCount()
        if (starCount > 0) {
            Row(
                modifier = Modifier.offset(y = size * 0.28f),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                repeat(starCount) {
                    Icon(
                        imageVector = Tabler.Filled.Star,
                        contentDescription = null,
                        tint = strokeColor,
                        modifier = Modifier.size(size * 0.10f),
                    )
                }
            }
        }
    }
}

/**
 * Hexagon shape for achievement icons — vertically centered in the bounds.
 */
private val HexagonShape = GenericShape { drawSize, _ ->
    val hexHeight = drawSize.width * 0.866f
    val verticalOffset = (drawSize.height - hexHeight) / 2
    val path = createHexagonPath(drawSize.width, verticalOffset)
    addPath(path)
}

/**
 * Creates a pointy-top hexagon path with optional vertical offset for centering.
 */
private fun createHexagonPath(width: Float, verticalOffset: Float = 0f): Path {
    val height = width * 0.866f // sqrt(3)/2
    val radius = width / 2
    val centerX = width / 2
    val centerY = height / 2 + verticalOffset

    return Path().apply {
        for (i in 0 until 6) {
            val angle = Math.PI / 3 * i - Math.PI / 2 // Start from top
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()

            if (i == 0) {
                moveTo(x, y)
            } else {
                lineTo(x, y)
            }
        }
        close()
    }
}

/**
 * Glow alpha for each rarity tier — common gets none, mythic gets the strongest.
 * Values are high enough to be clearly visible at 48dp.
 */
fun AchievementRarity.glowAlpha(): Float = when (this) {
    AchievementRarity.COMMON -> 0f
    AchievementRarity.UNCOMMON -> 0.15f
    AchievementRarity.RARE -> 0.25f
    AchievementRarity.EPIC -> 0.35f
    AchievementRarity.LEGENDARY -> 0.45f
    AchievementRarity.MYTHIC -> 0.55f
}

/**
 * Star pip count for each rarity tier — military-rank style.
 * Capped at 4 to avoid overflowing the hexagon edges.
 * Common shows no pips; mythic shows four.
 */
fun AchievementRarity.starCount(): Int = when (this) {
    AchievementRarity.COMMON -> 0
    AchievementRarity.UNCOMMON -> 1
    AchievementRarity.RARE -> 2
    AchievementRarity.EPIC -> 3
    AchievementRarity.LEGENDARY -> 4
    AchievementRarity.MYTHIC -> 4
}
