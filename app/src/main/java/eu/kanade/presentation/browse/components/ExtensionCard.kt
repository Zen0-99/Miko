package eu.kanade.presentation.browse.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.size.Precision
import coil3.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * A horizontal rectangle card for displaying extensions in browse.
 * The background is a gradient sampled from 3 distinct colors extracted from
 * the extension icon via Palette.
 * Bold title in top-left, language below, version inline with language (dot-separated).
 * Settings cog (or download icon when update available) in bottom-right.
 * When [isUpdating] is true, a progress indicator replaces the cog.
 */
@Composable
fun ExtensionCard(
    title: String,
    lang: String,
    version: String,
    iconDrawable: Drawable? = null,
    iconUrl: String? = null,
    hasUpdate: Boolean = false,
    isUpdating: Boolean = false,
    supportsComments: Boolean = false,
    onClick: () -> Unit,
    onCogClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var gradientColors by remember(title) {
        mutableStateOf<List<Color>?>(null)
    }

    // Extract 3 distinct colors from the icon
    LaunchedEffect(iconDrawable, iconUrl) {
        gradientColors = extractGradientColors(context, iconDrawable, iconUrl)
    }

    // Fallback colors if extraction fails or is loading
    val colors = gradientColors ?: listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surfaceVariant,
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Gradient background from 3 sampled colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = colors,
                        ),
                    ),
            )

            // Subtle dark overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top section: title + comment badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Language - Version (only show dot separator if version is non-empty)
                        val langVersion = if (version.isNotBlank()) "$lang - $version" else lang
                        Text(
                            text = langVersion,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (supportsComments) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Comment,
                            contentDescription = "Supports comments",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Bottom section: cog, download, or progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        IconButton(
                            onClick = onCogClick,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (hasUpdate) Icons.Outlined.Download else Icons.Outlined.Settings,
                                contentDescription = if (hasUpdate) "Update" else stringResource(MR.strings.label_extension_info),
                                tint = if (hasUpdate) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extracts 3 distinct colors from an icon (Drawable or URL) using Palette.
 * Returns a list of 3 Colors suitable for a gradient.
 */
private suspend fun extractGradientColors(
    context: android.content.Context,
    drawable: Drawable?,
    url: String?,
): List<Color> {
    val bitmap = when {
        drawable != null -> drawable.toBitmap(128, 128)
        url != null -> loadBitmapFromUrl(context, url)
        else -> return emptyList()
    } ?: return emptyList()

    return withContext(Dispatchers.Default) {
        val palette = Palette.from(bitmap).generate()
        // Get 3 distinct swatches: dominant, vibrant, muted
        val dominant = palette.dominantSwatch?.rgb
        val vibrant = palette.vibrantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
        val muted = palette.mutedSwatch?.rgb
            ?: palette.lightMutedSwatch?.rgb
            ?: palette.darkMutedSwatch?.rgb

        // Build the 3-color list, filling gaps with dominant or a default
        val colors = mutableListOf<Int>()
        if (dominant != null) colors.add(dominant)
        if (vibrant != null && colors.size < 3) colors.add(vibrant)
        if (muted != null && colors.size < 3) colors.add(muted)

        // Fill remaining slots with dominant or derived colors
        while (colors.size < 3) {
            colors.add(dominant ?: 0xFF444444.toInt())
        }

        // Boost saturation for each color
        colors.map { boostSaturation(Color(it)) }
    }
}

private fun boostSaturation(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
        hsv,
    )
    // Boost saturation to at least 0.5 for vivid gradient
    if (hsv[1] < 0.5f) {
        hsv[1] = (hsv[1] + 0.3f).coerceAtMost(1.0f)
    }
    val argb = android.graphics.Color.HSVToColor(hsv)
    return Color(argb)
}

private suspend fun loadBitmapFromUrl(
    context: android.content.Context,
    url: String,
): Bitmap? {
    return withContext(Dispatchers.IO) {
        val loader = context.imageLoader
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(128, 128)
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .build()
        val drawable = runCatching {
            loader.execute(request).image?.asDrawable(context.resources)
        }.getOrNull()
        drawable?.toBitmap(128, 128)
    }
}
