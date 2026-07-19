package eu.kanade.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Propagates the host Scaffold's [PaddingValues] (most importantly its bottom inset that
 * accounts for the floating NavigationBar height) down to every tab content rendered inside
 * the HomeScreen. The host intentionally extends the body under the bottomBar for an
 * edge-to-edge look, so without this hook every tab's LazyColumn would scroll its last
 * items behind the floating bar.
 */
val LocalHostScaffoldContentPadding = compositionLocalOf<PaddingValues?> { null }

/**
 * Resolves the effective content padding for a tab, combining the host scaffold's
 * bottom padding (nav bar height) with an optional extra bottom spacer.
 */
@Composable
fun resolveTabContentPadding(
    extraBottom: Dp = 16.dp,
): PaddingValues {
    val hostPadding = LocalHostScaffoldContentPadding.current
    val layoutDirection = LocalLayoutDirection.current
    val hostBottom = hostPadding?.calculateBottomPadding() ?: 0.dp
    val hostStart = hostPadding?.calculateStartPadding(layoutDirection) ?: 0.dp
    val hostEnd = hostPadding?.calculateEndPadding(layoutDirection) ?: 0.dp
    return PaddingValues(
        start = hostStart,
        end = hostEnd,
        bottom = hostBottom + extraBottom,
    )
}
