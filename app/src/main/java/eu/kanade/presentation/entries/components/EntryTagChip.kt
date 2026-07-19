package eu.kanade.presentation.entries.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Miko-style HSL-themed tag chip: uses the accent hue with adjusted
 * saturation/luminance for the container, and high-contrast label color.
 * No border — matching Miko's chipStrokeWidth=0dp.
 *
 * Extracted from [eu.kanade.presentation.entries.novel.components.ExpandableNovelDescription]'s
 * private `NovelTagChip` so manga and anime can reuse it.
 */
@Composable
fun EntryTagChip(
    text: String,
    accentColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val hsl = remember(accent) {
        FloatArray(3).also {
            androidx.core.graphics.ColorUtils.colorToHSL(accent.toArgb(), it)
        }
    }

    val containerColor = Color(
        androidx.core.graphics.ColorUtils.HSLToColor(
            floatArrayOf(
                hsl[0],
                (hsl[1] * 0.6f).coerceIn(0f, 1f),
                if (isDark) 0.225f else 0.85f,
            ),
        ),
    ).copy(alpha = 0.78f)

    val labelColor = Color(
        androidx.core.graphics.ColorUtils.HSLToColor(
            floatArrayOf(
                hsl[0],
                hsl[1],
                if (isDark) 0.945f else 0.175f,
            ),
        ),
    )

    AssistChip(
        onClick = onClick,
        label = { Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
        ),
        border = null,
        modifier = modifier.padding(vertical = 1.dp),
    )
}
