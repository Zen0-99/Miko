package miko.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.StringResource
import miko.ui.download.DownloadManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Android's MoreTab — the catch-all list of utility destinations (download
 * queue with live pending count, collections, stats, settings + the
 * incognito switch).
 */
@Composable
fun MoreScreen(
    onClickStats: () -> Unit,
    onClickSettings: () -> Unit,
    onClickCollections: () -> Unit,
    onClickDownloadQueue: () -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val queueRows by DownloadManager.queueRows.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        // Android's incognito switch — pauses history writes globally.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onIncognitoModeChange(!incognitoMode) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f),
            ) {
                Text(
                    text = stringResource(MR.strings.pref_incognito_mode),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(MR.strings.pref_incognito_mode_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = incognitoMode,
                onCheckedChange = onIncognitoModeChange,
            )
        }
        MoreRow(
            titleRes = MR.strings.label_download_queue,
            subtitle = queueRows.size.takeIf { it > 0 }?.let {
                pluralStringResource(MR.plurals.download_queue_summary, it, it)
            },
            icon = Icons.Outlined.Download,
            onClick = onClickDownloadQueue,
        )
        MoreRow(
            titleRes = MR.strings.collections,
            icon = Icons.AutoMirrored.Outlined.Label,
            onClick = onClickCollections,
        )
        MoreRow(
            titleRes = MR.strings.label_stats,
            icon = Icons.Outlined.QueryStats,
            onClick = onClickStats,
        )
        MoreRow(
            titleRes = MR.strings.label_settings,
            icon = Icons.Outlined.Settings,
            onClick = onClickSettings,
        )
        HorizontalDivider()
    }
}

@Composable
private fun MoreRow(
    titleRes: StringResource,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
