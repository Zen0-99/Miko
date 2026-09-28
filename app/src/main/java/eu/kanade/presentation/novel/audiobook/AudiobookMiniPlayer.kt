package eu.kanade.presentation.novel.audiobook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import eu.kanade.presentation.components.LocalHostScaffoldContentPadding
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlayerController
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlayerState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs
import kotlin.math.roundToInt

// Anchors: 0 = settled, ±1 = dismissed off that screen edge.
private const val ANCHOR_SETTLED = 0
private const val ANCHOR_DISMISSED = 1

/**
 * Floating glass mini-player for the audiobook session (REQ-008, D-07).
 *
 * Attached inside HomeScreen's tab-host Box beside
 * [eu.kanade.presentation.library.LibraryUpdateProgressOverlay] — so it only
 * ever appears on the main navigation screens, never on pushed detail or
 * settings screens. Sits above the nav bar via
 * [LocalHostScaffoldContentPadding]; publishes its visibility through
 * [AudiobookPlayerController.setMiniPlayerVisible] so the update overlay can
 * stack on top of it when both are showing (PB-15).
 *
 * - Tap the card body → expands the full overlay (PB-18).
 * - Swipe left/right away → pauses playback and hides (D-09/PB-16); a new
 *   play session re-shows it.
 * - Paused-but-loaded keeps the card visible — it's the resume surface.
 */
@Composable
fun AudiobookMiniPlayer(
    hazeState: HazeState?,
    tint: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val controller = remember { Injekt.get<AudiobookPlayerController>() }
    val state by controller.state.collectAsState()

    // Dismissal lives on the controller singleton: `remember`ed state dies
    // when navigation removes this composable, so a local flag let the card
    // resurrect after swiping it away and pushing a detail screen. Cleared
    // by the controller on the next playback start (PB-16).
    val userDismissed by controller.miniPlayerDismissed.collectAsState()

    // Fade out while a bottom sheet is open so the card doesn't sit on top
    // of sheet content (shared signal — the update overlay hides on it too).
    val sheetVisible by LibraryUpdateProgressBus.sheetVisible.collectAsState()

    val visible = state.track != null && !userDismissed && !state.overlayVisible && !sheetVisible

    // Publish visibility so the update-progress overlay can lift itself above
    // the card while it's on screen (PB-15 — update card sits on top).
    LaunchedEffect(visible) {
        controller.setMiniPlayerVisible(visible)
    }
    DisposableEffect(Unit) {
        onDispose { controller.setMiniPlayerVisible(false) }
    }

    val hostPadding = LocalHostScaffoldContentPadding.current
    val navBarBottom = hostPadding?.calculateBottomPadding() ?: 0.dp
    val bottomPadding = navBarBottom + 12.dp

    // ---- Swipe-away (D-09) — same anchoredDraggable recipe as the update
    // overlay, horizontal this time.
    val density = LocalDensity.current
    val dismissDistance = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx() * 1.5f
    }
    val decayAnimationSpec = rememberSplineBasedDecay<Float>()
    val draggableState = remember {
        AnchoredDraggableState(
            initialValue = ANCHOR_SETTLED,
            snapAnimationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            decayAnimationSpec = decayAnimationSpec,
            positionalThreshold = { totalDistance -> totalDistance * 0.4f },
            velocityThreshold = { with(density) { 800.dp.toPx() } },
        )
    }
    LaunchedEffect(dismissDistance) {
        draggableState.updateAnchors(
            DraggableAnchors {
                ANCHOR_SETTLED at 0f
                ANCHOR_DISMISSED at dismissDistance
                -ANCHOR_DISMISSED at -dismissDistance
            },
        )
    }
    LaunchedEffect(visible) {
        // snapTo (not animateTo): a previous swipe-away leaves the offset at
        // ±dismissDistance — animating back makes the card visibly swoop in
        // from the screen edge during the enter transition (the "flicker").
        if (visible) draggableState.snapTo(ANCHOR_SETTLED)
    }
    // Dismiss on settledValue, not currentValue: currentValue flips to a
    // dismissed anchor the moment the drag crosses the midpoint — while the
    // card is still partially on screen and the finger is still down — so
    // AnimatedVisibility's exit blinks the card out mid-gesture (the flicker).
    // settledValue only changes once the fling/spring has carried the card
    // fully off-screen, making the exit invisible.
    LaunchedEffect(draggableState.settledValue) {
        if (draggableState.settledValue == ANCHOR_DISMISSED ||
            draggableState.settledValue == -ANCHOR_DISMISSED
        ) {
            // D-09: swiping the card away pauses playback.
            controller.pause()
            controller.dismissMiniPlayer()
        }
    }
    val dragProgress by remember {
        derivedStateOf {
            val offset = try {
                draggableState.requireOffset()
            } catch (_: IllegalStateException) {
                0f
            }
            (abs(offset) / dismissDistance).coerceIn(0f, 1f)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 },
        exit = fadeOut(tween(200)),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding)
            .padding(horizontal = 16.dp),
    ) {
        MiniPlayerCard(
            state = state,
            onExpand = {
                // Guard: a horizontal swipe that ends inside the card must not
                // be read as a tap-to-expand — only expand when fully settled.
                val ox = try {
                    draggableState.requireOffset()
                } catch (_: IllegalStateException) {
                    0f
                }
                if (abs(ox) < with(density) { 4.dp.toPx() }) controller.showOverlay()
            },
            onTogglePause = controller::togglePause,
            onRewind = controller::rewind,
            onForward = controller::forward,
            hazeState = hazeState,
            tint = tint,
            modifier = Modifier
                .offset {
                    val ox = try {
                        draggableState.requireOffset()
                    } catch (_: IllegalStateException) {
                        0f
                    }
                    IntOffset(ox.roundToInt(), 0)
                }
                .graphicsLayer {
                    val scale = 1f - dragProgress * 0.15f
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - dragProgress * 0.7f
                }
                .anchoredDraggable(state = draggableState, orientation = Orientation.Horizontal),
        )
    }
}

@Composable
private fun MiniPlayerCard(
    state: AudiobookPlayerState,
    onExpand: () -> Unit,
    onTogglePause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    hazeState: HazeState?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val resolvedTint = if (tint != Color.Unspecified) {
        tint
    } else {
        if (isDark) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.2f)
    }
    val shape: Shape = RoundedCornerShape(20.dp)

    val context = LocalContext.current
    val coverRequest = remember(state.cover) {
        ImageRequest.Builder(context)
            .data(state.cover)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = shape)
            .clip(shape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = colorScheme.background,
                            tint = HazeTint(resolvedTint),
                            blurRadius = 24.dp,
                            noiseFactor = 0.12f,
                        ),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // PB-18: card body taps expand; transport icons are their
                    // own targets so they never trigger expansion.
                    // indication = null: the press ripple fires on ACTION_DOWN
                    // and only cancels once the drag wins slop — a visible
                    // flash at the start of every horizontal swipe.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = stringResource(AYMR.strings.audiobook_mini_expand_cd),
                        onClick = onExpand,
                    )
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ItemCover.Square(
                    data = coverRequest,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(48.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.novelTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.track?.chapterName.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Progress line sits directly under the chapter title,
                    // inside the text column — not a full-width card hairline.
                    LinearProgressIndicator(
                        progress = {
                            if (state.durationMs > 0) {
                                (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(50)),
                        color = colorScheme.primary,
                        trackColor = colorScheme.onSurface.copy(alpha = 0.15f),
                        drawStopIndicator = {},
                    )
                }
                IconButton(onClick = onRewind, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Replay30,
                        contentDescription = stringResource(AYMR.strings.audiobook_rewind_cd),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onTogglePause, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) AYMR.strings.action_pause else AYMR.strings.action_play,
                        ),
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(onClick = onForward, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Forward10,
                        contentDescription = stringResource(AYMR.strings.audiobook_forward_cd),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
