package miko.ui.platform

import androidx.compose.runtime.Composable
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/** Top-most presented controller — the sheet's presenter on iOS. */
private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}

@Composable
actual fun rememberShareHandler(): (String) -> Unit = { text ->
    val activity = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null,
    )
    topViewController()?.presentViewController(
        viewControllerToPresent = activity,
        animated = true,
        completion = null,
    )
}
