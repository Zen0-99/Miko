package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.presentation.collection.visualName
import tachiyomi.domain.collection.model.Collection
import tachiyomi.presentation.core.components.material.TabText

@Composable
internal fun LibraryTabs(
    collections: List<Collection>,
    pagerState: PagerState,
    getNumberOfItemsForCollection: (Collection) -> Int?,
    onTabItemClick: (Int) -> Unit,
) {
    val currentPageIndex = pagerState.currentPage.coerceAtMost(collections.lastIndex)
    Column(
        modifier = Modifier.zIndex(1f),
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = currentPageIndex,
            edgePadding = 0.dp,
            // TODO: use default when width is fixed upstream
            // https://issuetracker.google.com/issues/242879624
            divider = {},
            indicator = {
                Box(
                    Modifier
                        .tabIndicatorLayout { measurable, constraints, tabPositions ->
                            val targetPos = tabPositions.getOrElse(currentPageIndex) { tabPositions.first() }
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
            },
        ) {
            collections.forEachIndexed { index, collection ->
                Tab(
                    selected = currentPageIndex == index,
                    onClick = { onTabItemClick(index) },
                    text = {
                        TabText(
                            text = collection.visualName,
                            badgeCount = getNumberOfItemsForCollection(collection),
                        )
                    },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider()
    }
}
