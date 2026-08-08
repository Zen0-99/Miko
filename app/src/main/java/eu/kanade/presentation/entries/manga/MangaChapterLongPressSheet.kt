package eu.kanade.presentation.entries.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.novel.EyeDown
import eu.kanade.presentation.entries.novel.EyeDots
import eu.kanade.presentation.entries.novel.EyeOffDown
import eu.kanade.presentation.entries.novel.EyeOffDots

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaChapterLongPressSheet(
    chapterTitle: String,
    onDismiss: () -> Unit,
    onOpenInWebView: () -> Unit,
    onMarkPreviousAsRead: () -> Unit,
    onMarkPreviousAsUnread: () -> Unit,
    onMarkRangeAsRead: () -> Unit,
    onMarkRangeAsUnread: () -> Unit,
    onHideChapter: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Text(
            text = chapterTitle,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            SheetOption(
                label = "Open in WebView",
                icon = Icons.Filled.Public,
                onClick = { onOpenInWebView(); onDismiss() },
            )
            SheetOption(
                label = "Mark previous as read",
                icon = EyeDown,
                onClick = { onMarkPreviousAsRead(); onDismiss() },
            )
            SheetOption(
                label = "Mark previous as unread",
                icon = EyeOffDown,
                onClick = { onMarkPreviousAsUnread(); onDismiss() },
            )
            SheetOption(
                label = "Mark range as read",
                icon = EyeDots,
                onClick = { onMarkRangeAsRead(); onDismiss() },
            )
            SheetOption(
                label = "Mark range as unread",
                icon = EyeOffDots,
                onClick = { onMarkRangeAsUnread(); onDismiss() },
            )
            if (onHideChapter != null) {
                SheetOption(
                    label = "Hide chapter",
                    icon = Icons.Outlined.VisibilityOff,
                    onClick = { onHideChapter(); onDismiss() },
                )
            }
        }
    }
}

@Composable
private fun SheetOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
