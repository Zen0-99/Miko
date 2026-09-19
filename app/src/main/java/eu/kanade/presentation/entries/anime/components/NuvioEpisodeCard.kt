package eu.kanade.presentation.entries.anime.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.entries.components.DotSeparatorText
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.util.secondaryItemAlpha
import tachiyomi.presentation.core.util.selectedBackground

/**
 * NuvioMobile-style episode card — horizontal layout with thumbnail on the left
 * and episode info on the right. Features:
 * - 120dp thumbnail with SxxExx badge, progress bar, watched/unwatched indicators
 * - Episode title, metadata row (date, scanlator), overview text
 * - Swipe actions preserved (seen toggle, bookmark, download)
 * - Download indicator and bookmark icon
 */
@Composable
fun NuvioEpisodeCard(
    title: String,
    episodeString: String,
    rating: String? = null,
    date: String?,
    watchProgress: String?,
    scanlator: String?,
    summary: String?,
    previewUrl: String?,
    seen: Boolean,
    bookmark: Boolean,
    fillermark: Boolean,
    selected: Boolean,
    isAnyEpisodeSelected: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> AnimeDownload.State,
    downloadProgressProvider: () -> Int,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownloadClick: ((EpisodeDownloadAction) -> Unit)?,
    onEpisodeSwipe: (LibraryPreferences.EpisodeSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
) {
    val start = getSwipeAction(
        action = episodeSwipeStartAction,
        seen = seen,
        bookmark = bookmark,
        fillermark = fillermark,
        downloadState = downloadStateProvider(),
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onEpisodeSwipe(episodeSwipeStartAction) },
    )
    val end = getSwipeAction(
        action = episodeSwipeEndAction,
        seen = seen,
        bookmark = bookmark,
        fillermark = fillermark,
        downloadState = downloadStateProvider(),
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onEpisodeSwipe(episodeSwipeEndAction) },
    )

    me.saket.swipe.SwipeableActionsBox(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        startActions = listOfNotNull(start),
        endActions = listOfNotNull(end),
        swipeThreshold = swipeActionThreshold,
        backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .selectedBackground(selected)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .height(120.dp),
        ) {
            // Thumbnail with overlays
            EpisodeThumbnailWithBadges(
                previewUrl = previewUrl,
                episodeString = episodeString,
                seen = seen,
                watchProgress = watchProgress,
                modifier = Modifier.size(120.dp),
            )

            // Info section
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (seen) DISABLED_ALPHA else 1f,
                    ),
                )

                // Metadata row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.secondaryItemAlpha(),
                ) {
                    if (rating != null) {
                        Text(
                            text = "★ $rating",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (rating != null && date != null) {
                        DotSeparatorText()
                    }
                    if (date != null) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 12.sp,
                        )
                    }
                    if (scanlator != null && date != null) {
                        DotSeparatorText()
                    }
                    if (scanlator != null) {
                        Text(
                            text = scanlator,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (watchProgress != null) {
                        if (date != null || scanlator != null) DotSeparatorText()
                        Text(
                            text = watchProgress,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 12.sp,
                            color = accentColor ?: MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // Overview / summary
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
                    )
                }
            }

            // Download + bookmark icons
            Column(
                modifier = Modifier.padding(end = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (bookmark) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = accentColor ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (downloadIndicatorEnabled && onDownloadClick != null) {
                    DownloadIndicator(
                        downloadState = downloadStateProvider(),
                        downloadProgress = downloadProgressProvider(),
                        onDownloadClick = onDownloadClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeThumbnailWithBadges(
    previewUrl: String?,
    episodeString: String,
    seen: Boolean,
    watchProgress: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // Thumbnail image or placeholder
        if (!previewUrl.isNullOrBlank()) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(previewUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = episodeString.ifBlank { "?" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // SxxExx badge (bottom-right)
        if (episodeString.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(
                        Color.Black.copy(alpha = 0.85f),
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = episodeString,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Watched indicator (top-left)
        if (seen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(20.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadIndicator(
    downloadState: AnimeDownload.State,
    downloadProgress: Int,
    onDownloadClick: (EpisodeDownloadAction) -> Unit,
) {
    val (icon, tint) = when (downloadState) {
        AnimeDownload.State.NOT_DOWNLOADED -> Icons.Outlined.Download to MaterialTheme.colorScheme.onSurfaceVariant
        AnimeDownload.State.QUEUE -> Icons.Outlined.Download to MaterialTheme.colorScheme.primary
        AnimeDownload.State.DOWNLOADING -> Icons.Outlined.Download to MaterialTheme.colorScheme.primary
        AnimeDownload.State.DOWNLOADED -> Icons.Outlined.Delete to MaterialTheme.colorScheme.primary
        AnimeDownload.State.ERROR -> Icons.Outlined.FileDownloadOff to MaterialTheme.colorScheme.error
    }
    IconButton(
        onClick = {
            val action = when (downloadState) {
                AnimeDownload.State.NOT_DOWNLOADED, AnimeDownload.State.ERROR -> EpisodeDownloadAction.START
                AnimeDownload.State.QUEUE, AnimeDownload.State.DOWNLOADING -> EpisodeDownloadAction.CANCEL
                AnimeDownload.State.DOWNLOADED -> EpisodeDownloadAction.DELETE
            }
            onDownloadClick(action)
        },
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}
