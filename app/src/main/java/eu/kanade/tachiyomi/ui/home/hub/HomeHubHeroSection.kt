package eu.kanade.tachiyomi.ui.home.hub

/*
 * Full-bleed hero carousel ported from Nuvio (GPL-3.0) —
 * github.com/NuvioMedia/NuvioMobile, file
 * composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeHeroSection.kt
 *
 * Ported pieces: the crossfade page layers driven by a userScrollEnabled-
 * off PagerState, the custom initial-pass swipe gesture
 * (homeHeroPagerGesture/resolveHeroTargetPage), background/content
 * parallax + scroll-driven scale, the 8s auto-advance, the expanding dot
 * indicator, and the scrim stack. Adapted to Miko's HomeHubHero (recently
 * consumed entries instead of MetaPreview catalog items): the content
 * block is a centered title + type meta + "Resume <chapter/episode>"
 * pill, the stretch-zoom hooks and tablet branches are dropped, and the
 * hero fills the full screen height.
 */

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import tachiyomi.presentation.core.util.clickableNoIndication
import tachiyomi.presentation.core.util.collectAsState
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.entries.components.EnhancedTitleText
import eu.kanade.presentation.entries.components.HERO_DETAIL_CONTENT_TAIL
import eu.kanade.presentation.entries.components.HeroSharedKind
import eu.kanade.presentation.entries.components.PreExtractEntryCoverColor
import eu.kanade.presentation.entries.components.heroSharedBoundsModifier
import eu.kanade.presentation.util.LocalNavAnimatedVisibilityScope
import eu.kanade.presentation.util.navTransitionFadeAlpha
import eu.kanade.tachiyomi.metadata.stream.StreamResolver
import eu.kanade.tachiyomi.util.EntryCoverMetadata
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.entries.shows.ShowsScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs

private const val HERO_BACKGROUND_PARALLAX = 0.055f
private const val HERO_CONTENT_PARALLAX = 0.18f
private const val HERO_SCROLL_PARALLAX = 0.3f
private const val HERO_SWIPE_THRESHOLD_FRACTION = 0.16f
private const val HERO_SWIPE_VELOCITY_THRESHOLD = 300f
private const val HERO_AUTO_SCROLL_INTERVAL_MS = 8_000L

/**
 * The entry whose detail screen a tap opens — the canonical entry
 * ([HomeHubHero.canonicalId]: showKey for Cinemeta-unified shows, the
 * unifier's `miko:` row for manga/novel) rather than the history row's
 * extension-side id. Pushing the canonical id skips the detail screen's
 * extension→canonical `navigator.replace`, which would run a second
 * transition and pop every shared element; all home-side shared keys
 * therefore key on this id to match `state.*.id` on the canonical entry.
 */
private val HomeHubHero.sharedEntryId: Long get() = canonicalId ?: entryId

/** The hero occupies ~3/4 of the screen height — catalogs peek above the
 *  fold instead of sitting a full page down. */
private const val HERO_HEIGHT_FRACTION = 0.72f

private data class HeroPageLayer(
    val itemIndex: Int,
    val visibility: Float,
    val offset: Float,
)

@Composable
fun HomeHubHeroSection(
    items: List<HomeHubHero>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    // Live pull distance in px from the tab's pullToRefresh stretch — read
    // inside the graphicsLayer so per-frame changes don't recompose.
    overscrollPx: () -> Float = { 0f },
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val playerPreferences: PlayerPreferences by injectLazy()
    val audiobookPlayer = remember { Injekt.get<AudiobookPlayerController>() }
    val getNovel = remember { Injekt.get<GetNovel>() }
    val streamResolver = remember { Injekt.get<StreamResolver>() }
    val preferenceStore = remember { Injekt.get<PreferenceStore>() }

    // Shared-transition bookkeeping. While the push/pop morph runs we
    // freeze the item list and the visible-page layers — history flows can
    // re-emit mid-flight and would otherwise remap itemIndex → entry, yank
    // the shared partners (image/title/pill pop = "rebuild"), and reassign
    // each dot's activeFraction (dot flicker). pendingHeroId pins the
    // tapped entry's page so the remounted home still hosts the shared
    // elements even when a reorder moved it.
    val navAnimScope = LocalNavAnimatedVisibilityScope.current
    val transitioning = navAnimScope?.transition?.let {
        it.isRunning || it.currentState != it.targetState
    } == true
    var pendingHeroId by rememberSaveable { mutableStateOf(-1L) }
    var frozenItems by remember { mutableStateOf(items) }
    var frozenPages by remember { mutableStateOf<List<HeroPageLayer>?>(null) }
    val renderItems = if (transitioning) frozenItems else items
    // Non-shared chrome (the dot pager below) fades out early on push and
    // back in early on pop — otherwise it sits under the flying image
    // element and blinks out / reappears at transition end.
    val navChromeAlpha = navTransitionFadeAlpha()

    val pagerState = key(items.size) {
        rememberPagerState(
            initialPage = if (items.size > 1) {
                Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2) % items.size
            } else {
                0
            },
            pageCount = { if (items.size > 1) Int.MAX_VALUE else 1 },
        )
    }

    // Auto-advance every 8s while the page is settled (Nuvio parity) —
    // but never mid-nav-transition: an auto-scroll tick shifts the layer
    // offsets and drags the shared hero element's bounds sideways while
    // it's in flight ("image jumps right then corrects").
    val settledPage = pagerState.settledPage
    androidx.compose.runtime.LaunchedEffect(settledPage, items.size) {
        if (items.size <= 1) return@LaunchedEffect
        delay(HERO_AUTO_SCROLL_INTERVAL_MS)
        while (pagerState.isScrollInProgress ||
            navAnimScope?.transition?.isRunning == true
        ) {
            delay(100L)
        }
        pagerState.animateScrollToPage(pagerState.currentPage + 1)
    }

    // Resume the entry where the user left it — same logic the old hero
    // card used (Cinemeta entries resolve to a mapped episode first).
    val resumeHero: (HomeHubHero) -> Unit = { hero ->
        when (hero.mediaType) {
            HomeHubMediaType.SHOWS -> {
                val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
                scope.launch {
                    val playable = streamResolver.resolvePlayableByIds(hero.entryId, hero.subId)
                    if (playable != null) {
                        MainActivity.startPlayerActivity(
                            context = context,
                            animeId = playable.first.id,
                            episodeId = playable.second.id,
                            extPlayer = extPlayer,
                        )
                    } else {
                        navigator.push(ShowsScreen(hero.entryId))
                    }
                }
            }
            HomeHubMediaType.MANGA -> {
                context.startActivity(
                    ReaderActivity.newIntent(context, hero.entryId, hero.subId),
                )
            }
            HomeHubMediaType.NOVEL -> {
                // Continue in the medium the user last used — audio if they
                // listened to this novel before, the reader otherwise.
                scope.launch {
                    val novel = getNovel.await(hero.entryId)
                    if (novel != null && audiobookPlayer.hasListenedBefore(hero.entryId) &&
                        audiobookPlayer.resumeListening(novel)
                    ) {
                        return@launch
                    }
                    navigator.push(NovelReaderScreen(hero.entryId, hero.subId))
                }
            }
        }
    }
    val openDetail: (HomeHubHero) -> Unit = { hero ->
        // Heroes carry the canonical id the detail resolves to (showKey)
        // — pushing it instead of the history row's animeId skips the
        // extension→canonical navigator.replace, which would run a SECOND
        // screen transition and pop every shared element mid-flight.
        val detailId = hero.sharedEntryId
        if (navAnimScope != null) pendingHeroId = detailId
        // previewCover/previewTitle/previewLogo let the pushed screen host
        // the shared hero elements during its Loading state — the artwork
        // morph and title handoff land on first open, not just revisits.
        val previewCover = hero.backdrop ?: hero.coverData
        val previewLogo = hero.canonicalId?.let {
            preferenceStore.getString("cinemeta_logo_$it", "").get().takeIf(String::isNotBlank)
        }
        when (hero.mediaType) {
            HomeHubMediaType.SHOWS -> navigator.push(
                ShowsScreen(
                    detailId,
                    previewCover = previewCover,
                    previewTitle = hero.title,
                    previewLogo = previewLogo,
                ),
            )
            HomeHubMediaType.MANGA -> navigator.push(
                MangaScreen(
                    detailId,
                    previewCover = previewCover,
                    previewTitle = hero.title,
                    previewLogo = previewLogo,
                ),
            )
            HomeHubMediaType.NOVEL -> navigator.push(
                NovelScreen(
                    detailId,
                    previewCover = previewCover,
                    previewTitle = hero.title,
                    previewLogo = previewLogo,
                ),
            )
        }
    }

    // Pre-extract each hero's cover accent while it's on screen — the
    // detail view's rememberEntryAccentColor then seeds synchronously
    // and the screen never flashes theme-default → cover accent. The
    // cover key (url#lastModified) matches the domain object's key, so
    // this lands in the exact slot the detail screen reads.
    items.forEach { hero ->
        PreExtractEntryCoverColor(
            entryId = hero.entryId,
            cover = hero.coverData,
            type = when (hero.mediaType) {
                HomeHubMediaType.SHOWS -> EntryCoverMetadata.EntryType.SHOWS
                HomeHubMediaType.MANGA -> EntryCoverMetadata.EntryType.MANGA
                HomeHubMediaType.NOVEL -> EntryCoverMetadata.EntryType.NOVEL
            },
        )
    }

    val heroHeight = LocalConfiguration.current.screenHeightDp.dp * HERO_HEIGHT_FRACTION
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Overscroll rubber-band (Nuvio port): the hero box itself grows
    // under the pull — its top edge stays pinned to the screen top, so
    // no background strip can open above it — and the growth pushes the
    // catalog rows below down, the whole-content overshoot. The image
    // inside applies Nuvio's zoom + upward parallax (see below).
    val overshootDp = with(LocalDensity.current) { (overscrollPx() * 0.5f).toDp() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight + overshootDp)
            .homeHeroPagerGesture(
                pagerState = pagerState,
                itemCount = items.size,
                coroutineScope = scope,
            )
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
    ) {
        val heroWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
        val heroHeightPx = with(LocalDensity.current) { heroHeight.toPx() }

        // Shows-mode heroes drift horizontally — the detail backdrop's
        // exact pan: an infinite eased -1..1 driving accumulated
        // translationX on a 1.25-oversized image (edges never show),
        // decelerating to a stop while the pull is held. Manga/novel
        // heroes stay still; gated by the same hero-pan preference.
        val panPrefEnabled by remember {
            Injekt.get<UiPreferences>().heroImagePanEnabled()
        }.collectAsState()
        val panEnabled = panPrefEnabled &&
            items.any { it.mediaType == HomeHubMediaType.SHOWS }
        val panFraction by rememberInfiniteTransition(label = "hero_pan").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "hero_pan_offset",
        )
        val panSpeed by animateFloatAsState(
            targetValue = if (panEnabled && overscrollPx() <= 0f) 1f else 0f,
            animationSpec = tween(900, easing = LinearOutSlowInEasing),
            label = "hero_pan_speed",
        )
        val panX = remember { mutableFloatStateOf(0f) }
        var lastPanFraction by remember { mutableStateOf<Float?>(null) }
        LaunchedEffect(panEnabled, panFraction, panSpeed, heroWidthPx) {
            if (!panEnabled || heroWidthPx == 0f) return@LaunchedEffect
            val last = lastPanFraction
            if (last == null) {
                panX.floatValue = panFraction * heroWidthPx * 0.10f
            } else {
                panX.floatValue += (panFraction - last) * heroWidthPx * 0.10f * panSpeed
            }
            lastPanFraction = panFraction
        }

        val scrollOffsetPx by remember(listState, heroHeightPx) {
            derivedStateOf {
                when {
                    listState.firstVisibleItemIndex > 0 -> heroHeightPx
                    else -> listState.firstVisibleItemScrollOffset.toFloat()
                }
            }
        }
        // If a hero-detail transition is pending, keep the tapped entry's
        // page in the visible set — after a mid-visit reorder the restored
        // pager can point at a different itemIndex, which would leave the
        // returning shared elements without partners.
        val pendingIndex = renderItems
            .indexOfFirst { it.sharedEntryId == pendingHeroId }
            .takeIf { it >= 0 }
        val currentPage = pendingIndex?.let {
            heroPageForItem(pagerState.currentPage, it, renderItems.size)
        } ?: pagerState.currentPage
        val liveVisiblePages = if (pendingIndex != null) {
            // A hero push is pending/in-flight — render only the tapped
            // hero, centered and fully visible. A residual user-swipe
            // offsetFraction would otherwise ride into the shared
            // elements' start bounds (image/title begin displaced and
            // visibly "slide" into place), and a still-running fling would
            // keep animating the real pager under the frozen snapshot.
            // Auto-scroll taps always arrive settled (offset 0), which is
            // why only manual swipes reproduced the slide.
            listOf(
                HeroPageLayer(
                    itemIndex = pendingIndex,
                    visibility = 1f,
                    offset = 0f,
                ),
            )
        } else {
            listOf(
                currentPage,
                (currentPage - 1).coerceAtLeast(0),
                (currentPage + 1).coerceAtMost(pagerState.pageCount - 1),
            ).distinct()
                .mapNotNull { index ->
                    val pageOffset = heroPageOffset(pagerState, index)
                    val visibility = (1f - abs(pageOffset)).coerceIn(0f, 1f)
                    if (visibility <= 0f) {
                        null
                    } else {
                        HeroPageLayer(
                            itemIndex = index % renderItems.size,
                            visibility = visibility,
                            offset = pageOffset,
                        )
                    }
                }
                .sortedBy(HeroPageLayer::visibility)
        }
        // While the nav transition runs, freeze the page layers and the
        // item list; when it ends, settle the pager onto the pending
        // entry's (possibly reordered) page and release the snapshot.
        LaunchedEffect(transitioning, items) {
            if (transitioning) {
                if (frozenPages == null) frozenPages = liveVisiblePages
            } else {
                frozenPages = null
                frozenItems = items
                if (pendingHeroId != -1L) {
                    items.indexOfFirst { it.sharedEntryId == pendingHeroId }
                        .takeIf { it >= 0 }
                        ?.let { idx ->
                            val target = heroPageForItem(pagerState.currentPage, idx, items.size)
                            if (target != pagerState.currentPage) {
                                pagerState.scrollToPage(target)
                            }
                        }
                    pendingHeroId = -1L
                }
            }
        }
        val visiblePages = if (transitioning) frozenPages ?: liveVisiblePages else liveVisiblePages
        val currentItem = visiblePages
            .lastOrNull()
            ?.itemIndex
            ?.let(renderItems::get)
            ?: renderItems[currentPage % renderItems.size]

        // Invisible driver — pages are painted manually below for the
        // crossfade + parallax look; gesture goes through
        // homeHeroPagerGesture.
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.01f },
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }

        // Background layers — crossfaded imagery with parallax drift.
        // Prefers the high-res backdrop (Cinemeta fanart) over the
        // low-res entry thumbnail. key() per item — visiblePages re-sorts
        // by visibility mid-swipe; keyed children keep their identity so
        // outgoing layers don't rebuild (and flash) when order flips.
        visiblePages.forEach { layer ->
            val hero = renderItems[layer.itemIndex]
            val layerPans = panEnabled && hero.mediaType == HomeHubMediaType.SHOWS
            key(layer.itemIndex) {
                val imageModel = hero.backdrop ?: hero.coverData
                Box(modifier = Modifier.fillMaxSize()) {
                    // The shared element is the image PLUS its scrims —
                    // shared content renders in an overlay above everything
                    // during the morph, so a bare image would fly over the
                    // title bright and undimmed. Scrims inside keep the
                    // transit render identical to the laid-out one (and
                    // scrimming each layer before the visibility blend
                    // composites identically to one scrim on top).
                    //
                    // The element's height matches the DETAIL hero's
                    // footprint (statusBar + heroContentPadding 320/440 +
                    // title/meta tail), not this hero box's full height —
                    // ContentScale.Crop is height-bound on these portrait
                    // boxes, so a taller element would frame a tighter slice
                    // than the detail's ("home looks more zoomed in"). With
                    // matching heights the morph is a near-pure position
                    // glide; below the element the gradient's bg end blends
                    // into the page. clipToBounds keeps the scroll-parallax
                    // translationY inside the element — without it the image
                    // slides past the element bottom on scroll and paints
                    // un-scrimmed art over the hero's lower band.
                    //
                    // The element node sits OUTSIDE the layer's parallax
                    // graphicsLayer — a residual mid-settle swipe offset
                    // would otherwise translate the element's bounds and
                    // the morph would start displaced (image "slides" in
                    // the swipe direction). The parallax carrier lives
                    // inside the clipped element instead: the artwork still
                    // pans during swipes, the bounds never move.
                    val heroImageHeight = statusBarTop + overshootDp +
                        when (hero.mediaType) {
                            HomeHubMediaType.SHOWS -> 320.dp
                            else -> 440.dp
                        } + HERO_DETAIL_CONTENT_TAIL.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroImageHeight)
                            .align(Alignment.TopCenter)
                            .clipToBounds()
                            .then(heroSharedBoundsModifier("hero-image-${hero.sharedEntryId}")),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = layer.visibility
                                    translationX = -layer.offset * heroWidthPx * HERO_BACKGROUND_PARALLAX
                                },
                        ) {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = hero.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                    // Freeze pan/parallax/zoom while a nav
                                    // transition runs — these transforms
                                    // ride *inside* the shared element's
                                    // content, so mid-flight they make the
                                    // artwork appear to slide around
                                    // instead of gliding as one element.
                                        val transit = navAnimScope?.transition?.isRunning == true
                                        val offset = scrollOffsetPx
                                        val stretchPx = overscrollPx()
                                        // Detail-view recipe: the pull adds a
                                        // mild zoom (pull*0.0005, capped +15%
                                        // over base) while the grown hero box
                                        // pushes everything below down — the
                                        // image never drifts on pull; only real
                                        // scroll gets the 0.3 parallax. Panning
                                        // layers oversize 25% so the ±10%
                                        // translation never exposes an edge.
                                        val baseScale = if (layerPans) 1.25f else 1f
                                        val scale = if (transit) {
                                            baseScale
                                        } else {
                                            (baseScale + stretchPx * 0.0005f + offset * 0.0001f)
                                                .coerceAtMost(baseScale + 0.15f)
                                        }
                                        scaleX = scale
                                        scaleY = scale
                                        translationY = if (transit) 0f else offset * HERO_SCROLL_PARALLAX
                                        if (layerPans && !transit) {
                                            translationX = panX.floatValue
                                        }
                                    },
                                alignment = Alignment.Center,
                                contentScale = ContentScale.Crop,
                            )

                            // Same scrim stack as the detail hero and the
                            // loading preview — identical content on both ends
                            // of the shared element is what makes the morph a
                            // move (like the title) instead of a crossfade
                            // between differently-scrimmed stacks.
                            val scrimBackground = MaterialTheme.colorScheme.background
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                scrimBackground,
                                            ),
                                        ),
                                    ),
                            )

                            // Top scrim — same 140dp black fade the detail hero
                            // paints so the status bar / top bar stay legible.
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
                    }
                }
            }
        }

        // Tap the artwork → the entry's detail screen. The pager gesture
        // only claims horizontal drags, so taps fall through to this
        // layer; the content column above it keeps its own handlers
        // (title → detail, resume pill → resume, dots → page jump).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickableNoIndication { openDetail(currentItem) },
        )

        // Content — centered title + meta + resume pill + dots, parked just
        // above the hero's bottom edge so it sits tight against the first
        // catalog row ("recently watched") below. Title, genres and the
        // resume pill are separate shared elements pairing with their
        // detail-view counterparts (all above the artwork's overlay z):
        // the title only glides into place — it exists on both sides — the
        // genre line crossfades into the detail meta row, and the pill
        // morphs width-wise into the detail's continue bar.
        val contentBottomPadding = 24.dp
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = contentBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title/logo layers — bottom-anchored so a two-line title grows
            // UPWARD; the genre line below never shifts.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                visiblePages.forEach { layer ->
                    key(layer.itemIndex) {
                        Box(
                            modifier = Modifier
                                .then(
                                    heroSharedBoundsModifier(
                                        "hero-title-${renderItems[layer.itemIndex].sharedEntryId}",
                                        HeroSharedKind.Text,
                                    ),
                                )
                                .graphicsLayer {
                                    alpha = layer.visibility
                                    translationX = -layer.offset * heroWidthPx * HERO_CONTENT_PARALLAX
                                },
                        ) {
                            HeroTitleBlock(
                                hero = renderItems[layer.itemIndex],
                                onTitleClick = { openDetail(renderItems[layer.itemIndex]) },
                            )
                        }
                    }
                }
            }

            // Genre line — pinned at a fixed distance above the resume button,
            // crossfaded per layer like the title.
            if (renderItems.any { it.genres.isNotEmpty() }) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    visiblePages.forEach { layer ->
                        key(layer.itemIndex) {
                            val genres = renderItems[layer.itemIndex].genres
                            Text(
                                text = genres.joinToString(" · "),
                                modifier = Modifier
                                    .then(
                                        heroSharedBoundsModifier(
                                            "hero-meta-${renderItems[layer.itemIndex].sharedEntryId}",
                                            HeroSharedKind.Text,
                                        ),
                                    )
                                    .graphicsLayer {
                                        alpha = layer.visibility
                                        translationX = -layer.offset * heroWidthPx * HERO_CONTENT_PARALLAX
                                    },
                                style = MaterialTheme.typography.labelLarge,
                                // Theme-dependent — over light-theme artwork
                                // hard white is unreadable; onBackground
                                // matches the detail meta row this line
                                // morphs into, and stays near-white in
                                // dark mode.
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Resume button — same chrome as the detail view's continue bar
            // (accent fill, 16dp corners, contrast-aware content), tinted by
            // the app-mode theme accent rather than the hero artwork. The
            // shared element morphs it width-wise into the detail's
            // EntryContinueRow; labels crossfade and the fill animates from
            // theme primary to the entry accent via the accent transition.
            val resumeLabel = heroResumeLabel(currentItem)
            val resumeEnabled = !currentItem.allRead
            Surface(
                modifier = Modifier
                    .then(
                        heroSharedBoundsModifier(
                            "hero-resume-${currentItem.sharedEntryId}",
                            HeroSharedKind.Container,
                        ),
                    )
                    .clickable(enabled = resumeEnabled) { resumeHero(currentItem) },
                color = if (resumeEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    eu.kanade.presentation.entries.components.accentTintedSurface(
                        accent = MaterialTheme.colorScheme.primary,
                        background = MaterialTheme.colorScheme.background,
                        dimmed = false,
                    )
                },
                contentColor = if (resumeEnabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (resumeEnabled) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = resumeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (renderItems.size > 1) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    // Not a shared element — fade with the nav transition
                    // instead of blinking under the flying image element.
                    modifier = Modifier.graphicsLayer { alpha = navChromeAlpha },
                ) {
                    renderItems.forEachIndexed { index, _ ->
                        val activeFraction = visiblePages
                            .firstOrNull { it.itemIndex == index }
                            ?.visibility ?: 0f
                        Box(
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            heroPageForItem(
                                                pagerState.currentPage,
                                                index,
                                                renderItems.size,
                                            ),
                                        )
                                    }
                                }
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onBackground)
                                .graphicsLayer {
                                    alpha = 0.35f + (0.57f * activeFraction)
                                }
                                .width(8.dp + (24.dp * activeFraction))
                                .height(8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Centered logo (title as fallback) for a hero page — no meta here; the
 *  genre line lives in a separate pinned slot so title height changes
 *  never shift it. */
@Composable
private fun HeroTitleBlock(hero: HomeHubHero, onTitleClick: () -> Unit) {
    // Cinemeta clearlogo (title treatment PNG) — same per-entry pref the
    // detail screen reads; blank/missing → the text title stays.
    val preferenceStore = remember { Injekt.get<PreferenceStore>() }
    val logoUrl by remember(hero.canonicalId) {
        preferenceStore.getString("cinemeta_logo_${hero.canonicalId}", "")
    }.collectAsState()

    // Wrap-content — the shared element's bounds must match the detail
    // hero's wrap-content title, or Fit rescaling shrinks the text to
    // the smaller bound mid-flight (visible as a scale-down/scale-up
    // "rebuild"). The parent Box centers it.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hero.canonicalId != null && logoUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(logoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = hero.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(max = 110.dp)
                    .widthIn(max = 320.dp)
                    .clickable(onClick = onTitleClick),
                error = {
                    HeroTitleText(hero, onTitleClick)
                },
            )
        } else {
            HeroTitleText(hero, onTitleClick)
        }
    }
}

@Composable
private fun HeroTitleText(hero: HomeHubHero, onTitleClick: () -> Unit) {
    // Same composable the detail hero + loading preview use — identical
    // content on both ends of the shared element makes the morph a pure
    // position glide, not a style crossfade.
    EnhancedTitleText(
        title = hero.title,
        textAlign = TextAlign.Center,
        modifier = Modifier.clickable(onClick = onTitleClick),
    )
}

/** Pill text: "Resume <S2 · Ep. 5 / Chapter 312 / chapter name>" — or the
 * all-done / not-started states. */
@Composable
private fun heroResumeLabel(hero: HomeHubHero): String {
    if (hero.allRead) return stringResource(AYMR.strings.fetching_overlay_all_up_to_date)
    val progress = when {
        hero.isCinemetaMovie -> eu.kanade.presentation.util.formatRuntime(hero.runtime) ?: ""
        hero.mediaType == HomeHubMediaType.SHOWS -> {
            hero.season?.takeIf { it > 0 }
                ?.let { "S$it · ${eu.kanade.presentation.util.formatEpisodeNumber(hero.progressNumber)}" }
                ?: eu.kanade.presentation.util.formatEpisodeNumber(hero.progressNumber)
        }
        hero.mediaType == HomeHubMediaType.NOVEL && !hero.chapterTitle.isNullOrBlank() -> {
            hero.chapterTitle
        }
        else -> stringResource(
            AYMR.strings.home_hero_chapter_progress,
            eu.kanade.presentation.util.formatChapterNumber(hero.progressNumber),
        )
    }
    return if (progress.isNullOrBlank()) {
        stringResource(MR.strings.action_start)
    } else {
        stringResource(AYMR.strings.home_hero_resume, progress)
    }
}

// --- Pager plumbing (ported from Nuvio's HomeHeroSection.kt) ---

private fun heroPageOffset(pagerState: PagerState, page: Int): Float =
    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

internal fun heroPageForItem(currentPage: Int, itemIndex: Int, itemCount: Int): Int {
    val page = currentPage.toLong() - currentPage % itemCount + itemIndex
    return listOf(page - itemCount, page, page + itemCount)
        .filter { it in 0L until Int.MAX_VALUE.toLong() }
        .minBy { abs(it - currentPage) }
        .toInt()
}

private fun Modifier.homeHeroPagerGesture(
    pagerState: PagerState,
    itemCount: Int,
    coroutineScope: CoroutineScope,
): Modifier {
    if (itemCount <= 1) return this

    return pointerInput(pagerState, itemCount) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val widthPx = size.width.toFloat().takeIf { it > 0f } ?: return@awaitEachGesture
            val velocityTracker = VelocityTracker().apply {
                addPosition(down.uptimeMillis, down.position)
            }
            val startPage = pagerState.currentPage
            var totalDx = 0f
            var totalDy = 0f
            var dragging = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)

                if (!change.pressed) {
                    if (dragging) {
                        val targetPage = resolveHeroTargetPage(
                            startPage = startPage,
                            pageCount = pagerState.pageCount,
                            totalDx = totalDx,
                            velocityX = velocityTracker.calculateVelocity().x,
                            widthPx = widthPx,
                        )
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                    break
                }

                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y

                if (!dragging) {
                    val horizontalDrag =
                        abs(totalDx) > viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy)
                    val verticalDrag =
                        abs(totalDy) > viewConfiguration.touchSlop && abs(totalDy) > abs(totalDx)

                    when {
                        verticalDrag -> break
                        horizontalDrag -> dragging = true
                        else -> continue
                    }
                }

                pagerState.dispatchRawDelta(-delta.x)
                change.consume()
            }
        }
    }
}

private fun resolveHeroTargetPage(
    startPage: Int,
    pageCount: Int,
    totalDx: Float,
    velocityX: Float,
    widthPx: Float,
): Int {
    val thresholdPassed = abs(totalDx) > widthPx * HERO_SWIPE_THRESHOLD_FRACTION ||
        abs(velocityX) > HERO_SWIPE_VELOCITY_THRESHOLD
    if (!thresholdPassed) return startPage

    val currentPage = startPage.coerceIn(0, pageCount - 1)
    return when {
        totalDx > 0f -> (currentPage - 1).coerceAtLeast(0)
        totalDx < 0f -> (currentPage + 1).coerceAtMost(pageCount - 1)
        else -> currentPage
    }
}
