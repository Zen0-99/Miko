package eu.kanade.tachiyomi.ui.entries.shows

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.entries.shows.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.shows.interactor.SyncSeasonsWithSource
import eu.kanade.domain.entries.shows.interactor.UpdateAnime
import eu.kanade.domain.entries.shows.model.downloadedFilter
import eu.kanade.domain.entries.shows.model.seasonDownloadedFilter
import eu.kanade.domain.entries.shows.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.shows.components.EpisodeDownloadAction
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.shows.ShowsDownloadCache
import eu.kanade.tachiyomi.data.download.shows.ShowsDownloadManager
import eu.kanade.tachiyomi.data.download.shows.model.ShowsDownload
import eu.kanade.tachiyomi.data.suggestions.SuggestionCache
import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionSourceWeight
import eu.kanade.tachiyomi.data.suggestions.SuggestionState
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.shows.AnimeFallbackOutcome
import eu.kanade.tachiyomi.data.suggestions.shows.AnimeSearchFallbackEngine
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.data.suggestions.util.bestMatchScoreFor
import eu.kanade.tachiyomi.data.suggestions.util.dedupeByCleanTitle
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.anime.isSourceForTorrents
import eu.kanade.tachiyomi.ui.entries.shows.track.ShowsTrackItem
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.AniChartApi
import eu.kanade.tachiyomi.util.episode.getNextUnseen
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.items.episode.interactor.FilterEpisodesForDownload
import miko.core.common.torrent.TorrentPreferences
import miko.core.common.torrent.TorrentServerUtils
import miko.domain.shows.SeasonAnime
import miko.domain.shows.SeasonDisplayMode
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.shows.interactor.GetAnimeCollections
import tachiyomi.domain.collection.shows.interactor.SetAnimeCollections
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.entries.shows.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.shows.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.entries.shows.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.shows.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.shows.interactor.SetAnimeSeasonFlags
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.entries.shows.model.NoSeasonsException
import tachiyomi.domain.entries.shows.repository.AnimeRepository
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.model.NoEpisodesException
import tachiyomi.domain.items.episode.service.calculateEpisodeGap
import tachiyomi.domain.items.episode.service.getEpisodeSort
import tachiyomi.domain.items.season.interactor.SetAnimeDefaultSeasonFlags
import tachiyomi.domain.items.season.service.getSeasonSortComparator
import tachiyomi.domain.items.season.service.seasonSortAlphabetically
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import kotlin.math.floor

class ShowsScreenModel(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val animeId: Long,
    private val isFromSource: Boolean,
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val basePreferences: eu.kanade.domain.base.BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    internal val playerPreferences: PlayerPreferences = Injekt.get(),
    internal val gesturePreferences: GesturePreferences = Injekt.get(),
    private val torrentPreferences: TorrentPreferences = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val trackEpisode: TrackEpisode = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadManager: ShowsDownloadManager = Injekt.get(),
    private val downloadCache: ShowsDownloadCache = Injekt.get(),
    private val getAnimeAndEpisodesAndSeasons: GetAnimeWithEpisodesAndSeasons = Injekt.get(),
    private val getDuplicateLibraryAnime: GetDuplicateLibraryAnime = Injekt.get(),
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags = Injekt.get(),
    private val setAnimeDefaultEpisodeFlags: SetAnimeDefaultEpisodeFlags = Injekt.get(),
    private val setAnimeSeasonFlags: SetAnimeSeasonFlags = Injekt.get(),
    private val setAnimeDefaultSeasonFlags: SetAnimeDefaultSeasonFlags = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get(),
    private val syncSeasonsWithSource: SyncSeasonsWithSource = Injekt.get(),
    private val getCollections: GetAnimeCollections = Injekt.get(),
    private val getTracks: GetAnimeTracks = Injekt.get(),
    private val addTracks: AddAnimeTracks = Injekt.get(),
    private val setAnimeCollections: SetAnimeCollections = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val filterEpisodesForDownload: FilterEpisodesForDownload = Injekt.get(),
    private val fetchInterval: AnimeFetchInterval = Injekt.get(),
    private val torrentServerUtils: TorrentServerUtils = Injekt.get(),
    internal val setAnimeViewerFlags: SetAnimeViewerFlags = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val suggestionCoordinator: SuggestionCoordinator = Injekt.get(),
    private val searchFallbackEngine: AnimeSearchFallbackEngine = Injekt.get(),
    private val loadCinemetaEpisodes: eu.kanade.tachiyomi.metadata.stream.LoadCinemetaEpisodes = Injekt.get(),
    private val cinemetaEntryUnifier: eu.kanade.tachiyomi.metadata.stream.CinemetaEntryUnifier = Injekt.get(),
    private val streamResolver: eu.kanade.tachiyomi.metadata.stream.StreamResolver = Injekt.get(),
    private val sourceMappingCache: eu.kanade.tachiyomi.metadata.stream.SourceMappingCache = Injekt.get(),
    private val workIdResolver: eu.kanade.tachiyomi.data.mikodb.WorkIdResolver = Injekt.get(),
    private val workIdentityApi: eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi = Injekt.get(),
    private val mikoDbManager: eu.kanade.tachiyomi.data.mikodb.MikoDbManager = Injekt.get(),
    private val workCarriers: eu.kanade.tachiyomi.data.mikodb.WorkCarriers = Injekt.get(),
    private val carrierExclusions: eu.kanade.tachiyomi.data.mikodb.CarrierExclusions = Injekt.get(),
    private val providerFinder: eu.kanade.tachiyomi.data.mikodb.ProviderFinder = Injekt.get(),
    private val episodeMap: eu.kanade.tachiyomi.data.mikodb.CanonicalEpisodeMap = Injekt.get(),
    private val canonicalPipelineRunner: eu.kanade.tachiyomi.data.mikodb.CanonicalPipelineRunner = Injekt.get(),
    private val providerTiers: eu.kanade.tachiyomi.data.mikodb.ProviderTiers = Injekt.get(),
    private val preferenceStore: tachiyomi.core.common.preference.PreferenceStore = Injekt.get(),
    private val animeExtensionManager: eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager = Injekt.get(),
    private val threadIdResolver: eu.kanade.tachiyomi.data.comments.ThreadIdResolver = Injekt.get(),
    private val commentsApi: eu.kanade.tachiyomi.data.comments.CommentsApi = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<ShowsScreenModel.State>(State.Loading) {

    /**
     * Set when this entry resolves to a canonical Cinemeta entry — the
     * screen replaces itself with that entry so there's one entry per
     * real anime instead of a duplicate per extension.
     */
    val redirectToAnimeId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    private val successState: State.Success?
        get() = state.value as? State.Success

    val anime: Anime?
        get() = successState?.anime

    val source: AnimeSource?
        get() = successState?.source

    private val isFavorited: Boolean
        get() = anime?.favorite ?: false

    private val processedEpisodes: List<EpisodeList.Item>?
        get() = successState?.processedEpisodes

    val episodeSwipeStartAction = libraryPreferences.swipeEpisodeEndAction().get()
    val episodeSwipeEndAction = libraryPreferences.swipeEpisodeStartAction().get()
    var autoTrackState = trackPreferences.autoUpdateTrackOnMarkRead().get()

    val showNextEpisodeAirTime = trackPreferences.showNextEpisodeAiringTime().get()
    val alwaysUseExternalPlayer = playerPreferences.alwaysUseExternalPlayer().get()
    val useExternalDownloader = downloadPreferences.useExternalDownloader().get()

    val isUpdateIntervalEnabled =
        LibraryPreferences.ENTRY_OUTSIDE_RELEASE_PERIOD in libraryPreferences.autoUpdateItemRestrictions().get()

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list
    private val selectedEpisodeIds: HashSet<Long> = HashSet()

    internal var isFromChangeCollection: Boolean = false

    internal val autoOpenTrack: Boolean
        get() = successState?.hasLoggedInTrackers == true && trackPreferences.trackOnAddingToLibrary().get()

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    /** Completed by the screen once the nav transition lands — heavy
     *  secondary work holds here so nothing contends with the
     *  shared-element flight (see MangaScreenModel's note). */
    private val transitionSettled = CompletableDeferred<Unit>()
    fun onTransitionSettled() {
        transitionSettled.complete(Unit)
    }

    private inline fun updateSuccessState(func: (State.Success) -> State.Success) {
        mutableState.update {
            when (it) {
                State.Loading -> it
                is State.Success -> func(it)
            }
        }
    }

    fun setAccentColor(color: Color?) {
        updateSuccessState { it.copy(accentColor = color) }
    }

    // -- Suggestions --

    private var suggestionSeedUsed: SuggestionSeed? = null
    private var suggestionsJob: kotlinx.coroutines.Job? = null

    fun getSuggestionSeed(): SuggestionSeed? = suggestionSeedUsed

    fun retrySuggestions() {
        val success = successState ?: return
        val seed = buildSuggestionSeed(success.anime)
        SuggestionCache.invalidateForSeed(seed, success.anime.url)
        loadSuggestions(
            seed,
            anime = success.anime,
            source = success.anime.toCatalogueSource(),
            force = true,
        )
    }

    private fun buildSuggestionSeed(anime: Anime): SuggestionSeed {
        val title = anime.title
        val candidates = SuggestionTitleResolver.resolveCandidates(
            title = title,
            description = anime.description,
            url = anime.url,
        )
        return SuggestionSeed(
            mediaType = SuggestionMediaType.ANIME,
            primaryTitle = title,
            candidateTitles = candidates,
            description = anime.description,
            author = anime.author,
            genres = anime.genre,
        )
    }

    private fun Anime.toCatalogueSource(): AnimeCatalogueSource? =
        sourceManager.getOrStub(source) as? AnimeCatalogueSource

    private fun emitProgressiveSuggestions(list: List<SuggestionItem>, currentAnime: Anime?) {
        val seed = suggestionSeedUsed ?: return
        val sorted = synchronized(list) {
            list.dedupeByCleanTitle(seed)
                .filter { item ->
                    val isSelf = SuggestionTitleResolver.isSameProviderEntry(item, currentAnime?.url)
                    val isFranchise = SuggestionTitleResolver.isFranchiseDuplicate(item.title, seed.primaryTitle)
                    !isSelf && !isFranchise
                }
                .sortedByDescending { SuggestionSourceWeight.finalScore(it.reason, it.bestMatchScoreFor(seed)) }
                .take(20)
        }
        if (sorted.isNotEmpty()) {
            updateSuccessState { it.copy(suggestions = SuggestionState.Success(sorted)) }
        }
    }

    private fun loadSuggestions(
        seed: SuggestionSeed,
        anime: Anime? = null,
        source: AnimeCatalogueSource? = null,
        force: Boolean = false,
    ) {
        if (!sourcePreferences.entrySuggestionsEnabled().get()) {
            updateSuccessState { it.copy(suggestions = SuggestionState.Disabled) }
            return
        }
        if (!force && suggestionSeedUsed == seed) {
            return
        }
        suggestionSeedUsed = seed

        val currentAnime = anime ?: successState?.anime
        val currentSource = source ?: (
            currentAnime?.let {
                sourceManager.getOrStub(it.source)
            } as? AnimeCatalogueSource
            )

        suggestionsJob?.cancel()
        suggestionsJob = screenModelScope.launchIO {
            updateSuccessState { it.copy(suggestions = SuggestionState.Loading) }
            try {
                val suggestionsList = java.util.Collections.synchronizedList(mutableListOf<SuggestionItem>())

                kotlinx.coroutines.coroutineScope {
                    // Task 1: External Suggestions (AniList/etc)
                    launch {
                        try {
                            val externalResult = suggestionCoordinator.fetchSuggestions(seed, limit = 40)
                            if (externalResult.items.isNotEmpty()) {
                                val externalFiltered = externalResult.items.filter { item ->
                                    val isSelf = SuggestionTitleResolver.isSameProviderEntry(item, currentAnime?.url)
                                    val isFranchise = SuggestionTitleResolver.isFranchiseDuplicate(
                                        item.title,
                                        seed.primaryTitle,
                                    )
                                    !isSelf && !isFranchise
                                }
                                if (externalFiltered.isNotEmpty()) {
                                    synchronized(suggestionsList) {
                                        suggestionsList.addAll(externalFiltered)
                                    }
                                    emitProgressiveSuggestions(suggestionsList, currentAnime)
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logcat(LogPriority.DEBUG) { "[ShowsScreenModel] External suggestions failed: ${e.message}" }
                        }
                    }

                    // Task 2: Search Fallback suggestions
                    if (currentAnime != null && currentSource != null) {
                        launch {
                            try {
                                val outcome = searchFallbackEngine.fetchSearchFallback(
                                    anime = currentAnime,
                                    source = currentSource,
                                    seed = seed,
                                    maxResults = 40,
                                    onProgress = { progressItems ->
                                        synchronized(suggestionsList) {
                                            val existingUrls = suggestionsList.map { it.providerUrl }.toSet()
                                            val newItems = progressItems.filter { it.providerUrl !in existingUrls }
                                            suggestionsList.addAll(newItems)
                                        }
                                        emitProgressiveSuggestions(suggestionsList, currentAnime)
                                    },
                                )
                                if (outcome is AnimeFallbackOutcome.Success && outcome.items.isNotEmpty()) {
                                    synchronized(suggestionsList) {
                                        val existingUrls = suggestionsList.map { it.providerUrl }.toSet()
                                        val newItems = outcome.items.filter { it.providerUrl !in existingUrls }
                                        suggestionsList.addAll(newItems)
                                    }
                                    emitProgressiveSuggestions(suggestionsList, currentAnime)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                logcat(LogPriority.DEBUG) {
                                    "[ShowsScreenModel] Native search fallback failed: ${e.message}"
                                }
                            }
                        }
                    }
                }

                val finalCombined = synchronized(suggestionsList) {
                    suggestionsList.dedupeByCleanTitle(seed)
                        .filter { item ->
                            val isSelf = SuggestionTitleResolver.isSameProviderEntry(item, currentAnime?.url)
                            val isFranchise = SuggestionTitleResolver.isFranchiseDuplicate(
                                item.title,
                                seed.primaryTitle,
                            )
                            !isSelf && !isFranchise
                        }
                        .sortedByDescending { SuggestionSourceWeight.finalScore(it.reason, it.bestMatchScoreFor(seed)) }
                        .take(20)
                }

                updateSuccessState {
                    val nextState = when {
                        finalCombined.isEmpty() -> SuggestionState.Empty()
                        else -> SuggestionState.Success(finalCombined)
                    }
                    it.copy(suggestions = nextState)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG) { "ShowsScreenModel suggestions fetch failed: ${e.message}" }
                updateSuccessState { it.copy(suggestions = SuggestionState.Error(e.message ?: "Unknown error")) }
            }
        }
    }

    init {
        // Warm-start: reopening an entry renders the last Success instantly
        // instead of flashing the spinner — the flows below overwrite every
        // field within a frame anyway. Cleared dialogs/refresh flags.
        lastSuccess[animeId]?.let {
            mutableState.value = it.copy(dialog = null, isRefreshingData = false)
        }
        screenModelScope.launch {
            state.collect { s ->
                if (s is State.Success) {
                    synchronized(lastSuccess) { lastSuccess[s.anime.id] = s.copy(dialog = null) }
                }
            }
        }

        screenModelScope.launchIO {
            combine(
                getAnimeAndEpisodesAndSeasons.subscribe(animeId).distinctUntilChanged(),
                downloadCache.changes,
                downloadManager.queueState,
            ) { animeAndEpisodesAndSeasons, _, _ -> animeAndEpisodesAndSeasons }
                .flowWithLifecycle(lifecycle)
                .collectLatest { (anime, episodes, seasons) ->
                    updateSuccessState {
                        it.copy(
                            anime = anime,
                            episodes = episodes.toEpisodeListItems(anime),
                            seasons = seasons.toAnimeSeasonItems(),
                        )
                    }
                }
        }

        observeDownloads()

        screenModelScope.launchIO {
            val anime = getAnimeAndEpisodesAndSeasons.awaitAnime(animeId)

            // Phase 15-02: cinemeta-bound entries resolve mk:imdb: +
            // markSeen DIRECTLY — CinemetaEntryUnifier.resolve early-returns
            // on cinemeta: urls, so entry-open can't rely on it firing.
            // Extension entries still go through the unifier first; on
            // redirect, the canonical entry's own open performs the resolve.
            if (eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.cinemetaRef(anime.url) == null) {
                // Unify extension-source entries with the canonical Cinemeta
                // entry (MikoDB identity). Redirect instead of loading this
                // duplicate — one entry per real anime.
                val canonical = try {
                    cinemetaEntryUnifier.resolve(anime)
                } catch (e: Exception) {
                    null
                }
                if (canonical != null && canonical.id != animeId) {
                    logcat(LogPriority.DEBUG) {
                        "ShowsScreenModel: redirecting '${anime.title}' (id=$animeId, source=${anime.source}) to canonical Cinemeta entry id=${canonical.id}"
                    }
                    redirectToAnimeId.value = canonical.id
                    return@launchIO
                }
            }

            // Resolve + report the shows work id (ID-only telemetry,
            // fire-and-forget — never blocks the screen). Internally it
            // kicks carrier loads + the shows canonize pipeline, so it
            // holds until the transition lands.
            screenModelScope.launchIO {
                transitionSettled.await()
                resolveShowsWorkId(anime)
            }

            // Independent reads run in parallel — first-open latency is
            // dominated by these round-trips back to back.
            val sourceDeferred = async { sourceManager.getOrStub(anime.source) }
            val episodesDeferred = async {
                if (anime.fetchType == FetchType.Seasons) {
                    emptyList()
                } else {
                    getAnimeAndEpisodesAndSeasons.awaitEpisodes(animeId)
                        .toEpisodeListItems(anime)
                }
            }
            val seasonsDeferred = async {
                if (anime.fetchType == FetchType.Episodes) {
                    emptyList()
                } else {
                    getAnimeAndEpisodesAndSeasons.awaitSeasons(animeId)
                        .toAnimeSeasonItems()
                }
            }
            val flagsDeferred = async {
                // Untouched rows only — non-favorite entries keep the
                // user's sort/filter choices across visits.
                if (!anime.favorite) {
                    if (anime.episodeFlags == 0L) setAnimeDefaultEpisodeFlags.await(anime)
                    if (anime.seasonFlags == 0L) setAnimeDefaultSeasonFlags.await(anime)
                }
            }

            val source = sourceDeferred.await()
            val episodes = episodesDeferred.await()
            val seasons = seasonsDeferred.await()
            flagsDeferred.await()

            val needRefreshInfo = !anime.initialized
            val needRefreshEpisode = episodes.isEmpty() && anime.fetchType == FetchType.Episodes
            val needRefreshSeason = seasons.isEmpty() && anime.fetchType == FetchType.Seasons

            // Cinemeta entries (source == 0, URL starts with "cinemeta:") — load virtual
            // episodes from Cinemeta metadata. Skip source-based episode/season/info fetching
            // since source 0 is a stub for Cinemeta entries.
            // Always call loadCinemetaEpisodes — it updates existing episodes' metadata
            // (ratings, thumbnails, dates) to keep them in sync with Cinemeta.
            // Cinemeta entries (source == 0, URL starts with "cinemeta:") — load virtual
            // episodes from Cinemeta metadata. Skip source-based episode/season/info fetching
            // since source 0 is a stub for Cinemeta entries.
            // Always call loadCinemetaEpisodes — it updates existing episodes' metadata
            // (ratings, thumbnails, dates) to keep them in sync with Cinemeta.
            // It runs AFTER the success state emits — the network fetches
            // (Cinemeta meta + TMDB stills) would otherwise keep the screen
            // on the loading spinner; the anime/episodes subscribe flow
            // picks the results up as they land in the DB.
            val isCinemetaEntry = anime.source == 0L && anime.url.startsWith("cinemeta:")

            // Seed the accent synchronously — this update REPLACES the
            // warm-started Success, so a default `accentColor = null`
            // would erase the extracted color and the composable's
            // LaunchedEffect never refires (its key didn't change).
            val isDark =
                (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            val cachedAccentColor = eu.kanade.tachiyomi.util.EntryCoverMetadata.getBaseColor(
                eu.kanade.tachiyomi.util.EntryCoverMetadata.EntryType.SHOWS,
                anime.id,
                eu.kanade.presentation.entries.components.coverKeyOf(anime),
            )?.let {
                Color(eu.kanade.presentation.entries.components.adjustForTheme(it, isDark))
            }

            // Show what we have earlier
            val intervalDays = fetchInterval.calculateInterval(
                episodes.map { it.episode },
                kotlinx.datetime.TimeZone.currentSystemDefault(),
            )
            // Hide interval badge if series is completed or last update is
            // older than 2 months (stale schedule).
            val showInterval = shouldShowInterval(anime.status, episodes.map { it.episode })
            mutableState.update { prev ->
                val p = prev as? State.Success
                State.Success(
                    anime = anime,
                    source = source,
                    isFromSource = isFromSource,
                    episodes = episodes,
                    seasons = seasons,
                    isRefreshingData = needRefreshInfo || needRefreshEpisode || needRefreshSeason,
                    dialog = null,
                    accentColor = cachedAccentColor ?: p?.accentColor,
                    intervalDays = intervalDays,
                    showInterval = showInterval,
                    // Secondary overlays the warm-started snapshot already
                    // carries — wiping them here makes their chrome vanish
                    // mid-transition and pop back when the gated refetch
                    // lands post-settle (carrier label, comment badge).
                    trackingCount = p?.trackingCount ?: 0,
                    hasLoggedInTrackers = p?.hasLoggedInTrackers ?: false,
                    trackItems = p?.trackItems ?: emptyList(),
                    suggestions = p?.suggestions ?: SuggestionState.Idle,
                    commentsCount = p?.commentsCount,
                    canonicalWorkId = p?.canonicalWorkId,
                    canonicalKind = p?.canonicalKind,
                    carrierMatrix = p?.carrierMatrix,
                    canonicalEpisodes = p?.canonicalEpisodes ?: emptyList(),
                )
            }

            // Everything below is secondary — holds until the nav
            // transition lands (see transitionSettled).
            transitionSettled.await()

            // Cinemeta meta/episode sync in the background — the screen shows
            // what's already in the DB immediately; DB writes flow through
            // the subscribe flow above as they land.
            if (isCinemetaEntry) {
                screenModelScope.launchIO {
                    loadCinemetaEpisodes.await(anime)
                }
            }

            // Start observe tracking since it only needs animeId
            observeTrackers()

            // "View comments (N)" badge — resolves the work thread once
            // and counts its visible comments. ID-only, silent on failure.
            if (commentsEnabled) {
                screenModelScope.launchIO {
                    transitionSettled.await()
                    val threadId = threadIdResolver.forShows(anime) ?: return@launchIO
                    val result = commentsApi.threadCounts(listOf(threadId))
                    if (result is eu.kanade.tachiyomi.data.comments.CommentsApi.Result.Ok) {
                        updateSuccessState {
                            it.copy(commentsCount = result.value.counts[threadId] ?: 0L)
                        }
                    }
                }
            }

            // Fetch suggestions asynchronously
            loadSuggestions(buildSuggestionSeed(anime))

            // Fetch info-episodes when needed — skip for Cinemeta entries (source 0 is a stub)
            if (screenModelScope.isActive && !isCinemetaEntry) {
                val fetchFromSourceTasks = listOf(
                    async { if (needRefreshInfo) fetchAnimeFromSource() },
                    async { if (needRefreshEpisode || needRefreshSeason) fetchEpisodesAndSeasonsFromSource() },
                )
                fetchFromSourceTasks.awaitAll()
            }

            // Initial loading finished
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    /**
     * Phase 15-02 shows work identity — resolve to a canonical work id
     * and report it via `/v1/ids/seen` (ID-only contract: work_id +
     * medium; silent on failure).
     *
     * `cinemeta:{type}:{ttid}` urls feed ('imdb', ttid) straight into the
     * resolver's external-ID mining; the url's type also seeds
     * `kind_hint` (fetch-order only — the worker is the kind authority).
     * For extension entries the anime map bridges tracker-bound ids
     * (anilist > mal, then title) to an imdb id; genuinely uncovered
     * shows fall back to a shows-kind `mk:m:` mint. A resolved id kicks
     * the shows pipeline (19-03): artifact + carrier episode feeds →
     * `canonical_episodes`.
     */
    private suspend fun resolveShowsWorkId(anime: Anime) {
        try {
            val ref = eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.cinemetaRef(anime.url)
            var kindHint = ref?.first
            var url = anime.url
            if (ref == null) {
                val bridge = mikoDbManager.bridge()
                if (bridge != null) {
                    val tracks = runCatching { getTracks.await(anime.id) }.getOrDefault(emptyList())
                    val mapping = tracks
                        .firstOrNull { it.trackerId == TrackerManager.ANILIST }?.remoteId
                        ?.let(bridge::byAnilist)
                        ?: tracks.firstOrNull { it.trackerId == MAL_TRACKER_ID }?.remoteId
                            ?.let(bridge::byMal)
                        ?: bridge.byTitle(anime.title)
                    mapping?.imdbId?.let { imdbId ->
                        if (kindHint == null && mapping.animeMediaType == "MOVIE") kindHint = "movie"
                        url = "cinemeta:${kindHint ?: "series"}:$imdbId"
                    }
                }
            }
            val resolved = workIdResolver.resolve(
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.Candidate(
                    medium = eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_SHOWS,
                    title = anime.title,
                    url = url,
                    kindHint = kindHint,
                ),
            ) ?: return
            workIdentityApi.markSeen(resolved.value, resolved.medium)
            // Retain the work id + worker kind for carrier/provider
            // state (17-02) — non-anime movies/series are ID-only.
            updateSuccessState {
                it.copy(canonicalWorkId = resolved.value, canonicalKind = resolved.kind)
            }
            loadCarriers(resolved.value)
            // Shows pipeline (19-03): enrich is a no-op for anime (the
            // artifact couriers canon into the feeds); canonize →
            // canonical_episodes, hydration lands in 19-04. Fire-and-
            // forget — never blocks the resolve path.
            canonicalPipelineRunner.launchShows(resolved.value, anime)
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG) { "shows work resolve failed for '${anime.title}': ${e.message}" }
        }
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        screenModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            val state = successState ?: return@launch
            val isCinemetaEntry = state.anime.source == 0L && state.anime.url.startsWith("cinemeta:")
            if (isCinemetaEntry) {
                // Cinemeta entries — reload virtual episodes from Cinemeta metadata.
                // Skip source-based fetching since source 0 is a stub.
                withIOContext {
                    loadCinemetaEpisodes.await(state.anime)
                }
                updateSuccessState { it.copy(isRefreshingData = false) }
            } else {
                val fetchFromSourceTasks = listOf(
                    async { fetchAnimeFromSource(manualFetch) },
                    async { fetchEpisodesAndSeasonsFromSource(manualFetch) },
                )
                fetchFromSourceTasks.awaitAll()
                updateSuccessState { it.copy(isRefreshingData = false) }
                successState?.let { updateAiringTime(it.anime, it.trackItems, manualFetch) }
            }
        }
    }

    // Anime info - start

    /**
     * Fetch anime information from source.
     */
    private suspend fun fetchAnimeFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        try {
            withIOContext {
                startTorrentServer(state.source)
                val networkAnime = state.source.getAnimeDetails(state.anime.toSAnime())
                updateAnime.awaitUpdateFromSource(state.anime, networkAnime, manualFetch)
            }
        } catch (e: Throwable) {
            // Ignore early hints "errors" that aren't handled by OkHttp
            if (e is HttpException && e.code == 103) return

            logcat(LogPriority.ERROR, e)
            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = with(context) { e.formattedMessage })
            }
        }
    }

    fun toggleFavorite() {
        toggleFavorite(
            onRemoved = {
                screenModelScope.launch {
                    if (!hasDownloads()) return@launch
                    val result = snackbarHostState.showSnackbar(
                        message = context.stringResource(AYMR.strings.delete_downloads_for_anime),
                        actionLabel = context.stringResource(MR.strings.action_delete),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        deleteDownloads()
                    }
                }
            },
        )
    }

    /**
     * Update favorite status of anime, (removes / adds) anime (to / from) library.
     */
    fun toggleFavorite(
        onRemoved: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        screenModelScope.launchIO {
            val anime = state.anime

            if (isFavorited) {
                // Remove from library
                if (updateAnime.awaitUpdateFavorite(anime.id, false)) {
                    // Remove covers and update last modified in db
                    if (anime.removeCovers() != anime) {
                        updateAnime.awaitUpdateCoverLastModified(anime.id)
                    }
                    withUIContext { onRemoved() }
                }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (checkDuplicate) {
                    val duplicate = getDuplicateLibraryAnime.await(anime).getOrNull(0)
                    if (duplicate != null) {
                        updateSuccessState {
                            it.copy(
                                dialog = Dialog.DuplicateAnime(anime, duplicate),
                            )
                        }
                        return@launchIO
                    }
                }

                // Now check if user previously set collections, when available
                val collections = getCollections()
                val defaultCollectionId = libraryPreferences.defaultShowsCollection().get().toLong()
                val defaultCollection = collections.find { it.id == defaultCollectionId }
                when {
                    // Default collection set
                    defaultCollection != null -> {
                        val result = updateAnime.awaitUpdateFavorite(anime.id, true)
                        if (!result) return@launchIO
                        moveAnimeToCollection(defaultCollection)
                    }

                    // Automatic 'Default' or no collections
                    defaultCollectionId == 0L || collections.isEmpty() -> {
                        val result = updateAnime.awaitUpdateFavorite(anime.id, true)
                        if (!result) return@launchIO
                        moveAnimeToCollection(null)
                    }

                    // Choose a collection
                    else -> {
                        isFromChangeCollection = true
                        showChangeCollectionDialog()
                    }
                }

                // Finally match with enhanced tracking when available
                addTracks.bindEnhancedTrackers(anime, state.source)
                if (autoOpenTrack) {
                    showTrackDialog()
                }
            }
        }
    }

    fun showChangeCollectionDialog() {
        val anime = successState?.anime ?: return
        screenModelScope.launch {
            val collections = getCollections()
            val selection = getAnimeCollectionIds(anime)
            updateSuccessState { successState ->
                successState.copy(
                    dialog = Dialog.ChangeCollection(
                        anime = anime,
                        initialSelection = collections.mapAsCheckboxState { it.id in selection }.toImmutableList(),
                    ),
                )
            }
        }
    }

    fun showSetAnimeFetchIntervalDialog() {
        val anime = successState?.anime ?: return
        updateSuccessState {
            it.copy(dialog = Dialog.SetAnimeFetchInterval(anime))
        }
    }

    fun setFetchInterval(anime: Anime, interval: Int) {
        screenModelScope.launchIO {
            if (
                updateAnime.awaitUpdateFetchInterval(
                    // Custom intervals are negative
                    anime.copy(fetchInterval = -interval),
                )
            ) {
                val updatedAnime = animeRepository.getAnimeById(anime.id)
                updateSuccessState { it.copy(anime = updatedAnime) }
            }
        }
    }

    /**
     * Returns true if the anime has any downloads.
     */
    private fun hasDownloads(): Boolean {
        val anime = successState?.anime ?: return false
        return downloadManager.getDownloadCount(anime) > 0
    }

    /**
     * Deletes all the downloads for the anime.
     */
    private fun deleteDownloads() {
        val state = successState ?: return
        downloadManager.deleteAnime(state.anime, state.source)
    }

    /**
     * Get user collections.
     *
     * @return List of collections, not including the default collection
     */
    suspend fun getCollections(): List<Collection> {
        return getCollections.await().filterNot { it.isSystemCollection }
    }

    /**
     * Gets the collection id's the anime is in, if the anime is not in a collection, returns the default id.
     *
     * @param anime the anime to get collections from.
     * @return Array of collection ids the anime is in, if none returns default id
     */
    private suspend fun getAnimeCollectionIds(anime: Anime): List<Long> {
        return getCollections.await(anime.id)
            .map { it.id }
    }

    fun moveAnimeToCollectionsAndAddToLibrary(anime: Anime, collections: List<Long>) {
        moveAnimeToCollection(collections)
        if (anime.favorite) return

        screenModelScope.launchIO {
            updateAnime.awaitUpdateFavorite(anime.id, true)
        }
    }

    /**
     * Move the given anime to collections.
     *
     * @param collections the selected collections.
     */
    private fun moveAnimeToCollections(collections: List<Collection>) {
        val collectionIds = collections.map { it.id }
        moveAnimeToCollection(collectionIds)
    }

    private fun moveAnimeToCollection(collectionIds: List<Long>) {
        screenModelScope.launchIO {
            setAnimeCollections.await(animeId, collectionIds)
        }
    }

    /**
     * Move the given anime to the collection.
     *
     * @param collection the selected collection, or null for default collection.
     */
    private fun moveAnimeToCollection(collection: Collection?) {
        moveAnimeToCollections(listOfNotNull(collection))
    }

    // Anime info - end

    // Episodes list - start

    private fun observeDownloads() {
        screenModelScope.launchIO {
            downloadManager.statusFlow()
                // Cinemeta downloads queue against the source anime's
                // episode — a ref maps it back to the displayed Cinemeta
                // episode, so let those through too.
                .filter {
                    it.anime.id == successState?.anime?.id ||
                        sourceMappingCache.cinemetaEpisodeIdFor(it.episode.id) != null
                }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }

        screenModelScope.launchIO {
            downloadManager.progressFlow()
                .filter {
                    it.anime.id == successState?.anime?.id ||
                        sourceMappingCache.cinemetaEpisodeIdFor(it.episode.id) != null
                }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }
    }

    private fun updateDownloadState(download: ShowsDownload) {
        updateSuccessState { successState ->
            // Cinemeta downloads run against the source anime's episode —
            // remap to the Cinemeta episode the card displays.
            val episodeId = download.episode.id.takeIf { id ->
                successState.episodes.any { it.id == id }
            } ?: sourceMappingCache.cinemetaEpisodeIdFor(download.episode.id)
                ?: return@updateSuccessState successState
            val modifiedIndex = successState.episodes.indexOfFirst { it.id == episodeId }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newEpisodes = successState.episodes.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    .copy(downloadState = download.status, downloadProgress = download.progress)
                add(modifiedIndex, item)
            }
            successState.copy(episodes = newEpisodes)
        }
    }

    private suspend fun List<Episode>.toEpisodeListItems(anime: Anime): List<EpisodeList.Item> {
        val isLocal = anime.isLocal()
        // Cinemeta entries also have source == 0 (same as LocalAnimeSource.ID),
        // but their URL starts with "cinemeta:" — distinguish them from local.
        val isCinemeta = anime.source == 0L && anime.url.startsWith("cinemeta:")
        val cinemetaDownloads = if (isCinemeta) cinemetaDownloadStates() else emptyMap()
        return map { episode ->
            val activeDownload = when {
                isCinemeta -> cinemetaDownloads[episode.id]?.activeDownload
                isLocal -> null
                else -> downloadManager.getQueuedDownloadOrNull(episode.id)
            }
            val downloaded = if (isLocal && !isCinemeta) {
                true
            } else if (isCinemeta) {
                cinemetaDownloads[episode.id]?.downloaded == true
            } else {
                downloadManager.isEpisodeDownloaded(
                    episode.name,
                    episode.scanlator,
                    anime.title,
                    anime.source,
                )
            }
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> ShowsDownload.State.DOWNLOADED
                else -> ShowsDownload.State.NOT_DOWNLOADED
            }

            EpisodeList.Item(
                episode = episode,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                selected = episode.id in selectedEpisodeIds,
            )
        }
    }

    private data class CinemetaDownloadState(
        val activeDownload: ShowsDownload?,
        val downloaded: Boolean,
    )

    /**
     * Download state for Cinemeta virtual episodes. Episodes queued through
     * a source download carry a ref to the real source episode — resolve
     * queue + filesystem state from that entry, dropping stale refs.
     */
    private suspend fun cinemetaDownloadStates(): Map<Long, CinemetaDownloadState> {
        val refs = sourceMappingCache.episodeDownloadRefs()
        if (refs.isEmpty()) return emptyMap()
        val result = mutableMapOf<Long, CinemetaDownloadState>()
        refs.entries.groupBy { it.value.sourceAnimeId }.forEach { (sourceAnimeId, entries) ->
            val sourceAnime = try {
                animeRepository.getAnimeById(sourceAnimeId)
            } catch (e: Exception) {
                null
            } ?: return@forEach
            val sourceEpisodes = getEpisodesByAnimeId.await(sourceAnimeId).associateBy { it.id }
            entries.forEach { (cinemetaEpisodeId, ref) ->
                val sourceEpisode = sourceEpisodes[ref.sourceEpisodeId]
                if (sourceEpisode == null) {
                    sourceMappingCache.removeEpisodeDownload(cinemetaEpisodeId)
                    return@forEach
                }
                val queued = downloadManager.getQueuedDownloadOrNull(sourceEpisode.id)
                val downloaded = downloadManager.isEpisodeDownloaded(
                    sourceEpisode.name,
                    sourceEpisode.scanlator,
                    sourceAnime.title,
                    ref.sourceId,
                )
                result[cinemetaEpisodeId] = CinemetaDownloadState(queued, downloaded)
            }
        }
        return result
    }

    /**
     * Queue Cinemeta episode downloads from a user-picked extension source.
     * Materializes the source anime, syncs its episode list, resolves each
     * Cinemeta episode to its source counterpart, records the mapping so
     * the cards can reflect queue/downloaded state, then enqueues real
     * downloads via [ShowsDownloadManager].
     */
    fun downloadCinemetaEpisodes(
        items: List<EpisodeList.Item>,
        candidate: eu.kanade.tachiyomi.metadata.stream.StreamResolver.StreamCandidate.Extension,
    ) {
        val anime = successState?.anime ?: return
        screenModelScope.launchIO {
            try {
                // Cache the pick as this entry's source mapping — the user
                // chose this source, so resume/continue paths can resolve
                // it without re-showing the picker.
                streamResolver.selectCandidate(anime, candidate)
                val sourceAnime = streamResolver.materializeExtension(candidate)
                val sEpisodes = candidate.source.getEpisodeList(sourceAnime.toSAnime())
                if (sEpisodes.isEmpty()) {
                    withUIContext { context.toast("No episodes on ${candidate.source.name}") }
                    return@launchIO
                }
                syncEpisodesWithSource.await(
                    sEpisodes,
                    sourceAnime,
                    candidate.source,
                    manualFetch = false,
                )
                val sourceEpisodes = getEpisodesByAnimeId.await(sourceAnime.id)

                val resolved = items.mapNotNull { item ->
                    resolveCinemetaEpisodeTarget(
                        item.episode,
                        sourceEpisodes,
                        anime,
                        candidate.source.id,
                    )?.let { item.episode to it }
                }
                if (resolved.isEmpty()) {
                    withUIContext {
                        context.toast("Couldn't match episodes on ${candidate.source.name}")
                    }
                    return@launchIO
                }
                resolved.forEach { (cinemetaEpisode, sourceEpisode) ->
                    sourceMappingCache.putEpisodeDownload(
                        cinemetaEpisodeId = cinemetaEpisode.id,
                        ref = eu.kanade.tachiyomi.metadata.stream.SourceMappingCache.EpisodeDownloadRef(
                            sourceId = candidate.source.id,
                            sourceAnimeId = sourceAnime.id,
                            sourceEpisodeId = sourceEpisode.id,
                        ),
                    )
                }
                downloadManager.downloadEpisodes(
                    sourceAnime,
                    resolved.map { it.second },
                    autoStart = true,
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Cinemeta source download failed" }
                withUIContext { context.toast("Download failed: ${e.message}") }
            }
        }
    }

    /**
     * Non-START download actions on Cinemeta entries (CANCEL queued,
     * DELETE downloaded). The real download lives on the resolved source
     * episode — look it up via the ref recorded at queue time.
     */
    fun runCinemetaDownloadActions(
        items: List<EpisodeList.Item>,
        action: EpisodeDownloadAction,
    ) {
        screenModelScope.launchIO {
            try {
                when (action) {
                    EpisodeDownloadAction.CANCEL -> {
                        items.forEach { item ->
                            val ref = sourceMappingCache.episodeDownloadRef(item.episode.id)
                                ?: return@forEach
                            downloadManager.getQueuedDownloadOrNull(ref.sourceEpisodeId)
                                ?.let { downloadManager.cancelQueuedDownloads(listOf(it)) }
                        }
                    }
                    EpisodeDownloadAction.DELETE -> {
                        val pairs = items.mapNotNull { item ->
                            sourceMappingCache.episodeDownloadRef(item.episode.id)
                                ?.let { item.episode.id to it }
                        }
                        pairs.groupBy({ it.second.sourceAnimeId }, { it })
                            .forEach { (sourceAnimeId, group) ->
                                val sourceAnime = try {
                                    animeRepository.getAnimeById(sourceAnimeId)
                                } catch (e: Exception) {
                                    null
                                } ?: return@forEach
                                val source = sourceManager.get(group.first().second.sourceId)
                                    ?: return@forEach
                                val sourceEpisodes = getEpisodesByAnimeId.await(sourceAnimeId)
                                    .associateBy { it.id }
                                val episodes = group.mapNotNull { sourceEpisodes[it.second.sourceEpisodeId] }
                                if (episodes.isNotEmpty()) {
                                    downloadManager.deleteEpisodes(episodes, sourceAnime, source)
                                }
                                group.forEach { (cinemetaEpisodeId, _) ->
                                    sourceMappingCache.removeEpisodeDownload(cinemetaEpisodeId)
                                }
                            }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Cinemeta download action failed" }
            }
        }
    }

    /**
     * Match a Cinemeta virtual episode to a real source episode — exact
     * name first, then MikoDB-aware numbering targets (bound-entry local
     * number, season number, absolute), same order as the playback
     * stream-resolution path.
     */
    private fun resolveCinemetaEpisodeTarget(
        cinemetaEpisode: Episode,
        sourceEpisodes: List<Episode>,
        cinemetaAnime: Anime,
        sourceId: Long,
    ): Episode? {
        val name = cinemetaEpisode.name.trim()
        if (name.isNotEmpty()) {
            sourceEpisodes.find { it.name.trim().equals(name, ignoreCase = true) }
                ?.let { return it }
        }
        val imdbId = cinemetaAnime.url.split(":").getOrNull(2)
        val (season, epFromUrl) = cinemetaSeasonEpisode(cinemetaEpisode.url)
        val targets = streamResolver.episodeTargets(
            sourceId,
            imdbId,
            season,
            epFromUrl ?: cinemetaEpisode.episodeNumber.toInt(),
        )
        return targets.firstNotNullOfOrNull { target ->
            sourceEpisodes.find { it.episodeNumber.toInt() == target }
        }
    }

    /** "cinemeta:episode:tt0903747:1:1" → (season, episode) */
    private fun cinemetaSeasonEpisode(episodeUrl: String?): Pair<Int?, Int?> {
        if (episodeUrl == null || !episodeUrl.startsWith("cinemeta:episode:")) return null to null
        val parts = episodeUrl.split(":")
        return parts.getOrNull(3)?.toIntOrNull() to parts.getOrNull(4)?.toIntOrNull()
    }

    /**
     * Delete source downloads backing Cinemeta episodes — resolves each
     * ref, removes the real downloaded files, and drops the mapping.
     */
    fun deleteCinemetaDownloads(items: List<EpisodeList.Item>) {
        screenModelScope.launchIO {
            items.forEach { item ->
                val ref = sourceMappingCache.episodeDownloadRef(item.episode.id)
                    ?: return@forEach
                try {
                    val sourceAnime = animeRepository.getAnimeById(ref.sourceAnimeId)
                    val sourceEpisode = getEpisodesByAnimeId.await(ref.sourceAnimeId)
                        .find { it.id == ref.sourceEpisodeId }
                    val source = sourceManager.get(ref.sourceId)
                    if (sourceEpisode != null && source != null) {
                        downloadManager.deleteEpisodes(listOf(sourceEpisode), sourceAnime, source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to delete source download" }
                }
                sourceMappingCache.removeEpisodeDownload(item.episode.id)
            }
        }
    }

    /** Cancel in-flight source downloads backing Cinemeta episodes. */
    fun cancelCinemetaDownloads(items: List<EpisodeList.Item>) {
        items.forEach { item ->
            val ref = sourceMappingCache.episodeDownloadRef(item.episode.id)
                ?: return@forEach
            val activeDownload = downloadManager.getQueuedDownloadOrNull(ref.sourceEpisodeId)
                ?: return@forEach
            downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        }
    }

    private fun List<SeasonAnime>.toAnimeSeasonItems(): List<ShowsSeasonItem> {
        return map { seasonAnime ->
            ShowsSeasonItem(
                seasonAnime = seasonAnime,
                downloadCount = downloadManager.getDownloadCount(seasonAnime.anime).toLong(),
                unseenCount = seasonAnime.unseenCount,
                isLocal = seasonAnime.anime.isLocal(),
                sourceLanguage = sourceManager.getOrStub(seasonAnime.anime.source).lang,
                showContinueOverlay = false,
            )
        }
    }

    private suspend fun fetchEpisodesFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        try {
            withIOContext {
                updateEpisodesFromSource(state.anime, state.source, manualFetch)
            }
        } catch (e: Throwable) {
            val message = if (e is NoEpisodesException) {
                context.stringResource(AYMR.strings.no_episodes_error)
            } else {
                logcat(LogPriority.ERROR, e)
                with(context) { e.formattedMessage }
            }

            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
            val newAnime = animeRepository.getAnimeById(animeId)
            updateSuccessState { it.copy(anime = newAnime, isRefreshingData = false) }
        }
    }

    private suspend fun updateEpisodesFromSource(
        anime: Anime,
        source: AnimeSource,
        manualFetch: Boolean = false,
    ) {
        val episodes = source.getEpisodeList(anime.toSAnime())

        val newEpisodes = syncEpisodesWithSource.await(
            episodes,
            anime,
            source,
            manualFetch,
        )

        if (manualFetch) {
            downloadNewEpisodes(newEpisodes)
        }
    }

    private suspend fun fetchSeasonsFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        try {
            withIOContext {
                val seasons = state.source.getSeasonList(state.anime.toSAnime())

                val newSeasons = syncSeasonsWithSource.await(
                    seasons,
                    state.anime,
                    state.source,
                )

                if (libraryPreferences.updateSeasonOnRefresh().get()) {
                    fetchEpisodesFromSeasons(newSeasons, manualFetch)
                }
            }
        } catch (e: Throwable) {
            val message = if (e is NoSeasonsException) {
                context.stringResource(AYMR.strings.no_seasons_error)
            } else {
                logcat(LogPriority.ERROR, e)
                with(context) { e.formattedMessage }
            }

            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
            val newAnime = animeRepository.getAnimeById(animeId)
            updateSuccessState { it.copy(anime = newAnime, isRefreshingData = false) }
        }
    }

    fun isTorrentEnabled(): Boolean {
        return torrentPreferences.torrServerEnable().get()
    }

    private suspend fun startTorrentServer(source: AnimeSource?) {
        if (isTorrentEnabled() && source.isSourceForTorrents()) {
            TorrentServerService.startInProcess()
            torrentServerUtils.setTrackersList()
        }
    }

    /**
     * Requests an updated list of episodes and seasons from the source.
     */
    private suspend fun fetchEpisodesAndSeasonsFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return

        startTorrentServer(state.source)

        when (state.anime.fetchType) {
            FetchType.Seasons -> fetchSeasonsFromSource(manualFetch)
            FetchType.Episodes -> fetchEpisodesFromSource(manualFetch)
        }
    }

    /**
     * Fetch episodes from all seasons of an anime.
     */
    private suspend fun CoroutineScope.fetchEpisodesFromSeasons(seasons: List<Anime>, manualFetch: Boolean) {
        val state = successState ?: return

        val fetch: suspend (Anime) -> Unit = { s ->
            // Only fetch seasons with `Episodes` fetch type and only for non completed, unless they
            // haven't been fetched at all.
            if (s.fetchType === FetchType.Episodes && (s.lastUpdate == 0L || s.status.toInt() != SAnime.COMPLETED)) {
                try {
                    updateEpisodesFromSource(s, state.source, manualFetch)
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e)
                }
            }
        }

        if (state.source is UnmeteredSource) {
            seasons.map { s ->
                async(Dispatchers.IO) {
                    fetch(s)
                }
            }.awaitAll()
        } else {
            seasons.forEach { s ->
                ensureActive()
                fetch(s)
            }
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.EpisodeSwipeAction.Disabled]
     */
    fun episodeSwipe(episodeItem: EpisodeList.Item, swipeAction: LibraryPreferences.EpisodeSwipeAction) {
        screenModelScope.launch {
            executeEpisodeSwipeAction(episodeItem, swipeAction)
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.EpisodeSwipeAction.Disabled]
     */
    private fun executeEpisodeSwipeAction(
        episodeItem: EpisodeList.Item,
        swipeAction: LibraryPreferences.EpisodeSwipeAction,
    ) {
        val episode = episodeItem.episode
        when (swipeAction) {
            LibraryPreferences.EpisodeSwipeAction.ToggleSeen -> {
                markEpisodesSeen(listOf(episode), !episode.seen)
            }
            LibraryPreferences.EpisodeSwipeAction.ToggleBookmark -> {
                bookmarkEpisodes(listOf(episode), !episode.bookmark)
            }
            LibraryPreferences.EpisodeSwipeAction.ToggleFillermark -> {
                fillermarkEpisodes(listOf(episode), !episode.fillermark)
            }
            LibraryPreferences.EpisodeSwipeAction.Download -> {
                val downloadAction: EpisodeDownloadAction = when (episodeItem.downloadState) {
                    ShowsDownload.State.ERROR,
                    ShowsDownload.State.NOT_DOWNLOADED,
                    -> EpisodeDownloadAction.START_NOW
                    ShowsDownload.State.QUEUE,
                    ShowsDownload.State.DOWNLOADING,
                    -> EpisodeDownloadAction.CANCEL
                    ShowsDownload.State.DOWNLOADED -> EpisodeDownloadAction.DELETE
                }
                runEpisodeDownloadActions(
                    items = listOf(episodeItem),
                    action = downloadAction,
                )
            }
            LibraryPreferences.EpisodeSwipeAction.Disabled -> throw IllegalStateException()
        }
    }

    suspend fun getNextUnseenEpisode(anime: Anime): Episode? {
        return getEpisodesByAnimeId.await(anime.id).getNextUnseen(anime, downloadManager)
    }

    /**
     * Returns the next unseen episode or null if everything is seen.
     */
    fun getNextUnseenEpisode(): Episode? {
        val successState = successState ?: return null
        return successState.episodes.getNextUnseen(successState.anime)
    }

    private fun getUnseenEpisodes(): List<Episode> {
        return successState?.processedEpisodes
            ?.filter { (episode, dlStatus) -> !episode.seen && dlStatus == ShowsDownload.State.NOT_DOWNLOADED }
            ?.map { it.episode }
            ?.toList()
            ?: emptyList()
    }

    private fun getUnseenEpisodesSorted(): List<Episode> {
        val anime = successState?.anime ?: return emptyList()
        val episodes = getUnseenEpisodes().sortedWith(getEpisodeSort(anime))
        return if (anime.sortDescending()) episodes.reversed() else episodes
    }

    private fun startDownload(
        episodes: List<Episode>,
        startNow: Boolean,
        video: Video? = null,
    ) {
        val successState = successState ?: return

        screenModelScope.launchNonCancellable {
            if (startNow) {
                val episodeId = episodes.singleOrNull()?.id ?: return@launchNonCancellable
                downloadManager.startDownloadNow(episodeId)
            } else {
                downloadEpisodes(episodes, false, video)
            }
            if (!isFavorited && !successState.hasPromptedToAddBefore) {
                updateSuccessState { state ->
                    state.copy(hasPromptedToAddBefore = true)
                }
                val result = snackbarHostState.showSnackbar(
                    message = context.stringResource(AYMR.strings.snack_add_to_anime_library),
                    actionLabel = context.stringResource(MR.strings.action_add),
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed && !isFavorited) {
                    toggleFavorite()
                }
            }
        }
    }

    fun runEpisodeDownloadActions(
        items: List<EpisodeList.Item>,
        action: EpisodeDownloadAction,
    ) {
        when (action) {
            EpisodeDownloadAction.START -> {
                startDownload(items.map { it.episode }, false)
                if (items.any { it.downloadState == ShowsDownload.State.ERROR }) {
                    downloadManager.startDownloads()
                }
            }
            EpisodeDownloadAction.START_NOW -> {
                val episode = items.singleOrNull()?.episode ?: return
                startDownload(listOf(episode), true)
            }
            EpisodeDownloadAction.CANCEL -> {
                val episodeId = items.singleOrNull()?.id ?: return
                cancelDownload(episodeId)
            }
            EpisodeDownloadAction.DELETE -> {
                deleteEpisodes(items.map { it.episode })
            }
            EpisodeDownloadAction.SHOW_QUALITIES -> {
                val episode = items.singleOrNull()?.episode ?: return
                showQualitiesDialog(episode)
            }
        }
    }

    fun runDownloadAction(action: DownloadAction) {
        val episodesToDownload = when (action) {
            DownloadAction.NEXT_1_ITEM -> getUnseenEpisodesSorted().take(1)
            DownloadAction.NEXT_5_ITEMS -> getUnseenEpisodesSorted().take(5)
            DownloadAction.NEXT_10_ITEMS -> getUnseenEpisodesSorted().take(10)
            DownloadAction.NEXT_25_ITEMS -> getUnseenEpisodesSorted().take(25)

            DownloadAction.UNVIEWED_ITEMS -> getUnseenEpisodes()
        }
        if (episodesToDownload.isNotEmpty()) {
            startDownload(episodesToDownload, false)
        }
    }

    private fun cancelDownload(episodeId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(episodeId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = ShowsDownload.State.NOT_DOWNLOADED })
    }

    fun markPreviousEpisodeSeen(pointer: Episode, seen: Boolean = true) {
        val anime = successState?.anime ?: return
        val episodes = processedEpisodes.orEmpty().map { it.episode }.toList()
        // Cinemeta season pills: scope "previous" to the pointer's own
        // season — marking must not leak across seasons.
        val scoped = if (anime.source == 0L && anime.url.startsWith("cinemeta:")) {
            val season = cinemetaSeasonOf(pointer.scanlator)
            episodes.filter { cinemetaSeasonOf(it.scanlator) == season }
        } else {
            episodes
        }
        val prevEpisodes = if (anime.sortDescending()) scoped.asReversed() else scoped
        val pointerPos = prevEpisodes.indexOf(pointer)
        if (pointerPos != -1) markEpisodesSeen(prevEpisodes.take(pointerPos), seen)
    }

    /** Parse the season out of a Cinemeta episode scanlator ("Season 3|★8.1"). */
    private fun cinemetaSeasonOf(scanlator: String?): Int {
        if (scanlator.isNullOrBlank()) return 1
        return scanlator.substringBefore("|").removePrefix("Season ").trim().toIntOrNull() ?: 1
    }

    /**
     * Mark the selected episode list as seen/unseen.
     * @param episodes the list of selected episodes.
     * @param seen whether to mark episodes as seen or unseen.
     */
    fun markEpisodesSeen(episodes: List<Episode>, seen: Boolean) {
        toggleAllSelection(false)
        if (episodes.isEmpty()) return
        screenModelScope.launchIO {
            setSeenStatus.await(
                seen = seen,
                episodes = episodes.toTypedArray(),
            )

            if (!seen || successState?.hasLoggedInTrackers == false || autoTrackState == AutoTrackState.NEVER) {
                return@launchIO
            }

            refreshTrackers()

            val tracks = getTracks.await(animeId)
            val maxEpisodeNumber = episodes.maxOf { it.episodeNumber }
            val shouldPromptTrackingUpdate = tracks.any { track -> maxEpisodeNumber > track.lastEpisodeSeen }

            if (!shouldPromptTrackingUpdate) return@launchIO

            if (autoTrackState == AutoTrackState.ALWAYS) {
                trackEpisode.await(context, animeId, maxEpisodeNumber)
                withUIContext {
                    context.toast(
                        context.stringResource(AYMR.strings.trackers_updated_summary_anime, maxEpisodeNumber.toInt()),
                    )
                }
                return@launchIO
            }

            val result = snackbarHostState.showSnackbar(
                message = context.stringResource(AYMR.strings.confirm_tracker_update_anime, maxEpisodeNumber.toInt()),
                actionLabel = context.stringResource(MR.strings.action_ok),
                duration = SnackbarDuration.Short,
                withDismissAction = true,
            )

            if (result == SnackbarResult.ActionPerformed) {
                trackEpisode.await(context, animeId, maxEpisodeNumber)
            }
        }
    }

    fun markAllSeen() {
        val episodes = successState?.episodes?.map { it.episode } ?: return
        markEpisodesSeen(episodes, true)
    }

    fun markAllUnseen() {
        val episodes = successState?.episodes?.map { it.episode } ?: return
        markEpisodesSeen(episodes, false)
    }

    fun deleteAllDownloads() {
        val state = successState ?: return
        downloadManager.deleteAnime(state.anime, state.source)
    }

    fun deleteNonBookmarkedDownloads() {
        val state = successState ?: return
        val episodes = state.episodes.filter { !it.episode.bookmark }.map { it.episode }
        if (episodes.isNotEmpty()) {
            screenModelScope.launchNonCancellable {
                downloadManager.deleteEpisodes(episodes, state.anime, state.source)
            }
        }
    }

    fun deleteSeenDownloads() {
        val state = successState ?: return
        val episodes = state.episodes.filter { it.episode.seen }.map { it.episode }
        if (episodes.isNotEmpty()) {
            screenModelScope.launchNonCancellable {
                downloadManager.deleteEpisodes(episodes, state.anime, state.source)
            }
        }
    }

    fun refreshTracking() {
        screenModelScope.launchIO {
            refreshTrackers()
        }
    }

    private suspend fun refreshTrackers(
        refreshTracks: RefreshAnimeTracks = Injekt.get(),
    ) {
        refreshTracks.await(animeId)
            .filter { it.first != null }
            .forEach { (track, e) ->
                logcat(LogPriority.ERROR, e) {
                    "Failed to refresh track data animeId=$animeId for service ${track!!.id}"
                }
                withUIContext {
                    context.toast(
                        context.stringResource(
                            MR.strings.track_error,
                            track!!.name,
                            e.message ?: "",
                        ),
                    )
                }
            }
    }

    /**
     * Downloads the given list of episodes with the manager.
     * @param episodes the list of episodes to download.
     */
    private fun downloadEpisodes(
        episodes: List<Episode>,
        alt: Boolean = false,
        video: Video? = null,
    ) {
        val anime = successState?.anime ?: return
        downloadManager.downloadEpisodes(anime, episodes, true, alt, video)
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of episodes.
     * @param episodes the list of episodes to bookmark.
     */
    fun bookmarkEpisodes(episodes: List<Episode>, bookmarked: Boolean) {
        screenModelScope.launchIO {
            episodes
                .filterNot { it.bookmark == bookmarked }
                .map { EpisodeUpdate(id = it.id, bookmark = bookmarked) }
                .let { updateEpisode.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Fillermarks the given list of episodes.
     * @param episodes the list of episodes to fillermark.
     */
    fun fillermarkEpisodes(episodes: List<Episode>, fillermarked: Boolean) {
        screenModelScope.launchIO {
            episodes
                .filterNot { it.fillermark == fillermarked }
                .map { EpisodeUpdate(id = it.id, fillermark = fillermarked) }
                .let { updateEpisode.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Deletes the given list of episode.
     *
     * @param episodes the list of episodes to delete.
     */
    fun deleteEpisodes(episodes: List<Episode>) {
        screenModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    downloadManager.deleteEpisodes(
                        episodes,
                        state.anime,
                        state.source,
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun downloadNewEpisodes(episodes: List<Episode>) {
        screenModelScope.launchNonCancellable {
            val anime = successState?.anime ?: return@launchNonCancellable
            val episodesToDownload = filterEpisodesForDownload.await(anime, episodes)

            if (episodesToDownload.isNotEmpty()) {
                downloadEpisodes(episodesToDownload)
            }
        }
    }

    /**
     * Sets the seen filter and requests an UI update.
     * @param state whether to display only unseen episodes or all episodes.
     */
    fun setUnseenFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_UNSEEN
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_SEEN
        }
        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetUnseenFilter(anime, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded episodes or all episodes.
     */
    fun setDownloadedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_DOWNLOADED
        }

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetDownloadedFilter(anime, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked episodes or all episodes.
     */
    fun setBookmarkedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_BOOKMARKED
        }

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetBookmarkFilter(anime, flag)
        }
    }

    /**
     * Sets the fillermark filter and requests an UI update.
     * @param state whether to display only fillermarked episodes or all episodes.
     */
    fun setFillermarkedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_FILLERMARKED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_FILLERMARKED
        }

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetFillermarkFilter(anime, flag)
        }
    }

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetDisplayMode(anime, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetSortingModeOrFlipOrder(anime, sort)
        }
    }

    /**
     * Sets whether previews are to be shown or not.
     * @param flag to show previews.
     */
    fun showEpisodePreviews(flag: Long) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitShowEpisodePreviews(anime, flag)
        }
    }

    /**
     * Sets whether summaries are to be shown or not.
     * @param flag to show summaries.
     */
    fun showEpisodeSummaries(flag: Long) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitShowEpisodeSummaries(anime, flag)
        }
    }

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val anime = successState?.anime ?: return
        screenModelScope.launchNonCancellable {
            libraryPreferences.setEpisodeSettingsDefault(anime)
            if (applyToExisting) {
                setAnimeDefaultEpisodeFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(
                message = context.stringResource(AYMR.strings.episode_settings_updated),
            )
        }
    }

    /**
     * Sets the season download filter and requests an UI update.
     * @param state whether to display only downloaded seasons or all seasons.
     */
    fun setSeasonDownloadedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_DOWNLOADED
        }

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetDownloadedFilter(anime, flag)
        }
    }

    /**
     * Sets the season seen filter and requests an UI update.
     * @param state whether to display only unseen seasons or all seasons.
     */
    fun setSeasonUnseenFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_UNSEEN
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_SEEN
        }

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetUnseenFilter(anime, flag)
        }
    }

    /**
     * Sets the season started filter and requests an UI update.
     * @param state whether to display only started seasons or all seasons.
     */
    fun setSeasonStartedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_STARTED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_STARTED
        }

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetStartedFilter(anime, flag)
        }
    }

    /**
     * Sets the season bookmarked filter and requests an UI update.
     * @param state whether to display only bookmarked seasons or all seasons.
     */
    fun setSeasonBookmarkedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_BOOKMARKED
        }

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetBookmarkedFilter(anime, flag)
        }
    }

    /**
     * Sets the season fillermarked filter and requests an UI update.
     * @param state whether to display only fillermarked seasons or all seasons.
     */
    fun setSeasonFillermarkedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_FILLERMARKED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_FILLERMARKED
        }

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetFillermarkedFilter(anime, flag)
        }
    }

    /**
     * Sets the season completed filter and requests an UI update.
     * @param state whether to display only completed seasons or all seasons.
     */
    fun setSeasonCompletedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_COMPLETED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_COMPLETED
        }

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetCompletedFilter(anime, flag)
        }
    }

    /**
     * Sets the season sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSeasonSorting(sort: Long) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetSortingModeOrFlipOrder(anime, sort)
        }
    }

    /**
     * Sets the season grid display method and requests an UI update.
     * @param mode the display mode.
     */
    fun setSeasonDisplayGridMode(mode: SeasonDisplayMode) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetGridMode(anime, mode)
        }
    }

    /**
     * Sets the season grid size and requests an UI update.
     * @param size the size.
     */
    fun setSeasonDisplayGridSize(size: Int) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetGridSize(anime, size)
        }
    }

    /**
     * Sets the season download overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonDownloadOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetDownloadedOverlay(anime, visible)
        }
    }

    /**
     * Sets the season unseen overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonUnseenOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetUnseenOverlay(anime, visible)
        }
    }

    /**
     * Sets the season local overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonLocalOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetLocalOverlay(anime, visible)
        }
    }

    /**
     * Sets the season lang overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonLangOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetLangOverlay(anime, visible)
        }
    }

    /**
     * Sets the season continue overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonContinueOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetContinueOverlay(anime, visible)
        }
    }

    /**
     * Sets the active season display mode.
     * @param mode the mode to set.
     */
    fun setSeasonDisplayMode(mode: Long) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetDisplayMode(anime, mode)
        }
    }

    fun setSeasonCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            libraryPreferences.setSeasonSettingsDefault(anime)
            if (applyToExisting) {
                setAnimeDefaultSeasonFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(
                message = context.stringResource(AYMR.strings.season_settings_updated),
            )
        }
    }

    fun toggleSelection(
        item: EpisodeList.Item,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { successState ->
            val newEpisodes = successState.processedEpisodes.toMutableList().apply {
                val selectedIndex = successState.processedEpisodes.indexOfFirst { it.id == item.episode.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if ((selectedItem.selected && selected) || (!selectedItem.selected && !selected)) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedEpisodeIds.addOrRemove(item.id, selected)

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1..<selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1)..<selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inbetweenItem = get(it)
                            if (!inbetweenItem.selected) {
                                selectedEpisodeIds.add(inbetweenItem.id)
                                set(it, inbetweenItem.copy(selected = true))
                            }
                        }
                    }
                } else if (userSelected && !fromLongPress) {
                    if (!selected) {
                        if (selectedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (selectedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (selectedIndex < selectedPositions[0]) {
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            selectedPositions[1] = selectedIndex
                        }
                    }
                }
            }
            successState.copy(episodes = newEpisodes)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        updateSuccessState { successState ->
            val newEpisodes = successState.episodes.map {
                selectedEpisodeIds.addOrRemove(it.id, selected)
                it.copy(selected = selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(episodes = newEpisodes)
        }
    }

    fun invertSelection() {
        updateSuccessState { successState ->
            val newEpisodes = successState.episodes.map {
                selectedEpisodeIds.addOrRemove(it.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(episodes = newEpisodes)
        }
    }

    // Episodes list - end

    // Track sheet - start

    private fun observeTrackers() {
        val anime = successState?.anime ?: return

        screenModelScope.launchIO {
            combine(
                getTracks.subscribe(anime.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { animeTracks, loggedInTrackers ->
                // Show only if the service supports this manga's source
                val supportedTrackers = loggedInTrackers.filter {
                    (it as? EnhancedAnimeTracker)?.accept(source!!) ?: true
                }
                val supportedTrackerIds = supportedTrackers.map { it.id }.toHashSet()
                val supportedTrackerTracks = animeTracks.filter { it.trackerId in supportedTrackerIds }
                supportedTrackerTracks.size to supportedTrackers.isNotEmpty()
            }
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { (trackingCount, hasLoggedInTrackers) ->
                    updateSuccessState {
                        it.copy(
                            trackingCount = trackingCount,
                            hasLoggedInTrackers = hasLoggedInTrackers,
                        )
                    }
                }
        }

        screenModelScope.launchIO {
            combine(
                getTracks.subscribe(anime.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { animeTracks, loggedInTrackers ->
                loggedInTrackers
                    .map { service -> ShowsTrackItem(animeTracks.find { it.trackerId == service.id }, service) }
            }
                .distinctUntilChanged()
                .collectLatest { trackItems ->
                    updateAiringTime(anime, trackItems, manualFetch = false)
                }
        }
    }

    private suspend fun updateAiringTime(
        anime: Anime,
        trackItems: List<ShowsTrackItem>,
        manualFetch: Boolean,
    ) {
        val airingEpisodeData = AniChartApi().loadAiringTime(anime, trackItems, manualFetch)
        setAnimeViewerFlags.awaitSetNextEpisodeAiring(anime.id, airingEpisodeData)
        updateSuccessState { it.copy(nextAiringEpisode = airingEpisodeData) }
    }

    // Track sheet - end

    sealed interface Dialog {
        data class ChangeCollection(
            val anime: Anime,
            val initialSelection: ImmutableList<CheckboxState<Collection>>,
        ) : Dialog
        data class DeleteEpisodes(val episodes: List<Episode>) : Dialog
        data class DuplicateAnime(val anime: Anime, val duplicate: Anime) : Dialog
        data class Migrate(val newAnime: Anime, val oldAnime: Anime) : Dialog
        data class SetAnimeFetchInterval(val anime: Anime) : Dialog
        data class ShowQualities(val episode: Episode, val anime: Anime, val source: AnimeSource) : Dialog
        data object ChangeAnimeSkipIntro : Dialog
        data object EpisodeSettingsSheet : Dialog
        data object SeasonSettingsSheet : Dialog
        data object TrackSheet : Dialog
        data object Comments : Dialog
        data class FullImages(val coverData: Any? = null) : Dialog

        /** Find Content Provider — zero-bound canonical anime entry (17-02). */
        data object FindProvider : Dialog
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    fun showDeleteEpisodeDialog(episodes: List<Episode>) {
        updateSuccessState { it.copy(dialog = Dialog.DeleteEpisodes(episodes)) }
    }

    fun showSettingsDialog() {
        updateSuccessState {
            when (it.anime.fetchType) {
                FetchType.Seasons -> it.copy(dialog = Dialog.SeasonSettingsSheet)
                FetchType.Episodes -> it.copy(dialog = Dialog.EpisodeSettingsSheet)
            }
        }
    }

    fun showTrackDialog() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    fun showCommentsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.Comments) }
    }

    val commentsEnabled: Boolean
        get() = basePreferences.commentsEnabled().get()

    // ---------- Find Content Provider (17-02) ----------

    /** Fetch the work's carrier matrix into state (resolved canonical
     *  shows entries only). */
    private fun loadCarriers(workId: String) {
        screenModelScope.launchIO {
            val matrix = workCarriers.forWork(
                workId,
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_SHOWS,
            )
            // 19-05: the canonical episode sequence feeds the coverage
            // diff (per-row "Missing provider" + aggregate counts).
            // Non-blocking — empty when the work was never canonized.
            val canon = runCatching { episodeMap.forWork(workId) }.getOrDefault(emptyList())
            updateSuccessState { it.copy(carrierMatrix = matrix, canonicalEpisodes = canon) }
        }
    }

    /** Active collector of the finder's search flow — cancelled when a
     *  new search attaches so stale collectors can't overwrite picks. */
    private var providerSearchJob: kotlinx.coroutines.Job? = null

    /** Open the Find Provider sheet and stream per-extension search
     *  results into state. Anime only — non-anime movies/series mint
     *  canonical IDs without extension pursuit. The finder owns the
     *  search job — closing the sheet never cancels it; reopening within
     *  5 min reattaches to the cached/live flow instead of restarting. */
    fun showFindProviderDialog() {
        val workId = successState?.canonicalWorkId ?: return
        updateSuccessState {
            it.copy(
                dialog = Dialog.FindProvider,
                providerSearch = (
                    it.providerSearch
                        ?: eu.kanade.tachiyomi.data.mikodb.ProviderFinder.Search()
                    )
                    .copy(recommendations = providerRecommendations()),
            )
        }
        providerSearchJob?.cancel()
        providerSearchJob = screenModelScope.launchIO {
            val flow = providerFinder.search(
                workId,
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_SHOWS,
            )
            if (flow == null) {
                updateSuccessState { s ->
                    s.providerSearch?.let { s.copy(providerSearch = it.copy(searching = false)) } ?: s
                }
                return@launchIO
            }
            flow.collect { search ->
                updateSuccessState { s ->
                    s.copy(
                        providerSearch = search.copy(
                            confirmed = s.providerSearch?.confirmed.orEmpty(),
                            recommendations = s.providerSearch?.recommendations.orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    fun toggleProviderPick(pkgName: String) {
        updateSuccessState { s ->
            s.providerSearch?.let { p ->
                s.copy(
                    providerSearch = p.copy(
                        confirmed = if (pkgName in p.confirmed) p.confirmed - pkgName else p.confirmed + pkgName,
                    ),
                )
            } ?: s
        }
    }

    /** Mint every confirmed pick as a `user_picked` binding, then
     *  refresh carriers so the entry flips back to the comments button. */
    fun confirmProviderPicks() {
        val search = successState?.providerSearch ?: return
        val feeds = search.rows.filter { it.pkgName in search.confirmed }
            .mapNotNull { it.feed }
        val workId = successState?.canonicalWorkId ?: return
        val anime = successState?.anime
        screenModelScope.launchIO {
            providerFinder.mint(
                workId,
                feeds,
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_SHOWS,
            )
            // Confirmed carriers carry the extension's own cover art —
            // adopt the highest-scoring pick when the entry's cover is
            // blank or a non-preferred URL. Cinemeta art is preferred,
            // so this only ever fills genuinely art-less entries.
            if (anime != null) {
                feeds
                    .filter { !it.details?.thumbnailUrl.isNullOrBlank() }
                    .maxByOrNull { it.score }
                    ?.details?.thumbnailUrl
                    ?.let { cover ->
                        val stored = anime.thumbnailUrl
                        if (stored.isNullOrBlank() ||
                            !eu.kanade.tachiyomi.data.cover.HiResCoverResolver
                                .isPreferredCoverUrl(stored)
                        ) {
                            updateAnime.await(
                                tachiyomi.domain.entries.shows.model.AnimeUpdate(
                                    id = anime.id,
                                    thumbnailUrl = cover,
                                    coverLastModified = System.currentTimeMillis(),
                                ),
                            )
                            updateSuccessState {
                                it.copy(anime = animeRepository.getAnimeById(anime.id))
                            }
                        }
                    }
            }
            workCarriers.invalidate(workId)
            dismissDialog()
            updateSuccessState { it.copy(providerSearch = null) }
            loadCarriers(workId)
        }
    }

    /** Find-Providers "Restore" on a previously-removed row (20-06):
     *  clears the local carrier exclusion and retracts the remove
     *  signal; does not re-mint — the binding returns only if the
     *  server still holds it. */
    fun restoreExcludedCarrier(pkgName: String) {
        val workId = successState?.canonicalWorkId ?: return
        screenModelScope.launchIO {
            val ref = carrierExclusions.providerRefFor(workId, pkgName)
            carrierExclusions.unexclude(workId, pkgName)
            if (ref != null) {
                workIdentityApi.removeBindingSignal(workId, pkgName, ref, removed = false)
            }
            workCarriers.invalidate(workId)
            loadCarriers(workId)
        }
    }

    /** Install a recommended extension from the repo — progress feeds
     *  the sheet's install buttons. */
    fun installRecommendedProvider(pkgName: String) {
        val ext = animeExtensionManager.availableExtensionsFlow.value
            .firstOrNull { it.pkgName == pkgName } ?: return
        screenModelScope.launchIO {
            animeExtensionManager.installExtension(ext).collect { step ->
                updateSuccessState {
                    it.copy(carrierInstallSteps = it.carrierInstallSteps + (pkgName to step))
                }
            }
        }
    }

    /** Available-but-uninstalled anime extensions ranked by the surveyed
     *  S/A/B/C/D tier — the "likely to have it" list. */
    private fun providerRecommendations(): List<eu.kanade.tachiyomi.data.mikodb.ProviderFinder.RecommendedExtension> {
        val installed = animeExtensionManager.installedExtensionsFlow.value
            .map { it.pkgName }.toSet()
        return animeExtensionManager.availableExtensionsFlow.value
            .asSequence()
            .filter { it.pkgName !in installed }
            .map { ext ->
                eu.kanade.tachiyomi.data.mikodb.ProviderFinder.RecommendedExtension(
                    pkgName = ext.pkgName,
                    name = ext.name,
                    // 20-04: effective tier — the community overlay
                    // promotes/demotes on top of the manifest survey.
                    tier = providerTiers.effectiveTier(ext.pkgName),
                    iconUrl = ext.iconUrl,
                    lang = ext.lang,
                    points = providerTiers.confirmCount(ext.pkgName),
                )
            }
            .sortedWith(
                compareBy(
                    { eu.kanade.tachiyomi.data.mikodb.tierRank(it.tier) },
                    { it.name },
                ),
            )
            .take(eu.kanade.tachiyomi.data.mikodb.ProviderFinder.MAX_RECOMMENDATIONS)
            .toList()
    }

    fun showImagesDialog(coverData: Any? = null) {
        updateSuccessState { it.copy(dialog = Dialog.FullImages(coverData)) }
        loadCoverOptions()
    }

    /**
     * Assemble the cover-picker strip: per-season posters plus the
     * series/provider artwork, and the pinned marker from prefs. Runs
     * per dialog open.
     */
    private var coverOptionsLoadedAt = 0L

    private fun loadCoverOptions() {
        val state = successState ?: return
        val anime = state.anime
        screenModelScope.launchIO {
            // Reopening inside the TTL replays the assembled strip —
            // the pin pref is still re-read so a V-committed selection
            // ticks right.
            val pinPref = preferenceStore.getString(
                eu.kanade.presentation.entries.components.coverPinPrefKey(
                    eu.kanade.presentation.entries.components.COVER_PIN_KIND_ANIME,
                    anime.id,
                ),
                "",
            )
            if (state.coverOptions.isNotEmpty() &&
                System.currentTimeMillis() - coverOptionsLoadedAt < COVER_OPTIONS_TTL_MS
            ) {
                updateSuccessState {
                    it.copy(coverPinKey = pinPref.get().takeIf(String::isNotBlank))
                }
                return@launchIO
            }
            val options = mutableListOf<eu.kanade.presentation.entries.components.CoverOption>()
            state.processedSeasons.forEach { seasonItem ->
                val seasonAnime = seasonItem.seasonAnime.anime
                seasonAnime.thumbnailUrl?.takeIf(String::isNotBlank)?.let { url ->
                    val n = seasonAnime.seasonNumber
                    val label = "S. ${eu.kanade.presentation.entries.components.coverOrdinal(n)}"
                    options += eu.kanade.presentation.entries.components.CoverOption(
                        key = "season:$n",
                        label = label,
                        data = url,
                    )
                }
            }
            anime.thumbnailUrl?.takeIf(String::isNotBlank)?.let { url ->
                options += eu.kanade.presentation.entries.components.CoverOption(
                    key = "series",
                    label = "Series",
                    data = url,
                )
            }
            val pinned = pinPref.get().takeIf(String::isNotBlank)
            updateSuccessState {
                it.copy(
                    coverOptions = options.distinctBy { o -> o.data },
                    coverPinKey = pinned,
                )
            }
            coverOptionsLoadedAt = System.currentTimeMillis()
        }
    }

    fun showMigrateDialog(duplicate: Anime) {
        val anime = successState?.anime ?: return
        updateSuccessState { it.copy(dialog = Dialog.Migrate(newAnime = anime, oldAnime = duplicate)) }
    }

    fun showAnimeSkipIntroDialog() {
        updateSuccessState { it.copy(dialog = Dialog.ChangeAnimeSkipIntro) }
    }

    private fun showQualitiesDialog(episode: Episode) {
        updateSuccessState { it.copy(dialog = Dialog.ShowQualities(episode, it.anime, it.source)) }
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val anime: Anime,
            val source: AnimeSource,
            val isFromSource: Boolean,
            val episodes: List<EpisodeList.Item>,
            val seasons: List<ShowsSeasonItem>,
            val trackingCount: Int = 0,
            val hasLoggedInTrackers: Boolean = false,
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
            val hasPromptedToAddBefore: Boolean = false,
            val trackItems: List<ShowsTrackItem> = emptyList(),
            val nextAiringEpisode: Pair<Int, Long> = Pair(
                anime.nextEpisodeToAir,
                anime.nextEpisodeAiringAt,
            ),
            val accentColor: Color? = null,
            val intervalDays: Int? = null,
            val showInterval: Boolean = true,
            val suggestions: SuggestionState = SuggestionState.Idle,
            val commentsCount: Long? = null,
            /** Resolved canonical work id + worker kind (15-02/17-02);
             *  null until resolve finishes — never treat as unbound. */
            val canonicalWorkId: String? = null,
            val canonicalKind: String? = null,
            /** Server bindings ∩ installed/enabled extensions (17-02). */
            val carrierMatrix: eu.kanade.tachiyomi.data.mikodb.WorkCarriers.Matrix? = null,
            /** pkgName → install progress for recommended extensions. */
            val carrierInstallSteps: Map<String, eu.kanade.tachiyomi.extension.InstallStep> = emptyMap(),
            /** Find Content Provider sheet state (17-02). */
            val providerSearch: eu.kanade.tachiyomi.data.mikodb.ProviderFinder.Search? = null,
            /** Canonical episode sequence for the resolved work (19-05)
             *  — empty until fetched; the coverage diff simply renders
             *  nothing when canon is absent. */
            val canonicalEpisodes: List<eu.kanade.tachiyomi.data.mikodb.CanonicalEpisodeMap.CanonEpisode> =
                emptyList(),
            /** Cover-picker candidates + the pinned tile's key (null =
             *  automatic cover resolution). Populated when the images
             *  dialog opens. */
            val coverOptions: List<eu.kanade.presentation.entries.components.CoverOption> = emptyList(),
            val coverPinKey: String? = null,
        ) : State {

            val processedSeasons by lazy {
                seasons.applySeasonFilters(anime).toList()
            }

            val processedEpisodes by lazy {
                episodes.applyFilters(anime).toList()
            }

            /** Canon rows no readable bound carrier serves (19-05,
             *  D-03b). An unfetched carrier matrix or absent canon
             *  means zero flags — never a false "Missing provider". */
            val unservedCanonical by lazy {
                val matrix = carrierMatrix
                if (canonicalEpisodes.isEmpty() || matrix == null || !matrix.fetched) {
                    emptyList()
                } else {
                    eu.kanade.tachiyomi.data.mikodb.EpisodeCoverage.unserved(
                        canonicalEpisodes,
                        matrix.readable.mapTo(HashSet()) { it.pkgName },
                    )
                }
            }

            /** Unserved canonical MAIN episodes — the aggregate count
             *  shown by the missing-items header/rows. */
            val canonicalMissingCount by lazy {
                unservedCanonical.count { it.kind == "episode" }
            }

            val episodeListItems by lazy {
                // Cinemeta entries: episode numbers reset per season, so
                // gap-based missing count separators produce false positives.
                // Skip separator insertion entirely for Cinemeta entries.
                val isCinemetaEntry = anime.source == 0L && anime.url.startsWith("cinemeta:")
                // 19-05: flag rows whose canon row no readable carrier
                // serves — the card/list item renders the note slot.
                val unserved = unservedCanonical.toSet()
                val items = if (unserved.isEmpty()) {
                    processedEpisodes
                } else {
                    processedEpisodes.map { item ->
                        val ep = item.episode
                        val canonRow = canonRowFor(
                            ep.episodeType,
                            eu.kanade.tachiyomi.data.comments.ChapterBinder.parseSeason(ep.scanlator),
                            ep.episodeNumber,
                            canonicalEpisodes,
                        )
                        if (canonRow != null && canonRow in unserved) {
                            item.copy(missingProvider = true)
                        } else {
                            item
                        }
                    }
                }
                val separated = if (isCinemetaEntry) {
                    items
                } else {
                    items.insertSeparators { before, after ->
                        val (lowerEpisode, higherEpisode) = if (anime.sortDescending()) {
                            after to before
                        } else {
                            before to after
                        }
                        if (higherEpisode == null) return@insertSeparators null

                        if (lowerEpisode == null) {
                            floor(higherEpisode.episode.episodeNumber)
                                .toInt()
                                .minus(1)
                                .coerceAtLeast(0)
                        } else {
                            calculateEpisodeGap(higherEpisode.episode, lowerEpisode.episode)
                        }
                            .takeIf { it > 0 }
                            ?.let { missingCount ->
                                EpisodeList.MissingCount(
                                    id = "${lowerEpisode?.id}-${higherEpisode.id}",
                                    count = missingCount,
                                )
                            }
                    }
                }
                // Canonical coverage diff rides the same MissingCount
                // mechanism — appended once, at the list tail.
                if (canonicalMissingCount > 0) {
                    separated + EpisodeList.MissingCount(
                        id = "canon-missing",
                        count = canonicalMissingCount,
                    )
                } else {
                    separated
                }
            }

            val trackingAvailable: Boolean
                get() = trackItems.isNotEmpty()

            val airingEpisodeNumber: Double
                get() = nextAiringEpisode.first.toDouble()

            val airingTime: Long
                get() = nextAiringEpisode.second.times(1000L).minus(
                    Calendar.getInstance().timeInMillis,
                )
            val showPreviews: Boolean
                get() = anime.showPreviews()

            val showSummaries: Boolean
                get() = anime.showSummaries()

            /**
             * Applies the view filters to the list of episodes obtained from the database.
             * @return an observable of the list of episodes filtered and sorted.
             */
            private fun List<EpisodeList.Item>.applyFilters(anime: Anime): Sequence<EpisodeList.Item> {
                val isLocalAnime = anime.isLocal()
                val unseenFilter = anime.unseenFilter
                val downloadedFilter = anime.downloadedFilter
                val bookmarkedFilter = anime.bookmarkedFilter
                val fillermarkedFilter = anime.fillermarkedFilter
                return asSequence()
                    .filter { (episode) -> applyFilter(unseenFilter) { !episode.seen } }
                    .filter { (episode) -> applyFilter(bookmarkedFilter) { episode.bookmark } }
                    // 19-05: canonical filler counts as filler for
                    // filtering without mutating user-owned fillermark.
                    .filter { (episode) ->
                        applyFilter(fillermarkedFilter) {
                            episode.fillermark || episode.canonicalFiller == true
                        }
                    }
                    .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalAnime } }
                    .sortedWith { (episode1), (episode2) ->
                        getEpisodeSort(anime).invoke(
                            episode1,
                            episode2,
                        )
                    }
            }

            private fun List<ShowsSeasonItem>.applySeasonFilters(anime: Anime): Sequence<ShowsSeasonItem> {
                val unseenFilter = anime.seasonUnseenFilter
                val downloadedFilter = anime.seasonDownloadedFilter
                val startedFilter = anime.seasonStartedFilter
                val completedFilter = anime.seasonCompletedFilter
                val bookmarkedFilter = anime.seasonBookmarkedFilter
                val fillermarkedFilter = anime.seasonFillermarkedFilter

                val comparator = getSeasonSortComparator(anime)
                    .let { if (anime.seasonSortDescending()) it.reversed() else it }
                    .thenComparator(seasonSortAlphabetically)

                return asSequence()
                    .filter { (season) -> applyFilter(unseenFilter) { !season.seen } }
                    .filter { (season) -> applyFilter(startedFilter) { season.hasStarted } }
                    .filter { (season) ->
                        applyFilter(completedFilter) { season.anime.status.toInt() == SAnime.COMPLETED }
                    }
                    .filter { (season) -> applyFilter(bookmarkedFilter) { season.hasBookmarks } }
                    .filter { (season) -> applyFilter(fillermarkedFilter) { season.hasFillermarks } }
                    .filter { applyFilter(downloadedFilter) { it.downloadCount > 0 || it.seasonAnime.anime.isLocal() } }
                    .sortedWith(compareBy(comparator) { it.seasonAnime })
                    .map {
                        val itemAnime = it.seasonAnime.anime
                        ShowsSeasonItem(
                            seasonAnime = it.seasonAnime,
                            downloadCount = if (anime.seasonDownloadedOverlay) it.downloadCount else -1L,
                            unseenCount = if (anime.seasonUnseenOverlay) it.unseenCount else -1L,
                            isLocal = anime.seasonLocalOverlay && it.isLocal,
                            sourceLanguage = if (anime.seasonLangOverlay) it.sourceLanguage else "",
                            showContinueOverlay =
                            anime.seasonContinueOverlay &&
                                it.unseenCount > 0 &&
                                itemAnime.fetchType == FetchType.Episodes,
                        )
                    }
            }
        }
    }

    private companion object {
        /** How long an assembled cover-option strip stays fresh — a
         *  reopen inside the window replays it instead of re-walking
         *  seasons. */
        const val COVER_OPTIONS_TTL_MS = 5 * 60 * 1000L

        /**
         * Bind one library episode row to its canon row (19-05) — mirrors
         * `CanonicalEpisodeMap.bind`/`bindKind` semantics as a pure
         * function so the list-item diff can use it. Mains try
         * (season, epInSeason) then canonical abs; `special`/`nc` rows
         * bind inside their own keyspace. Null when nothing matches —
         * an unbound episode is never flagged unserved.
         */
        fun canonRowFor(
            episodeType: String?,
            season: Int?,
            number: Double,
            canon: List<eu.kanade.tachiyomi.data.mikodb.CanonicalEpisodeMap.CanonEpisode>,
        ): eu.kanade.tachiyomi.data.mikodb.CanonicalEpisodeMap.CanonEpisode? = when (episodeType) {
            "special", "nc" -> canon.firstOrNull {
                it.kind == episodeType &&
                    (it.number == number || it.epInSeason?.toDouble() == number)
            }
            else -> canon.firstOrNull {
                it.season == season && it.epInSeason?.toDouble() == number
            } ?: canon.firstOrNull { it.kind == "episode" && it.abs?.toDouble() == number }
        }

        /** Warm-start cache — the last [State.Success] emitted per anime,
         *  so reopening a detail screen renders instantly instead of
         *  flashing the spinner. Tiny LRU: heroes/recents only ever touch
         *  a handful of entries. */
        val lastSuccess = object : LinkedHashMap<Long, State.Success>(8, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Long, State.Success>?,
            ): Boolean = size > 8
        }
    }
}

/** MyAnimeList tracker id — same constant as ThreadIdResolver.MAL_TRACKER. */
private const val MAL_TRACKER_ID = 1L

@Immutable
sealed class EpisodeList {
    @Immutable
    data class MissingCount(
        val id: String,
        val count: Int,
    ) : EpisodeList()

    @Immutable
    data class Item(
        val episode: Episode,
        val downloadState: ShowsDownload.State,
        val downloadProgress: Int,
        val selected: Boolean = false,
        /** Canon row exists but no readable bound carrier serves it
         *  (19-05, D-03b) — row renders the "Missing provider" note. */
        val missingProvider: Boolean = false,
    ) : EpisodeList() {
        val id = episode.id
        val isDownloaded = downloadState == ShowsDownload.State.DOWNLOADED
    }
}

/**
 * Determine whether the update interval badge should be shown.
 *
 * Returns false when:
 * - The series status is COMPLETED
 * - The most recent episode upload date is more than 2 months ago (stale)
 */
private fun shouldShowInterval(status: Long, episodes: List<*>): Boolean {
    if (status == eu.kanade.tachiyomi.animesource.model.SAnime.COMPLETED.toLong()) return false

    val twoMonthsMs = 60L * 24L * 60L * 60L * 1000L
    val now = System.currentTimeMillis()
    val latestDate = episodes.maxOfOrNull { item ->
        when (item) {
            is Episode -> maxOf(item.dateUpload, item.dateFetch)
            else -> 0L
        }
    } ?: return false

    if (latestDate == 0L) return true
    return (now - latestDate) < twoMonthsMs
}
