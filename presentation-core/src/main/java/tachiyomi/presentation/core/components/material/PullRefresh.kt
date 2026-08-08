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

        // Draw the indicator directly on top of the content, aligned to
        // the top center. This ensures the indicator is in the same
        // composition tree as the pullToRefresh modifier and state,
        // so it properly tracks the pull gesture and refresh state.
        // The indicator is drawn after content so it appears on top.
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

    // Also register with the Scaffold's overlay slot so the indicator
    // can be drawn above the floating glass top bar if needed.
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
