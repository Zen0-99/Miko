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
import androidx.compose.foundation.gestures.animateTo
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgress
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlayerController
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlayerState
import kotlin.math.abs
import kotlin.math.roundToInt
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
 * [LocalHostScaffoldContentPadding] and lifts itself above the update
 * overlay when both are showing (PB-15).
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

    var userDismissed by remember { mutableStateOf(false) }

    // A new play session re-shows the card after a swipe-away (PB-16).
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) userDismissed = false
    }

    val visible = state.track != null && !userDismissed && !state.overlayVisible

    // Stacking (PB-15): the update-progress card owns the slot nearest the
    // nav bar; lift the mini-player above it while a run is displayed.
    val updateStates by LibraryUpdateProgressBus.states.collectAsState()
    val updateOverlayActive = updateStates.values.any {
        it is LibraryUpdateProgress.Running || it is LibraryUpdateProgress.Completed
    }

    val hostPadding = LocalHostScaffoldContentPadding.current
    val navBarBottom = hostPadding?.calculateBottomPadding() ?: 0.dp
    val bottomPadding = navBarBottom + 12.dp + if (updateOverlayActive) UPDATE_CARD_LIFT else 0.dp

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
        if (visible) draggableState.animateTo(ANCHOR_SETTLED)
    }
    LaunchedEffect(draggableState.currentValue) {
        if (draggableState.currentValue == ANCHOR_DISMISSED ||
            draggableState.currentValue == -ANCHOR_DISMISSED
        ) {
            // D-09: swiping the card away pauses playback.
            controller.pause()
            userDismissed = true
        }
    }
    val dragProgress by remember {
        derivedStateOf {
            val offset = try { draggableState.requireOffset() } catch (_: IllegalStateException) { 0f }
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
            onExpand = controller::showOverlay,
            onTogglePause = controller::togglePause,
            onRewind = controller::rewind,
            onForward = controller::forward,
            hazeState = hazeState,
            tint = tint,
            modifier = Modifier
                .offset {
                    val ox = try { draggableState.requireOffset() } catch (_: IllegalStateException) { 0f }
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
                    .clickable(
                        onClickLabel = stringResource(AYMR.strings.audiobook_mini_expand_cd),
                        onClick = onExpand,
                    )
                    .padding(start = 10.dp, top = 6.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ItemCover.Square(
                    data = coverRequest,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(40.dp),
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
            // Bottom hairline progress (PB-17).
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
                    .height(2.dp),
                color = colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
    }
}

/** Extra bottom offset so the card clears the update-progress overlay (PB-15). */
private val UPDATE_CARD_LIFT = 68.dp
