package eu.kanade.presentation.entries.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.NAV_DOWNLOAD_SHARED_KEY
import eu.kanade.presentation.components.NAV_LEADING_ICON_SHARED_KEY
import eu.kanade.presentation.components.NAV_OVERFLOW_SHARED_KEY
import eu.kanade.presentation.components.NAV_PILL_SHARED_KEY
import eu.kanade.presentation.library.components.coverCellMemoryKey
import eu.kanade.presentation.util.LocalNavAnimatedVisibilityScope
import eu.kanade.presentation.util.LocalSharedTransitionScope
import eu.kanade.presentation.util.navTransitionProgress
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.useBackground
import tachiyomi.domain.entries.EntryCover
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.entries.shows.model.AnimeCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.DelayedLoadingScreen
import tachiyomi.presentation.core.util.clickableNoIndication
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

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
 * pull-to-refresh gesture scales the image up (capped at +15%), and
 * [parallaxOffsetPx] lags the image behind the scroll for depth
 * (`scrollY * 0.3`).
 */
@Composable
fun EnhancedEntryInfoBox(
    appBarPadding: Dp,
    title: String,
    backdrop: Any?,
    logoUrl: String?,
    metaItems: List<String>,
    rating: String?,
    /** IMDb glyph for Cinemeta scores; star for source-parsed ratings. */
    ratingMark: RatingMark = RatingMark.IMDB,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier,
    heroContentPadding: Dp = 320.dp,
    panHorizontally: Boolean = false,
    overscrollPullPx: Float = 0f,
    parallaxOffsetPx: Float = 0f,
    accentColor: Color? = null,
    /**
     * Shared-element key matching the home hero's — when set (and the
     * navigator's shared-transition scopes exist) the backdrop image morphs
     * from the home hero into this hero on push and back on pop.
     */
    sharedKey: Any? = null,
    /**
     * Artwork the caller already displays for this entry (the home hero's
     * image). Painted under the real backdrop inside the shared element —
     * without it the morphing box draws empty while Coil resolves a
     * different request model (entry object / season override vs the
     * preview's URL), which reads as the image rebuilding mid-transition.
     * Memory-cached, so it paints instantly; the real image crossfades
     * over it once loaded.
     */
    previewCover: Any? = null,
    /** The artwork the hero is actually targeting once settled (volume /
     * season override). After the push lands the preview underlay swaps
     * to it, so a later volume/season switch crossfades the hi-res layer
     * over *its own* artwork instead of letting the low-res navigation
     * bitmap peek through the semi-transparent midpoint. */
    liveCover: Any? = null,
    /** Skeleton use: hold the meta row's slot (and its shared key) empty
     *  while data loads, so the title sits at its final Y and the home
     *  genre line has a landing target. */
    reserveMeta: Boolean = false,
    /** Skeleton use: whether the reserved meta slot paints its ghost bar —
     *  hidden while the caller's slow-load delay hasn't elapsed. */
    metaGhostVisible: Boolean = true,
) {
    val panPrefEnabled by Injekt.get<UiPreferences>()
        .heroImagePanEnabled()
        .collectAsState()
    val panEnabled = panHorizontally && panPrefEnabled

    val navAnimScope = LocalNavAnimatedVisibilityScope.current
    val overshootDp = with(LocalDensity.current) { (overscrollPullPx * 0.5f).toDp() }
    Box(
        modifier = modifier
            .clipToBounds()
            // Fixed hero height — the title/meta tail is bottom-anchored,
            // so the wrap-height box put the start/continue row (the next
            // grid item) at a different Y per entry (logo up to 110dp vs
            // a two-line text title vs no meta). A constant tail pins the
            // bottom edge: the row sits at the same Y for every entry,
            // and the artwork element (matchParentSize) morphs between
            // identical bounds on every first-open.
            .height(appBarPadding + heroContentPadding + overshootDp + HERO_DETAIL_HERO_TAIL.dp)
            // Library covers fly here as "cover-image-$id". The key sits on
            // the WHOLE hero box — not the bare image — so sharedBounds'
            // content crossfade fades the cell into artwork + scrims +
            // title/meta during the morph, not instantly at handback.
            .then(heroSharedBoundsModifier(coverSharedKeyOf(sharedKey), HeroSharedKind.Cover))
            .clickableNoIndication(onClick = onCoverClick),
    ) {
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
        var heroWidthPx by remember { mutableIntStateOf(0) }
        // Shared clock with the home hero — see HeroPanClock.
        HeroPanDriver(panEnabled) { panSpeed }
        // The shared element is the image PLUS its scrims — shared content
        // renders in an overlay above everything during the morph, so a
        // bare image would fly over the title bright and undimmed. Carrying
        // the scrims keeps the transit render identical to the laid-out
        // one. The dimming gradient stays at full alpha inside the content
        // — the element's own content crossfade fades image+gradient as one
        // layer; ramping the gradient separately leaves the image visible
        // ahead of its dimming mid-flight ("image on top of the gradient").
        val sharedBoundsModifier = heroSharedBoundsModifier(sharedKey)
        val poseKey = sharedKey as? String
        val poseProgress = navTransitionProgress()
        // With a preview (the caller's memory-cached artwork) underneath,
        // the hi-res request stays hidden until the push lands — when it
        // resolved mid-flight it swapped a different bitmap/crop into the
        // morphing element. After the first landing it stays shown, so
        // pops fly the hi-res image.
        val navInTransit = navAnimScope.isNavInTransit()
        var hiResReady by remember { mutableStateOf(previewCover == null) }
        LaunchedEffect(navInTransit) { if (!navInTransit) hiResReady = true }
        // Same artwork under a different model object (nav bitmap's
        // MangaCover vs the live url string / volume override) must not
        // re-decode the underlay — only a genuinely different url swaps.
        fun coverUrlOf(model: Any?): String? = when (model) {
            is MangaCover -> model.url
            is NovelCover -> model.url
            is AnimeCover -> model.url
            is String -> model
            else -> null
        }
        // The layer composes at landing but fades only once Coil has a
        // decoded bitmap — a timed fade can outrun a slow decode and the
        // hi-res pops in mid-fade instead of arriving with it.
        var hiResDrawn by remember { mutableStateOf(false) }
        val hiResAlpha by animateFloatAsState(
            targetValue = if (hiResReady && hiResDrawn) 1f else 0f,
            animationSpec = tween(250),
            label = "hero_hires_alpha",
        )
        val imageContext = LocalContext.current
        val imageRequest = ImageRequest.Builder(imageContext)
            .data(backdrop)
            .useBackground(true)
            .crossfade(true)
            .build()
        // The library cell stores its decoded bitmap under coverCellMemoryKey
        // — the placeholder lookup skips Coil's cached-size validation (a
        // ~400px cell bitmap never satisfies a ~1344px hero request), so the
        // morphing element's target paints that bitmap from frame 0 instead
        // of decoding the same file at hero size mid-flight. Non-cover
        // previews (home hero URL/model) get no key and behave as before.
        // Mid-flight the underlay must stay the navigation bitmap (it's
        // what the origin cell painted); once landed it follows the live
        // artwork so volume/season swaps never expose it.
        val underlayCover = if (navInTransit ||
            liveCover == null ||
            (previewCover != null && coverUrlOf(liveCover) == coverUrlOf(previewCover))
        ) {
            previewCover
        } else {
            liveCover
        }
        val previewModel = remember(underlayCover) {
            coverCellMemoryKey(underlayCover as? EntryCover)?.let { key ->
                ImageRequest.Builder(imageContext)
                    .data(underlayCover)
                    .placeholderMemoryCacheKey(key)
                    .crossfade(true)
                    .build()
            } ?: underlayCover
        }
        val backgroundColor = MaterialTheme.colorScheme.background
        // Fixed element footprint identical to the home hero's and the
        // loading preview's (statusBar + contentPadding + TAIL tail) —
        // matchParentSize bound the element to the wrap-height hero box,
        // and appBarPadding (scaffold top padding ≈ 147dp) is ~100dp
        // taller than home's statusBarTop — either mismatch leaves the
        // element's bottom edge traveling mid-flight = the "gap joining
        // up" artifact. A taller title column simply extends below the
        // element onto background — visually identical, that region's
        // gradient is already ~opaque background.
        // The element spans the full hero box — artwork and its gradients
        // continue behind the real title/meta tail down to the solid
        // background edge, so there's no dead band above the action area.
        // Bounds morph absorbs the height delta vs the home element (its
        // fixed TAIL tail); RemeasureToBounds re-Crops per interpolated
        // bound so the bottom edge travels filled, never a gap.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clipToBounds()
                .then(sharedBoundsModifier)
                // Inner clip — the overlay skips the outer one, and the
                // blended parallax pose would spill past the scrim.
                .clipToBounds()
                .heroBottomFeather()
                // The pan width is measured here, not on the hi-res image —
                // that layer isn't composed until the push lands.
                .onSizeChanged { heroWidthPx = it.width },
        ) {
            // Missing/failed artwork: plain background, never a tint —
            // and it lives INSIDE the element so it rides the morph
            // instead of leaking into the vacated band mid-flight.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(backgroundColor),
            )
            if (underlayCover != null) {
                AsyncImage(
                    model = previewModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            // Pose continuity with the home hero — see
                            // applyHeroPose. Same pose as the real image
                            // below so the preview underlay never shears.
                            applyHeroPose(
                                key = poseKey,
                                side = HeroSide.Detail,
                                transit = navAnimScope.isNavInTransit(),
                                ownProgress = poseProgress,
                                tx = if (panEnabled) HeroPanClock.fraction.floatValue * heroWidthPx * 0.10f else 0f,
                                ty = parallaxOffsetPx * 0.3f,
                                scale = (baseScale + overscrollPullPx * 0.0005f + parallaxOffsetPx * 0.0001f)
                                    .coerceAtMost(baseScale + 0.15f),
                                neutralScale = baseScale,
                            )
                        },
                )
            }
            // Volume/season artwork swaps keep painting the last decoded
            // frame as the new request's placeholder — without it a model
            // change resets to empty and the low-res preview underlay
            // flashes through while the replacement loads.
            var previousPainter by remember { mutableStateOf<Painter?>(null) }
            // Not composed at all until the push lands — a second decode of
            // the same file mid-flight competes with the frames the morph
            // needs, and it renders nothing while hidden anyway.
            if (hiResReady) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = previousPainter,
                    onSuccess = { success ->
                        previousPainter = success.painter
                        hiResDrawn = true
                    },
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            applyHeroPose(
                                key = poseKey,
                                side = HeroSide.Detail,
                                transit = navAnimScope.isNavInTransit(),
                                ownProgress = poseProgress,
                                tx = if (panEnabled) HeroPanClock.fraction.floatValue * heroWidthPx * 0.10f else 0f,
                                ty = parallaxOffsetPx * 0.3f,
                                scale = (baseScale + overscrollPullPx * 0.0005f + parallaxOffsetPx * 0.0001f)
                                    .coerceAtMost(baseScale + 0.15f),
                                neutralScale = baseScale,
                            )
                            alpha = hiResAlpha
                        },
                )
            }

            // Scrim: transparent to background
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

            // Top scrim so back/overflow icons stay legible over
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
        }

        // Hero content: logo, meta row. The column is bottom-anchored, so
        // the box itself growing with the pull pushes the title, meta row
        // AND every list item below the hero downward, matching Nuvio's
        // whole-content rubber-band ("soft UI overshoot") while the image
        // stays pinned to the top edge and zooms. The top padding is only
        // the appBar: a long title must be free to grow UPWARD over the
        // artwork region — padding it out of heroContentPadding clipped
        // two-line titles against the box's clipToBounds. Elements below
        // (meta, start row) never move — they pin to the bottom edge.
        //
        // Title and meta are separate shared elements matching the home
        // hero's — the title only glides/scales into place (Fit, it already
        // exists on both sides) while the meta row crossfades with the home
        // genre line. Both ride above the artwork element's overlay z.
        val heroKeyPrefix = (sharedKey as? String)
            ?.takeIf { it.startsWith("hero-image-") }
            ?.removePrefix("hero-image-")
        val titleSharedKey = heroKeyPrefix?.let { "hero-title-$it" }
        val metaSharedKey = heroKeyPrefix?.let { "hero-meta-$it" }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = appBarPadding,
                    bottom = 16.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            run {
                val textAlign = TextAlign.Center
                Box(
                    modifier = Modifier.then(
                        heroSharedBoundsModifier(titleSharedKey, HeroSharedKind.Text),
                    ),
                ) {
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
                                .heightIn(max = 72.dp)
                                .widthIn(max = 320.dp),
                            error = {
                                EnhancedTitleText(title, textAlign)
                            },
                        )
                    } else {
                        EnhancedTitleText(title, textAlign)
                    }
                }

                // The meta slot (author/source/genre line) keeps its height
                // even when there's nothing to show — the skeleton reserves
                // it, and an empty slot on the real screen would drop the
                // bottom-anchored title ~28dp on swap for meta-less entries.
                val metaGhostHeight = with(LocalDensity.current) {
                    MaterialTheme.typography.bodyMedium.lineHeight.toDp()
                }
                if (reserveMeta && metaItems.isEmpty() && rating == null) {
                    // Skeleton ghost bar — the slot reads as a placeholder
                    // instead of dead space. The bar must match the real
                    // bodyMedium row's line height — a shorter ghost grows
                    // the bottom-anchored tail on swap and visibly pushes
                    // the title upward.
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(width = 180.dp, height = metaGhostHeight)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                MaterialTheme.colorScheme.onBackground
                                    .copy(alpha = if (metaGhostVisible) 0.12f else 0f),
                            )
                            .then(heroSharedBoundsModifier(metaSharedKey, HeroSharedKind.Text)),
                    )
                } else if (metaItems.isNotEmpty() || rating != null) {
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .then(
                                heroSharedBoundsModifier(metaSharedKey, HeroSharedKind.Text),
                            ),
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
                                mark = ratingMark,
                            )
                        }
                    }
                } else {
                    // No meta at all — hold the slot invisible so the title
                    // keeps the same Y it had during the skeleton.
                    Spacer(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .height(metaGhostHeight),
                    )
                }
            }
        }

        // Partner for the home glass pill's shared element — invisible
        // anchor at the toolbar's trailing slot; the pill's surface fill
        // collapses into this corner alongside the overflow icon.
        // (Mirrors the loading preview's anchor so both states pair;
        // toolbarIconTop replicates AppBar's status-bar math so the
        // flight endpoint equals the loaded toolbar's slot.)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues()
                        .calculateTopPadding() + 4.dp,
                    end = 4.dp,
                )
                .size(48.dp)
                .alpha(0f)
                .then(
                    heroSharedBoundsModifier(
                        NAV_PILL_SHARED_KEY,
                        HeroSharedKind.Container,
                    ),
                ),
        )
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

/**
 * Which hero piece a shared element carries — drives the resize mode and
 * overlay z-order. Image = the artwork + scrims (RemeasureToBounds — the
 * endpoints' heights differ by the title tail, so the element must
 * relayout and re-Crop at each interpolated bound; ScaleToBounds would
 * interpolate a scaled snapshot and read as a rebuild, the same reason
 * the pill uses Remeasure). Text = title / genre-meta lines (Fit — the
 * text moves and scales as a unit, never reflows or rebuilds mid-flight).
 * Container = the resume pill ↔ continue bar. Content kinds sit above
 * the artwork so nothing is covered mid-transition.
 */
internal enum class HeroSharedKind { Image, Text, Container, NavIcon, Cover }

/**
 * Bounds spec for every shared element here: the screen transition
 * hands back to real content at ~300ms, but the default
 * (SharedTransitionDefaults.BoundsTransform) is a StiffnessMediumLow
 * spring that is still traveling at handback — every element then
 * snaps its last few dp to target at the same instant (the
 * simultaneous image+icon "jump" at the end of pop). A tween bound to
 * the transition length converges exactly when the overlay drops.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private val HERO_BOUNDS_TRANSFORM = BoundsTransform { _, _ ->
    tween<Rect>(durationMillis = 300)
}

internal enum class HeroSide { Home, Detail }

/** "hero-image-42" → "cover-image-42" — the key library covers fly under. */
private fun coverSharedKeyOf(sharedKey: Any?): String? = (sharedKey as? String)
    ?.takeIf { it.startsWith("hero-image-") }
    ?.let { "cover-image-" + it.removePrefix("hero-image-") }

/**
 * One ambient pan clock shared by every hero (home, loading preview,
 * detail). Each screen used to run its own infinite transition from phase
 * 0, so opening a detail restarted the drift. With one clock the detail
 * picks up exactly where home was, and the pan keeps moving through the
 * transition. Speed scales the phase (pull-to-stop), so position never
 * snaps. Several composed heroes may drive it; it advances once per frame.
 */
internal object HeroPanClock {
    private const val SWEEP_MS = 14000.0
    private var phaseMs = 0.0
    private var lastFrameNanos = 0L

    /** -1..1, eased back and forth — multiply by width * 0.10 for px. */
    val fraction = mutableFloatStateOf(-1f)

    fun advance(frameNanos: Long, speed: Float) {
        if (frameNanos <= lastFrameNanos) return
        // Clamp: no frames while backgrounded must not fast-forward.
        val dtMs = if (lastFrameNanos == 0L) 0.0 else ((frameNanos - lastFrameNanos) / 1e6).coerceAtMost(100.0)
        lastFrameNanos = frameNanos
        phaseMs += dtMs * speed
        val cycle = (phaseMs % (2 * SWEEP_MS)) / SWEEP_MS
        val sweep = if (cycle <= 1.0) cycle else 2.0 - cycle
        fraction.floatValue = -1f + 2f * FastOutSlowInEasing.transform(sweep.toFloat())
    }
}

/** Drives [HeroPanClock] while this hero is composed and panning. */
@Composable
internal fun HeroPanDriver(enabled: Boolean, speed: () -> Float) {
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) withFrameNanos { HeroPanClock.advance(it, speed()) }
    }
}

/**
 * Feathers the bottom of a hero shared element. Mid-transit the overlay
 * draws the element's opaque background-coloured tail straight over the
 * cross-fading screens, and the hard bottom edge reads as a thin line
 * travelling with the image. Fading the last stretch to transparent hides
 * that edge. At rest the element sits over the same background colour, so
 * nothing visibly changes.
 */
internal fun Modifier.heroBottomFeather(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = 56.dp.toPx().coerceAtMost(size.height)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * True for every frame of a nav transition, including the first. On that
 * first frame the target state has already changed but `isRunning` is
 * still false. Checking `isRunning` alone let the pose render one frame at
 * its resting value (image jumped up on push, down on pop).
 */
internal fun AnimatedVisibilityScope?.isNavInTransit(): Boolean =
    this?.transition?.let { it.isRunning || it.currentState != it.targetState } == true

/**
 * True only while this side is the ENTERING screen of a nav transition
 * (targetState = Visible). The detail skeleton keys on this: a Success
 * arriving mid-flight must not swap the composition under the morph —
 * the mount is a ~140ms UI-thread stall plus a shared-element re-resolve.
 * On pop (targetState != Visible) this stays false, so the loaded screen
 * remains while it exits.
 */
internal fun AnimatedVisibilityScope?.isNavEntering(): Boolean =
    isNavInTransit() && this?.transition?.targetState == EnterExitState.Visible

/**
 * Last at-rest in-element pose (pan X, parallax Y, zoom) of each hero
 * image, per shared key and side. The scroll parallax/zoom/pan live
 * INSIDE the shared element (graphicsLayer on the image), so the bounds
 * morph never sees them — the old code zeroed them for the whole transit
 * and restored them at handback, which teleported the artwork by the
 * full scroll parallax whenever the user wasn't at scroll 0.
 */
internal object HeroPoseRegistry {
    private val poses = HashMap<String, FloatArray>()
    private val frozen = HashMap<String, FloatArray>()
    fun record(key: String, side: HeroSide, tx: Float, ty: Float, scale: Float) {
        poses["$side|$key"] = floatArrayOf(tx, ty, scale)
    }

    /** First transit frame freezes this side's pose — pan drift keeps
     *  running on the destination, which made the blend target move and
     *  the artwork visibly wobble left/right. */
    fun freezeOwn(key: String, side: HeroSide, tx: Float, ty: Float, scale: Float) {
        frozen.putIfAbsent("$side|$key", floatArrayOf(tx, ty, scale))
    }
    fun unfreeze(key: String, side: HeroSide) {
        frozen.remove("$side|$key")
    }
    fun frozenOwn(key: String, side: HeroSide): FloatArray? = frozen["$side|$key"]
    fun other(key: String, side: HeroSide): FloatArray? {
        val other = if (side == HeroSide.Home) HeroSide.Detail else HeroSide.Home
        return frozen["$other|$key"] ?: poses["$other|$key"]
    }
}

/**
 * Applies a hero image's in-element pose with continuity across the nav
 * transition. At rest: the live pose, recorded for the opposite side.
 * In transit (this side is the incoming/target content the overlay
 * draws): blend from the opposite side's recorded pose to this side's
 * live pose on the bounds' own clock (300ms FastOutSlowIn), so content
 * and bounds land together — no snap at start or handback.
 * [ownProgress] = [navTransitionProgress] (1 = this side settled).
 */
internal fun GraphicsLayerScope.applyHeroPose(
    key: String?,
    side: HeroSide,
    transit: Boolean,
    ownProgress: Float,
    tx: Float,
    ty: Float,
    scale: Float,
    neutralScale: Float,
) {
    var x = tx
    var y = ty
    var s = scale
    if (!transit) {
        if (key != null) {
            HeroPoseRegistry.record(key, side, tx, ty, scale)
            HeroPoseRegistry.unfreeze(key, side)
        }
    } else {
        if (key != null) HeroPoseRegistry.freezeOwn(key, side, tx, ty, scale)
        val from = key?.let { HeroPoseRegistry.other(it, side) }
            ?: floatArrayOf(0f, 0f, neutralScale)
        val to = key?.let { HeroPoseRegistry.frozenOwn(it, side) }
            ?: floatArrayOf(tx, ty, scale)
        val e = FastOutSlowInEasing.transform(ownProgress.coerceIn(0f, 1f))
        // x stays live: every side reads the shared HeroPanClock, so both
        // ends already agree and the pan keeps drifting through transit.
        y = from[1] + (to[1] - from[1]) * e
        s = from[2] + (to[2] - from[2]) * e
    }
    // Whole-pixel translations: a fractional pose draws the image edge a
    // half-pixel off the scrim's end and reads as a hairline bright seam.
    translationX = x.roundToInt().toFloat()
    translationY = y.roundToInt().toFloat()
    scaleX = s
    scaleY = s
}

/**
 * Approximate height of the detail hero's bottom-aligned title/meta tail —
 * title (≤110dp logo or ≤2-line text) + 8dp gap + ~20dp meta row + 16dp
 * bottom. The home hero's image element and the loading preview size their
 * shared element as `statusBar + heroContentPadding + TAIL` so the artwork
 * Crop frames the same slice on every endpoint — a taller home box would
 * height-bound the Crop and read as more zoomed-in than the detail. The
 * element's bounds morph absorbs the residual few dp.
 */
internal const val HERO_DETAIL_CONTENT_TAIL = 110f

/**
 * Fixed bottom tail for the DETAIL hero box (the home element/preview use
 * [HERO_DETAIL_CONTENT_TAIL]). The detail's title/meta block is
 * bottom-anchored with 16dp bottom padding; the cap is logo ≤72dp + 8dp
 * gap + ~24dp meta + 16dp bottom = 120dp. A constant keeps the bottom
 * edge — and therefore the start/continue row below it — at the same Y
 * for every entry, regardless of title/logo height. The logo is capped
 * at 72dp (not 110) so the fixed tail stays tight and nothing clips.
 */
internal const val HERO_DETAIL_HERO_TAIL = 120f

/**
 * Builds the `sharedBounds` modifier for hero content shared with the other
 * screen under [sharedKey], or [Modifier] when no key is provided or the
 * navigator's transition scopes aren't present (previews, tests).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun heroSharedBoundsModifier(
    sharedKey: Any?,
    kind: HeroSharedKind = HeroSharedKind.Image,
): Modifier {
    if (sharedKey == null) return Modifier
    val sharedScope = LocalSharedTransitionScope.current ?: return Modifier
    val animScope = LocalNavAnimatedVisibilityScope.current ?: return Modifier
    return with(sharedScope) {
        if (kind == HeroSharedKind.Image || kind == HeroSharedKind.NavIcon) {
            // sharedElement, not sharedBounds: a bounds morph crossfades
            // the two contents, so mid-flight the element sits at ~75%
            // alpha and the theme background bleeds through — a darken
            // pulse in dark mode, a brighten pulse in light mode. For
            // NavIcon the same crossfade reads as an alpha dip/two
            // overlapping icons since all endpoints paint the same glyph
            // (home toolbar ↔ loading preview ↔ loaded toolbar).
            // sharedElement treats both sides as the same element: the
            // target content renders at full opacity and only the bounds
            // animate — the instant handoff is invisible on identical
            // content.
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = sharedKey),
                animatedVisibilityScope = animScope,
                boundsTransform = HERO_BOUNDS_TRANSFORM,
                // NavIcon must sit above every bounds-morph element —
                // sharedBounds hardcodes 1f, so equal zIndex lets the
                // pill's collapsing block cover the icon mid-flight.
                zIndexInOverlay = if (kind == HeroSharedKind.NavIcon) 2f else 0f,
            )
        } else {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = sharedKey),
                animatedVisibilityScope = animScope,
                boundsTransform = HERO_BOUNDS_TRANSFORM,
                resizeMode = when (kind) {
                    HeroSharedKind.Container ->
                        SharedTransitionScope.ResizeMode.RemeasureToBounds
                    HeroSharedKind.Text ->
                        SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit, Alignment.Center)
                    // Library cover ↔ hero box: each side is laid out ONCE
                    // at its own size and scaled with the bounds. Remeasure
                    // re-Cropped the hero around its centre every frame —
                    // the "grows from the middle" artifact — instead of the
                    // whole picture slotting in along the flight path.
                    HeroSharedKind.Cover ->
                        SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop, Alignment.Center)
                    else -> error("unreachable")
                },
                zIndexInOverlay = 1f,
            )
        }
    }
}

/**
 * Loading-state stand-in for the hero artwork: renders the cover the caller
 * already knows (e.g. the home hero image passed as `previewCover`) inside
 * the same shared-element key as the real hero, so the artwork morphs across
 * the push transition even on a cold first open. When the Success state
 * lands, the same key lives on the real hero and the element settles into
 * place. Falls back to a plain delayed spinner without [cover].
 */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun EntryHeroLoadingPreview(
    cover: Any?,
    sharedKey: Any?,
    heroContentPadding: Dp,
    modifier: Modifier = Modifier,
    /**
     * Entry title rendered inside the `hero-title-` shared element — the
     * counterpart to the home hero's title and the loaded hero's title
     * block, so the text morphs continuously through Loading instead of
     * popping off when the push animation ends.
     */
    title: String? = null,
    /** Cinemeta clearlogo for the shared title element — same content the
     *  home hero and the loaded hero render for logo-bearing entries. */
    logo: String? = null,
    /** Whether the counterpart heroes oversize the image 25% for the
     *  horizontal pan — the preview matches that crop so the morph doesn't
     *  visibly zoom mid-flight. */
    panOversize: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (cover != null) {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            // The shared-element anchors must land exactly where the
            // loaded EntryToolbar's icon slots sit: AppBar pads by the
            // fixed `status_bar_height` dimen (not the live inset) and
            // EntryToolbar's TopAppBar is 56dp (icon slot top = +4,
            // center = +28 — matching home's 44dp row + 6 padding).
            val anchorContext = LocalContext.current
            val anchorDensity = LocalDensity.current
            val toolbarIconTop = remember {
                val res = anchorContext.resources
                val id = res.getIdentifier("status_bar_height", "dimen", "android")
                val bar = if (id > 0) {
                    with(anchorDensity) { res.getDimensionPixelSize(id).toDp() }
                } else {
                    0.dp
                }
                bar + 4.dp
            }
            // Approximate the hero's eventual height — the real box is
            // appBar + contentPadding + the bottom-aligned title/meta
            // block (HERO_DETAIL_CONTENT_TAIL). The shared element animates
            // the last few dp when Success lands. The scrims ride inside
            // the shared element so the artwork stays dimmed under its
            // gradients mid-morph, same as the real hero.
            val previewBackground = MaterialTheme.colorScheme.background
            val heroKeyPrefix = (sharedKey as? String)
                ?.takeIf { it.startsWith("hero-image-") }
                ?.removePrefix("hero-image-")
            val panPrefEnabled by Injekt.get<UiPreferences>()
                .heroImagePanEnabled()
                .collectAsState()
            val previewAnimScope = LocalNavAnimatedVisibilityScope.current
            val previewProgress = navTransitionProgress()
            var previewWidthPx by remember { mutableIntStateOf(0) }
            HeroPanDriver(panOversize && panPrefEnabled) { 1f }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarTop + heroContentPadding + HERO_DETAIL_CONTENT_TAIL.dp)
                    // Cover shared elements land on the whole preview box so
                    // their content crossfade picks up the scrims, same as
                    // the loaded hero box.
                    .then(
                        heroSharedBoundsModifier(
                            heroKeyPrefix?.let { "cover-image-$it" },
                            HeroSharedKind.Cover,
                        ),
                    )
                    .align(Alignment.TopCenter),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(heroSharedBoundsModifier(sharedKey))
                        // Clip inside the element: the pose blends in from
                        // the home hero's parallax offset mid-flight.
                        .clipToBounds()
                        .heroBottomFeather()
                        .onSizeChanged { previewWidthPx = it.width },
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(cover)
                            .useBackground(true)
                            .crossfade(true)
                            // Paints the library cell's already-decoded
                            // bitmap instantly — see EnhancedEntryInfoBox.
                            .placeholderMemoryCacheKey(
                                coverCellMemoryKey(cover as? EntryCover),
                            )
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Match the live heroes' pan oversize so
                                // the shared element's crop is consistent
                                // across the morph; the pose blends in
                                // from the home hero's scrolled/panned
                                // pose instead of snapping to neutral.
                                val previewPans = panOversize && panPrefEnabled
                                val scale = if (previewPans) 1.25f else 1f
                                applyHeroPose(
                                    key = sharedKey as? String,
                                    side = HeroSide.Detail,
                                    transit = previewAnimScope.isNavInTransit(),
                                    ownProgress = previewProgress,
                                    // Same shared pan clock as home/detail
                                    // so the drift never restarts mid-open.
                                    tx = if (previewPans) {
                                        HeroPanClock.fraction.floatValue * previewWidthPx * 0.10f
                                    } else {
                                        0f
                                    },
                                    ty = 0f,
                                    scale = scale,
                                    neutralScale = scale,
                                )
                            },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            previewBackground,
                                        ),
                                    ),
                                )
                            },
                    )
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
                }
                // Content shared elements — the home hero's title morphs
                // onto this title while the real data loads, then hands
                // off to the loaded hero's title block. Same key derivation
                // as EnhancedEntryInfoBox; a sibling of the image element,
                // matching the real hero. The empty meta/resume anchors give
                // the home genre line and resume pill somewhere to morph
                // into — without counterparts they'd freeze mid-screen and
                // pop off when the push animation ends.
                if (!logo.isNullOrBlank() || !title.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                            .then(
                                heroSharedBoundsModifier(
                                    heroKeyPrefix?.let { "hero-title-$it" },
                                    HeroSharedKind.Text,
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Same content rule as the real hero — the logo
                        // image for logo-bearing entries, the text title
                        // otherwise — so the shared element's endpoints
                        // are identical and the morph is a pure move.
                        if (!logo.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(logo)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = title,
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.Center,
                                modifier = Modifier
                                    .heightIn(max = 72.dp)
                                    .widthIn(max = 320.dp),
                                error = {
                                    EnhancedTitleText(title ?: "", TextAlign.Center)
                                },
                            )
                        } else {
                            EnhancedTitleText(title ?: "", TextAlign.Center)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(24.dp)
                        .then(
                            heroSharedBoundsModifier(
                                heroKeyPrefix?.let { "hero-meta-$it" },
                                HeroSharedKind.Text,
                            ),
                        ),
                )
            }
            // Resume-pill landing pad — the home pill morphs width-wise
            // into the detail's continue bar; this empty anchor sits where
            // the real bar lands (below the hero box).
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues()
                            .calculateTopPadding() + heroContentPadding +
                            HERO_DETAIL_CONTENT_TAIL.dp + 12.dp,
                        start = 16.dp,
                        end = 16.dp,
                    )
                    .fillMaxWidth()
                    .height(60.dp)
                    .then(
                        heroSharedBoundsModifier(
                            heroKeyPrefix?.let { "hero-resume-$it" },
                            HeroSharedKind.Container,
                        ),
                    ),
            )
            // Partner for the home top bar's shared overflow icon — the
            // real EntryToolbar only exists once state loads, so without
            // this anchor the icon's shared element has no destination
            // mid-push and pops out. Anchored where the toolbar's action
            // row lands (top-end, under the status bar).
            IconButton(
                onClick = { },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = toolbarIconTop, end = 4.dp)
                    .size(48.dp)
                    .onGloballyPositioned {
                        android.util.Log.d(
                            "NavIconPos",
                            "preview pos=${it.positionInRoot()} size=${it.size}",
                        )
                    }
                    .then(
                        heroSharedBoundsModifier(
                            NAV_OVERFLOW_SHARED_KEY,
                            HeroSharedKind.NavIcon,
                        ),
                    ),
            ) {
                Icon(
                    rememberAnimatedVectorPainter(
                        AnimatedImageVector.animatedVectorResource(R.drawable.anim_more_enter),
                        false,
                    ),
                    contentDescription = null,
                )
            }
            // Partner for the detail toolbar's download action — sits one
            // slot left of the overflow anchor so the icon glides through
            // Loading to the loaded toolbar's actions row without a pop.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = toolbarIconTop, end = 52.dp)
                    .size(48.dp)
                    .alpha(0f)
                    .then(
                        heroSharedBoundsModifier(
                            NAV_DOWNLOAD_SHARED_KEY,
                            HeroSharedKind.Text,
                        ),
                    ),
            )
            // Partner for the home glass pill's shared element — the pill
            // collapses into this toolbar-corner slot (crossfade to an
            // invisible endpoint = dissolve-shrink). Position matches the
            // overflow anchor so the pill folds into the same corner the
            // three-dot flies to.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = toolbarIconTop, end = 4.dp)
                    .size(48.dp)
                    .alpha(0f)
                    .then(
                        heroSharedBoundsModifier(
                            NAV_PILL_SHARED_KEY,
                            HeroSharedKind.Container,
                        ),
                    ),
            )
            // Partner for the home bar's leading icon slot — a real back
            // arrow at the toolbar's up position so the icon crossfades
            // in place through Loading and hands off to the EntryToolbar's
            // up button without a pop.
            IconButton(
                onClick = { },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = toolbarIconTop, start = 4.dp)
                    .size(48.dp)
                    .then(
                        heroSharedBoundsModifier(
                            NAV_LEADING_ICON_SHARED_KEY,
                            HeroSharedKind.Text,
                        ),
                    ),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                )
            }
        }
        DelayedLoadingScreen()
    }
}

/**
 * The single hero-title treatment shared by the home hero, the loading
 * preview, and this detail hero — identical content on both ends of the
 * `hero-title-*` shared element means the transition is a pure move
 * (bounds glide), not a content crossfade/rebuild.
 *
 * Typographic orphan control lives here so every endpoint agrees: the
 * last two words are joined by a non-breaking space, so a wrap never
 * leaves a single word on the second line ("hidden class gravity /
 * user" becomes "hidden class / gravity user"). Only for 4+ word
 * titles — with fewer, the joined pair can overflow the line and the
 * wrap then splits mid-word, producing a worse orphan ("Ultra-Alternate
 * Charact" / "er") than the one it was preventing.
 */
@Composable
internal fun EnhancedTitleText(
    title: String,
    textAlign: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier,
) {
    val shadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.86f),
            offset = Offset(0f, 2.5f),
            blurRadius = 10f,
        )
    }
    val displayTitle = remember(title) {
        title.ifBlank { null }?.let { t ->
            val lastSpace = t.lastIndexOf(' ')
            if (lastSpace > 0 && t.count { it == ' ' } >= 3) {
                t.replaceRange(lastSpace, lastSpace + 1, " ")
            } else {
                t
            }
        }
    }
    Text(
        text = displayTitle ?: stringResource(MR.strings.unknown_title),
        modifier = modifier,
        style = MaterialTheme.typography.displaySmall.merge(
            TextStyle(shadow = shadow),
        ),
        color = Color.White,
        fontWeight = FontWeight.Black,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}
