package miko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private val CoverPlaceholderColor = Color(0x1F888888)
private const val COVER_RATIO = 2f / 3f

/**
 * Port of Android's `EntryComfortableGridItem` — cover with the title in a
 * column *below* it (used by the global-search card rows).
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
    coverBadge: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            AsyncImage(
                model = coverUrl,
                placeholder = ColorPainter(CoverPlaceholderColor),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = coverAlpha,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(COVER_RATIO),
            )
            if (coverBadge != null) {
                Box(modifier = Modifier.align(Alignment.TopStart)) {
                    coverBadge()
                }
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
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
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(COVER_RATIO)
                .clip(MaterialTheme.shapes.extraSmall),
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
    topStartBadge: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = coverUrl,
            placeholder = ColorPainter(CoverPlaceholderColor),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_RATIO),
        )
        if (topStartBadge != null) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                topStartBadge()
            }
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
