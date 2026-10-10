package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.presentation.components.LocalHostScaffoldContentPadding
import eu.kanade.presentation.components.globalOverflowActions
import eu.kanade.presentation.components.useSharedTopBarWithSearch
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType
import eu.kanade.tachiyomi.ui.metadata.canonical.CanonicalCatalogScreenModel
import eu.kanade.tachiyomi.ui.metadata.cinemeta.CinemetaBrowseScreenModel
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import kotlinx.collections.immutable.toPersistentList
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object DiscoveryTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 4u,
                title = stringResource(MR.strings.discovery),
                icon = rememberVectorPainter(Icons.Outlined.Explore),
            )
        }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val contentMode by uiPreferences.contentMode().collectAsState()
        val carrier = Carrier.fromContentMode(contentMode)

        val screenModel = rememberScreenModel { DiscoveryScreenModel() }
        val state by screenModel.state.collectAsState()

        // Catalog screen models — hoisted at the tab so shared-top-bar search
        // can reach them and tab/content-mode switches keep their cached pages.
        val cinemetaBrowseSM = rememberScreenModel { CinemetaBrowseScreenModel() }
        val canonicalSM = rememberScreenModel { CanonicalCatalogScreenModel() }
        val cinemetaState by cinemetaBrowseSM.state.collectAsState()
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }

        // Keep the canonical catalog pointed at the active carrier's medium;
        // the shows carrier is served by the Cinemeta surface instead.
        LaunchedEffect(carrier) {
            if (carrier != Carrier.SHOWS) {
                canonicalSM.setMedium(carrier.medium)
            }
            // A submitted search follows the carrier — switching pills
            // with a live query runs it on the newly-visible surface
            // instead of silently dropping back to the idle catalog.
            // Each surface keeps its own results; only surfaces whose
            // query doesn't match re-run.
            val discoveryState = screenModel.state.value
            val query = discoveryState.searchQuery
            if (query != null && discoveryState.searchSubmitted) {
                when (carrier) {
                    Carrier.SHOWS -> {
                        if (cinemetaBrowseSM.state.value.searchQuery != query) {
                            cinemetaBrowseSM.search(query)
                        }
                    }
                    else -> {
                        // Scoped to the carrier's committed pill — the
                        // composable effect would otherwise re-scope a
                        // moment later and double-fetch.
                        val kinds = searchKindsCsvFor(
                            carrier,
                            discoveryState.subTypeSelections[carrier] ?: 0,
                        )
                        if (canonicalSM.state.value.searchQuery != query ||
                            canonicalSM.state.value.searchKindsCsv != kinds
                        ) {
                            canonicalSM.search(query, kinds)
                        }
                    }
                }
            }
        }

        // Detail screens dispose this tab's composition — on pop-back
        // this refires and freshens bindings + covers changed in detail.
        LaunchedEffect(Unit) {
            canonicalSM.onSurfaceShown()
        }

        // Shared top bar — sub-type + filtering live in the Type/Filter
        // sheet opened from the content's DiscoveryTypeRow; the bar only
        // carries the global overflow.
        val actions = buildList {
            addAll(
                globalOverflowActions(
                    onClickSettings = { navigator.push(SettingsScreen()) },
                ),
            )
        }.toPersistentList()

        useSharedTopBarWithSearch(
            title = stringResource(MR.strings.discovery),
            actions = actions,
            searchEnabled = true,
            searchPlaceholderText = stringResource(MR.strings.search_hint_browse),
            searchQuery = state.searchQuery,
            onSearchQueryChange = { query ->
                screenModel.setSearchQuery(query)
                if (query == null) {
                    // Search closed (X pressed / tab switch) — restore the
                    // idle catalogs.
                    cinemetaBrowseSM.clearSearch()
                    canonicalSM.clearSearch()
                }
            },
            onSearch = { query ->
                if (query.isBlank()) return@useSharedTopBarWithSearch
                when (carrier) {
                    Carrier.SHOWS -> cinemetaBrowseSM.search(query)
                    Carrier.MANGA, Carrier.BOOKS -> canonicalSM.search(
                        query,
                        searchKindsCsvFor(
                            carrier,
                            screenModel.state.value.subTypeSelections[carrier] ?: 0,
                        ),
                    )
                }
                screenModel.setSearchSubmitted(true)
            },
        )

        Scaffold(
            topBar = {},
        ) { padding ->
            val hostPadding = LocalHostScaffoldContentPadding.current
            val hostTop = hostPadding?.calculateTopPadding() ?: 0.dp
            val hostBottom = hostPadding?.calculateBottomPadding() ?: 0.dp
            val resolvedPadding = PaddingValues(
                top = padding.calculateTopPadding() + hostTop,
                bottom = padding.calculateBottomPadding() + hostBottom,
            )

            // The catalog surface is mounted directly — submitting a search
            // swaps its contents to results inline; closing the search field
            // restores the idle catalog (same semantics as the old Discover
            // overlay in BrowseTab).
            when (carrier) {
                // 15-06: the type row + facet bar wrap the shows
                // catalog; an active facet set swaps the grid to TMDB
                // discover results.
                Carrier.SHOWS -> ShowsDiscoveryContent(
                    discoveryScreenModel = screenModel,
                    cinemetaScreenModel = cinemetaBrowseSM,
                    contentPadding = resolvedPadding,
                    libraryPreferences = libraryPreferences,
                )
                // 15-07: the manga/books carriers mount the ranked
                // catalog surface — the worker's /v1/works/browse feed
                // or OpenLibrary trending; search swaps to the
                // canonical map results inline.
                Carrier.MANGA, Carrier.BOOKS -> CatalogBrowseContent(
                    carrier = carrier,
                    discoveryScreenModel = screenModel,
                    screenModel = canonicalSM,
                    contentPadding = resolvedPadding,
                    libraryPreferences = libraryPreferences,
                )
            }

            // The shared Type/Filter sheet — same component for every
            // carrier; only the type options and filter sections vary.
            if (state.showTypeFilterSheet) {
                val typeOptions = discoveryTypeLabelsFor(carrier)
                val selectedTypeIndex = when (carrier) {
                    Carrier.SHOWS -> if (cinemetaState.type == "movie") 0 else 1
                    else -> (state.subTypeSelections[carrier] ?: 0)
                        .coerceIn(0, typeOptions.lastIndex)
                }
                DiscoveryTypeFilterSheet(
                    carrier = carrier,
                    typeOptions = typeOptions,
                    selectedTypeIndex = selectedTypeIndex,
                    onTypeSelected = { index ->
                        when (carrier) {
                            Carrier.SHOWS -> cinemetaBrowseSM.changeType(
                                if (index == 0) "movie" else "series",
                            )
                            else -> screenModel.selectSubType(carrier, index)
                        }
                    },
                    filter = state.filters[Carrier.SHOWS] ?: DiscoveryFilter(),
                    mediaType = if (cinemetaState.type == "movie") {
                        MediaType.MOVIE
                    } else {
                        MediaType.TV
                    },
                    onFilterChange = { screenModel.setFilter(Carrier.SHOWS, it) },
                    catalogSort = screenModel.browseSortFor(carrier),
                    onCatalogSortChange = { screenModel.setBrowseSort(carrier, it) },
                    catalogFilter = screenModel.catalogFilterFor(carrier),
                    onCatalogFilterChange = { screenModel.setCatalogFilter(carrier, it) },
                    catalogGenres = state.catalogGenres[carrier.medium].orEmpty(),
                    initialPage = state.typeFilterSheetPage,
                    onDismiss = { screenModel.setTypeFilterSheetVisible(false) },
                )
            }
        }
    }
}
