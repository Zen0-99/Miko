package eu.kanade.presentation.entries.novel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelChapterLongPressSheet(
    chapterTitle: String,
    onDismiss: () -> Unit,
    onOpenInWebView: () -> Unit,
    onMarkPreviousAsRead: () -> Unit,
    onMarkPreviousAsUnread: () -> Unit,
    onMarkRangeAsRead: () -> Unit,
    onMarkRangeAsUnread: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = chapterTitle,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            ListItem(
                headlineContent = { Text("Open in WebView") },
                leadingContent = { Icon(Icons.Filled.Public, contentDescription = null) },
                modifier = Modifier.clickable { onOpenInWebView(); onDismiss() },
            )
            ListItem(
                headlineContent = { Text("Mark previous as read") },
                leadingContent = { Icon(Icons.Filled.DoneAll, contentDescription = null) },
                modifier = Modifier.clickable { onMarkPreviousAsRead(); onDismiss() },
            )
            ListItem(
                headlineContent = { Text("Mark previous as unread") },
                leadingContent = { Icon(Icons.Filled.Undo, contentDescription = null) },
                modifier = Modifier.clickable { onMarkPreviousAsUnread(); onDismiss() },
            )
            ListItem(
                headlineContent = { Text("Mark range as read") },
                leadingContent = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                modifier = Modifier.clickable { onMarkRangeAsRead(); onDismiss() },
            )
            ListItem(
                headlineContent = { Text("Mark range as unread") },
                leadingContent = { Icon(Icons.Filled.RemoveDone, contentDescription = null) },
                modifier = Modifier.clickable { onMarkRangeAsUnread(); onDismiss() },
            )
        }
    }
}
