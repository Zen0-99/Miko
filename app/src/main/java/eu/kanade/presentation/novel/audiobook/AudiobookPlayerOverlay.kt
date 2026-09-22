package eu.kanade.presentation.novel.audiobook

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlayerController
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlayerState
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.SleepTimer
import kotlin.math.roundToInt
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

// Swipe-down dismiss anchors: 0 = open, 1 = dragged past screen bottom.
private const val ANCHOR_OPEN = 0
private const val ANCHOR_DISMISSED = 1

/**
 * Full-screen audiobook player overlay (REQ-008, D-08).
 *
 * Attached at the app root (MainActivity's navigator Box) so it covers every
 * screen including pushed detail screens. Visibility is a plain state flag
 * on [AudiobookPlayerController] — no navigation, so dismissing (system
 * back or swipe-down, PB-8) leaves playback running through the session
 * service.
 *
 * Styling follows Retro Music Player: a full-bleed blurred-cover backdrop
 * with a dark scrim, a large rounded cover, and a clean transport row.
 */
@Composable
fun AudiobookPlayerOverlay(
    controller: AudiobookPlayerController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val visible = state.overlayVisible && state.track != null

    BackHandler(enabled = visible) {
        controller.hideOverlay()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) +
            fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) +
            fadeOut(animationSpec = tween(250)),
        modifier = modifier,
    ) {
        PlayerSurface(state = state, controller = controller)
    }
}

@Composable
private fun PlayerSurface(
    state: AudiobookPlayerState,
    controller: AudiobookPlayerController,
) {
    val context = LocalContext.current
    val coverRequest = remember(state.cover) {
        ImageRequest.Builder(context)
            .data(state.cover)
            .crossfade(true)
            .build()
    }

    // ---- Swipe-down dismiss (PB-8) — vertical anchoredDraggable over the
    // whole surface; the seek Slider only consumes horizontal drags.
    val density = LocalDensity.current
    val dismissDistance = with(density) {
        LocalConfiguration.current.screenHeightDp.dp.toPx() * 1.2f
    }
    val decayAnimationSpec = rememberSplineBasedDecay<Float>()
    val draggableState = remember {
        AnchoredDraggableState(
            initialValue = ANCHOR_OPEN,
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
                ANCHOR_OPEN at 0f
                ANCHOR_DISMISSED at dismissDistance
            },
        )
    }
    // Reset to open when the overlay is re-shown.
    LaunchedEffect(state.overlayVisible) {
        if (state.overlayVisible) draggableState.animateTo(ANCHOR_OPEN)
    }
    LaunchedEffect(draggableState.currentValue) {
        if (draggableState.currentValue == ANCHOR_DISMISSED) {
            controller.hideOverlay()
        }
    }
    val dragProgress by remember {
        derivedStateOf {
            val offset = try { draggableState.requireOffset() } catch (_: IllegalStateException) { 0f }
            (offset / dismissDistance).coerceIn(0f, 1f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Opaque base — a missing cover must never leak the screen beneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101010)),
        )
        // Backdrop: blurred cover art + dark scrim (Retro-style).
        AsyncImage(
            model = coverRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(32.dp)
                .alpha(0.35f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    val oy = try { draggableState.requireOffset() } catch (_: IllegalStateException) { 0f }
                    IntOffset(0, oy.roundToInt())
                }
                .graphicsLayer { alpha = 1f - dragProgress * 0.6f }
                .anchoredDraggable(state = draggableState, orientation = Orientation.Vertical)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar — collapse chevron + sleep timer.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = controller::hideOverlay) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(AYMR.strings.action_dismiss),
                        tint = Color.White,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                SleepTimerButton(state = state, controller = controller)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Large cover (PB-11: ItemCover supplies placeholder + error art).
            ItemCover.Book(
                data = coverRequest,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .sizeIn(maxWidth = 280.dp)
                    .fillMaxWidth(0.72f)
                    .shadow(24.dp, RoundedCornerShape(24.dp)),
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = state.track?.chapterName.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.novelTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(24.dp))

            SeekRow(state = state, onSeekTo = controller::seekTo)

            Spacer(modifier = Modifier.height(8.dp))

            TransportRow(
                state = state,
                onTogglePause = controller::togglePause,
                onRewind = controller::rewind,
                onForward = controller::forward,
                onPrevious = controller::previousChapter,
                onNext = controller::nextChapter,
            )

            Spacer(modifier = Modifier.weight(1f))

            SpeedChip(state = state, onSetSpeed = controller::setSpeed)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SeekRow(state: AudiobookPlayerState, onSeekTo: (Long) -> Unit) {
    // Scrub-live (PB-12): local value while dragging, commit on release.
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val position = scrubPosition?.toLong() ?: state.positionMs.coerceIn(0L, duration)

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = position.toFloat(),
            onValueChange = { scrubPosition = it },
            onValueChangeFinished = {
                scrubPosition?.let { onSeekTo(it.toLong()) }
                scrubPosition = null
            },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDurationMs(position),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = "-${formatDurationMs(duration - position)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun TransportRow(
    state: AudiobookPlayerState,
    onTogglePause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(
            onClick = onPrevious,
            // PB-13: disabled at playlist bounds, not hidden.
            enabled = state.chapterIndex > 0,
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(AYMR.strings.audiobook_previous_chapter_cd),
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onRewind) {
            Icon(
                imageVector = Icons.Filled.Replay30,
                contentDescription = stringResource(AYMR.strings.audiobook_rewind_cd),
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        FilledIconButton(
            onClick = onTogglePause,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (state.isPlaying) AYMR.strings.action_pause else AYMR.strings.action_play,
                ),
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = onForward) {
            Icon(
                imageVector = Icons.Filled.Forward10,
                contentDescription = stringResource(AYMR.strings.audiobook_forward_cd),
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(
            onClick = onNext,
            enabled = state.chapterIndex in 0 until state.chapterCount - 1,
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(AYMR.strings.audiobook_next_chapter_cd),
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun SpeedChip(state: AudiobookPlayerState, onSetSpeed: (Float) -> Unit) {
    var dialogOpen by remember { mutableStateOf(false) }

    Surface(
        onClick = { dialogOpen = true },
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.15f),
    ) {
        Text(
            text = "%.1f×".format(state.speed),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    if (dialogOpen) {
        // PB-9: 0.5–3.0 in 0.1 steps — same convention as the TTS rate control.
        var sliderValue by remember { mutableStateOf(state.speed) }
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(stringResource(AYMR.strings.audiobook_speed_title)) },
            text = {
                Column {
                    Text(
                        text = "%.1f×".format(sliderValue),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = (it * 10).roundToInt() / 10f },
                        onValueChangeFinished = { onSetSpeed(sliderValue) },
                        valueRange = 0.5f..3.0f,
                        steps = 24,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onSetSpeed(sliderValue); dialogOpen = false }) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
        )
    }
}

@Composable
private fun SleepTimerButton(state: AudiobookPlayerState, controller: AudiobookPlayerController) {
    var dialogOpen by remember { mutableStateOf(false) }
    val armed = state.sleepTimer !is SleepTimer.Off

    IconButton(onClick = { dialogOpen = true }) {
        Icon(
            imageVector = Icons.Outlined.Bedtime,
            contentDescription = stringResource(AYMR.strings.audiobook_sleep_timer),
            tint = if (armed) MaterialTheme.colorScheme.primary else Color.White,
        )
    }

    if (dialogOpen) {
        val now = System.currentTimeMillis()
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(stringResource(AYMR.strings.audiobook_sleep_timer)) },
            text = {
                Column {
                    // Active-state line (PB-10 countdown).
                    when (val timer = state.sleepTimer) {
                        is SleepTimer.Minutes -> Text(
                            text = stringResource(
                                AYMR.strings.audiobook_sleep_timer_stops_in,
                                formatDurationMs(timer.remainingMs(now)),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        SleepTimer.EndOfChapter -> Text(
                            text = stringResource(AYMR.strings.audiobook_sleep_timer_stops_at_chapter_end),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        SleepTimer.Off -> Unit
                    }
                    SleepTimer.MINUTE_PRESETS.forEach { minutes ->
                        SleepTimerRow(
                            label = stringResource(AYMR.strings.audiobook_sleep_timer_minutes, minutes),
                            selected = (state.sleepTimer as? SleepTimer.Minutes)?.presetMinutes == minutes,
                            onClick = {
                                controller.armSleepMinutes(minutes)
                                dialogOpen = false
                            },
                        )
                    }
                    SleepTimerRow(
                        label = stringResource(AYMR.strings.audiobook_sleep_timer_end_of_chapter),
                        selected = state.sleepTimer is SleepTimer.EndOfChapter,
                        onClick = {
                            controller.armEndOfChapter()
                            dialogOpen = false
                        },
                    )
                    if (armed) {
                        SleepTimerRow(
                            label = stringResource(AYMR.strings.audiobook_sleep_timer_off),
                            selected = false,
                            onClick = {
                                controller.disarmSleepTimer()
                                dialogOpen = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(stringResource(MR.strings.action_close))
                }
            },
        )
    }
}

@Composable
private fun SleepTimerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    )
}

/** `m:ss` for sub-hour durations, `h:mm:ss` past an hour. */
private fun formatDurationMs(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
