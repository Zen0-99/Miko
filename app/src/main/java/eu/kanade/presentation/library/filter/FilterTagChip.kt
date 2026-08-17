package eu.kanade.presentation.library.filter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * A single filter chip that cycles through TriState (Disabled → Is → Not).
 * Visual states:
 * - DISABLED: outlined chip, normal text
 * - ENABLED_IS: filled with primary, check icon
 * - ENABLED_NOT: outlined with error border, block icon
 */
@Composable
fun FilterTagChip(
    label: String,
    state: TriState,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val isActive = state != TriState.DISABLED

    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surface
        state == TriState.ENABLED_IS -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
        state == TriState.ENABLED_NOT -> MaterialTheme.colorScheme.error
        state == TriState.ENABLED_IS -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        state == TriState.ENABLED_IS -> MaterialTheme.colorScheme.onPrimary
        state == TriState.ENABLED_NOT -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.5f),
            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.5f),
        ) {
            Icon(
                imageVector = when (state) {
                    TriState.ENABLED_IS -> Icons.Outlined.Check
                    TriState.ENABLED_NOT -> Icons.Outlined.Block
                    TriState.DISABLED -> Icons.Outlined.Check
                },
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
