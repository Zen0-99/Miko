package eu.kanade.presentation.entries.shows

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.entries.shows.model.episodesFiltered
import eu.kanade.domain.entries.shows.model.hasCustomCover
import eu.kanade.domain.entries.shows.model.seasonsFiltered
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.components.fullDateText
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.EntryScreenItem
import eu.kanade.presentation.entries.components.EnhancedEntryInfoBox
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.EntryChapterHeader
import eu.kanade.presentation.entries.components.EntryContinueRow
import eu.kanade.presentation.entries.components.EntryFindProviderButton
import eu.kanade.presentation.entries.components.EntryGenreRow
import eu.kanade.presentation.entries.components.EntryNextUpAction
import eu.kanade.presentation.entries.components.EntryToolbar
import eu.kanade.presentation.entries.components.EntryViewCommentsButton
import eu.kanade.presentation.entries.components.MissingItemCountListItem
import eu.kanade.presentation.entries.components.enhancedEntryStatusText
import eu.kanade.presentation.entries.components.rememberEntryAccentColor
import eu.kanade.presentation.entries.shows.components.EpisodeDownloadAction
import eu.kanade.presentation.entries.shows.components.ExpandableAnimeDescription
import eu.kanade.presentation.entries.shows.components.NextEpisodeAiringListItem
import eu.kanade.presentation.entries.shows.components.NuvioEpisodeCard
import eu.kanade.presentation.entries.shows.components.SeasonPillRow
import eu.kanade.presentation.entries.shows.components.ShowsSeasonListItem
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.presentation.util.formatRuntime
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.download.shows.model.ShowsDownload
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.ui.browse.shows.extension.details.AnimeSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.entries.shows.EpisodeList
import eu.kanade.tachiyomi.ui.entries.shows.ShowsScreenModel
import eu.kanade.tachiyomi.ui.entries.shows.ShowsSeasonItem
import eu.kanade.tachiyomi.util.EntryCoverMetadata
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.coroutines.delay
import miko.domain.shows.SeasonAnime
import miko.domain.shows.SeasonDisplayMode
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.service.missingEntriesCount
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit
import kotlin.time.Instant

@Composable
fun ShowsScreen(
    state: ShowsScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    navigateUp: () -> Unit,
    onEpisodeClicked: (episode: Episode, alt: Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onCommentsClicked: (() -> Unit)? = null,
    /** Find Content Provider (17-02) — replaces the comments button on
     *  canonical entries with zero bound carriers. */
    onFindProviderClicked: (() -> Unit)? = null,

    // For tags menu
    onTagSearch: (String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog — receives the artwork currently shown in the hero
    // (e.g. the selected season's poster), not necessarily the persisted cover.
    onCoverClicked: (Any?) -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCollectionClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    changeAnimeSkipIntro: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode, Boolean) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,

    // For episode swipe
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Episode selection
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // For overflow menu
    onMarkAllSeenClicked: (() -> Unit)? = null,
    onMarkAllUnseenClicked: (() -> Unit)? = null,
    onRemoveAllDownloadsClicked: (() -> Unit)? = null,
    onRemoveNonBookmarkedDownloadsClicked: (() -> Unit)? = null,
    onRemoveSeenDownloadsClicked: (() -> Unit)? = null,

    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onContinueWatchingClicked: ((SeasonAnime) -> Unit)?,
    onSuggestionClick: (SuggestionItem) -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
    onRetrySuggestions: () -> Unit = {},
    /** Artwork the caller already shows for this entry (home hero) —
     *  underlays the shared hero image so the morph never draws empty. */
    previewCover: Any? = null,
    /** Skeleton item heights measured during Loading, keyed by
     *  [EntryScreenItem] — real items mount constrained to the ghost's
     *  height for one frame, then animateContentSize glides every
     *  boundary the ghosts got wrong so nothing snaps on swap. */
    seedHeightsPx: Map<Any, Int> = emptyMap(),
) {
    val context = LocalContext.current
    val onCopyTagToClipboard: (tag: String) -> Unit = {
        if (it.isNotEmpty()) {
            context.copyToClipboard(it, it)
        }
    }

    val navigator = LocalNavigator.currentOrThrow
    val onSettingsClicked: (() -> Unit)? = {
        navigator.push(AnimeSourcePreferencesScreen(state.source.id))
    }.takeIf { state.source is ConfigurableAnimeSource }

    if (!isTabletUi) {
        ShowsScreenSmallImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            episodeSwipeStartAction = episodeSwipeStartAction,
            episodeSwipeEndAction = episodeSwipeEndAction,
            showNextEpisodeAirTime = showNextEpisodeAirTime,
            alwaysUseExternalPlayer = alwaysUseExternalPlayer,
            navigateUp = navigateUp,
            onEpisodeClicked = onEpisodeClicked,
            onDownloadEpisode = onDownloadEpisode,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onTrackingClicked = onTrackingClicked,
            onCommentsClicked = onCommentsClicked,
            onFindProviderClicked = onFindProviderClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueWatching = onContinueWatching,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCollectionClicked = onEditCollectionClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            changeAnimeSkipIntro = changeAnimeSkipIntro,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiFillermarkClicked = onMultiFillermarkClicked,
            onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
            onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onEpisodeSwipe = onEpisodeSwipe,
            onEpisodeSelected = onEpisodeSelected,
            onAllEpisodeSelected = onAllEpisodeSelected,
            onInvertSelection = onInvertSelection,
            onSettingsClicked = onSettingsClicked,
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onContinueWatchingClicked,
            onMarkAllSeenClicked = onMarkAllSeenClicked,
            onMarkAllUnseenClicked = onMarkAllUnseenClicked,
            onRemoveAllDownloadsClicked = onRemoveAllDownloadsClicked,
            onRemoveNonBookmarkedDownloadsClicked = onRemoveNonBookmarkedDownloadsClicked,
            onRemoveSeenDownloadsClicked = onRemoveSeenDownloadsClicked,
            onSuggestionClick = onSuggestionClick,
            onOpenSuggestions = onOpenSuggestions,
            onRetrySuggestions = onRetrySuggestions,
            previewCover = previewCover,
            seedHeightsPx = seedHeightsPx,
        )
    } else {
        ShowsScreenLargeImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            episodeSwipeStartAction = episodeSwipeStartAction,
            episodeSwipeEndAction = episodeSwipeEndAction,
            showNextEpisodeAirTime = showNextEpisodeAirTime,
            alwaysUseExternalPlayer = alwaysUseExternalPlayer,
            navigateUp = navigateUp,
            onEpisodeClicked = onEpisodeClicked,
            onDownloadEpisode = onDownloadEpisode,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onTrackingClicked = onTrackingClicked,
            onCommentsClicked = onCommentsClicked,
            onFindProviderClicked = onFindProviderClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterButtonClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueWatching = onContinueWatching,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCollectionClicked = onEditCollectionClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            changeAnimeSkipIntro = changeAnimeSkipIntro,
            onMigrateClicked = onMigrateClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiFillermarkClicked = onMultiFillermarkClicked,
            onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
            onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onEpisodeSwipe = onEpisodeSwipe,
            onEpisodeSelected = onEpisodeSelected,
            onAllEpisodeSelected = onAllEpisodeSelected,
            onInvertSelection = onInvertSelection,
            onSettingsClicked = onSettingsClicked,
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onContinueWatchingClicked,
            onMarkAllSeenClicked = onMarkAllSeenClicked,
            onMarkAllUnseenClicked = onMarkAllUnseenClicked,
            onRemoveAllDownloadsClicked = onRemoveAllDownloadsClicked,
            onRemoveNonBookmarkedDownloadsClicked = onRemoveNonBookmarkedDownloadsClicked,
            onRemoveSeenDownloadsClicked = onRemoveSeenDownloadsClicked,
            onSuggestionClick = onSuggestionClick,
            onOpenSuggestions = onOpenSuggestions,
            onRetrySuggestions = onRetrySuggestions,
            previewCover = previewCover,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowsScreenSmallImpl(
    state: ShowsScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    navigateUp: () -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onCommentsClicked: (() -> Unit)? = null,
    /** Find Content Provider (17-02) — replaces the comments button on
     *  canonical entries with zero bound carriers. */
    onFindProviderClicked: (() -> Unit)? = null,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog — receives the artwork currently shown in the hero
    // (e.g. the selected season's poster), not necessarily the persisted cover.
    onCoverClicked: (Any?) -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCollectionClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    changeAnimeSkipIntro: (() -> Unit)?,
    onSettingsClicked: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode, Boolean) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,

    // For episode swipe
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Episode selection
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // For overflow menu
    onMarkAllSeenClicked: (() -> Unit)? = null,
    onMarkAllUnseenClicked: (() -> Unit)? = null,
    onRemoveAllDownloadsClicked: (() -> Unit)? = null,
    onRemoveNonBookmarkedDownloadsClicked: (() -> Unit)? = null,
    onRemoveSeenDownloadsClicked: (() -> Unit)? = null,

    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,
    onSuggestionClick: (SuggestionItem) -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
    onRetrySuggestions: () -> Unit = {},
    previewCover: Any? = null,
    seedHeightsPx: Map<Any, Int> = emptyMap(),
) {
    val density = LocalDensity.current
    val offsetGridPaddingPx = with(density) { GRID_PADDING.roundToPx() }

    // Settle pass after a skeleton→real swap: each item mounts at the
    // ghost's measured height, then all release on the same frame so
    // every boundary the skeleton got slightly wrong glides instead of
    // snapping.
    var settling by remember(seedHeightsPx) { mutableStateOf(seedHeightsPx.isNotEmpty()) }
    LaunchedEffect(seedHeightsPx) {
        if (settling) {
            withFrameNanos { }
            withFrameNanos { }
            settling = false
        }
    }
    fun Modifier.settleIn(key: Any): Modifier = animateContentSize(
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 350f),
    ).then(
        if (settling) {
            Modifier.height(with(density) { (seedHeightsPx[key] ?: 0).toDp() })
        } else {
            Modifier
        },
    )
    val gridSize = remember(state.anime) { state.anime.seasonDisplayGridSize }

    val itemListState = rememberLazyGridState()

    val seasons = remember(state) { state.processedSeasons }
    val episodes = remember(state) { state.processedEpisodes }
    val listItem = remember(state) { state.episodeListItems }

    // Cinemeta season pills — parse season numbers from episode scanlator ("Season N")
    val isCinemetaEntry = state.anime.source == 0L && state.anime.url.startsWith("cinemeta:")
    val isCinemetaMovie = isCinemetaEntry && state.anime.url.startsWith("cinemeta:movie")
    val cinemetaSeasons = remember(listItem) {
        if (!isCinemetaEntry) {
            emptyList()
        } else {
            listItem.mapNotNull { ep ->
                (ep as? EpisodeList.Item)?.episode?.scanlator
                    ?.let { parseCinemetaSeason(it) }
            }.distinct()
        }
    }
    val preferenceStore = remember { Injekt.get<PreferenceStore>() }

    // Enhanced detail hero is the base view for all anime now.
    // The logo pref is written by LoadCinemetaEpisodes on each entry open.
    val cinemetaLogo by remember(state.anime.id) {
        preferenceStore.getString("cinemeta_logo_${state.anime.id}", "")
    }.collectAsState()

    // The user's progress season — the season containing the next unseen
    // episode (falls to the last season when everything is watched). Pill
    // taps are browse-only: the canonical season is derived from seen
    // state, so leaving and re-entering lands on real progress and the
    // library keeps the progress season's artwork. Watch order is
    // ascending — in a descending display the next unseen sits at the
    // end of the unseen run, not the head.
    val sortDescending = state.anime.sortDescending()
    val progressSeason = remember(listItem, isCinemetaEntry, cinemetaSeasons, sortDescending) {
        if (!isCinemetaEntry || cinemetaSeasons.isEmpty()) {
            null
        } else {
            val unseen = listItem.filter {
                (it as? EpisodeList.Item)?.episode?.seen == false
            }
            (
                (if (sortDescending) unseen.lastOrNull() else unseen.firstOrNull())
                    as? EpisodeList.Item
                )
                ?.episode?.scanlator?.let(::parseCinemetaSeason)
                ?: cinemetaSeasons.filter { it != 0 }.lastOrNull()
                ?: cinemetaSeasons.lastOrNull()
        }
    }

    // Seasons whose episodes are all seen — shown with a checkmark pill.
    val watchedSeasons = remember(listItem, isCinemetaEntry) {
        if (!isCinemetaEntry) {
            emptySet()
        } else {
            listItem.mapNotNull { (it as? EpisodeList.Item)?.episode }
                .groupBy { parseCinemetaSeason(it.scanlator) }
                .filterValues { eps -> eps.all { it.seen } }
                .keys
        }
    }

    // -1 = follow progress (auto-advances as episodes get watched); a pill
    // tap sets a browse override so the user can look at other seasons
    // without changing their progress.
    var seasonBrowseOverride by remember(state.anime.id) { mutableIntStateOf(-1) }

    // Once a season is being displayed it stays displayed for the session:
    // finishing it (progress advancing to the next season) must not yank the
    // viewed season — or its accent — away mid-browse. Re-entering resets.
    var pinnedSeason by remember(state.anime.id) { mutableIntStateOf(-1) }
    var lastProgressSeason by remember(state.anime.id) { mutableIntStateOf(-1) }
    val selectedCinemetaSeason = when {
        seasonBrowseOverride >= 0 -> seasonBrowseOverride
        pinnedSeason >= 0 -> pinnedSeason
        else ->
            progressSeason
                ?: cinemetaSeasons.firstOrNull { it != 0 }
                ?: cinemetaSeasons.firstOrNull()
                ?: 1
    }
    LaunchedEffect(progressSeason) {
        val p = progressSeason ?: return@LaunchedEffect
        if (lastProgressSeason < 0) {
            lastProgressSeason = p
        } else if (p != lastProgressSeason) {
            if (pinnedSeason < 0 && seasonBrowseOverride < 0) {
                pinnedSeason = lastProgressSeason
            }
            lastProgressSeason = p
        }
    }
    val filteredListItem = remember(listItem, selectedCinemetaSeason, isCinemetaEntry) {
        if (!isCinemetaEntry || cinemetaSeasons.size <= 1) {
            listItem
        } else {
            listItem.filter { ep ->
                when (ep) {
                    is EpisodeList.MissingCount -> true
                    is EpisodeList.Item -> {
                        val season = parseCinemetaSeason(ep.episode.scanlator)
                        season == selectedCinemetaSeason
                    }
                }
            }
        }
    }

    // Season cover override — fetch per-season posters and overviews from TMDB
    val tmdbSeasonPosters = remember { Injekt.get<eu.kanade.tachiyomi.metadata.tmdb.TmdbSeasonPosters>() }
    val updateAnime = remember { Injekt.get<eu.kanade.domain.entries.shows.interactor.UpdateAnime>() }
    val seasonPosters = remember(state.anime.url) {
        if (!isCinemetaEntry) {
            mutableStateMapOf()
        } else {
            mutableStateMapOf<Int, String>()
        }
    }
    val seasonOverviews = remember(state.anime.url) {
        if (!isCinemetaEntry) {
            mutableStateMapOf()
        } else {
            mutableStateMapOf<Int, String>()
        }
    }
    var seriesPoster by remember(state.anime.url) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.anime.url, isCinemetaEntry) {
        if (isCinemetaEntry && !isCinemetaMovie) {
            val imdbId = tmdbSeasonPosters.extractImdbId(state.anime.url)
            if (imdbId != null) {
                tmdbSeasonPosters.getSeasonPosters(imdbId)
                    .forEach { (season, url) -> seasonPosters[season] = url }
                tmdbSeasonPosters.getSeasonOverviews(imdbId)
                    .forEach { (season, text) -> seasonOverviews[season] = text }
                seriesPoster = tmdbSeasonPosters.getSeriesPoster(imdbId)
            }
        }
    }
    // Cinemeta can split a show into more seasons than TMDB tracks (e.g.
    // Naruto S5) — fall back to the series poster instead of leaving the
    // previous season's art in place. A pinned/custom cover beats the
    // swap — the user chose that art explicitly.
    val hasCustomCover = remember(state.anime.coverLastModified) {
        state.anime.hasCustomCover()
    }
    val seasonCoverOverride = if (hasCustomCover) {
        null
    } else {
        seasonPosters[selectedCinemetaSeason] ?: seriesPoster
    }
    val seasonOverview = seasonOverviews[selectedCinemetaSeason]

    // The accent follows the artwork the user is looking at, not the
    // persisted progress cover — finishing S1 swaps the library poster to
    // S2's art, but the detail view shouldn't recolor while the user is
    // still browsing S1. Falls back to the entry accent (persisted cover)
    // while the season poster loads and for non-Cinemeta entries.
    val displayedAccent = rememberEntryAccentColor(
        entryId = state.anime.id,
        cover = seasonCoverOverride ?: state.anime,
        type = EntryCoverMetadata.EntryType.SHOWS,
    ) ?: state.accentColor

    // Persisted artwork is progress-derived — browsing another season must
    // not rewrite the library cover. The progress season's poster becomes
    // the entry's cover + blurred background, so library/home/recently-
    // watched cards and the detail accent color follow watch progress.
    // cover_last_modified must be bumped too — AnimeCover's Coil key
    // includes it, so without a bump every other screen keeps serving the
    // old image.
    val libraryPoster = progressSeason?.let { seasonPosters[it] } ?: seriesPoster
    LaunchedEffect(libraryPoster, state.anime.thumbnailUrl, hasCustomCover) {
        val poster = libraryPoster
        // A pinned/custom cover owns the artwork — don't churn
        // thumbnailUrl underneath it.
        if (!hasCustomCover &&
            isCinemetaEntry &&
            !isCinemetaMovie &&
            poster != null &&
            poster != state.anime.thumbnailUrl
        ) {
            try {
                updateAnime.await(
                    tachiyomi.domain.entries.shows.model.AnimeUpdate(
                        id = state.anime.id,
                        thumbnailUrl = poster,
                        backgroundUrl = poster,
                        coverLastModified = System.currentTimeMillis(),
                        backgroundLastModified = System.currentTimeMillis(),
                    ),
                )
            } catch (e: Exception) {
                // Non-fatal — detail view still shows the override
            }
        }
    }

    val hasFilters = remember(state) {
        when (state.anime.fetchType) {
            FetchType.Seasons -> state.anime.seasonsFiltered()
            FetchType.Episodes -> state.anime.episodesFiltered()
        }
    }

    var toolbarHeight by remember { mutableIntStateOf(0) }

    val isAnySelected by remember {
        derivedStateOf {
            episodes.fastAny { it.selected }
        }
    }

    // First-mount budget: the hero + chrome compose on frame one (they carry
    // the shared elements); seasons/episodes stream in next frame so a
    // Success landing mid-push can't stall the flight.
    var listContentReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        listContentReady = true
    }

    BackHandler(onBack = {
        if (isAnySelected) {
            onAllEpisodeSelected(false)
        } else {
            navigateUp()
        }
    })

    BoxWithConstraints {
        val density = LocalDensity.current
        val containerHeightPx = with(density) { this@BoxWithConstraints.maxHeight.roundToPx() }
        Scaffold(
            // The bar stays put (back/download/menu always reachable) but the
            // scroll-reveal is gone — title and background are pinned transparent
            // outside multi-select; action mode forces both to full alpha itself.
            topBar = {
                val selectedEpisodeCount: Int = remember(episodes) {
                    episodes.count { it.selected }
                }
                EntryToolbar(
                    title = state.anime.title,
                    hasFilters = hasFilters,
                    navigateUp = navigateUp,
                    onClickFilter = null,
                    onClickShare = onShareClicked,
                    onClickDownload = onDownloadActionClicked,
                    onClickEditCollection = onEditCollectionClicked,
                    onClickMigrate = onMigrateClicked,
                    onClickSettings = onSettingsClicked,
                    changeAnimeSkipIntro = changeAnimeSkipIntro,
                    actionModeCounter = selectedEpisodeCount,
                    onCancelActionMode = { onAllEpisodeSelected(false) },
                    onSelectAll = { onAllEpisodeSelected(true) },
                    onInvertSelection = { onInvertSelection() },
                    titleAlphaProvider = { 0f },
                    backgroundAlphaProvider = { 0f },
                    isManga = false,
                    modifier = Modifier.onSizeChanged { toolbarHeight = it.height },
                    toolbarBackgroundColor = MaterialTheme.colorScheme.background,
                    onClickWebView = onWebViewClicked,
                    onClickRemoveAllDownloads = onRemoveAllDownloadsClicked,
                    onClickRemoveNonBookmarkedDownloads = onRemoveNonBookmarkedDownloadsClicked,
                    onClickRemoveReadDownloads = onRemoveSeenDownloadsClicked,
                )
            },
            bottomBar = {
                val selectedEpisodes = remember(episodes) {
                    episodes.filter { it.selected }
                }
                SharedShowsBottomActionMenu(
                    selected = selectedEpisodes,
                    onEpisodeClicked = onEpisodeClicked,
                    onMultiBookmarkClicked = onMultiBookmarkClicked,
                    onMultiFillermarkClicked = onMultiFillermarkClicked,
                    onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
                    onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
                    onDownloadEpisode = onDownloadEpisode,
                    onMultiDeleteClicked = onMultiDeleteClicked,
                    fillFraction = 1f,
                    alwaysUseExternalPlayer = alwaysUseExternalPlayer,
                )
            },
            snackbarHost = { AchievementStyledSnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            val topPadding = contentPadding.calculateTopPadding()
            // The bar renders but is transparent outside selection — still keep
            // the indicator/hero clear of the status bar.
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

            val pullState = rememberPullToRefreshState()
            // Pull distance in px — drives the Nuvio-style hero zoom. The PTR
            // state already tracks the gesture, so the zoom tracks the finger
            // and springs back with the indicator.
            val heroOverscrollPx = pullState.distanceFraction *
                with(LocalDensity.current) { 80.dp.toPx() }
            PullRefresh(
                refreshing = state.isRefreshingData,
                onRefresh = onRefresh,
                enabled = !isAnySelected,
                indicatorPadding = PaddingValues(top = maxOf(topPadding, statusBarTop)),
                state = pullState,
            ) {
                val layoutDirection = LocalLayoutDirection.current
                val heroParallaxPx = if (itemListState.firstVisibleItemIndex == 0) {
                    itemListState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    0f
                }
                FastScrollLazyVerticalGrid(
                    modifier = Modifier
                        .fillMaxHeight(),
                    state = itemListState,
                    thumbAllowed = { false },
                    columns = if (gridSize == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(gridSize),
                    contentPadding = PaddingValues(
                        start = GRID_PADDING + contentPadding.calculateStartPadding(layoutDirection),
                        end = GRID_PADDING + contentPadding.calculateEndPadding(layoutDirection),
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                ) {
                    item(
                        key = EntryScreenItem.INFO_BOX,
                        contentType = EntryScreenItem.INFO_BOX,
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        EnhancedEntryInfoBox(
                            appBarPadding = maxOf(topPadding, statusBarTop),
                            title = state.anime.title,
                            backdrop = seasonCoverOverride ?: (state.anime.backgroundUrl ?: state.anime),
                            sharedKey = "hero-image-${state.anime.id}",
                            logoUrl = cinemetaLogo.takeIf { it.isNotBlank() },
                            metaItems = buildList {
                                addAll(cinemetaMovieMetaItems(state.anime))
                                if (!isCinemetaMovie) {
                                    enhancedEntryStatusText(state.anime.status)?.let { add(it) }
                                }
                            },
                            rating = cinemetaMovieRating(state.anime),
                            // The viewer's first page must show the artwork
                            // the hero is displaying — movies have no season
                            // poster, so it falls back to the fanart
                            // backgroundUrl (a poster otherwise).
                            onCoverClick = {
                                onCoverClicked(seasonCoverOverride ?: state.anime.backgroundUrl)
                            },
                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                            panHorizontally = true,
                            overscrollPullPx = heroOverscrollPx,
                            parallaxOffsetPx = heroParallaxPx,
                            accentColor = displayedAccent,
                            previewCover = previewCover,
                            liveCover = seasonCoverOverride,
                        )
                    }

                    item(
                        key = EntryScreenItem.ACTION_ROW,
                        contentType = EntryScreenItem.ACTION_ROW,
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Column(
                            modifier = Modifier
                                .settleIn(EntryScreenItem.ACTION_ROW)
                                .ignorePadding(offsetGridPaddingPx),
                        ) {
                            val nextEpisode = remember(episodes) {
                                // Lowest-numbered unseen — the "next in
                                // line" episode regardless of sort
                                // direction (firstOrNull on a descending
                                // list returns the newest unseen).
                                episodes
                                    .filter { !it.episode.seen }
                                    .minByOrNull { it.episode.episodeNumber }
                                    ?.episode
                            }
                            val hasSeenEpisodes = remember(state.episodes) {
                                state.episodes.fastAny { it.episode.seen }
                            }
                            val hasStarted = hasSeenEpisodes ||
                                (nextEpisode?.lastSecondSeen ?: 0L) > 0
                            val allSeen = episodes.isNotEmpty() &&
                                episodes.fastAll { it.episode.seen }
                            EntryContinueRow(
                                sharedKey = "hero-resume-${state.anime.id}",
                                actions = listOf(
                                    EntryNextUpAction(
                                        icon = if (allSeen) {
                                            Icons.Filled.CheckCircle
                                        } else {
                                            Icons.Filled.PlayArrow
                                        },
                                        label = when {
                                            allSeen -> stringResource(
                                                AYMR.strings.fetching_overlay_all_up_to_date,
                                            )
                                            nextEpisode != null && hasStarted ->
                                                stringResource(
                                                    MR.strings.action_continue_named,
                                                    eu.kanade.presentation.entries.components
                                                        .qualifiedEpisodeLabel(
                                                            nextEpisode.name,
                                                            nextEpisode.episodeNumber,
                                                            nextEpisode.scanlator,
                                                        ),
                                                )
                                            isCinemetaMovie ->
                                                stringResource(AYMR.strings.action_play)
                                            else -> stringResource(MR.strings.action_start)
                                        },
                                        onClickLabel = stringResource(
                                            AYMR.strings.action_play,
                                        ),
                                        onClick = onContinueWatching,
                                    ),
                                ),
                                actionsEnabled = !allSeen,
                                accentColor = displayedAccent,
                                favorite = state.anime.favorite,
                                allDone = allSeen,
                                favoriteContentDescription = stringResource(
                                    if (state.anime.favorite) {
                                        MR.strings.in_library
                                    } else {
                                        MR.strings.add_to_library
                                    },
                                ),
                                doneContentDescription = stringResource(
                                    if (allSeen) {
                                        AYMR.strings.action_mark_as_unseen
                                    } else {
                                        AYMR.strings.action_mark_as_seen
                                    },
                                ),
                                onFavoriteClick = onAddToLibraryClicked,
                                onToggleDoneClick = {
                                    if (allSeen) {
                                        onMarkAllUnseenClicked?.invoke()
                                    } else {
                                        onMarkAllSeenClicked?.invoke()
                                    }
                                },
                            )
                            EntryGenreRow(
                                pills = state.anime.genre.orEmpty(),
                                onPillClick = { onSearch(it, true) },
                                accentColor = displayedAccent,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }

                    item(
                        key = EntryScreenItem.DESCRIPTION_WITH_TAG,
                        contentType = EntryScreenItem.DESCRIPTION_WITH_TAG,
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        ExpandableAnimeDescription(
                            defaultExpandState = false,
                            // Per-season TMDB overview replaces the series
                            // description when the selected season has one.
                            description = seasonOverview ?: state.anime.description,
                            // Genres live in the pill scroll row — no pills
                            // inside the description.
                            tagsProvider = { null },
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                            modifier = Modifier
                                .settleIn(EntryScreenItem.DESCRIPTION_WITH_TAG)
                                .ignorePadding(offsetGridPaddingPx),
                            accentColor = displayedAccent,
                        )
                    }

                    item(
                        key = "SUGGESTIONS",
                        contentType = "SUGGESTIONS",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        // Similar titles — temporarily disabled
//                        AuroraSuggestionsRow(
//                            state = state.suggestions,
//                            onSuggestionClick = onSuggestionClick,
//                            onOpenSuggestions = onOpenSuggestions,
//                            onRetryClick = onRetrySuggestions,
//                            modifier = Modifier.fillMaxWidth(),
//                            accentColor = displayedAccent,
//                        )
                    }

                    // The hero already surfaces runtime/genres — the count
                    // header and the single "episode" row below the Play
                    // button would be redundant for movies.
                    if (!isCinemetaMovie) {
                        item(
                            key = EntryScreenItem.ITEM_HEADER,
                            contentType = EntryScreenItem.ITEM_HEADER,
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            // Cinemeta entries: episode numbers reset per season, so
                            // missingEntriesCount produces false positives. Skip it.
                            val missingEpisodesCount = if (isCinemetaEntry) {
                                0
                            } else {
                                remember(episodes) {
                                    episodes.map { it.episode.episodeNumber }.missingEntriesCount()
                                }
                            }
                            val missingSeasonsCount = remember(seasons) {
                                seasons.map { it.seasonAnime.anime.seasonNumber }.missingEntriesCount()
                            }
                            val itemCount = when (state.anime.fetchType) {
                                FetchType.Seasons -> seasons.size
                                FetchType.Episodes -> filteredListItem.size
                            }
                            val headerText = if (isCinemetaMovie) {
                                // Movies have a single virtual episode — "1 episode"
                                // is meaningless; show the runtime instead.
                                formatRuntime(state.anime.runtime) ?: ""
                            } else {
                                when (state.anime.fetchType) {
                                    FetchType.Seasons -> pluralStringResource(
                                        AYMR.plurals.anime_num_seasons,
                                        count = itemCount,
                                        itemCount,
                                    )
                                    FetchType.Episodes -> pluralStringResource(
                                        AYMR.plurals.anime_num_episodes,
                                        count = itemCount,
                                        itemCount,
                                    )
                                }
                            }
                            // On a seasons page the per-row episode counts
                            // are one level down — surface the work-wide
                            // total in the secondary "entries" slot so the
                            // header still answers "how much is here".
                            val totalEpisodes = if (state.anime.fetchType == FetchType.Seasons) {
                                seasons.sumOf { it.seasonAnime.totalCount.coerceAtLeast(0) }
                            } else {
                                0L
                            }
                            Column(
                                modifier = Modifier.settleIn(EntryScreenItem.ITEM_HEADER),
                            ) {
                                EntryChapterHeader(
                                    itemCountText = headerText,
                                    entryCountText = if (totalEpisodes > 0) {
                                        pluralStringResource(
                                            AYMR.plurals.anime_num_episodes_in_total,
                                            count = totalEpisodes.toInt(),
                                            totalEpisodes.toInt(),
                                        )
                                    } else {
                                        null
                                    },
                                    onClick = onFilterClicked,
                                    accentColor = displayedAccent,
                                    intervalDays = state.intervalDays,
                                    showInterval = state.showInterval,
                                    modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                )
                                if (!isCinemetaEntry) {
                                    // 19-05: canonical-unserved mains add to the
                                    // existing aggregate missing-items slot.
                                    MissingItemCountListItem(
                                        count = maxOf(missingEpisodesCount, missingSeasonsCount) +
                                            state.canonicalMissingCount,
                                    )
                                }
                            }
                        }
                    }

                    if (!isCinemetaMovie && listContentReady) {
                        when (state.anime.fetchType) {
                            FetchType.Seasons -> {
                                sharedSeasons(
                                    anime = state.anime,
                                    seasons = seasons,
                                    containerHeight = containerHeightPx - toolbarHeight,
                                    onSeasonClicked = onSeasonClicked,
                                    onClickContinueWatching = onClickContinueWatching,
                                    listItemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                )
                            }

                            FetchType.Episodes -> {
                                if (state.airingTime > 0L) {
                                    item(
                                        key = EntryScreenItem.AIRING_TIME,
                                        contentType = EntryScreenItem.AIRING_TIME,
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        // Handles the second by second countdown
                                        var timer by remember { mutableLongStateOf(state.airingTime) }
                                        LaunchedEffect(key1 = timer) {
                                            if (timer > 0L) {
                                                delay(1000L)
                                                timer -= 1000L
                                            }
                                        }
                                        if (timer > 0L &&
                                            showNextEpisodeAirTime &&
                                            state.anime.status.toInt() != SAnime.COMPLETED
                                        ) {
                                            NextEpisodeAiringListItem(
                                                title = stringResource(
                                                    AYMR.strings.display_mode_episode,
                                                    formatEpisodeNumber(state.airingEpisodeNumber),
                                                ),
                                                date = formatTime(state.airingTime, useDayFormat = true),
                                                modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                            )
                                        }
                                    }
                                }

                                // Season pills for Cinemeta entries
                                if (isCinemetaEntry && cinemetaSeasons.size > 1) {
                                    item(
                                        key = "cinemeta-season-pills",
                                        contentType = EntryScreenItem.ITEM,
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        SeasonPillRow(
                                            seasons = cinemetaSeasons,
                                            selectedSeason = selectedCinemetaSeason,
                                            watchedSeasons = watchedSeasons,
                                            onSeasonSelected = { seasonBrowseOverride = it },
                                            onSeasonLongPressed = { season ->
                                                // Hold = seen/unseen the
                                                // whole season — seen unless
                                                // every episode already is.
                                                val seasonEpisodes = listItem
                                                    .mapNotNull { (it as? EpisodeList.Item)?.episode }
                                                    .filter { parseCinemetaSeason(it.scanlator) == season }
                                                if (seasonEpisodes.isNotEmpty()) {
                                                    onMultiMarkAsSeenClicked(
                                                        seasonEpisodes,
                                                        !seasonEpisodes.all { it.seen },
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .settleIn("cinemeta-season-pills")
                                                .ignorePadding(offsetGridPaddingPx),
                                            accentColor = displayedAccent,
                                        )
                                    }
                                }

                                if (listContentReady) {
                                    sharedEpisodeItems(
                                        anime = state.anime,
                                        episodes = filteredListItem,
                                        isAnyEpisodeSelected = episodes.fastAny { it.selected },
                                        showSummaries = state.showSummaries,
                                        showPreviews = state.showPreviews,
                                        onEpisodeClicked = onEpisodeClicked,
                                        onDownloadEpisode = onDownloadEpisode,
                                        onEpisodeSelected = onEpisodeSelected,
                                        itemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                        accentColor = displayedAccent,
                                        commentsCount = state.commentsCount,
                                        onCommentsClicked = onCommentsClicked,
                                        hasNoCarriers = hasNoCarriers(state),
                                        onFindProviderClicked = onFindProviderClicked,
                                        episodesLoading = episodesLoading(state),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowsScreenLargeImpl(
    state: ShowsScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    navigateUp: () -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onCommentsClicked: (() -> Unit)? = null,
    /** Find Content Provider (17-02) — replaces the comments button on
     *  canonical entries with zero bound carriers. */
    onFindProviderClicked: (() -> Unit)? = null,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog — receives the artwork currently shown in the hero
    // (e.g. the selected season's poster), not necessarily the persisted cover.
    onCoverClicked: (Any?) -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCollectionClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    changeAnimeSkipIntro: (() -> Unit)?,
    onSettingsClicked: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode, Boolean) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,

    // For swipe actions
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Episode selection
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // For overflow menu
    onMarkAllSeenClicked: (() -> Unit)? = null,
    onMarkAllUnseenClicked: (() -> Unit)? = null,
    onRemoveAllDownloadsClicked: (() -> Unit)? = null,
    onRemoveNonBookmarkedDownloadsClicked: (() -> Unit)? = null,
    onRemoveSeenDownloadsClicked: (() -> Unit)? = null,

    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,
    onSuggestionClick: (SuggestionItem) -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
    onRetrySuggestions: () -> Unit = {},
    previewCover: Any? = null,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val seasons = remember(state) { state.processedSeasons }
    val episodes = remember(state) { state.processedEpisodes }
    val listItem = remember(state) { state.episodeListItems }

    // Cinemeta season pills — parse season numbers from episode scanlator ("Season N")
    val isCinemetaEntry = state.anime.source == 0L && state.anime.url.startsWith("cinemeta:")
    val isCinemetaMovie = isCinemetaEntry && state.anime.url.startsWith("cinemeta:movie")
    val cinemetaSeasons = remember(listItem) {
        if (!isCinemetaEntry) {
            emptyList()
        } else {
            listItem.mapNotNull { ep ->
                (ep as? EpisodeList.Item)?.episode?.scanlator
                    ?.let { parseCinemetaSeason(it) }
            }.distinct()
        }
    }
    val preferenceStore = remember { Injekt.get<PreferenceStore>() }

    // Enhanced detail hero is the base view for all anime now.
    // The logo pref is written by LoadCinemetaEpisodes on each entry open.
    val cinemetaLogo by remember(state.anime.id) {
        preferenceStore.getString("cinemeta_logo_${state.anime.id}", "")
    }.collectAsState()

    // The user's progress season — the season containing the next unseen
    // episode (falls to the last season when everything is watched). Pill
    // taps are browse-only: the canonical season is derived from seen
    // state, so leaving and re-entering lands on real progress and the
    // library keeps the progress season's artwork. Watch order is
    // ascending — in a descending display the next unseen sits at the
    // end of the unseen run, not the head.
    val sortDescending = state.anime.sortDescending()
    val progressSeason = remember(listItem, isCinemetaEntry, cinemetaSeasons, sortDescending) {
        if (!isCinemetaEntry || cinemetaSeasons.isEmpty()) {
            null
        } else {
            val unseen = listItem.filter {
                (it as? EpisodeList.Item)?.episode?.seen == false
            }
            (
                (if (sortDescending) unseen.lastOrNull() else unseen.firstOrNull())
                    as? EpisodeList.Item
                )
                ?.episode?.scanlator?.let(::parseCinemetaSeason)
                ?: cinemetaSeasons.filter { it != 0 }.lastOrNull()
                ?: cinemetaSeasons.lastOrNull()
        }
    }

    // Seasons whose episodes are all seen — shown with a checkmark pill.
    val watchedSeasons = remember(listItem, isCinemetaEntry) {
        if (!isCinemetaEntry) {
            emptySet()
        } else {
            listItem.mapNotNull { (it as? EpisodeList.Item)?.episode }
                .groupBy { parseCinemetaSeason(it.scanlator) }
                .filterValues { eps -> eps.all { it.seen } }
                .keys
        }
    }

    // -1 = follow progress (auto-advances as episodes get watched); a pill
    // tap sets a browse override so the user can look at other seasons
    // without changing their progress.
    var seasonBrowseOverride by remember(state.anime.id) { mutableIntStateOf(-1) }

    // Once a season is being displayed it stays displayed for the session:
    // finishing it (progress advancing to the next season) must not yank the
    // viewed season — or its accent — away mid-browse. Re-entering resets.
    var pinnedSeason by remember(state.anime.id) { mutableIntStateOf(-1) }
    var lastProgressSeason by remember(state.anime.id) { mutableIntStateOf(-1) }
    val selectedCinemetaSeason = when {
        seasonBrowseOverride >= 0 -> seasonBrowseOverride
        pinnedSeason >= 0 -> pinnedSeason
        else ->
            progressSeason
                ?: cinemetaSeasons.firstOrNull { it != 0 }
                ?: cinemetaSeasons.firstOrNull()
                ?: 1
    }
    LaunchedEffect(progressSeason) {
        val p = progressSeason ?: return@LaunchedEffect
        if (lastProgressSeason < 0) {
            lastProgressSeason = p
        } else if (p != lastProgressSeason) {
            if (pinnedSeason < 0 && seasonBrowseOverride < 0) {
                pinnedSeason = lastProgressSeason
            }
            lastProgressSeason = p
        }
    }
    val filteredListItem = remember(listItem, selectedCinemetaSeason, isCinemetaEntry) {
        if (!isCinemetaEntry || cinemetaSeasons.size <= 1) {
            listItem
        } else {
            listItem.filter { ep ->
                when (ep) {
                    is EpisodeList.MissingCount -> true
                    is EpisodeList.Item -> {
                        val season = parseCinemetaSeason(ep.episode.scanlator)
                        season == selectedCinemetaSeason
                    }
                }
            }
        }
    }

    // Season cover override — fetch per-season posters and overviews from TMDB
    val tmdbSeasonPosters = remember { Injekt.get<eu.kanade.tachiyomi.metadata.tmdb.TmdbSeasonPosters>() }
    val updateAnime = remember { Injekt.get<eu.kanade.domain.entries.shows.interactor.UpdateAnime>() }
    val seasonPosters = remember(state.anime.url) {
        if (!isCinemetaEntry) {
            mutableStateMapOf()
        } else {
            mutableStateMapOf<Int, String>()
        }
    }
    val seasonOverviews = remember(state.anime.url) {
        if (!isCinemetaEntry) {
            mutableStateMapOf()
        } else {
            mutableStateMapOf<Int, String>()
        }
    }
    var seriesPoster by remember(state.anime.url) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.anime.url, isCinemetaEntry) {
        if (isCinemetaEntry && !isCinemetaMovie) {
            val imdbId = tmdbSeasonPosters.extractImdbId(state.anime.url)
            if (imdbId != null) {
                tmdbSeasonPosters.getSeasonPosters(imdbId)
                    .forEach { (season, url) -> seasonPosters[season] = url }
                tmdbSeasonPosters.getSeasonOverviews(imdbId)
                    .forEach { (season, text) -> seasonOverviews[season] = text }
                seriesPoster = tmdbSeasonPosters.getSeriesPoster(imdbId)
            }
        }
    }
    // Cinemeta can split a show into more seasons than TMDB tracks (e.g.
    // Naruto S5) — fall back to the series poster instead of leaving the
    // previous season's art in place. A pinned/custom cover beats the
    // swap — the user chose that art explicitly.
    val hasCustomCover = remember(state.anime.coverLastModified) {
        state.anime.hasCustomCover()
    }
    val seasonCoverOverride = if (hasCustomCover) {
        null
    } else {
        seasonPosters[selectedCinemetaSeason] ?: seriesPoster
    }
    val seasonOverview = seasonOverviews[selectedCinemetaSeason]

    // The accent follows the artwork the user is looking at, not the
    // persisted progress cover — finishing S1 swaps the library poster to
    // S2's art, but the detail view shouldn't recolor while the user is
    // still browsing S1. Falls back to the entry accent (persisted cover)
    // while the season poster loads and for non-Cinemeta entries.
    val displayedAccent = rememberEntryAccentColor(
        entryId = state.anime.id,
        cover = seasonCoverOverride ?: state.anime,
        type = EntryCoverMetadata.EntryType.SHOWS,
    ) ?: state.accentColor

    // Persisted artwork is progress-derived — browsing another season must
    // not rewrite the library cover. The progress season's poster becomes
    // the entry's cover + blurred background, so library/home/recently-
    // watched cards and the detail accent color follow watch progress.
    // cover_last_modified must be bumped too — AnimeCover's Coil key
    // includes it, so without a bump every other screen keeps serving the
    // old image.
    val libraryPoster = progressSeason?.let { seasonPosters[it] } ?: seriesPoster
    LaunchedEffect(libraryPoster, state.anime.thumbnailUrl, hasCustomCover) {
        val poster = libraryPoster
        // A pinned/custom cover owns the artwork — don't churn
        // thumbnailUrl underneath it.
        if (!hasCustomCover &&
            isCinemetaEntry &&
            !isCinemetaMovie &&
            poster != null &&
            poster != state.anime.thumbnailUrl
        ) {
            try {
                updateAnime.await(
                    tachiyomi.domain.entries.shows.model.AnimeUpdate(
                        id = state.anime.id,
                        thumbnailUrl = poster,
                        backgroundUrl = poster,
                        coverLastModified = System.currentTimeMillis(),
                        backgroundLastModified = System.currentTimeMillis(),
                    ),
                )
            } catch (e: Exception) {
                // Non-fatal — detail view still shows the override
            }
        }
    }

    val isAnySelected by remember {
        derivedStateOf {
            episodes.fastAny { it.selected }
        }
    }

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    var topBarHeight by remember { mutableIntStateOf(0) }
    val offsetGridPaddingPx = with(density) { GRID_PADDING.roundToPx() }
    val gridSize = remember(state.anime) { state.anime.seasonDisplayGridSize }

    val itemListState = rememberLazyGridState()
    val hasFilters = remember(state) {
        when (state.anime.fetchType) {
            FetchType.Seasons -> state.anime.seasonsFiltered()
            FetchType.Episodes -> state.anime.episodesFiltered()
        }
    }

    // Same first-mount budget as the phone layout — hero composes on frame
    // one, seasons/episodes stream in on the next.
    var listContentReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        listContentReady = true
    }

    BackHandler(onBack = {
        if (isAnySelected) {
            onAllEpisodeSelected(false)
        } else {
            navigateUp()
        }
    })

    BoxWithConstraints {
        val density = LocalDensity.current
        val containerHeightPx = with(density) { this@BoxWithConstraints.maxHeight.roundToPx() }
        Scaffold(
            topBar = {
                val selectedChapterCount = remember(episodes) {
                    episodes.count { it.selected }
                }
                EntryToolbar(
                    modifier = Modifier.onSizeChanged { topBarHeight = it.height },
                    title = state.anime.title,
                    hasFilters = hasFilters,
                    navigateUp = navigateUp,
                    onClickFilter = null,
                    onClickShare = onShareClicked,
                    onClickDownload = onDownloadActionClicked,
                    onClickEditCollection = onEditCollectionClicked,
                    onClickMigrate = onMigrateClicked,
                    onCancelActionMode = { onAllEpisodeSelected(false) },
                    onClickSettings = onSettingsClicked,
                    changeAnimeSkipIntro = changeAnimeSkipIntro,
                    actionModeCounter = selectedChapterCount,
                    onSelectAll = { onAllEpisodeSelected(true) },
                    onInvertSelection = { onInvertSelection() },
                    titleAlphaProvider = { 1f },
                    backgroundAlphaProvider = { 1f },
                    isManga = false,
                    toolbarBackgroundColor = MaterialTheme.colorScheme.background,
                    onClickWebView = onWebViewClicked,
                    onClickRemoveAllDownloads = onRemoveAllDownloadsClicked,
                    onClickRemoveNonBookmarkedDownloads = onRemoveNonBookmarkedDownloadsClicked,
                    onClickRemoveReadDownloads = onRemoveSeenDownloadsClicked,
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    val selectedEpisodes = remember(episodes) {
                        episodes.filter { it.selected }
                    }
                    SharedShowsBottomActionMenu(
                        selected = selectedEpisodes,
                        onEpisodeClicked = onEpisodeClicked,
                        onMultiBookmarkClicked = onMultiBookmarkClicked,
                        onMultiFillermarkClicked = onMultiFillermarkClicked,
                        onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
                        onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
                        onDownloadEpisode = onDownloadEpisode,
                        onMultiDeleteClicked = onMultiDeleteClicked,
                        fillFraction = 0.5f,
                        alwaysUseExternalPlayer = alwaysUseExternalPlayer,
                    )
                }
            },
            snackbarHost = { AchievementStyledSnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            val pullState = rememberPullToRefreshState()
            val heroOverscrollPx = pullState.distanceFraction *
                with(LocalDensity.current) { 80.dp.toPx() }
            PullRefresh(
                refreshing = state.isRefreshingData,
                onRefresh = onRefresh,
                enabled = !isAnySelected,
                indicatorPadding = PaddingValues(
                    start = insetPadding.calculateStartPadding(layoutDirection),
                    top = with(density) { topBarHeight.toDp() },
                    end = insetPadding.calculateEndPadding(layoutDirection),
                ),
                state = pullState,
            ) {
                TwoPanelBox(
                    modifier = Modifier.padding(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                    ),
                    startContent = {
                        val heroScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .verticalScroll(heroScrollState)
                                .padding(bottom = contentPadding.calculateBottomPadding()),
                        ) {
                            EnhancedEntryInfoBox(
                                appBarPadding = contentPadding.calculateTopPadding(),
                                title = state.anime.title,
                                backdrop = seasonCoverOverride ?: (state.anime.backgroundUrl ?: state.anime),
                                sharedKey = "hero-image-${state.anime.id}",
                                logoUrl = cinemetaLogo.takeIf { it.isNotBlank() },
                                metaItems = buildList {
                                    addAll(cinemetaMovieMetaItems(state.anime))
                                    if (!isCinemetaMovie) {
                                        enhancedEntryStatusText(state.anime.status)?.let { add(it) }
                                    }
                                },
                                rating = cinemetaMovieRating(state.anime),
                                onCoverClick = {
                                    onCoverClicked(seasonCoverOverride ?: state.anime.backgroundUrl)
                                },
                                panHorizontally = true,
                                overscrollPullPx = heroOverscrollPx,
                                parallaxOffsetPx = heroScrollState.value.toFloat(),
                                accentColor = displayedAccent,
                                previewCover = previewCover,
                                liveCover = seasonCoverOverride,
                            )
                            val nextEpisode = remember(episodes) {
                                // Lowest-numbered unseen — the "next in
                                // line" episode regardless of sort
                                // direction (firstOrNull on a descending
                                // list returns the newest unseen).
                                episodes
                                    .filter { !it.episode.seen }
                                    .minByOrNull { it.episode.episodeNumber }
                                    ?.episode
                            }
                            val hasSeenEpisodes = remember(state.episodes) {
                                state.episodes.fastAny { it.episode.seen }
                            }
                            val hasStarted = hasSeenEpisodes ||
                                (nextEpisode?.lastSecondSeen ?: 0L) > 0
                            val allSeen = episodes.isNotEmpty() &&
                                episodes.fastAll { it.episode.seen }
                            EntryContinueRow(
                                sharedKey = "hero-resume-${state.anime.id}",
                                actions = listOf(
                                    EntryNextUpAction(
                                        icon = if (allSeen) {
                                            Icons.Filled.CheckCircle
                                        } else {
                                            Icons.Filled.PlayArrow
                                        },
                                        label = when {
                                            allSeen -> stringResource(
                                                AYMR.strings.fetching_overlay_all_up_to_date,
                                            )
                                            nextEpisode != null && hasStarted ->
                                                stringResource(
                                                    MR.strings.action_continue_named,
                                                    eu.kanade.presentation.entries.components
                                                        .qualifiedEpisodeLabel(
                                                            nextEpisode.name,
                                                            nextEpisode.episodeNumber,
                                                            nextEpisode.scanlator,
                                                        ),
                                                )
                                            isCinemetaMovie ->
                                                stringResource(AYMR.strings.action_play)
                                            else -> stringResource(MR.strings.action_start)
                                        },
                                        onClickLabel = stringResource(
                                            AYMR.strings.action_play,
                                        ),
                                        onClick = onContinueWatching,
                                    ),
                                ),
                                actionsEnabled = !allSeen,
                                accentColor = displayedAccent,
                                favorite = state.anime.favorite,
                                allDone = allSeen,
                                favoriteContentDescription = stringResource(
                                    if (state.anime.favorite) {
                                        MR.strings.in_library
                                    } else {
                                        MR.strings.add_to_library
                                    },
                                ),
                                doneContentDescription = stringResource(
                                    if (allSeen) {
                                        AYMR.strings.action_mark_as_unseen
                                    } else {
                                        AYMR.strings.action_mark_as_seen
                                    },
                                ),
                                onFavoriteClick = onAddToLibraryClicked,
                                onToggleDoneClick = {
                                    if (allSeen) {
                                        onMarkAllUnseenClicked?.invoke()
                                    } else {
                                        onMarkAllSeenClicked?.invoke()
                                    }
                                },
                            )
                            EntryGenreRow(
                                pills = state.anime.genre.orEmpty(),
                                onPillClick = { onSearch(it, true) },
                                accentColor = displayedAccent,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            ExpandableAnimeDescription(
                                defaultExpandState = false,
                                // Per-season TMDB overview replaces the series
                                // description when the selected season has one.
                                description = seasonOverview ?: state.anime.description,
                                tagsProvider = { null },
                                onTagSearch = onTagSearch,
                                onCopyTagToClipboard = onCopyTagToClipboard,
                                accentColor = displayedAccent,
                            )
                            // Similar titles — temporarily disabled
//                            AuroraSuggestionsRow(
//                                state = state.suggestions,
//                                onSuggestionClick = onSuggestionClick,
//                                onOpenSuggestions = onOpenSuggestions,
//                                onRetryClick = onRetrySuggestions,
//                                modifier = Modifier.fillMaxWidth(),
//                                accentColor = displayedAccent,
//                            )
                        }
                    },
                    endContent = {
                        FastScrollLazyVerticalGrid(
                            modifier = Modifier.fillMaxHeight(),
                            state = itemListState,
                            thumbAllowed = { false },
                            columns = if (gridSize == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(gridSize),
                            contentPadding = PaddingValues(
                                start = GRID_PADDING,
                                end = GRID_PADDING,
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                        ) {
                            if (!isCinemetaMovie) {
                                item(
                                    key = EntryScreenItem.ITEM_HEADER,
                                    contentType = EntryScreenItem.ITEM_HEADER,
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    // Cinemeta entries: episode numbers reset per season, so
                                    // missingEntriesCount produces false positives. Skip it.
                                    val missingEpisodesCount = if (isCinemetaEntry) {
                                        0
                                    } else {
                                        remember(episodes) {
                                            episodes.map { it.episode.episodeNumber }.missingEntriesCount()
                                        }
                                    }
                                    val missingSeasonsCount = remember(seasons) {
                                        seasons.map { it.seasonAnime.anime.seasonNumber }.missingEntriesCount()
                                    }
                                    val itemCount = when (state.anime.fetchType) {
                                        FetchType.Seasons -> seasons.size
                                        FetchType.Episodes -> filteredListItem.size
                                    }
                                    val headerText = if (isCinemetaMovie) {
                                        // Movies have a single virtual episode —
                                        // "1 episode" is meaningless; show runtime.
                                        formatRuntime(state.anime.runtime) ?: ""
                                    } else {
                                        when (state.anime.fetchType) {
                                            FetchType.Seasons -> pluralStringResource(
                                                AYMR.plurals.anime_num_seasons,
                                                count = itemCount,
                                                itemCount,
                                            )
                                            FetchType.Episodes -> pluralStringResource(
                                                AYMR.plurals.anime_num_episodes,
                                                count = itemCount,
                                                itemCount,
                                            )
                                        }
                                    }
                                    EntryChapterHeader(
                                        itemCountText = headerText,
                                        onClick = onFilterButtonClicked,
                                        accentColor = displayedAccent,
                                        intervalDays = state.intervalDays,
                                        showInterval = state.showInterval,
                                        modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                    )
                                    if (!isCinemetaEntry) {
                                        // 19-05: canonical-unserved mains add to
                                        // the existing aggregate missing-items slot.
                                        MissingItemCountListItem(
                                            count = maxOf(missingEpisodesCount, missingSeasonsCount) +
                                                state.canonicalMissingCount,
                                        )
                                    }
                                }
                            }

                            if (!isCinemetaMovie && listContentReady) {
                                when (state.anime.fetchType) {
                                    FetchType.Seasons -> {
                                        sharedSeasons(
                                            anime = state.anime,
                                            seasons = seasons,
                                            containerHeight = containerHeightPx - topBarHeight,
                                            onSeasonClicked = onSeasonClicked,
                                            onClickContinueWatching = onClickContinueWatching,
                                            listItemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                        )
                                    }

                                    FetchType.Episodes -> {
                                        if (state.airingTime > 0L) {
                                            item(
                                                key = EntryScreenItem.AIRING_TIME,
                                                contentType = EntryScreenItem.AIRING_TIME,
                                            ) {
                                                // Handles the second by second countdown reseting
                                                var timer by remember { mutableLongStateOf(state.airingTime) }
                                                LaunchedEffect(key1 = timer) {
                                                    if (timer > 0L) {
                                                        delay(1000L)
                                                        timer -= 1000L
                                                    }
                                                }
                                                if (timer > 0L &&
                                                    showNextEpisodeAirTime &&
                                                    state.anime.status.toInt() != SAnime.COMPLETED
                                                ) {
                                                    NextEpisodeAiringListItem(
                                                        title = stringResource(
                                                            AYMR.strings.display_mode_episode,
                                                            formatEpisodeNumber(state.airingEpisodeNumber),
                                                        ),
                                                        date = formatTime(state.airingTime, useDayFormat = true),
                                                        modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                                    )
                                                }
                                            }
                                        }

                                        // Season pills for Cinemeta entries
                                        if (isCinemetaEntry && cinemetaSeasons.size > 1) {
                                            item(
                                                key = "cinemeta-season-pills",
                                                contentType = EntryScreenItem.ITEM,
                                                span = { GridItemSpan(maxLineSpan) },
                                            ) {
                                                Column {
                                                    SeasonPillRow(
                                                        seasons = cinemetaSeasons,
                                                        selectedSeason = selectedCinemetaSeason,
                                                        watchedSeasons = watchedSeasons,
                                                        onSeasonSelected = { seasonBrowseOverride = it },
                                                        onSeasonLongPressed = { season ->
                                                            val seasonEpisodes = listItem
                                                                .mapNotNull { (it as? EpisodeList.Item)?.episode }
                                                                .filter { parseCinemetaSeason(it.scanlator) == season }
                                                            if (seasonEpisodes.isNotEmpty()) {
                                                                onMultiMarkAsSeenClicked(
                                                                    seasonEpisodes,
                                                                    !seasonEpisodes.all { it.seen },
                                                                )
                                                            }
                                                        },
                                                        modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                                        accentColor = displayedAccent,
                                                    )
                                                    seasonOverview?.let {
                                                        Text(
                                                            text = it,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 4,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier
                                                                .ignorePadding(offsetGridPaddingPx)
                                                                .padding(top = 8.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (listContentReady) {
                                            sharedEpisodeItems(
                                                anime = state.anime,
                                                episodes = filteredListItem,
                                                isAnyEpisodeSelected = episodes.fastAny { it.selected },
                                                showSummaries = state.showSummaries,
                                                showPreviews = state.showPreviews,
                                                onEpisodeClicked = onEpisodeClicked,
                                                onDownloadEpisode = onDownloadEpisode,
                                                onEpisodeSelected = onEpisodeSelected,
                                                itemModifier = Modifier.ignorePadding(
                                                    offsetGridPaddingPx,
                                                ),
                                                accentColor = displayedAccent,
                                                commentsCount = state.commentsCount,
                                                onCommentsClicked = onCommentsClicked,
                                                hasNoCarriers = hasNoCarriers(state),
                                                onFindProviderClicked = onFindProviderClicked,
                                                episodesLoading = episodesLoading(state),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SharedShowsBottomActionMenu(
    selected: List<EpisodeList.Item>,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    fillFraction: Float,
    alwaysUseExternalPlayer: Boolean,
    modifier: Modifier = Modifier,
) {
    EntryBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = modifier.fillMaxWidth(fillFraction),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAll { it.episode.bookmark } },
        onFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.fillermark } },
        onRemoveFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAll { it.episode.fillermark } },
        onMarkAsViewedClicked = {
            onMultiMarkAsSeenClicked(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.seen } },
        onMarkAsUnviewedClicked = {
            onMultiMarkAsSeenClicked(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAny { it.episode.seen || it.episode.lastSecondSeen > 0L } },
        onMarkPreviousAsViewedClicked = {
            onMarkPreviousAsSeenClicked(selected[0].episode, true)
        }.takeIf { selected.size == 1 },
        onDownloadClicked = {
            onDownloadEpisode!!(selected.toList(), EpisodeDownloadAction.START)
        }.takeIf {
            onDownloadEpisode != null && selected.fastAny { it.downloadState != ShowsDownload.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected.fastMap { it.episode })
        }.takeIf {
            onDownloadEpisode != null && selected.fastAny { it.downloadState == ShowsDownload.State.DOWNLOADED }
        },
        onInternalClicked = {
            onEpisodeClicked(selected.fastMap { it.episode }.first(), true)
        }.takeIf { alwaysUseExternalPlayer && selected.size == 1 },
        isManga = false,
    )
}

private fun LazyGridScope.sharedSeasons(
    anime: Anime,
    seasons: List<ShowsSeasonItem>,
    containerHeight: Int,
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,
    listItemModifier: Modifier = Modifier,
) {
    items(
        items = seasons,
        key = { season -> season.seasonAnime.anime },
        span = { GridItemSpan(if (anime.seasonDisplayGridMode == SeasonDisplayMode.List) maxLineSpan else 1) },
    ) { item ->
        ShowsSeasonListItem(
            anime = anime,
            item = item,
            containerHeight = containerHeight,
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onClickContinueWatching,
            listItemModifier = listItemModifier,
        )
    }
}

private fun LazyGridScope.sharedEpisodeItems(
    anime: Anime,
    episodes: List<EpisodeList>,
    isAnyEpisodeSelected: Boolean,
    showSummaries: Boolean,
    showPreviews: Boolean,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    itemModifier: Modifier = Modifier,
    accentColor: Color? = null,
    commentsCount: Long? = null,
    onCommentsClicked: (() -> Unit)? = null,
    /** True when the canonical work has zero bound carriers — swaps the
     *  comments slot to Find Content Provider (17-02). */
    hasNoCarriers: Boolean = false,
    onFindProviderClicked: (() -> Unit)? = null,
    /** Carriers are bound but episode rows haven't materialized yet —
     *  the extension fetch is running; show a spinner in the slot. */
    episodesLoading: Boolean = false,
) {
    item(
        key = "episode-carousel",
        contentType = EntryScreenItem.ITEM,
        span = { GridItemSpan(maxLineSpan) },
    ) {
        // Nuvio-style horizontal episode carousel — cards are full-bleed
        // stills, so the row scrolls horizontally with snap-to-card.
        // Opens on the first unseen card in display order: ascending
        // anchors the lowest unseen, descending the newest unseen at the
        // row's head — continuing is always a rightward scroll, never
        // leftward, whichever sort is active.
        val nextIndex = episodes.indexOfFirst {
            it is EpisodeList.Item && !it.episode.seen
        }.takeIf { it >= 0 } ?: 0
        // The same episode carries a thin accent outline on its card.
        val nextEpisodeId = episodes.filterIsInstance<EpisodeList.Item>()
            .filter { !it.episode.seen }
            .minByOrNull { it.episode.episodeNumber }
            ?.episode?.id
        // Cards are 75% of screen width — a negative initial offset lands
        // the next-up episode centered instead of pinned to the start edge.
        val centeredOffsetPx = with(LocalDensity.current) {
            (16.dp - LocalConfiguration.current.screenWidthDp.dp * 0.125f).roundToPx()
        }
        val sortDescending = anime.sortDescending()
        // Descending puts the latest episode at the list head — anchor
        // there, not on ep1 at the far end, so rightward scroll descends
        // through numbers rather than forcing a leftward read.
        val anchorIndex = if (sortDescending) 0 else nextIndex
        val anchorOffset = if (sortDescending) 0 else centeredOffsetPx
        val rowState = rememberLazyListState(
            initialFirstVisibleItemIndex = anchorIndex,
            initialFirstVisibleItemScrollOffset = anchorOffset,
        )
        // Re-anchor when the sort direction flips or a season pill swaps
        // the list — LazyRow otherwise keeps the stale scroll index and
        // strands the user at the wrong end, needing a backwards scroll.
        val listHeadId = episodes.firstOrNull()?.let {
            when (it) {
                is EpisodeList.Item -> "episode-${it.id}"
                is EpisodeList.MissingCount -> it.id
            }
        }
        LaunchedEffect(listHeadId, sortDescending) {
            rowState.scrollToItem(anchorIndex, anchorOffset)
        }
        LazyRow(
            state = rowState,
            modifier = itemModifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
        ) {
            items(
                count = episodes.size,
                key = { index ->
                    when (val episodeItem = episodes[index]) {
                        is EpisodeList.MissingCount -> "missing-count-${episodeItem.id}"
                        is EpisodeList.Item -> "episode-${episodeItem.id}"
                    }
                },
                contentType = { EntryScreenItem.ITEM },
            ) { index ->
                val episodeItem = episodes[index]
                val haptic = LocalHapticFeedback.current

                when (episodeItem) {
                    is EpisodeList.MissingCount -> {
                        MissingItemCountListItem(
                            count = episodeItem.count,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    is EpisodeList.Item -> {
                        val isCinemetaEntry = anime.source == 0L && anime.url.startsWith("cinemeta:")
                        // Cinemeta movies have a single virtual episode — an "S01E01"
                        // badge is meaningless; show the runtime instead.
                        val isCinemetaMovie = anime.url.startsWith("cinemeta:movie")
                        val episodeString = when {
                            isCinemetaMovie -> formatRuntime(anime.runtime) ?: ""
                            isCinemetaEntry -> {
                                val seasonNum = parseCinemetaSeason(episodeItem.episode.scanlator)
                                val epNum = episodeItem.episode.episodeNumber.toInt()
                                "S${seasonNum}E$epNum"
                            }
                            else -> ""
                        }
                        val episodeRating = if (isCinemetaEntry) {
                            parseCinemetaRating(episodeItem.episode.scanlator)
                        } else {
                            null
                        }
                        NuvioEpisodeCard(
                            title = if (anime.displayMode == Anime.EPISODE_DISPLAY_NUMBER) {
                                stringResource(
                                    AYMR.strings.display_mode_episode,
                                    formatEpisodeNumber(episodeItem.episode.episodeNumber),
                                )
                            } else {
                                episodeItem.episode.name
                            },
                            episodeString = episodeString,
                            rating = episodeRating,
                            date = if (isCinemetaEntry) {
                                fullDateText(episodeItem.episode.dateUpload)
                            } else {
                                relativeDateTimeText(episodeItem.episode.dateUpload)
                            },
                            watchProgress = episodeItem.episode
                                .takeIf { !it.seen && it.lastSecondSeen > 0L }
                                ?.let { formatEpisodeRuntime(it.lastSecondSeen * 1000) },
                            progressFraction = episodeItem.episode
                                .takeIf { !it.seen && it.totalSeconds > 0L && it.lastSecondSeen > 0L }
                                ?.let {
                                    it.lastSecondSeen.toFloat() / it.totalSeconds.toFloat()
                                },
                            scanlator = episodeItem.episode.scanlator
                                ?.takeIf { !it.isNullOrBlank() && !it.startsWith("Season ") }
                                ?.takeIf { !it.isNullOrBlank() },
                            // 19-05 D-03b: canon row no readable carrier
                            // serves → the meta-row note slot.
                            note = if (episodeItem.missingProvider) {
                                stringResource(MR.strings.carrier_unavailable)
                            } else {
                                null
                            },
                            summary = episodeItem.episode.summary.takeIf { !it.isNullOrBlank() && showSummaries },
                            previewUrl = episodeItem.episode.previewUrl.takeIf { !it.isNullOrBlank() && showPreviews },
                            seen = episodeItem.episode.seen,
                            bookmark = episodeItem.episode.bookmark,
                            selected = episodeItem.selected,
                            isNext = episodeItem.episode.id == nextEpisodeId,
                            // Cinemeta entries download through a picked source —
                            // the icon opens the source picker rather than a direct
                            // queue, so it stays enabled for them too.
                            downloadIndicatorEnabled = !anime.isLocal() || isCinemetaEntry,
                            downloadStateProvider = { episodeItem.downloadState },
                            downloadProgressProvider = { episodeItem.downloadProgress },
                            onLongClick = {
                                onEpisodeSelected(episodeItem, !episodeItem.selected, true, true)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onClick = {
                                onEpisodeItemClick(
                                    episodeItem = episodeItem,
                                    isAnyEpisodeSelected = isAnyEpisodeSelected,
                                    onToggleSelection = {
                                        onEpisodeSelected(episodeItem, !episodeItem.selected, true, false)
                                    },
                                    onEpisodeClicked = onEpisodeClicked,
                                )
                            },
                            onDownloadClick = if (onDownloadEpisode != null) {
                                { onDownloadEpisode(listOf(episodeItem), it) }
                            } else {
                                null
                            },
                            accentColor = accentColor,
                        )
                    }
                }
            }
        }
    }

    // The action slot under the episode cards. Priority: Find Content
    // Provider on zero-carrier canonical entries (17-02) → a spinner
    // while bound carriers are still expanding into episode rows →
    // comments, which only renders when episodes actually exist.
    val hasEpisodeItems = episodes.any { it is EpisodeList.Item }
    if (hasNoCarriers && onFindProviderClicked != null) {
        item(
            key = "episode-find-provider-button",
            contentType = EntryScreenItem.ITEM,
            span = { GridItemSpan(maxLineSpan) },
        ) {
            EntryFindProviderButton(
                accentColor = accentColor,
                onClick = onFindProviderClicked,
                modifier = itemModifier,
            )
        }
    } else if (episodesLoading && !hasEpisodeItems) {
        item(
            key = "episode-loading",
            contentType = EntryScreenItem.ITEM,
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = accentColor ?: MaterialTheme.colorScheme.primary,
                )
            }
        }
    } else if (onCommentsClicked != null && hasEpisodeItems) {
        item(
            key = "episode-comments-button",
            contentType = EntryScreenItem.ITEM,
            span = { GridItemSpan(maxLineSpan) },
        ) {
            EntryViewCommentsButton(
                commentsCount = commentsCount,
                accentColor = accentColor,
                onClick = onCommentsClicked,
                modifier = itemModifier,
            )
        }
    }
}

private fun onEpisodeItemClick(
    episodeItem: EpisodeList.Item,
    isAnyEpisodeSelected: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
) {
    when {
        episodeItem.selected -> onToggleSelection(false)
        isAnyEpisodeSelected -> onToggleSelection(true)
        else -> onEpisodeClicked(episodeItem.episode, false)
    }
}

/**
 * Parse season number from Cinemeta episode scanlator field.
 * Format: "Season N" or "Season N|★R.R"
 */
private fun parseCinemetaSeason(scanlator: String?): Int {
    if (scanlator.isNullOrBlank()) return 1
    val seasonPart = scanlator.substringBefore("|")
    return seasonPart.removePrefix("Season ").trim().toIntOrNull() ?: 1
}

/**
 * Parse rating from Cinemeta episode scanlator field.
 * Format: "Season N|★R.R" → "R.R", or null if no rating.
 */
private fun parseCinemetaRating(scanlator: String?): String? {
    if (scanlator.isNullOrBlank()) return null
    val idx = scanlator.indexOf("|")
    if (idx < 0) return null
    return scanlator.substring(idx + 1).removePrefix("★").trim().ifBlank { null }
}

/**
 * Meta row items for the enhanced movie hero — release info and runtime.
 * `artist` carries "★ rating||releaseInfo" for Cinemeta entries.
 */
private fun cinemetaMovieMetaItems(anime: Anime): List<String> {
    val artistParts = anime.artist?.split("||", limit = 2).orEmpty()
    val releaseInfo = if (artistParts.size > 1) {
        artistParts[1]
    } else {
        artistParts.getOrNull(0)?.takeUnless { it.startsWith("★") }
    }
    return listOfNotNull(
        releaseInfo?.takeIf { it.isNotBlank() },
        formatRuntime(anime.runtime),
    )
}

/** Bare rating value ("8.4") for the enhanced movie hero, or null. */
private fun cinemetaMovieRating(anime: Anime): String? {
    val artistParts = anime.artist?.split("||", limit = 2).orEmpty()
    return artistParts.getOrNull(0)
        ?.takeIf { artistParts.size > 1 || it.startsWith("★") }
        ?.removePrefix("★")?.trim()?.ifBlank { null }
}

private fun formatEpisodeRuntime(milliseconds: Long): String {
    val totalMin = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    return if (totalMin >= 60) {
        "${totalMin / 60}h ${totalMin % 60}m"
    } else {
        "${totalMin}m"
    }
}

private fun formatTime(milliseconds: Long, useDayFormat: Boolean = false): String {
    return if (useDayFormat) {
        String.format(
            "Airing in %02dd %02dh %02dm %02ds",
            TimeUnit.MILLISECONDS.toDays(milliseconds),
            TimeUnit.MILLISECONDS.toHours(milliseconds) -
                TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS.toDays(milliseconds)),
            TimeUnit.MILLISECONDS.toMinutes(milliseconds) -
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds)),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    } else if (milliseconds > 3600000L) {
        String.format(
            "%d:%02d:%02d",
            TimeUnit.MILLISECONDS.toHours(milliseconds),
            TimeUnit.MILLISECONDS.toMinutes(milliseconds) -
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds)),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    } else {
        String.format(
            "%d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(milliseconds),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    }
}

private val GRID_PADDING = 14.dp

/** Zero bindings on a fetched canonical entry — the comments slot
 *  swaps to Find Content Provider (17-02). Worker-assigned kind gates
 *  it: non-anime movies/series are ID-only, never extension-pursued. */
private fun hasNoCarriers(state: ShowsScreenModel.State.Success): Boolean {
    val carrierless = state.carrierMatrix
        ?.let { it.fetched && it.readable.isEmpty() } == true
    if (!carrierless) return false
    return state.canonicalKind != "movie" && state.canonicalKind != "series"
}

/** Enabled carriers but no episode rows yet — the extension fetch is
 *  still expanding; the action slot shows a spinner (17-02). An empty
 *  readable set can't expand — that's the Find Provider state. */
private fun episodesLoading(state: ShowsScreenModel.State.Success): Boolean =
    state.carrierMatrix?.let { it.fetched && it.readable.isNotEmpty() } == true &&
        state.episodes.none { it is EpisodeList.Item }

private fun Modifier.ignorePadding(gridPadding: Int) = layout { measurable, constraints ->
    val looseConstraints = constraints.offset(gridPadding * 2, 0)
    val placeable = measurable.measure(looseConstraints)

    layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}
