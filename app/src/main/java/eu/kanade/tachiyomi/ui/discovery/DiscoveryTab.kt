package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.MaterialTheme
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
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.LocalHostScaffoldContentPadding
import eu.kanade.presentation.components.globalOverflowActions
import eu.kanade.presentation.components.useSharedTopBarWithSearch
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.metadata.canonical.CanonicalCatalogScreenModel
import eu.kanade.tachiyomi.ui.metadata.cinemeta.CinemetaBrowseScreenModel
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import kotlinx.collections.immutable.toPersistentList
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active
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
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }

        // Keep the canonical catalog pointed at the active carrier's medium;
        // the shows carrier is served by the Cinemeta surface instead.
        LaunchedEffect(carrier) {
            if (carrier != Carrier.SHOWS) {
                canonicalSM.setMedium(carrier.medium)
            }
        }

        // Shared top bar — the filter action follows the library-tab
        // pattern (icon tints when facets are active) but only exists on
        // the shows carrier; manga/books keep their per-source filters.
        val showsFilter = state.filters[Carrier.SHOWS] ?: DiscoveryFilter()
        val actions = buildList {
            if (carrier == Carrier.SHOWS) {
                add(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_filter),
                        icon = Icons.Outlined.FilterList,
                        iconTint = if (showsFilter.isActive) MaterialTheme.colorScheme.active else null,
                        onClick = { screenModel.setFilterSheetVisible(true) },
                    ),
                )
            }
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
                    Carrier.MANGA, Carrier.BOOKS -> canonicalSM.search(query)
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
                // 15-06: the facet bar + filter sheet wrap the shows
                // catalog; an active facet set swaps the grid to TMDB
                // discover results. manga/books keep per-source filters.
                Carrier.SHOWS -> ShowsDiscoveryContent(
                    discoveryScreenModel = screenModel,
                    cinemetaScreenModel = cinemetaBrowseSM,
                    contentPadding = resolvedPadding,
                    libraryPreferences = libraryPreferences,
                )
                // 15-07: the manga/books carriers mount the ranked
                // catalog surface — sub-type pills (Manga|Comics,
                // Novels|Books) over the worker's /v1/works/browse feed
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
        }
    }
}
