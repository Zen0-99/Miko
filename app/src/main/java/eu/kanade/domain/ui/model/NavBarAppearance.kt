package eu.kanade.domain.ui.model

/**
 * Controls the visual appearance of the bottom navigation bar.
 *
 * - [STANDARD]: Full-width bar attached to the bottom edge (Material3 default).
 * - [FLOATING_GLASS]: Floating pill-shaped bar with glassmorphism (blur + transparency),
 *   inset from screen edges with rounded corners and shadow elevation.
 */
enum class NavBarAppearance(val displayName: String) {
    STANDARD("Standard"),
    FLOATING_GLASS("Floating glass"),
}
