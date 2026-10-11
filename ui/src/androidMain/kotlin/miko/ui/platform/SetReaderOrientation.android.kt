package miko.ui.platform

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
actual fun SetReaderOrientation(flagValue: Int) {
    val view = LocalView.current
    DisposableEffect(flagValue) {
        val activity = view.context as? Activity
            ?: return@DisposableEffect onDispose {}
        activity.requestedOrientation = when (flagValue) {
            0x10 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            0x18 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            0x20 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            0x28 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            0x30 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
