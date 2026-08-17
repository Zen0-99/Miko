package eu.kanade.presentation.entries.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

private val whitespaceLineRegex = Regex("[\\r\\n]{2,}", setOf(RegexOption.MULTILINE))

/**
 * Expandable description with "More"/"Less" buttons and tag hiding, matching the novel
 * detail screen's implementation exactly.
 *
 * - Collapsed: Shows 3 lines, clips overflow, gradient fade + "More" button at bottom-right
 * - Expanded: Shows full text + "Less" button + tags in FlowRow
 * - Tags only visible when expanded
 * - Uses animateContentSize for smooth height animation
 *
 * Extracted from [eu.kanade.presentation.entries.novel.components.ExpandableNovelDescription]
 * so manga and anime can reuse it.
 */
@Composable
fun ExpandableEntryDescription(
    defaultExpandState: Boolean,
    description: String?,
    tagsProvider: () -> List<String>?,
    accentColor: Color?,
    onTagSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(bottom = 8.dp),
    ) {
        val (expanded, onExpanded) = rememberSaveable {
            mutableStateOf(defaultExpandState)
        }
        var isOverflowing by remember { mutableStateOf(false) }
        var collapsedTextHeight by remember { mutableStateOf<Int?>(null) }
        var hasInitialized by rememberSaveable { mutableStateOf(false) }
        // Skip animation on first composition (including restoration from saver)
        // to prevent the description from flashing during collapse when returning
        // from the reader. The animation is enabled only after the height constraint
        // has been applied for one full frame without animation, so the initial
        // sizing is instant but subsequent expand/collapse toggles animate.
        var hasLaidOut by remember { mutableStateOf(false) }
        val desc = description.takeIf { !it.isNullOrBlank() }
            ?: stringResource(MR.strings.description_placeholder)
        val trimmedDescription = remember(desc) {
            desc.replace(whitespaceLineRegex, "\n").trimEnd()
        }
        val descColor = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA)
        val accent = accentColor ?: MaterialTheme.colorScheme.primary

        Text(
            text = stringResource(MR.strings.about_this_novel),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(top = 12.dp, bottom = 4.dp)
                .padding(horizontal = 16.dp),
        )

        val canExpand = isOverflowing

        // Enable animation one frame after the height constraint is first applied,
        // so the initial collapse (including state restoration) is instant.
        LaunchedEffect(collapsedTextHeight) {
            if (collapsedTextHeight != null && !hasLaidOut) {
                withFrameNanos { }
                hasLaidOut = true
            }
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clipToBounds()
                .then(
                    if (hasInitialized && hasLaidOut) {
                        Modifier.animateContentSize(animationSpec = spring(dampingRatio = 0.85f, stiffness = 350f))
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (canExpand) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onExpanded(!expanded) }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = trimmedDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = descColor,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.fillMaxWidth(),
                    onTextLayout = { textLayoutResult ->
                        isOverflowing = if (expanded) {
                            textLayoutResult.lineCount > 3
                        } else {
                            textLayoutResult.hasVisualOverflow || textLayoutResult.lineCount > 3
                        }
                        if (textLayoutResult.lineCount >= 3) {
                            collapsedTextHeight = textLayoutResult.getLineBottom(2).toInt()
                        } else {
                            collapsedTextHeight = textLayoutResult.size.height
                        }
                        hasInitialized = true
                    },
                )

                if (expanded && canExpand) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { onExpanded(false) },
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(MR.strings.manga_info_collapse),
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent,
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }

                    val tags = tagsProvider()
                    if (!tags.isNullOrEmpty()) {
                        FlowRow(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            tags.forEach { tag ->
                                EntryTagChip(
                                    text = tag,
                                    accentColor = accentColor,
                                    onClick = { onTagSearch(tag) },
                                )
                            }
                        }
                    }
                }
            }

            if (!expanded && canExpand) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(
                            brush = Brush.horizontalGradient(
                                0f to MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                0.5f to MaterialTheme.colorScheme.background,
                            ),
                        )
                        .padding(start = 24.dp)
                        .clickable { onExpanded(true) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(MR.strings.manga_info_expand),
                        style = MaterialTheme.typography.bodyMedium,
                        color = accent,
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
    }
}
