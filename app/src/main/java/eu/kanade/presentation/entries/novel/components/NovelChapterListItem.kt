package eu.kanade.presentation.entries.novel.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.DotSeparatorText
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterList
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground

@Composable
fun NovelChapterListItem(
    item: NovelChapterList.Item,
    isFromSource: Boolean,
    downloadIndicatorEnabled: Boolean,
    date: String?,
    readProgress: String?,
    scanlator: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: ((NovelChapterDownloadAction) -> Unit)?,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSelect: (Boolean) -> Unit,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
) {
    val chapter = item.chapter
    val downloadState = item.downloadState
    val accent = accentColor ?: MaterialTheme.colorScheme.primary

    val start = getSwipeAction(
        action = chapterSwipeStartAction,
        read = chapter.read,
        bookmark = chapter.bookmark,
        downloadState = downloadState,
        background = when (chapterSwipeStartAction) {
            LibraryPreferences.ChapterSwipeAction.ToggleRead -> MaterialTheme.colorScheme.tertiary
            LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> MaterialTheme.colorScheme.secondary
            LibraryPreferences.ChapterSwipeAction.Download -> MaterialTheme.colorScheme.primary
            LibraryPreferences.ChapterSwipeAction.Disabled -> MaterialTheme.colorScheme.primary
        },
        onSwipe = onSwipeLeft,
    )
    val end = getSwipeAction(
        action = chapterSwipeEndAction,
        read = chapter.read,
        bookmark = chapter.bookmark,
        downloadState = downloadState,
        background = when (chapterSwipeEndAction) {
            LibraryPreferences.ChapterSwipeAction.ToggleRead -> MaterialTheme.colorScheme.tertiary
            LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> MaterialTheme.colorScheme.secondary
            LibraryPreferences.ChapterSwipeAction.Download -> MaterialTheme.colorScheme.primary
            LibraryPreferences.ChapterSwipeAction.Disabled -> MaterialTheme.colorScheme.primary
        },
        onSwipe = onSwipeRight,
    )

    SwipeableActionsBox(
        modifier = Modifier.clipToBounds(),
        startActions = listOfNotNull(start),
        endActions = listOfNotNull(end),
        swipeThreshold = swipeActionThreshold,
        backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .selectedBackground(item.selected)
                .combinedClickable(
                    onClick = if (item.selected) { { onSelect(!item.selected) } } else onClick,
                    onLongClick = onLongClick,
                )
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!chapter.read) {
                        Icon(
                            imageVector = Icons.Filled.Circle,
                            contentDescription = stringResource(MR.strings.unread),
                            modifier = Modifier
                                .height(8.dp)
                                .padding(end = 4.dp),
                            tint = accent,
                        )
                    }
                    if (chapter.bookmark) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = stringResource(MR.strings.action_filter_bookmarked),
                            modifier = Modifier.padding(end = 4.dp),
                            tint = accent,
                        )
                    }
                    Text(
                        text = chapter.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = LocalContentColor.current.copy(alpha = if (chapter.read) DISABLED_ALPHA else 1f),
                    )
                }

                Row {
                    val subtitleStyle = MaterialTheme.typography.bodySmall
                        .merge(
                            color = LocalContentColor.current
                                .copy(alpha = if (chapter.read) DISABLED_ALPHA else SECONDARY_ALPHA),
                        )
                    ProvideTextStyle(value = subtitleStyle) {
                        if (date != null) {
                            Text(
                                text = date,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (scanlator != null || readProgress != null) DotSeparatorText()
                        }
                        if (scanlator != null) {
                            Text(
                                text = scanlator,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (readProgress != null) DotSeparatorText()
                        }
                        if (readProgress != null) {
                            Text(
                                text = readProgress,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            NovelChapterDownloadIndicator(
                enabled = downloadIndicatorEnabled,
                modifier = Modifier.padding(start = 4.dp),
                downloadStateProvider = { item.downloadState },
                downloadProgressProvider = { item.downloadProgress },
                onClick = { onDownloadClick?.invoke(it) },
                accentColor = accentColor,
            )
        }
    }
}

private fun getSwipeAction(
    action: LibraryPreferences.ChapterSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloadState: NovelDownload.State,
    background: Color,
    onSwipe: () -> Unit,
): me.saket.swipe.SwipeAction? {
    return when (action) {
        LibraryPreferences.ChapterSwipeAction.ToggleRead -> swipeAction(
            icon = if (!read) Icons.Outlined.Done else Icons.Outlined.RemoveDone,
            background = background,
            isUndo = read,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> swipeAction(
            icon = if (!bookmark) Icons.Outlined.BookmarkAdd else Icons.Outlined.BookmarkRemove,
            background = background,
            isUndo = bookmark,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.Download -> swipeAction(
            icon = when (downloadState) {
                NovelDownload.State.NOT_DOWNLOADED, NovelDownload.State.ERROR -> Icons.Outlined.Download
                NovelDownload.State.QUEUE, NovelDownload.State.DOWNLOADING -> Icons.Outlined.FileDownloadOff
                NovelDownload.State.DOWNLOADED -> Icons.Outlined.Delete
            },
            background = background,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.Disabled -> null
    }
}

private fun swipeAction(
    onSwipe: () -> Unit,
    icon: ImageVector,
    background: Color,
    isUndo: Boolean = false,
): me.saket.swipe.SwipeAction {
    return me.saket.swipe.SwipeAction(
        icon = {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = icon,
                tint = contentColorFor(background),
                contentDescription = null,
            )
        },
        background = background,
        onSwipe = onSwipe,
        isUndo = isUndo,
    )
}

private val swipeActionThreshold = 56.dp
