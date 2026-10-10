package eu.kanade.presentation.entries.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
    // The cached color is tagged with the cover it was extracted from — a
    // cover change (e.g. Cinemeta per-season poster swap) invalidates it.
    // The key is the *image* identity, not the whole entry object, so a
    // metadata refresh that rewrites description/genre doesn't flash the accent.
    val coverKey = cover?.let(::coverKeyOf)
    val colorState = remember(entryId) {
        // Strict cover-key match first, then the lenient read: a cover swap
        // means the previous accent still beats the theme gray while the new
        // extraction runs (the effect below re-extracts and fades over).
        val cachedBase = entryId?.let {
            EntryCoverMetadata.getBaseColor(type, it, coverKey)
                ?: EntryCoverMetadata.getBaseColor(type, it)
        }
        mutableStateOf<Color?>(
            cachedBase?.let { Color(adjustForTheme(it, isDark(context))) },
        )
    }

    LaunchedEffect(entryId, coverKey, enabled) {
        if (!enabled || entryId == null || cover == null) {
            colorState.value = null
            return@LaunchedEffect
        }

        // Already cached for this exact cover (e.g. pre-extracted while
        // browsing) — apply synchronously.
        val cachedBase = EntryCoverMetadata.getBaseColor(type, entryId, coverKey)
        if (cachedBase != null) {
            colorState.value = Color(adjustForTheme(cachedBase, isDark(context)))
            return@LaunchedEffect
        }

        // Deliberately keep showing the previous color while the new one is
        // extracted — never blank to the theme color mid-swap.
        val base = extractEntryCoverBaseColor(context, cover) ?: return@LaunchedEffect
        EntryCoverMetadata.setBaseColor(type, entryId, base, coverKey)
        EntryCoverMetadata.savePrefs(type)
        colorState.value = Color(adjustForTheme(base, isDark(context)))
    }

    // "Fadethrough" — interpolate the palette to the new accent (~400ms)
    // instead of snapping. Consumers recompose per frame, so every accented
    // element glides between the two colors with no blank flash.
    return colorState.value?.let {
        animateColorAsState(
            targetValue = it,
            animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
            label = "entry_accent",
        ).value
    }
}

/**
 * Readable content color to place on an accent-colored surface — black on
 * light accents, white on dark ones. Part of the theme transition: it
 * animates with the same 400ms fadethrough spec as the accent itself, so
 * text on pills/buttons glides with the color change instead of snapping.
 */
@Composable
fun rememberOnAccentColor(
    accentColor: Color?,
    fallback: Color = MaterialTheme.colorScheme.onPrimary,
): Color {
    val target = if (accentColor != null) {
        if (accentColor.luminance() > 0.5f) Color.Black else Color.White
    } else {
        fallback
    }
    return animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
        label = "on_accent",
    ).value
}

/** Height above/below the page background an accent-tinted surface sits. */
private const val TINTED_SURFACE_STEP = 0.075f

/** Extra darkening applied to a tinted surface in its "consumed" state. */
private const val TINTED_SURFACE_DIMMED_DELTA = 0.035f

/** Saturation kept from the entry accent — hue stays recognizable. */
private const val TINTED_SURFACE_SATURATION = 0.38f

/**
 * Surface color derived from the entry accent: the accent's hue with its
 * saturation damped, seated just off the page background. On dark themes the
 * accent is lighter than the background, so the surface lands a step *above*
 * it — darkened, desaturated accent that is never darker than the page
 * behind it. On light themes the accent is darker than the background, so
 * the surface sits the same step *below*. [dimmed] drops the lightness a
 * notch for already-consumed rows while staying above the background on
 * dark themes.
 */
fun accentTintedSurface(accent: Color, background: Color, dimmed: Boolean): Color {
    val accentHsl = FloatArray(3).also { ColorUtils.colorToHSL(accent.toArgb(), it) }
    val bgHsl = FloatArray(3).also { ColorUtils.colorToHSL(background.toArgb(), it) }
    val direction = if (accentHsl[2] >= bgHsl[2]) 1f else -1f
    val lightness = (bgHsl[2] + direction * TINTED_SURFACE_STEP).coerceIn(0f, 1f)
    val dimmedLightness = if (direction > 0f) {
        (lightness - TINTED_SURFACE_DIMMED_DELTA).coerceAtLeast(bgHsl[2] + 0.012f)
    } else {
        (lightness - TINTED_SURFACE_DIMMED_DELTA).coerceIn(0f, 1f)
    }
    return Color(
        ColorUtils.HSLToColor(
            floatArrayOf(
                accentHsl[0],
                accentHsl[1] * TINTED_SURFACE_SATURATION,
                if (dimmed) dimmedLightness else lightness,
            ),
        ),
    )
}

/**
 * Stable identity for a cover object — the image URL + last-modified stamp.
 * Domain entry objects (Anime/Manga/Novel) change identity on any field
 * update, so keying on `toString()` would invalidate the cache on unrelated
 * metadata refreshes; keying on url+lastModified only re-extracts when the
 * actual image changes.
 */
internal fun coverKeyOf(cover: Any): String {
    // "v2" busts colors cached before perimeter sampling replaced the
    // full-image palette — without it, stale logo-skewed accents persist.
    val base = when (cover) {
        is tachiyomi.domain.entries.shows.model.Anime ->
            "${cover.thumbnailUrl}#${cover.coverLastModified}"
        is tachiyomi.domain.entries.manga.model.Manga ->
            "${cover.thumbnailUrl}#${cover.coverLastModified}"
        is tachiyomi.domain.entries.novel.model.Novel ->
            "${cover.thumbnailUrl}#${cover.coverLastModified}"
        is tachiyomi.domain.entries.shows.model.AnimeCover ->
            "${cover.url}#${cover.lastModified}"
        is tachiyomi.domain.entries.manga.model.MangaCover ->
            "${cover.url}#${cover.lastModified}"
        is tachiyomi.domain.entries.novel.model.NovelCover ->
            "${cover.url}#${cover.lastModified}"
        else -> cover.toString()
    }
    return "v2|$base"
}

/**
 * Synchronous lenient read of the cached accent for [entryId] — whatever cover
 * produced it. Unlike [rememberEntryAccentColor] this never extracts: it is for
 * transient loading surfaces (skeleton, loading preview) that exist only until
 * the real screen takes over. Returns null when no color was ever cached.
 */
@Composable
fun rememberCachedAccentColor(
    entryId: Long?,
    type: EntryCoverMetadata.EntryType,
): Color? {
    val context = LocalContext.current
    return remember(entryId) {
        entryId?.let {
            EntryCoverMetadata.getBaseColor(type, it)
                ?.let { base -> Color(adjustForTheme(base, isDark(context))) }
        }
    }
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
        val coverKey = coverKeyOf(cover)
        if (EntryCoverMetadata.getBaseColor(type, entryId, coverKey) != null) return@LaunchedEffect
        val base = extractEntryCoverBaseColor(context, cover, size = 64) ?: return@LaunchedEffect
        EntryCoverMetadata.setBaseColor(type, entryId, base, coverKey)
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
            // FIT keeps the whole poster (including its colored background)
            // in the palette input — FILL would center-crop portrait art to a
            // square and skew the dominant swatch toward the subject, not the
            // cover's actual dominant color.
            .size(size, size)
            .scale(Scale.FIT)
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
                // Poster art often carries a huge baked-in title logo (e.g. the
                // orange "NARUTO" banner spanning the bottom of TMDB season
                // posters). A full-image palette lets that logo win the vibrant
                // or even dominant swatch, so the ambient cover color is read
                // from the perimeter instead: the top strip plus the left/right
                // edges of the top ~72%, which logos and subjects rarely reach.
                val ringBitmap = edgeRingBitmap(safeBitmap)
                val palette = androidx.palette.graphics.Palette
                    .from(ringBitmap ?: safeBitmap)
                    .generate()
                val rawColor = palette?.dominantSwatch?.rgb
                    ?: androidx.palette.graphics.Palette.from(safeBitmap)
                        .generate()
                        .getBestColor()
                if (rawColor != null) {
                    injectSaturation(rawColor)
                } else {
                    palette?.vibrantSwatch?.rgb?.let {
                        injectSaturation(it)
                    }
                }
            } finally {
                if (isHardware) safeBitmap.recycle()
            }
        }
    }
}

/**
 * Builds a compact bitmap containing only the poster's perimeter: the top
 * ~14% strip plus the left/right ~16% columns within the top ~72% of the
 * image. Title logos and subjects concentrate in the center and bottom band
 * of cover art, so the perimeter is the cleanest estimate of the cover's
 * ambient color. Returns null for degenerate (tiny) bitmaps.
 */
private fun edgeRingBitmap(bitmap: Bitmap): Bitmap? {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 16 || h < 16) return null
    val side = (w * 0.16f).toInt().coerceAtLeast(1)
    val topRows = (h * 0.14f).toInt().coerceAtLeast(1)
    val midRows = ((h * 0.72f).toInt() - topRows).coerceAtLeast(1)
    val ring = IntArray(w * topRows + midRows * side * 2)
    var off = 0
    bitmap.getPixels(ring, off, w, 0, 0, w, topRows)
    off += w * topRows
    bitmap.getPixels(ring, off, side, 0, topRows, side, midRows)
    off += midRows * side
    bitmap.getPixels(ring, off, side, w - side, topRows, side, midRows)
    return Bitmap.createBitmap(ring, w, ring.size / w, Bitmap.Config.ARGB_8888)
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

/**
 * Minimum accent saturation at display time. Edge-sampled covers often produce
 * muted slate/sage hues; without a floor the accent reads as gray on surfaces
 * and pills. Applied in [adjustForTheme] so cached base colors get the floor
 * too — no cache invalidation needed.
 */
private const val MIN_ACCENT_SATURATION = 0.45f

/**
 * Maximum accent saturation at display time. Very vivid covers (e.g. bright
 * orange key art) produce accents that overwhelm tinted surfaces and pills —
 * cap them to a strong-but-tame saturation.
 */
private const val MAX_ACCENT_SATURATION = 0.65f

fun adjustForTheme(color: Int, isDark: Boolean): Int {
    val luminance = ColorUtils.calculateLuminance(color).toFloat()
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    // Lightness moves via HSL — the old ARGB white/black blend raised luminance
    // but bled saturation out of the accent, producing the grayish tints.
    when {
        !isDark && luminance > 0.4f -> {
            hsl[2] = (hsl[2] * (1f - luminance * 0.5f)).coerceIn(0f, 1f)
        }
        isDark && luminance <= 0.6f -> {
            hsl[2] = (hsl[2] + (1f - hsl[2]) * (1f - luminance) * 0.33f).coerceIn(0f, 1f)
        }
    }
    hsl[1] = hsl[1].coerceIn(MIN_ACCENT_SATURATION, MAX_ACCENT_SATURATION)
    return ColorUtils.HSLToColor(hsl)
}
