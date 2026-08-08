package eu.kanade.presentation.entries.anime

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
fun AnimeEpisodeLongPressSheet(
    episodeTitle: String,
    onDismiss: () -> Unit,
    onOpenInWebView: () -> Unit,
    onMarkPreviousAsSeen: () -> Unit,
    onMarkPreviousAsUnseen: () -> Unit,
    onMarkRangeAsSeen: () -> Unit,
    onMarkRangeAsUnseen: () -> Unit,
    onHideEpisode: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Text(
            text = episodeTitle,
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
                label = "Mark previous as seen",
                icon = EyeDown,
                onClick = { onMarkPreviousAsSeen(); onDismiss() },
            )
            SheetOption(
                label = "Mark previous as unseen",
                icon = EyeOffDown,
                onClick = { onMarkPreviousAsUnseen(); onDismiss() },
            )
            SheetOption(
                label = "Mark range as seen",
                icon = EyeDots,
                onClick = { onMarkRangeAsSeen(); onDismiss() },
            )
            SheetOption(
                label = "Mark range as unseen",
                icon = EyeOffDots,
                onClick = { onMarkRangeAsUnseen(); onDismiss() },
            )
            if (onHideEpisode != null) {
                SheetOption(
                    label = "Hide episode",
                    icon = Icons.Outlined.VisibilityOff,
                    onClick = { onHideEpisode(); onDismiss() },
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
