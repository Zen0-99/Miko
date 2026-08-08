package eu.kanade.presentation.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import eu.kanade.presentation.components.LocalHostScaffoldContentPadding
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgress
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Floating glassmorphic progress overlay for library updates, mirroring the
 * floating glass navigation bar's visual language: rounded card with Haze blur,
 * subtle border, soft shadow, and translucent surface.
 *
 * Sits above the bottom navigation bar using [LocalHostScaffoldContentPadding].
 * Persists across main navigation tabs; auto-hides on completion after a short delay.
 *
 * Gated by [LibraryPreferences.showUpdateProgressOverlay]. Per-instance hide
 * (the eye icon) only suppresses the current run; the next pull or auto-update
 * will show it again.
 */
@Composable
fun LibraryUpdateProgressOverlay(
    onViewFailures: () -> Unit,
    hazeState: HazeState? = null,
    tint: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val enabled by libraryPreferences.showUpdateProgressOverlay().collectAsState()
    val state by LibraryUpdateProgressBus.state.collectAsState()
    val context = LocalContext.current

    if (!enabled) return

    // Auto-dismiss Completed state after 4 seconds, then transition to Idle
    // so the overlay doesn't reappear when the composable is recreated.
    var completedVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        when (state) {
            is LibraryUpdateProgress.Running -> completedVisible = true
            is LibraryUpdateProgress.Completed -> {
                completedVisible = true
                delay(4000)
                completedVisible = false
                // Clear the bus state so navigation/recomposition doesn't
                // re-trigger the Completed overlay.
                LibraryUpdateProgressBus.idle()
            }
            LibraryUpdateProgress.Idle -> completedVisible = false
        }
    }

    // User can swipe down to hide the overlay for the current run.
    var userDismissed by remember { mutableStateOf(false) }
    // Reset dismiss when a new run starts (state transitions to Running from
    // a different source, or from Idle/Completed).
    var lastSource by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state) {
        if (state is LibraryUpdateProgress.Running) {
            val source = (state as LibraryUpdateProgress.Running).source
            if (source != lastSource) {
                userDismissed = false
                lastSource = source
            }
        } else if (state is LibraryUpdateProgress.Idle) {
            lastSource = null
            userDismissed = false
        }
    }

    val visible = completedVisible && !userDismissed &&
        (state is LibraryUpdateProgress.Running || state is LibraryUpdateProgress.Completed)

    // Resolve the host scaffold's bottom padding (nav bar height) so the overlay
    // sits above the nav bar regardless of floating vs. standard nav style.
    val hostPadding = LocalHostScaffoldContentPadding.current
    val navBarBottom = hostPadding?.calculateBottomPadding() ?: 0.dp

    // Swipe-side dismiss: user can drag the overlay sideways to hide it.
    // When dismissed by swipe, the card animates off-screen in the swipe
    // direction (left or right). Auto-dismiss uses fade + slide down.
    val dismissThreshold = with(LocalDensity.current) { 200.dp.toPx() }
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    var dragOffset by remember { mutableStateOf(0f) }
    // Track whether the overlay is being dismissed by swipe (vs auto-dismiss)
    var swipeDismissDirection by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
            slideInVertically(
                initialOffsetY = { it / 8 },
                animationSpec = tween(300),
            ),
        exit = fadeOut(animationSpec = tween(250)) +
            slideOutVertically(
                targetOffsetY = { it / 8 },
                animationSpec = tween(250),
            ),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = navBarBottom + 12.dp)
            .padding(horizontal = 16.dp),
    ) {
        GlassOverlayCard(
            state = state,
            hazeState = hazeState,
            tint = tint,
            onViewFailures = onViewFailures,
            onPause = LibraryUpdateProgressBus::requestPause,
            onResume = { LibraryUpdateProgressBus.resumeRun(context) },
            onCancel = LibraryUpdateProgressBus::requestCancel,
            modifier = Modifier
                .graphicsLayer {
                    translationX = if (swipeDismissDirection != 0f) {
                        // Animate off-screen in the swipe direction
                        swipeDismissDirection * screenWidthPx
                    } else {
                        dragOffset
                    }
                    alpha = if (swipeDismissDirection != 0f) {
                        1f - (kotlin.math.abs(swipeDismissDirection * screenWidthPx) / screenWidthPx).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(dragOffset) > dismissThreshold) {
                                // Animate off-screen in the swipe direction
                                val direction = if (dragOffset > 0) 1f else -1f
                                scope.launch {
                                    // Animate the swipe dismissal
                                    animatableSwipeDismiss(direction, screenWidthPx) { progress ->
                                        swipeDismissDirection = progress
                                    }
                                    userDismissed = true
                                    swipeDismissDirection = 0f
                                }
                            }
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                    ) { _, dragAmount ->
                        // Respond to horizontal drags (either direction)
                        if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y)) {
                            dragOffset += dragAmount.x
                        }
                    }
                },
        )
    }
}

@Composable
private fun GlassOverlayCard(
    state: LibraryUpdateProgress,
    hazeState: HazeState?,
    tint: Color = Color.Unspecified,
    onViewFailures: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val running = state as? LibraryUpdateProgress.Running
    val completed = state as? LibraryUpdateProgress.Completed
    val isPaused = running?.isPaused == true
    val processed = running?.processedEntries ?: completed?.totalProcessed ?: 0
    val total = running?.totalEntries ?: completed?.totalEntries ?: 0
    val failedCount = running?.failedSoFar?.size ?: completed?.failed?.size ?: 0
    // Keep the last non-null title so it doesn't disappear between entries
    // during long fetches (the currentlyUpdating list can be briefly empty).
    var lastTitle by remember { mutableStateOf<String?>(null) }
    val currentTitle = running?.currentlyUpdating?.firstOrNull()?.title
    if (currentTitle != null) lastTitle = currentTitle
    val displayTitle = currentTitle ?: lastTitle
    val source = running?.source ?: completed?.source ?: ""
    val isCompleted = completed != null
    val isAllUpToDate = isCompleted && failedCount == 0 && processed == total

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    // Use the unified glass tint (passed from caller) so the overlay reads
    // as the same glass surface as the nav bar and top bar.
    val resolvedTint = if (tint != Color.Unspecified) {
        tint
    } else {
        // Fallback if no tint passed
        if (isDark) {
            Color.Black.copy(alpha = 0.2f)
        } else {
            Color.White.copy(alpha = 0.2f)
        }
    }

    val shape: Shape = RoundedCornerShape(20.dp)
    val primaryColor = colorScheme.primary

    val glassModifier = Modifier
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
        )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(glassModifier),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Text + progress column (no leading icon — clean glassmorphic look)
            Column(modifier = Modifier.weight(1f)) {
                // Primary line: "X / Y  ·  Source" or "All up to date"
                Text(
                    text = if (isAllUpToDate) {
                        stringResource(AYMR.strings.fetching_overlay_all_up_to_date)
                    } else {
                        buildString {
                            append("$processed / $total")
                            if (source.isNotEmpty()) append("  ·  $source")
                        }
                    },
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Current entry (only while running)
                if (displayTitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = displayTitle,
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Progress bar — custom drawn to avoid M3 LinearProgressIndicator's
                // end dot/gap artifact.
                if (total > 0 && !isAllUpToDate) {
                    Spacer(Modifier.height(6.dp))
                    val progress = if (total > 0) processed.toFloat() / total.toFloat() else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(primaryColor.copy(alpha = 0.15f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(primaryColor),
                        )
                    }
                }
                // Failure summary (tappable to open Fetching tab)
                if (failedCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.error.copy(alpha = 0.12f))
                            .clickable(onClick = onViewFailures)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = stringResource(AYMR.strings.fetching_overlay_view_failures, failedCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Action icons column
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (running != null) {
                    IconButton(onClick = if (isPaused) onResume else onPause, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (isPaused) stringResource(AYMR.strings.action_resume) else stringResource(AYMR.strings.action_pause),
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(AYMR.strings.action_cancel),
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animate the swipe dismissal: interpolate from 0 to [direction] over ~250ms,
 * calling [onProgress] with the current value each frame so the caller can
 * update the graphicsLayer translationX.
 */
private suspend fun animatableSwipeDismiss(
    direction: Float,
    screenWidthPx: Float,
    onProgress: (Float) -> Unit,
) {
    val durationMs = 250L
    val startTime = System.currentTimeMillis()
    while (true) {
        val elapsed = System.currentTimeMillis() - startTime
        val t = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
        // Ease-out for natural deceleration
        val eased = 1f - (1f - t) * (1f - t)
        onProgress(direction * eased)
        if (t >= 1f) break
        kotlinx.coroutines.delay(16)
    }
}
