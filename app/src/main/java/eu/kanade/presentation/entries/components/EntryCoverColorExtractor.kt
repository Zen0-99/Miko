package eu.kanade.presentation.entries.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.size.Precision
import coil3.size.Scale
import eu.kanade.tachiyomi.util.EntryCoverMetadata
import eu.kanade.tachiyomi.util.getBestColor
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Remembers the cover-derived accent color for an entry identified by [entryId]
 * and [cover].
 *
 * To avoid the "theme flash" (default theme → cover theme) the initial value is
 * seeded synchronously from [EntryCoverMetadata] when a color was pre-extracted
 * while browsing. If no cached color exists, extraction runs asynchronously and
 * the result is written back to the cache for next time.
 *
 * Generalized from [eu.kanade.presentation.entries.novel.components.rememberNovelAccentColor]
 * so manga and anime can use the same mechanism.
 *
 * @param entryId the novel/manga/anime id, or null if not yet known
 * @param cover the cover data to extract color from (any Coil-compatible data)
 * @param type which [EntryCoverMetadata.EntryType] to cache under
 * @param enabled whether extraction should run
 */
@Composable
fun rememberEntryAccentColor(
    entryId: Long?,
    cover: Any?,
    type: EntryCoverMetadata.EntryType,
    enabled: Boolean = true,
): Color? {
    val context = LocalContext.current
    val colorState = remember(entryId) {
        val cachedBase = entryId?.let { EntryCoverMetadata.getBaseColor(type, it) }
        mutableStateOf<Color?>(
            cachedBase?.let { Color(adjustForTheme(it, isDark(context))) },
        )
    }

    LaunchedEffect(entryId, cover, enabled) {
        if (!enabled || entryId == null || cover == null) {
            colorState.value = null
            return@LaunchedEffect
        }

        // Already cached (e.g. pre-extracted while browsing) — apply synchronously.
        val cachedBase = EntryCoverMetadata.getBaseColor(type, entryId)
        if (cachedBase != null) {
            colorState.value = Color(adjustForTheme(cachedBase, isDark(context)))
            return@LaunchedEffect
        }

        val base = extractEntryCoverBaseColor(context, cover) ?: return@LaunchedEffect
        EntryCoverMetadata.setBaseColor(type, entryId, base)
        EntryCoverMetadata.savePrefs(type)
        colorState.value = Color(adjustForTheme(base, isDark(context)))
    }

    return colorState.value
}

/**
 * Pre-extracts and caches the cover accent color for an entry while its cover is
 * displayed in library/browse lists. This populates [EntryCoverMetadata] so that
 * opening the detail screen later applies the theme color with no flash.
 *
 * Cheap to call: it is a no-op once a color is already cached for [entryId].
 *
 * Generalized from [eu.kanade.presentation.entries.novel.components.PreExtractNovelCoverColor].
 */
@Composable
fun PreExtractEntryCoverColor(
    entryId: Long,
    cover: Any?,
    type: EntryCoverMetadata.EntryType,
) {
    val context = LocalContext.current
    LaunchedEffect(entryId, cover) {
        if (cover == null) return@LaunchedEffect
        if (EntryCoverMetadata.getBaseColor(type, entryId) != null) return@LaunchedEffect
        val base = extractEntryCoverBaseColor(context, cover, size = 64) ?: return@LaunchedEffect
        EntryCoverMetadata.setBaseColor(type, entryId, base)
    }
}

/**
 * Loads the cover for [cover], extracts its palette, and returns the base accent
 * color (after saturation injection, before light/dark theme adjustment). Returns
 * null on any failure.
 */
suspend fun extractEntryCoverBaseColor(
    context: Context,
    cover: Any?,
    size: Int = 128,
): Int? {
    cover ?: return null
    return withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(cover)
            .size(size, size)
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .build()

        val drawable = runCatching {
            context.imageLoader.execute(request).image?.asDrawable(context.resources)
        }.getOrNull() ?: return@withContext null

        val bitmap = drawable.getBitmapOrNull() ?: return@withContext null
        val isHardware = bitmap.config == Bitmap.Config.HARDWARE
        val safeBitmap = if (isHardware) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        } ?: return@withContext null

        withContext(Dispatchers.Default) {
            try {
                val palette = androidx.palette.graphics.Palette.from(safeBitmap).generate()
                val rawColor = palette?.getBestColor()
                if (rawColor != null) {
                    injectSaturation(rawColor)
                } else {
                    palette?.dominantSwatch?.rgb?.let {
                        injectSaturation(it)
                    }
                }
            } finally {
                if (isHardware) safeBitmap.recycle()
            }
        }
    }
}

fun isDark(context: Context): Boolean {
    return (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
}

/**
 * If the extracted color has very low saturation (B&W or near-grayscale cover),
 * boost its saturation directly so the detail view always has a tint of color.
 */
private fun injectSaturation(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    val saturation = hsl[1]

    return if (saturation < 0.15f) {
        hsl[1] = 0.4f
        ColorUtils.HSLToColor(hsl)
    } else if (saturation < 0.3f) {
        hsl[1] = 0.5f
        ColorUtils.HSLToColor(hsl)
    } else {
        color
    }
}

fun adjustForTheme(color: Int, isDark: Boolean): Int {
    val luminance = ColorUtils.calculateLuminance(color).toFloat()
    return when {
        !isDark && luminance > 0.4f -> {
            ColorUtils.blendARGB(color, AndroidColor.BLACK, luminance * 0.5f)
        }
        isDark && luminance <= 0.6f -> {
            ColorUtils.blendARGB(color, AndroidColor.WHITE, (1.0f - luminance) * 0.33f)
        }
        else -> color
    }
}
