package eu.kanade.presentation.entries.shows.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import coil3.asDrawable
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.entries.components.ImdbRatingLabel
import eu.kanade.tachiyomi.data.download.shows.model.ShowsDownload
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA

/**
 * Nuvio-style immersive episode card — the episode still fills the whole
 * card under a bottom-heavy gradient, with all metadata pinned to the
 * bottom. Rendered inside a horizontal episode carousel (fixed
 * 0.75 × screen width), so swipe actions are intentionally absent —
 * long-press selection exposes the same actions.
 *
 * - Full-bleed still, SxEx badge, title, overview, meta row
 * - Bottom-edge progress bar and completed badge, Nuvio-style
 * - Comments on a legibility chip, download indicator bare (top-right)
 */
@Composable
fun NuvioEpisodeCard(
    title: String,
    episodeString: String,
    rating: String? = null,
    date: String?,
    watchProgress: String? = null,
    progressFraction: Float?,
    scanlator: String?,
    /** Provider-coverage note (19-05, D-03b) — "Missing provider" on
     *  canon episodes no readable carrier serves. Renders at the
     *  scanlator slot, dimmed-error; rows stay tappable. */
    note: String? = null,
    summary: String?,
    previewUrl: String?,
    seen: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    /** The next-in-line episode to watch — the card outline switches to
     *  the entry accent. */
    isNext: Boolean = false,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> ShowsDownload.State,
    downloadProgressProvider: () -> Int,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownloadClick: ((EpisodeDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val cardWidth = LocalConfiguration.current.screenWidthDp.dp * 0.75f
    // Watched episodes dim the still and every metadata element, not just the title
    val metaAlpha = if (seen) 0.45f else 1f

    // Contrast tint for the bare download icon: the top-right corner of the
    // still decides black vs white (no bounding chip, per Nuvio spec).
    var stillIsLight by remember(previewUrl) { mutableStateOf<Boolean?>(null) }
    val placeholderSurface = MaterialTheme.colorScheme.surfaceContainerHighest
    val downloadIconTint = when (stillIsLight ?: (placeholderSurface.luminance() > 0.4f)) {
        true -> Color.Black.copy(alpha = 0.75f)
        false -> Color.White.copy(alpha = 0.9f)
    }

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                1.dp,
                if (isNext) accent else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        // Full-bleed episode still (placeholder on missing/error)
        Box(modifier = Modifier.fillMaxSize().alpha(metaAlpha)) {
            if (!previewUrl.isNullOrBlank()) {
                val context = LocalContext.current
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(previewUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    error = { EpisodeStillPlaceholder(episodeString) },
                    onSuccess = { state ->
                        stillIsLight = state.result.image
                            ?.asDrawable(context.resources)
                            ?.getBitmapOrNull()
                            ?.let(::topRightRegionIsLight)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                EpisodeStillPlaceholder(episodeString)
            }
        }

        // Bottom-heavy gradient scrim (Nuvio: black 0.05 → 0.95)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.05f),
                            0.2f to Color.Black.copy(alpha = 0.2f),
                            0.5f to Color.Black.copy(alpha = 0.6f),
                            0.8f to Color.Black.copy(alpha = 0.85f),
                            1.0f to Color.Black.copy(alpha = 0.95f),
                        ),
                    ),
                ),
        )

        // Selection — child layer so it paints above the opaque still
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.5.dp, accent, RoundedCornerShape(16.dp)),
            )
        }

        // Top-left status cluster: watched → completed badge; partially
        // watched → "time watched" pill; bookmark beside either.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (seen) {
                // Completed badge (accent circle with white ring)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(accent, RoundedCornerShape(12.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            } else if (watchProgress != null) {
                // "Time watched" pill — same top-left slot, swapped for
                // the check badge once the episode finishes.
                Text(
                    text = watchProgress,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.55f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (bookmark) {
                // Bookmark marker
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            Color.Black.copy(alpha = 0.55f),
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

        // Download indicator (top-right) — tinted by the still's luminance
        // so it stays legible without a bounding chip
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (downloadIndicatorEnabled && onDownloadClick != null) {
                EpisodeDownloadIndicator(
                    enabled = downloadIndicatorEnabled,
                    downloadStateProvider = downloadStateProvider,
                    downloadProgressProvider = downloadProgressProvider,
                    onClick = onDownloadClick,
                    accentColor = downloadIconTint,
                )
            }
        }

        // Bottom content — badge, title, overview, metadata row
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (episodeString.isNotBlank()) {
                    Text(
                        text = episodeString,
                        color = Color.White.copy(alpha = 0.8f * metaAlpha),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = title,
                    color = Color.White.copy(alpha = if (seen) DISABLED_ALPHA else 1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        color = Color.White.copy(alpha = 0.9f * metaAlpha),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                CompositionLocalProvider(
                    LocalContentColor provides Color.White.copy(alpha = SECONDARY_ALPHA * metaAlpha),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Left cluster: release date + scanlator
                        // (truncates against the slack). Rating pins to
                        // the right edge.
                        if (date != null) {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                // Same color as the summary/description
                                // text, not the dimmer meta-channel color.
                                color = Color.White.copy(alpha = 0.9f * metaAlpha),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (note != null) {
                            Text(
                                text = note,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                                    .copy(alpha = SECONDARY_ALPHA),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        } else if (scanlator != null) {
                            Text(
                                text = scanlator,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        if (rating != null) {
                            val metaColor = LocalContentColor.current
                            ImdbRatingLabel(
                                rating = rating,
                                style = MaterialTheme.typography.labelSmall.merge(fontSize = 11.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = metaColor,
                                tint = metaColor,
                            )
                        }
                    }
                }
            }
        }

        // Bottom-edge progress bar (Nuvio: 3dp, white 20% track)
        if (progressFraction != null && !seen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.2f)),
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

/**
 * True when the top-right ~30%×25% corner of the still is light enough to
 * need a dark icon. The download indicator sits there with no scrim, so its
 * tint is derived from the actual pixels under it.
 */
private fun topRightRegionIsLight(bitmap: Bitmap): Boolean {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 8 || h < 8) return false
    val safe = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false
    } else {
        bitmap
    }
    val regionW = (w * 0.3f).toInt().coerceIn(1, w)
    val regionH = (h * 0.25f).toInt().coerceIn(1, h)
    val pixels = IntArray(regionW * regionH)
    safe.getPixels(pixels, 0, regionW, w - regionW, 0, regionW, regionH)
    var r = 0L
    var g = 0L
    var b = 0L
    var a = 0L
    for (p in pixels) {
        a += p ushr 24
        r += (p shr 16) and 0xFF
        g += (p shr 8) and 0xFF
        b += p and 0xFF
    }
    val n = pixels.size.toLong()
    val argb = ((a / n).toInt() shl 24) or
        ((r / n).toInt() shl 16) or
        ((g / n).toInt() shl 8) or
        (b / n).toInt()
    if (safe !== bitmap) safe.recycle()
    return ColorUtils.calculateLuminance(argb) > 0.4
}

@Composable
private fun EpisodeStillPlaceholder(episodeString: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = episodeString.ifBlank { "?" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
