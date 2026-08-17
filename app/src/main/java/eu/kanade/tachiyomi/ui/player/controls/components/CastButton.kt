package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * A Compose wrapper around the Cast MediaRouteButton.
 * Shows the cast icon when cast devices are available.
 */
@Composable
fun CastButton(
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            MediaRouteButton(context).apply {
                CastButtonFactory.setUpMediaRouteButton(context, this)
            }
        },
        modifier = modifier.size(48.dp),
    )
}
