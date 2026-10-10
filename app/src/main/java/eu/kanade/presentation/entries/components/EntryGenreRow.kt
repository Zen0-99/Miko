package eu.kanade.presentation.entries.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.formatGenreLabel
import eu.kanade.presentation.util.sortGenresByPrevalence

/**
 * Text-led genre row: comma/dot-separated genres rendered as plain text with
 * per-genre click targets (MAL-style). No pill containers — the genres read as
 * a quiet line of metadata in the accent hue, separated by muted dots.
 */
@Composable
fun EntryGenreRow(
    pills: List<String>,
    onPillClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
) {
    if (pills.isEmpty()) return
    val orderedPills = remember(pills) { sortGenresByPrevalence(pills) }
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val hsl = remember(accent) {
        FloatArray(3).also {
            androidx.core.graphics.ColorUtils.colorToHSL(accent.toArgb(), it)
        }
    }
    // Same accent-hue derivation EntryTagChip used for its label — readable
    // accent-tinted text on both themes.
    val genreColor = Color(
        androidx.core.graphics.ColorUtils.HSLToColor(
            floatArrayOf(
                hsl[0],
                hsl[1],
                if (isDark) 0.945f else 0.175f,
            ),
        ),
    )
    val separatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    val text = remember(orderedPills, genreColor, separatorColor, onPillClick) {
        buildAnnotatedString {
            orderedPills.forEachIndexed { index, pill ->
                if (index > 0) {
                    withStyle(SpanStyle(color = separatorColor)) {
                        append(" · ")
                    }
                }
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "genre",
                        linkInteractionListener = { onPillClick(pill) },
                    ),
                ) {
                    withStyle(SpanStyle(color = genreColor)) {
                        append(formatGenreLabel(pill))
                    }
                }
            }
        }
    }

    // Text stays centered while it fits; once it overflows the viewport it
    // scrolls horizontally instead of ellipsizing. The scroll lives on the
    // Text (measured unbounded → content width) and the Box centers it when
    // it's narrower than the viewport — the design doesn't change, it just
    // stops truncating.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}
