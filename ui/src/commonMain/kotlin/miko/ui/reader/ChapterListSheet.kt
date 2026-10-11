package miko.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * In-reader chapter picker — Android's reader chapters sheet: a bottom
 * sheet of all items with read chapters dimmed and the current one
 * highlighted. Shared by the novel and manga readers.
 */
@Composable
fun ChapterListSheet(
    chapters: List<ChapterRow>,
    currentUrl: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn {
            items(chapters, key = { it.url }) { chapter ->
                ChapterSheetRow(
                    chapter = chapter,
                    isCurrent = chapter.url == currentUrl,
                    onClick = {
                        onDismiss()
                        onSelect(chapter.url)
                    },
                )
            }
        }
    }
}

@Composable
private fun ChapterSheetRow(
    chapter: ChapterRow,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = chapter.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.Bold else null,
            color = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                chapter.done -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
    HorizontalDivider()
}
