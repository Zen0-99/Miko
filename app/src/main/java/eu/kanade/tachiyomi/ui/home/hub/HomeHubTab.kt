package eu.kanade.tachiyomi.ui.home.hub

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.globalOverflowActions
import eu.kanade.presentation.components.useSharedTopBar
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.home.catalog.CatalogBuilderSheet
import eu.kanade.tachiyomi.ui.home.catalog.CatalogRow
import eu.kanade.tachiyomi.ui.home.catalog.ManageCatalogsSheet
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

data object HomeHubTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_home_enter)
            return TabOptions(
                index = 0u,
                title = stringResource(AYMR.strings.label_home),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { HomeHubScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()

        HomeHubContent(state = state, screenModel = screenModel, navigator = navigator)

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

// --- Main Content ---

@Composable
private fun HomeHubContent(
    state: HomeHubState,
    screenModel: HomeHubScreenModel,
    navigator: cafe.adriel.voyager.navigator.Navigator,
) {
    val listState = rememberLazyListState()
    val tabNavigator = LocalTabNavigator.current
    val scope = rememberCoroutineScope()

    // Clear selection mode when navigating away from Home tab
    LaunchedEffect(tabNavigator.current) {
        if (tabNavigator.current !is HomeHubTab && state.selectionMode) {
            screenModel.clearSelection()
        }
    }

    // Register with shared top bar — use selection-mode actions when in selection mode
    if (state.selectionMode) {
        useSharedTopBar(
            title = "${state.selection.size}",
            actions = persistentListOf(
                AppBar.Action(
                    title = "Delete",
                    icon = Icons.Outlined.Delete,
                    onClick = { screenModel.deleteSelectedItems() },
                ),
            ),
            navigateUp = screenModel::clearSelection,
        )
    } else {
        useSharedTopBar(
            title = stringResource(AYMR.strings.label_home),
            actions = globalOverflowActions(
                onClickSettings = { navigator.push(SettingsScreen()) },
                onManageCatalogs = { screenModel.setDialog(HomeHubDialog.ManageCatalogs) },
            ),
        )
    }

    // Catalog sheets host before the welcome early-return — a fresh user
    // must be able to build their first row on an empty home.
    when (val dialog = state.dialog) {
        HomeHubDialog.ManageCatalogs -> ManageCatalogsSheet(
            specs = state.catalogSpecs,
            currentMedium = Carrier.fromContentMode(state.currentMode).medium,
            resolveTitle = { screenModel.catalogRowTitle(it) },
            onReorder = screenModel::reorderCatalogSpecs,
            onSetVisible = screenModel::setCatalogSpecVisible,
            onEdit = { screenModel.setDialog(HomeHubDialog.BuildCatalog(it)) },
            onDelete = screenModel::deleteCatalogSpec,
            onMediumPicked = screenModel::activateCatalogMedium,
            onAddNew = { medium ->
                screenModel.setDialog(HomeHubDialog.BuildCatalog(null, medium))
            },
            onDismissRequest = { screenModel.setDialog(null) },
        )
        is HomeHubDialog.BuildCatalog -> CatalogBuilderSheet(
            existing = dialog.existing,
            lockedMedium = dialog.lockedMedium,
            loadFeedOptions = screenModel::catalogFeedOptions,
            loadWatchProviders = screenModel::catalogWatchProviders,
            loadExtensionGenreOptions = screenModel::catalogExtensionGenreOptions,
            loadLibraryGenreOptions = screenModel::catalogLibraryGenreOptions,
            onMediumPicked = screenModel::activateCatalogMedium,
            onCommit = screenModel::upsertCatalogSpec,
            onDismissRequest = { screenModel.setDialog(null) },
        )
        null -> Unit
    }

    if (state.isEmpty) {
        Scaffold(
            topBar = {},
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(AYMR.strings.home_welcome),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(AYMR.strings.home_welcome_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { tabNavigator.current = BrowseTab }) {
                    Text(stringResource(AYMR.strings.home_browse_sources))
                }
            }
        }
        return
    }

    // Overscroll stretch — the same pullToRefresh mechanism the entry
    // detail screens use: post-scroll leftover deltas feed the state
    // whenever the list is pinned at its top edge, no matter where on
    // the page the drag started. Nuvio-exact: the hero itself grows
    // under the pull (its top edge never leaves the screen top, so no
    // background strip can open) while the image zooms inside it —
    // see HomeHubHeroSection.
    val pullState = rememberPullToRefreshState()
    val overscrollThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    val overscrollPx: () -> Float = { pullState.distanceFraction * overscrollThresholdPx }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                isRefreshing = false,
                state = pullState,
                enabled = true,
                onRefresh = {},
            ),
    ) {
        Scaffold(
            topBar = {},
        ) { padding ->
            val hostPadding = eu.kanade.presentation.components.LocalHostScaffoldContentPadding.current
            val hostBottomPadding = hostPadding?.calculateBottomPadding() ?: 0.dp

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    bottom = padding.calculateBottomPadding() + hostBottomPadding + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // --- Hero — full-bleed Nuvio-style carousel over the
                // recently consumed entries (see HomeHubHeroSection).
                // The list has no top contentPadding, so page one paints
                // from the screen's top edge.
                item(key = "hero") {
                    if (state.heroes.isNotEmpty()) {
                        HomeHubHeroSection(
                            items = state.heroes,
                            listState = listState,
                            overscrollPx = overscrollPx,
                        )
                    } else {
                        // Cold-load slab — the hero's own full-bleed
                        // geometry (screen-top edge, bottom-rounded,
                        // overscroll growth) in a muted surface tone, so
                        // the swap to live art lands without a jump.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(
                                    LocalConfiguration.current.screenHeightDp.dp * 0.72f +
                                        with(LocalDensity.current) {
                                            (overscrollPx() * 0.5f).toDp()
                                        },
                                )
                                .clip(
                                    RoundedCornerShape(
                                        bottomStart = 28.dp,
                                        bottomEnd = 28.dp,
                                    ),
                                )
                                .background(MaterialTheme.colorScheme.background),
                        )
                    }
                }

                // --- Catalog rows (Phase 18) — every row on home is a
                // spec now: the recents rows are seeded `Feed.History`
                // specs, user rows append after them, all filtered to
                // the active medium and fetched lazily on visibility. ---
                val modeMedium = Carrier.fromContentMode(state.currentMode).medium
                items(
                    state.catalogSpecs.filter { it.visible && it.medium == modeMedium },
                    key = { "catalog_row_" + it.id },
                    contentType = { "catalog_row" },
                ) { spec ->
                    CatalogRow(
                        spec = spec,
                        rowState = state.catalogRows[spec.id],
                        resolveTitle = { screenModel.catalogRowTitle(it) },
                        openItem = screenModel::openRowItem,
                        onItemRendered = { screenModel.onCatalogItemRendered(it) },
                    )
                    // Composition IS the visibility signal — spec-keyed so
                    // content edits refetch while reorder/visibility don't.
                    LaunchedEffect(spec) { screenModel.onCatalogRowVisible(spec.id) }
                }
            }
        }
    }
}
