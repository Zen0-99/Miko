package miko.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import platform.UIKit.UIScreen

/**
 * UIScreen.brightness is the system-wide display brightness on iOS —
 * null restores whatever level was active before the reader set it.
 */
@Composable
actual fun SetScreenBrightness(brightness: Float?) {
    val previous = remember { UIScreen.mainScreen.brightness }
    DisposableEffect(brightness) {
        brightness?.let { UIScreen.mainScreen.brightness = it.toDouble() }
        onDispose { UIScreen.mainScreen.brightness = previous }
    }
}
