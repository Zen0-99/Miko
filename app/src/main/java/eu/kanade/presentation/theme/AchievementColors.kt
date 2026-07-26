package eu.kanade.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Fixed semantic colors used by the achievement system that have no
 * direct Material3 ColorScheme slot. These are brand colors that stay
 * constant regardless of the active theme or content mode.
 *
 * All theme-aware colors (accent, surface, text, error, etc.) should be
 * read from [androidx.compose.material3.MaterialTheme.colorScheme] —
 * which is already mode-aware via the per-content-mode TachiyomiTheme
 * wrapper in MainActivity.
 */
object AchievementColors {
    val Gold = Color(0xFFFFB800)
    val RatingStar = Color(0xFFFACC15)
    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFFBBF24)
}

/**
 * Relative luminance of a [Color] using the BT601 weights. Used to detect
 * whether the current theme is dark so achievement stat labels and inactive
 * streak bars can adjust their alpha per mode.
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
 * Readable color for small achievement stat labels (Rank, XP Points,
 * Unlocked, Streak, Days, etc). These tiny 8sp labels were previously
 * `onSurfaceVariant.copy(alpha = 0.4f)`, which rendered nearly invisible
 * on dark `surfaceContainerHigh` cards. Using the higher-contrast
 * `onSurface` with a mode-aware alpha keeps them legible in both modes —
 * brighter in dark mode, subtler in light mode.
 */
@Composable
fun achievementLabelColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (isDarkTheme()) 0.72f else 0.5f,
    )

/**
 * Color for inactive streak indicator bars and day cells. In dark mode
 * `outlineVariant` is too dark to see on `surfaceContainerHigh`, so a
 * neutral `onSurface` tint is used instead — brighter in dark mode,
 * subtle in light mode.
 */
@Composable
fun achievementInactiveBarColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (isDarkTheme()) 0.3f else 0.16f,
    )