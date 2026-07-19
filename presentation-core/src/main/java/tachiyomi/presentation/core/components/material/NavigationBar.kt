package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

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
 * Floating glassmorphism navigation bar using Haze for real background blur.
 *
 * Unlike [Modifier.blur] (which blurs the composable's own content), Haze blurs
 * only what is behind the composable, keeping icons and text sharp.
 *
 * The caller must wrap the screen content with [Modifier.hazeSource] using the
 * same [hazeState] so that the blur has a backdrop to sample.
 *
 * @param hazeState Shared haze state linking the source (content) and effect (nav bar).
 * @param tint Tint color applied over the blurred background (default: surface at 65% alpha).
 * @param blurRadius Blur radius for the glass effect (default 24dp).
 * @param cornerRadius Corner radius for the pill shape (default 28dp).
 * @param horizontalPadding Horizontal inset from screen edges (default 12dp).
 * @param bottomPadding Bottom inset from screen edge (default 10dp).
 */
@Composable
fun FloatingGlassNavigationBar(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
    blurRadius: Dp = 24.dp,
    cornerRadius: Dp = 28.dp,
    horizontalPadding: Dp = 12.dp,
    bottomPadding: Dp = 10.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape: Shape = CircleShape
    val baseModifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(windowInsets)
        .padding(horizontal = horizontalPadding, vertical = bottomPadding)

    val glassModifier = if (isDark) {
        baseModifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.White.copy(alpha = 0.12f),
                spotColor = Color.White.copy(alpha = 0.08f),
            )
            .shadow(
                elevation = 3.dp,
                shape = shape,
                ambientColor = Color.White.copy(alpha = 0.18f),
                spotColor = Color.White.copy(alpha = 0.12f),
            )
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.background,
                    tint = HazeTint(tint),
                    blurRadius = blurRadius,
                    noiseFactor = 0.12f,
                ),
            )
            .borderRimLight(shape, isDark)
    } else {
        baseModifier
            .shadow(
                elevation = 8.dp,
                shape = shape,
            )
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.background,
                    tint = HazeTint(tint),
                    blurRadius = blurRadius,
                    noiseFactor = 0.12f,
                ),
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.80f),
                            Color.White.copy(alpha = 0.20f),
                        ),
                    ),
                ),
                shape = shape,
            )
    }

    androidx.compose.material3.Surface(
        color = Color.Transparent,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = shape,
        modifier = modifier.then(glassModifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .selectableGroup(),
            content = content,
        )
    }
}

/**
 * Floating glassmorphism navigation bar with an attached mode-selector row on top.
 *
 * The [modeRow] composable is rendered above the nav row inside the same glass
 * container, separated by a thin divider.
 */
@Composable
fun FloatingGlassNavigationBarWithModes(
    hazeState: HazeState,
    modeRow: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
    blurRadius: Dp = 24.dp,
    cornerRadius: Dp = 28.dp,
    horizontalPadding: Dp = 12.dp,
    bottomPadding: Dp = 10.dp,
    showDivider: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape: Shape = CircleShape
    val baseModifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(windowInsets)
        .padding(horizontal = horizontalPadding, vertical = bottomPadding)

    val glassModifier = if (isDark) {
        baseModifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.White.copy(alpha = 0.12f),
                spotColor = Color.White.copy(alpha = 0.08f),
            )
            .shadow(
                elevation = 3.dp,
                shape = shape,
                ambientColor = Color.White.copy(alpha = 0.18f),
                spotColor = Color.White.copy(alpha = 0.12f),
            )
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.background,
                    tint = HazeTint(tint),
                    blurRadius = blurRadius,
                    noiseFactor = 0.12f,
                ),
            )
            .borderRimLight(shape, isDark)
    } else {
        baseModifier
            .shadow(
                elevation = 8.dp,
                shape = shape,
            )
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.background,
                    tint = HazeTint(tint),
                    blurRadius = blurRadius,
                    noiseFactor = 0.12f,
                ),
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.80f),
                            Color.White.copy(alpha = 0.20f),
                        ),
                    ),
                ),
                shape = shape,
            )
    }

    androidx.compose.material3.Surface(
        color = Color.Transparent,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = shape,
        modifier = modifier.then(glassModifier),
    ) {
        Column {
            // Mode row
            modeRow()

            // Divider between mode row and nav row
            if (showDivider) {
                HorizontalDivider(
                    color = contentColor.copy(alpha = 0.1f),
                    thickness = 0.5.dp,
                )
            }

            // Nav row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .selectableGroup(),
                content = content,
            )
        }
    }
}

/**
 * Rim-light border for dark theme — subtle white gradient at the top edge.
 */
private fun Modifier.borderRimLight(shape: Shape, isDark: Boolean): Modifier {
    val stops = arrayOf(
        0.00f to 0.24f,
        0.28f to 0.12f,
        0.62f to 0.00f,
        1.00f to 0.00f,
    )
    val colorStops = stops.map { (stop, alpha) ->
        stop to if (isDark) {
            Color.White.copy(alpha = alpha)
        } else {
            // For light theme, use accent at half alpha — but we handle light separately
            Color.White.copy(alpha = alpha)
        }
    }.toTypedArray()

    return this.border(
        BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(colorStops = colorStops),
        ),
        shape = shape,
    )
}

private fun Color.luminance(): Float {
    val r = red * 0.299f
    val g = green * 0.587f
    val b = blue * 0.114f
    return r + g + b
}
