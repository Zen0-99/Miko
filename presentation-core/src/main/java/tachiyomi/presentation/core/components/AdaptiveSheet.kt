package tachiyomi.presentation.core.components

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val sheetAnimationSpec = tween<Float>(durationMillis = 350)

@Composable
fun AdaptiveSheet(
    isTabletUi: Boolean,
    enableSwipeDismiss: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxWidth = if (LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE) {
        600.dp
    } else {
        460.dp
    }

    if (isTabletUi) {
        var targetAlpha by remember { mutableFloatStateOf(0f) }
        val alpha by animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = sheetAnimationSpec,
            label = "alpha",
        )
        val internalOnDismissRequest: () -> Unit = {
            scope.launch {
                targetAlpha = 0f
                onDismissRequest()
            }
        }
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = internalOnDismissRequest,
                )
                .fillMaxSize()
                .alpha(alpha),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .requiredWidthIn(max = maxWidth)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {},
                    )
                    .padding(vertical = 16.dp)
                    .then(modifier),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = {
                    BackHandler(enabled = alpha > 0f, onBack = internalOnDismissRequest)
                    content()
                },
            )

            LaunchedEffect(Unit) {
                targetAlpha = 1f
            }
        }
    } else {
        val screenHeightDpForLog = LocalConfiguration.current.screenHeightDp
        val decayAnimationSpec = rememberSplineBasedDecay<Float>()
        val anchoredDraggableState = remember {
            AnchoredDraggableState(
                initialValue = 1,
                positionalThreshold = { with(density) { 56.dp.toPx() } },
                velocityThreshold = { with(density) { 125.dp.toPx() } },
                snapAnimationSpec = sheetAnimationSpec,
                decayAnimationSpec = decayAnimationSpec,
            )
        }
        val internalOnDismissRequest = {
            android.util.Log.d("AdaptiveSheet", "internalOnDismissRequest called. settledValue=${anchoredDraggableState.settledValue}")
            if (anchoredDraggableState.settledValue == 0) {
                scope.launch { anchoredDraggableState.animateTo(1) }
            }
        }

        // Guard: the original tap that opened the sheet can leak into the
        // Dialog's scrim clickable, causing the sheet to immediately close.
        // Disable scrim dismiss for 300ms after the sheet appears to absorb
        // that stray touch event.
        var dismissEnabled by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(300)
            dismissEnabled = true
            android.util.Log.d("AdaptiveSheet", "dismissEnabled = true (scrim clickable now active)")
        }

        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = dismissEnabled,
                    onClick = {
                        android.util.Log.d("AdaptiveSheet", "SCRIM CLICKED — calling internalOnDismissRequest")
                        internalOnDismissRequest()
                    },
                )
                .fillMaxSize()
                .onSizeChanged {
                    android.util.Log.d(
                        "AdaptiveSheet",
                        "SCRIM(window) onSizeChanged: w=${it.width} h=${it.height} " +
                            "density=${density.density} screenH=${screenHeightDpForLog}dp",
                    )
                    val anchors = DraggableAnchors {
                        0 at 0f
                        1 at it.height.toFloat()
                    }
                    anchoredDraggableState.updateAnchors(anchors)
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Constrain the sheet height so tall content scrolls instead of
            // extending beyond the screen top (which cuts off the top items).
            val screenHeight = LocalConfiguration.current.screenHeightDp.dp
            Surface(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .heightIn(max = screenHeight * 0.85f)
                    .onSizeChanged {
                        android.util.Log.d("AdaptiveSheet", "Surface onSizeChanged: w=${it.width} h=${it.height}")
                    }
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {},
                    )
                    .then(
                        if (enableSwipeDismiss) {
                            Modifier.nestedScroll(
                                remember(anchoredDraggableState) {
                                    anchoredDraggableState.preUpPostDownNestedScrollConnection(
                                        onFling = { scope.launch { anchoredDraggableState.settle(it) } },
                                    )
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(modifier)
                    .offset {
                        val o = anchoredDraggableState.offset
                            .takeIf { it.isFinite() }
                            ?.roundToInt()
                            ?: 0
                        IntOffset(0, o)
                    }
                    .anchoredDraggable(
                        state = anchoredDraggableState,
                        orientation = Orientation.Vertical,
                        enabled = enableSwipeDismiss,
                    ),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = {
                    BackHandler(
                        enabled = anchoredDraggableState.targetValue == 0,
                        onBack = internalOnDismissRequest,
                    )
                    content()
                },
            )

            LaunchedEffect(anchoredDraggableState) {
                android.util.Log.d("AdaptiveSheet", "LaunchedEffect: animateTo(0) start. state=${anchoredDraggableState.settledValue}")
                scope.launch {
                    anchoredDraggableState.animateTo(0)
                    android.util.Log.d("AdaptiveSheet", "animateTo(0) done. state=${anchoredDraggableState.settledValue} offset=${anchoredDraggableState.offset}")
                }
                snapshotFlow { anchoredDraggableState.settledValue }
                    .drop(1)
                    .filter { it == 1 }
                    .collectLatest {
                        android.util.Log.d("AdaptiveSheet", "settled to 1 → onDismissRequest")
                        onDismissRequest()
                    }
            }
        }
    }
}

private fun <T> AnchoredDraggableState<T>.preUpPostDownNestedScrollConnection(
    onFling: (velocity: Float) -> Unit,
) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.toFloat()
        return if (delta < 0 && source == NestedScrollSource.UserInput) {
            dispatchRawDelta(delta).toOffset()
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return if (source == NestedScrollSource.UserInput) {
            dispatchRawDelta(available.toFloat()).toOffset()
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val toFling = available.toFloat()
        return if (toFling < 0 && offset > anchors.minAnchor()) {
            onFling(toFling)
            // since we go to the anchor with tween settling, consume all for the best UX
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        onFling(available.toFloat())
        return available
    }

    private fun Float.toOffset(): Offset = Offset(0f, this)

    @JvmName("velocityToFloat")
    private fun Velocity.toFloat() = this.y

    @JvmName("offsetToFloat")
    private fun Offset.toFloat(): Float = this.y
}
