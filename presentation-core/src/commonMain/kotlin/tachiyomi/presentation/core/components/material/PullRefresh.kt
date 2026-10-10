package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * @param refreshing Whether the layout is currently refreshing
 * @param onRefresh Lambda which is invoked when a swipe to refresh gesture is completed.
 * @param enabled Whether the the layout should react to swipe gestures or not.
 * @param indicatorPadding Content padding for the indicator, to inset the indicator in if required.
 * @param content The content containing a vertically scrollable composable.
 */
@Composable
fun PullRefresh(
    refreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    state: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable BoxScope.() -> Unit,
) {
    val overlaySlot = LocalPullRefreshOverlay.current

    // The top spinner is a manual-refresh visual only. [refreshing] also
    // goes true for automatic loads (first load, pagination, preview→live
    // handoff), so it can't drive the indicator alone — latch on the pull
    // gesture's own onRefresh and clear once the load settles. The gated
    // value must feed the modifier too: pullToRefresh(isRefreshing=true)
    // animates state.distanceFraction to threshold, and the Indicator
    // draws its arc from distanceFraction regardless of its own
    // isRefreshing flag — passing raw `refreshing` would still pop the
    // circle on every automatic load. While the finger is down the
    // gesture drives distanceFraction directly, so pull feedback is
    // unaffected; a pull mid-load is allowed and arms the spinner.
    var pulled by remember { mutableStateOf(false) }
    // Minimum visible run — instant-refresh paths (canonical relaunch,
    // no-op guards, mint veils) flicker `refreshing` true→false within a
    // frame or two of the pull, or never set it at all. Releasing the
    // latch that fast strands the indicator mid-drop: the M3 node's
    // settle gets superseded before it can run, and distanceFraction
    // stays frozen. [minHold] is armed in the same snapshot write as
    // [pulled] so the clear-effect can't beat it; the latch lives long
    // enough for one full drop → spin → retract cycle.
    var minHold by remember { mutableStateOf(false) }
    LaunchedEffect(pulled) {
        if (!pulled) {
            minHold = false
            return@LaunchedEffect
        }
        delay(600)
        minHold = false
    }
    LaunchedEffect(refreshing, minHold) {
        if (!refreshing && !minHold) pulled = false
    }
    val showRefreshing = pulled && (refreshing || minHold)

    Box(
        modifier = modifier
            .pullToRefresh(
                isRefreshing = showRefreshing,
                state = state,
                enabled = enabled,
                onRefresh = {
                    pulled = true
                    minHold = true
                    onRefresh()
                },
            ),
    ) {
        content()

        // Draw the indicator directly on top of the content, aligned to
        // the top center. This ensures the indicator is in the same
        // composition tree as the pullToRefresh modifier and state,
        // so it properly tracks the pull gesture and refresh state.
        // The indicator is drawn after content so it appears on top.
        PullToRefreshDefaults.Indicator(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(indicatorPadding),
            isRefreshing = showRefreshing,
            state = state,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Also register with the Scaffold's overlay slot so the indicator
    // can be drawn above the floating glass top bar if needed.
    if (overlaySlot != null) {
        overlaySlot.content = {
            Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(indicatorPadding),
                    isRefreshing = showRefreshing,
                    state = state,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
