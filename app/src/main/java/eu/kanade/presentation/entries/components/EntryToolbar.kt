package eu.kanade.presentation.entries.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.EntryDownloadDropdownMenu
import eu.kanade.presentation.entries.DownloadAction
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active

@Composable
fun EntryToolbar(
    title: String,
    hasFilters: Boolean,
    navigateUp: () -> Unit,
    onClickFilter: (() -> Unit)?,
    onClickShare: (() -> Unit)?,
    onClickDownload: ((DownloadAction) -> Unit)?,
    onClickEditCategory: (() -> Unit)?,
    onClickRefresh: (() -> Unit)?,
    onClickMigrate: (() -> Unit)?,
    onClickSettings: (() -> Unit)?,
    onClickMarkAllRead: (() -> Unit)? = null,
    onClickMarkAllUnread: (() -> Unit)? = null,
    onClickRefreshTracking: (() -> Unit)? = null,
    onClickRemoveAllDownloads: (() -> Unit)? = null,
    onClickRemoveNonBookmarkedDownloads: (() -> Unit)? = null,
    onClickRemoveReadDownloads: (() -> Unit)? = null,
    onClickLinkedSources: (() -> Unit)? = null,
    // Anime only
    changeAnimeSkipIntro: (() -> Unit)?,
    // For action mode
    actionModeCounter: Int,
    onCancelActionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    titleAlphaProvider: () -> Float,
    backgroundAlphaProvider: () -> Float,
    isManga: Boolean,
    modifier: Modifier = Modifier,
    toolbarBackgroundColor: Color? = null,
    // Smart update interval (novels only); null = no badge, Int? = days or N/A
    intervalDays: Int? = null,
    showIntervalBadge: Boolean = false,
) {
    val isActionMode = actionModeCounter > 0
    AppBar(
        titleContent = {
            if (isActionMode) {
                AppBarTitle(actionModeCounter.toString())
            } else {
                AppBarTitle(title, modifier = Modifier.alpha(titleAlphaProvider()))
            }
        },
        modifier = modifier,
        backgroundColor = (toolbarBackgroundColor ?: MaterialTheme.colorScheme
            .surfaceColorAtElevation(3.dp))
            .copy(alpha = if (isActionMode) 1f else backgroundAlphaProvider()),
        navigateUp = navigateUp,
        actions = {
            var downloadExpanded by remember { mutableStateOf(false) }

            if (onClickDownload != null) {
                val onDismissRequest = { downloadExpanded = false }
                EntryDownloadDropdownMenu(
                    expanded = downloadExpanded,
                    onDismissRequest = onDismissRequest,
                    onDownloadClicked = onClickDownload,
                    isManga = isManga,
                )
            }

            val filterTint = if (hasFilters) MaterialTheme.colorScheme.active else LocalContentColor.current
            AppBarActions(
                actions = persistentListOf<AppBar.AppBarAction>().builder().apply {
                    if (isActionMode) {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_all),
                                icon = Icons.Outlined.SelectAll,
                                onClick = onSelectAll,
                            ),
                        )
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_inverse),
                                icon = Icons.Outlined.FlipToBack,
                                onClick = onInvertSelection,
                            ),
                        )
                        return@apply
                    }
                    if (onClickDownload != null) {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.manga_download),
                                icon = Icons.Outlined.Download,
                                onClick = { downloadExpanded = !downloadExpanded },
                            ),
                        )
                    }
                    if (onClickFilter != null) {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_filter),
                                icon = Icons.Outlined.FilterList,
                                iconTint = filterTint,
                                onClick = onClickFilter,
                            ),
                        )
                    }
                    if (changeAnimeSkipIntro != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(AYMR.strings.action_change_intro_length),
                                onClick = changeAnimeSkipIntro,
                            ),
                        )
                    }
                    if (onClickMarkAllRead != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_mark_all_as_read),
                                onClick = onClickMarkAllRead,
                            ),
                        )
                    }
                    if (onClickMarkAllUnread != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_mark_all_as_unread),
                                onClick = onClickMarkAllUnread,
                            ),
                        )
                    }
                    if (onClickRefreshTracking != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_refresh_tracking),
                                onClick = onClickRefreshTracking,
                            ),
                        )
                    }
                    if (onClickRemoveAllDownloads != null) {
                        add(
                            AppBar.NestedOverflowAction(
                                title = stringResource(MR.strings.action_remove_downloads),
                                subActions = buildList {
                                    add(AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_remove_all_downloads),
                                        onClick = onClickRemoveAllDownloads,
                                    ))
                                    if (onClickRemoveNonBookmarkedDownloads != null) {
                                        add(AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_remove_non_bookmarked_downloads),
                                            onClick = onClickRemoveNonBookmarkedDownloads,
                                        ))
                                    }
                                    if (onClickRemoveReadDownloads != null) {
                                        add(AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_remove_read_downloads),
                                            onClick = onClickRemoveReadDownloads,
                                        ))
                                    }
                                },
                            ),
                        )
                    }
                    if (onClickRefresh != null) {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_webview_refresh),
                                icon = Icons.Filled.Refresh,
                                onClick = onClickRefresh,
                            ),
                        )
                    }

                    if (onClickEditCategory != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_edit),
                                onClick = onClickEditCategory,
                            ),
                        )
                    }
                    if (onClickMigrate != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_migrate),
                                onClick = onClickMigrate,
                            ),
                        )
                    }
                    if (onClickLinkedSources != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_linked_sources),
                                onClick = onClickLinkedSources,
                            ),
                        )
                    }
                    if (onClickShare != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_share),
                                onClick = onClickShare,
                            ),
                        )
                    }
                    if (onClickSettings != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(AYMR.strings.settings),
                                onClick = onClickSettings,
                            ),
                        )
                    }
                }
                    .build(),
            )
        },
        isActionMode = isActionMode,
        onCancelActionMode = onCancelActionMode,
    )
}

/**
 * Sandtimer (hourglass) icon with the average update interval (in days) shown
 * beside it. Shows "N/A" when [intervalDays] is null.
 */
@Composable
private fun IntervalBadge(
    intervalDays: Int?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val label = when (intervalDays) {
        null, 0 -> "N/A"
        else -> intervalDays.toString()
    }

    Row(
        modifier = modifier
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.HourglassEmpty,
            contentDescription = null,
            tint = tint.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = tint,
            maxLines = 1,
        )
    }
}
