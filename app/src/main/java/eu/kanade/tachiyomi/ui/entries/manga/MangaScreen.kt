package eu.kanade.tachiyomi.ui.entries.manga

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifMangaSourcesLoaded
import eu.kanade.domain.entries.manga.model.hasCustomCover
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.presentation.collection.components.ChangeCollectionDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.components.DeleteItemsDialog
import eu.kanade.presentation.entries.components.SetIntervalDialog
import eu.kanade.presentation.entries.components.rememberEntryAccentColor
import eu.kanade.tachiyomi.util.EntryCoverMetadata
import eu.kanade.presentation.entries.manga.ChapterSettingsDialog
import eu.kanade.presentation.entries.manga.DuplicateMangaDialog
import eu.kanade.presentation.entries.manga.MangaScreen
import eu.kanade.presentation.entries.manga.components.MangaCoverDialog
import eu.kanade.presentation.entries.manga.components.ScanlatorFilterDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.manga.isLocalOrStub
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.ui.entries.suggestions.EntrySuggestionsScreen
import eu.kanade.tachiyomi.ui.entries.suggestions.toDirectEntryScreenOrNull
import eu.kanade.tachiyomi.ui.entries.suggestions.toGlobalSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.all.MigrateMangaAllScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialog
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.collection.CollectionsTab
import eu.kanade.tachiyomi.ui.entries.manga.track.MangaTrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.readingorder.ReadingOrderLockDialog
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaScreen(
    private val mangaId: Long,
    val fromSource: Boolean = false,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifMangaSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val screenModel =
            rememberScreenModel { MangaScreenModel(context, lifecycleOwner.lifecycle, mangaId, fromSource) }

        val getLockedReadingOrders: tachiyomi.domain.readingorder.interactor.GetLockedReadingOrders = uy.kohesive.injekt.Injekt.get()
        var lockDialog by remember { androidx.compose.runtime.mutableStateOf<List<tachiyomi.domain.readingorder.model.ReadingOrder>>(emptyList()) }

        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state is MangaScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as MangaScreenModel.State.Success
        val isHttpSource = remember { successState.source is HttpSource }

        val extractedAccent = rememberEntryAccentColor(
            entryId = successState.manga.id,
            cover = successState.manga,
            type = EntryCoverMetadata.EntryType.MANGA,
        )
        LaunchedEffect(extractedAccent) {
            if (extractedAccent != successState.accentColor) {
                screenModel.setAccentColor(extractedAccent)
            }
        }

        LaunchedEffect(successState.manga, screenModel.source) {
            if (isHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getMangaUrl(screenModel.manga, screenModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get manga URL" }
                }
            }
        }

        MangaScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            nextUpdate = successState.manga.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            chapterSwipeStartAction = screenModel.chapterSwipeStartAction,
            chapterSwipeEndAction = screenModel.chapterSwipeEndAction,
            navigateUp = navigator::pop,
            onChapterClicked = {
                openChapterWithLockCheck(context, it, scope, getLockedReadingOrders) { lockedOrders ->
                    lockDialog = lockedOrders
                }
            },
            onDownloadChapter = screenModel::runChapterDownloadActions.takeIf { !successState.source.isLocalOrStub() },
            onAddToLibraryClicked = {
                screenModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onWebViewClicked = {
                openMangaInWebView(
                    navigator,
                    screenModel.manga,
                    screenModel.source,
                )
            }.takeIf { isHttpSource },
            onWebViewLongClicked = {
                copyMangaUrl(
                    context,
                    screenModel.manga,
                    screenModel.source,
                )
            }.takeIf { isHttpSource },
            onTrackingClicked = {
                if (!successState.hasLoggedInTrackers) {
                    navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                } else {
                    screenModel.showTrackDialog()
                }
            },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it, screenModel.source!!) } },
            onFilterButtonClicked = screenModel::showSettingsDialog,
            onRefresh = screenModel::fetchAllFromSource,
            onContinueReading = { continueReading(context, screenModel.getNextUnreadChapter()) },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            onCoverClicked = screenModel::showCoverDialog,
            onShareClicked = { shareManga(context, screenModel.manga, screenModel.source) }.takeIf { isHttpSource },
            onDownloadActionClicked = screenModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },
            onEditCollectionClicked = screenModel::showChangeCollectionDialog.takeIf { successState.manga.favorite },
            onEditFetchIntervalClicked = screenModel::showSetMangaFetchIntervalDialog.takeIf {
                successState.manga.favorite
            },
            onMigrateClicked = {
                navigator.push(MigrateMangaAllScreen(listOf(successState.manga.id), successState.manga.title))
            }.takeIf { successState.manga.favorite },
            onMultiBookmarkClicked = screenModel::bookmarkChapters,
            onMultiMarkAsReadClicked = screenModel::markChaptersRead,
            onMarkPreviousAsReadClicked = screenModel::markPreviousChapterRead,
            onMultiDeleteClicked = screenModel::showDeleteChapterDialog,
            onChapterSwipe = screenModel::chapterSwipe,
            onChapterSelected = screenModel::toggleSelection,
            onAllChapterSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onMarkAllReadClicked = { screenModel.markAllRead() },
            onMarkAllUnreadClicked = { screenModel.markAllUnread() },
            onRefreshTrackingClicked = { screenModel.refreshTracking() },
            onRemoveAllDownloadsClicked = { screenModel.deleteAllDownloads() }
                .takeIf { !successState.source.isLocalOrStub() },
            onRemoveNonBookmarkedDownloadsClicked = { screenModel.deleteNonBookmarkedDownloads() }
                .takeIf { !successState.source.isLocalOrStub() },
            onRemoveReadDownloadsClicked = { screenModel.deleteReadDownloads() }
                .takeIf { !successState.source.isLocalOrStub() },
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
                            entryUrl = successState.manga.url,
                        ),
                    )
                }
            },
            onRetrySuggestions = { screenModel.retrySuggestions() },
        )

        var showScanlatorsDialog by remember { mutableStateOf(false) }

        val onDismissRequest = {
            screenModel.dismissDialog()
            if (screenModel.autoOpenTrack && screenModel.isFromChangeCollection) {
                screenModel.isFromChangeCollection = false
                screenModel.showTrackDialog()
            }
        }
        when (val dialog = successState.dialog) {
            null -> {}
            is MangaScreenModel.Dialog.ChangeCollection -> {
                ChangeCollectionDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCollections = {
                        navigator.push(CollectionsTab)
                        CollectionsTab.showMangaCollection()
                    },
                    onConfirm = { include, _ ->
                        screenModel.moveMangaToCollectionsAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is MangaScreenModel.Dialog.DeleteChapters -> {
                DeleteItemsDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteChapters(dialog.chapters)
                    },
                    isManga = true,
                )
            }

            is MangaScreenModel.Dialog.DuplicateManga -> {
                DuplicateMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenManga = { navigator.push(MangaScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        screenModel.showMigrateDialog(dialog.duplicate)
                    },
                )
            }

            is MangaScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    oldManga = dialog.oldManga,
                    newManga = dialog.newManga,
                    screenModel = MigrateMangaDialogScreenModel(),
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(MangaScreen(dialog.oldManga.id)) },
                    onPopScreen = { navigator.replace(MangaScreen(dialog.newManga.id)) },
                )
            }
            MangaScreenModel.Dialog.SettingsSheet -> ChapterSettingsDialog(
                onDismissRequest = onDismissRequest,
                manga = successState.manga,
                onDownloadFilterChanged = screenModel::setDownloadedFilter,
                onUnreadFilterChanged = screenModel::setUnreadFilter,
                onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                onSortModeChanged = screenModel::setSorting,
                onDisplayModeChanged = screenModel::setDisplayMode,
                onSetAsDefault = screenModel::setCurrentSettingsAsDefault,
                onResetToDefault = screenModel::resetToDefaultSettings,
                scanlatorFilterActive = successState.scanlatorFilterActive,
                onScanlatorFilterClicked = { showScanlatorsDialog = true },
            )
            MangaScreenModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = MangaTrackInfoDialogHomeScreen(
                        mangaId = successState.manga.id,
                        mangaTitle = successState.manga.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is MangaTrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }
            MangaScreenModel.Dialog.FullCover -> {
                val sm = rememberScreenModel { MangaCoverScreenModel(successState.manga.id) }
                val manga by sm.state.collectAsState()
                if (manga != null) {
                    val getContent = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent(),
                    ) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editCover(context, it)
                    }
                    MangaCoverDialog(
                        manga = manga!!,
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(manga) { manga!!.hasCustomCover() },
                        onShareClick = { sm.shareCover(context) },
                        onSaveClick = { sm.saveCover(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.deleteCustomCover(context)
                            }
                        },
                        onDismissRequest = onDismissRequest,
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }
            is MangaScreenModel.Dialog.SetMangaFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.manga.fetchInterval,
                    nextUpdate = dialog.manga.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    isManga = true,
                    onValueChanged = { interval: Int -> screenModel.setFetchInterval(dialog.manga, interval) }
                        .takeIf { screenModel.isUpdateIntervalEnabled },
                )
            }
        }

        if (showScanlatorsDialog) {
            ScanlatorFilterDialog(
                availableScanlators = successState.availableScanlators,
                excludedScanlators = successState.excludedScanlators,
                onDismissRequest = { showScanlatorsDialog = false },
                onConfirm = screenModel::setExcludedScanlators,
            )
        }

        if (lockDialog.isNotEmpty()) {
            ReadingOrderLockDialog(
                lockedOrders = lockDialog,
                onDismiss = { lockDialog = emptyList() },
                onViewOrder = { orderId ->
                    lockDialog = emptyList()
                    navigator.push(eu.kanade.tachiyomi.ui.readingorder.ReadingOrderEditorScreen(orderId))
                },
            )
        }
    }

    private fun continueReading(context: Context, unreadChapter: Chapter?) {
        if (unreadChapter != null) openChapter(context, unreadChapter)
    }

    private fun openChapter(context: Context, chapter: Chapter) {
        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
    }

    private fun openChapterWithLockCheck(
        context: Context,
        chapter: Chapter,
        scope: kotlinx.coroutines.CoroutineScope,
        getLockedReadingOrders: tachiyomi.domain.readingorder.interactor.GetLockedReadingOrders,
        onLocked: (List<tachiyomi.domain.readingorder.model.ReadingOrder>) -> Unit,
    ) {
        scope.launchIO {
            val lockedOrders = getLockedReadingOrders.await(chapter.mangaId)
            if (lockedOrders.isNotEmpty()) {
                onLocked(lockedOrders)
            } else {
                openChapter(context, chapter)
            }
        }
    }

    private fun getMangaUrl(manga_: Manga?, source_: MangaSource?): String? {
        val manga = manga_ ?: return null
        val source = source_ as? HttpSource ?: return null

        return try {
            source.getMangaUrl(manga.toSManga())
        } catch (e: Exception) {
            null
        }
    }

    private fun openMangaInWebView(navigator: Navigator, manga_: Manga?, source_: MangaSource?) {
        getMangaUrl(manga_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = manga_?.title,
                    sourceId = source_?.id,
                ),
            )
        }
    }

    private fun shareManga(context: Context, manga_: Manga?, source_: MangaSource?) {
        try {
            getMangaUrl(manga_, source_)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        context.stringResource(MR.strings.action_share),
                    ),
                )
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(GlobalMangaSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                // Set mode to MANGA so the library search targets manga content.
                Injekt.get<UiPreferences>().contentMode().set(ContentMode.MANGA)
                navigator.pop()
                LibraryTab.search(query)
            }
            is BrowseMangaSourceScreen -> {
                navigator.pop()
                previousController.search(query)
            }
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     *
     * @param genreName the search genre to the parent controller
     */
    private suspend fun performGenreSearch(
        navigator: Navigator,
        genreName: String,
        source: MangaSource,
    ) {
        if (navigator.size < 2) {
            return
        }

        val previousController = navigator.items[navigator.size - 2]
        if (previousController is BrowseMangaSourceScreen && source is HttpSource) {
            navigator.pop()
            previousController.searchGenre(genreName)
        } else {
            performSearch(navigator, genreName, global = false)
        }
    }

    /**
     * Copy Manga URL to Clipboard
     */
    private fun copyMangaUrl(context: Context, manga_: Manga?, source_: MangaSource?) {
        val manga = manga_ ?: return
        val source = source_ as? HttpSource ?: return
        val url = source.getMangaUrl(manga.toSManga())
        context.copyToClipboard(url, url)
    }
}
