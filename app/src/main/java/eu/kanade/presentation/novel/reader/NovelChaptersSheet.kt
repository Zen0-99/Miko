package eu.kanade.presentation.novel.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.domain.items.chapter.model.NovelChapter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelChaptersSheet(
    chapters: List<NovelChapter>,
    currentChapterId: Long?,
    onChapterClick: (NovelChapter) -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color? = null,
    renderedChapterIds: Set<Long> = emptySet(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // Scroll to current chapter when sheet opens — centers it like Miko.
    LaunchedEffect(currentChapterId, chapters) {
        if (currentChapterId != null && chapters.isNotEmpty()) {
            val index = chapters.indexOfFirst { it.id == currentChapterId }
            if (index >= 0) {
                // Offset to center the current chapter roughly in the middle
                listState.scrollToItem(index)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // No drag handle — sheet still drags but no visual handle bar.
        dragHandle = null,
    ) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumn(
            state = listState,
        ) {
            items(
                items = chapters,
                key = { it.id },
            ) { chapter ->
                val isSelected = chapter.id == currentChapterId
                // Gray out read chapters (35% alpha), unread slightly dimmed (80%),
                // current chapter full opacity — matching Miko's approach.
                val itemAlpha = when {
                    isSelected -> 1.0f
                    chapter.read -> 0.35f
                    else -> 0.8f
                }
                ListItem(
                    headlineContent = {
                        Text(
                            text = chapter.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isSelected) {
                                accentColor ?: MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(itemAlpha)
                        .clickable { onChapterClick(chapter) },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Read-only rendered-audio glyph (05-04 SD-3,
                            // UI-SPEC optional surface) — decorative; the
                            // row's own semantics carry the description.
                            if (chapter.id in renderedChapterIds) {
                                Icon(
                                    imageVector = Icons.Filled.Headphones,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = accentColor ?: MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = "Ch. ${chapter.chapterNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) {
                                    accentColor ?: MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}
