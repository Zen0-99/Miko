package tachiyomi.presentation.core.util

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Composition-local that indicates whether non-essential animations should be
 * disabled. When true, screens should:
 * - Use instant transitions instead of animated ones.
 * - Skip skeleton-loader pulse animations.
 * - Disable image crossfades.
 * - Skip activity transitions.
 *
 * This is provided by the app's root composable, which reads the
 * [eu.kanade.domain.ui.UiPreferences.reduceMotion] preference.
 * Access it with `LocalReduceMotion.current`.
 */
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

/**
 * Returns an [AnimationSpec] that is instant when reduce-motion is enabled,
 * or the given [spec] when it's not. Use for transitions and animations that
 * should respect the reduce-motion preference.
 */
@Composable
fun <T> motionAwareSpec(spec: AnimationSpec<T>): AnimationSpec<T> {
    return if (LocalReduceMotion.current) tween(durationMillis = 0) else spec
}

/**
 * Returns a duration in milliseconds that is 0 when reduce-motion is enabled,
 * or the given [durationMs] when it's not.
 */
@Composable
fun motionAwareDuration(durationMs: Int): Int {
    return if (LocalReduceMotion.current) 0 else durationMs
}
