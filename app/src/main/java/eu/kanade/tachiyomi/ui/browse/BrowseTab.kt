package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.globalOverflowActions
import eu.kanade.presentation.components.useSharedTopBarWithSearch
import eu.kanade.presentation.more.settings.screen.browse.ConsolidatedExtensionReposScreen
import eu.kanade.presentation.browse.components.ExtensionCard
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.ui.browse.anime.extension.animeExtensionsTab
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.animeSourcesTab
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.extension.mangaExtensionsTab
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.mangaSourcesTab
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.novel.extension.novelExtensionsTab
import eu.kanade.tachiyomi.ui.browse.novel.extension.NovelExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.source.novelSourcesTab
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.interactor.GetRemoteManga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.interactor.GetRemoteNovel
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object BrowseTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current is BrowseTab
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalAnimeSearchScreen())
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val contentMode by uiPreferences.contentMode().collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        // Search state — query survives pill toggle
        var searchQuery by remember { mutableStateOf<String?>(null) }
        var showExtensions by remember { mutableStateOf(false) }
        // Whether the user has submitted the search (pressed enter/search on IME).
        // Results are only shown after submission, not while typing.
        var searchSubmitted by remember { mutableStateOf(false) }

        // Extension screen models (one per type; cached across mode changes via rememberScreenModel)
        val animeExtSM = rememberScreenModel { AnimeExtensionsScreenModel() }
        val mangaExtSM = rememberScreenModel { MangaExtensionsScreenModel() }
        val novelExtSM = rememberScreenModel { NovelExtensionsScreenModel() }

        // Source managers for source search
        val animeSourceManager = remember { Injekt.get<AnimeSourceManager>() }
        val mangaSourceManager = remember { Injekt.get<MangaSourceManager>() }
        val novelSourceManager = remember { Injekt.get<NovelSourceManager>() }

        // Background check for extension updates/uninstalls/errors each time the
        // content mode changes or the tab is revisited. Installed extensions are
        // already cached in the extension manager's StateFlow, so the UI shows
        // them immediately; this refreshes the *available* list (which drives
        // update detection) in the background.
        LaunchedEffect(contentMode) {
            when (contentMode) {
                ContentMode.ANIME -> animeExtSM.findAvailableExtensions()
                ContentMode.MANGA -> mangaExtSM.findAvailableExtensions()
                ContentMode.NOVEL -> novelExtSM.findAvailableExtensions()
            }
        }

        // Push search query into the active extension screen model.
        // Extensions side: search immediately as the user types (no submit
        // needed). Browse side: results only show after the user submits.
        LaunchedEffect(searchQuery, showExtensions, contentMode) {
            if (showExtensions && searchQuery != null) {
                when (contentMode) {
                    ContentMode.ANIME -> animeExtSM.search(searchQuery)
                    ContentMode.MANGA -> mangaExtSM.search(searchQuery)
                    ContentMode.NOVEL -> novelExtSM.search(searchQuery)
                }
            }
        }

        // Mode-aware source tab (used when search is NOT active)
        val sourceTab = when (contentMode) {
            ContentMode.ANIME -> animeSourcesTab()
            ContentMode.MANGA -> mangaSourcesTab()
            ContentMode.NOVEL -> novelSourcesTab()
        }

        val titleRes = MR.strings.browse

        // Card design preferences
        val sourcePreferences: SourcePreferences = remember { Injekt.get() }
        val cardDesignPref = sourcePreferences.browseCardDesign()
        val cardColumnsPref = sourcePreferences.browseCardColumns()
        val cardDesign by cardDesignPref.changes().collectAsState(cardDesignPref.get())
        val cardColumns by cardColumnsPref.changes().collectAsState(cardColumnsPref.get())

        // Register with shared top bar (with search support)
        val extensionAction = listOf<AppBar.AppBarAction>(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.label_extension_repos),
                onClick = { navigator.push(ConsolidatedExtensionReposScreen()) },
            ),
        )
        val columnSelectorAction = if (cardDesign) {
            listOf<AppBar.AppBarAction>(
                AppBar.NestedOverflowAction(
                    title = "Card Columns",
                    subActions = listOf(
                        AppBar.OverflowAction(
                            title = "1 column",
                            onClick = { sourcePreferences.browseCardColumns().set(1) },
                        ),
                        AppBar.OverflowAction(
                            title = "2 columns",
                            onClick = { sourcePreferences.browseCardColumns().set(2) },
                        ),
                        AppBar.OverflowAction(
                            title = "3 columns",
                            onClick = { sourcePreferences.browseCardColumns().set(3) },
                        ),
                    ),
                ),
            )
        } else {
            emptyList()
        }
        val allActions = (sourceTab.actions + columnSelectorAction + extensionAction + globalOverflowActions(
            onClickSettings = { navigator.push(SettingsScreen()) },
        )).toImmutableList()

        useSharedTopBarWithSearch(
            title = stringResource(titleRes),
            actions = allActions,
            searchEnabled = true,
            searchQuery = searchQuery,
            onSearchQueryChange = { query ->
                searchQuery = query
                // Reset submitted flag while typing — Browse results only show
                // after enter. Extensions side shows results immediately.
                searchSubmitted = false
                if (query == null) {
                    showExtensions = false
                    searchSubmitted = false
                }
            },
            onSearch = { query ->
                if (query.isBlank()) return@useSharedTopBarWithSearch
                // Only the Browse side uses submit — it navigates to global
                // search. The Extensions side already shows results live.
                if (!showExtensions) {
                    when (contentMode) {
                        ContentMode.ANIME -> navigator.push(GlobalAnimeSearchScreen(query))
                        ContentMode.MANGA -> navigator.push(GlobalMangaSearchScreen(query))
                        ContentMode.NOVEL -> navigator.push(GlobalNovelSearchScreen(query))
                    }
                    searchQuery = null
                    searchSubmitted = false
                } else {
                    // On the Extensions side, submit just marks results as
                    // "submitted" so the Browse-side gate (if toggled later)
                    // doesn't immediately navigate.
                    searchSubmitted = true
                }
            },
            searchPillContent = {
                BrowseSearchPill(
                    showExtensions = showExtensions,
                    onToggle = { showExtensions = it },
                )
            },
        )

        Scaffold(
            topBar = {},
            snackbarHost = {
                SnackbarHost(
                    snackbarHostState,
                    modifier = Modifier.padding(bottom = 80.dp),
                )
            },
        ) { padding ->
            val hostBottom = eu.kanade.presentation.components.LocalHostScaffoldContentPadding.current
                ?.calculateBottomPadding() ?: androidx.compose.ui.unit.Dp.Hairline
            val resolvedPadding = androidx.compose.foundation.layout.PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + hostBottom,
            )

            val currentQuery = searchQuery
            if (currentQuery != null) {
                // Search mode
                if (showExtensions) {
                    // Extensions side: show results immediately as the user
                    // types — no submit needed. Use card design when enabled.
                    if (cardDesign) {
                        BrowseExtensionCardSearchResults(
                            contentMode = contentMode,
                            contentPadding = resolvedPadding,
                            cardColumns = cardColumns,
                            animeExtSM = animeExtSM,
                            mangaExtSM = mangaExtSM,
                            novelExtSM = novelExtSM,
                            onInstallExtension = { ext ->
                                when (ext) {
                                    is NovelExtension.Available -> novelExtSM.installExtension(ext)
                                    is MangaExtension.Available -> mangaExtSM.installExtension(ext)
                                    is AnimeExtension.Available -> animeExtSM.installExtension(ext)
                                }
                            },
                        )
                    } else {
                        val extTab = when (contentMode) {
                            ContentMode.ANIME -> animeExtensionsTab(animeExtSM)
                            ContentMode.MANGA -> mangaExtensionsTab(mangaExtSM)
                            ContentMode.NOVEL -> novelExtensionsTab(novelExtSM)
                        }
                        extTab.content(resolvedPadding, snackbarHostState)
                    }
                } else if (searchSubmitted) {
                    // Browse side: only show source search results after the
                    // user submits (presses enter/search on IME).
                    BrowseSourceSearchResults(
                        contentMode = contentMode,
                        searchQuery = currentQuery,
                        contentPadding = resolvedPadding,
                        animeSourceManager = animeSourceManager,
                        mangaSourceManager = mangaSourceManager,
                        novelSourceManager = novelSourceManager,
                        onSourceClick = { sourceId ->
                            when (contentMode) {
                                ContentMode.ANIME -> navigator.push(
                                    BrowseAnimeSourceScreen(sourceId, GetRemoteAnime.QUERY_POPULAR),
                                )
                                ContentMode.MANGA -> navigator.push(
                                    BrowseMangaSourceScreen(sourceId, GetRemoteManga.QUERY_POPULAR),
                                )
                                ContentMode.NOVEL -> navigator.push(
                                    BrowseNovelSourceScreen(sourceId, GetRemoteNovel.QUERY_POPULAR),
                                )
                            }
                        },
                    )
                } else {
                    // Browse side, typing but not submitted yet: show normal
                    // source tab content underneath.
                    sourceTab.content(resolvedPadding, snackbarHostState)
                }
            } else {
                // Normal mode: show source tab
                sourceTab.content(resolvedPadding, snackbarHostState)
            }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

/**
 * Segmented pill toggle: Browse | Extensions.
 * Uses Material3 SingleChoiceSegmentedButtonRow — same component as the
 * Settings > Appearance content mode toggle.
 * Only shown while search is active.
 */
@Composable
private fun BrowseSearchPill(
    showExtensions: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        stringResource(MR.strings.browse) to false,
        stringResource(MR.strings.label_extensions) to true,
    )
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        options.forEachIndexed { index, (label, isExt) ->
            SegmentedButton(
                selected = showExtensions == isExt,
                onClick = { onToggle(isExt) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label)
            }
        }
    }
}

/**
 * Simple filtered list of online sources for Browse search mode.
 */
@Composable
private fun BrowseSourceSearchResults(
    contentMode: ContentMode,
    searchQuery: String,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    animeSourceManager: AnimeSourceManager,
    mangaSourceManager: MangaSourceManager,
    novelSourceManager: NovelSourceManager,
    onSourceClick: (Long) -> Unit,
) {
    val query = searchQuery.trim()
    val sources = remember(contentMode, query) {
        when (contentMode) {
            ContentMode.ANIME -> animeSourceManager.getOnlineSources()
                .filter { it.name.contains(query, ignoreCase = true) }
                .map { SourceSearchItem(it.id, it.name, it.lang) }
            ContentMode.MANGA -> mangaSourceManager.getOnlineSources()
                .filter { it.name.contains(query, ignoreCase = true) }
                .map { SourceSearchItem(it.id, it.name, it.lang) }
            ContentMode.NOVEL -> novelSourceManager.getOnlineSources()
                .filter { it.name.contains(query, ignoreCase = true) }
                .map { SourceSearchItem(it.id, it.name, it.lang) }
        }
    }

    if (sources.isEmpty()) {
        EmptyScreen(
            stringRes = MR.strings.no_results_found,
            modifier = Modifier.padding(contentPadding),
        )
    } else {
        LazyColumn(
            contentPadding = contentPadding,
        ) {
            items(
                items = sources,
                key = { it.id },
            ) { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSourceClick(source.id) }
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp,
                        ),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        if (source.lang.isNotEmpty()) {
                            Text(
                                text = source.lang.uppercase(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SourceSearchItem(
    val id: Long,
    val name: String,
    val lang: String,
)

/**
 * Card-based extension search results. Renders all extensions matching the
 * current search query as cards in a lazy grid of rows. Used when card design
 * is enabled and the user is on the Extensions side of the search pill.
 */
@Composable
private fun BrowseExtensionCardSearchResults(
    contentMode: ContentMode,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    cardColumns: Int,
    animeExtSM: AnimeExtensionsScreenModel,
    mangaExtSM: MangaExtensionsScreenModel,
    novelExtSM: NovelExtensionsScreenModel,
    onInstallExtension: (Any) -> Unit,
) {
    val animeState by animeExtSM.state.collectAsState()
    val mangaState by mangaExtSM.state.collectAsState()
    val novelState by novelExtSM.state.collectAsState()

    // Flatten the grouped items into a single list of extensions, filtered by
    // the search query (which is already applied by the screen model).
    val extensions: List<Any> = when (contentMode) {
        ContentMode.ANIME -> animeState.items.values.flatten().map { it.extension }
        ContentMode.MANGA -> mangaState.items.values.flatten().map { it.extension }
        ContentMode.NOVEL -> novelState.items.values.flatten().map { it.extension }
    }

    if (extensions.isEmpty()) {
        EmptyScreen(
            stringRes = MR.strings.no_results_found,
            modifier = Modifier.padding(contentPadding),
        )
    } else {
        val rows = extensions.chunked(cardColumns)
        LazyColumn(
            contentPadding = contentPadding,
        ) {
            rows.forEachIndexed { rowIndex, rowItems ->
                item(key = "ext-card-$rowIndex") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { ext ->
                            when (ext) {
                                is NovelExtension.Installed -> {
                                    ExtensionCard(
                                        modifier = Modifier.weight(1f),
                                        title = ext.name,
                                        lang = ext.lang.uppercase(),
                                        version = ext.versionName,
                                        iconDrawable = ext.icon,
                                        isInstalled = true,
                                        isObsolete = ext.isObsolete,
                                        supportsComments = ext.sources.any { it.supportsComments },
                                        onClick = { onInstallExtension(ext) },
                                    )
                                }
                                is NovelExtension.Available -> {
                                    ExtensionCard(
                                        modifier = Modifier.weight(1f),
                                        title = ext.name,
                                        lang = ext.lang.uppercase(),
                                        version = ext.versionName,
                                        iconUrl = ext.iconUrl,
                                        isInstalled = false,
                                        supportsComments = ext.sources.any { it.supportsComments },
                                        onClick = { onInstallExtension(ext) },
                                    )
                                }
                                is MangaExtension.Installed -> {
                                    ExtensionCard(
                                        modifier = Modifier.weight(1f),
                                        title = ext.name,
                                        lang = ext.lang.uppercase(),
                                        version = ext.versionName,
                                        iconDrawable = ext.icon,
                                        isInstalled = true,
                                        isObsolete = ext.isObsolete,
                                        onClick = { onInstallExtension(ext) },
                                    )
                                }
                                is MangaExtension.Available -> {
                                    ExtensionCard(
                                        modifier = Modifier.weight(1f),
                                        title = ext.name,
                                        lang = ext.lang.uppercase(),
                                        version = ext.versionName,
                                        iconUrl = ext.iconUrl,
                                        isInstalled = false,
                                        onClick = { onInstallExtension(ext) },
                                    )
                                }
                                is AnimeExtension.Installed -> {
                                    ExtensionCard(
                                        modifier = Modifier.weight(1f),
                                        title = ext.name,
                                        lang = ext.lang.uppercase(),
                                        version = ext.versionName,
                                        iconDrawable = ext.icon,
                                        isInstalled = true,
                                        isObsolete = ext.isObsolete,
                                        onClick = { onInstallExtension(ext) },
                                    )
                                }
                                is AnimeExtension.Available -> {
                                    ExtensionCard(
                                        modifier = Modifier.weight(1f),
                                        title = ext.name,
                                        lang = ext.lang.uppercase(),
                                        version = ext.versionName,
                                        iconUrl = ext.iconUrl,
                                        isInstalled = false,
                                        onClick = { onInstallExtension(ext) },
                                    )
                                }
                                else -> {}
                            }
                        }
                        repeat(cardColumns - rowItems.size) {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
