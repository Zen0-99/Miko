package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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

private val GlassShape = RoundedCornerShape(20.dp)

/**
 * Floating glassmorphism navigation bar using Haze for real background blur.
 *
 * Uses the Compose Haze library to blur only the background behind the nav bar,
 * keeping icons and text sharp. The caller must wrap the screen content with
 * [Modifier.hazeSource] using the same [hazeState].
 */
@Composable
fun FloatingGlassNavigationBar(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 24.dp,
    horizontalPadding: Dp = 12.dp,
    bottomPadding: Dp = 14.dp,
    navRowHeight: Dp = 72.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val resolvedTint = if (tint != Color.Unspecified) {
        tint
    } else {
        // In dark mode, surface == background (both black in AMOLED), so using
        // surface as tint is invisible. Use a lighter color instead.
        if (isDark) {
            Color.White.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        }
    }
    val shape: Shape = GlassShape
    val baseModifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(windowInsets)
        .padding(horizontal = horizontalPadding, vertical = bottomPadding)

    val glassModifier = baseModifier
        .shadow(elevation = 8.dp, shape = shape)
        .clip(shape)
        .hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = MaterialTheme.colorScheme.background,
                tint = HazeTint(resolvedTint),
                blurRadius = blurRadius,
                noiseFactor = 0.12f,
            ),
        )

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
                .height(navRowHeight)
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
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 24.dp,
    horizontalPadding: Dp = 12.dp,
    bottomPadding: Dp = 14.dp,
    navRowHeight: Dp = 72.dp,
    showDivider: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val resolvedTint = if (tint != Color.Unspecified) {
        tint
    } else {
        if (isDark) {
            Color.White.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        }
    }
    val shape: Shape = GlassShape
    val baseModifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(windowInsets)
        .padding(horizontal = horizontalPadding, vertical = bottomPadding)

    val glassModifier = baseModifier
        .shadow(elevation = 8.dp, shape = shape)
        .clip(shape)
        .hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = MaterialTheme.colorScheme.background,
                tint = HazeTint(resolvedTint),
                blurRadius = blurRadius,
                noiseFactor = 0.12f,
            ),
        )

    androidx.compose.material3.Surface(
        color = Color.Transparent,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = shape,
        modifier = modifier.then(glassModifier),
    ) {
        Column {
            // Mode row — transparent so the Haze background shows through
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
                    .height(navRowHeight)
                    .selectableGroup(),
                content = content,
            )
        }
    }
}

private fun Color.luminance(): Float {
    val r = red * 0.299f
    val g = green * 0.587f
    val b = blue * 0.114f
    return r + g + b
}
