package miko.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.darwin.dispatch_get_main_queue

/**
 * AVPlayerViewController-backed player — the controller is remembered so
 * [url] changes swap the AVPlayer underneath the same view.
 *
 * [initialPositionMs] becomes a pending seek applied once the player item is
 * attached — AVPlayer ignores seeks issued before the asset loads. The same
 * periodic observer reports progress for `lastSecondSeen` persistence.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    initialPositionMs: Long,
    onPosition: ((positionMs: Long, durationMs: Long) -> Unit)?,
) {
    val controller = remember { AVPlayerViewController() }
    val pendingSeekSeconds = remember { arrayOf(Double.NaN) }
    val timeObserver = remember { arrayOf<Any?>(null) }
    // Item ref — duration/status read off it (AVPlayer.currentItem isn't
    // reachable through this K/N platform def, so keep our own handle).
    val playerItem = remember { arrayOf<AVPlayerItem?>(null) }

    LaunchedEffect(url) {
        pendingSeekSeconds[0] = initialPositionMs / 1000.0
        // Detach the observer from the outgoing player before swapping —
        // removeTimeObserver must target the instance that owns it.
        timeObserver[0]?.let { controller.player?.removeTimeObserver(it) }
        playerItem[0] = NSURL.URLWithString(url)?.let { AVPlayerItem.playerItemWithURL(it) }
        controller.player = playerItem[0]?.let { AVPlayer(playerItem = it) }
        timeObserver[0] = controller.player?.addPeriodicTimeObserverForInterval(
            interval = CMTimeMake(1L, 2),
            queue = dispatch_get_main_queue(),
        ) { time ->
            val player = controller.player ?: return@addPeriodicTimeObserverForInterval
            val item = playerItem[0]
            val pending = pendingSeekSeconds[0]
            if (
                !pending.isNaN() &&
                item != null &&
                item.status == AVPlayerItemStatusReadyToPlay
            ) {
                player.seekToTime(CMTimeMakeWithSeconds(pending, 600))
                pendingSeekSeconds[0] = Double.NaN
            }
            val seconds = CMTimeGetSeconds(time)
            val duration = item?.duration?.let { CMTimeGetSeconds(it) } ?: 0.0
            if (!seconds.isNaN() && !duration.isNaN() && duration > 0) {
                onPosition?.invoke((seconds * 1000).toLong(), (duration * 1000).toLong())
            }
        }
        controller.player?.play()
    }

    DisposableEffect(controller) {
        onDispose {
            timeObserver[0]?.let { controller.player?.removeTimeObserver(it) }
            controller.player?.pause()
            controller.player = null
        }
    }

    UIKitView(
        factory = { controller.view },
        modifier = modifier,
    )
}
