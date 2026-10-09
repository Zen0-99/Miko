package eu.kanade.tachiyomi.ui.entries.novel

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifNovelSourcesLoaded
import eu.kanade.domain.entries.novel.model.hasCustomCover
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.manga.carrierSheetContainerColor
import eu.kanade.presentation.entries.novel.NovelScreen
import eu.kanade.presentation.entries.novel.components.NovelChapterAudioAction
import eu.kanade.presentation.entries.novel.components.rememberNovelAccentColor
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import eu.kanade.tachiyomi.source.novel.isLocalOrStub
import eu.kanade.tachiyomi.ui.browse.novel.migration.all.MigrateNovelAllScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelDialog
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.tachiyomi.ui.comments.CommentsSheetScreen
import eu.kanade.tachiyomi.ui.entries.novel.track.NovelTrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.entries.suggestions.EntrySuggestionsScreen
import eu.kanade.tachiyomi.ui.entries.suggestions.toDirectEntryScreenOrNull
import eu.kanade.tachiyomi.ui.entries.suggestions.toGlobalSearchScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.reader.novel.NovelHighlightsScreen
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlayerController
import eu.kanade.tachiyomi.ui.readingorder.ReadingOrderLockDialog
import eu.kanade.tachiyomi.ui.readingorder.ReadingOrderViewerScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.EntryCoverMetadata
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.readingorder.interactor.GetLockedReadingOrders
import tachiyomi.domain.readingorder.model.ReadingOrder
import eu.kanade.presentation.entries.components.EntryDetailSkeleton
import eu.kanade.presentation.entries.components.EntryHeroLoadingPreview
import eu.kanade.presentation.entries.components.SKELETON_FADE_MS
import eu.kanade.presentation.entries.components.SKELETON_LATE_ARRIVAL_MS
import eu.kanade.presentation.entries.components.isNavEntering
import eu.kanade.presentation.entries.components.rememberCachedAccentColor
import eu.kanade.presentation.util.LocalNavAnimatedVisibilityScope
import tachiyomi.presentation.core.screens.DelayedLoadingScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelScreen(
    private val novelId: Long,
    val fromSource: Boolean = false,
    /**
     * Artwork the caller already displays for this entry (e.g. the home
     * hero's). Shown as a shared element during Loading so the push
     * transition morphs the hero image instead of a blank spinner.
     */
    private val previewCover: Any? = null,
    /** Title for the shared `hero-title-` element during Loading. */
    private val previewTitle: String? = null,
    /** Clearlogo for the shared `hero-title-` element during Loading. */
    private val previewLogo: String? = null,
    /** Continue-row label the caller already knows (home hero / library
     *  position) — rendered inside the shared resume pill during Loading. */
    private val previewResumeLabel: String? = null,
    /** The pre-filled action is a real resume target — the skeleton's
     *  continue bar renders the accent fill, not the "all up to date" tint. */
    private val previewResumeEnabled: Boolean = false,
    /** Callers that already display the entry's genres (home hero, library
     *  rows) tell the skeleton whether the genre row will exist so a
     *  genre-less entry doesn't get a ghost bar for content that never
     *  loads. Defaults true — most entries have genres. */
    private val previewHasGenres: Boolean = true,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifNovelSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val getLockedReadingOrders: GetLockedReadingOrders = remember { Injekt.get() }
        val audiobookPlayer: AudiobookPlayerController = remember { Injekt.get() }
        var lockDialog by remember { mutableStateOf<List<ReadingOrder>>(emptyList()) }
        val screenModel =
            rememberScreenModel { NovelScreenModel(context, lifecycleOwner.lifecycle, novelId, fromSource) }

        val state by screenModel.state.collectAsStateWithLifecycle()
        val redirectId by screenModel.redirectToEntryId.collectAsStateWithLifecycle()

        // Extension-source duplicate of a canonical `miko:` entry — swap
        // to the canonical entry so there's one entry per work. Hand the
        // current entry's artwork to the new screen's Loading preview so
        // the cover stays painted through the mint handoff instead of
        // flashing blank.
        LaunchedEffect(redirectId) {
            redirectId?.let {
                val current = (state as? NovelScreenModel.State.Success)?.novel
                navigator.replace(
                    NovelScreen(
                        it,
                        fromSource,
                        previewCover = current?.thumbnailUrl ?: previewCover,
                        previewTitle = current?.title ?: previewTitle,
                        previewLogo = previewLogo,
                        previewResumeLabel = previewResumeLabel,
                        previewResumeEnabled = previewResumeEnabled,
                        previewHasGenres = previewHasGenres,
                    ),
                )
            }
        }

        // Secondary work in the screen model (canonical pipelines,
        // carriers, source fetches, suggestions, badges) waits for the
        // push to land — mid-flight DB merges and network writes are
        // what made cold opens adjust the hero mid-transition. Screens
        // without a nav scope settle on their first frame.
        val navScope = LocalNavAnimatedVisibilityScope.current
        val entering = navScope.isNavEntering()
        LaunchedEffect(entering) {
            if (!entering) screenModel.onTransitionSettled()
        }

        // Ghost bars are for genuinely-slow loads only — a cached
        // entry's Success lands inside a few frames, and painting the
        // bars anyway reads as the skeleton hijacking every push.
        var showGhosts by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(220)
            showGhosts = true
        }
        // A Success inside SKELETON_LATE_ARRIVAL_MS resolved from cache —
        // the real screen mounts immediately so cached opens skip the
        // skeleton entirely. Later arrivals hold the skeleton (identical
        // geometry) to landing so its mount can't stall the flight; the
        // skeleton then crossfades out over the real screen instead of
        // the ghost bars popping off in a single frame.
        val mountNanos = remember { System.nanoTime() }
        var firstSuccessNanos by remember { mutableLongStateOf(-1L) }
        LaunchedEffect(state is NovelScreenModel.State.Success) {
            if (state is NovelScreenModel.State.Success && firstSuccessNanos < 0L) {
                firstSuccessNanos = System.nanoTime()
            }
        }
        val earlySuccess = state is NovelScreenModel.State.Success &&
            (firstSuccessNanos.takeIf { it >= 0L } ?: System.nanoTime()) - mountNanos <
            SKELETON_LATE_ARRIVAL_MS * 1_000_000L
        val skeletonWanted = previewCover != null && !isTabletUi() &&
            (state is NovelScreenModel.State.Loading || (entering && !earlySuccess))
        val skeletonAlpha by animateFloatAsState(
            targetValue = if (skeletonWanted) 1f else 0f,
            animationSpec = tween(SKELETON_FADE_MS),
            label = "detailSkeletonFade",
        )
        val seedHeightsPx = remember { mutableStateMapOf<Any, Int>() }
        if (skeletonWanted) {
            EntryDetailSkeleton(
                cover = previewCover,
                sharedKey = "hero-image-$novelId",
                heroContentPadding = 440.dp,
                title = previewTitle,
                logo = previewLogo,
                navigateUp = navigator::pop,
                isManga = true,
                favorite = (previewCover as? tachiyomi.domain.entries.novel.model.NovelCover)
                    ?.isNovelFavorite == true,
                showGhosts = showGhosts,
                accentColor = rememberCachedAccentColor(
                    novelId,
                    EntryCoverMetadata.EntryType.NOVEL,
                ),
                resumeLabel = previewResumeLabel,
                resumeEnabled = previewResumeEnabled,
                genreGhostVisible = previewHasGenres,
                onItemHeight = { key, height -> seedHeightsPx[key] = height },
            )
            return
        }
        if (state is NovelScreenModel.State.Loading) {
            EntryHeroLoadingPreview(
                cover = previewCover,
                sharedKey = "hero-image-$novelId",
                heroContentPadding = 440.dp,
                title = previewTitle,
                logo = previewLogo,
            )
            return
        }

        val successState = state as NovelScreenModel.State.Success
        val isHttpSource = remember { successState.source is NovelHttpSource }

        val extractedAccent = rememberNovelAccentColor(successState.novel)
        LaunchedEffect(extractedAccent) {
            if (extractedAccent != successState.accentColor) {
                screenModel.setAccentColor(extractedAccent)
            }
        }

        LaunchedEffect(successState.novel, screenModel.source) {
            if (isHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getNovelUrl(screenModel.novel, screenModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get novel URL" }
                }
            }
        }

        // Mark chapters as seen when leaving the detail screen so the "new"
        // indicator (colored circle) is cleared on next visit.
        DisposableEffect(Unit) {
            onDispose {
                screenModel.markChaptersAsSeen()
            }
        }

        Box(Modifier.fillMaxSize()) {
            NovelScreen(
                state = successState,
                previewCover = previewCover,
                seedHeightsPx = seedHeightsPx,
                snackbarHostState = screenModel.snackbarHostState,
                isTabletUi = isTabletUi(),
                chapterSwipeStartAction = screenModel.chapterSwipeStartAction,
                chapterSwipeEndAction = screenModel.chapterSwipeEndAction,
                navigateUp = navigator::pop,
            onChapterClicked = { chapter ->
                scope.launch {
                    val lockedOrders = getLockedReadingOrders.await(novelId)
                    if (lockedOrders.isNotEmpty()) {
                        lockDialog = lockedOrders
                    } else if (successState.novel.isAudiobook) {
                        // 22-02: audio entries have no text — the reader
                        // route is meaningless, the tap goes straight to
                        // the player. A failed play reports inline instead
                        // of falling back to a reader that can't load.
                        if (!audiobookPlayer.play(successState.novel, chapter)) {
                            screenModel.snackbarHostState.showSnackbar(
                                context.stringResource(
                                    tachiyomi.i18n.aniyomi.AYMR.strings.audiobook_no_stream,
                                ),
                            )
                        }
                    } else {
                        navigator.push(NovelReaderScreen(novelId, chapter.id))
                    }
                }
            },
            onDownloadChapter = screenModel::runChapterDownloadActions.takeIf {
                // Canon entries ride a stub source but DO support
                // downloads — the queue resolves each chapter to its
                // carrier at enqueue time. Audiobook entries stream —
                // audio tracks aren't text-downloadable (22-02).
                !successState.novel.isAudiobook &&
                    (
                        !successState.source.isLocalOrStub() ||
                            eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
                                .isCanonicalUrl(successState.novel.url)
                        )
            },
            onAudioChapter = { items: List<NovelChapterList.Item>, action: NovelChapterAudioAction ->
                when (action) {
                    // D-04: Play opens the audiobook player overlay — same
                    // locked-reading-order check as onChapterClicked. If the
                    // rendered file vanished since the state refresh, fall
                    // back to the reader's live read-along.
                    NovelChapterAudioAction.PLAY -> scope.launch {
                        val lockedOrders = getLockedReadingOrders.await(novelId)
                        if (lockedOrders.isNotEmpty()) {
                            lockDialog = lockedOrders
                        } else if (!audiobookPlayer.play(successState.novel, items.first().chapter)) {
                            navigator.push(
                                NovelReaderScreen(
                                    novelId,
                                    items.first().chapter.id,
                                    autoStartTts = true,
                                ),
                            )
                        }
                    }
                    else -> screenModel.runChapterAudioActions(items, action)
                }
                Unit
            }.takeIf {
                // Same carve-out as onDownloadChapter above: canon entries
                // ride a stub source but their carrier files render/play.
                !successState.source.isLocalOrStub() ||
                    eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
                        .isCanonicalUrl(successState.novel.url)
            },
            onAddToLibraryClicked = {
                screenModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onCoverClick = { screenModel.showFullCoverDialog(it) },
            onWebViewClicked = {
                openNovelInWebView(navigator, screenModel.novel, screenModel.source)
            }.takeIf { isHttpSource },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it, screenModel.source!!) } },
            onFilterButtonClicked = screenModel::showSettingsDialog,
            onBookmarkFilterClicked = {
                screenModel.setBookmarkedFilter(
                    when (successState.novel.bookmarkedFilter) {
                        TriState.DISABLED -> TriState.ENABLED_IS
                        TriState.ENABLED_IS -> TriState.DISABLED
                        TriState.ENABLED_NOT -> TriState.DISABLED
                    },
                )
            },
            onHighlightsClicked = {
                navigator.push(NovelHighlightsScreen(successState.novel.title, successState.novel.id))
            }.takeIf { successState.novel.favorite },
            onTrackingClicked = { screenModel.showTrackSheet() },
            onCommentsClicked = screenModel::showCommentsDialog
                .takeIf { screenModel.commentsEnabled },
            onFindProviderClicked = screenModel::showFindProviderDialog,
            onCarrierPickerClicked = screenModel::showCarrierPicker,
            chapterCarrierLabel = screenModel::carrierLabelFor,
            onChapterCarrierClicked = screenModel::showChapterCarrierPicker,
            onRefresh = screenModel::fetchAllFromSource,
            onContinueReading = {
                val firstUnread = successState.nextContinueChapter
                navigator.push(NovelReaderScreen(novelId, firstUnread?.chapter?.id))
            },
            // Same routing as the chapter-row Play action: rendered audio →
            // player overlay; otherwise the reader opens with live TTS on.
            onContinueListening = {
                val chapter = successState.nextContinueChapter?.chapter
                scope.launch {
                    val lockedOrders = getLockedReadingOrders.await(novelId)
                    if (lockedOrders.isNotEmpty()) {
                        lockDialog = lockedOrders
                    } else if (!audiobookPlayer.resumeListening(successState.novel)) {
                        navigator.push(
                            NovelReaderScreen(
                                novelId,
                                chapter?.id,
                                autoStartTts = true,
                            ),
                        )
                    }
                }
            },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            onShareClicked = { shareNovel(context, screenModel.novel, screenModel.source) }.takeIf { isHttpSource },
            onDownloadActionClicked = screenModel::runDownloadAction
                .takeIf { !successState.source.isLocalOrStub() && !successState.novel.isAudiobook },
            onEditCollectionClicked = screenModel::showChangeCollectionDialog.takeIf { successState.novel.favorite },
            onEditNovel = { title, author, desc, status, tags ->
                screenModel.updateNovelMetadata(title, author, desc, status, tags)
            },
            onMigrateClicked = {
                navigator.push(MigrateNovelAllScreen(listOf(successState.novel.id), successState.novel.title))
            }.takeIf { successState.novel.favorite },
            onMarkAllReadClicked = { screenModel.markAllRead() },
            onMarkAllUnreadClicked = { screenModel.markAllUnread() },
            onRemoveAllDownloadsClicked = { screenModel.deleteAllDownloads() }
                .takeIf { !successState.source.isLocalOrStub() },
            onRemoveNonBookmarkedDownloadsClicked = { screenModel.deleteNonBookmarkedDownloads() }
                .takeIf { !successState.source.isLocalOrStub() },
            onRemoveReadDownloadsClicked = { screenModel.deleteReadDownloads() }
                .takeIf { !successState.source.isLocalOrStub() },
            onMultiBookmarkClicked = screenModel::bookmarkChapters,
            onMultiMarkAsReadClicked = screenModel::markChaptersRead,
            onMarkPreviousAsReadClicked = screenModel::markPreviousChaptersRead,
            onMultiDeleteClicked = screenModel::showDeleteChapterDialog,
            onChapterSwipe = screenModel::chapterSwipe,
            onChapterSelected = screenModel::toggleSelection,
            onAllChapterSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onHideChapter = { chapter -> screenModel.hideChapter(chapter) },
            onUnhideChapters = { screenModel.unhideAllChapters() }
                .takeIf { screenModel.hasHiddenChapters() },
            onFetchNewChapters = { screenModel.fetchNewChapters() }
                .takeIf { successState.source is NovelHttpSource && !successState.source.isLocalOrStub() },
            onFetchAllChapters = { screenModel.fetchAllChaptersWithProgress() }
                .takeIf { successState.source is NovelHttpSource && !successState.source.isLocalOrStub() },
            onSuggestionClick = { item ->
                scope.launch {
                    val screen = item.toDirectEntryScreenOrNull() ?: item.toGlobalSearchScreen()
                    navigator.push(screen)
                }
            },
            onOpenSuggestions = {
                val seed = screenModel.getSuggestionSeed()
                if (seed != null) {
                    navigator.push(
                        EntrySuggestionsScreen(
                            seed = seed,
                            sourceId = successState.source.id,
                            entryUrl = successState.novel.url,
                        ),
                    )
                }
            },
            onRetrySuggestions = { screenModel.retrySuggestions() },
            onDownloadBook = { screenModel.downloadBook() }
                .takeIf { successState.isBookSource },
            onDeleteBook = { screenModel.deleteBook() }
                .takeIf { successState.isBookSource },
            )

            // Post-landing swap dissolves the held skeleton over the real
            // screen instead of popping the ghost bars off in one frame.
            // "settle-fade" keys keep its shared elements from colliding
            // with the real screen's while both are composed; mid-flight
            // swaps skip the overlay since the morph needs an instant
            // target, not a fade.
            if (skeletonAlpha > 0f && !entering) {
                Box(Modifier.fillMaxSize().alpha(skeletonAlpha)) {
                    EntryDetailSkeleton(
                        cover = previewCover,
                        sharedKey = "hero-image-settle-fade",
                        heroContentPadding = 440.dp,
                        title = previewTitle,
                        logo = previewLogo,
                        navigateUp = navigator::pop,
                        isManga = true,
                        favorite = (previewCover as? tachiyomi.domain.entries.novel.model.NovelCover)
                            ?.isNovelFavorite == true,
                        showGhosts = showGhosts,
                        accentColor = rememberCachedAccentColor(
                            novelId,
                            EntryCoverMetadata.EntryType.NOVEL,
                        ),
                        resumeLabel = previewResumeLabel,
                        resumeEnabled = previewResumeEnabled,
                        genreGhostVisible = previewHasGenres,
                    )
                }
            }
        }

        val onDismissRequest = {
            screenModel.dismissDialog()
        }
        when (val dialog = successState.dialog) {
            null -> {}
            is NovelScreenModel.Dialog.ChangeCollection -> {
                eu.kanade.presentation.collection.components.ChangeCollectionDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCollections = { /* TODO: collections tab */ },
                    onConfirm = { include, _ ->
                        screenModel.moveNovelToCollectionsAndAddToLibrary(dialog.novel, include)
                    },
                    accentColor = successState.accentColor,
                )
            }
            is NovelScreenModel.Dialog.DeleteChapters -> {
                eu.kanade.presentation.entries.components.DeleteItemsDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteChapters(dialog.chapters)
                    },
                    isManga = false,
                )
            }
            is NovelScreenModel.Dialog.DuplicateNovel -> {
                eu.kanade.presentation.entries.novel.DuplicateNovelDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenNovel = { navigator.push(NovelScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        screenModel.showMigrateDialog(dialog.duplicate)
                    },
                )
            }
            is NovelScreenModel.Dialog.Migrate -> {
                MigrateNovelDialog(
                    oldNovel = dialog.oldNovel,
                    newNovel = dialog.newNovel,
                    screenModel = MigrateNovelDialogScreenModel(),
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(NovelScreen(dialog.oldNovel.id)) },
                    onPopScreen = { navigator.replace(NovelScreen(dialog.newNovel.id)) },
                )
            }
            NovelScreenModel.Dialog.SettingsSheet -> {
                eu.kanade.presentation.entries.novel.NovelChapterSettingsDialog(
                    onDismissRequest = onDismissRequest,
                    novel = successState.novel,
                    onUnreadFilterChanged = screenModel::setUnreadFilter,
                    onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                    onDownloadedFilterChanged = screenModel::setDownloadedFilter,
                    onSortModeChanged = screenModel::setSorting,
                    onDisplayModeChanged = screenModel::setDisplayMode,
                    onSetAsDefault = screenModel::setAsDefault,
                    onResetToDefault = screenModel::resetToDefaultSettings,
                    accentColor = successState.accentColor,
                )
            }
            is NovelScreenModel.Dialog.FullCover -> {
                val sm = rememberScreenModel { NovelCoverScreenModel(successState.novel.id) }
                val novelState by sm.state.collectAsState()
                val novelObj = novelState
                if (novelObj != null) {
                    val getContent = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent(),
                    ) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editCover(context, it)
                    }
                    eu.kanade.presentation.entries.novel.components.NovelCoverDialog(
                        novel = novelObj,
                        coverData = dialog.coverData,
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(novelObj) {
                            novelObj.hasCustomCover()
                        },
                        onShareClick = { sm.shareCover(context, dialog.coverData) },
                        onSaveClick = { sm.saveCover(context, dialog.coverData) },
                        onEditClick = if (novelObj.favorite) {
                            {
                                when (it) {
                                    EditCoverAction.EDIT -> getContent.launch("image/*")
                                    EditCoverAction.DELETE -> sm.deleteCustomCover(context)
                                }
                            }
                        } else {
                            null
                        },
                        onDismissRequest = onDismissRequest,
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }
            NovelScreenModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = NovelTrackInfoDialogHomeScreen(
                        novelId = successState.novel.id,
                        novelTitle = successState.novel.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is NovelTrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }
            NovelScreenModel.Dialog.Comments -> {
                NavigatorAdaptiveSheet(
                    screen = CommentsSheetScreen(
                        carrier = Carrier.BOOKS,
                        entryId = successState.novel.id,
                        expectedComments = successState.commentsCount,
                    ),
                    containerColor = carrierSheetContainerColor(successState.accentColor),
                    onDismissRequest = onDismissRequest,
                )
            }
            NovelScreenModel.Dialog.FindProvider -> {
                val search = successState.providerSearch
                if (search != null) {
                    eu.kanade.presentation.entries.components.FindProviderDialog(
                        searching = search.searching,
                        rows = search.rows,
                        confirmed = search.confirmed,
                        recommendations = search.recommendations,
                        installSteps = successState.carrierInstallSteps,
                        accentColor = successState.accentColor,
                        onToggle = screenModel::toggleProviderPick,
                        onInstall = screenModel::installRecommendedProvider,
                        onRestore = screenModel::restoreExcludedCarrier,
                        onConfirm = screenModel::confirmProviderPicks,
                        onDismissRequest = onDismissRequest,
                    )
                }
            }
            is NovelScreenModel.Dialog.CarrierPicker -> {
                val pickedChapter = dialog.chapter
                eu.kanade.presentation.entries.manga.CarrierPickerDialog(
                    matrix = successState.carrierMatrix,
                    preferred = pickedChapter?.let { screenModel.chapterCarrierFor(it) }
                        ?: successState.preferredCarrier,
                    chapterName = pickedChapter?.name,
                    accentColor = successState.accentColor,
                    // Chapter-scoped picks stay purely a routing choice —
                    // the work-level sheet offers provider discovery.
                    onFindMoreProviders = if (pickedChapter == null) {
                        { screenModel.showFindProviderDialog() }
                    } else {
                        null
                    },
                    onVote = screenModel::voteCarrier,
                    onReport = screenModel::reportCarrier,
                    onRemove = screenModel::removeCarrier,
                    onSelect = { pkg ->
                        if (pickedChapter != null) {
                            screenModel.setChapterCarrier(pickedChapter, pkg)
                        } else {
                            screenModel.setPreferredCarrier(pkg)
                        }
                    },
                    onDismissRequest = onDismissRequest,
                )
            }
        }

        if (lockDialog.isNotEmpty()) {
            ReadingOrderLockDialog(
                lockedOrders = lockDialog,
                onDismiss = { lockDialog = emptyList() },
                onViewOrder = { orderId ->
                    lockDialog = emptyList()
                    navigator.push(ReadingOrderViewerScreen(orderId))
                },
            )
        }
    }

    private fun getNovelUrl(novel_: Novel?, source_: Any?): String? {
        val novel = novel_ ?: return null
        val source = source_ as? NovelHttpSource ?: return null
        return try {
            source.getNovelUrl(novel.toSNovel())
        } catch (e: Exception) {
            null
        }
    }

    private fun openNovelInWebView(navigator: Navigator, novel_: Novel?, source_: Any?) {
        getNovelUrl(novel_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = novel_?.title,
                    sourceId = (source_ as? NovelHttpSource)?.id,
                ),
            )
        }
    }

    private fun shareNovel(context: Context, novel_: Novel?, source_: Any?) {
        try {
            getNovelUrl(novel_, source_)?.let { url ->
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, url)
                }
                context.startActivity(
                    android.content.Intent.createChooser(
                        intent,
                        context.stringResource(tachiyomi.i18n.MR.strings.action_share),
                    ),
                )
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(GlobalNovelSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                // Set mode to NOVEL so the library search targets novel content.
                Injekt.get<UiPreferences>().contentMode().set(ContentMode.NOVEL)
                navigator.pop()
                LibraryTab.search(query)
            }
            is BrowseNovelSourceScreen -> {
                navigator.pop()
                previousController.search(query)
            }
        }
    }

    private suspend fun performGenreSearch(
        navigator: Navigator,
        genreName: String,
        source: Any,
    ) {
        if (navigator.size < 2) {
            return
        }

        val previousController = navigator.items[navigator.size - 2]
        if (previousController is BrowseNovelSourceScreen && source is NovelHttpSource) {
            navigator.pop()
            previousController.searchGenre(genreName)
        } else {
            performSearch(navigator, genreName, global = false)
        }
    }
}
