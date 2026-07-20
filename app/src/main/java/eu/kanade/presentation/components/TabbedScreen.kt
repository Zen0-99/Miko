package eu.kanade.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TabbedScreen(
    titleRes: StringResource?,
    tabs: ImmutableList<TabContent>,
    modifier: Modifier = Modifier,
    state: PagerState = rememberPagerState { tabs.size },
    mangaSearchQuery: String? = null,
    onChangeMangaSearchQuery: (String?) -> Unit = {},
    scrollable: Boolean = false,
    animeSearchQuery: String? = null,
    onChangeAnimeSearchQuery: (String?) -> Unit = {},
    novelSearchQuery: String? = null,
    onChangeNovelSearchQuery: (String?) -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    onClickSettings: (() -> Unit)? = null,

    ) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Register with shared top bar — update title/actions when sub-tab changes
    if (titleRes != null) {
        val tab = tabs[state.currentPage]
        val allActions = if (onClickSettings != null) {
            (tab.actions + globalOverflowActions(onClickSettings = onClickSettings)).toImmutableList()
        } else {
            tab.actions
        }
        useSharedTopBar(
            title = stringResource(titleRes),
            actions = allActions,
            navigateUp = tab.navigateUp,
        )
    }

    Scaffold(
        topBar = {},
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 80.dp),
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            ),
        ) {
            // Hide the tab row when there's only one tab (mode-aware single-content mode).
            if (tabs.size > 1) {
                FlexibleTabRow(
                    scrollable = scrollable,
                    selectedTabIndex = state.currentPage,
                    pagerState = state,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = state.currentPage == index,
                            onClick = { scope.launch { state.animateScrollToPage(index) } },
                            text = {
                                TabText(
                                    text = stringResource(tab.titleRes),
                                    badgeCount = tab.badgeNumber,
                                )
                            },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = state,
                verticalAlignment = Alignment.Top,
            ) { page ->
                tabs[page].content(
                    PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                    snackbarHostState,
                )
            }
        }
    }
}

data class TabContent(
    val titleRes: StringResource,
    val badgeNumber: Int? = null,
    val searchEnabled: Boolean = false,
    val actions: ImmutableList<AppBar.AppBarAction> = persistentListOf(),
    val content: @Composable (contentPadding: PaddingValues, snackbarHostState: SnackbarHostState) -> Unit,
    val numberTitle: Int = 0,
    val cancelAction: () -> Unit = {},
    val navigateUp: (() -> Unit)? = null,
)

@Composable
private fun FlexibleTabRow(
    scrollable: Boolean,
    selectedTabIndex: Int,
    pagerState: PagerState? = null,
    block: @Composable () -> Unit,
) {
    return if (scrollable) {
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 13.dp,
            modifier = Modifier.zIndex(1f),
            indicator = if (pagerState != null) {
                { tabPositions ->
                    val targetPos = tabPositions.getOrElse(pagerState.currentPage) { tabPositions.first() }
                    val fraction = pagerState.currentPageOffsetFraction
                    val leftDp = targetPos.left + targetPos.width * fraction
                    val widthDp = targetPos.width
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .offset(x = leftDp)
                                .width(widthDp)
                                .height(2.dp)
                                .align(Alignment.BottomStart)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            } else {
                {}
            },
        ) {
            block()
        }
    } else {
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.zIndex(1f),
            indicator = if (pagerState != null) {
                {
                    Box(
                        Modifier
                            .tabIndicatorLayout { measurable, constraints, tabPositions ->
                                val targetPos = tabPositions.getOrElse(pagerState.currentPage) { tabPositions.first() }
                                val fraction = pagerState.currentPageOffsetFraction
                                val left = targetPos.left + targetPos.width * fraction
                                val width = targetPos.width
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = width.roundToPx(),
                                        maxWidth = width.roundToPx(),
                                    ),
                                )
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.place(left.roundToPx(), constraints.maxHeight - placeable.height)
                                }
                            }
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            } else {
                {}
            },
        ) {
            block()
        }
    }
}
