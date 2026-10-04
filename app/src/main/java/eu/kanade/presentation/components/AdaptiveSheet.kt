package eu.kanade.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.lifecycle.DisposableEffectIgnoringConfiguration
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.ScreenTransition
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import tachiyomi.presentation.core.components.AdaptiveSheet as AdaptiveSheetImpl

@OptIn(InternalVoyagerApi::class)
@Composable
fun NavigatorAdaptiveSheet(
    screen: Screen,
    enableSwipeDismiss: (Navigator) -> Boolean = { true },
    onDismissRequest: () -> Unit,
) {
    Navigator(
        screen = screen,
        content = { sheetNavigator ->
            AdaptiveSheet(
                enableSwipeDismiss = enableSwipeDismiss(sheetNavigator),
                onDismissRequest = onDismissRequest,
            ) {
                ScreenTransition(
                    navigator = sheetNavigator,
                    transition = {
                        fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                            fadeOut(animationSpec = tween(90))
                    },
                )

                BackHandler(
                    enabled = sheetNavigator.size > 1,
                    onBack = sheetNavigator::pop,
                )
            }

            // Make sure screens are disposed no matter what
            if (sheetNavigator.parent?.disposeBehavior?.disposeNestedNavigators == false) {
                DisposableEffectIgnoringConfiguration {
                    onDispose {
                        sheetNavigator.items
                            .asReversed()
                            .forEach(sheetNavigator::dispose)
                    }
                }
            }
        },
    )
}

/**
 * Sheet with adaptive position aligned to bottom on small screen, otherwise aligned to center
 * and will not be able to dismissed with swipe gesture.
 *
 * On phones uses Material3 [ModalBottomSheet]. On tablets uses a centered Dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    enableSwipeDismiss: Boolean = true,
    /** Optional container override (e.g. the entry-accent tinted surface
     *  used by carrier/provider sheets, 17-02). Unspecified keeps the
     *  default `surfaceContainerHigh`. */
    containerColor: Color = Color.Unspecified,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable () -> Unit,
) {
    val isTabletUi = isTabletUi()

    // While any sheet is open, floating overlays (mini-player, update progress)
    // fade out so they don't sit on top of the sheet content.
    DisposableEffect(Unit) {
        LibraryUpdateProgressBus.sheetOpened()
        onDispose { LibraryUpdateProgressBus.sheetClosed() }
    }

    if (isTabletUi) {
        // Tablet: centered dialog-style sheet (unchanged)
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = dialogProperties,
        ) {
            AdaptiveSheetImpl(
                modifier = modifier,
                isTabletUi = true,
                enableSwipeDismiss = enableSwipeDismiss,
                onDismissRequest = onDismissRequest,
                color = if (containerColor != Color.Unspecified) {
                    containerColor
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                content()
            }
        }
    } else {
        // Phone: standard Material3 ModalBottomSheet — replaces the custom
        // AnchoredDraggableState impl that was auto-dismissing.
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = if (containerColor != Color.Unspecified) {
                containerColor
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            shape = MaterialTheme.shapes.extraLarge,
            dragHandle = null,
            contentWindowInsets = contentWindowInsets,
            modifier = modifier,
        ) {
            content()
        }
    }
}

private val dialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = true,
    dismissOnClickOutside = false,
)
