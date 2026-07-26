package eu.kanade.presentation.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import eu.kanade.domain.ui.UiPreferences
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Unified glass tint controller for all glassmorphic surfaces.
 *
 * All glass surfaces (floating nav bar, floating top bar, library update overlay)
 * read the same [UiPreferences.glassTintAlpha] preference so that changing the
 * alpha in Settings updates all glass surfaces together.
 *
 * The stored value is the glass tint alpha (default 0.12). Both dark and light
 * mode use the same alpha so the blur effect is visible in both themes. Dark
 * mode uses a black tint for a deeper glass look; light mode uses a white tint
 * for a frosted glass look. Readability is maintained by the blur itself plus
 * the surface's on-color contrast, not by cranking the tint opacity to 1.0.
 */
object GlassTintController {

    /**
     * Returns the resolved tint color for the current theme, using the unified
     * glass tint alpha preference.
     *
     * - Dark mode: `Color.Black.copy(alpha = alpha)` — darker tint for
     *   better contrast and a deeper glass look.
     * - Light mode: `Color.White.copy(alpha = alpha)` — frosted glass look
     *   that lets the blur show through.
     */
    @Composable
    fun resolvedTint(): Color {
        val uiPreferences = Injekt.get<UiPreferences>()
        val alpha by uiPreferences.glassTintAlpha().collectAsState()
        val isDark = isSystemInDarkTheme()
        return if (isDark) {
            Color.Black.copy(alpha = alpha.coerceIn(0f, 1f))
        } else {
            // Light mode uses a fixed 0.7 alpha for readability while still
            // letting the blur show through.
            Color.White.copy(alpha = 0.7f)
        }
    }
}
