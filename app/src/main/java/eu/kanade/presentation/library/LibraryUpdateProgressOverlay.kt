package eu.kanade.presentation.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

// Anchors for the swipe-to-dismiss gesture.
// 0 = settled (center), 1 = dismissed to the right, -1 = dismissed to the left
private const val ANCHOR_SETTLED = 0
private const val ANCHOR_DISMISSED = 1

/**
 * Floating glassmorphic progress overlay for library updates, mirroring the
 * floating glass navigation bar's visual language: rounded card with Haze blur,
 * subtle border, soft shadow, and translucent surface.
 *
 * Supports multiple concurrent sources: when the user refreshes multiple modes
 * (e.g. anime then manga), each mode gets its own progress section stacked in
 * the overlay. The card grows taller with an animated reveal when a new source
 * starts, and sections shrink away when their source completes.
 *
 * Sits above the bottom navigation bar using [LocalHostScaffoldContentPadding].
 * Persists across main navigation tabs; auto-hides completed sections after a
 * short delay, and hides entirely when no sources remain.
 *
 * Gated by [LibraryPreferences.showUpdateProgressOverlay]. Per-instance hide
 * (swipe-dismiss or cancel) only suppresses the current run; the next pull or
 * auto-update will show it again (detected via [runGeneration]).
 *
 * Swipe-to-dismiss uses [anchoredDraggable] with spring physics for a natural,
 * polished feel: the card follows the finger, scales down slightly, fades out,
 * and springs off-screen past the threshold. A fast fling dismisses regardless
 * of distance.
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
    val states by LibraryUpdateProgressBus.states.collectAsState()
    val runGeneration by LibraryUpdateProgressBus.runGeneration.collectAsState()
    val refreshRequested by LibraryUpdateProgressBus.refreshRequested.collectAsState()
    val sheetVisible by LibraryUpdateProgressBus.sheetVisible.collectAsState()

    if (!enabled) return

    // Active sources: Running or Completed (Idle sources are not in the map)
    val activeSources = states.filterValues {
        it is LibraryUpdateProgress.Running || it is LibraryUpdateProgress.Completed
    }

    // Track which completed sources have already been scheduled for auto-dismiss
    val scheduledDismissals = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    // Auto-dismiss completed sources after 4 seconds, then remove from the map
    LaunchedEffect(states) {
        activeSources.forEach { (source, state) ->
            if (state is LibraryUpdateProgress.Completed && source !in scheduledDismissals) {
                scheduledDismissals.add(source)
                scope.launch {
                    delay(4000)
                    LibraryUpdateProgressBus.removeSource(source)
                    scheduledDismissals.remove(source)
                }
            }
        }
    }

    // User can swipe to hide the overlay for the current run.
    // Also hidden instantly when cancel is requested (Command.Cancel).
    var userDismissed by remember { mutableStateOf(false) }

    // Un-dismiss when a new run starts OR when the user pulls to refresh
    // again (even if the job is already running and startNow returned false).
    LaunchedEffect(runGeneration, refreshRequested) {
        if (runGeneration > 0 || refreshRequested > 0) {
            userDismissed = false
        }
    }

    // Instantly hide the overlay when cancel is requested — don't wait for
    // the job to actually cancel (which may take time for slow sources).
    // Per-source cancel (CancelSource) removes just that source from view
    // by marking it as cancelled — the job will call completeRun which
    // transitions it to Completed and then auto-dismisses.
    val context = androidx.compose.ui.platform.LocalContext.current
    val cancelledSources = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        LibraryUpdateProgressBus.commands.collect { command ->
            when (command) {
                is LibraryUpdateProgressBus.Command.Cancel -> {
                    userDismissed = true
                }
                is LibraryUpdateProgressBus.Command.CancelSource -> {
                    cancelledSources.add(command.source)
                }
                else -> {}
            }
        }
    }
    // Clear cancelled sources when generation changes (new run)
    LaunchedEffect(runGeneration) {
        cancelledSources.clear()
    }

    // Filter out cancelled sources from display
    val displaySources = activeSources.filterKeys { it !in cancelledSources }

    val hasRunning = displaySources.values.any { it is LibraryUpdateProgress.Running }

    val visible = displaySources.isNotEmpty() && !userDismissed && !sheetVisible

    // Resolve the host scaffold's bottom padding (nav bar height) so the overlay
    // sits above the nav bar regardless of floating vs. standard nav style.
    val hostPadding = LocalHostScaffoldContentPadding.current
    val navBarBottom = hostPadding?.calculateBottomPadding() ?: 0.dp

    // ---- Swipe-to-dismiss setup ----
    // anchoredDraggable provides spring physics, velocity-based fling, and
    // smooth anchor transitions — far better than manual detectDragGestures.
    val density = LocalDensity.current
    val screenWidthPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val dismissDistance = screenWidthPx * 1.5f // anchor past screen edge

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

    // Update anchors when screen width changes (configuration change, rotation)
    LaunchedEffect(screenWidthPx) {
        draggableState.updateAnchors(
            DraggableAnchors {
                ANCHOR_SETTLED at 0f
                ANCHOR_DISMISSED at dismissDistance
                -ANCHOR_DISMISSED at -dismissDistance
            },
        )
    }

    // When overlay becomes visible again (new run), reset drag state
    LaunchedEffect(visible) {
        if (visible) {
            draggableState.animateTo(ANCHOR_SETTLED)
        }
    }

    // Dismiss the overlay when dragged past a threshold anchor
    LaunchedEffect(draggableState.currentValue) {
        if (draggableState.currentValue == ANCHOR_DISMISSED ||
            draggableState.currentValue == -ANCHOR_DISMISSED
        ) {
            userDismissed = true
        }
    }

    // Drag progress for scale + opacity effects (0f = settled, 1f = fully dismissed)
    // Use try/catch around requireOffset because the state may not be
    // initialized yet during the first layout pass (anchors are set in a
    // LaunchedEffect which runs after composition).
    val dragProgress by remember {
        derivedStateOf {
            val offset = try { draggableState.requireOffset() } catch (_: IllegalStateException) { 0f }
            (kotlin.math.abs(offset) / dismissDistance).coerceIn(0f, 1f)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
            slideInVertically(
                initialOffsetY = { it / 8 },
                animationSpec = tween(300),
            ),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = navBarBottom + 12.dp)
            .padding(horizontal = 16.dp),
    ) {
        GlassOverlayCard(
            sourceStates = displaySources,
            hasRunning = hasRunning,
            hazeState = hazeState,
            tint = tint,
            onViewFailures = onViewFailures,
            onCancelSource = { source ->
                // Per-source cancel: only cancel the specific mode
                LibraryUpdateProgressBus.requestCancelSource(source, context)
            },
            onCancelAll = {
                // Cancel all running sources
                userDismissed = true
                LibraryUpdateProgressBus.requestCancel(context)
            },
            modifier = Modifier
                .offset {
                    val ox = try { draggableState.requireOffset() } catch (_: IllegalStateException) { 0f }
                    IntOffset(ox.roundToInt(), 0)
                }
                .graphicsLayer {
                    // Scale down slightly during drag for a "shrinking away" feel
                    val scale = 1f - (dragProgress * 0.15f)
                    scaleX = scale
                    scaleY = scale
                    // Fade out as the card moves off-screen
                    alpha = 1f - (dragProgress * 0.7f)
                }
                .anchoredDraggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                ),
        )
    }
}

@Composable
private fun GlassOverlayCard(
    sourceStates: Map<String, LibraryUpdateProgress>,
    hasRunning: Boolean,
    hazeState: HazeState?,
    tint: Color = Color.Unspecified,
    onViewFailures: () -> Unit,
    onCancelSource: (String) -> Unit,
    onCancelAll: () -> Unit,
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
            // Stacked progress sections — one per source.
            // Each section has its own cancel (X) button for per-mode cancel.
            // animateContentSize makes the card grow/shrink smoothly when
            // sections are added or removed.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(animationSpec = tween(300)),
            ) {
                sourceStates.forEach { (source, state) ->
                    // AnimatedVisibility per section: new sections slide in
                    // from the top, completed sections shrink away.
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(
                            initialOffsetY = { -it },
                            animationSpec = tween(300),
                        ) + fadeIn(animationSpec = tween(300)) +
                            expandVertically(animationSpec = tween(300)),
                        exit = slideOutVertically(
                            targetOffsetY = { -it },
                            animationSpec = tween(250),
                        ) + fadeOut(animationSpec = tween(250)) +
                            shrinkVertically(animationSpec = tween(250)),
                    ) {
                        SourceProgressSection(
                            state = state,
                            source = source,
                            onViewFailures = onViewFailures,
                            onCancel = if (state is LibraryUpdateProgress.Running) {
                                { onCancelSource(source) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            // Global cancel-all icon — shown when 2+ sources are running.
            // When only 1 source is running, its per-section X suffices.
            val runningCount = sourceStates.values.count { it is LibraryUpdateProgress.Running }
            if (runningCount >= 2) {
                IconButton(onClick = onCancelAll, modifier = Modifier.size(28.dp)) {
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

@Composable
private fun SourceProgressSection(
    state: LibraryUpdateProgress,
    source: String,
    onViewFailures: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary
    val running = state as? LibraryUpdateProgress.Running
    val completed = state as? LibraryUpdateProgress.Completed
    val processed = running?.processedEntries ?: completed?.totalProcessed ?: 0
    val total = running?.totalEntries ?: completed?.totalEntries ?: 0
    val failedCount = running?.failedSoFar?.size ?: completed?.failed?.size ?: 0
    val isCompleted = completed != null
    val isAllUpToDate = isCompleted && failedCount == 0 && processed == total

    // Keep the last non-null title so it doesn't disappear between entries
    var lastTitle by remember { mutableStateOf<String?>(null) }
    // Track when each title first appeared, so we can timeout stuck titles
    val titleFirstSeen = remember { mutableStateMapOf<String, Long>() }
    // Titles that have timed out (been showing too long)
    val timedOutTitles = remember { mutableStateListOf<String>() }

    val currentlyUpdating = running?.currentlyUpdating ?: emptyList()
    val currentTitles = currentlyUpdating.map { it.title }

    // Update lastTitle and track first-seen time for new titles
    if (currentTitles.isNotEmpty()) {
        lastTitle = currentTitles.first()
    }
    val now = System.currentTimeMillis()
    currentTitles.forEach { title ->
        if (title !in titleFirstSeen) {
            titleFirstSeen[title] = now
        }
    }
    // Clean up titles that are no longer updating
    titleFirstSeen.keys.removeAll { it !in currentTitles }
    timedOutTitles.removeAll { it !in currentTitles && it !in titleFirstSeen.keys }

    // Per-title timeout: check every 500ms if any title has been showing
    // for more than TITLE_TIMEOUT_MS. If so, mark it as timed out.
    // The title is hidden from display unless it's the ONLY entry currently
    // updating (in which case we always show it, even if stuck).
    LaunchedEffect(currentTitles) {
        while (true) {
            val checkTime = System.currentTimeMillis()
            titleFirstSeen.forEach { (title, firstSeen) ->
                if (checkTime - firstSeen > TITLE_TIMEOUT_MS && title !in timedOutTitles) {
                    timedOutTitles.add(title)
                }
            }
            delay(500)
        }
    }

    // Pick the first non-timed-out title to display. If all are timed out
    // and there's only one entry, show it anyway (better than showing nothing).
    val displayTitle = currentTitles.firstOrNull { it !in timedOutTitles }
        ?: if (currentTitles.size <= 1) currentTitles.firstOrNull()
        else if (!isCompleted) lastTitle else null

    Column {
        // Source label + progress count + per-section cancel button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (isAllUpToDate) {
                    "$source  ·  ${stringResource(AYMR.strings.fetching_overlay_all_up_to_date)}"
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
                modifier = Modifier.weight(1f),
            )
            // Per-section cancel button — only for running sources
            if (onCancel != null) {
                IconButton(onClick = onCancel, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(AYMR.strings.action_cancel),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
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
        // Progress bar
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
        // Failure summary
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
        // Spacer between sections
        Spacer(Modifier.height(8.dp))
    }
}

// ---- Per-title timeout constants ----
// How long a single entry title can stay visible before being hidden
// (unless it's the only entry currently updating). 15 seconds.
private const val TITLE_TIMEOUT_MS = 15_000L
