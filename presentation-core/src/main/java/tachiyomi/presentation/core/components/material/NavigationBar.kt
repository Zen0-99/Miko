package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M3 Navbar with no horizontal spacer
 *
 * @see [androidx.compose.material3.NavigationBar]
 */
@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationBarDefaults.containerColor,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(containerColor),
    tonalElevation: Dp = NavigationBarDefaults.Elevation,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.Surface(
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .height(80.dp)
                .selectableGroup(),
            content = content,
        )
    }
}

/**
 * Floating glassmorphism navigation bar — pill-shaped, inset from edges,
 * with semi-transparent background and blur effect.
 *
 * @param blurRadius Blur radius for the glass effect (default 20dp).
 * @param cornerRadius Corner radius for the pill shape (default 28dp).
 * @param horizontalPadding Horizontal inset from screen edges (default 16dp).
 * @param bottomPadding Bottom inset from screen edge (default 8dp).
 * @param containerAlpha Alpha for the semi-transparent background (default 0.7f).
 */
@Composable
fun FloatingGlassNavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationBarDefaults.containerColor,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(containerColor),
    tonalElevation: Dp = 3.dp,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    blurRadius: Dp = 20.dp,
    cornerRadius: Dp = 28.dp,
    horizontalPadding: Dp = 16.dp,
    bottomPadding: Dp = 8.dp,
    containerAlpha: Float = 0.7f,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.Surface(
        color = containerColor.copy(alpha = containerAlpha),
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(cornerRadius),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = bottomPadding)
            .blur(blurRadius),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .height(72.dp)
                .selectableGroup(),
            content = content,
        )
    }
}
