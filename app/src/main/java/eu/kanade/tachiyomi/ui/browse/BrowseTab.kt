package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.togetherWith
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
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
import eu.kanade.tachiyomi.ui.metadata.cinemeta.CinemetaBrowseScreen
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

enum class BrowseMode { BROWSE, DISCOVER, EXTENSIONS }

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
        var browseMode by remember { mutableStateOf(BrowseMode.BROWSE) }
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
        // When search is cancelled (query = null), we do NOT call
        // extSM.search(null) — doing so triggers a recomposition of the
        // extension list (which is being torn down) and causes icon
        // produceState to restart, briefly showing placeholder/broken icons.
        // The stale searchQuery in the SM is harmless: it's reset the next
        // time the user opens search (search("") is called first, then the
        // actual query).
        LaunchedEffect(searchQuery, browseMode, contentMode) {
            if (browseMode == BrowseMode.EXTENSIONS && searchQuery != null) {
                val query = searchQuery!!
                when (contentMode) {
                    ContentMode.ANIME -> animeExtSM.search(query)
                    ContentMode.MANGA -> mangaExtSM.search(query)
                    ContentMode.NOVEL -> {
                        novelExtSM.search(query)
                    }
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
            searchPlaceholderText = when (browseMode) {
                BrowseMode.EXTENSIONS -> stringResource(MR.strings.search_hint_extensions)
                BrowseMode.DISCOVER -> stringResource(MR.strings.search_hint_browse)
                BrowseMode.BROWSE -> stringResource(MR.strings.search_hint_browse)
            },
            searchQuery = searchQuery,
            onSearchQueryChange = { query ->
                searchQuery = query
                // Reset submitted flag while typing — Browse results only show
                // after enter. Extensions side shows results immediately.
                searchSubmitted = false
                if (query == null) {
                    browseMode = BrowseMode.BROWSE
                    searchSubmitted = false
                }
            },
            onSearch = { query ->
                if (query.isBlank()) return@useSharedTopBarWithSearch
                when (browseMode) {
                    BrowseMode.DISCOVER -> {
                        // Cinemeta search is handled by the screen model
                        searchSubmitted = true
                    }
                    BrowseMode.EXTENSIONS -> {
                        searchSubmitted = true
                    }
                    BrowseMode.BROWSE -> {
                        when (contentMode) {
                            ContentMode.ANIME -> navigator.push(GlobalAnimeSearchScreen(query))
                            ContentMode.MANGA -> navigator.push(GlobalMangaSearchScreen(query))
                            ContentMode.NOVEL -> navigator.push(GlobalNovelSearchScreen(query))
                        }
                        searchQuery = null
                        searchSubmitted = false
                    }
                }
            },
            searchPillContent = {
                BrowseSearchPill(
                    browseMode = browseMode,
                    onModeChange = { browseMode = it },
                )
            },
        )

        Scaffold(
            topBar = {},
            snackbarHost = {
                AchievementStyledSnackbarHost(hostState = snackbarHostState)
            },
        ) { padding ->
            val hostPadding = eu.kanade.presentation.components.LocalHostScaffoldContentPadding.current
            val hostTop = hostPadding?.calculateTopPadding() ?: androidx.compose.ui.unit.Dp.Hairline
            val hostBottom = hostPadding?.calculateBottomPadding() ?: androidx.compose.ui.unit.Dp.Hairline
            val resolvedPadding = androidx.compose.foundation.layout.PaddingValues(
                top = padding.calculateTopPadding() + hostTop,
                bottom = padding.calculateBottomPadding() + hostBottom,
            )

            // The source tab is ALWAYS composed underneath, even when search
            // is active. This keeps its icons alive (remembered bitmaps, GPU
            // textures intact) so that when search is closed (X pressed), the
            // source tab is simply revealed — no recomposition from scratch,
            // no icon re-decode, no placeholder flash. Search content is
            // overlaid on top with an opaque background to hide the source
            // tab beneath.
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Source tab — always mounted, never torn down by search.
                sourceTab.content(resolvedPadding, snackbarHostState)

                // Discover overlay — Cinemeta browse screen shown when Discover mode is active
                if (browseMode == BrowseMode.DISCOVER) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        CinemetaBrowseScreen().Content()
                    }
                }

                // Search overlay — only present when there is actual search
                // content to show. When typing on the Browse side (not yet
                // submitted), no overlay is shown and the source tab is
                // visible underneath (same behavior as before).
                val currentQuery = searchQuery
                val showSearchOverlay = currentQuery != null && (browseMode == BrowseMode.EXTENSIONS || searchSubmitted)
                if (showSearchOverlay) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        if (browseMode == BrowseMode.EXTENSIONS) {
                            // Extensions side: show results immediately as the
                            // user types — no submit needed.
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
                            // JS plugins are now merged into the novel extensions
                            // list (Not Installed section) via NovelExtensionsScreenModel.
                        } else {
                            // Browse side: source search results after submit.
                            BrowseSourceSearchResults(
                                contentMode = contentMode,
                                searchQuery = currentQuery ?: "",
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
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

/**
 * Combined pill toggle: Browse | Extensions.
 * A single rounded container with two segments — the selected segment gets
 * a filled pill background, the unselected one is transparent.
 * Only shown while search is active.
 */
@Composable
private fun BrowseSearchPill(
    browseMode: BrowseMode,
    onModeChange: (BrowseMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        stringResource(MR.strings.browse) to BrowseMode.BROWSE,
        stringResource(MR.strings.discover) to BrowseMode.DISCOVER,
        stringResource(MR.strings.label_extensions) to BrowseMode.EXTENSIONS,
    )
    val containerShape = RoundedCornerShape(999.dp)
    val segmentShape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(containerShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (label, mode) ->
            val isSelected = browseMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(segmentShape)
                    .then(
                        if (isSelected) {
                            Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onModeChange(mode) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
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
                val rowKey = rowItems.joinToString(",") { ext ->
                    when (ext) {
                        is AnimeExtension -> ext.pkgName
                        is MangaExtension -> ext.pkgName
                        is NovelExtension -> ext.pkgName
                        else -> ext.toString()
                    }
                }
                item(key = "ext-card-$rowKey") {
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
