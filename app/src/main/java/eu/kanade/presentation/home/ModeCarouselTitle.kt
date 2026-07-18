package eu.kanade.presentation.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.domain.ui.model.ContentMode.Companion.carouselOrderFor
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.absoluteValue

/**
 * The HomeScreen top-bar title that doubles as the content-mode switcher.
 *
 * The mode word (Anime / Manga / Novels) is a swipeable carousel shown on top, with the
 * section word (Library / Updates / History / Browse) underneath in a secondary style.
 * Both are left-aligned. The carousel has gradient-faded edges so neighboring mode words
 * smoothly appear/disappear as the user swipes.
 *
 * Mode changes are committed mid-swipe (when the scroll crosses 50% to the next page),
 * so the content updates while the title animation is still in progress. The title
 * carousel is decoupled from the mode visually — it doesn't snap when the mode changes.
 *
 * Wrap-around is achieved with a virtually-infinite [HorizontalPager] (page count
 * [Int.MAX_VALUE]) mapped `page % 3` to [ContentMode.carouselOrder].
 */
@Composable
fun ModeCarouselTitle(
    currentMode: ContentMode,
    onModeChange: (ContentMode) -> Unit,
    sectionTitle: String,
    modifier: Modifier = Modifier,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val showManga by uiPreferences.showMangaMode().collectAsState()
    val showAnime by uiPreferences.showAnimeMode().collectAsState()
    val showNovel by uiPreferences.showNovelMode().collectAsState()

    val carousel = remember(showManga, showAnime, showNovel) {
        val visible = mutableSetOf<ContentMode>()
        if (showManga) visible.add(ContentMode.MANGA)
        if (showAnime) visible.add(ContentMode.ANIME)
        if (showNovel) visible.add(ContentMode.NOVEL)
        carouselOrderFor(visible)
    }
    val pageCount = Int.MAX_VALUE
    // Start near the middle of the virtual range, anchored to the current mode's slot.
    val initialPage = remember(currentMode) {
        val baseCenter = pageCount / 2
        val targetSlot = carousel.indexOf(currentMode)
        baseCenter - (baseCenter % carousel.size) + targetSlot
    }

    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

    // Track whether the user is actively swiping to avoid external sync fighting
    // with an in-progress gesture.
    var isUserSwiping by remember { mutableStateOf(false) }

    // When the mode changes externally (e.g. deep link, settings, or another tab's
    // carousel), animate the pager to the new mode's page. Skip if the user is
    // actively swiping or if the pager is already at the right page.
    LaunchedEffect(currentMode) {
        if (isUserSwiping) return@LaunchedEffect
        val currentSlot = carousel.indexOf(currentMode)
        if (currentSlot < 0) return@LaunchedEffect
        val targetPage = nearestPageForSlot(pagerState.currentPage, currentSlot, carousel.size)
        if (targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // Detect when the user starts swiping (scroll offset becomes non-zero).
    LaunchedEffect(pagerState.currentPageOffsetFraction) {
        isUserSwiping = pagerState.currentPageOffsetFraction != 0f
    }

    // Commit mode changes only after the scroll fully settles (offset fraction == 0).
    // This lets the title text finish its swipe animation before the mode updates.
    LaunchedEffect(pagerState.currentPage, pagerState.currentPageOffsetFraction) {
        if (pagerState.currentPageOffsetFraction != 0f) return@LaunchedEffect
        val slot = pagerState.currentPage % carousel.size
        val resolved = if (slot < 0) slot + carousel.size else slot
        val newMode = carousel[resolved]
        if (newMode != currentMode) {
            onModeChange(newMode)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        // Mode word on top — swipeable carousel with gradient-faded edges.
        ModeCarousel(
            pagerState = pagerState,
            carousel = carousel,
        )
        // Section word underneath in a secondary style.
        Text(
            text = sectionTitle,
            style = MaterialTheme.typography.titleSmall,
            color = LocalContentColor.current.let { it.copy(alpha = 0.6f) },
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModeCarousel(
    pagerState: PagerState,
    carousel: List<ContentMode>,
    modifier: Modifier = Modifier,
) {
    val pageWidthDp = 104.dp

    Box(
        modifier = modifier
            .width(pageWidthDp)
            // Use Offscreen compositing so the gradient mask erases pixels cleanly.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Alpha mask: DstIn keeps content where the mask is opaque.
                // The mask is narrower than the pager and offset left so:
                // - Left fade is mostly off-screen (text starts at left edge, visible)
                // - Right fade is closer to the text, allowing mode words to appear
                //   closer together during mid-swipe.
                val maskWidth = size.width * 0.82f
                val maskOffset = -size.width * 0.08f // shift left
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.12f to Color.Black,
                            0.88f to Color.Black,
                            1f to Color.Transparent,
                        ),
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(maskOffset, 0f),
                    size = androidx.compose.ui.geometry.Size(maskWidth, size.height),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 2.dp,
            pageSize = PageSize.Fixed(pageWidthDp),
            pageContent = { page ->
                val slot = ((page % carousel.size) + carousel.size) % carousel.size
                val mode = carousel[slot]

                // Continuous scroll position: currentPage + fractional offset.
                val scrollPos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                val distance = (page - scrollPos).absoluteValue
                // Presence: 1f when centered, fading to 0f as the page moves away.
                val presence = (1f - distance).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .width(pageWidthDp)
                        .wrapContentSize(Alignment.CenterStart),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(mode.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .alpha(presence)
                            .scale(lerp(0.86f, 1f, presence)),
                    )
                }
            },
        )
    }
}

/**
 * Pick the virtual page nearest to [fromPage] that maps to the requested [slot].
 */
private fun nearestPageForSlot(fromPage: Int, slot: Int, size: Int): Int {
    val currentSlot = ((fromPage % size) + size) % size
    val delta = ((slot - currentSlot) + size) % size
    // Prefer the shorter direction.
    val shortest = if (delta <= size / 2) delta else delta - size
    return fromPage + shortest
}

/**
 * Self-contained [ModeCarouselTitle] that reads [ContentMode] from [UiPreferences] and
 * writes changes back. Use this in per-tab toolbars so each tab gets the swipeable mode
 * switcher without needing to pass state manually.
 *
 * @param sectionTitle the tab's section word (e.g. "Library", "Updates", "Browse")
 */
@Composable
fun ModeCarouselTitleConnected(
    sectionTitle: String,
    modifier: Modifier = Modifier,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val contentMode by uiPreferences.contentMode().collectAsState()
    val scope = rememberCoroutineScope()

    ModeCarouselTitle(
        currentMode = contentMode,
        onModeChange = { newMode ->
            scope.launch { uiPreferences.contentMode().set(newMode) }
        },
        sectionTitle = sectionTitle,
        modifier = modifier,
    )
}
