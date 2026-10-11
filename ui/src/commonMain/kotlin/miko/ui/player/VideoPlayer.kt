package miko.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * In-app video surface — Android's episode player equivalent. Media3
 * PlayerView on Android, AVPlayerViewController on iOS; both autoplay the
 * stream and render the platform's native controls. Streams needing custom
 * headers are out of scope until [miko.ui.carrier.VideoEntry] carries them.
 *
 * [initialPositionMs] seeds Android's resume — seek before play when the
 * episode has a saved `lastSecondSeen`. [onPosition] reports playback
 * progress every few seconds so the model can persist it.
 */
@Composable
expect fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    initialPositionMs: Long = 0L,
    onPosition: ((positionMs: Long, durationMs: Long) -> Unit)? = null,
)
