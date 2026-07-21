package eu.kanade.presentation.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
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
 * Colors animate smoothly via [animateColors] when the mode changes — no Activity recreate
 * or screenshot-wipe needed since Compose recomposes natively.
 */
@Composable
fun TachiyomiTheme(
    contentMode: ContentMode,
    content: @Composable () -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val isDark = isSystemInDarkTheme()
    val resolvedTheme = if (isDark) {
        uiPreferences.darkThemeFor(contentMode).get()
    } else {
        uiPreferences.lightThemeFor(contentMode).get()
    }
    BaseTachiyomiTheme(
        appTheme = resolvedTheme,
        isAmoled = uiPreferences.amoledFor(contentMode).get(),
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
    val colorScheme = if (animate) targetScheme.animateColors() else targetScheme
    // Provide AuroraColors derived from the actual selected color scheme so that
    // AuroraTheme-based components (achievements, etc.) follow the user's theme
    // instead of falling back to the hardcoded AuroraColors.Dark default.
    val auroraColors = AuroraColors.fromColorScheme(colorScheme, isDark, isAmoled)
    CompositionLocalProvider(LocalAuroraColors provides auroraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

private const val THEME_ANIMATION_DURATION_MS = 300

@Composable
private fun ColorScheme.animateColors(): ColorScheme {
    val spec = tween<Color>(durationMillis = THEME_ANIMATION_DURATION_MS)

    @Composable
    fun anim(target: Color) = animateColorAsState(
        targetValue = target,
        animationSpec = spec,
        label = "themeColor",
    ).value

    return copy(
        primary = anim(primary),
        onPrimary = anim(onPrimary),
        primaryContainer = anim(primaryContainer),
        onPrimaryContainer = anim(onPrimaryContainer),
        inversePrimary = anim(inversePrimary),
        secondary = anim(secondary),
        onSecondary = anim(onSecondary),
        secondaryContainer = anim(secondaryContainer),
        onSecondaryContainer = anim(onSecondaryContainer),
        tertiary = anim(tertiary),
        onTertiary = anim(onTertiary),
        tertiaryContainer = anim(tertiaryContainer),
        onTertiaryContainer = anim(onTertiaryContainer),
        background = anim(background),
        onBackground = anim(onBackground),
        surface = anim(surface),
        onSurface = anim(onSurface),
        surfaceVariant = anim(surfaceVariant),
        onSurfaceVariant = anim(onSurfaceVariant),
        surfaceTint = anim(surfaceTint),
        inverseSurface = anim(inverseSurface),
        inverseOnSurface = anim(inverseOnSurface),
        error = anim(error),
        onError = anim(onError),
        errorContainer = anim(errorContainer),
        onErrorContainer = anim(onErrorContainer),
        outline = anim(outline),
        outlineVariant = anim(outlineVariant),
        scrim = anim(scrim),
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
