package miko.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import miko.ui.cover.hslToColor
import miko.ui.cover.rgbToHsl
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.i18n.pluralStringResource
import kotlin.math.roundToInt

/**
 * Android's chapter/episode carousels — the details item list is a
 * horizontal snap row of 0.75×screen cards (Nuvio episode-card styling),
 * not a vertical list. `ChapterCarouselCard` covers manga/novel (tinted
 * surface, no still); `EpisodeStillCard` covers shows (full-bleed still
 * under a bottom scrim). Long-press selects — swipe actions are absent
 * upstream too (they fight the row scroll).
 */

private const val CARD_WIDTH_FRACTION = 0.75f

/** Accent-hue surface — Android's `accentTintedSurface` (pure-Kotlin HSL). */
private fun accentTintedSurface(accent: Color, background: Color, dimmed: Boolean): Color {
    val accentHsl = rgbToHsl(accent.toArgb())
    val bgHsl = rgbToHsl(background.toArgb())
    val direction = if (accentHsl[2] >= bgHsl[2]) 1f else -1f
    val lightness = (bgHsl[2] + direction * TINTED_SURFACE_STEP).coerceIn(0f, 1f)
    val dimmedLightness = if (direction > 0f) {
        (lightness - TINTED_SURFACE_DIMMED_DELTA).coerceAtLeast(bgHsl[2] + 0.012f)
    } else {
        (lightness - TINTED_SURFACE_DIMMED_DELTA).coerceIn(0f, 1f)
    }
    return hslToColor(
        accentHsl[0],
        accentHsl[1] * TINTED_SURFACE_SATURATION,
        if (dimmed) dimmedLightness else lightness,
    )
}

private const val TINTED_SURFACE_STEP = 0.075f
private const val TINTED_SURFACE_DIMMED_DELTA = 0.035f
private const val TINTED_SURFACE_SATURATION = 0.38f

/**
 * Port of Android's `EntryChapterCarouselCard` — 0.75×screen tinted card:
 * status cluster top-left (read check / progress pill / bookmark),
 * indicators top-right, title + date/scanlator meta row pinned bottom.
 */
@Composable
fun ChapterCarouselCard(
    title: String,
    dateLabel: String,
    scanlator: String?,
    progress: String?,
    read: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    isNext: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    trailing: (@Composable (tint: Color) -> Unit)? = null,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val cardWidth = LocalConfiguration.current.screenWidthDp.dp * CARD_WIDTH_FRACTION
    val cardColor = accentTintedSurface(
        accent = accent,
        background = MaterialTheme.colorScheme.background,
        dimmed = read,
    )
    val readCheckTint = if (cardColor.luminance() > 0.5f) Color.Black else Color.White
    val indicatorTint = if (cardColor.luminance() > 0.4f) {
        Color.Black.copy(alpha = 0.75f)
    } else {
        Color.White.copy(alpha = 0.9f)
    }

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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(accent.copy(alpha = 0.18f)),
            )
        }

        // Top-left status cluster — read check, progress pill, bookmark.
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

        // Indicators top-right.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailing?.invoke(indicatorTint)
        }

        // Bottom content — title, then date (left) + scanlator (right).
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
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground
                        .copy(alpha = SECONDARY_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!scanlator.isNullOrBlank()) {
                    Text(
                        text = scanlator,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onBackground
                            .copy(alpha = SECONDARY_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Port of Android's `NuvioEpisodeCard` — the episode still fills the
 * card under a bottom-heavy gradient: SxEx badge chip, title, overview,
 * date + scanlator meta, top-left seen/progress/bookmark cluster,
 * download indicator top-right, bottom-edge progress bar.
 */
@Composable
fun EpisodeStillCard(
    title: String,
    episodeString: String,
    date: String?,
    watchProgress: String?,
    progressFraction: Float?,
    scanlator: String?,
    summary: String?,
    previewUrl: String?,
    seen: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    isNext: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    trailing: (@Composable (tint: Color) -> Unit)? = null,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val cardWidth = LocalConfiguration.current.screenWidthDp.dp * CARD_WIDTH_FRACTION
    val metaAlpha = if (seen) 0.45f else 1f
    // Without a still-luminance probe the indicator gets a scrim chip —
    // legible on any image (Android probes the still's corner pixels).
    val indicatorTint = Color.White.copy(alpha = 0.9f)

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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(modifier = Modifier.fillMaxSize().alpha(metaAlpha)) {
            if (!previewUrl.isNullOrBlank()) {
                AsyncImage(
                    model = previewUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                EpisodeStillPlaceholder(episodeString)
            }
        }

        // Bottom-heavy gradient scrim.
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

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.5.dp, accent, RoundedCornerShape(16.dp)),
            )
        }

        // Top-left status cluster — seen badge, watch pill, bookmark.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (seen) {
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

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            trailing?.invoke(indicatorTint)
        }

        // Bottom content — badge, title, overview, metadata row.
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
                    LocalContentColor provides Color.White.copy(
                        alpha = SECONDARY_ALPHA * metaAlpha,
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (date != null) {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f * metaAlpha),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (scanlator != null) {
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
                    }
                }
            }
        }

        // Bottom-edge progress bar (3dp, white 20% track).
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

/** The missing-count separator as a carousel card (0.75w like siblings). */
@Composable
fun MissingCountCarouselCard(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .width(LocalConfiguration.current.screenWidthDp.dp * CARD_WIDTH_FRACTION)
            .height(132.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = pluralStringResource(
                AYMR.plurals.missing_items,
                count = count,
                count,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

/**
 * Port of Android's `EntryChapterScrubber` — slim track + thumb above the
 * carousel when it holds ≥[SCRUBBER_MIN_ITEMS] items; dragging maps the
 * finger's x-fraction onto the row's scroll range.
 */
@Composable
fun ItemCarouselScrubber(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
) {
    if (itemCount < SCRUBBER_MIN_ITEMS) return

    val scope = rememberCoroutineScope()
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val stridePx = with(LocalDensity.current) {
        (LocalConfiguration.current.screenWidthDp.dp * CARD_WIDTH_FRACTION + 12.dp).toPx()
    }
    val maxScrollPx = stridePx * (itemCount - 1).coerceAtLeast(1)
    var dragFraction by remember { mutableStateOf(-1f) }
    val listFraction by remember {
        derivedStateOf {
            (
                (
                    listState.firstVisibleItemIndex * stridePx +
                        listState.firstVisibleItemScrollOffset
                    ) / maxScrollPx
                ).coerceIn(0f, 1f)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 16.dp),
    ) {
        val trackW = constraints.maxWidth.toFloat()
        val thumbW = with(LocalDensity.current) { 36.dp.toPx() }
        val pendingIndex = remember { mutableIntStateOf(-1) }
        val scrollJob = remember { mutableStateOf<Job?>(null) }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.25f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val f = if (dragFraction >= 0f) dragFraction else listFraction
                    IntOffset(
                        x = (f * (trackW - thumbW)).toInt(),
                        y = 0,
                    )
                }
                .width(36.dp)
                .height(14.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(itemCount, trackW) {
                    detectHorizontalDragGestures(
                        onDragStart = { down ->
                            dragFraction = (down.x / trackW).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val nearest = pendingIndex.intValue
                                .takeIf { it >= 0 }
                                ?: (
                                    (
                                        listState.firstVisibleItemIndex * stridePx +
                                            listState.firstVisibleItemScrollOffset
                                        ) / stridePx
                                    ).toInt()
                            pendingIndex.intValue = -1
                            scrollJob.value?.cancel()
                            dragFraction = -1f
                            scope.launch {
                                listState.animateScrollToItem(nearest.coerceIn(0, itemCount - 1))
                            }
                        },
                        onDragCancel = {
                            pendingIndex.intValue = -1
                            scrollJob.value?.cancel()
                            dragFraction = -1f
                        },
                    ) { change, _ ->
                        change.consume()
                        val f = (change.position.x / trackW).coerceIn(0f, 1f)
                        dragFraction = f
                        pendingIndex.intValue =
                            (f * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                        if (scrollJob.value?.isActive != true) {
                            scrollJob.value = scope.launch {
                                while (isActive) {
                                    val idx = pendingIndex.intValue
                                    if (idx < 0) break
                                    pendingIndex.intValue = -1
                                    listState.scrollToItem(idx)
                                    withFrameNanos { }
                                }
                            }
                        }
                    }
                },
        )
    }
}

/** Show the scrubber only when the card row is long enough to need it. */
const val SCRUBBER_MIN_ITEMS = 40

/** "1h 5m" / "45m" — Android's `formatEpisodeRuntime`. */
fun formatEpisodeRuntime(milliseconds: Long): String {
    val totalMin = milliseconds / 60_000L
    return if (totalMin >= 60) {
        "${totalMin / 60}h ${totalMin % 60}m"
    } else {
        "${totalMin}m"
    }
}
