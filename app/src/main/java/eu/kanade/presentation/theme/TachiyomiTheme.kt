package eu.kanade.presentation.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.presentation.theme.colorscheme.BaseColorScheme
import eu.kanade.presentation.theme.colorscheme.CloudflareColorScheme
import eu.kanade.presentation.theme.colorscheme.CottoncandyColorScheme
import eu.kanade.presentation.theme.colorscheme.DokiColorScheme
import eu.kanade.presentation.theme.colorscheme.DoomColorScheme
import eu.kanade.presentation.theme.colorscheme.GreenAppleColorScheme
import eu.kanade.presentation.theme.colorscheme.LavenderColorScheme
import eu.kanade.presentation.theme.colorscheme.LimeColorScheme
import eu.kanade.presentation.theme.colorscheme.MatrixColorScheme
import eu.kanade.presentation.theme.colorscheme.MidnightDuskColorScheme
import eu.kanade.presentation.theme.colorscheme.MochaColorScheme
import eu.kanade.presentation.theme.colorscheme.MonetColorScheme
import eu.kanade.presentation.theme.colorscheme.NordColorScheme
import eu.kanade.presentation.theme.colorscheme.SapphireColorScheme
import eu.kanade.presentation.theme.colorscheme.StrawberryColorScheme
import eu.kanade.presentation.theme.colorscheme.TachiyomiColorScheme
import eu.kanade.presentation.theme.colorscheme.TakoColorScheme
import eu.kanade.presentation.theme.colorscheme.TealTurqoiseColorScheme
import eu.kanade.presentation.theme.colorscheme.TidalWaveColorScheme
import eu.kanade.presentation.theme.colorscheme.YinYangColorScheme
import eu.kanade.presentation.theme.colorscheme.YotsubaColorScheme
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TachiyomiTheme(
    appTheme: AppTheme? = null,
    amoled: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val isDark = isSystemInDarkTheme()
    val resolvedTheme = appTheme
        ?: if (isDark) uiPreferences.darkTheme().get() else uiPreferences.lightTheme().get()
    BaseTachiyomiTheme(
        appTheme = resolvedTheme,
        isAmoled = amoled ?: uiPreferences.themeDarkAmoled().get(),
        isDark = isDark,
        content = content,
    )
}

/**
 * Mode-aware theme: resolves the light/dark theme and amoled setting for the given
 * [ContentMode] from [UiPreferences.lightThemeFor] / [UiPreferences.darkThemeFor].
 *
 * Theme preferences are read reactively via [collectAsState] so that changing a theme
 * in Settings triggers a smooth animated transition instead of an Activity recreate.
 * Colors animate via a single synchronized [Animatable] progress value that drives
 * [lerp] across every color slot — all colors reach their target simultaneously.
 */
@Composable
fun TachiyomiTheme(
    contentMode: ContentMode,
    content: @Composable () -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val isDark = isSystemInDarkTheme()
    // Read theme preferences reactively so changes in Settings animate smoothly
    // without requiring ActivityCompat.recreate().
    val lightTheme by uiPreferences.lightThemeFor(contentMode).collectAsState()
    val darkTheme by uiPreferences.darkThemeFor(contentMode).collectAsState()
    val amoled by uiPreferences.amoledFor(contentMode).collectAsState()
    val resolvedTheme = if (isDark) darkTheme else lightTheme
    BaseTachiyomiTheme(
        appTheme = resolvedTheme,
        isAmoled = amoled,
        isDark = isDark,
        content = content,
    )
}

@Composable
fun TachiyomiTheme(
    appTheme: AppTheme,
    amoled: Boolean,
    isDark: Boolean,
    animate: Boolean = true,
    content: @Composable () -> Unit,
) {
    BaseTachiyomiTheme(
        appTheme = appTheme,
        isAmoled = amoled,
        isDark = isDark,
        animate = animate,
        content = content,
    )
}

@Composable
fun TachiyomiPreviewTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) = BaseTachiyomiTheme(
    appTheme = appTheme,
    isAmoled = isAmoled,
    isDark = isSystemInDarkTheme(),
    content = content,
)

@Composable
private fun BaseTachiyomiTheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
    isDark: Boolean,
    animate: Boolean = true,
    content: @Composable () -> Unit,
) {
    val targetScheme = getThemeColorScheme(appTheme, isAmoled, isDark)
    val colorScheme = if (animate) animatedColorScheme(targetScheme) else targetScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

private const val THEME_ANIMATION_DURATION_MS = 400

/**
 * Synchronized theme color transition using a single [Animatable] progress value.
 *
 * Inspired by Wakely's Reanimated SharedValue pattern: instead of animating each
 * color slot independently (28+ separate [animateColorAsState] instances), a single
 * progress float (0→1) drives [lerp] across every color in the [ColorScheme].
 *
 * The animation fires whenever the [target] ColorScheme changes — detected by
 * comparing the primary color value, which is unique per theme and changes on
 * any theme/mode/amoled/contentMode switch.
 */
@Composable
private fun animatedColorScheme(target: ColorScheme): ColorScheme {
    val fromScheme = remember { mutableStateOf(target) }
    val toScheme = remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    // Use the primary color value as the change-detection key. This is unique
    // per theme+mode+amoled combination and changes on any theme switch.
    val targetKey = target.primary.value to target.background.value
    val lastKey = remember { mutableStateOf(targetKey) }

    LaunchedEffect(targetKey) {
        if (lastKey.value != targetKey) {
            // Capture the current interpolated state as the new "from" so that
            // changing the target mid-transition doesn't jump to the old start.
            val currentProgress = progress.value
            fromScheme.value = lerpScheme(fromScheme.value, toScheme.value, currentProgress)
            toScheme.value = target
            lastKey.value = targetKey
            progress.snapTo(0f)
            progress.animateTo(1f, tween(THEME_ANIMATION_DURATION_MS))
        }
    }

    val p = progress.value
    return lerpScheme(fromScheme.value, toScheme.value, p)
}

/**
 * Linearly interpolate every color slot between [from] and [to] by [progress].
 * Uses [Color.lerp] (RGB space) for each color — same as Compose's built-in lerp.
 */
private fun lerpScheme(from: ColorScheme, to: ColorScheme, progress: Float): ColorScheme {
    if (progress >= 1f) return to
    if (progress <= 0f) return from
    return to.copy(
        primary = lerp(from.primary, to.primary, progress),
        onPrimary = lerp(from.onPrimary, to.onPrimary, progress),
        primaryContainer = lerp(from.primaryContainer, to.primaryContainer, progress),
        onPrimaryContainer = lerp(from.onPrimaryContainer, to.onPrimaryContainer, progress),
        inversePrimary = lerp(from.inversePrimary, to.inversePrimary, progress),
        secondary = lerp(from.secondary, to.secondary, progress),
        onSecondary = lerp(from.onSecondary, to.onSecondary, progress),
        secondaryContainer = lerp(from.secondaryContainer, to.secondaryContainer, progress),
        onSecondaryContainer = lerp(from.onSecondaryContainer, to.onSecondaryContainer, progress),
        tertiary = lerp(from.tertiary, to.tertiary, progress),
        onTertiary = lerp(from.onTertiary, to.onTertiary, progress),
        tertiaryContainer = lerp(from.tertiaryContainer, to.tertiaryContainer, progress),
        onTertiaryContainer = lerp(from.onTertiaryContainer, to.onTertiaryContainer, progress),
        background = lerp(from.background, to.background, progress),
        onBackground = lerp(from.onBackground, to.onBackground, progress),
        surface = lerp(from.surface, to.surface, progress),
        onSurface = lerp(from.onSurface, to.onSurface, progress),
        surfaceVariant = lerp(from.surfaceVariant, to.surfaceVariant, progress),
        onSurfaceVariant = lerp(from.onSurfaceVariant, to.onSurfaceVariant, progress),
        surfaceTint = lerp(from.surfaceTint, to.surfaceTint, progress),
        inverseSurface = lerp(from.inverseSurface, to.inverseSurface, progress),
        inverseOnSurface = lerp(from.inverseOnSurface, to.inverseOnSurface, progress),
        error = lerp(from.error, to.error, progress),
        onError = lerp(from.onError, to.onError, progress),
        errorContainer = lerp(from.errorContainer, to.errorContainer, progress),
        onErrorContainer = lerp(from.onErrorContainer, to.onErrorContainer, progress),
        outline = lerp(from.outline, to.outline, progress),
        outlineVariant = lerp(from.outlineVariant, to.outlineVariant, progress),
        scrim = lerp(from.scrim, to.scrim, progress),
    )
}

@Composable
@ReadOnlyComposable
private fun getThemeColorScheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
    isDark: Boolean,
): ColorScheme {
    val colorScheme = if (appTheme == AppTheme.MONET) {
        MonetColorScheme(LocalContext.current)
    } else {
        colorSchemes.getOrDefault(appTheme, TachiyomiColorScheme)
    }
    return colorScheme.getColorScheme(
        isDark,
        isAmoled,
    )
}

private const val RIPPLE_DRAGGED_ALPHA = .1f
private const val RIPPLE_FOCUSED_ALPHA = .1f
private const val RIPPLE_HOVERED_ALPHA = .1f
private const val RIPPLE_PRESSED_ALPHA = .1f

val playerRippleConfiguration
    @Composable get() = RippleConfiguration(
        color = if (isSystemInDarkTheme()) Color.White else Color.Black,
        rippleAlpha = RippleAlpha(
            draggedAlpha = RIPPLE_DRAGGED_ALPHA,
            focusedAlpha = RIPPLE_FOCUSED_ALPHA,
            hoveredAlpha = RIPPLE_HOVERED_ALPHA,
            pressedAlpha = RIPPLE_PRESSED_ALPHA,
        ),
    )

private val colorSchemes: Map<AppTheme, BaseColorScheme> = mapOf(
    AppTheme.DEFAULT to TachiyomiColorScheme,
    AppTheme.CLOUDFLARE to CloudflareColorScheme,
    AppTheme.COTTONCANDY to CottoncandyColorScheme,
    AppTheme.DOOM to DoomColorScheme,
    AppTheme.GREEN_APPLE to GreenAppleColorScheme,
    AppTheme.LAVENDER to LavenderColorScheme,
    AppTheme.LIME to LimeColorScheme,
    AppTheme.MATRIX to MatrixColorScheme,
    AppTheme.MIDNIGHT_DUSK to MidnightDuskColorScheme,
    AppTheme.MOCHA to MochaColorScheme,
    AppTheme.SAPPHIRE to SapphireColorScheme,
    AppTheme.NORD to NordColorScheme,
    AppTheme.STRAWBERRY_DAIQUIRI to StrawberryColorScheme,
    AppTheme.TAKO to TakoColorScheme,
    AppTheme.TEALTURQUOISE to TealTurqoiseColorScheme,
    AppTheme.TIDAL_WAVE to TidalWaveColorScheme,
    AppTheme.YINYANG to YinYangColorScheme,
    AppTheme.YOTSUBA to YotsubaColorScheme,
    AppTheme.DOKI to DokiColorScheme,
)
