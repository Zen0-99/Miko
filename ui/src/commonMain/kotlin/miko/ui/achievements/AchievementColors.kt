package miko.ui.achievements

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Fixed semantic colors used by the achievement system that have no
 * direct Material3 ColorScheme slot. Verbatim port of Android's
 * `eu.kanade.presentation.theme.AchievementColors`.
 */
object AchievementColors {
    val Gold = Color(0xFFFFB800)
    val RatingStar = Color(0xFFFACC15)
    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFFBBF24)
}

/**
 * Relative luminance of a [Color] using the BT601 weights.
 */
private fun Color.luminance(): Float =
    red * 0.299f + green * 0.587f + blue * 0.114f

/**
 * Whether the active theme is a dark theme, inferred from the surface color.
 */
@Composable
private fun isDarkTheme(): Boolean =
    MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * Readable color for small achievement stat labels.
 */
@Composable
fun achievementLabelColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (isDarkTheme()) 0.72f else 0.5f,
    )

/**
 * Color for inactive streak indicator bars and day cells.
 */
@Composable
fun achievementInactiveBarColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (isDarkTheme()) 0.3f else 0.16f,
    )
