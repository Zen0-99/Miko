package eu.kanade.presentation.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * A floating pill showing the current content mode (Anime / Manga / Novels).
 *
 * Swipe left or right to switch to the neighboring mode — circular/infinite.
 * One swipe gesture = at most one mode change, regardless of finger travel distance.
 * The mode commits when the finger is released and the text slides to the new mode.
 * When not swiping, only the current mode's text is visible.
 */
@Composable
fun ModePill(
    modifier: Modifier = Modifier,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val contentMode by uiPreferences.contentMode().collectAsState()
    val showManga by uiPreferences.showMangaMode().collectAsState()
    val showAnime by uiPreferences.showAnimeMode().collectAsState()
    val showNovel by uiPreferences.showNovelMode().collectAsState()
    val scope = rememberCoroutineScope()

    val carousel = remember(showManga, showAnime, showNovel) {
        val visible = mutableSetOf<ContentMode>()
        if (showManga) visible.add(ContentMode.MANGA)
        if (showAnime) visible.add(ContentMode.ANIME)
        if (showNovel) visible.add(ContentMode.NOVEL)
        carouselOrderFor(visible)
    }
    val pageCount = carousel.size

    var currentPage by remember { mutableIntStateOf(carousel.indexOf(contentMode).coerceIn(0, pageCount - 1)) }

    // Animated visual offset: 0 = centered, negative = dragged left, positive = dragged right
    // Range: -1.0 (fully swiped left) to 1.0 (fully swiped right)
    val dragOffset = remember { Animatable(0f) }

    // Sync external contentMode changes → currentPage
    LaunchedEffect(contentMode) {
        val target = carousel.indexOf(contentMode)
        if (target >= 0 && target != currentPage) {
            currentPage = target
            dragOffset.snapTo(0f)
        }
    }

    fun commitPage(rawPage: Int) {
        val newPage = ((rawPage % pageCount) + pageCount) % pageCount
        if (newPage == currentPage) return

        // Determine direction: +1 = next (swipe left), -1 = previous (swipe right)
        val direction = if (rawPage > currentPage) 1 else -1

        scope.launch {
            // Animate offset to full page slide (±1.0) — the new page slides into center
            dragOffset.animateTo(-direction.toFloat(), tween(250))
            // Now swap: update currentPage to the new page, snap offset back to 0
            currentPage = newPage
            dragOffset.snapTo(0f)
            // Commit the mode change
            val newMode = carousel[newPage]
            if (newMode != contentMode) {
                uiPreferences.contentMode().set(newMode)
            }
        }
    }

    fun onSwipeLeft() = commitPage(currentPage + 1)
    fun onSwipeRight() = commitPage(currentPage - 1)
    fun switchMode(direction: Int) = commitPage(currentPage + direction)

    // Hide the pill entirely if only one mode is visible — no switching needed
    if (pageCount <= 1) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(
                onClick = { switchMode(-1) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Previous mode",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            ModePillText(
                carousel = carousel,
                currentPage = currentPage,
                dragOffset = dragOffset,
                onSwipeLeft = { onSwipeLeft() },
                onSwipeRight = { onSwipeRight() },
                modifier = Modifier.weight(1f, false),
            )

            Spacer(modifier = Modifier.width(2.dp))

            IconButton(
                onClick = { switchMode(1) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Next mode",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ModePillText(
    carousel: List<ContentMode>,
    currentPage: Int,
    dragOffset: Animatable<Float, *>,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val pageCount = carousel.size
    val viewportWidthDp = 80.dp
    val offset = dragOffset.value

    Box(
        modifier = modifier
            .width(viewportWidthDp)
            .height(44.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Gradient fade mask at edges — only visible when swiping
                val fadeAmount = offset.absoluteValue.coerceIn(0f, 1f)
                if (fadeAmount > 0.01f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.15f to Color.Black,
                                0.85f to Color.Black,
                                1f to Color.Transparent,
                            ),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            .pointerInput(currentPage) {
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulated = 0f
                        scope.launch { dragOffset.snapTo(0f) }
                    },
                    onDragEnd = {
                        when {
                            accumulated < -0.3f -> onSwipeLeft()
                            accumulated > 0.3f -> onSwipeRight()
                            else -> scope.launch { dragOffset.animateTo(0f, tween(150)) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffset.animateTo(0f, tween(150)) }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        accumulated += dragAmount / this.size.width
                        // Clamp to [-1, 1] — only one page max
                        val clamped = accumulated.coerceIn(-1f, 1f)
                        scope.launch { dragOffset.snapTo(clamped) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Render current and circular neighbors
        // delta=-1: left neighbor (previous), delta=0: current, delta=1: right neighbor (next)
        for (delta in -1..1) {
            val page = ((currentPage + delta) % pageCount + pageCount) % pageCount
            val mode = carousel[page]

            // position = delta + offset
            // offset < 0 (swiped left): current moves left, right neighbor (delta=1) slides in from right
            // offset > 0 (swiped right): current moves right, left neighbor (delta=-1) slides in from left
            val position = delta.toFloat() + offset

            // Alpha: only show text that's near center. When not swiping (offset=0),
            // only delta=0 (current) is visible. Neighbors fade in as they approach center.
            val alpha = (1f - position.absoluteValue).coerceIn(0f, 1f)
            // Boost alpha slightly so the incoming text is more visible during swipe
            val boostedAlpha = if (alpha > 0f) (alpha * 1.5f).coerceAtMost(1f) else 0f

            Text(
                text = stringResource(mode.titleRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .graphicsLayer {
                        // 1.6x multiplier creates a wider gap between mode texts during swipe
                        translationX = position * this.size.width * 1.6f
                    }
                    .alpha(boostedAlpha),
            )
        }
    }
}
