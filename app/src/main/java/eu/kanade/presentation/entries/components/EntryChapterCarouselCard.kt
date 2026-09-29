package eu.kanade.presentation.entries.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA

/**
 * Horizontal chapter card for manga/novel detail views — the chapter-list
 * counterpart of the Nuvio episode card. Same chrome: fixed
 * 0.75 × screen width inside a snap-fling carousel, rounded 16dp body,
 * metadata pinned to the bottom, download/audio indicators top-right,
 * bottom-edge progress bar, check badge when finished. There is no still,
 * so the body is a tonal surface instead of an image + scrim.
 *
 * Swipe actions are intentionally absent (they fight the carousel's
 * scroll) — long-press selection exposes the same actions.
 */
@Composable
fun EntryChapterCarouselCard(
    title: String,
    date: String?,
    metaParts: List<String>,
    progressFraction: Float?,
    read: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val cardWidth = LocalConfiguration.current.screenWidthDp.dp * 0.75f
    val metaAlpha = if (read) DISABLED_ALPHA else 1f

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                RoundedCornerShape(16.dp),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        // Selection layer — accent tint + border, same as the episode card
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.5.dp, accent, RoundedCornerShape(16.dp)),
            )
        }

        // Read badge (top-left accent circle, white ring — Nuvio style)
        if (read) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(24.dp)
                    .background(accent, RoundedCornerShape(12.dp))
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        // Bookmark marker (top-left, beside the read badge)
        if (bookmark) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = if (read) 42.dp else 10.dp, top = 10.dp)
                    .size(24.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Download/audio indicators (top-right)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            trailing?.invoke()
        }

        // Bottom content — title, then a meta row with the date pinned
        // to the right edge (episode-card convention).
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .alpha(metaAlpha),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (metaParts.isNotEmpty() || date != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    metaParts.forEach { part ->
                        Text(
                            text = part,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = SECONDARY_ALPHA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (date != null) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = SECONDARY_ALPHA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Bottom-edge progress bar — in-progress chapters only
        if (progressFraction != null && !read) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(accent),
                )
            }
        }
    }
}
