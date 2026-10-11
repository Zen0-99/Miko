package miko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground

private val CoverPlaceholderColor = Color(0x1F888888)
private const val COVER_RATIO = 2f / 3f

/** Android's `CommonEntryItemDefaults.BrowseFavoriteCoverAlpha`. */
const val BROWSE_FAVORITE_COVER_ALPHA = 0.34f

/**
 * Android's `InLibraryBadge` — the bookmark-strip corner badge on browse
 * covers when the entry is already a library favorite.
 */
@Composable
fun InLibraryBadge(enabled: Boolean) {
    if (enabled) {
        Badge(imageVector = Icons.Outlined.CollectionsBookmark)
    }
}

/** Android's GRID_SELECTED_COVER_ALPHA — covers dim in selection mode. */
private const val SELECTED_COVER_ALPHA = 0.76f

/** Android's GridItemSelectable outline — secondary fill behind the cell. */
private fun Modifier.selectedOutline(isSelected: Boolean, color: Color) = drawBehind {
    if (isSelected) drawRect(color)
}

/**
 * Port of Android's `EntryComfortableGridItem` — cover with the title in a
 * column *below* it (used by the global-search card rows). [isSelected]
 * applies Android's GridItemSelectable chrome: a secondary outline behind
 * the 4dp-padded cell with the cover dimmed.
 */
@Composable
fun CoverComfortableItem(
    title: String,
    coverUrl: Any?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    titleMaxLines: Int = 2,
    coverAlpha: Float = 1f,
    isSelected: Boolean = false,
    coverBadge: (@Composable RowScope.() -> Unit)? = null,
    coverBadgeEnd: (@Composable RowScope.() -> Unit)? = null,
    onClickContinueViewing: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .selectedOutline(isSelected, MaterialTheme.colorScheme.secondary)
            .padding(4.dp),
    ) {
        Box {
            AsyncImage(
                model = coverUrl,
                placeholder = ColorPainter(CoverPlaceholderColor),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (isSelected) SELECTED_COVER_ALPHA else coverAlpha,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(COVER_RATIO),
            )
            if (coverBadge != null) {
                BadgeGroup(
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.TopStart),
                    content = coverBadge,
                )
            }
            if (coverBadgeEnd != null) {
                BadgeGroup(
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.TopEnd),
                    content = coverBadgeEnd,
                )
            }
            if (onClickContinueViewing != null) {
                ContinueViewingButton(
                    size = 30.dp,
                    iconSize = 18.dp,
                    onClick = onClickContinueViewing,
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomEnd),
                )
            }
        }
        Text(
            text = title,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            minLines = 1,
            maxLines = titleMaxLines,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(4.dp),
        )
    }
}

/**
 * Port of Android's `EntryListItem` — 56dp row: 2:3 cover thumb + bodyMedium
 * title (+ optional onSurfaceVariant subtitle).
 */
@Composable
fun CoverListItem(
    title: String,
    coverUrl: Any?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isSelected: Boolean = false,
    coverAlpha: Float = 1f,
    onCoverClick: (() -> Unit)? = null,
    badge: (@Composable RowScope.() -> Unit)? = null,
    onClickContinueViewing: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectedBackground(isSelected)
            .height(56.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverUrl,
            placeholder = ColorPainter(CoverPlaceholderColor),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = coverAlpha,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(COVER_RATIO)
                .clip(MaterialTheme.shapes.extraSmall)
                .then(
                    // Android's row-cover hit target — opens the entry page
                    // independently of the row's own click.
                    if (onCoverClick != null) {
                        Modifier.clickable(onClick = onCoverClick)
                    } else {
                        Modifier
                    },
                ),
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
        ) {
            Text(
                text = title,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (badge != null) {
            BadgeGroup(content = badge)
        }
        if (onClickContinueViewing != null) {
            ContinueViewingButton(
                size = 28.dp,
                iconSize = 16.dp,
                onClick = onClickContinueViewing,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        trailing?.invoke()
    }
}

/**
 * Port of Android's `EntryCompactGridItem` — cover with the title overlaid
 * on a bottom gradient scrim. Cover is 2:3, extraSmall clip; title is white
 * titleSmall with a black drop shadow.
 */
@Composable
fun CoverGridItem(
    title: String?,
    coverUrl: Any?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    coverAlpha: Float = 1f,
    topStartBadge: (@Composable RowScope.() -> Unit)? = null,
    topEndBadge: (@Composable RowScope.() -> Unit)? = null,
    onClickContinueViewing: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .selectedOutline(isSelected, MaterialTheme.colorScheme.secondary)
            .padding(4.dp),
    ) {
        AsyncImage(
            model = coverUrl,
            placeholder = ColorPainter(CoverPlaceholderColor),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = if (isSelected) SELECTED_COVER_ALPHA else coverAlpha,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_RATIO),
        )
        if (topStartBadge != null) {
            BadgeGroup(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                content = topStartBadge,
            )
        }
        if (topEndBadge != null) {
            BadgeGroup(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                content = topEndBadge,
            )
        }
        if (onClickContinueViewing != null) {
            ContinueViewingButton(
                size = 30.dp,
                iconSize = 18.dp,
                onClick = onClickContinueViewing,
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.BottomEnd),
            )
        }
        if (title != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.33f)
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color(0xAA000000),
                        ),
                    ),
            )
            Text(
                text = title,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                minLines = 1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = Color.White,
                    shadow = Shadow(
                        color = Color.Black,
                        blurRadius = 4f,
                    ),
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
        }
    }
}

/**
 * Android's `ContinueViewingButton` — the dark scrim circle with a book
 * icon overlaid on library covers/rows; tap resumes at the next unread
 * item. Grid uses 30dp/18dp at cover bottom-end, list rows 28dp/16dp.
 */
@Composable
private fun ContinueViewingButton(
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(Color(0xAD212121))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = stringResource(MR.strings.action_resume),
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}
