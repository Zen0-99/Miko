package miko.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * In-app video surface — Android's episode player equivalent. Media3
 * PlayerView on Android, AVPlayerViewController on iOS; both autoplay the
 * stream and render the platform's native controls. Streams needing custom
 * headers are out of scope until [miko.ui.carrier.VideoEntry] carries them.
 */
@Composable
expect fun VideoPlayer(url: String, modifier: Modifier = Modifier)
