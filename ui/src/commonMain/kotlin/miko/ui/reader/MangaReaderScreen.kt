package miko.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import miko.ui.components.SettingDropdown
import miko.ui.components.SettingSwitch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Manga reader — reading mode follows Android's `ReadingMode`: paged LTR /
 * RTL / vertical via [HorizontalPager]/[VerticalPager], or the long-strip
 * scroll for WEBTOON/CONTINUOUS_VERTICAL. Chapters mark themselves read on
 * open; prev/next + the chapter list sit in the bottom bar.
 */
@Composable
fun MangaReaderScreen(
    model: MangaReaderScreenModel,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingScreen(modifier = Modifier.weight(1f))
            state.error != null -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { model.openChapter(state.chapterUrl) }) {
                    Text(stringResource(MR.strings.action_retry))
                }
            }
            else -> PageViewer(
                state = state,
                onPageChange = model::savePageProgress,
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = model::prevChapter,
                enabled = state.hasPrev,
            ) {
                Text(stringResource(MR.strings.action_previous_chapter))
            }
            IconButton(onClick = model::openChapterSheet) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = stringResource(MR.strings.chapters),
                )
            }
            IconButton(onClick = model::openSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(MR.strings.pref_category_reading_mode),
                )
            }
            TextButton(
                onClick = model::nextChapter,
                enabled = state.hasNext,
            ) {
                Text(stringResource(MR.strings.action_next_chapter))
            }
        }
    }

    if (state.showChapters) {
        ChapterListSheet(
            chapters = state.chapters,
            currentUrl = state.chapterUrl,
            onDismiss = model::closeChapterSheet,
            onSelect = model::openChapter,
        )
    }

    if (state.showSettings) {
        ReaderSettingsSheet(
            state = state,
            onModeSelect = model::setReadingMode,
            onPageNumberChange = model::setShowPageNumber,
            onDismiss = model::closeSettings,
        )
    }
}

/** Dispatches to the paged or long-strip viewer per the resolved mode. */
@Composable
private fun PageViewer(
    state: MangaReaderScreenModel.State,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.readingMode) {
        MangaReadingMode.LEFT_TO_RIGHT,
        MangaReadingMode.RIGHT_TO_LEFT,
        MangaReadingMode.VERTICAL,
        -> PagedViewer(state, onPageChange, modifier)
        else -> WebtoonViewer(state, onPageChange, modifier)
    }
}

/**
 * Android's *PagerViewer: one page per screen, swipes advance. RTL renders
 * the same page order with a reversed pager so swiping left moves forward.
 */
@Composable
private fun PagedViewer(
    state: MangaReaderScreenModel.State,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Reset page position whenever the chapter changes; lastPageRead
    // seeds the resume page (Android's preserveReadingPosition).
    key(state.chapterUrl) {
        val pagerState = rememberPagerState(
            initialPage = state.initialPage.coerceIn(0, state.pages.lastIndex.coerceAtLeast(0)),
            pageCount = { state.pages.size },
        )
        val scope = rememberCoroutineScope()
        LaunchedEffect(pagerState.currentPage) {
            onPageChange(pagerState.currentPage)
        }
        val rtl = state.readingMode == MangaReadingMode.RIGHT_TO_LEFT
        Box(
            modifier = modifier
                .fillMaxSize()
                // Android's tap zones: outer thirds flip pages (mirrored in
                // RTL); the center third is a no-op until an app-bar toggle
                // lands. Swipes still go through — taps aren't consumed.
                .pointerInput(state.readingMode) {
                    detectTapGestures { offset ->
                        val target = if (state.readingMode == MangaReadingMode.VERTICAL) {
                            when {
                                offset.y < size.height / 3 -> -1
                                offset.y > size.height * 2 / 3 -> 1
                                else -> 0
                            }
                        } else {
                            val nextEdge = if (rtl) {
                                offset.x < size.width / 3
                            } else {
                                offset.x > size.width * 2 / 3
                            }
                            val prevEdge = if (rtl) {
                                offset.x > size.width * 2 / 3
                            } else {
                                offset.x < size.width / 3
                            }
                            when {
                                nextEdge -> 1
                                prevEdge -> -1
                                else -> 0
                            }
                        }
                        if (target != 0) {
                            val page = (pagerState.currentPage + target)
                                .coerceIn(0, state.pages.lastIndex)
                            if (page != pagerState.currentPage) {
                                scope.launch { pagerState.animateScrollToPage(page) }
                            }
                        }
                    }
                },
        ) {
            if (state.readingMode == MangaReadingMode.VERTICAL) {
                VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                    PageImage(state.pages[index], Modifier.fillMaxSize(), ContentScale.Fit)
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = state.readingMode == MangaReadingMode.RIGHT_TO_LEFT,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    PageImage(state.pages[index], Modifier.fillMaxSize(), ContentScale.Fit)
                }
            }
            if (state.showPageNumber && state.pages.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${state.pages.size}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Android's WebtoonViewer: continuous vertical strip, full-width images.
 * CONTINUOUS_VERTICAL shares the strip (the gap distinction between the
 * two long-strip modes isn't meaningful without stitched transitions).
 */
@Composable
private fun WebtoonViewer(
    state: MangaReaderScreenModel.State,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // +1 shifts past the chapter-name header item; strip it when saving
    // so the stored index stays a page index, not a row index.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (state.initialPage + 1)
            .coerceIn(0, state.pages.size + 1),
    )
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onPageChange((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item {
            Text(
                text = state.chapterName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(state.pages, key = { it.index }) { page ->
            PageImage(page, Modifier.fillMaxWidth())
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PageImage(
    page: miko.ui.carrier.PageEntry,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
) {
    AsyncImage(
        model = page.imageUrl ?: page.url,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
    )
}

/**
 * Android's in-reader settings (reduced): the per-manga reading mode picker
 * and the page-number toggle.
 */
@Composable
private fun ReaderSettingsSheet(
    state: MangaReaderScreenModel.State,
    onModeSelect: (MangaReadingMode) -> Unit,
    onPageNumberChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(MR.strings.pref_category_reading_mode),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SettingDropdown(
                labelRes = MR.strings.pref_category_reading_mode,
                current = stringResource(MangaReadingMode.fromFlag(state.viewerFlag).titleRes),
                options = MangaReadingMode.entries.map { it to it.titleRes },
                onSelect = onModeSelect,
            )
            SettingSwitch(
                labelRes = MR.strings.pref_show_page_number,
                checked = state.showPageNumber,
                onChange = onPageNumberChange,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
