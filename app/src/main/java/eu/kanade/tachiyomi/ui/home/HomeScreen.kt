package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import tachiyomi.presentation.core.util.runOnEnterKeyPressed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import kotlinx.collections.immutable.toPersistentList
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.LocalSharedTopBar
import eu.kanade.presentation.components.SharedTopBarState
import eu.kanade.presentation.home.ModePill
import eu.kanade.presentation.library.LibraryUpdateProgressOverlay
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.download.DownloadsTab
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.components.material.FloatingGlassNavigationBar
import tachiyomi.presentation.core.components.material.FloatingGlassNavigationBarWithModes
import tachiyomi.presentation.core.components.material.NavigationBar
import tachiyomi.presentation.core.components.material.NavigationRail
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

object HomeScreen : Screen() {

    private val librarySearchEvent = Channel<String>()
    private val openTabEvent = Channel<Tab>()
    private val showBottomNavEvent = Channel<Boolean>()

    private const val TAB_FADE_DURATION = 200
    private const val TAB_NAVIGATOR_KEY = "HomeTabs"

    @Composable
    private fun tabFadeDuration(): Int =
        if (tachiyomi.presentation.core.util.LocalReduceMotion.current) 0 else TAB_FADE_DURATION

    private val uiPreferences: UiPreferences by injectLazy()
    private val defaultTab = uiPreferences.startScreen().get().tab

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navStyle by uiPreferences.navStyle().collectAsStateWithLifecycle()
        val contentMode by uiPreferences.contentMode().collectAsStateWithLifecycle()
        val showManga by uiPreferences.showMangaMode().collectAsStateWithLifecycle()
        val sharedTopBarState = remember { SharedTopBarState() }
        val showAnime by uiPreferences.showAnimeMode().collectAsStateWithLifecycle()
        val showNovel by uiPreferences.showNovelMode().collectAsStateWithLifecycle()
        // Unified glass tint — shared by nav bar, top bar, and update overlay.
        val glassTint = eu.kanade.presentation.components.GlassTintController.resolvedTint()
        val floatingGlassTopBar by uiPreferences.floatingGlassTopBar().collectAsStateWithLifecycle()
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        // If the current content mode is disabled, switch to the first visible mode
        LaunchedEffect(showManga, showAnime, showNovel) {
            val isVisible = when (contentMode) {
                ContentMode.MANGA -> showManga
                ContentMode.ANIME -> showAnime
                ContentMode.NOVEL -> showNovel
            }
            if (!isVisible) {
                val fallback = when {
                    showManga -> ContentMode.MANGA
                    showAnime -> ContentMode.ANIME
                    showNovel -> ContentMode.NOVEL
                    else -> ContentMode.MANGA
                }
                uiPreferences.contentMode().set(fallback)
            }
        }

        TabNavigator(
            tab = defaultTab,
            key = TAB_NAVIGATOR_KEY,
        ) { tabNavigator ->
            // Provide usable navigator to content screen
            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalSharedTopBar provides sharedTopBarState,
            ) {
                val hazeState = remember { HazeState() }
                val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
                    rememberTopAppBarState(),
                )
                // Reset top bar visibility when switching tabs.
                // Also close any active search so it doesn't leak into the
                // new tab — each tab starts with search closed.
                val currentTabKey = tabNavigator.current.key
                LaunchedEffect(currentTabKey) {
                    topBarScrollBehavior.state.heightOffset = 0f
                    if (sharedTopBarState.searchQuery != null) {
                        sharedTopBarState.onSearchQueryChange(null)
                    }
                }
                // Observe "view failures" commands from the library update progress
                // overlay (which lives outside the TabNavigator scope in MainActivity)
                // and switch to the Updates tab when the user taps "view failures".
                LaunchedEffect(Unit) {
                    LibraryUpdateProgressBus.commands.collect { command ->
                        if (command is LibraryUpdateProgressBus.Command.ViewFailures) {
                            tabNavigator.current = UpdatesTab
                        }
                    }
                }
                Scaffold(
                    modifier = if (floatingGlassTopBar) {
                        Modifier
                    } else {
                        Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection)
                    },
                    containerColor = if (floatingGlassTopBar) Color.Transparent else MaterialTheme.colorScheme.background,
                    topBar = {
                        if (floatingGlassTopBar) {
                            FloatingGlassTopBar(
                                hazeState = hazeState,
                                sharedTopBarState = sharedTopBarState,
                                tint = glassTint,
                            )
                        } else {
                            // Standard (non-glass) top bar — same search-first design
                            // but with a solid surface background.
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val statusBarHeight = remember {
                                val res = context.resources
                                val id = res.getIdentifier("status_bar_height", "dimen", "android")
                                if (id > 0) with(density) { res.getDimensionPixelSize(id).toDp() } else 0.dp
                            }
                            Column(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(top = statusBarHeight),
                            ) {
                                SearchFirstTopBarContent(
                                    sharedTopBarState = sharedTopBarState,
                                )
                            }
                        }
                    },
                    startBar = {
                        if (isTabletUi()) {
                            NavigationRail {
                                navStyle.tabs.fastForEach {
                                    NavigationRailItem(it)
                                }
                            }
                        }
                    },
                    bottomBar = {
                        if (!isTabletUi()) {
                                val bottomNavVisible by produceState(initialValue = true) {
                                    showBottomNavEvent.receiveAsFlow().collectLatest { value = it }
                                }
                                val navBarAppearance by uiPreferences.navBarAppearance().collectAsStateWithLifecycle()
                                val navBarIconsOnly by uiPreferences.navBarIconsOnly().collectAsStateWithLifecycle()
                                val modeCount = listOf(showManga, showAnime, showNovel).count { it }
                                AnimatedVisibility(
                                    visible = bottomNavVisible,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    val navBottomPadding = if (navBarIconsOnly) 6.dp else 14.dp
                                    val navRowHeight = if (navBarIconsOnly) 52.dp else 72.dp
                                    val navTopPadding = if (navBarIconsOnly) 10.dp else 10.dp
                                    if (navBarAppearance == eu.kanade.domain.ui.model.NavBarAppearance.FLOATING_GLASS) {
                                        if (modeCount > 1) {
                                            FloatingGlassNavigationBarWithModes(
                                                hazeState = hazeState,
                                                tint = glassTint,
                                                topPadding = navTopPadding,
                                                bottomPadding = navBottomPadding,
                                                navRowHeight = navRowHeight,
                                                modeRow = {
                                                    ModePill(
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                },
                                            ) {
                                                navStyle.tabs.fastForEach {
                                                    AuroraNavigationBarItem(it, showLabel = !navBarIconsOnly)
                                                }
                                            }
                                        } else {
                                            FloatingGlassNavigationBar(
                                                hazeState = hazeState,
                                                tint = glassTint,
                                                bottomPadding = navBottomPadding,
                                                navRowHeight = navRowHeight,
                                            ) {
                                                navStyle.tabs.fastForEach {
                                                    AuroraNavigationBarItem(it, showLabel = !navBarIconsOnly)
                                                }
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.background(
                                                MaterialTheme.colorScheme.surfaceContainer,
                                            ),
                                        ) {
                                            if (modeCount > 1) {
                                                ModePill(
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                            NavigationBar {
                                                navStyle.tabs.fastForEach {
                                                    NavigationBarItem(it, showLabel = !navBarIconsOnly)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        contentWindowInsets = WindowInsets(0),
                    ) { contentPadding ->
                        val fadeDuration = tabFadeDuration()
                        androidx.compose.runtime.CompositionLocalProvider(
                            eu.kanade.presentation.components.LocalHostScaffoldContentPadding provides contentPadding,
                        ) {
                            // Outer Box: contains hazeSource and overlay as siblings.
                            // Haze cannot blur a source it lives inside, so the overlay
                            // must be a sibling of the hazeSource Box, not a child.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (floatingGlassTopBar) Modifier
                                        else Modifier.padding(top = contentPadding.calculateTopPadding()),
                                    )
                                    .consumeWindowInsets(contentPadding),
                            ) {
                                // hazeSource: tab content
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .hazeSource(hazeState),
                                ) {
                                    // Tab content — extends behind the floating glass top
                                    // bar so the haze blur can sample it (same pattern as
                                    // the floating glass nav bar). Each tab reads
                                    // LocalHostScaffoldContentPadding to pad its own
                                    // scrollable content so items aren't hidden.
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        AnimatedContent(
                                            targetState = tabNavigator.current,
                                            transitionSpec = {
                                                materialFadeThroughIn(
                                                    initialScale = 1f,
                                                    durationMillis = fadeDuration,
                                                ) togetherWith
                                                    materialFadeThroughOut(durationMillis = fadeDuration)
                                            },
                                            label = "tabContent",
                                        ) {
                                            tabNavigator.saveableState(key = "currentTab", it) {
                                                it.Content()
                                            }
                                        }
                                    }
                                }
                                // Library update progress overlay — sibling of the
                                // hazeSource so its glass blur samples the tab
                                // content behind it. Sits above the nav bar via
                                // LocalHostScaffoldContentPadding.
                                LibraryUpdateProgressOverlay(
                                    onViewFailures = {
                                        UpdatesTab.navigateToFetchingRequested = true
                                        tabNavigator.current = UpdatesTab
                                    },
                                    hazeState = hazeState,
                                    tint = glassTint,
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                                // No click-outside-to-close scrim: when search is
                                // active, the user should be able to scroll through
                                // the results list without the search closing.
                                // Search is closed via the X button or back press.
                            }
                        }
                    }
                }

            val goToStartScreen = {
                tabNavigator.current = defaultTab
            }
            BackHandler(
                enabled = tabNavigator.current != defaultTab,
                onBack = goToStartScreen,
            )

            LaunchedEffect(Unit) {
                launch {
                    librarySearchEvent.receiveAsFlow().collectLatest {
                        goToStartScreen()
                        LibraryTab.search(it)
                    }
                }
                launch {
                    openTabEvent.receiveAsFlow().collectLatest {
                        // Set content mode if specified (e.g. from a deep link
                        // or extension update notification).
                        if (it is Tab.Library && it.mode != null) {
                            uiPreferences.contentMode().set(it.mode)
                        }
                        if (it is Tab.Browse && it.mode != null) {
                            uiPreferences.contentMode().set(it.mode)
                        }

                        tabNavigator.current = when (it) {
                            is Tab.Library -> LibraryTab
                            is Tab.Updates -> UpdatesTab
                            is Tab.History -> UpdatesTab // History is now a sub-tab of Updates
                            is Tab.Fetching -> UpdatesTab // Fetching is a conditional sub-tab of Updates
                            is Tab.Browse -> BrowseTab
                            is Tab.More -> {
                                // More tab removed — navigate to Settings instead
                                navigator.push(eu.kanade.tachiyomi.ui.setting.SettingsScreen())
                                tabNavigator.current
                            }
                        }

                        if (it is Tab.Library && it.entryIdToOpen != null) {
                            when (it.mode ?: uiPreferences.contentMode().get()) {
                                ContentMode.ANIME -> navigator.push(AnimeScreen(it.entryIdToOpen))
                                ContentMode.MANGA -> navigator.push(MangaScreen(it.entryIdToOpen))
                                ContentMode.NOVEL -> navigator.push(NovelScreen(it.entryIdToOpen))
                            }
                        }
                        if (it is Tab.More && it.toDownloads) {
                            navigator.push(DownloadsTab)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RowScope.NavigationBarItem(
        tab: eu.kanade.presentation.util.Tab,
        showLabel: Boolean = true,
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationBarItem(
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = showLabel,
        )
    }

    /**
     * Aurora-styled nav bar item with a gradient pill behind the selected icon.
     * Ported from Tadami's AuroraNavigationBarItem.
     */
    @Composable
    private fun RowScope.AuroraNavigationBarItem(
        tab: eu.kanade.presentation.util.Tab,
        showLabel: Boolean = true,
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val accent = MaterialTheme.colorScheme.primary
        val iconColor = if (selected) {
            accent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.72f else 0.85f)
        }
        val labelColor = if (selected) {
            accent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.82f else 0.92f)
        }
        val iconBackgroundColor = if (selected) {
            // Solid color pill — no gradient. Uses the primary color at a
            // moderate alpha so it reads as a selection indicator without
            // overwhelming the icon.
            accent.copy(alpha = if (isDark) 0.22f else 0.15f)
        } else {
            null
        }
        val iconShape = RoundedCornerShape(999.dp)

        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 1.dp)
                .padding(top = if (showLabel) 8.dp else 6.dp, bottom = if (showLabel) 4.dp else 6.dp)
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (!selected) {
                            tabNavigator.current = tab
                        } else {
                            scope.launch { tab.onReselect(navigator) }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (selected && iconBackgroundColor != null) {
                                Modifier
                                    .background(iconBackgroundColor, iconShape)
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                        NavigationIconItem(tab)
                    }
                }

                if (showLabel) {
                    Text(
                        text = tab.options.title,
                        color = labelColor,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.92f,
                        ),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    @Composable
    fun NavigationRailItem(tab: eu.kanade.presentation.util.Tab) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationRailItem(
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = true,
        )
    }

    @Composable
    private fun NavigationIconItem(tab: eu.kanade.presentation.util.Tab) {
        BadgedBox(
            badge = {
                when {
                    UpdatesTab::class.isInstance(tab) -> {
                        val contentMode by uiPreferences.contentMode().collectAsStateWithLifecycle()
                        val count by produceState(initialValue = 0, contentMode) {
                            val pref = Injekt.get<LibraryPreferences>()
                            val countFlow = when (contentMode) {
                                ContentMode.ANIME -> pref.newAnimeUpdatesCount().changes()
                                ContentMode.MANGA -> pref.newMangaUpdatesCount().changes()
                                ContentMode.NOVEL -> pref.newNovelUpdatesCount().changes()
                            }
                            countFlow.collectLatest { value = if (pref.newShowUpdatesCount().get()) it else 0 }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.notification_chapters_generic,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                    BrowseTab::class.isInstance(tab) -> {
                        val count by produceState(initialValue = 0) {
                            val pref = Injekt.get<SourcePreferences>()
                            combine(
                                pref.mangaExtensionUpdatesCount().changes(),
                                pref.animeExtensionUpdatesCount().changes(),
                            ) { extCount, animeExtCount -> extCount + animeExtCount }
                                .collectLatest { value = it }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.update_check_notification_ext_updates,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                }
            },
        ) {
            Icon(
                painter = tab.options.icon!!,
                contentDescription = tab.options.title,
                // TODO: https://issuetracker.google.com/u/0/issues/316327367
                tint = LocalContentColor.current,
            )
        }
    }

    suspend fun search(query: String) {
        librarySearchEvent.send(query)
    }

    suspend fun openTab(tab: Tab) {
        openTabEvent.send(tab)
    }

    suspend fun showBottomNav(show: Boolean) {
        showBottomNavEvent.send(show)
    }

    sealed interface Tab {
        data class Library(
            val mode: ContentMode? = null,
            val entryIdToOpen: Long? = null,
        ) : Tab
        data object Updates : Tab
        data object History : Tab
        /** Opens the Updates tab and switches to the Fetching sub-tab. */
        data object Fetching : Tab
        data class Browse(val mode: ContentMode? = null) : Tab
        data class More(val toDownloads: Boolean) : Tab
    }
}

private fun Color.luminance(): Float {
    val r = red * 0.299f
    val g = green * 0.587f
    val b = blue * 0.114f
    return r + g + b
}

/**
 * Floating glassmorphic top bar — mirrors the [FloatingGlassNavigationBar] design.
 *
 * Design:
 * - No title text. The bar is a search-first design.
 * - Left: search icon (if search is available for this tab) or back arrow (if navigateUp is set).
 * - Center: tappable search area showing hint text. Tapping activates search mode.
 *   When search is active, this becomes a text input field.
 * - Right: action icons.
 * - Narrower than the old design — just icon height + padding.
 * - No solid background — the glass surface is transparent so Haze can blur
 *   the content scrolling behind it.
 */
@Composable
private fun FloatingGlassTopBar(
    hazeState: HazeState,
    sharedTopBarState: SharedTopBarState,
    tint: Color,
) {
    val colorScheme = MaterialTheme.colorScheme
    val resolvedTint = tint
    val shape: Shape = RoundedCornerShape(20.dp)

    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val statusBarHeight = remember {
        val res = context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        if (id > 0) with(density) { res.getDimensionPixelSize(id).toDp() } else 0.dp
    }

    val glassModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp)
        .padding(top = statusBarHeight + 6.dp, bottom = 6.dp)
        .shadow(elevation = 8.dp, shape = shape)
        .clip(shape)
        .hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = colorScheme.background,
                tint = HazeTint(resolvedTint),
                blurRadius = 24.dp,
                noiseFactor = 0.12f,
            ),
        )

    Box(
        modifier = Modifier.then(glassModifier),
    ) {
        Column {
            SearchFirstTopBarContent(
                sharedTopBarState = sharedTopBarState,
            )
        }
    }
}

/**
 * Search-first top bar content — used by both the floating glass top bar
 * and the standard (non-glass) top bar.
 *
 * Layout: [back/search icon] [search field or hint area] [action icons]
 */
@Composable
private fun SearchFirstTopBarContent(
    sharedTopBarState: SharedTopBarState,
) {
    val colorScheme = MaterialTheme.colorScheme
    // Search is active when the user has opened the search field (query is non-null).
    // Does NOT require searchEnabled — that field is inconsistently set across tabs.
    val isSearchActive = sharedTopBarState.searchAvailable && sharedTopBarState.searchQuery != null
    val canSearch = sharedTopBarState.searchAvailable
    val hasNavigateUp = sharedTopBarState.navigateUp != null

    // Fixed small slide offsets — just a few dp, never the full icon width.
    val slidePx = with(LocalDensity.current) { 8.dp.roundToPx() }
    // Hint and icon-swap use a smaller slide.
    val hintSlidePx = with(LocalDensity.current) { 4.dp.roundToPx() }

    Column {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Left slot (48dp): back arrow or search icon ──
        // Static between main tabs — only animates when entering/leaving
        // a search-capable or navigateUp context. Small fixed-dp slide + fade.
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Back arrow — visible on sub-screens with navigateUp
            androidx.compose.animation.AnimatedVisibility(
                visible = hasNavigateUp,
                enter = slideInHorizontally(animationSpec = tween(300), initialOffsetX = { -slidePx }) +
                    fadeIn(animationSpec = tween(300)),
                exit = slideOutHorizontally(animationSpec = tween(220), targetOffsetX = { -slidePx }) +
                    fadeOut(animationSpec = tween(220)),
            ) {
                IconButton(onClick = { sharedTopBarState.navigateUp?.invoke() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
            // Search icon — visible on search-capable tabs without navigateUp.
            androidx.compose.animation.AnimatedVisibility(
                visible = canSearch && !hasNavigateUp,
                enter = slideInHorizontally(animationSpec = tween(300), initialOffsetX = { -slidePx }) +
                    fadeIn(animationSpec = tween(300)),
                exit = slideOutHorizontally(animationSpec = tween(220), targetOffsetX = { -slidePx }) +
                    fadeOut(animationSpec = tween(220)),
            ) {
                IconButton(
                    onClick = { sharedTopBarState.onSearchQueryChange("") },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                    )
                }
            }
        }

        // ── Center slot: search field or tappable hint ──
        // Both use the same horizontal padding so the hint text stays in the
        // same position when transitioning between hint and active search.
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Use the tab's placeholder text, or fall back to "Search..." only
            // when search is active (for the text field placeholder). When the
            // tab doesn't support search, fullHint is null and the hint box
            // shows nothing — avoiding the "Search..." flash during fade-out.
            val fullHint = sharedTopBarState.searchPlaceholderText
                ?: if (isSearchActive) stringResource(MR.strings.action_search_hint) else null

            if (isSearchActive) {
                // Active search field — auto-focused on appearance.
                val focusRequester = remember { FocusRequester() }
                val keyboardController = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) {
                    try { focusRequester.requestFocus() } catch (_: Exception) {}
                }
                val submitSearch: () -> Unit = {
                    val query = sharedTopBarState.searchQuery
                    if (!query.isNullOrBlank()) {
                        sharedTopBarState.onSearch(query)
                        keyboardController?.hide()
                    }
                }
                BasicTextField(
                    value = sharedTopBarState.searchQuery ?: "",
                    onValueChange = { sharedTopBarState.onSearchQueryChange(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .focusRequester(focusRequester)
                        .runOnEnterKeyPressed(action = submitSearch),
                    decorationBox = { innerTextField ->
                        Box {
                            if (sharedTopBarState.searchQuery.isNullOrBlank()) {
                                // Placeholder — single Text with the full hint,
                                // same padding as the tappable hint so text
                                // doesn't shift when search opens.
                                Text(
                                    text = fullHint ?: stringResource(MR.strings.action_search_hint),
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            } else {
                // Tappable hint or empty (non-search tab).
                // AnimatedVisibility keyed on canSearch: fades the hint in/out
                // when entering/leaving a search-capable tab. No size animation
                // — the hint text stays in place and just fades.
                androidx.compose.animation.AnimatedVisibility(
                    visible = canSearch,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(220)),
                ) {
                    // Keep the last non-null hint text during exit animation
                    // so the text doesn't flash to "Search..." when switching
                    // to a non-search tab.
                    var lastHint by remember { mutableStateOf(fullHint) }
                    if (fullHint != null) lastHint = fullHint
                    val displayHint = fullHint ?: lastHint ?: ""

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                sharedTopBarState.onSearchQueryChange("")
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        // Hint text — instant swap between search-capable tabs.
                        Text(
                            text = displayHint,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // ── Right slot: action icons + X close button (overlaid) ──
        // Visible icons use per-slot AnimatedVisibility (no AnimatedContent,
        // no container size animation, no arch). Three-dot overflow is static.
        // The X close button is overlaid at the end so it doesn't shift while
        // action icons are fading out.
        Box {
            // Action icons — visible when search is NOT active.
            androidx.compose.animation.AnimatedVisibility(
                visible = !isSearchActive,
                enter = slideInHorizontally(animationSpec = tween(300), initialOffsetX = { slidePx }) +
                    fadeIn(animationSpec = tween(300)),
                exit = slideOutHorizontally(animationSpec = tween(220), targetOffsetX = { -slidePx }) +
                    fadeOut(animationSpec = tween(220)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tab-specific visible icons — per-slot approach.
                    // Outer AnimatedVisibility handles appear/disappear (slot
                    // becomes empty/non-empty). Inner AnimatedContent handles
                    // icon swaps within the same slot (filter → calendar).
                    // Each slot is a fixed 48dp IconButton, so AnimatedContent's
                    // size animation is a no-op — no arch.
                    val visibleActions = sharedTopBarState.actions
                        .filterIsInstance<AppBar.Action>()
                    val maxSlots = 4
                    for (i in 0 until maxSlots) {
                        val action = visibleActions.getOrNull(i)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = action != null,
                            enter = slideInHorizontally(animationSpec = tween(300), initialOffsetX = { slidePx }) +
                                fadeIn(animationSpec = tween(300)),
                            exit = slideOutHorizontally(animationSpec = tween(220), targetOffsetX = { -slidePx }) +
                                fadeOut(animationSpec = tween(220)),
                        ) {
                            // Icon swap within the same slot is instant — no
                            // slide/fade animation when the icon changes (e.g.
                            // filter → calendar between tabs).
                            if (action != null) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                    tooltip = {
                                        PlainTooltip { Text(action.title) }
                                    },
                                    state = rememberTooltipState(),
                                ) {
                                    IconButton(
                                        onClick = action.onClick,
                                        enabled = action.enabled,
                                    ) {
                                        Icon(
                                            imageVector = action.icon,
                                            tint = action.iconTint ?: LocalContentColor.current,
                                            contentDescription = action.title,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Three-dot overflow — static between tabs.
                    val overflowActions = sharedTopBarState.actions
                        .filter { it !is AppBar.Action }
                        .toPersistentList()
                    if (overflowActions.isNotEmpty()) {
                        AppBarActions(overflowActions)
                    }
                }
            }
            // X close button — overlaid at the end so it doesn't shift.
            // Takes longer to come in (delayed + longer duration) so action
            // icons have time to fade out first.
            androidx.compose.animation.AnimatedVisibility(
                visible = isSearchActive,
                enter = slideInHorizontally(
                    animationSpec = tween(450, delayMillis = 200),
                    initialOffsetX = { slidePx },
                ) + fadeIn(animationSpec = tween(450, delayMillis = 200)),
                exit = slideOutHorizontally(animationSpec = tween(220), targetOffsetX = { slidePx }) +
                    fadeOut(animationSpec = tween(220)),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                IconButton(
                    onClick = { sharedTopBarState.onSearchQueryChange(null) },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close search",
                    )
                }
            }
        }
    }

    // Collection subtitle — shown below the top bar when a library tab provides
    // a collection name and/or item count. Hidden during active search.
    if (!isSearchActive && (sharedTopBarState.subtitle != null || sharedTopBarState.numberOfEntries != null)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sharedTopBarState.subtitle?.let { subtitleText ->
                Text(
                    text = subtitleText,
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            sharedTopBarState.numberOfEntries?.let { count ->
                if (sharedTopBarState.subtitle != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "($count)",
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }

    // Optional pill content shown below the search bar while search is active.
    // Fades + expands/collapses vertically when search opens/closes.
    androidx.compose.animation.AnimatedVisibility(
        visible = isSearchActive,
        enter = fadeIn(animationSpec = tween(300)) + expandVertically(
            animationSpec = tween(300),
            expandFrom = Alignment.Top,
        ),
        exit = fadeOut(animationSpec = tween(220)) + shrinkVertically(
            animationSpec = tween(220),
            shrinkTowards = Alignment.Top,
        ),
    ) {
        sharedTopBarState.searchPillContent?.invoke()
    }
    } // Column
}
