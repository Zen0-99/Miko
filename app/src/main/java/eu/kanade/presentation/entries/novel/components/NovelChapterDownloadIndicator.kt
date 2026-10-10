package eu.kanade.presentation.entries.novel.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.ArrowModifier
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.IndicatorModifier
import eu.kanade.presentation.components.IndicatorSize
import eu.kanade.presentation.components.IndicatorStrokeWidth
import eu.kanade.presentation.components.commonClickable
import eu.kanade.presentation.entries.components.rememberOnAccentColor
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.IconButtonTokens
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun NovelChapterDownloadIndicator(
    enabled: Boolean,
    downloadStateProvider: () -> NovelDownload.State,
    downloadProgressProvider: () -> Int,
    onClick: (NovelChapterDownloadAction) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    /**
     * Canon chapters: (pkgName → display name) carriers that could serve
     * this download. Non-empty swaps the not-downloaded long-press from
     * "queue at start" to a per-chapter carrier picker — the tap still
     * downloads via the preferred/automatic routing.
     */
    downloadCarriers: List<Pair<String, String>>? = null,
    onCarrierPick: ((String) -> Unit)? = null,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.onSurface
    when (val downloadState = downloadStateProvider()) {
        NovelDownload.State.NOT_DOWNLOADED -> NotDownloadedIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
            tint = accent,
            downloadCarriers = downloadCarriers,
            onCarrierPick = onCarrierPick,
        )
        NovelDownload.State.QUEUE, NovelDownload.State.DOWNLOADING -> DownloadingIndicator(
            enabled = enabled,
            modifier = modifier,
            downloadState = downloadState,
            downloadProgressProvider = downloadProgressProvider,
            onClick = onClick,
            tint = accent,
        )
        NovelDownload.State.DOWNLOADED -> DownloadedIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
            tint = accent,
        )
        NovelDownload.State.ERROR -> ErrorIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
    }
}

@Composable
private fun NotDownloadedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (NovelChapterDownloadAction) -> Unit,
    tint: Color,
    downloadCarriers: List<Pair<String, String>>? = null,
    onCarrierPick: ((String) -> Unit)? = null,
) {
    var carrierMenuExpanded by remember { mutableStateOf(false) }
    val carrierPicker = downloadCarriers?.takeIf { it.isNotEmpty() && onCarrierPick != null }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = {
                    if (carrierPicker != null) {
                        carrierMenuExpanded = true
                    } else {
                        onClick(NovelChapterDownloadAction.START_NOW)
                    }
                },
                onClick = { onClick(NovelChapterDownloadAction.START) },
            )
            .secondaryItemAlpha(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_download_item_24dp),
            contentDescription = stringResource(MR.strings.manga_download),
            modifier = Modifier.size(IndicatorSize),
            tint = tint,
        )
        if (carrierPicker != null) {
            DropdownMenu(
                expanded = carrierMenuExpanded,
                onDismissRequest = { carrierMenuExpanded = false },
            ) {
                carrierPicker.forEach { (pkg, name) ->
                    DropdownMenuItem(
                        text = { Text(text = name) },
                        onClick = {
                            onCarrierPick?.invoke(pkg)
                            carrierMenuExpanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadingIndicator(
    enabled: Boolean,
    downloadState: NovelDownload.State,
    downloadProgressProvider: () -> Int,
    onClick: (NovelChapterDownloadAction) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(NovelChapterDownloadAction.CANCEL) },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val arrowColor: Color
        val strokeColor = tint
        val downloadProgress = downloadProgressProvider()
        val indeterminate = downloadState == NovelDownload.State.QUEUE ||
            (downloadState == NovelDownload.State.DOWNLOADING && downloadProgress == 0)
        if (indeterminate) {
            arrowColor = strokeColor
            CircularProgressIndicator(
                modifier = IndicatorModifier,
                color = strokeColor,
                strokeWidth = IndicatorStrokeWidth,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
            )
        } else {
            val animatedProgress by animateFloatAsState(
                targetValue = downloadProgress / 100f,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "progress",
            )
            arrowColor = if (animatedProgress < 0.5f) {
                strokeColor
            } else {
                MaterialTheme.colorScheme.background
            }
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = IndicatorModifier,
                color = strokeColor,
                strokeWidth = IndicatorSize / 2,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
            )
        }
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_start_downloading_now)) },
                onClick = {
                    onClick(NovelChapterDownloadAction.START_NOW)
                    isMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_cancel)) },
                onClick = {
                    onClick(NovelChapterDownloadAction.CANCEL)
                    isMenuExpanded = false
                },
            )
        }
        Icon(
            imageVector = Icons.Outlined.ArrowDownward,
            contentDescription = null,
            modifier = ArrowModifier,
            tint = arrowColor,
        )
    }
}

@Composable
private fun DownloadedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (NovelChapterDownloadAction) -> Unit,
    tint: Color,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    // Downloaded = the idle glyph inverted: a filled disc in the card's
    // contrast tint with the ring still on top in the same tint and the
    // arrow punched through in the on-tint color.
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { isMenuExpanded = true },
                onClick = { isMenuExpanded = true },
            )
            // Same off-white as NOT_DOWNLOADED — the state reads from the
            // filled disc + punched arrow, not from a brighter tint.
            .secondaryItemAlpha(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(IndicatorSize - 4.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Icon(
            painter = painterResource(R.drawable.ic_download_item_24dp),
            contentDescription = null,
            modifier = Modifier.size(IndicatorSize),
            tint = tint,
        )
        Icon(
            imageVector = Icons.Outlined.ArrowDownward,
            contentDescription = null,
            modifier = ArrowModifier,
            tint = rememberOnAccentColor(tint),
        )
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_delete)) },
                onClick = {
                    onClick(NovelChapterDownloadAction.DELETE)
                    isMenuExpanded = false
                },
            )
        }
    }
}

@Composable
private fun ErrorIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (NovelChapterDownloadAction) -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(NovelChapterDownloadAction.START) },
                onClick = { onClick(NovelChapterDownloadAction.START) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = stringResource(AYMR.strings.download_error),
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}
