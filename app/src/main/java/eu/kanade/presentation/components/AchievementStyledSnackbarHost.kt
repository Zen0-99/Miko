package eu.kanade.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A SnackbarHost wrapper that:
 *  1. Sits above the bottom navigation bar (using [LocalHostScaffoldContentPadding]
 *     plus system navigation bar insets), regardless of whether the nav bar is
 *     floating or standard.
 *  2. Renders each snackbar with the achievement-banner visual language:
 *     rounded card, accent-colored border, soft accent shadow, themed surface,
 *     leading icon with a subtle glow.
 *
 * Drop-in replacement for [SnackbarHost] — pass the same [hostState].
 */
@Composable
fun AchievementStyledSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val hostPadding = LocalHostScaffoldContentPadding.current
    val navBarBottom = hostPadding?.calculateBottomPadding() ?: 0.dp
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .padding(bottom = navBarBottom + 12.dp),
        snackbar = { data -> AchievementSnackbar(data) },
    )
}

@Composable
private fun AchievementSnackbar(data: SnackbarData) {
    val visuals = data.visuals
    val icon = snackbarIcon(visuals)
    val iconTint = snackbarIconTint(visuals, MaterialTheme.colorScheme.primary)

    AnimatedVisibility(
        visible = true,
        enter = expandVertically(
            expandFrom = Alignment.Bottom,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        ) + fadeIn(animationSpec = tween(200)),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Bottom,
            animationSpec = tween(200),
        ) + fadeOut(animationSpec = tween(200)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = visuals.message,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                visuals.actionLabel?.let { label ->
                    TextButton(
                        onClick = { data.performAction() },
                        content = { Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                    )
                }
            }
        }
    }
}

@Composable
private fun snackbarIcon(visuals: SnackbarVisuals): ImageVector? {
    return when (visuals) {
        is AchievementSnackbarVisuals -> visuals.icon
        else -> when {
            visuals.message.contains("error", ignoreCase = true) ||
                visuals.message.contains("fail", ignoreCase = true) -> Icons.Filled.Error
            visuals.message.contains("warn", ignoreCase = true) -> Icons.Filled.Warning
            else -> Icons.Filled.Info
        }
    }
}

@Composable
private fun snackbarIconTint(visuals: SnackbarVisuals, accent: Color): Color {
    val isError = visuals is AchievementSnackbarVisuals && visuals.isError ||
        visuals.message.contains("error", ignoreCase = true) ||
        visuals.message.contains("fail", ignoreCase = true)
    val isWarn = visuals.message.contains("warn", ignoreCase = true)
    return when {
        isError -> MaterialTheme.colorScheme.error
        isWarn -> MaterialTheme.colorScheme.tertiary
        else -> accent
    }
}

/**
 * Optional custom [SnackbarVisuals] that lets a caller explicitly supply an icon
 * and success/failure intent instead of relying on message-string heuristics.
 */
data class AchievementSnackbarVisuals(
    override val message: String,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
    val icon: ImageVector? = Icons.Filled.Info,
    val isSuccess: Boolean = false,
    val isError: Boolean = false,
) : SnackbarVisuals
