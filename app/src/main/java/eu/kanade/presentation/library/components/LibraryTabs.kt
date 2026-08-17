package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import eu.kanade.presentation.collection.visualName
import tachiyomi.domain.collection.model.Collection
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun LibraryTabs(
    collections: List<Collection>,
    pagerState: PagerState,
    getNumberOfItemsForCollection: (Collection) -> Int?,
    onTabItemClick: (Int) -> Unit,
    sortLabel: String? = null,
    sortDescending: Boolean? = null,
    onSortClick: () -> Unit = {},
) {
    val currentPageIndex = pagerState.currentPage.coerceAtMost(collections.lastIndex)
    Column(
        modifier = Modifier.zIndex(1f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = currentPageIndex,
                edgePadding = 0.dp,
                modifier = Modifier.weight(1f),
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
                                fontSize = 16.sp,
                            )
                        },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Sort chip on the right of the tab row
            if (sortLabel != null) {
                Row(
                    modifier = Modifier
                        .clickable(onClick = onSortClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (sortDescending != null) {
                        Icon(
                            imageVector = if (sortDescending) Icons.Outlined.ArrowDownward
                                          else Icons.Outlined.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = sortLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HorizontalDivider()
    }
}
