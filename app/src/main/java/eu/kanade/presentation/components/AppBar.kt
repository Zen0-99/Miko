package eu.kanade.presentation.components

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.components.HeroSharedKind
import eu.kanade.presentation.entries.components.heroSharedBoundsModifier
import eu.kanade.presentation.util.navTransitionFadeAlpha
import eu.kanade.tachiyomi.R
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clearFocusOnSoftKeyboardHide
import tachiyomi.presentation.core.util.runOnEnterKeyPressed
import tachiyomi.presentation.core.util.secondaryItemAlpha

const val SEARCH_DEBOUNCE_MILLIS = 250L

@Composable
fun AppBar(
    title: String?,

    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    // Text
    subtitle: String? = null,
    // Up button
    navigateUp: (() -> Unit)? = null,
    navigationIcon: ImageVector? = null,
    // Menu
    actions: @Composable RowScope.() -> Unit = {},
    // Action mode
    actionModeCounter: Int = 0,
    onCancelActionMode: () -> Unit = {},
    actionModeActions: @Composable RowScope.() -> Unit = {},

    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val isActionMode by remember(actionModeCounter) {
        derivedStateOf { actionModeCounter > 0 }
    }

    AppBar(
        modifier = modifier,
        backgroundColor = backgroundColor,
        titleContent = {
            if (isActionMode) {
                AppBarTitle(actionModeCounter.toString())
            } else {
                AppBarTitle(title, subtitle = subtitle)
            }
        },
        navigateUp = navigateUp,
        navigationIcon = navigationIcon,
        actions = {
            if (isActionMode) {
                actionModeActions()
            } else {
                actions()
            }
        },
        isActionMode = isActionMode,
        onCancelActionMode = onCancelActionMode,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun AppBar(
    // Title
    titleContent: @Composable () -> Unit,

    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    // Up button
    navigateUp: (() -> Unit)? = null,
    navigationIcon: ImageVector? = null,
    // Menu
    actions: @Composable RowScope.() -> Unit = {},
    // Action mode
    isActionMode: Boolean = false,
    onCancelActionMode: () -> Unit = {},

    scrollBehavior: TopAppBarScrollBehavior? = null,
    // When true, AppBar skips adding status-bar top padding — the caller is
    // responsible for positioning the bar below the status bar (e.g. the
    // floating glass top bar adds its own top padding before the glass surface).
    skipStatusBarPadding: Boolean = false,
    // Bar height override — entry toolbars pass 60dp so their icon slots
    // land exactly where the home top bar's do (icon center = +30 from
    // the bar top, same as home's 6+24). Everywhere else keeps the M3
    // default 64.
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
) {
    // Use a fixed status-bar height so the app bar height does not change
    // when system bars are hidden/shown (e.g. leaving fullscreen reader).
    val context = LocalContext.current
    val density = LocalDensity.current
    val statusBarHeight = remember {
        val res = context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        if (id > 0) with(density) { res.getDimensionPixelSize(id).toDp() } else 0.dp
    }

    // Resolve the background color so we can apply it to the status bar area too
    val resolvedBg = backgroundColor ?: MaterialTheme.colorScheme.surfaceColorAtElevation(
        elevation = if (isActionMode) 3.dp else 0.dp,
    )

    Column(
        modifier = modifier
            .background(resolvedBg)
            .then(if (skipStatusBarPadding) Modifier else Modifier.padding(top = statusBarHeight)),
    ) {
        // The bar's icons/title aren't shared elements — fade them with the
        // nav transition so they don't blink under the shared-element
        // overlay mid-morph. The overflow icon is shared (NAV_OVERFLOW_
        // SHARED_KEY) and escapes this alpha inside the overlay.
        val navFade = navTransitionFadeAlpha()
        Box {
            TopAppBar(
                modifier = Modifier.graphicsLayer { alpha = navFade },
                expandedHeight = expandedHeight,
                navigationIcon = {
                    if (isActionMode) {
                        IconButton(onClick = onCancelActionMode) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(MR.strings.action_cancel),
                            )
                        }
                    } else {
                        navigateUp?.let {
                            IconButton(
                                onClick = it,
                                // The up arrow pairs with the home bar's
                                // leading icon slot — the element
                                // crossfades through the overlay during
                                // transit (like the overflow icon) instead
                                // of riding the bar's late alpha ramp and
                                // appearing only at the end.
                                modifier = heroSharedBoundsModifier(
                                    NAV_LEADING_ICON_SHARED_KEY,
                                    HeroSharedKind.Text,
                                ),
                            ) {
                                UpIcon(navigationIcon = navigationIcon)
                            }
                        }
                    }
                },
                title = titleContent,
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    // M3 dims action icons to onSurfaceVariant; the home
                    // top bar paints them onSurface — the shared overflow
                    // glyph would otherwise arrive a shade dimmer.
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        }
    }
}

@Composable
fun AppBarTitle(
    title: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    count: Int = 0,
) {
    if (count > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title!!,
                maxLines = 1,
                modifier = Modifier.weight(1f, false),
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
            Pill(
                text = "$count",
                modifier = Modifier.padding(start = 4.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = pillAlpha),
                fontSize = 14.sp,
            )
        }
    } else {
        Column(modifier = modifier) {
            title?.let {
                Text(
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(
                        repeatDelayMillis = 2_000,
                    ),
                )
            }
        }
    }
}

/** Shared-element key for the three-dot overflow icon — constant so the
 *  home top bar's icon pairs with the detail toolbar's during the
 *  push/pop transition. */
internal const val NAV_OVERFLOW_SHARED_KEY = "nav-overflow"

/** Shared-element key for the top bar's leading 48dp icon slot — the home
 *  bar's search/back icon pairs with the detail toolbar's up arrow so it
 *  crossfades in place during the nav transition instead of appearing only
 *  after the screen's fade completes. */
internal const val NAV_LEADING_ICON_SHARED_KEY = "nav-leading-icon"

/** Shared-element key for the detail toolbar's download action — pairs
 *  with an invisible anchor on the home bar and the loading preview so
 *  it glides through transit instead of popping with the bar's alpha. */
internal const val NAV_DOWNLOAD_SHARED_KEY = "nav-download"

/** Shared-element key for the home glass pill. The haze blur cannot
 *  render in the transition overlay, so while transit runs the home
 *  endpoint swaps the haze for an opaque surface fill; the detail
 *  endpoint is an invisible anchor at the toolbar's trailing slot —
 *  the bounds morph + crossfade reads as the pill collapsing into the
 *  overflow corner instead of dying with the screen's early fade-out. */
internal const val NAV_PILL_SHARED_KEY = "nav-pill"

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun AppBarActions(
    actions: ImmutableList<AppBar.AppBarAction>,
    /** Keeps the overflow icon composed even when [actions] momentarily
     *  carries no overflow items — the shared home bar uses this so a
     *  transient empty write during a tab swap can't dispose the icon's
     *  animated-vector painter and replay its enter animation. */
    alwaysShowOverflow: Boolean = false,
) {
    var showMenu by remember { mutableStateOf(false) }

    actions.filterIsInstance<AppBar.Action>().map {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip {
                    Text(it.title)
                }
            },
            state = rememberTooltipState(),
        ) {
            IconButton(
                onClick = it.onClick,
                enabled = it.enabled,
                modifier = it.sharedKey?.let { key ->
                    heroSharedBoundsModifier(key, HeroSharedKind.Text)
                } ?: Modifier,
            ) {
                Icon(
                    imageVector = it.icon,
                    tint = it.iconTint ?: LocalContentColor.current,
                    contentDescription = it.title,
                )
            }
        }
    }

    val overflowActions = actions.filterIsInstance<AppBar.AppBarAction>()
        .filter {
            it is AppBar.OverflowAction ||
                it is AppBar.NestedOverflowAction ||
                it is AppBar.CheckableOverflowAction
        }
    if (overflowActions.isNotEmpty() || alwaysShowOverflow) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip {
                    Text(stringResource(MR.strings.action_menu_overflow_description))
                }
            },
            state = rememberTooltipState(),
        ) {
            val moreImage = AnimatedImageVector.animatedVectorResource(R.drawable.anim_more_enter)
            IconButton(
                onClick = { if (overflowActions.isNotEmpty()) showMenu = !showMenu },
                // The overflow icon exists on both the home top bar and
                // the detail toolbar — one shared element makes it glide
                // between positions on navigation instead of rebuilding.
                // NavIcon (sharedElement) rather than a bounds morph: all
                // endpoints draw the same glyph, so skipping the content
                // crossfade removes the mid-flight alpha dip.
                // requiredSize: home's icon row is 44dp, which squeezed
                // this node to 48×44 there vs 48×48 on the detail bar —
                // sharedElement places target content by the bounds' top,
                // so mismatched endpoint sizes shift the glyph at handoff.
                modifier = Modifier
                    .requiredSize(48.dp)
                    .onGloballyPositioned {
                        android.util.Log.d(
                            "NavIconPos",
                            "overflow pos=${it.positionInRoot()} size=${it.size}",
                        )
                    }
                    .then(
                        heroSharedBoundsModifier(
                            NAV_OVERFLOW_SHARED_KEY,
                            HeroSharedKind.NavIcon,
                        ),
                    ),
            ) {
                Icon(
                    rememberAnimatedVectorPainter(moreImage, showMenu),
                    contentDescription = stringResource(
                        MR.strings.action_menu_overflow_description,
                    ),
                )
            }
        }

        // Track which nested action's submenu is open (replaces parent menu)
        var nestedAction by remember { mutableStateOf<AppBar.NestedOverflowAction?>(null) }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = DpOffset(0.dp, 8.dp),
        ) {
            overflowActions.map { action ->
                when (action) {
                    is AppBar.Action -> {} // Not rendered in overflow menu
                    is AppBar.OverflowAction -> {
                        DropdownMenuItem(
                            onClick = {
                                action.onClick()
                                showMenu = false
                            },
                            text = { Text(action.title) },
                        )
                    }
                    is AppBar.CheckableOverflowAction -> {
                        DropdownMenuItem(
                            onClick = {
                                action.onCheckedChange(!action.checked)
                            },
                            text = { Text(action.title) },
                            trailingIcon = {
                                Checkbox(
                                    checked = action.checked,
                                    onCheckedChange = {
                                        action.onCheckedChange(it)
                                        showMenu = false
                                    },
                                )
                            },
                        )
                    }
                    is AppBar.NestedOverflowAction -> {
                        DropdownMenuItem(
                            text = { Text(action.title) },
                            onClick = {
                                // Close parent menu, open submenu in its place
                                showMenu = false
                                nestedAction = action
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowRight,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }
        }

        // Submenu dropdown — appears in the same position as the parent menu
        DropdownMenu(
            expanded = nestedAction != null,
            onDismissRequest = { nestedAction = null },
            offset = DpOffset(0.dp, 8.dp),
        ) {
            // Back item to return to main menu
            DropdownMenuItem(
                text = { Text("‹ Back") },
                onClick = {
                    nestedAction = null
                    showMenu = true
                },
            )
            nestedAction?.subActions?.forEach { subAction ->
                DropdownMenuItem(
                    onClick = {
                        subAction.onClick()
                        nestedAction = null
                    },
                    text = { Text(subAction.title) },
                )
            }
        }
    }
}

/**
 * @param searchEnabled Set to false if you don't want to show search action.
 * @param searchQuery If null, use normal toolbar.
 * @param placeholderText If null, [MR.strings.action_search_hint] is used.
 */
@Composable
fun SearchToolbar(
    searchQuery: String?,
    onChangeSearchQuery: (String?) -> Unit,
    modifier: Modifier = Modifier,
    titleContent: @Composable () -> Unit = {},
    navigateUp: (() -> Unit)? = null,
    searchEnabled: Boolean = true,
    placeholderText: String? = null,
    onSearch: (String) -> Unit = {},
    onClickCloseSearch: () -> Unit = { onChangeSearchQuery(null) },
    actions: @Composable RowScope.() -> Unit = {},
    backgroundColor: Color? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    skipStatusBarPadding: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focusRequester = remember { FocusRequester() }

    AppBar(
        modifier = modifier,
        backgroundColor = backgroundColor,
        skipStatusBarPadding = skipStatusBarPadding,
        titleContent = {
            if (searchQuery == null) return@AppBar titleContent()

            val keyboardController = LocalSoftwareKeyboardController.current

            // Request focus (and show the soft keyboard) when the search field
            // first appears with an empty query. We use the SAME FocusRequester
            // as the BasicTextField below — chaining a second .focusRequester()
            // via showSoftKeyboard() was creating a conflicting FocusRequester
            // that could prevent IME actions (KeyboardActions.onSearch) from
            // firing on certain devices/keyboards.
            LaunchedEffect(focusRequester) {
                if (searchQuery.isEmpty()) {
                    try {
                        focusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            }

            val searchAndClearFocus: () -> Unit = f@{
                if (searchQuery.isBlank()) return@f
                onSearch(searchQuery)
                keyboardController?.hide()
            }

            BasicTextField(
                value = searchQuery,
                onValueChange = onChangeSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .runOnEnterKeyPressed(action = searchAndClearFocus)
                    .clearFocusOnSoftKeyboardHide(),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Normal,
                    fontSize = 18.sp,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchAndClearFocus() }),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    TextFieldDefaults.DecorationBox(
                        value = searchQuery,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = true,
                        visualTransformation = visualTransformation,
                        interactionSource = interactionSource,
                        placeholder = {
                            Text(
                                modifier = Modifier.secondaryItemAlpha(),
                                text = (
                                    placeholderText ?: stringResource(
                                        MR.strings.action_search_hint,
                                    )
                                    ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                        },
                        container = {},
                    )
                },
            )
        },
        navigateUp = if (searchQuery == null) {
            navigateUp
        } else {
            onClickCloseSearch
        },
        actions = {
            key("search") {
                val onClick = { onChangeSearchQuery("") }

                if (!searchEnabled) {
                    // Don't show search action
                } else if (searchQuery == null) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(MR.strings.action_search))
                            }
                        },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = onClick,
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = stringResource(MR.strings.action_search),
                            )
                        }
                    }
                } else if (searchQuery.isNotEmpty()) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(MR.strings.action_reset))
                            }
                        },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = {
                                onClick()
                                focusRequester.requestFocus()
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(MR.strings.action_reset),
                            )
                        }
                    }
                }
            }

            key("actions") { actions() }
        },
        isActionMode = false,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun UpIcon(
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
) {
    val icon = navigationIcon
        ?: Icons.AutoMirrored.Outlined.ArrowBack
    Icon(
        imageVector = icon,
        contentDescription = stringResource(MR.strings.action_bar_up_description),
        modifier = modifier,
    )
}

sealed interface AppBar {
    sealed interface AppBarAction

    data class Action(
        val title: String,
        val icon: ImageVector,
        val iconTint: Color? = null,
        val onClick: () -> Unit,
        val enabled: Boolean = true,
        /** Shared-element key pairing this action with a counterpart on
         *  the navigating screen's bar (or an invisible anchor) — the icon
         *  then glides through the overlay like the overflow button
         *  instead of popping with the screen's chrome alpha. */
        val sharedKey: String? = null,
    ) : AppBarAction

    data class OverflowAction(
        val title: String,
        val onClick: () -> Unit,
    ) : AppBarAction

    /**
     * An overflow action with a checkbox (toggle) state.
     * Used for settings like "Downloaded only" and "Incognito mode".
     */
    data class CheckableOverflowAction(
        val title: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
    ) : AppBarAction

    /**
     * An overflow action that expands into a nested submenu (with ">" icon)
     * instead of performing an action directly.
     */
    data class NestedOverflowAction(
        val title: String,
        val subActions: List<OverflowAction>,
    ) : AppBarAction
}
