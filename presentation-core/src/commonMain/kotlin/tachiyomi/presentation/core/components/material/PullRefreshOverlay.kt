package tachiyomi.presentation.core.components.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Shared slot that allows [PullRefresh] (which lives in the Scaffold's body
 * content) to register its indicator composable so the [Scaffold] can draw it
 * in an overlay slot **after** the topBar — on top of the floating glass top
 * bar — without using a Popup (which would intercept touch events).
 *
 * The Scaffold creates the slot, provides it via [LocalPullRefreshOverlay],
 * and composes [content] in its overlay slot. PullRefresh reads the
 * CompositionLocal and sets [content] to its indicator.
 *
 * Uses a plain `var` (not `mutableStateOf`) because the value is written
 * during the body content's composition and read in the same measure pass
 * when the overlay is subcomposed. `mutableStateOf` defers writes until
 * after composition is applied, which would cause the overlay to miss the
 * value in the same pass.
 */
class PullRefreshOverlaySlot {
    @Volatile
    var content: (@Composable () -> Unit)? = null

    /**
     * Clear the slot. Called by the Scaffold at the start of each measure
     * pass so stale indicators from a removed PullRefresh don't linger.
     */
    fun clear() {
        content = null
    }
}

val LocalPullRefreshOverlay = compositionLocalOf<PullRefreshOverlaySlot?> { null }
