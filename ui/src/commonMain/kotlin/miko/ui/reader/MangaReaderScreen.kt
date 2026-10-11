package miko.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import miko.ui.components.SettingDropdown
import miko.ui.components.SettingSwitch
import miko.ui.download.DownloadManager
import miko.ui.platform.KeepScreenOn
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.secondaryItemAlpha

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

    // Android's reader keep-screen-on — idle timer off while reading.
    KeepScreenOn(state.keepScreenOn)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(readerThemeBackground(state.readerTheme)),
    ) {
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
                onCenterTap = model::toggleChrome,
                onPrevChapter = model::prevChapter,
                onNextChapter = model::nextChapter,
                modifier = Modifier.weight(1f),
            )
        }

        if (state.chromeVisible) {
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
                    enabled = state.prevChapter != null,
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
                    enabled = state.nextChapter != null,
                ) {
                    Text(stringResource(MR.strings.action_next_chapter))
                }
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
            onTransitionsChange = model::setShowTransitions,
            onThemeSelect = model::setReaderTheme,
            onDismiss = model::closeSettings,
        )
    }
}

/**
 * Android's reader background colors — white/black/gray follow the pref;
 * automatic picks gray in dark mode and white in light (ReaderActivity's
 * automaticBackgroundColor).
 */
@Composable
private fun readerThemeBackground(theme: Int): Color = when (theme) {
    0 -> Color.White
    2 -> GrayReaderBackground
    3 -> if (isSystemInDarkTheme()) GrayReaderBackground else Color.White
    else -> Color.Black
}

private val GrayReaderBackground = Color(0xFF202125)

/** Android's GeneralSettingsPage themes — same order and pref values. */
private val readerThemeOptions = listOf(
    MR.strings.black_background to 1,
    MR.strings.gray_background to 2,
    MR.strings.white_background to 0,
    MR.strings.automatic_background to 3,
)

/** Dispatches to the paged or long-strip viewer per the resolved mode. */
@Composable
private fun PageViewer(
    state: MangaReaderScreenModel.State,
    onPageChange: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.readingMode) {
        MangaReadingMode.LEFT_TO_RIGHT,
        MangaReadingMode.RIGHT_TO_LEFT,
        MangaReadingMode.VERTICAL,
        -> PagedViewer(state, onPageChange, onCenterTap, onPrevChapter, onNextChapter, modifier)
        else -> Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Android's webtoon viewer toggles the app bar on any tap.
                    detectTapGestures { onCenterTap() }
                },
        ) {
            WebtoonViewer(state, onPageChange, onPrevChapter, onNextChapter, Modifier.fillMaxSize())
        }
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
    onCenterTap: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Reset page position whenever the chapter changes; lastPageRead
    // seeds the resume page (Android's preserveReadingPosition).
    key(state.chapterUrl) {
        // Android wraps the pages with prev/next ChapterTransition cards —
        // index 0 is the prev transition, pageCount-1 the next one, real
        // pages sit between them. Offsets below convert to page indices.
        val transitions = if (state.showTransitions) 1 else 0
        val pagerState = rememberPagerState(
            initialPage = (state.initialPage + transitions)
                .coerceIn(
                    transitions,
                    (state.pages.lastIndex + transitions).coerceAtLeast(transitions),
                ),
            pageCount = { state.pages.size + transitions * 2 },
        )
        val scope = rememberCoroutineScope()
        LaunchedEffect(pagerState.currentPage) {
            onPageChange(
                (pagerState.currentPage - transitions)
                    .coerceIn(0, state.pages.lastIndex.coerceAtLeast(0)),
            )
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
                                .coerceIn(0, state.pages.size + transitions * 2 - 1)
                            if (page != pagerState.currentPage) {
                                scope.launch { pagerState.animateScrollToPage(page) }
                            }
                        } else {
                            onCenterTap()
                        }
                    }
                },
        ) {
            val pageContent: @Composable PagerScope.(Int) -> Unit = { index ->
                val pageIndex = index - transitions
                when {
                    transitions == 1 && index == 0 -> ChapterTransitionBlock(
                        goingNext = false,
                        currentName = state.chapterName,
                        neighbor = state.prevChapter,
                        onNavigate = onPrevChapter,
                        modifier = Modifier.fillMaxSize(),
                    )
                    transitions == 1 && index == state.pages.size + 1 -> ChapterTransitionBlock(
                        goingNext = true,
                        currentName = state.chapterName,
                        neighbor = state.nextChapter,
                        onNavigate = onNextChapter,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> PageImage(
                        state.pages[pageIndex],
                        Modifier.fillMaxSize(),
                        ContentScale.Fit,
                    )
                }
            }
            if (state.readingMode == MangaReadingMode.VERTICAL) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageContent = pageContent,
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = state.readingMode == MangaReadingMode.RIGHT_TO_LEFT,
                    modifier = Modifier.fillMaxSize(),
                    pageContent = pageContent,
                )
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
                        text = "${(pagerState.currentPage + 1 - transitions).coerceIn(
                            1,
                            state.pages.size,
                        )} / ${state.pages.size}",
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
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Item layout mirrors Android's stitched strip: [prev transition]
    // [chapter header] [pages…] [spacer] [next transition]. The saved index
    // strips the leading items so it stays a page index, not a row index.
    val headerItems = 1 + (if (state.showTransitions) 1 else 0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (state.initialPage + headerItems)
            .coerceIn(
                headerItems,
                (state.pages.lastIndex + headerItems).coerceAtLeast(headerItems),
            ),
    )
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onPageChange(
            (listState.firstVisibleItemIndex - headerItems)
                .coerceIn(0, state.pages.lastIndex.coerceAtLeast(0)),
        )
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        if (state.showTransitions) {
            item {
                ChapterTransitionBlock(
                    goingNext = false,
                    currentName = state.chapterName,
                    neighbor = state.prevChapter,
                    onNavigate = onPrevChapter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }
        }
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
        if (state.showTransitions) {
            item {
                ChapterTransitionBlock(
                    goingNext = true,
                    currentName = state.chapterName,
                    neighbor = state.nextChapter,
                    onNavigate = onNextChapter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun PageImage(
    page: miko.ui.carrier.PageEntry,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
) {
    val src = page.imageUrl ?: page.url
    // Downloaded pages carry miko-file:// paths — read them off disk;
    // remote urls go straight to Coil like before.
    val fileBytes by produceState<ByteArray?>(initialValue = null, src) {
        if (src.startsWith(DownloadManager.FILE_SCHEME)) {
            value = withContext(Dispatchers.Default) {
                runCatching {
                    kotlinx.io.files.SystemFileSystem
                        .source(kotlinx.io.files.Path(src.removePrefix(DownloadManager.FILE_SCHEME)))
                        .buffered().use { it.readByteArray() }
                }.getOrNull()
            }
        }
    }
    AsyncImage(
        model = if (src.startsWith(DownloadManager.FILE_SCHEME)) fileBytes else src,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
    )
}

/**
 * Android's ReaderTransitionView — the card between chapters. Prev shows
 * "Previous: <neighbor>" over "Current: <name>"; Next shows "Finished:
 * <name>" over "Next: <neighbor>". Chapters aren't stitched here, so the
 * block taps through to navigate; null neighbors get the "no chapter" card.
 */
@Composable
private fun ChapterTransitionBlock(
    goingNext: Boolean,
    currentName: String,
    neighbor: NeighborChapter?,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 460.dp)
            .then(
                if (neighbor != null) {
                    Modifier.clickable(onClick = onNavigate)
                } else {
                    Modifier
                },
            ),
    ) {
        if (goingNext) {
            TransitionChapterText(header = stringResource(MR.strings.transition_finished), chapter = currentName)
        } else if (neighbor != null) {
            TransitionChapterText(
                header = stringResource(MR.strings.transition_previous),
                chapter = neighbor.name,
                scanlator = neighbor.scanlator,
            )
        } else {
            TransitionFallbackCard(text = stringResource(MR.strings.transition_no_previous))
        }

        Spacer(Modifier.height(24.dp))

        if (neighbor != null && neighbor.gap > 0) {
            OutlinedCard(colors = transitionCardColors()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        tint = MaterialTheme.colorScheme.error,
                        contentDescription = null,
                    )
                    Text(
                        text = pluralStringResource(
                            MR.plurals.missing_chapters_warning,
                            count = neighbor.gap,
                            neighbor.gap,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (goingNext) {
            if (neighbor != null) {
                TransitionChapterText(
                    header = stringResource(MR.strings.transition_next),
                    chapter = neighbor.name,
                    scanlator = neighbor.scanlator,
                )
            } else {
                TransitionFallbackCard(text = stringResource(MR.strings.transition_no_next))
            }
        } else {
            TransitionChapterText(header = stringResource(MR.strings.transition_current), chapter = currentName)
        }
    }
}

@Composable
private fun TransitionChapterText(
    header: String,
    chapter: String,
    scanlator: String? = null,
) {
    Column {
        Text(
            text = header,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = chapter,
            fontSize = 20.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge,
        )
        scanlator?.let {
            Text(
                text = it,
                modifier = Modifier
                    .secondaryItemAlpha()
                    .padding(top = 2.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TransitionFallbackCard(text: String) {
    OutlinedCard(colors = transitionCardColors()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null,
            )
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun transitionCardColors() = CardDefaults.outlinedCardColors(
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/**
 * Android's in-reader settings (reduced): the per-manga reading mode picker
 * and the page-number toggle.
 */
@Composable
private fun ReaderSettingsSheet(
    state: MangaReaderScreenModel.State,
    onModeSelect: (MangaReadingMode) -> Unit,
    onPageNumberChange: (Boolean) -> Unit,
    onTransitionsChange: (Boolean) -> Unit,
    onThemeSelect: (Int) -> Unit,
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
            Text(
                text = stringResource(MR.strings.pref_reader_theme),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                readerThemeOptions.forEach { (labelRes, value) ->
                    FilterChip(
                        selected = state.readerTheme == value,
                        onClick = { onThemeSelect(value) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            SettingSwitch(
                labelRes = MR.strings.pref_show_page_number,
                checked = state.showPageNumber,
                onChange = onPageNumberChange,
            )
            SettingSwitch(
                labelRes = MR.strings.pref_always_show_chapter_transition,
                checked = state.showTransitions,
                onChange = onTransitionsChange,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
