package eu.kanade.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import eu.kanade.presentation.entries.manga.carrierSheetContainerColor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource

object TabbedDialogPaddings {
    val Horizontal = 24.dp
    val Vertical = 8.dp
}

@Composable
fun TabbedDialog(
    onDismissRequest: () -> Unit,
    tabTitles: ImmutableList<String>,
    modifier: Modifier = Modifier,
    tabOverflowMenuContent: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
    onOverflowMenuClicked: (() -> Unit)? = null,
    overflowIcon: ImageVector? = null,
    pagerState: PagerState = rememberPagerState { tabTitles.size },
    fillHeight: Boolean = false,
    /**
     * Detail-theme accent (canonical entries carry their own palette):
     * tints the sheet container like the carrier sheets and rebinds
     * `primary` inside so tab indicator, radios, checkboxes and sort
     * arrows read as the entry's accent, not the global theme.
     */
    accentColor: Color? = null,
    /**
     * Caps the sheet CONTENT's height to this fraction of the sheet
     * window. Height must be constrained inside the content slot —
     * ModalBottomSheet computes its Expanded anchor from the surface's
     * measure constraints, so a height() on the sheet's own [modifier]
     * collapses the anchor to 0 and the sheet pins to the screen top.
     */
    heightFraction: Float? = null,
    content: @Composable (Int) -> Unit,
) {
    AdaptiveSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        containerColor = if (accentColor != null) {
            carrierSheetContainerColor(accentColor)
        } else {
            Color.Unspecified
        },
    ) {
        val scope = rememberCoroutineScope()
        val themedInner: @Composable () -> Unit = {
            BoxWithConstraints {
                val capped = heightFraction?.let { Modifier.height(maxHeight * it) }
                    ?: Modifier
                Column(modifier = capped) {
                    Row {
                        PrimaryTabRow(
                            modifier = Modifier.weight(1f),
                            selectedTabIndex = pagerState.currentPage,
                            containerColor = if (accentColor != null) {
                                // The tinted sheet surface shows through —
                                // an untinted strip would read as a grey band.
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            divider = {},
                            indicator = {
                                Box(
                                    Modifier
                                        .tabIndicatorLayout { measurable, constraints, tabPositions ->
                                            val targetPos = tabPositions.getOrElse(pagerState.currentPage) {
                                                tabPositions.first()
                                            }
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
                                                placeable.place(
                                                    left.roundToPx(),
                                                    constraints.maxHeight - placeable.height,
                                                )
                                            }
                                        }
                                        .height(2.dp)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            },
                        ) {
                            tabTitles.fastForEachIndexed { index, tab ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                    text = { TabText(text = tab) },
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        MoreMenu(onOverflowMenuClicked, tabOverflowMenuContent, overflowIcon)
                    }
                    HorizontalDivider()

                    HorizontalPager(
                        modifier = if (fillHeight) Modifier.weight(1f) else Modifier.animateContentSize(),
                        state = pagerState,
                        verticalAlignment = Alignment.Top,
                        pageContent = { page -> content(page) },
                    )
                }
            }
        }
        if (accentColor != null) {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(primary = accentColor),
            ) {
                themedInner()
            }
        } else {
            themedInner()
        }
    }
}

@Composable
private fun MoreMenu(
    onClickIcon: (() -> Unit)?,
    content: @Composable (ColumnScope.(() -> Unit) -> Unit)?,
    overflowIcon: ImageVector? = null,
) {
    if (onClickIcon == null && content == null) return

    var expanded by remember { mutableStateOf(false) }
    val onClick = onClickIcon ?: { expanded = true }

    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = overflowIcon ?: Icons.Default.MoreVert,
                contentDescription = stringResource(MR.strings.label_more),
            )
        }
        if (onClickIcon == null) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                content!! { expanded = false }
            }
        }
    }
}
