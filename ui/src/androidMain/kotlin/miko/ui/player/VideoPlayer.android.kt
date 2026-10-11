package miko.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Media3 PlayerView-backed player — same surface Android's episode player
 * drives; [url] swaps swap the media item, release on dispose.
 */
@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    initialPositionMs: Long,
    onPosition: ((positionMs: Long, durationMs: Long) -> Unit)?,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(url) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (initialPositionMs > 0) player.seekTo(initialPositionMs)
        player.playWhenReady = true
    }

    LaunchedEffect(onPosition) {
        if (onPosition == null) return@LaunchedEffect
        while (isActive) {
            val duration = player.duration
            if (duration > 0) onPosition(player.currentPosition, duration)
            delay(5_000)
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
        modifier = modifier,
    )
}
