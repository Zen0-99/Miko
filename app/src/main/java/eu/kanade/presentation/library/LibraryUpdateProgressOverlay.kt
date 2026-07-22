package eu.kanade.presentation.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tachiyomi.presentation.core.util.collectAsState as preferenceCollectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgress
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import kotlinx.coroutines.delay
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Floating top-center progress overlay for library updates, modeled on the
 * achievement unlock banner. Shows overall progress, the current entry being
 * fetched, and pause/resume/cancel/hide controls. Persists across main
 * navigation tabs; auto-hides on completion after a short delay.
 *
 * The overlay is gated by [LibraryPreferences.showUpdateProgressOverlay].
 * Per-instance hide (the eye icon) only suppresses the current run; the next
 * pull or auto-update will show it again.
 */
@Composable
fun LibraryUpdateProgressOverlay(
    onViewFailures: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val enabled by libraryPreferences.showUpdateProgressOverlay().preferenceCollectAsState()
    val state by LibraryUpdateProgressBus.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Per-instance hide flag — resets when a new run starts
    var hiddenForCurrentRun by remember { mutableStateOf(false) }
    var lastRunSource by remember { mutableStateOf<String?>(null) }

    // Reset hide flag when a new run starts (source changes from Idle/Completed to Running)
    LaunchedEffect(state) {
        if (state is LibraryUpdateProgress.Running) {
            val source = (state as LibraryUpdateProgress.Running).source
            if (source != lastRunSource) {
                hiddenForCurrentRun = false
                lastRunSource = source
            }
        } else if (state is LibraryUpdateProgress.Idle) {
            lastRunSource = null
        }
    }

    if (!enabled) return

    // Auto-dismiss Completed state after 4 seconds
    var completedVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        when (state) {
            is LibraryUpdateProgress.Running -> completedVisible = true
            is LibraryUpdateProgress.Completed -> {
                completedVisible = true
                delay(4000)
                completedVisible = false
            }
            LibraryUpdateProgress.Idle -> completedVisible = false
        }
    }

    val visible = completedVisible &&
        !hiddenForCurrentRun &&
        (state is LibraryUpdateProgress.Running || state is LibraryUpdateProgress.Completed)

    // Slide from top with bounce (mirrors achievement banner)
    val slideOffset by animateFloatAsState(
        targetValue = if (visible) 0f else -120f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "overlay_slide",
    )

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        ) + fadeIn(animationSpec = tween(300)),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(200),
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier,
    ) {
        OverlayCard(
            state = state,
            slideOffsetDp = slideOffset,
            onHide = { hiddenForCurrentRun = true },
            onViewFailures = onViewFailures,
            onPause = LibraryUpdateProgressBus::requestPause,
            onResume = { LibraryUpdateProgressBus.resumeRun(context) },
            onCancel = LibraryUpdateProgressBus::requestCancel,
        )
    }
}

@Composable
private fun OverlayCard(
    state: LibraryUpdateProgress,
    slideOffsetDp: Float,
    onHide: () -> Unit,
    onViewFailures: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val running = state as? LibraryUpdateProgress.Running
    val completed = state as? LibraryUpdateProgress.Completed
    val isPaused = running?.isPaused == true
    val processed = running?.processedEntries ?: completed?.totalProcessed ?: 0
    val total = running?.totalEntries ?: completed?.totalProcessed ?: 0
    val failedCount = running?.failedSoFar?.size ?: completed?.failed?.size ?: 0
    val currentTitle = running?.currentlyUpdating?.firstOrNull()?.title
    val source = running?.source ?: completed?.source ?: ""

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                )
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(16.dp),
                ),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.padding(14.dp)) {
                // Header row: title + action icons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.fetching_overlay_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Row {
                        if (running != null) {
                            IconButton(onClick = if (isPaused) onResume else onPause, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = if (isPaused) stringResource(AYMR.strings.action_resume) else stringResource(AYMR.strings.action_pause),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(AYMR.strings.action_cancel), modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(onClick = onHide, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.VisibilityOff, contentDescription = stringResource(AYMR.strings.fetching_overlay_hide), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Progress line
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) processed.toFloat() / total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "$processed / $total" + if (source.isNotEmpty()) "  ·  $source" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Current entry (only while running)
                if (currentTitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = currentTitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Failure summary (tappable to open Fetching tab)
                if (failedCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            .clickable(onClick = onViewFailures)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(AYMR.strings.fetching_overlay_view_failures, failedCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

