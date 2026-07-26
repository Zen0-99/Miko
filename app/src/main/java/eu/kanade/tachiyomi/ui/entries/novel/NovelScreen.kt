package eu.kanade.tachiyomi.ui.entries.novel

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.entries.novel.components.rememberNovelAccentColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifNovelSourcesLoaded
import eu.kanade.domain.entries.novel.model.hasCustomCover
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.tachiyomi.source.novel.isLocalOrStub
import eu.kanade.presentation.entries.novel.NovelScreen
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.ui.entries.novel.track.NovelTrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.entries.suggestions.EntrySuggestionsScreen
import eu.kanade.tachiyomi.ui.entries.suggestions.toDirectEntryScreenOrNull
import eu.kanade.tachiyomi.ui.entries.suggestions.toGlobalSearchScreen
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import tachiyomi.presentation.core.screens.LoadingScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelDialog
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelSearchScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import eu.kanade.tachiyomi.ui.reader.novel.NovelHighlightsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelScreen(
    private val novelId: Long,
    val fromSource: Boolean = false,
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
        val screenModel =
            rememberScreenModel { NovelScreenModel(context, lifecycleOwner.lifecycle, novelId, fromSource) }

        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state is NovelScreenModel.State.Loading) {
            LoadingScreen()
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

        NovelScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            isTabletUi = isTabletUi(),
            chapterSwipeStartAction = screenModel.chapterSwipeStartAction,
            chapterSwipeEndAction = screenModel.chapterSwipeEndAction,
            navigateUp = navigator::pop,
            onChapterClicked = { chapter ->
                navigator.push(NovelReaderScreen(novelId, chapter.id))
            },
            onDownloadChapter = screenModel::runChapterDownloadActions.takeIf { !successState.source.isLocalOrStub() },
            onAddToLibraryClicked = {
                screenModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onCoverClick = { screenModel.showFullCoverDialog() },
            onWebViewClicked = {
                openNovelInWebView(navigator, screenModel.novel, screenModel.source)
            }.takeIf { isHttpSource },
            onWebViewLongClicked = {
                copyNovelUrl(context, screenModel.novel, screenModel.source)
            }.takeIf { isHttpSource },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it, screenModel.source!!) } },
            onFilterButtonClicked = screenModel::showSettingsDialog,
            onBookmarkFilterClicked = {
                screenModel.setBookmarkedFilter(
                    when (successState.novel.bookmarkedFilter) {
                        tachiyomi.core.common.preference.TriState.DISABLED -> tachiyomi.core.common.preference.TriState.ENABLED_IS
                        tachiyomi.core.common.preference.TriState.ENABLED_IS -> tachiyomi.core.common.preference.TriState.DISABLED
                        tachiyomi.core.common.preference.TriState.ENABLED_NOT -> tachiyomi.core.common.preference.TriState.DISABLED
                    },
                )
            },
            onHighlightsClicked = {
                navigator.push(NovelHighlightsScreen(successState.novel.title, successState.novel.id))
            }.takeIf { successState.novel.favorite },
            onTrackingClicked = { screenModel.showTrackSheet() },
            onRefresh = screenModel::fetchAllFromSource,
            onContinueReading = {
                val firstUnread = successState.nextContinueChapter
                navigator.push(NovelReaderScreen(novelId, firstUnread?.chapter?.id))
            },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            onShareClicked = { shareNovel(context, screenModel.novel, screenModel.source) }.takeIf { isHttpSource },
            onDownloadActionClicked = screenModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },
            onEditCollectionClicked = screenModel::showChangeCollectionDialog.takeIf { successState.novel.favorite },
            onEditNovel = { title, author, desc, status, tags ->
                screenModel.updateNovelMetadata(title, author, desc, status, tags)
            },
            onMigrateClicked = {
                navigator.push(MigrateNovelSearchScreen(successState.novel.id))
            }.takeIf { successState.novel.favorite },
            onMarkAllReadClicked = { screenModel.markAllRead() },
            onMarkAllUnreadClicked = { screenModel.markAllUnread() },
            onRefreshTrackingClicked = { screenModel.refreshTracking() },
            onRemoveAllDownloadsClicked = { screenModel.deleteAllDownloads() }
                .takeIf { !successState.source.isLocalOrStub() },
            onRemoveNonBookmarkedDownloadsClicked = { screenModel.deleteNonBookmarkedDownloads() }
                .takeIf { !successState.source.isLocalOrStub() },
            onRemoveReadDownloadsClicked = { screenModel.deleteReadDownloads() }
                .takeIf { !successState.source.isLocalOrStub() },
            onClickLinkedSources = { screenModel.showLinkedSourcesDialog() },
            onMultiBookmarkClicked = screenModel::bookmarkChapters,
            onMultiMarkAsReadClicked = screenModel::markChaptersRead,
            onMarkPreviousAsReadClicked = screenModel::markPreviousChapterRead,
            onMultiDeleteClicked = screenModel::showDeleteChapterDialog,
            onChapterSwipe = screenModel::chapterSwipe,
            onChapterSelected = screenModel::toggleSelection,
            onAllChapterSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
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
            onOpenChapterInWebView = { chapter: tachiyomi.domain.items.chapter.model.NovelChapter ->
                openChapterInWebView(navigator, chapter, screenModel.source)
            }.takeIf { isHttpSource },
            onMarkPreviousChaptersAsRead = screenModel::markPreviousChaptersAsRead,
            onMarkChapterRangeAsRead = screenModel::markChapterRangeAsRead,
        )

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
            NovelScreenModel.Dialog.FullCover -> {
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
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(novelObj) {
                            novelObj.hasCustomCover()
                        },
                        onShareClick = { sm.shareCover(context) },
                        onSaveClick = { sm.saveCover(context) },
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
            NovelScreenModel.Dialog.LinkedSources -> {
                eu.kanade.presentation.entries.novel.LinkedSourcesDialog(
                    loadLinked = { screenModel.loadLinkedNovelsForDialog() },
                    loadCandidates = { screenModel.loadFavoritesForLinking() },
                    onLink = screenModel::linkNovelSource,
                    onUnlink = screenModel::unlinkNovelSource,
                    onMakePrimary = screenModel::makeLinkedPrimary,
                    onOpenNovel = { navigator.push(NovelScreen(it)) },
                    onRefreshAll = screenModel::refreshLinkedSources,
                    onDismissRequest = onDismissRequest,
                )
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

    private fun openChapterInWebView(
        navigator: Navigator,
        chapter: tachiyomi.domain.items.chapter.model.NovelChapter,
        source_: Any?,
    ) {
        val source = source_ as? NovelHttpSource ?: return
        try {
            val sChapter = eu.kanade.tachiyomi.novelsource.model.SNovelChapter.create().apply {
                url = chapter.url
                name = chapter.name
            }
            val url = source.getChapterUrl(sChapter)
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = chapter.name,
                    sourceId = source.id,
                ),
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get novel chapter URL" }
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

    private fun copyNovelUrl(context: Context, novel_: Novel?, source_: Any?) {
        val novel = novel_ ?: return
        val source = source_ as? NovelHttpSource ?: return
        val url = source.getNovelUrl(novel.toSNovel())
        context.copyToClipboard(url, url)
    }
}
