package miko.ui.platform

import androidx.compose.runtime.Composable

/**
 * Android's reader custom-brightness — writes the platform screen
 * brightness while set; null restores the system brightness (Android's
 * BRIGHTNESS_OVERRIDE_NONE). Negative values don't reach here — the
 * overlay black-out handles those in shared code.
 */
@Composable
expect fun SetScreenBrightness(brightness: Float?)
