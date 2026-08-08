package eu.kanade.presentation.entries.novel.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.EntryIconAction
import eu.kanade.presentation.entries.components.EntryTogglePill
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun NovelActionRow(
    favorite: Boolean,
    accentColor: Color?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onShareClicked: (() -> Unit)?,
    onHighlightsClicked: (() -> Unit)?,
    trackingCount: Int = 0,
    onTrackingClicked: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // In Library pill
        EntryTogglePill(
            checked = favorite,
            checkedText = stringResource(MR.strings.in_library),
            uncheckedText = stringResource(MR.strings.add_to_library),
            checkedIcon = Icons.Filled.Favorite,
            uncheckedIcon = Icons.Outlined.FavoriteBorder,
            accentColor = accent,
            onClick = onAddToLibraryClicked,
        )

        // Tracking pill
        if (onTrackingClicked != null) {
            EntryTogglePill(
                checked = trackingCount > 0,
                checkedText = if (trackingCount == 0) {
                    stringResource(MR.strings.action_track)
                } else {
                    pluralStringResource(MR.plurals.num_trackers, trackingCount, trackingCount)
                },
                uncheckedText = stringResource(MR.strings.action_track),
                checkedIcon = Icons.Outlined.Done,
                uncheckedIcon = Icons.Outlined.Sync,
                accentColor = accent,
                onClick = onTrackingClicked,
            )
        }

        // Icon-only actions — use accent color like Miko
        EntryIconAction(
            icon = Icons.Outlined.Public,
            contentDescription = stringResource(MR.strings.action_web_view),
            tint = accent,
            onClick = onWebViewClicked,
        )
        EntryIconAction(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = stringResource(AYMR.strings.highlights),
            tint = accent,
            onClick = onHighlightsClicked,
        )
    }
}
