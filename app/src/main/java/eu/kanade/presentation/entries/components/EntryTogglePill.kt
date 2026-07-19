package eu.kanade.presentation.entries.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A pill-shaped toggle button that morphs between checked and unchecked states.
 * When checked, fills with an HSL-derived tag color; when unchecked, shows an
 * outlined border. Crossfades icon and text between states.
 *
 * Extracted from [eu.kanade.presentation.entries.novel.components.NovelActionRow]'s
 * private `NovelTogglePill` so manga and anime can reuse it.
 */
@Composable
fun EntryTogglePill(
    checked: Boolean,
    checkedText: String,
    uncheckedText: String,
    checkedIcon: ImageVector,
    uncheckedIcon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val hsl = remember(accentColor) {
        FloatArray(3).also {
            androidx.core.graphics.ColorUtils.colorToHSL(accentColor.toArgb(), it)
        }
    }
    val tagFillColor = Color(
        androidx.core.graphics.ColorUtils.HSLToColor(
            floatArrayOf(
                hsl[0],
                (hsl[1] * 0.6f).coerceIn(0f, 1f),
                if (isDark) 0.225f else 0.85f,
            ),
        ),
    ).copy(alpha = 0.78f)

    val containerColor by animateColorAsState(
        targetValue = if (checked) tagFillColor else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "pillContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) Color.Transparent else accentColor.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 300),
        label = "pillBorder",
    )

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = Color.White,
        ),
        border = BorderStroke(1.dp, borderColor),
    ) {
        AnimatedContent(
            targetState = checked,
            transitionSpec = {
                fadeIn(tween(250)) togetherWith fadeOut(tween(200))
            },
            modifier = Modifier.size(20.dp),
        ) { isChecked ->
            Icon(
                imageVector = if (isChecked) checkedIcon else uncheckedIcon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedContent(
            targetState = checked,
            transitionSpec = {
                fadeIn(tween(250)) togetherWith fadeOut(tween(200))
            },
        ) { isChecked ->
            Text(
                text = if (isChecked) checkedText else uncheckedText,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
