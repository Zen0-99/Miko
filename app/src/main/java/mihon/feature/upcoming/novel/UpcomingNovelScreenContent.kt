package mihon.feature.upcoming.novel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.util.isTabletUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.launch
import mihon.feature.upcoming.components.calendar.Calendar
import mihon.feature.upcoming.novel.components.UpcomingItem
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun UpcomingNovelScreenContent(
    state: UpcomingNovelScreenModel.State,
    setSelectedYearMonth: (YearMonth) -> Unit,
    onClickUpcoming: (novel: Novel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val onClickDay: (LocalDate, Int) -> Unit = { date, offset ->
        state.headerIndexes[date]?.let {
            scope.launch {
                listState.animateScrollToItem(it + offset)
            }
        }
    }
    Scaffold(
        modifier = modifier,
    ) { paddingValues ->
        if (isTabletUi()) {
            UpcomingNovelScreenLargeImpl(
                listState = listState,
                items = state.items,
                events = state.events,
                paddingValues = paddingValues,
                selectedYearMonth = state.selectedYearMonth,
                setSelectedYearMonth = setSelectedYearMonth,
                onClickDay = { onClickDay(it, 0) },
                onClickUpcoming = onClickUpcoming,
            )
        } else {
            UpcomingNovelScreenSmallImpl(
                listState = listState,
                items = state.items,
                events = state.events,
                paddingValues = paddingValues,
                selectedYearMonth = state.selectedYearMonth,
                setSelectedYearMonth = setSelectedYearMonth,
                onClickDay = { onClickDay(it, 1) },
                onClickUpcoming = onClickUpcoming,
            )
        }
    }
}

@Composable
private fun DateHeading(
    date: LocalDate,
    novelCount: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = relativeDateText(date),
            modifier = Modifier
                .padding(MaterialTheme.padding.small)
                .padding(start = MaterialTheme.padding.small),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Badge(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text("$novelCount")
        }
    }
}

@Composable
private fun UpcomingNovelScreenSmallImpl(
    listState: LazyListState,
    items: ImmutableList<UpcomingNovelUIModel>,
    events: ImmutableMap<LocalDate, Int>,
    paddingValues: PaddingValues,
    selectedYearMonth: YearMonth,
    setSelectedYearMonth: (YearMonth) -> Unit,
    onClickDay: (LocalDate) -> Unit,
    onClickUpcoming: (novel: Novel) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = paddingValues,
        state = listState,
    ) {
        item(key = "upcoming-calendar") {
            Calendar(
                selectedYearMonth = selectedYearMonth,
                events = events,
                setSelectedYearMonth = setSelectedYearMonth,
                onClickDay = onClickDay,
            )
        }
        items(
            items = items,
            key = { "upcoming-${it.hashCode()}" },
            contentType = {
                when (it) {
                    is UpcomingNovelUIModel.Header -> "header"
                    is UpcomingNovelUIModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is UpcomingNovelUIModel.Item -> {
                    UpcomingItem(
                        upcoming = item.novel,
                        onClick = { onClickUpcoming(item.novel) },
                    )
                }
                is UpcomingNovelUIModel.Header -> {
                    DateHeading(
                        date = item.date,
                        novelCount = item.novelCount,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingNovelScreenLargeImpl(
    listState: LazyListState,
    items: ImmutableList<UpcomingNovelUIModel>,
    events: ImmutableMap<LocalDate, Int>,
    paddingValues: PaddingValues,
    selectedYearMonth: YearMonth,
    setSelectedYearMonth: (YearMonth) -> Unit,
    onClickDay: (LocalDate) -> Unit,
    onClickUpcoming: (novel: Novel) -> Unit,
) {
    TwoPanelBox(
        modifier = Modifier.padding(paddingValues),
        startContent = {
            Calendar(
                selectedYearMonth = selectedYearMonth,
                events = events,
                setSelectedYearMonth = setSelectedYearMonth,
                onClickDay = onClickDay,
            )
        },
        endContent = {
            FastScrollLazyColumn(state = listState) {
                items(
                    items = items,
                    key = { "upcoming-${it.hashCode()}" },
                    contentType = {
                        when (it) {
                            is UpcomingNovelUIModel.Header -> "header"
                            is UpcomingNovelUIModel.Item -> "item"
                        }
                    },
                ) { item ->
                    when (item) {
                        is UpcomingNovelUIModel.Item -> {
                            UpcomingItem(
                                upcoming = item.novel,
                                onClick = { onClickUpcoming(item.novel) },
                            )
                        }
                        is UpcomingNovelUIModel.Header -> {
                            DateHeading(
                                date = item.date,
                                novelCount = item.novelCount,
                            )
                        }
                    }
                }
            }
        },
    )
}
