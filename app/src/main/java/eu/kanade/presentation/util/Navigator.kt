package eu.kanade.presentation.util

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.ScreenModelStore
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransitionContent
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavStyle
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * For invoking back press to the parent activity
 */
val LocalBackPress: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

/** Shared-element transition plumbing — provided by [ScreenTransition], null
 *  outside the navigator's animated content (previews, tests). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    staticCompositionLocalOf { null }
val LocalNavAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> =
    staticCompositionLocalOf { null }

/**
 * Alpha for screen chrome that isn't a shared element during the nav
 * transition — the top bar's pill/icons, the hero's dot pager. Without an
 * explicit fade those elements sit under the overlay while shared content
 * flies over them, reading as a blink-out + reappear at the end. 220ms
 * runs ahead of the shared-axis slide so the fade-out finishes before the
 * shared element passes over, and the fade-in lands early on return.
 * Returns 1f off-navigator (previews, tests) so nothing changes there.
 */
@Composable
fun navTransitionFadeAlpha(): Float {
    val transition = LocalNavAnimatedVisibilityScope.current?.transition ?: return 1f
    val progress by transition.animateFloat(
        transitionSpec = { tween(300, easing = LinearEasing) },
        label = "nav_chrome_fade",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    // progress runs 0→1 for the incoming screen and 1→0 for the
    // outgoing one. The incoming chrome ramps in over the first ~40%
    // so unpaired icons (download, filter, title) are at full alpha
    // when the flying element lands — the old 65%+ ramp made them
    // "appear only at the end". The outgoing side's late fade is
    // invisible: the shared artwork overlay covers the top region for
    // the whole transit, and the screen's own fade-out ends it early.
    return (progress / 0.4f).coerceIn(0f, 1f)
}

/**
 * Raw 0→1 transition progress — 1 for the settled/outgoing side, rising
 * to 1 for the incoming. Unlike [navTransitionFadeAlpha], which holds
 * 1f for most of the transit so chrome stays readable, this tracks the
 * whole duration: elements that need their own timing (the home glass
 * bar dissolving toward its overflow corner early in the push) read
 * this so they start the moment the nav begins.
 */
@Composable
fun navTransitionProgress(): Float {
    val transition = LocalNavAnimatedVisibilityScope.current?.transition ?: return 1f
    val progress by transition.animateFloat(
        transitionSpec = { tween(300, easing = LinearEasing) },
        label = "nav_transition_progress",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    return progress
}

private val uiPreferences: UiPreferences = Injekt.get()

interface Tab : cafe.adriel.voyager.navigator.tab.Tab {
    suspend fun onReselect(navigator: Navigator) {}

    @Composable
    fun currentNavigationStyle(): NavStyle = uiPreferences.navStyle().collectAsState().value
}

abstract class Screen : Screen {

    override val key: ScreenKey = uniqueScreenKey
}

/**
 * A variant of ScreenModel.coroutineScope except with the IO dispatcher instead of the
 * main dispatcher.
 */
val ScreenModel.ioCoroutineScope: CoroutineScope
    get() = ScreenModelStore.getOrPutDependency(
        screenModel = this,
        name = "ScreenModelIoCoroutineScope",
        factory = { key -> CoroutineScope(Dispatchers.IO + SupervisorJob()) + CoroutineName(key) },
        onDispose = { scope -> scope.cancel() },
    )

interface AssistContentScreen {
    fun onProvideAssistUrl(): String?
}

/**
 * Screen classes whose heroes share elements with the home hub
 * (MangaScreen/NovelScreen/ShowsScreen in tachiyomi.ui.entries). Pushes
 * and pops involving them run the same material shared-axis X as every
 * other screen — the non-shared content (home's catalog vs the detail's
 * description/cards) shifts sideways and crossfades while the shared
 * elements ride the transition overlay and morph between endpoints.
 */
private val HERO_SHARED_SCREEN_NAMES = setOf("MangaScreen", "NovelScreen", "ShowsScreen")
private const val NAV_TRANSITION_MS = 300

@Composable
fun DefaultNavigatorScreenTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val slideDistance = rememberSlideDistance()
    ScreenTransition(
        navigator = navigator,
        transition = {
            if (navigator.lastEvent == StackEvent.Replace &&
                initialState::class == targetState::class
            ) {
                // Same-class replace — the detail screen's
                // extension→canonical redirect swaps itself for the
                // canonical instance. Animating it would run a SECOND
                // transition mid-flight and yank the shared elements'
                // partners; an instant swap keeps the in-flight morph
                // anchored (the new instance re-registers the same keys).
                EnterTransition.None togetherWith ExitTransition.None
            } else if (
                initialState::class.simpleName in HERO_SHARED_SCREEN_NAMES ||
                targetState::class.simpleName in HERO_SHARED_SCREEN_NAMES
            ) {
                // Same shared-axis X as every other nav: the bottom
                // content (catalog rows vs description/cards) slides and
                // crossfades while the hero artwork, title, and bar icons
                // morph through the shared-element overlay on their own
                // paths. The push runs slightly longer than the pop —
                // the detail needs a beat to settle, returning home
                // should snap back promptly.
                materialSharedAxisX(
                    forward = navigator.lastEvent != StackEvent.Pop,
                    slideDistance = slideDistance,
                    durationMillis = if (navigator.lastEvent == StackEvent.Pop) {
                        NAV_TRANSITION_MS
                    } else {
                        NAV_TRANSITION_MS * 6 / 5
                    },
                )
            } else {
                materialSharedAxisX(
                    forward = navigator.lastEvent != StackEvent.Pop,
                    slideDistance = slideDistance,
                )
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScreenTransition(
    navigator: Navigator,
    transition: AnimatedContentTransitionScope<Screen>.() -> ContentTransform,
    modifier: Modifier = Modifier,
    content: ScreenTransitionContent = { it.Content() },
) {
    // SharedTransitionLayout lets screens fly keyed elements (hero artwork)
    // across the push/pop transition while the rest of the content runs the
    // shared-axis slide — the home hero morphs into the detail hero.
    SharedTransitionLayout(modifier = modifier) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
        ) {
            // Opaque canvas behind the screen content: the hero fade dips
            // total alpha below 1 mid-transit, and without a backing layer
            // the window's black decor shows through as a dark-then-bright
            // pulse. Fading over the app's own background keeps luminance
            // constant.
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
            )
            AnimatedContent(
                targetState = navigator.lastItem,
                transitionSpec = transition,
                label = "transition",
            ) { screen ->
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalNavAnimatedVisibilityScope provides this,
                ) {
                    navigator.saveableState("transition", screen) {
                        content(screen)
                    }
                }
            }
        }
    }
}
