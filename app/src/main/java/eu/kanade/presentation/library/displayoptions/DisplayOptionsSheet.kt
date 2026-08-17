package eu.kanade.presentation.library.displayoptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Display options sheet with 3 tabs: Display, Badges, Categories.
 * Replaces the old settings dialog for the Display button.
 * Works across manga/anime/novel by accepting LibraryPreferences directly.
 *
 * The tab indicator follows the pager's swipe position (exact same logic
 * as the novel reader settings bottom sheet), and the content area has a
 * fixed height to prevent resize on section change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayOptionsSheet(
    libraryPreferences: LibraryPreferences,
    libraryType: LibraryType = LibraryType.MANGA,
    onDismissRequest: () -> Unit,
    onClickReadingOrders: () -> Unit = {},
    currentDisplayMode: LibraryDisplayMode = LibraryDisplayMode.CompactGrid,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val configuration = LocalConfiguration.current
    // Fixed height — prevents resize on page change (matches novel reader approach)
    val sheetHeight = configuration.screenHeightDp.dp * 0.35f

    val tabTitles = persistentListOf(
        stringResource(MR.strings.action_display),
        stringResource(MR.strings.badges),
        stringResource(MR.strings.collections),
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier,
    ) {
        // TabRow with swipe-following indicator — exact same logic as novel reader
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            indicator = { tabPositions ->
                val targetPos = tabPositions[pagerState.currentPage]
                val fraction = pagerState.currentPageOffsetFraction
                val leftDp = targetPos.left + targetPos.width * fraction
                val widthDp = targetPos.width
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .offset(x = leftDp)
                            .width(widthDp)
                            .height(2.dp)
                            .align(Alignment.BottomStart)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            },
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title) },
                )
            }
        }

        // Fixed-height pager — content top-aligned, no resize on page change
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .padding(top = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (page) {
                    0 -> DisplayTab(
                        libraryPreferences = libraryPreferences,
                        libraryType = libraryType,
                        onClickReadingOrders = onClickReadingOrders,
                    )
                    1 -> BadgesTab(libraryPreferences = libraryPreferences)
                    2 -> CategoriesTab(
                        libraryPreferences = libraryPreferences,
                        currentDisplayMode = currentDisplayMode,
                    )
                }
            }
        }
    }
}
