package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    val overlaySlot = LocalPullRefreshOverlay.current

    Box(
        modifier = modifier
            .pullToRefresh(
                isRefreshing = refreshing,
                state = state,
                enabled = enabled,
                onRefresh = onRefresh,
            ),
    ) {
        content()
    }

    // Register the indicator with the Scaffold's overlay slot so it is drawn
    // AFTER the topBar — on top of the floating glass top bar — without using
    // a Popup (which would intercept touch events and block navigation).
    //
    // The overlay slot is provided by the Scaffold via CompositionLocal. The
    // indicator composable is set here and composed by the Scaffold's overlay
    // slot, which is placed after the topBar in the SubcomposeLayout.
    //
    // If no Scaffold is present (overlaySlot is null), the indicator is not
    // shown — the pull-to-refresh gesture still works, just without a visible
    // indicator.
    if (overlaySlot != null) {
        overlaySlot.content = {
            Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(indicatorPadding),
                    isRefreshing = refreshing,
                    state = state,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
