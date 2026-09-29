package eu.kanade.presentation.entries.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.coil.useBackground
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clickableNoIndication
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Enhanced detail hero — cinematic full-bleed backdrop with a centered title
 * logo (Cinemeta clearlogo when available) and a meta row. The Miko accent
 * tint still layers underneath so per-cover theming is kept.
 *
 * Landscape backdrops (anime series/movies) slowly pan horizontally like the
 * home hero when [panHorizontally] is on and the pan preference is enabled;
 * portrait covers (manga/novels) stay still.
 *
 * [overscrollPullPx] feeds the Nuvio-style stretch — pixel distance of the
 * pull-to-refresh gesture scales the image up (`pull * 0.002`, Nuvio's own
 * multiplier, capped at 1.4 like theirs), and [parallaxOffsetPx] lags the
 * image behind the scroll for depth (`scrollY * 0.3`, also Nuvio's factor).
 */
@Composable
fun EnhancedEntryInfoBox(
    appBarPadding: Dp,
    title: String,
    backdrop: Any?,
    logoUrl: String?,
    metaItems: List<String>,
    rating: String?,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier,
    heroContentPadding: Dp = 320.dp,
    panHorizontally: Boolean = false,
    overscrollPullPx: Float = 0f,
    parallaxOffsetPx: Float = 0f,
    accentColor: Color? = null,
) {
    val panPrefEnabled by Injekt.get<UiPreferences>()
        .heroImagePanEnabled()
        .collectAsState()
    val panEnabled = panHorizontally && panPrefEnabled

    // Same slow eased pan as the home hero — a single infinite float driving
    // a graphicsLayer translationX on an oversized image so edges never show.
    val panFraction by rememberInfiniteTransition(label = "hero_pan").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hero_pan_offset",
    )

    Box(
        modifier = modifier
            .clipToBounds()
            .clickableNoIndication(onClick = onCoverClick),
    ) {
        // Layer 1: accent tint (Miko-specific theming)
        if (accentColor != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(accentColor.copy(alpha = 0.2f)),
            )
        }

        // Layer 2: full-bleed backdrop — sharp, not blurred; the scrim
        // gradient handles readability. Scale is uniform so the image is
        // never distorted: pan oversizes it 25% for travel room, then the
        // Nuvio mechanics apply — a subtle zoom on pull (capped at +15% over
        // base) and a slow scroll zoom (scroll*0.0001). The rubber-band
        // "soft overshoot" comes from the hero box itself growing (see the
        // content Column's top padding below), not from the image shifting.
        //
        // The horizontal pan decelerates to a stop while the user pulls,
        // then accelerates back to full ambient speed on release — the
        // position accumulates speed-scaled deltas, so it never snaps.
        val baseScale = if (panEnabled) 1.25f else 1f
        val panSpeed by animateFloatAsState(
            targetValue = if (panEnabled && overscrollPullPx <= 0f) 1f else 0f,
            animationSpec = tween(900, easing = LinearOutSlowInEasing),
            label = "hero_pan_speed",
        )
        val panX = remember { mutableFloatStateOf(0f) }
        var lastPanFraction by remember { mutableStateOf<Float?>(null) }
        var heroWidthPx by remember { mutableIntStateOf(0) }
        LaunchedEffect(panEnabled, panFraction, panSpeed, heroWidthPx) {
            if (!panEnabled || heroWidthPx == 0) return@LaunchedEffect
            val last = lastPanFraction
            if (last == null) {
                panX.floatValue = panFraction * heroWidthPx * 0.10f
            } else {
                panX.floatValue += (panFraction - last) * heroWidthPx * 0.10f * panSpeed
            }
            lastPanFraction = panFraction
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(backdrop)
                .useBackground(true)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .onSizeChanged { heroWidthPx = it.width }
                .graphicsLayer {
                    val scale = (baseScale + overscrollPullPx * 0.0005f + parallaxOffsetPx * 0.0001f)
                        .coerceAtMost(baseScale + 0.15f)
                    scaleX = scale
                    scaleY = scale
                    if (panEnabled) {
                        translationX = panX.floatValue
                    }
                    translationY = parallaxOffsetPx * 0.3f
                },
        )

        // Layer 3: gradient scrim (transparent to background)
        val backgroundColor = MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                backgroundColor,
                            ),
                        ),
                    )
                },
        )

        // Layer 4: top scrim so back/overflow icons stay legible over
        // bright artwork.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // Hero content: logo, meta row. The top padding grows with the pull —
        // since everything is bottom-aligned this physically pushes the title,
        // meta row AND every list item below the hero downward, matching
        // Nuvio's whole-content rubber-band ("soft UI overshoot") while the
        // image stays pinned to the top edge and zooms.
        val overshootDp = with(LocalDensity.current) { (overscrollPullPx * 0.5f).toDp() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = appBarPadding + heroContentPadding + overshootDp,
                    bottom = 16.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            run {
                val textAlign = TextAlign.Center
                if (!logoUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(logoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .heightIn(max = 110.dp)
                            .widthIn(max = 320.dp),
                        error = {
                            EnhancedTitleText(title, textAlign)
                        },
                    )
                } else {
                    EnhancedTitleText(title, textAlign)
                }

                if (metaItems.isNotEmpty() || rating != null) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (metaItems.isNotEmpty()) {
                            Text(
                                text = metaItems.joinToString("  ·  "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA),
                                textAlign = textAlign,
                            )
                        }
                        rating?.let {
                            ImdbRatingLabel(
                                rating = it,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared status label for the enhanced meta row. SAnime/SManga/SNovel use the
 * same status int constants, so this covers all entry types.
 *
 * Returns null for statuses the release-date range already conveys (ongoing,
 * completed, publishing finished) — only hiatus and cancelled are worth a chip.
 */
@Composable
fun enhancedEntryStatusText(status: Long): String? {
    return when (status.toInt()) {
        5 -> stringResource(MR.strings.cancelled)
        6 -> stringResource(MR.strings.on_hiatus)
        else -> null
    }
}

@Composable
private fun EnhancedTitleText(title: String, textAlign: TextAlign = TextAlign.Center) {
    Text(
        text = title.ifBlank { stringResource(MR.strings.unknown_title) },
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}
