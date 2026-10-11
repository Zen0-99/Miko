package miko.ui.platform

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
actual fun SetScreenBrightness(brightness: Float?) {
    val view = LocalView.current
    DisposableEffect(brightness) {
        val window = (view.context as? Activity)?.window
            ?: return@DisposableEffect onDispose {}
        val attrs = window.attributes
        attrs.screenBrightness = brightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = attrs
        onDispose {
            val reset = window.attributes
            reset.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = reset
        }
    }
}
