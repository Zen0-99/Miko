package miko.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import miko.ui.carrier.BrowseCarrier
import miko.ui.catalog.CatalogRow
import miko.ui.catalog.Feed
import miko.ui.catalog.historyRowTitle
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import kotlin.time.Clock

/**
 * Home tab — Android's HomeHub scoped to what the shared graph provides:
 * a time greeting, a hero "continue" card (latest history row), and a
 * horizontal recents rail. Carrier chips match Sources/Library/Updates.
 */
@Composable
fun HomeScreen(
    model: HomeScreenModel,
    carrierFlow: StateFlow<BrowseCarrier>,
    onCarrierChange: (BrowseCarrier) -> Unit,
    onResume: (sourceId: Long, entryUrl: String, itemUrl: String) -> Unit,
    onOpenEntry: (sourceId: Long, entryUrl: String, entryTitle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val carrier by carrierFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val hour = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val greeting = when (model.greeting(hour)) {
        HomeScreenModel.Greeting.Morning -> AYMR.strings.home_greeting_morning
        HomeScreenModel.Greeting.Afternoon -> AYMR.strings.home_greeting_afternoon
        HomeScreenModel.Greeting.Evening -> AYMR.strings.home_greeting_evening
        HomeScreenModel.Greeting.Night -> AYMR.strings.home_greeting_night
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(greeting),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Carrier chips — same idiom as the other tabs.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                BrowseCarrier.SHOWS to AYMR.strings.carrier_shows,
                BrowseCarrier.MANGA to AYMR.strings.carrier_manga,
                BrowseCarrier.BOOKS to AYMR.strings.carrier_books,
            ).forEach { (option, labelRes) ->
                FilterChip(
                    selected = carrier == option,
                    onClick = { onCarrierChange(option) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }

        when {
            !state.initialized -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            ) {
                LoadingScreen()
            }
            else -> {
                // Android's hero is a swipeable carousel over the most
                // recently consumed entries — same card, paged.
                val heroes = state.recents.take(HERO_PAGE_LIMIT)
                if (heroes.isNotEmpty()) {
                    val pagerState = rememberPagerState { heroes.size }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                    ) { page ->
                        val hero = heroes[page]
                        HeroCard(
                            item = hero,
                            carrier = carrier,
                            onResume = {
                                scope.launch {
                                    model.resolveTarget(hero.entryId, hero.itemId)?.let { t ->
                                        onResume(t.sourceId, t.entryUrl, t.itemUrl)
                                    }
                                }
                            },
                            onOpenEntry = {
                                scope.launch {
                                    model.resolveTarget(hero.entryId, hero.itemId)?.let { t ->
                                        onOpenEntry(t.sourceId, t.entryUrl, hero.entryTitle)
                                    }
                                }
                            },
                        )
                    }
                    if (heroes.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            repeat(heroes.size) { page ->
                                val selected = pagerState.currentPage == page
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (selected) 7.dp else 5.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme
                                                    .onSurfaceVariant.copy(alpha = 0.4f)
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }

                // Catalog rows — the persisted spec rows; History specs
                // are the recents rows (one per medium), other feeds
                // resolve through the resolver. A totally empty home
                // shows the no-history placeholder instead.
                val visibleSpecs = state.catalogSpecs.filter { it.visible }
                val nothingAnywhere = state.recents.isEmpty() && visibleSpecs.all { spec ->
                    spec.feed is Feed.History &&
                        (state.catalogRows[spec.id]?.items?.isEmpty() != false)
                }
                if (nothingAnywhere) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                    ) {
                        EmptyScreen(
                            stringRes = AYMR.strings.information_no_home_history,
                        )
                    }
                }
                visibleSpecs.forEach { spec ->
                    val rowState = state.catalogRows[spec.id]
                    val title = if (spec.feed is Feed.History) {
                        historyRowTitle(spec.medium)
                    } else {
                        model.catalogRowTitle(spec)
                    }
                    CatalogRow(
                        spec = spec,
                        rowState = rowState,
                        title = title,
                        openItem = { item ->
                            scope.launch {
                                model.openRowItem(item)?.let { t ->
                                    if (t.itemUrl.isEmpty()) {
                                        onOpenEntry(t.sourceId, t.entryUrl, item.title)
                                    } else {
                                        onResume(t.sourceId, t.entryUrl, t.itemUrl)
                                    }
                                }
                            }
                        },
                    )
                    LaunchedEffect(spec.id, model.rowFetchKey(spec)) {
                        model.onRowVisible(spec)
                    }
                }
            }
        }
    }
}

/** Pages the hero carousel offers — Android pages its whole `heroes`
 *  list; we cap at the same slice the recents feed keeps fresh. */
private const val HERO_PAGE_LIMIT = 5

/**
 * Hero "continue" card — cover backdrop with a bottom scrim, entry title,
 * last-opened item name, and a Resume button (Android's home_hero_resume).
 */
@Composable
private fun HeroCard(
    item: HomeScreenModel.RecentItem,
    carrier: BrowseCarrier,
    onResume: () -> Unit,
    onOpenEntry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onOpenEntry, onLongClick = onResume),
    ) {
        AsyncImage(
            model = item.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color(0xCC000000),
                    ),
                )
                .padding(12.dp),
        ) {
            Column {
                Text(
                    text = stringResource(
                        if (carrier == BrowseCarrier.SHOWS) {
                            AYMR.strings.home_hero_continue_watching
                        } else {
                            AYMR.strings.home_hero_continue_reading
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Text(
                    text = item.entryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.itemName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = onResume,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(AYMR.strings.home_hero_resume, item.itemName))
                }
            }
        }
    }
}
