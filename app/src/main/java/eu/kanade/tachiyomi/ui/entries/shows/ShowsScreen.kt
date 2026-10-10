package eu.kanade.tachiyomi.ui.entries.shows

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
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
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.domain.entries.shows.model.hasCustomBackground
import eu.kanade.domain.entries.shows.model.hasCustomCover
import eu.kanade.domain.entries.shows.model.toSAnime
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.presentation.collection.components.ChangeCollectionDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.components.DeleteItemsDialog
import eu.kanade.presentation.entries.components.EntryDetailSkeleton
import eu.kanade.presentation.entries.components.EntryHeroLoadingPreview
import eu.kanade.presentation.entries.components.SKELETON_FADE_MS
import eu.kanade.presentation.entries.components.SKELETON_LATE_ARRIVAL_MS
import eu.kanade.presentation.entries.components.SetIntervalDialog
import eu.kanade.presentation.entries.components.isNavEntering
import eu.kanade.presentation.entries.components.rememberCachedAccentColor
import eu.kanade.presentation.entries.components.rememberEntryAccentColor
import eu.kanade.presentation.entries.manga.carrierSheetContainerColor
import eu.kanade.presentation.entries.shows.DuplicateShowsDialog
import eu.kanade.presentation.entries.shows.EpisodeOptionsDialogScreen
import eu.kanade.presentation.entries.shows.EpisodeSettingsDialog
import eu.kanade.presentation.entries.shows.SeasonSettingsDialog
import eu.kanade.presentation.entries.shows.ShowsScreen
import eu.kanade.presentation.entries.shows.components.EpisodeDownloadAction
import eu.kanade.presentation.entries.shows.components.ShowsImagesDialog
import eu.kanade.presentation.metadata.stream.StreamPickerSheet
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsGesturesScreen.SkipIntroLengthDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.LocalNavAnimatedVisibilityScope
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import eu.kanade.tachiyomi.metadata.stream.StreamResolver
import eu.kanade.tachiyomi.source.anime.isLocalOrStub
import eu.kanade.tachiyomi.source.anime.isSourceForTorrents
import eu.kanade.tachiyomi.ui.browse.shows.migration.all.MigrateShowsAllScreen
import eu.kanade.tachiyomi.ui.browse.shows.migration.search.MigrateShowsDialog
import eu.kanade.tachiyomi.ui.browse.shows.migration.search.MigrateShowsDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.shows.migration.shows.season.MigrateSeasonSelectScreen
import eu.kanade.tachiyomi.ui.browse.shows.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.shows.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.collection.CollectionsTab
import eu.kanade.tachiyomi.ui.comments.CommentsSheetScreen
import eu.kanade.tachiyomi.ui.entries.shows.track.AnimeTrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.entries.suggestions.EntrySuggestionsScreen
import eu.kanade.tachiyomi.ui.entries.suggestions.toDirectEntryScreenOrNull
import eu.kanade.tachiyomi.ui.entries.suggestions.toGlobalSearchScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.metadata.stream.StreamResolverScreenModel
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.EntryCoverMetadata
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ShowsScreen(
    private val animeId: Long,
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
    /**
     * Id the caller's shared elements are keyed on when it differs from
     * [animeId] — the extension→canonical redirect re-pushes this screen
     * with the canonical id, and without the override the Loading
     * preview's `hero-image-` key would no longer match the home hero's
     * (still keyed on the pushed extension id), un-pairing the artwork
     * mid-flight.
     */
    private val sharedIdOverride: Long? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val screenModel =
            rememberScreenModel { ShowsScreenModel(context, lifecycleOwner.lifecycle, animeId, fromSource) }

        val state by screenModel.state.collectAsStateWithLifecycle()
        val redirectId by screenModel.redirectToAnimeId.collectAsStateWithLifecycle()

        // Extension-source duplicate of a canonical Cinemeta entry —
        // swap to the canonical entry so there's one entry per anime.
        LaunchedEffect(redirectId) {
            redirectId?.let {
                navigator.replace(
                    ShowsScreen(
                        it,
                        fromSource,
                        previewCover,
                        previewTitle,
                        previewLogo,
                        previewResumeLabel,
                        previewResumeEnabled,
                        previewHasGenres = previewHasGenres,
                        sharedIdOverride = sharedIdOverride ?: animeId,
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
        LaunchedEffect(state is ShowsScreenModel.State.Success) {
            if (state is ShowsScreenModel.State.Success && firstSuccessNanos < 0L) {
                firstSuccessNanos = System.nanoTime()
            }
        }
        val earlySuccess = state is ShowsScreenModel.State.Success &&
            (firstSuccessNanos.takeIf { it >= 0L } ?: System.nanoTime()) - mountNanos <
            SKELETON_LATE_ARRIVAL_MS * 1_000_000L
        val skeletonWanted = previewCover != null &&
            !isTabletUi() &&
            (state is ShowsScreenModel.State.Loading || (entering && !earlySuccess))
        val skeletonAlpha by animateFloatAsState(
            targetValue = if (skeletonWanted) 1f else 0f,
            animationSpec = tween(SKELETON_FADE_MS),
            label = "detailSkeletonFade",
        )
        val seedHeightsPx = remember { mutableStateMapOf<Any, Int>() }
        if (skeletonWanted) {
            EntryDetailSkeleton(
                cover = previewCover,
                sharedKey = "hero-image-${sharedIdOverride ?: animeId}",
                heroContentPadding = 320.dp,
                title = previewTitle,
                logo = previewLogo,
                navigateUp = navigator::pop,
                isManga = false,
                panHorizontally = true,
                favorite = (previewCover as? tachiyomi.domain.entries.shows.model.AnimeCover)
                    ?.isAnimeFavorite == true,
                isVideo = true,
                showGhosts = showGhosts,
                accentColor = rememberCachedAccentColor(
                    animeId,
                    EntryCoverMetadata.EntryType.SHOWS,
                ),
                resumeLabel = previewResumeLabel,
                resumeEnabled = previewResumeEnabled,
                genreGhostVisible = previewHasGenres,
                onItemHeight = { key, height -> seedHeightsPx[key] = height },
            )
            return
        }
        if (state is ShowsScreenModel.State.Loading) {
            EntryHeroLoadingPreview(
                cover = previewCover,
                sharedKey = "hero-image-${sharedIdOverride ?: animeId}",
                heroContentPadding = 320.dp,
                title = previewTitle,
                logo = previewLogo,
                panOversize = true,
            )
            return
        }

        val successState = state as ShowsScreenModel.State.Success
        val isAnimeHttpSource = remember { successState.source is AnimeHttpSource }

        // Cinemeta entry detection — source ID 0 with URL prefix "cinemeta:" means it's a
        // Cinemeta metadata entry that needs stream resolution to find a playable source.
        // (LocalAnimeSource also uses ID 0, so we distinguish by URL prefix.)
        val isCinemetaEntry = successState.anime.source == 0L && successState.anime.url.startsWith("cinemeta:")
        var showStreamPicker by remember { mutableStateOf(false) }
        var streamPickerDownloadMode by remember { mutableStateOf(false) }
        var pendingEpisodeNumber by remember { mutableStateOf<Double?>(null) }
        var pendingEpisodeName by remember { mutableStateOf<String?>(null) }
        var pendingEpisodeUrl by remember { mutableStateOf<String?>(null) }
        var pendingDownloadItems by remember { mutableStateOf<List<EpisodeList.Item>>(emptyList()) }
        val streamResolverSM =
            rememberScreenModel { StreamResolverScreenModel(Injekt.get(), Injekt.get(), Injekt.get(), Injekt.get()) }
        val streamResolverState by streamResolverSM.state.collectAsState()
        val streamResolver = remember { Injekt.get<StreamResolver>() }

        val extractedAccent = rememberEntryAccentColor(
            entryId = successState.anime.id,
            cover = successState.anime,
            type = EntryCoverMetadata.EntryType.SHOWS,
        )
        LaunchedEffect(extractedAccent) {
            if (extractedAccent != successState.accentColor) {
                screenModel.setAccentColor(extractedAccent)
            }
        }

        LaunchedEffect(successState.anime, screenModel.source) {
            if (isAnimeHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getAnimeUrl(screenModel.anime, screenModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get anime URL" }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            ShowsScreen(
                state = successState,
                previewCover = previewCover,
                seedHeightsPx = seedHeightsPx,
                snackbarHostState = screenModel.snackbarHostState,
                nextUpdate = successState.anime.expectedNextUpdate,
                isTabletUi = isTabletUi(),
                episodeSwipeStartAction = screenModel.episodeSwipeStartAction,
                episodeSwipeEndAction = screenModel.episodeSwipeEndAction,
                showNextEpisodeAirTime = screenModel.showNextEpisodeAirTime,
                alwaysUseExternalPlayer = screenModel.alwaysUseExternalPlayer,
                navigateUp = navigator::pop,
                onEpisodeClicked = { episode, alt ->
                    logcat(LogPriority.DEBUG) {
                        "onEpisodeClicked: isCinemetaEntry=$isCinemetaEntry, " +
                            "source=${successState.anime.source}, url=${successState.anime.url}"
                    }
                    if (isCinemetaEntry) {
                        // Cinemeta entry — resolve streaming source before playing
                        pendingEpisodeNumber = episode.episodeNumber
                        pendingEpisodeName = episode.name
                        pendingEpisodeUrl = episode.url
                        streamResolverSM.resolve(successState.anime, episode.episodeNumber, episode.url, episode.name)
                        showStreamPicker = true
                    } else {
                        scope.launchIO {
                            if (screenModel.isTorrentEnabled() && successState.source.isSourceForTorrents()) {
                                TorrentServerService.startInProcess()
                            }
                            val extPlayer = screenModel.alwaysUseExternalPlayer != alt
                            openEpisode(context, episode, extPlayer)
                        }
                    }
                },
                onDownloadEpisode = if (isCinemetaEntry) {
                    // Cinemeta episodes have no downloadable source of their own —
                    // START opens the source picker (download mode) so the user
                    // picks which extension to pull the episode from. CANCEL /
                    // DELETE forward to the mapped source download.
                    download@{ items, action ->
                        when (action) {
                            EpisodeDownloadAction.START, EpisodeDownloadAction.START_NOW -> {
                                val episode = items.firstOrNull()?.episode ?: return@download
                                pendingDownloadItems = items
                                pendingEpisodeNumber = episode.episodeNumber
                                pendingEpisodeName = episode.name
                                pendingEpisodeUrl = episode.url
                                streamPickerDownloadMode = true
                                streamResolverSM.resolve(
                                    successState.anime,
                                    episode.episodeNumber,
                                    episode.url,
                                    episode.name,
                                )
                                showStreamPicker = true
                            }
                            else -> screenModel.runCinemetaDownloadActions(items, action)
                        }
                    }
                } else {
                    screenModel::runEpisodeDownloadActions.takeIf {
                        !successState.source.isLocalOrStub() && successState.anime.fetchType == FetchType.Episodes
                    }
                },
                onAddToLibraryClicked = {
                    screenModel.toggleFavorite()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onWebViewClicked = {
                    openAnimeInWebView(
                        navigator,
                        screenModel.anime,
                        screenModel.source,
                    )
                }.takeIf { isAnimeHttpSource },
                onTrackingClicked = {
                    if (!successState.hasLoggedInTrackers) {
                        navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                    } else {
                        screenModel.showTrackDialog()
                    }
                }.takeIf { successState.anime.fetchType == FetchType.Episodes },
                onCommentsClicked = screenModel::showCommentsDialog
                    .takeIf { screenModel.commentsEnabled },
                onFindProviderClicked = screenModel::showFindProviderDialog,
                onTagSearch = { scope.launch { performGenreSearch(navigator, it, screenModel.source!!) } },
                onFilterButtonClicked = screenModel::showSettingsDialog,
                onRefresh = screenModel::fetchAllFromSource,
                onContinueWatching = {
                    scope.launchIO {
                        val extPlayer = screenModel.alwaysUseExternalPlayer
                        val episode = screenModel.getNextUnseenEpisode() ?: return@launchIO
                        val playable = streamResolver.resolvePlayableByIds(episode.animeId, episode.id)
                        if (playable != null) {
                            openEpisode(context, playable.second, extPlayer)
                        } else {
                            // Cinemeta entry with no cached mapping — route
                            // through the source picker like an episode click.
                            withUIContext {
                                pendingEpisodeNumber = episode.episodeNumber
                                pendingEpisodeName = episode.name
                                pendingEpisodeUrl = episode.url
                                streamResolverSM.resolve(
                                    successState.anime,
                                    episode.episodeNumber,
                                    episode.url,
                                    episode.name,
                                )
                                showStreamPicker = true
                            }
                        }
                    }
                },
                onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
                onCoverClicked = screenModel::showImagesDialog,
                onShareClicked = {
                    shareAnime(
                        context,
                        screenModel.anime,
                        screenModel.source,
                    )
                }.takeIf { isAnimeHttpSource },
                onDownloadActionClicked = screenModel::runDownloadAction.takeIf {
                    !successState.source.isLocalOrStub() && successState.anime.fetchType == FetchType.Episodes
                },
                onEditCollectionClicked = screenModel::showChangeCollectionDialog.takeIf {
                    successState.anime.favorite
                },
                onEditFetchIntervalClicked = screenModel::showSetAnimeFetchIntervalDialog.takeIf {
                    successState.anime.favorite
                },
                onMigrateClicked = {
                    navigator.push(MigrateShowsAllScreen(listOf(successState.anime.id), successState.anime.title))
                }.takeIf { successState.anime.favorite },
                changeAnimeSkipIntro = screenModel::showAnimeSkipIntroDialog
                    .takeIf { successState.anime.favorite && successState.anime.fetchType == FetchType.Episodes },
                onMultiBookmarkClicked = screenModel::bookmarkEpisodes,
                onMultiFillermarkClicked = screenModel::fillermarkEpisodes,
                onMultiMarkAsSeenClicked = screenModel::markEpisodesSeen,
                onMarkPreviousAsSeenClicked = screenModel::markPreviousEpisodeSeen,
                onMultiDeleteClicked = screenModel::showDeleteEpisodeDialog,
                onEpisodeSwipe = screenModel::episodeSwipe,
                onEpisodeSelected = screenModel::toggleSelection,
                onAllEpisodeSelected = screenModel::toggleAllSelection,
                onInvertSelection = screenModel::invertSelection,
                onMarkAllSeenClicked = { screenModel.markAllSeen() },
                onMarkAllUnseenClicked = { screenModel.markAllUnseen() },
                onRemoveAllDownloadsClicked = { screenModel.deleteAllDownloads() }
                    .takeIf { !successState.source.isLocalOrStub() },
                onRemoveNonBookmarkedDownloadsClicked = { screenModel.deleteNonBookmarkedDownloads() }
                    .takeIf { !successState.source.isLocalOrStub() },
                onRemoveSeenDownloadsClicked = { screenModel.deleteSeenDownloads() }
                    .takeIf { !successState.source.isLocalOrStub() },
                onSeasonClicked = {
                    navigator.push(ShowsScreen(it.id))
                },
                onContinueWatchingClicked = {
                    scope.launchIO {
                        val episode = screenModel.getNextUnseenEpisode(it.anime)
                        episode?.let { ep ->
                            val playable = streamResolver.resolvePlayableByIds(ep.animeId, ep.id)
                            if (playable != null) {
                                openEpisode(context, playable.second, screenModel.alwaysUseExternalPlayer)
                            }
                        }
                    }
                },
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
                                entryUrl = successState.anime.url,
                            ),
                        )
                    }
                },
                onRetrySuggestions = { screenModel.retrySuggestions() },
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
                        heroContentPadding = 320.dp,
                        title = previewTitle,
                        logo = previewLogo,
                        navigateUp = navigator::pop,
                        isManga = false,
                        panHorizontally = true,
                        favorite = (previewCover as? tachiyomi.domain.entries.shows.model.AnimeCover)
                            ?.isAnimeFavorite == true,
                        isVideo = true,
                        showGhosts = showGhosts,
                        accentColor = rememberCachedAccentColor(
                            animeId,
                            EntryCoverMetadata.EntryType.SHOWS,
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
            if (screenModel.autoOpenTrack && screenModel.isFromChangeCollection) {
                screenModel.isFromChangeCollection = false
                screenModel.showTrackDialog()
            }
        }
        when (val dialog = successState.dialog) {
            null -> {}
            is ShowsScreenModel.Dialog.ChangeCollection -> {
                ChangeCollectionDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCollections = { navigator.push(CollectionsTab) },
                    onConfirm = { include, _ ->
                        screenModel.moveAnimeToCollectionsAndAddToLibrary(dialog.anime, include)
                    },
                    accentColor = successState.accentColor,
                )
            }
            is ShowsScreenModel.Dialog.DeleteEpisodes -> {
                DeleteItemsDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteEpisodes(dialog.episodes)
                    },
                    isManga = false,
                )
            }

            is ShowsScreenModel.Dialog.DuplicateAnime -> {
                DuplicateShowsDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenAnime = { navigator.push(ShowsScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        screenModel.showMigrateDialog(dialog.duplicate)
                    },
                )
            }

            is ShowsScreenModel.Dialog.Migrate -> {
                MigrateShowsDialog(
                    oldAnime = dialog.oldAnime,
                    newAnime = dialog.newAnime,
                    screenModel = MigrateShowsDialogScreenModel(),
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(ShowsScreen(dialog.oldAnime.id)) },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(dialog.oldAnime, dialog.newAnime)) },
                    onPopScreen = { navigator.replace(ShowsScreen(dialog.newAnime.id)) },
                )
            }
            ShowsScreenModel.Dialog.EpisodeSettingsSheet -> EpisodeSettingsDialog(
                onDismissRequest = onDismissRequest,
                anime = successState.anime,
                onDownloadFilterChanged = screenModel::setDownloadedFilter,
                onUnseenFilterChanged = screenModel::setUnseenFilter,
                onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                onFillermarkedFilterChanged = screenModel::setFillermarkedFilter,
                onSortModeChanged = screenModel::setSorting,
                onDisplayModeChanged = screenModel::setDisplayMode,
                onShowPreviewsEnabled = screenModel::showEpisodePreviews,
                onShowSummariesEnabled = screenModel::showEpisodeSummaries,
                onSetAsDefault = screenModel::setCurrentSettingsAsDefault,
                accentColor = successState.accentColor,
            )
            ShowsScreenModel.Dialog.SeasonSettingsSheet -> SeasonSettingsDialog(
                onDismissRequest = onDismissRequest,
                anime = successState.anime,
                onDownloadFilterChanged = screenModel::setSeasonDownloadedFilter,
                onUnseenFilterChanged = screenModel::setSeasonUnseenFilter,
                onStartedFilterChanged = screenModel::setSeasonStartedFilter,
                onCompletedFilterChanged = screenModel::setSeasonCompletedFilter,
                onBookmarkedFilterChanged = screenModel::setSeasonBookmarkedFilter,
                onFillermarkedFilterChanged = screenModel::setSeasonFillermarkedFilter,
                onSortModeChanged = screenModel::setSeasonSorting,
                onDisplayGridModeChanged = screenModel::setSeasonDisplayGridMode,
                onDisplayGridSizeChanged = screenModel::setSeasonDisplayGridSize,
                onOverlayDownloadedChanged = screenModel::setSeasonDownloadOverlay,
                onOverlayUnseenChanged = screenModel::setSeasonUnseenOverlay,
                onOverlayLocalChanged = screenModel::setSeasonLocalOverlay,
                onOverlayLangChanged = screenModel::setSeasonLangOverlay,
                onOverlayContinueChanged = screenModel::setSeasonContinueOverlay,
                onDisplayModeChanged = screenModel::setSeasonDisplayMode,
                onSetAsDefault = screenModel::setSeasonCurrentSettingsAsDefault,
                accentColor = successState.accentColor,
            )
            ShowsScreenModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = AnimeTrackInfoDialogHomeScreen(
                        animeId = successState.anime.id,
                        animeTitle = successState.anime.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is AnimeTrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }
            ShowsScreenModel.Dialog.Comments -> {
                NavigatorAdaptiveSheet(
                    screen = CommentsSheetScreen(
                        carrier = Carrier.SHOWS,
                        entryId = successState.anime.id,
                        expectedComments = successState.commentsCount,
                    ),
                    containerColor = carrierSheetContainerColor(successState.accentColor),
                    onDismissRequest = onDismissRequest,
                )
            }
            ShowsScreenModel.Dialog.FindProvider -> {
                val search = successState.providerSearch
                if (search != null) {
                    eu.kanade.presentation.entries.components.FindProviderDialog(
                        entryTitle = successState.anime.title,
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
            is ShowsScreenModel.Dialog.FullImages -> {
                val sm = rememberScreenModel { ShowsImageScreenModel(successState.anime.id) }
                val anime by sm.state.collectAsState()
                if (anime != null) {
                    val getContent = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent(),
                    ) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editImage(context, it)
                    }
                    ShowsImagesDialog(
                        anime = anime!!,
                        coverData = dialog.coverData,
                        coverOptions = successState.coverOptions,
                        pinnedCoverKey = successState.coverPinKey,
                        snackbarHostState = sm.snackbarHostState,
                        pagerState = sm.pagerState,
                        isCustomCover = remember(anime) { anime!!.hasCustomCover() },
                        isCustomBackground = remember(anime) { anime!!.hasCustomBackground() },
                        onShareClick = { sm.shareImage(context) },
                        onSaveClick = { sm.saveImage(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.deleteCustomImage(context)
                            }
                        },
                        onSelectCover = { option ->
                            if (option != null) {
                                sm.pinCover(context, option)
                            } else {
                                sm.unpinCover(context)
                            }
                        },
                        onDismissRequest = {
                            sm.flushImageToast(context)
                            onDismissRequest()
                        },
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }
            is ShowsScreenModel.Dialog.SetAnimeFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.anime.fetchInterval,
                    nextUpdate = dialog.anime.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    isManga = false,
                    onValueChanged = { interval: Int -> screenModel.setFetchInterval(dialog.anime, interval) }
                        .takeIf { screenModel.isUpdateIntervalEnabled },
                )
            }
            ShowsScreenModel.Dialog.ChangeAnimeSkipIntro -> {
                fun updateSkipIntroLength(newLength: Long) {
                    scope.launchIO {
                        screenModel.setAnimeViewerFlags.awaitSetSkipIntroLength(animeId, newLength)
                    }
                }
                SkipIntroLengthDialog(
                    initialSkipIntroLength = if (!successState.anime.skipIntroDisable &&
                        successState.anime.skipIntroLength == 0
                    ) {
                        screenModel.gesturePreferences.defaultIntroLength().get()
                    } else {
                        successState.anime.skipIntroLength
                    },
                    onDismissRequest = onDismissRequest,
                    onValueChanged = {
                        updateSkipIntroLength(it.toLong())
                        onDismissRequest()
                    },
                )
            }
            is ShowsScreenModel.Dialog.ShowQualities -> {
                EpisodeOptionsDialogScreen.onDismissDialog = onDismissRequest
                val episodeTitle = if (dialog.anime.displayMode == Anime.EPISODE_DISPLAY_NUMBER) {
                    stringResource(
                        AYMR.strings.display_mode_episode,
                        formatEpisodeNumber(dialog.episode.episodeNumber),
                    )
                } else {
                    dialog.episode.name
                }
                NavigatorAdaptiveSheet(
                    screen = EpisodeOptionsDialogScreen(
                        useExternalDownloader = screenModel.useExternalDownloader,
                        episodeTitle = episodeTitle,
                        episodeId = dialog.episode.id,
                        animeId = dialog.anime.id,
                        sourceId = dialog.source.id,
                    ),
                    onDismissRequest = onDismissRequest,
                )
            }
        }

        // Stream picker for Cinemeta entries — playback mode resolves a
        // stream, download mode queues the episode from the picked source.
        if (showStreamPicker) {
            StreamPickerSheet(
                state = streamResolverState,
                downloadMode = streamPickerDownloadMode,
                onDismiss = {
                    showStreamPicker = false
                    streamPickerDownloadMode = false
                    streamResolverSM.reset()
                },
                onCandidateSelected = { candidate ->
                    if (streamPickerDownloadMode) {
                        if (candidate is StreamResolver.StreamCandidate.Extension) {
                            screenModel.downloadCinemetaEpisodes(pendingDownloadItems, candidate)
                        }
                        showStreamPicker = false
                        streamPickerDownloadMode = false
                        streamResolverSM.reset()
                    } else {
                        streamResolverSM.selectCandidate(
                            cinemetaAnime = successState.anime,
                            candidate = candidate,
                            episodeNumber = pendingEpisodeNumber,
                            episodeName = pendingEpisodeName,
                            episodeUrl = pendingEpisodeUrl,
                        )
                    }
                },
                onSearchManually = {
                    showStreamPicker = false
                    streamResolverSM.reset()
                    navigator.push(GlobalAnimeSearchScreen(successState.anime.title))
                },
                onChangeSource = {
                    streamResolverSM.clearCachedMapping(successState.anime, pendingEpisodeUrl, pendingEpisodeNumber)
                    streamResolverSM.resolve(
                        successState.anime,
                        pendingEpisodeNumber,
                        pendingEpisodeUrl,
                        pendingEpisodeName,
                    )
                },
            )
        }

        // Launch player when episode is resolved (playback mode only)
        LaunchedEffect(streamResolverState) {
            val resolved = streamResolverState as? StreamResolverScreenModel.State.EpisodeResolved
            if (resolved != null && showStreamPicker && !streamPickerDownloadMode) {
                showStreamPicker = false
                streamResolverSM.reset()
                scope.launchIO {
                    try {
                        val extPlayer = screenModel.alwaysUseExternalPlayer
                        val episode = resolved.episode
                        android.util.Log.d(
                            "ShowsScreen",
                            "Launching player: animeId=${episode.animeId}, episodeId=${episode.id}, " +
                                "name=${episode.name}, url=${episode.url}",
                        )
                        if (screenModel.isTorrentEnabled()) {
                            TorrentServerService.startInProcess()
                        }
                        // Stremio streams carry their magnet link in streamUrl —
                        // hand it to the player as a pre-resolved hoster list so
                        // the magnet pipeline (MagnetResolver → TorrServer) runs.
                        val hosterList = resolved.streamUrl?.let { url ->
                            listOf(Video(videoUrl = url, videoTitle = episode.name)).toHosterList()
                        }
                        openEpisode(context, episode, extPlayer, hosterList)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to launch player for resolved episode" }
                        withUIContext {
                            context.toast("Failed to play episode: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private suspend fun openEpisode(
        context: Context,
        episode: Episode,
        useExternalPlayer: Boolean,
        hosterList: List<Hoster>? = null,
    ) {
        withIOContext {
            android.util.Log.d(
                "ShowsScreen",
                "openEpisode: animeId=${episode.animeId}, episodeId=${episode.id}, " +
                    "name=${episode.name}, url=${episode.url}",
            )
            try {
                MainActivity.startPlayerActivity(
                    context,
                    episode.animeId,
                    episode.id,
                    useExternalPlayer,
                    hosterList = hosterList,
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "startPlayerActivity failed" }
                withUIContext { context.toast("Failed to launch player: ${e.message}") }
            }
        }
    }

    private fun getAnimeUrl(anime_: Anime?, source_: AnimeSource?): String? {
        val anime = anime_ ?: return null
        val source = source_ as? AnimeHttpSource ?: return null

        return try {
            source.getAnimeUrl(anime.toSAnime())
        } catch (e: Exception) {
            null
        }
    }

    private fun openAnimeInWebView(navigator: Navigator, anime_: Anime?, source_: AnimeSource?) {
        getAnimeUrl(anime_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = anime_?.title,
                    sourceId = source_?.id,
                ),
            )
        }
    }

    private fun shareAnime(context: Context, anime_: Anime?, source_: AnimeSource?) {
        try {
            getAnimeUrl(anime_, source_)?.let { url ->
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
            navigator.push(GlobalAnimeSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                // Set mode to SHOWS so the library search targets shows content.
                Injekt.get<UiPreferences>().contentMode().set(ContentMode.SHOWS)
                navigator.pop()
                LibraryTab.search(query)
            }
            is BrowseAnimeSourceScreen -> {
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
        source: AnimeSource,
    ) {
        if (navigator.size < 2) {
            return
        }

        val previousController = navigator.items[navigator.size - 2]
        if (previousController is BrowseAnimeSourceScreen && source is AnimeHttpSource) {
            navigator.pop()
            previousController.searchGenre(genreName)
        } else {
            performSearch(navigator, genreName, global = false)
        }
    }
}
