package miko.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

/**
 * AVPlayerViewController-backed player — the controller is remembered so
 * [url] changes swap the AVPlayer underneath the same view.
 */
@Composable
actual fun VideoPlayer(url: String, modifier: Modifier) {
    val controller = remember { AVPlayerViewController() }

    LaunchedEffect(url) {
        controller.player = NSURL.URLWithString(url)?.let { AVPlayer(it) }
        controller.player?.play()
    }

    DisposableEffect(controller) {
        onDispose {
            controller.player?.pause()
            controller.player = null
        }
    }

    UIKitView(
        factory = { controller.view },
        modifier = modifier,
    )
}
