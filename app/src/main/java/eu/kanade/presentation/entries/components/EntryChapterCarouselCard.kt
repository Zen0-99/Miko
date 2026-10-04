package eu.kanade.presentation.entries.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.fullDateText
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA

/**
 * Horizontal chapter card for manga/novel detail views — the chapter-list
 * counterpart of the Nuvio episode card. Same chrome: fixed
 * 0.75 × screen width inside a snap-fling carousel, rounded 16dp body,
 * metadata pinned to the bottom, download/audio indicators top-right.
 * Both shades are the entry accent darkened + desaturated into a surface
 * ([accentTintedSurface]) — read cards sit a notch darker. There is no
 * still, so the body is a tonal surface instead of an image + scrim.
 *
 * Swipe actions are intentionally absent (they fight the carousel's
 * scroll) — long-press selection exposes the same actions.
 */
@Composable
fun EntryChapterCarouselCard(
    title: String,
    dateUpload: Long,
    scanlator: String?,
    progress: String?,
    read: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    /** The next-in-line chapter to read — gets a thin accent outline. */
    isNext: Boolean = false,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    /** Carrier hint on canonical rows (16-04) — e.g. "Needs extension"
     *  or the carrier that would serve the row. Renders in the
     *  scanlator slot (unused on canon rows); warning copies the error
     *  color so the affordance reads as actionable. */
    carrierNote: String? = null,
    carrierNoteWarning: Boolean = false,
    /** Non-null on multi-carrier canonical rows — tapping the note opens
     *  the chapter-scoped carrier picker ("read this chapter with…"). */
    onCarrierNoteClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val cardWidth = LocalConfiguration.current.screenWidthDp.dp * 0.75f
    // Cards are the ENTRY accent darkened + desaturated into a surface —
    // a purple-accented entry reads as a muted purple card. Read cards
    // sit a notch darker, but never at/below the page background.
    val cardColor = accentTintedSurface(
        accent = accent,
        background = MaterialTheme.colorScheme.background,
        dimmed = read,
    )
    val readCheckTint = rememberOnAccentColor(cardColor)

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .then(
                if (isNext && !selected) {
                    Modifier.border(1.dp, accent, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        // Selection layer — accent tint fill, same as the episode card
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(accent.copy(alpha = 0.18f)),
            )
        }

        // Top-left status cluster (episode-card convention): read check,
        // reading-progress pill, bookmark — one row pinned to the corner.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (read) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = readCheckTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (progress != null) {
                // "Page 7"-style progress pill — same black-scrim chip the
                // episode card uses for "time watched".
                Text(
                    text = progress,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (bookmark) {
                Box(
                    modifier = Modifier
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
        }

        // Download/audio indicators (top-right)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailing?.invoke()
        }

        // Bottom content — title, then a meta row with the date pinned
        // to the right edge (episode-card convention).
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Episode-card convention: release date on the left in the
                // description color; everything else pins to the right
                // edge — scanlator/carrier truncates, progress last.
                Text(
                    text = fullDateText(dateUpload),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground
                        .copy(alpha = SECONDARY_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val metaLabel = scanlator?.takeIf { it.isNotBlank() } ?: carrierNote
                if (!metaLabel.isNullOrBlank()) {
                    Text(
                        text = metaLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        color = if (carrierNoteWarning && scanlator.isNullOrBlank()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            // Same color as the date text — the carrier
                            // name is metadata, not a warning.
                            MaterialTheme.colorScheme.onBackground
                                .copy(alpha = SECONDARY_ALPHA)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .let { m ->
                                if (onCarrierNoteClick != null && scanlator.isNullOrBlank()) {
                                    m.clickable(onClick = onCarrierNoteClick)
                                } else {
                                    m
                                }
                            },
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

