package eu.kanade.presentation.entries.components

import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil3.compose.asPainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult

/**
 * Stretch the hosting Compose `Dialog` window under the status and
 * navigation bars. `DialogProperties(decorFitsSystemWindows = false)`
 * alone isn't enough: dialog windows are floating, and on API 30+ the
 * window manager shrinks a floating window's frame by its
 * `fitInsetsTypes` (system bars by default) — so the scrim stopped at
 * the clock/battery strip no matter what the content drew. Clearing the
 * fit types is what lets the frame reach the screen edges; the content
 * still receives the insets, so chrome pads itself clear of the bars.
 *
 * Timing matters: dialog content composes during `show()`'s attach,
 * BEFORE the window's first layout traversal. This must run inside
 * composition (DisposableEffect) — a coroutine (LaunchedEffect) lands
 * after the first laid-out frame and the whole dialog visibly shifts
 * up when the window re-fits.
 */
@Composable
fun EdgeToEdgeDialogWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.attributes = window.attributes.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    fitInsetsTypes = 0
                    fitInsetsSides = 0
                }
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        onDispose { }
    }
}

private class LoadedCover(val painter: Painter, val aspect: Float)

/**
 * A cover image whose rounded frame is always the bitmap's own frame.
 *
 * The art is decoded first and only then swapped in, sized to its real
 * aspect inside the available bounds — so the clip is never applied to
 * a letterboxed box (the old "corners round only once it settles" look)
 * and a pick of a taller/shorter cover morphs the frame (SizeTransform)
 * while the two images crossfade, each rounded at its own size.
 */
@Composable
fun RoundedCoverImage(
    model: Any?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
) {
    val context = LocalContext.current
    // Decode keyed on the model's CONTENT identity — an equal-but-rebuilt
    // ImageRequest (state pushes recreate options) must not restart the
    // decode; a new LoadedCover instance is what makes AnimatedContent
    // re-morph and the artwork blink.
    val modelKey = (model as? ImageRequest)?.data ?: model
    var shown by remember { mutableStateOf<LoadedCover?>(null) }
    LaunchedEffect(modelKey) {
        if (modelKey == null) return@LaunchedEffect
        val request = (model as? ImageRequest)?.newBuilder()?.size(COVER_DECODE_PX)?.build()
            ?: ImageRequest.Builder(context).data(model).size(COVER_DECODE_PX).build()
        val image = (context.imageLoader.execute(request) as? SuccessResult)?.image
            ?: return@LaunchedEffect
        if (image.width <= 0 || image.height <= 0) return@LaunchedEffect
        shown = LoadedCover(image.asPainter(context), image.width.toFloat() / image.height)
    }
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val boundW = maxWidth
        val boundH = maxHeight
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(220)) using
                    SizeTransform(clip = false) { _, _ -> tween(260) }
            },
            contentAlignment = Alignment.Center,
            label = "roundedCover",
        ) { cover ->
            // While the first decode runs, hold the default cover aspect —
            // the art below this composable in the dialog column (the
            // option strip) must not start high then get pushed down as
            // the image materializes.
            val aspect = cover?.aspect ?: DEFAULT_ASPECT
            val w = minOf(boundW, boundH * aspect)
            if (cover == null) {
                Box(Modifier.size(w, w / aspect))
                return@AnimatedContent
            }
            Image(
                painter = cover.painter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .size(w, w / cover.aspect)
                    .clip(RoundedCornerShape(cornerRadius)),
            )
        }
    }
}

private const val COVER_DECODE_PX = 1600
private const val DEFAULT_ASPECT = 2f / 3f
