package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Lime theme (Flat Lime / Lime Time)
 * Ported from Miko's XML color definitions
 *
 * Key colors:
 * Primary #57BD79 (light) / #7CF7A5 (dark)
 * Neutral #E9EFEB (light) / #202125 (dark)
 */
internal object LimeColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF7CF7A5),
        onPrimary = Color(0xFF043314),
        primaryContainer = Color(0xFF202125),
        onPrimaryContainer = Color(0xFF7CF7A5),
        inversePrimary = Color(0xFF09933C),
        secondary = Color(0xFF4AF88A),
        onSecondary = Color(0xFF043314),
        secondaryContainer = Color(0xFF202125),
        onSecondaryContainer = Color(0xFF7CF7A5),
        tertiary = Color(0xFF80CAD3),
        onTertiary = Color(0xFF043314),
        tertiaryContainer = Color(0xFF304E51),
        onTertiaryContainer = Color(0xFF80CAD3),
        background = Color(0xFF202125),
        onBackground = Color(0xFFE3E5E3),
        surface = Color(0xFF202125),
        onSurface = Color(0xFFE3E5E3),
        surfaceVariant = Color(0xFF2A2D30),
        onSurfaceVariant = Color(0xFFC5C6D0),
        surfaceTint = Color(0xFF7CF7A5),
        inverseSurface = Color(0xFFE3E5E3),
        inverseOnSurface = Color(0xFF202125),
        outline = Color(0xFF8E9099),
        surfaceContainerLowest = Color(0xFF1B1B1E),
        surfaceContainerLow = Color(0xFF1E1E21),
        surfaceContainer = Color(0xFF242528),
        surfaceContainerHigh = Color(0xFF2A2D30),
        surfaceContainerHighest = Color(0xFF35383B),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF57BD79),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD9ECE0),
        onPrimaryContainer = Color(0xFF043314),
        inversePrimary = Color(0xFFAEDFC0),
        secondary = Color(0xFF1DA750),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD9ECE0),
        onSecondaryContainer = Color(0xFF043314),
        tertiary = Color(0xFF2E8B8B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF80CAD3),
        onTertiaryContainer = Color(0xFF043314),
        background = Color(0xFFE9EFEB),
        onBackground = Color(0xFF1B1B1E),
        surface = Color(0xFFE9EFEB),
        onSurface = Color(0xFF1B1B1E),
        surfaceVariant = Color(0xFFD9ECE0),
        onSurfaceVariant = Color(0xFF44464E),
        surfaceTint = Color(0xFF57BD79),
        inverseSurface = Color(0xFF303033),
        inverseOnSurface = Color(0xFFF2F0F4),
        outline = Color(0xFF757780),
        surfaceContainerLowest = Color(0xFFD4DDD7),
        surfaceContainerLow = Color(0xFFDFE7E1),
        surfaceContainer = Color(0xFFE9EFEB),
        surfaceContainerHigh = Color(0xFFEFF4F0),
        surfaceContainerHighest = Color(0xFFF5F9F6),
    )
}
