package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import dev.chrisbanes.haze.hazeSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.presentation.home.ModePill
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
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

    @Composable
    override fun Content() {
        val navStyle by uiPreferences.navStyle().collectAsStateWithLifecycle()
        val contentMode by uiPreferences.contentMode().collectAsStateWithLifecycle()
        val showManga by uiPreferences.showMangaMode().collectAsStateWithLifecycle()
        val showAnime by uiPreferences.showAnimeMode().collectAsStateWithLifecycle()
        val showNovel by uiPreferences.showNovelMode().collectAsStateWithLifecycle()
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
            CompositionLocalProvider(LocalNavigator provides navigator) {
                val hazeState = remember { HazeState() }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
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
                                    val navBottomPadding = if (navBarIconsOnly) 8.dp else 14.dp
                                    val navRowHeight = if (navBarIconsOnly) 52.dp else 72.dp
                                    if (navBarAppearance == eu.kanade.domain.ui.model.NavBarAppearance.FLOATING_GLASS) {
                                        if (modeCount > 1) {
                                            FloatingGlassNavigationBarWithModes(
                                                hazeState = hazeState,
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
                                                bottomPadding = navBottomPadding,
                                                navRowHeight = navRowHeight,
                                            ) {
                                                navStyle.tabs.fastForEach {
                                                    AuroraNavigationBarItem(it, showLabel = !navBarIconsOnly)
                                                }
                                            }
                                        }
                                    } else {
                                        Column {
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
                        Box(
                            modifier = Modifier
                                .padding(top = contentPadding.calculateTopPadding())
                                .consumeWindowInsets(contentPadding)
                                .hazeSource(hazeState),
                        ) {
                            androidx.compose.runtime.CompositionLocalProvider(
                                eu.kanade.presentation.components.LocalHostScaffoldContentPadding provides contentPadding,
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
                        // Set content mode if specified (e.g. from a deep link).
                        if (it is Tab.Library && it.mode != null) {
                            uiPreferences.contentMode().set(it.mode)
                        }

                        tabNavigator.current = when (it) {
                            is Tab.Library -> LibraryTab
                            is Tab.Updates -> UpdatesTab
                            is Tab.History -> UpdatesTab // History is now a sub-tab of Updates
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
        val iconBackgroundBrush = if (selected) {
            Brush.verticalGradient(
                listOf(
                    if (isDark) accent.copy(alpha = 0.28f) else accent.copy(alpha = 0.18f),
                    if (isDark) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.78f),
                ),
            )
        } else {
            null
        }
        val iconShape = RoundedCornerShape(999.dp)

        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 1.dp)
                .padding(top = if (showLabel) 8.dp else 4.dp, bottom = if (showLabel) 4.dp else 4.dp)
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
                            if (selected && iconBackgroundBrush != null) {
                                Modifier
                                    .background(iconBackgroundBrush, iconShape)
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
        data class Browse(val toExtensions: Boolean = false, val anime: Boolean = false) : Tab
        data class More(val toDownloads: Boolean) : Tab
    }
}

private fun Color.luminance(): Float {
    val r = red * 0.299f
    val g = green * 0.587f
    val b = blue * 0.114f
    return r + g + b
}
