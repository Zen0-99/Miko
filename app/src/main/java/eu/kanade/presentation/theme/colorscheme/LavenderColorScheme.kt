package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Lavender theme
 * Color scheme by Osyx
 *
 * Key colors:
 * Primary #A177FF
 * Secondary #A177FF
 * Tertiary #5E25E1
 * Neutral #111129
 */
internal object LavenderColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFA177FF),
        onPrimary = Color(0xFF3D0090),
        primaryContainer = Color(0xFFA177FF),
        onPrimaryContainer = Color(0xFFFFFFFF),
        secondary = Color(0xFFA177FF), // Unread badge
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFF423271), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFA177FF), // Navigation bar selected icon
        tertiary = Color(0xFFCDBDFF), // Downloaded badge
        onTertiary = Color(0xFF360096), // Downloaded badge text
        tertiaryContainer = Color(0xFF5512D8),
        onTertiaryContainer = Color(0xFFEFE6FF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF111129),
        onBackground = Color(0xFFE7E0EC),
        surface = Color(0xFF111129),
        onSurface = Color(0xFFE7E0EC),
        surfaceVariant = Color(0xFF3D2F6B), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFCBC3D6),
        outline = Color(0xFF958E9F),
        outlineVariant = Color(0xFF4A4453),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFE7E0EC),
        inverseOnSurface = Color(0xFF322F38),
        inversePrimary = Color(0xFF6D41C8),
        surfaceDim = Color(0xFF111129),
        surfaceBright = Color(0xFF3B3841),
        surfaceContainerLowest = Color(0xFF15132d),
        surfaceContainerLow = Color(0xFF171531),
        surfaceContainer = Color(0xFF1D193B), // Navigation bar background
        surfaceContainerHigh = Color(0xFF241f41),
        surfaceContainerHighest = Color(0xFF282446),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF6D41C8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE9DDFF),
        onPrimaryContainer = Color(0xFF22005D),
        secondary = Color(0xFF635B70), // Unread badge
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFFE9DDFF), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF6D41C8), // Navigation bar selected icon
        tertiary = Color(0xFF7E5260), // Downloaded badge
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFFFFD8E2),
        onTertiaryContainer = Color(0xFF31101D),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFEF7FF),
        onBackground = Color(0xFF1D1B22),
        surface = Color(0xFFFEF7FF),
        onSurface = Color(0xFF1D1B22),
        surfaceVariant = Color(0xFFE7E0EB), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF7A757F),
        outlineVariant = Color(0xFFCAC4CF),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFF322F35),
        inverseOnSurface = Color(0xFFF5EFF7),
        inversePrimary = Color(0xFFCFBCFF),
        surfaceDim = Color(0xFFDED8E0),
        surfaceBright = Color(0xFFFEF7FF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8F1FA),
        surfaceContainer = Color(0xFFF2EBF4), // Navigation bar background
        surfaceContainerHigh = Color(0xFFECE6EE),
        surfaceContainerHighest = Color(0xFFE6E0E9),
    )
}
