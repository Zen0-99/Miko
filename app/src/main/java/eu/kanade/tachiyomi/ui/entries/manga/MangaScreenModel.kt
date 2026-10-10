package eu.kanade.tachiyomi.ui.entries.manga

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.domain.entries.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.manga.model.chaptersFiltered
import eu.kanade.domain.entries.manga.model.downloadedFilter
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.domain.items.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.domain.track.manga.interactor.RefreshMangaTracks
import eu.kanade.domain.track.manga.interactor.TrackChapter
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.mikodb.WorkCarriers.ChapterCarrierState
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.suggestions.SuggestionCache
import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionSourceWeight
import eu.kanade.tachiyomi.data.suggestions.SuggestionState
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.manga.MangaFallbackOutcome
import eu.kanade.tachiyomi.data.suggestions.manga.MangaSearchFallbackEngine
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.data.suggestions.util.bestMatchScoreFor
import eu.kanade.tachiyomi.data.suggestions.util.dedupeByCleanTitle
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.system.maybeShowDnsToast
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.items.chapter.interactor.FilterChaptersForDownload
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.collection.manga.interactor.SetMangaCollections
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.entries.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.chapter.model.NoChaptersException
import tachiyomi.domain.items.chapter.service.getChapterSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.entries.manga.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaScreenModel(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val mangaId: Long,
    private val isFromSource: Boolean,
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val basePreferences: eu.kanade.domain.base.BasePreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    readerPreferences: ReaderPreferences = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val trackChapter: TrackChapter = Injekt.get(),
    private val downloadManager: MangaDownloadManager = Injekt.get(),
    private val downloadCache: MangaDownloadCache = Injekt.get(),
    private val getMangaAndChapters: GetMangaWithChapters = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getAvailableScanlators: GetAvailableScanlators = Injekt.get(),
    private val getExcludedScanlators: GetExcludedScanlators = Injekt.get(),
    private val setExcludedScanlators: SetExcludedScanlators = Injekt.get(),
    private val setMangaChapterFlags: SetMangaChapterFlags = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val getCollections: GetMangaCollections = Injekt.get(),
    private val getTracks: GetMangaTracks = Injekt.get(),
    private val threadIdResolver: eu.kanade.tachiyomi.data.comments.ThreadIdResolver = Injekt.get(),
    private val commentsApi: eu.kanade.tachiyomi.data.comments.CommentsApi = Injekt.get(),
    private val addTracks: AddMangaTracks = Injekt.get(),
    private val setMangaCollections: SetMangaCollections = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val filterChaptersForDownload: FilterChaptersForDownload = Injekt.get(),
    private val fetchInterval: MangaFetchInterval = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val suggestionCoordinator: SuggestionCoordinator = Injekt.get(),
    private val searchFallbackEngine: MangaSearchFallbackEngine = Injekt.get(),
    private val canonicalEntryUnifier: eu.kanade.tachiyomi.data.mikodb.CanonicalEntryUnifier = Injekt.get(),
    private val libraryCanonizer: eu.kanade.tachiyomi.data.mikodb.LibraryCanonizer = Injekt.get(),
    private val canonicalPipelineRunner: eu.kanade.tachiyomi.data.mikodb.CanonicalPipelineRunner = Injekt.get(),
    private val workCarriers: eu.kanade.tachiyomi.data.mikodb.WorkCarriers = Injekt.get(),
    private val workIdentityApi: eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi = Injekt.get(),
    private val canonicalChapterRouter: eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter = Injekt.get(),
    private val mangaExtensionManager: eu.kanade.tachiyomi.extension.manga.MangaExtensionManager = Injekt.get(),
    private val providerFinder: eu.kanade.tachiyomi.data.mikodb.ProviderFinder = Injekt.get(),
    private val mikoDbManager: eu.kanade.tachiyomi.data.mikodb.MikoDbManager = Injekt.get(),
    private val providerTiers: eu.kanade.tachiyomi.data.mikodb.ProviderTiers = Injekt.get(),
    private val carrierExclusions: eu.kanade.tachiyomi.data.mikodb.CarrierExclusions = Injekt.get(),
    private val canonicalVolumes: eu.kanade.tachiyomi.data.mikodb.CanonicalVolumes = Injekt.get(),
    private val bridge: eu.kanade.tachiyomi.data.mikodb.MangaNovelIdBridge = Injekt.get(),
    private val preferenceStore: tachiyomi.core.common.preference.PreferenceStore = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<MangaScreenModel.State>(State.Loading) {

    /**
     * Set when this entry resolves to a materialized canonical `miko:`
     * row — the screen replaces itself with that entry so there's one
     * entry per real work instead of a duplicate per extension.
     */
    val redirectToEntryId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    private val successState: State.Success?
        get() = state.value as? State.Success

    val manga: Manga?
        get() = successState?.manga

    val source: MangaSource?
        get() = successState?.source

    private val isFavorited: Boolean
        get() = manga?.favorite ?: false

    private val allChapters: List<ChapterList.Item>?
        get() = successState?.chapters

    private val filteredChapters: List<ChapterList.Item>?
        get() = successState?.processedChapters

    val chapterSwipeStartAction = libraryPreferences.swipeChapterEndAction().get()
    val chapterSwipeEndAction = libraryPreferences.swipeChapterStartAction().get()
    var autoTrackState = trackPreferences.autoUpdateTrackOnMarkRead().get()

    private val skipFiltered by readerPreferences.skipFiltered().asState(screenModelScope)

    val isUpdateIntervalEnabled =
        LibraryPreferences.ENTRY_OUTSIDE_RELEASE_PERIOD in libraryPreferences.autoUpdateItemRestrictions().get()

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list
    private val selectedChapterIds: HashSet<Long> = HashSet()

    internal var isFromChangeCollection: Boolean = false

    internal val autoOpenTrack: Boolean
        get() = successState?.trackingAvailable == true && trackPreferences.trackOnAddingToLibrary().get()

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    /** Completed by the screen once the nav transition lands. Heavy
     *  secondary work — canonical pipelines, carrier loads, source
     *  fetches, suggestions, comment counts, hi-res cover upgrades —
     *  awaits this so no DB merge or network write contends with the
     *  shared-element flight (the Nuvio `shouldLoadSecondaryData`
     *  pattern: secondary data doesn't start until interactions end).
     *  Screens without a nav scope settle on their first frame, so
     *  tabs/deep links are unaffected. */
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
        val seed = buildSuggestionSeed(success.manga)
        SuggestionCache.invalidateForSeed(seed, success.manga.url)
        loadSuggestions(
            seed,
            manga = success.manga,
            source = success.manga.toCatalogueSource(),
            force = true,
        )
    }

    private fun buildSuggestionSeed(manga: Manga): SuggestionSeed {
        val title = manga.title
        val candidates = SuggestionTitleResolver.resolveCandidates(
            title = title,
            description = manga.description,
            url = manga.url,
        )
        return SuggestionSeed(
            mediaType = SuggestionMediaType.MANGA,
            primaryTitle = title,
            candidateTitles = candidates,
            description = manga.description,
            author = manga.author,
            genres = manga.genre,
        )
    }

    private fun Manga.toCatalogueSource(): CatalogueSource? =
        Injekt.get<MangaSourceManager>().getOrStub(source) as? CatalogueSource

    private fun emitProgressiveSuggestions(list: List<SuggestionItem>, currentManga: Manga?) {
        val seed = suggestionSeedUsed ?: return
        val sorted = synchronized(list) {
            list.dedupeByCleanTitle(seed)
                .filter { item ->
                    val isSelf = SuggestionTitleResolver.isSameProviderEntry(item, currentManga?.url)
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
        manga: Manga? = null,
        source: CatalogueSource? = null,
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

        val currentManga = manga ?: successState?.manga
        val currentSource = source ?: (
            currentManga?.let {
                Injekt.get<MangaSourceManager>().getOrStub(it.source)
            } as? CatalogueSource
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
                                    val isSelf = SuggestionTitleResolver.isSameProviderEntry(item, currentManga?.url)
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
                                    emitProgressiveSuggestions(suggestionsList, currentManga)
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logcat(LogPriority.DEBUG) { "[MangaScreenModel] External suggestions failed: ${e.message}" }
                        }
                    }

                    // Task 2: Search Fallback suggestions
                    if (currentManga != null && currentSource != null) {
                        launch {
                            try {
                                val outcome = searchFallbackEngine.fetchSearchFallback(
                                    manga = currentManga,
                                    source = currentSource,
                                    seed = seed,
                                    maxResults = 40,
                                    onProgress = { progressItems ->
                                        synchronized(suggestionsList) {
                                            val existingUrls = suggestionsList.map { it.providerUrl }.toSet()
                                            val newItems = progressItems.filter { it.providerUrl !in existingUrls }
                                            suggestionsList.addAll(newItems)
                                        }
                                        emitProgressiveSuggestions(suggestionsList, currentManga)
                                    },
                                )
                                if (outcome is MangaFallbackOutcome.Success && outcome.items.isNotEmpty()) {
                                    synchronized(suggestionsList) {
                                        val existingUrls = suggestionsList.map { it.providerUrl }.toSet()
                                        val newItems = outcome.items.filter { it.providerUrl !in existingUrls }
                                        suggestionsList.addAll(newItems)
                                    }
                                    emitProgressiveSuggestions(suggestionsList, currentManga)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                logcat(LogPriority.DEBUG) {
                                    "[MangaScreenModel] Native search fallback failed: ${e.message}"
                                }
                            }
                        }
                    }
                }

                val finalCombined = synchronized(suggestionsList) {
                    suggestionsList.dedupeByCleanTitle(seed)
                        .filter { item ->
                            val isSelf = SuggestionTitleResolver.isSameProviderEntry(item, currentManga?.url)
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
                logcat(LogPriority.DEBUG) { "MangaScreenModel suggestions fetch failed: ${e.message}" }
                updateSuccessState { it.copy(suggestions = SuggestionState.Error(e.message ?: "Unknown error")) }
            }
        }
    }

    init {
        // Warm-start: reopening an entry renders the last Success instantly
        // instead of flashing the spinner — the flows below overwrite every
        // field within a frame anyway. Cleared dialogs/refresh flags.
        lastSuccess[mangaId]?.let {
            mutableState.value = it.copy(dialog = null, isRefreshingData = false)
        }
        screenModelScope.launch {
            state.collect { s ->
                if (s is State.Success) {
                    synchronized(lastSuccess) { lastSuccess[s.manga.id] = s.copy(dialog = null) }
                }
            }
        }

        screenModelScope.launchIO {
            combine(
                getMangaAndChapters.subscribe(mangaId, applyScanlatorFilter = true).distinctUntilChanged(),
                downloadCache.changes,
                downloadManager.queueState,
            ) { mangaAndChapters, _, _ -> mangaAndChapters }
                .flowWithLifecycle(lifecycle)
                .collectLatest { (manga, chapters) ->
                    val items = chapters.toChapterListItems(manga)
                    updateSuccessState {
                        it.copy(
                            manga = manga,
                            chapters = items,
                        )
                    }
                }
        }

        screenModelScope.launchIO {
            getExcludedScanlators.subscribe(mangaId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { excludedScanlators ->
                    updateSuccessState {
                        it.copy(excludedScanlators = excludedScanlators)
                    }
                }
        }

        screenModelScope.launchIO {
            getAvailableScanlators.subscribe(mangaId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { availableScanlators ->
                    updateSuccessState {
                        it.copy(availableScanlators = availableScanlators)
                    }
                }
        }

        observeDownloads()

        screenModelScope.launchIO {
            val manga = getMangaAndChapters.awaitManga(mangaId)

            // Unify extension-source entries with a materialized canonical
            // `miko:` row (MikoDB identity). Redirect instead of loading
            // this duplicate — one entry per real work.
            val canonical = try {
                canonicalEntryUnifier.resolve(manga)
            } catch (e: Exception) {
                null
            }
            if (canonical != null && canonical.id != mangaId) {
                logcat(LogPriority.DEBUG) {
                    "MangaScreenModel: redirecting '${manga.title}' (id=$mangaId, " +
                        "source=${manga.source}) to canonical entry id=${canonical.id}"
                }
                redirectToEntryId.value = canonical.id
                return@launchIO
            }

            // Extension-source favorites never unify (read state lives on
            // this row) — kick the canonizer: resolve → materialize →
            // canonize → move the favorite onto the canonical row. On
            // success this screen swaps to the canonical entry whose
            // chapters come from the carrier matrix, not one source.
            if (manga.favorite &&
                manga.source != eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.CANONICAL_SOURCE_ID &&
                !eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.isCanonicalUrl(manga.url)
            ) {
                // Veil first, then mint — Success may not exist yet, so
                // wait for it before flagging. The redirect lands when
                // the mint resolves.
                screenModelScope.launchIO {
                    state.first { it is State.Success }
                    transitionSettled.await()
                    updateSuccessState { it.copy(isMinting = true) }
                    val c = runCatching { libraryCanonizer.migrateManga(manga, heavy = true) }
                        .onFailure {
                            logcat(LogPriority.WARN, it as? Exception ?: Exception(it)) {
                                "init mint failed for '${manga.title}'"
                            }
                        }
                        .getOrNull()
                    updateSuccessState { it.copy(isMinting = false) }
                    if (c != null) redirectToEntryId.value = c.id
                }
            }

            // Independent DB reads run in parallel — first-open latency is
            // dominated by these round-trips back to back.
            val chaptersDeferred = async {
                getMangaAndChapters.awaitChapters(mangaId, applyScanlatorFilter = true)
                    .toChapterListItems(manga)
            }
            val flagsDeferred = async {
                // Seed library defaults only on untouched rows — a
                // non-favorite canonical entry keeps the user's sort/
                // filter choices across visits.
                if (!manga.favorite && manga.chapterFlags == 0L) {
                    setMangaDefaultChapterFlags.await(manga)
                }
            }
            val sourceDeferred = async { Injekt.get<MangaSourceManager>().getOrStub(manga.source) }
            val availableScanlatorsDeferred = async { getAvailableScanlators.await(mangaId) }
            val excludedScanlatorsDeferred = async { getExcludedScanlators.await(mangaId) }

            val chapters = chaptersDeferred.await()
            flagsDeferred.await()

            // Canonical rows have no backing extension — details/chapters
            // come from the canonical chapter pipeline (12-04), not the
            // stub source.
            val isCanonical = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.isCanonicalUrl(manga.url)
            val needRefreshInfo = !manga.initialized && !isCanonical
            val needRefreshChapter = chapters.isEmpty() && !isCanonical

            // Seed the accent synchronously — this update REPLACES the
            // warm-started Success, so a default `accentColor = null`
            // would erase the extracted color and the composable's
            // LaunchedEffect never refires (its key didn't change).
            // Keyed to the current cover so a changed cover can't seed
            // a stale accent.
            val isDark =
                (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            val cachedAccentColor = eu.kanade.tachiyomi.util.EntryCoverMetadata.getBaseColor(
                eu.kanade.tachiyomi.util.EntryCoverMetadata.EntryType.MANGA,
                manga.id,
                eu.kanade.presentation.entries.components.coverKeyOf(manga),
            )?.let {
                Color(eu.kanade.presentation.entries.components.adjustForTheme(it, isDark))
            }

            // Show what we have earlier
            val intervalDays = fetchInterval.calculateInterval(
                chapters.map { it.chapter },
                kotlinx.datetime.TimeZone.currentSystemDefault(),
            )
            // Hide interval badge if series is completed or last update is
            // older than 2 months (stale schedule).
            val showInterval = shouldShowInterval(manga.status, chapters.map { it.chapter })
            mutableState.update { prev ->
                val p = prev as? State.Success
                State.Success(
                    manga = manga,
                    source = sourceDeferred.await(),
                    isFromSource = isFromSource,
                    chapters = chapters,
                    availableScanlators = availableScanlatorsDeferred.await(),
                    excludedScanlators = excludedScanlatorsDeferred.await(),
                    isRefreshingData = needRefreshInfo || needRefreshChapter,
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
                    suggestions = p?.suggestions ?: SuggestionState.Idle,
                    commentsCount = p?.commentsCount,
                    carrierMatrix = p?.carrierMatrix,
                    preferredCarrier = p?.preferredCarrier,
                    chapterCarriers = p?.chapterCarriers ?: emptyMap(),
                )
            }

            // Everything below is secondary — holds until the nav
            // transition lands (see transitionSettled).
            transitionSettled.await()

            // Start observe tracking since it only needs mangaId
            observeTrackers()

            // "View comments (N)" badge — resolves the work thread once
            // and counts its visible comments. Silent on failure.
            if (commentsEnabled) {
                screenModelScope.launchIO {
                    transitionSettled.await()
                    val threadId = threadIdResolver.forManga(manga) ?: return@launchIO
                    val result = commentsApi.threadCounts(listOf(threadId))
                    if (result is eu.kanade.tachiyomi.data.comments.CommentsApi.Result.Ok) {
                        updateSuccessState {
                            it.copy(commentsCount = result.value.counts[threadId] ?: 0L)
                        }
                    }
                }
            }

            // Canonical rows collect+canonize chapter feeds in the
            // background (13-02): cached sequence serves immediately,
            // a stale/absent one recanonizes off the render path.
            // The merged sequence is then materialized as chapter rows
            // (13-03) so the unified list renders through the normal
            // chapter pipeline.
            if (isCanonical) {
                eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
                    .workIdFromMangaUrl(manga.url)
                    ?.let { workId ->
                        canonicalPipelineRunner.launchManga(workId, manga)
                        loadCarriers(workId)
                        loadVolumes(workId)
                    }
            }

            // Upgrade the cover to hi-res artwork (AniList -> MangaDex -> Jikan)
            // for the full-bleed enhanced hero; no-op if already hi-res,
            // custom, or checked recently.
            screenModelScope.launchIO {
                runCatching {
                    Injekt.get<eu.kanade.tachiyomi.data.cover.HiResCoverResolver>()
                        .upgradeMangaCover(manga)
                }
            }

            // Fetch suggestions asynchronously
            loadSuggestions(buildSuggestionSeed(manga))

            // Fetch info-chapters when needed
            if (screenModelScope.isActive) {
                val fetchFromSourceTasks = listOf(
                    async { if (needRefreshInfo) fetchMangaFromSource() },
                    async { if (needRefreshChapter) fetchChaptersFromSource() },
                )
                fetchFromSourceTasks.awaitAll()
            }

            // Initial loading finished
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        // Manual refresh on an extension-linked favorite is also a
        // migration retry — canonize may have failed earlier (empty
        // feed, offline, no carriers) and init only kicks it once.
        val mintCandidate = successState?.manga
            ?.takeIf {
                manualFetch &&
                    it.favorite &&
                    it.source != eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.CANONICAL_SOURCE_ID &&
                    !eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.isCanonicalUrl(it.url)
            }
        if (mintCandidate != null) {
            updateSuccessState { it.copy(isMinting = true) }
            screenModelScope.launchIO {
                val canonical = runCatching {
                    libraryCanonizer.migrateManga(mintCandidate, heavy = true)
                }.onFailure {
                    logcat(LogPriority.WARN, it as? Exception ?: Exception(it)) {
                        "refresh mint failed for '${mintCandidate.title}'"
                    }
                }.getOrNull()
                updateSuccessState { it.copy(isMinting = false, isRefreshingData = false) }
                if (canonical != null) {
                    redirectToEntryId.value = canonical.id
                } else if (successState?.source is StubMangaSource) {
                    // Mint couldn't take over — run the deferred fetch so
                    // the dead-source failure still surfaces honestly.
                    fetchAllFromSourceBody(manualFetch)
                }
            }
        }
        // A stub source's fetch is guaranteed to throw — while a mint is
        // in flight the mint IS the refresh; the fetch only runs if the
        // mint fails. Live sources refresh in parallel with the mint.
        if (mintCandidate != null && successState?.source is StubMangaSource) return
        screenModelScope.launch { fetchAllFromSourceBody(manualFetch) }
    }

    private suspend fun fetchAllFromSourceBody(manualFetch: Boolean) {
        updateSuccessState { it.copy(isRefreshingData = true) }
        // Canonical rows have no extension source to refresh —
        // pull-to-refresh re-runs enrich + canonize + sync (force:
        // refresh is the explicit "retry now" action).
        val canonicalWorkId = successState?.manga?.url
            ?.let { eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.workIdFromMangaUrl(it) }
        if (canonicalWorkId != null &&
            successState?.manga?.source ==
            eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.CANONICAL_SOURCE_ID
        ) {
            successState?.manga?.let { m ->
                workCarriers.invalidate(canonicalWorkId)
                canonicalPipelineRunner.launchManga(canonicalWorkId, m, force = true)
            }
        } else {
            kotlinx.coroutines.coroutineScope {
                val fetchFromSourceTasks = listOf(
                    async { fetchMangaFromSource(manualFetch) },
                    async { fetchChaptersFromSource(manualFetch) },
                )
                fetchFromSourceTasks.awaitAll()
            }
        }
        updateSuccessState { it.copy(isRefreshingData = false) }
    }

    // Manga info - start

    /**
     * Fetch manga information from source.
     */
    private suspend fun fetchMangaFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        // A stub source can only throw — skip the guaranteed-failure
        // call on auto-open (the mint pass is what actually matters for
        // dead-source entries); keep it on manual refresh so the user
        // still sees why nothing fetched.
        if (state.source is StubMangaSource && !manualFetch) return
        try {
            withIOContext {
                val networkManga = state.source.getMangaDetails(state.manga.toSManga())
                updateManga.awaitUpdateFromSource(state.manga, networkManga, manualFetch)
            }
        } catch (e: Throwable) {
            // Ignore early hints "errors" that aren't handled by OkHttp
            if (e is HttpException && e.code == 103) return

            logcat(LogPriority.ERROR, e)
            context.maybeShowDnsToast(e, state.manga.thumbnailUrl)
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
                        message = context.stringResource(MR.strings.delete_downloads_for_manga),
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
     * Update favorite status of manga, (removes / adds) manga (to / from) library.
     */
    fun toggleFavorite(
        onRemoved: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        screenModelScope.launchIO {
            val manga = state.manga

            if (isFavorited) {
                // Remove from library
                if (updateManga.awaitUpdateFavorite(manga.id, false)) {
                    // Remove covers and update last modified in db
                    if (manga.removeCovers() != manga) {
                        updateManga.awaitUpdateCoverLastModified(manga.id)
                    }
                    withUIContext { onRemoved() }
                }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (checkDuplicate) {
                    val duplicate = getDuplicateLibraryManga.await(manga).getOrNull(0)

                    if (duplicate != null) {
                        updateSuccessState {
                            it.copy(
                                dialog = Dialog.DuplicateManga(manga, duplicate),
                            )
                        }
                        return@launchIO
                    }
                }

                // Now check if user previously set collections, when available
                val collections = getCollections()
                val defaultCollectionId = libraryPreferences.defaultMangaCollection().get().toLong()
                val defaultCollection = collections.find { it.id == defaultCollectionId }
                when {
                    // Default collection set
                    defaultCollection != null -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCollection(defaultCollection)
                    }

                    // Automatic 'Default' or no collections
                    defaultCollectionId == 0L || collections.isEmpty() -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCollection(null)
                    }

                    // Choose a collection
                    else -> {
                        isFromChangeCollection = true
                        showChangeCollectionDialog()
                    }
                }

                // Finally match with enhanced tracking when available
                addTracks.bindEnhancedTrackers(manga, state.source)
                if (autoOpenTrack) {
                    showTrackDialog()
                }
            }
        }
    }

    fun showChangeCollectionDialog() {
        val manga = successState?.manga ?: return
        screenModelScope.launch {
            val collections = getCollections()
            val selection = getMangaCollectionIds(manga)
            updateSuccessState { successState ->
                successState.copy(
                    dialog = Dialog.ChangeCollection(
                        manga = manga,
                        initialSelection = collections.mapAsCheckboxState { it.id in selection }.toImmutableList(),
                    ),
                )
            }
        }
    }

    fun showSetMangaFetchIntervalDialog() {
        val manga = successState?.manga ?: return
        updateSuccessState {
            it.copy(dialog = Dialog.SetMangaFetchInterval(manga))
        }
    }

    fun setFetchInterval(manga: Manga, interval: Int) {
        screenModelScope.launchIO {
            if (
                updateManga.awaitUpdateFetchInterval(
                    // Custom intervals are negative
                    manga.copy(fetchInterval = -interval),
                )
            ) {
                val updatedManga = mangaRepository.getMangaById(manga.id)
                updateSuccessState { it.copy(manga = updatedManga) }
            }
        }
    }

    /**
     * Returns true if the manga has any downloads.
     */
    private fun hasDownloads(): Boolean {
        val manga = successState?.manga ?: return false
        return downloadManager.getDownloadCount(manga) > 0
    }

    /**
     * Deletes all the downloads for the manga.
     */
    private fun deleteDownloads() {
        val state = successState ?: return
        downloadManager.deleteManga(state.manga, state.source)
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
     * Gets the collection id's the manga is in, if the manga is not in a collection, returns the default id.
     *
     * @param manga the manga to get collections from.
     * @return Array of collection ids the manga is in, if none returns default id
     */
    private suspend fun getMangaCollectionIds(manga: Manga): List<Long> {
        return getCollections.await(manga.id)
            .map { it.id }
    }

    fun moveMangaToCollectionsAndAddToLibrary(manga: Manga, collections: List<Long>) {
        moveMangaToCollection(collections)
        if (manga.favorite) return

        screenModelScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id, true)
        }
    }

    /**
     * Move the given manga to collections.
     *
     * @param collections the selected collections.
     */
    private fun moveMangaToCollections(collections: List<Collection>) {
        val collectionIds = collections.map { it.id }
        moveMangaToCollection(collectionIds)
    }

    private fun moveMangaToCollection(collectionIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCollections.await(mangaId, collectionIds)
        }
    }

    /**
     * Move the given manga to the collection.
     *
     * @param collection the selected collection, or null for default collection.
     */
    private fun moveMangaToCollection(collection: Collection?) {
        moveMangaToCollections(listOfNotNull(collection))
    }

    // Manga info - end

    // Chapters list - start

    private fun observeDownloads() {
        screenModelScope.launchIO {
            downloadManager.statusFlow()
                .filter { it.manga.id == successState?.manga?.id }
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
                .filter { it.manga.id == successState?.manga?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }
    }

    private fun updateDownloadState(download: MangaDownload) {
        updateSuccessState { successState ->
            val modifiedIndex = successState.chapters.indexOfFirst { it.id == download.chapter.id }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newChapters = successState.chapters.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    .copy(downloadState = download.status, downloadProgress = download.progress)
                add(modifiedIndex, item)
            }
            successState.copy(chapters = newChapters)
        }
    }

    private suspend fun List<Chapter>.toChapterListItems(manga: Manga): List<ChapterList.Item> {
        val isLocal = manga.isLocal()
        // Canon rows keep downloads under the serving carrier's dir —
        // "downloaded" = exists under ANY bound carrier.
        val isCanonical = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.isCanonicalUrl(manga.url)
        val carrierSourceIds = if (isCanonical) {
            eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.workIdFromMangaUrl(manga.url)
                ?.let { workId ->
                    runCatching {
                        workCarriers.forWork(
                            workId,
                            eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_MANGA,
                        ).carriers
                    }.getOrNull()
                }
                ?.flatMap { it.sourceIds }
                .orEmpty()
        } else {
            emptyList()
        }
        return map { chapter ->
            val activeDownload = if (isLocal) {
                null
            } else {
                downloadManager.getQueuedDownloadOrNull(chapter.id)
            }
            val downloaded = when {
                isLocal -> true
                isCanonical -> carrierSourceIds.any {
                    downloadManager.isChapterDownloaded(
                        chapter.name,
                        chapter.scanlator,
                        manga.title,
                        it,
                    )
                }
                else -> downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    manga.title,
                    manga.source,
                )
            }
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> MangaDownload.State.DOWNLOADED
                else -> MangaDownload.State.NOT_DOWNLOADED
            }

            ChapterList.Item(
                chapter = chapter,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                selected = chapter.id in selectedChapterIds,
            )
        }
    }

    /**
     * Requests an updated list of chapters from the source.
     */
    private suspend fun fetchChaptersFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        if (state.source is StubMangaSource && !manualFetch) return
        try {
            withIOContext {
                val chapters = state.source.getChapterList(state.manga.toSManga())

                val newChapters = syncChaptersWithSource.await(
                    chapters,
                    state.manga,
                    state.source,
                    manualFetch,
                )

                if (manualFetch) {
                    downloadNewChapters(newChapters)
                }
            }
        } catch (e: Throwable) {
            val message = if (e is NoChaptersException) {
                context.stringResource(MR.strings.no_chapters_error)
            } else {
                logcat(LogPriority.ERROR, e)
                with(context) { e.formattedMessage }
            }

            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
            val newManga = mangaRepository.getMangaById(mangaId)
            updateSuccessState { it.copy(manga = newManga, isRefreshingData = false) }
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    fun chapterSwipe(chapterItem: ChapterList.Item, swipeAction: LibraryPreferences.ChapterSwipeAction) {
        screenModelScope.launch {
            executeChapterSwipeAction(chapterItem, swipeAction)
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    private fun executeChapterSwipeAction(
        chapterItem: ChapterList.Item,
        swipeAction: LibraryPreferences.ChapterSwipeAction,
    ) {
        val chapter = chapterItem.chapter
        when (swipeAction) {
            LibraryPreferences.ChapterSwipeAction.ToggleRead -> {
                markChaptersRead(listOf(chapter), !chapter.read)
            }
            LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> {
                bookmarkChapters(listOf(chapter), !chapter.bookmark)
            }
            LibraryPreferences.ChapterSwipeAction.Download -> {
                val downloadAction: ChapterDownloadAction = when (chapterItem.downloadState) {
                    MangaDownload.State.ERROR,
                    MangaDownload.State.NOT_DOWNLOADED,
                    -> ChapterDownloadAction.START_NOW
                    MangaDownload.State.QUEUE,
                    MangaDownload.State.DOWNLOADING,
                    -> ChapterDownloadAction.CANCEL
                    MangaDownload.State.DOWNLOADED -> ChapterDownloadAction.DELETE
                }
                runChapterDownloadActions(
                    items = listOf(chapterItem),
                    action = downloadAction,
                )
            }
            LibraryPreferences.ChapterSwipeAction.Disabled -> throw IllegalStateException()
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): Chapter? {
        val successState = successState ?: return null
        return successState.chapters.getNextUnread(successState.manga)
    }

    private fun getUnreadChapters(): List<Chapter> {
        val chapterItems = if (skipFiltered) filteredChapters.orEmpty() else allChapters.orEmpty()
        return chapterItems
            .filter { (chapter, dlStatus) -> !chapter.read && dlStatus == MangaDownload.State.NOT_DOWNLOADED }
            .map { it.chapter }
    }

    private fun getUnreadChaptersSorted(): List<Chapter> {
        val manga = successState?.manga ?: return emptyList()
        val chaptersSorted = getUnreadChapters().sortedWith(getChapterSort(manga))
        return if (manga.sortDescending()) chaptersSorted.reversed() else chaptersSorted
    }

    private fun startDownload(
        chapters: List<Chapter>,
        startNow: Boolean,
        forcedCarrierPkg: String? = null,
    ) {
        val successState = successState ?: return

        screenModelScope.launchNonCancellable {
            if (startNow) {
                val chapterId = chapters.singleOrNull()?.id ?: return@launchNonCancellable
                if (forcedCarrierPkg != null) {
                    // A carrier-picked download supersedes anything
                    // already queued for the chapter.
                    downloadManager.getQueuedDownloadOrNull(chapterId)
                        ?.let { downloadManager.cancelQueuedDownloads(listOf(it)) }
                    downloadChapters(chapters, forcedCarrierPkg)
                }
                downloadManager.startDownloadNow(chapterId)
            } else {
                downloadChapters(chapters, forcedCarrierPkg)
            }

            if (!isFavorited && !successState.hasPromptedToAddBefore) {
                updateSuccessState { state ->
                    state.copy(hasPromptedToAddBefore = true)
                }
                val result = snackbarHostState.showSnackbar(
                    message = context.stringResource(AYMR.strings.snack_add_to_manga_library),
                    actionLabel = context.stringResource(MR.strings.action_add),
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed && !isFavorited) {
                    toggleFavorite()
                }
            }
        }
    }

    fun runChapterDownloadActions(
        items: List<ChapterList.Item>,
        action: ChapterDownloadAction,
        forcedCarrierPkg: String? = null,
    ) {
        when (action) {
            ChapterDownloadAction.START -> {
                startDownload(items.map { it.chapter }, false, forcedCarrierPkg)
                if (items.any { it.downloadState == MangaDownload.State.ERROR }) {
                    downloadManager.startDownloads()
                }
            }
            ChapterDownloadAction.START_NOW -> {
                val chapter = items.singleOrNull()?.chapter ?: return
                startDownload(listOf(chapter), true, forcedCarrierPkg)
            }
            ChapterDownloadAction.CANCEL -> {
                val chapterId = items.singleOrNull()?.id ?: return
                cancelDownload(chapterId)
            }
            ChapterDownloadAction.DELETE -> {
                deleteChapters(items.map { it.chapter })
            }
        }
    }

    fun runDownloadAction(action: DownloadAction) {
        val chaptersToDownload = when (action) {
            DownloadAction.NEXT_1_ITEM -> getUnreadChaptersSorted().take(1)
            DownloadAction.NEXT_5_ITEMS -> getUnreadChaptersSorted().take(5)
            DownloadAction.NEXT_10_ITEMS -> getUnreadChaptersSorted().take(10)
            DownloadAction.NEXT_25_ITEMS -> getUnreadChaptersSorted().take(25)

            DownloadAction.UNVIEWED_ITEMS -> getUnreadChapters()
        }
        if (chaptersToDownload.isNotEmpty()) {
            startDownload(chaptersToDownload, false)
        }
    }

    private fun cancelDownload(chapterId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(chapterId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = MangaDownload.State.NOT_DOWNLOADED })
    }

    fun markPreviousChapterRead(pointer: Chapter, read: Boolean = true) {
        val manga = successState?.manga ?: return
        val chapters = filteredChapters.orEmpty().map { it.chapter }
        // Chapter-group pills (Volume/Book/Season…): scope "previous" to
        // the pointer's own group — marking must not leak across groups.
        val pointerGroup = eu.kanade.tachiyomi.util.chapter.ChapterNameGroup.parse(pointer.name)
        val scoped = if (pointerGroup != null) {
            chapters.filter {
                eu.kanade.tachiyomi.util.chapter.ChapterNameGroup.parse(it.name) == pointerGroup
            }
        } else {
            chapters
        }
        val prevChapters = if (manga.sortDescending()) scoped.asReversed() else scoped
        val pointerPos = prevChapters.indexOf(pointer)
        if (pointerPos != -1) markChaptersRead(prevChapters.take(pointerPos), read)
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param chapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(chapters: List<Chapter>, read: Boolean) {
        toggleAllSelection(false)
        if (chapters.isEmpty()) return
        screenModelScope.launchIO {
            setReadStatus.await(
                read = read,
                chapters = chapters.toTypedArray(),
            )

            if (!read || successState?.hasLoggedInTrackers == false || autoTrackState == AutoTrackState.NEVER) {
                return@launchIO
            }

            refreshTrackers()

            val tracks = getTracks.await(mangaId)
            val maxChapterNumber = chapters.maxOf { it.chapterNumber }
            val shouldPromptTrackingUpdate = tracks.any { track -> maxChapterNumber > track.lastChapterRead }

            if (!shouldPromptTrackingUpdate) return@launchIO

            if (autoTrackState == AutoTrackState.ALWAYS) {
                trackChapter.await(context, mangaId, maxChapterNumber)
                withUIContext {
                    context.toast(
                        context.stringResource(AYMR.strings.trackers_updated_summary_manga, maxChapterNumber.toInt()),
                    )
                }
                return@launchIO
            }

            val result = snackbarHostState.showSnackbar(
                message = context.stringResource(MR.strings.confirm_tracker_update, maxChapterNumber.toInt()),
                actionLabel = context.stringResource(MR.strings.action_ok),
                duration = SnackbarDuration.Short,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                trackChapter.await(context, mangaId, maxChapterNumber)
            }
        }
    }

    fun markAllRead() {
        val chapters = successState?.chapters?.map { it.chapter } ?: return
        markChaptersRead(chapters, true)
    }

    fun markAllUnread() {
        val chapters = successState?.chapters?.map { it.chapter } ?: return
        markChaptersRead(chapters, false)
    }

    fun deleteAllDownloads() {
        val state = successState ?: return
        downloadManager.deleteManga(state.manga, state.source)
    }

    fun deleteNonBookmarkedDownloads() {
        val state = successState ?: return
        val chapters = state.chapters.filter { !it.chapter.bookmark }.map { it.chapter }
        if (chapters.isNotEmpty()) {
            screenModelScope.launchNonCancellable {
                val deleted = downloadManager.deleteChapters(chapters, state.manga, state.source)
                markChaptersNotDownloaded(deleted.map { it.id }.toSet())
            }
        }
    }

    fun deleteReadDownloads() {
        val state = successState ?: return
        val chapters = state.chapters.filter { it.chapter.read }.map { it.chapter }
        if (chapters.isNotEmpty()) {
            screenModelScope.launchNonCancellable {
                val deleted = downloadManager.deleteChapters(chapters, state.manga, state.source)
                markChaptersNotDownloaded(deleted.map { it.id }.toSet())
            }
        }
    }

    fun refreshTracking() {
        screenModelScope.launchIO {
            refreshTrackers()
        }
    }

    private suspend fun refreshTrackers(
        refreshTracks: RefreshMangaTracks = Injekt.get(),
    ) {
        refreshTracks.await(mangaId)
            .filter { it.first != null }
            .forEach { (track, e) ->
                logcat(LogPriority.ERROR, e) {
                    "Failed to refresh track data mangaId=$mangaId for service ${track!!.id}"
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
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    private suspend fun downloadChapters(chapters: List<Chapter>, forcedCarrierPkg: String? = null) {
        val manga = successState?.manga ?: return
        downloadManager.downloadChapters(manga, chapters, forcedCarrierPkg = forcedCarrierPkg)
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param chapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(chapters: List<Chapter>, bookmarked: Boolean) {
        screenModelScope.launchIO {
            chapters
                .filterNot { it.bookmark == bookmarked }
                .map { ChapterUpdate(id = it.id, bookmark = bookmarked) }
                .let { updateChapter.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Deletes the given list of chapter.
     *
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<Chapter>) {
        screenModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    val deleted = downloadManager.deleteChapters(
                        chapters,
                        state.manga,
                        state.source,
                    )
                    markChaptersNotDownloaded(deleted.map { it.id }.toSet())
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    /**
     * Drops a deleted chapter's download state on its row — file
     * deletions emit nothing on `downloadManager.statusFlow` (that flow
     * only tracks queued items), so without this the icon stayed
     * DOWNLOADED until the next full list rebuild.
     */
    private fun markChaptersNotDownloaded(deletedIds: Set<Long>) {
        if (deletedIds.isEmpty()) return
        updateSuccessState { state ->
            state.copy(
                chapters = state.chapters.map { item ->
                    if (item.id in deletedIds) {
                        item.copy(
                            downloadState = MangaDownload.State.NOT_DOWNLOADED,
                            downloadProgress = 0,
                        )
                    } else {
                        item
                    }
                },
            )
        }
    }

    private fun downloadNewChapters(chapters: List<Chapter>) {
        screenModelScope.launchNonCancellable {
            val manga = successState?.manga ?: return@launchNonCancellable
            val chaptersToDownload = filterChaptersForDownload.await(manga, chapters)

            if (chaptersToDownload.isNotEmpty()) {
                downloadChapters(chaptersToDownload)
            }
        }
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param state whether to display only unread chapters or all chapters.
     */
    fun setUnreadFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_UNREAD
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_READ
        }
        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetUnreadFilter(manga, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded chapters or all chapters.
     */
    fun setDownloadedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
        }

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDownloadedFilter(manga, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked chapters or all chapters.
     */
    fun setBookmarkedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
        }

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetBookmarkFilter(manga, flag)
        }
    }

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val manga = successState?.manga ?: return

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDisplayMode(manga, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val manga = successState?.manga ?: return

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetSortingModeOrFlipOrder(manga, sort)
        }
    }

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            libraryPreferences.setChapterSettingsDefault(manga)
            if (applyToExisting) {
                setMangaDefaultChapterFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(
                message = context.stringResource(MR.strings.chapter_settings_updated),
            )
        }
    }

    fun resetToDefaultSettings() {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            setMangaDefaultChapterFlags.await(manga)
        }
    }

    fun toggleSelection(
        item: ChapterList.Item,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { successState ->
            val newChapters = successState.processedChapters.toMutableList().apply {
                val selectedIndex = successState.processedChapters.indexOfFirst { it.id == item.chapter.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if ((selectedItem.selected && selected) || (!selectedItem.selected && !selected)) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedChapterIds.addOrRemove(item.id, selected)

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
                                selectedChapterIds.add(inbetweenItem.id)
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
            successState.copy(chapters = newChapters)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        updateSuccessState { successState ->
            val newChapters = successState.chapters.map {
                selectedChapterIds.addOrRemove(it.id, selected)
                it.copy(selected = selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(chapters = newChapters)
        }
    }

    fun invertSelection() {
        updateSuccessState { successState ->
            val newChapters = successState.chapters.map {
                selectedChapterIds.addOrRemove(it.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(chapters = newChapters)
        }
    }

    // Chapters list - end

    // Track sheet - start

    private fun observeTrackers() {
        val manga = successState?.manga ?: return

        screenModelScope.launchIO {
            combine(
                getTracks.subscribe(manga.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { mangaTracks, loggedInTrackers ->
                // Show only if the service supports this manga's source
                val supportedTrackers = loggedInTrackers.filter {
                    (it as? EnhancedMangaTracker)?.accept(source!!) ?: true
                }
                val supportedTrackerIds = supportedTrackers.map { it.id }.toHashSet()
                val supportedTrackerTracks = mangaTracks.filter { it.trackerId in supportedTrackerIds }
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
    }

    // Track sheet - end

    sealed interface Dialog {
        data class ChangeCollection(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState<Collection>>,
        ) : Dialog
        data class DeleteChapters(val chapters: List<Chapter>) : Dialog
        data class DuplicateManga(val manga: Manga, val duplicate: Manga) : Dialog
        data class Migrate(val newManga: Manga, val oldManga: Manga) : Dialog
        data class SetMangaFetchInterval(val manga: Manga) : Dialog
        data object SettingsSheet : Dialog
        data object TrackSheet : Dialog
        data object Comments : Dialog

        /** [coverData] = the artwork currently painted in the hero —
         *  the selected volume's cover when grouped, else the entry's
         *  own cover. The dialog views/shares what the user saw. */
        data class FullCover(val coverData: Any? = null) : Dialog

        /** Chapter tapped on a canonical entry with no enabled carrier —
         *  the "install an extension" sheet (16-04). */
        data class CarrierInstall(val chapter: Chapter) : Dialog

        /** Preferred-carrier picker for a canonical entry. [chapter]
         *  non-null scopes the choice to that chapter ("read this
         *  chapter with…"); null sets the work-level default. */
        data class CarrierPicker(val chapter: Chapter? = null) : Dialog

        /** Find Content Provider — zero-bound canonical entry (17-02). */
        data object FindProvider : Dialog
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    fun showDeleteChapterDialog(chapters: List<Chapter>) {
        updateSuccessState { it.copy(dialog = Dialog.DeleteChapters(chapters)) }
    }

    fun showSettingsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun showTrackDialog() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    fun showCommentsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.Comments) }
    }

    val commentsEnabled: Boolean
        get() = basePreferences.commentsEnabled().get()

    fun showCoverDialog(coverData: Any? = null) {
        updateSuccessState { it.copy(dialog = Dialog.FullCover(coverData)) }
        loadCoverOptions()
    }

    /**
     * Assemble the cover-picker strip: volume-pill art, each bound
     * carrier's own cover, and the canonical identity's artwork (all
     * identity-verified), plus the pinned marker from prefs. Runs per
     * dialog open so late-bound carriers show up.
     */
    private var coverOptionsLoadedAt = 0L

    private fun loadCoverOptions() {
        val state = successState ?: return
        val manga = state.manga
        screenModelScope.launchIO {
            val options = mutableListOf<eu.kanade.presentation.entries.components.CoverOption>()
            val pinPref = preferenceStore.getString(
                eu.kanade.presentation.entries.components.coverPinPrefKey(
                    eu.kanade.presentation.entries.components.COVER_PIN_KIND_MANGA,
                    manga.id,
                ),
                "",
            )
            // Reopening inside the TTL replays the assembled strip —
            // volume and carrier art doesn't re-request; the pin pref is
            // still re-read so a V-committed selection ticks right.
            if (state.coverOptions.isNotEmpty() &&
                System.currentTimeMillis() - coverOptionsLoadedAt < COVER_OPTIONS_TTL_MS
            ) {
                updateSuccessState {
                    it.copy(coverPinKey = pinPref.get().takeIf(String::isNotBlank))
                }
                return@launchIO
            }
            // Push after each batch so the strip fills in as data lands
            // instead of waiting on every carrier fetch to finish.
            fun push() {
                updateSuccessState {
                    it.copy(
                        coverOptions = options.distinctBy { o -> o.data },
                        coverPinKey = pinPref.get().takeIf(String::isNotBlank),
                    )
                }
            }
            state.volumes?.covers?.toSortedMap()?.forEach { (no, meta) ->
                (meta.coverUrl ?: meta.coverThumbUrl)?.let { url ->
                    options += eu.kanade.presentation.entries.components.CoverOption(
                        key = "vol:$no",
                        label = "V. ${eu.kanade.presentation.entries.components.coverOrdinal(no)}",
                        data = url,
                    )
                }
            }
            val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.workIdFromMangaUrl(manga.url)
            if (workId == null) {
                manga.thumbnailUrl?.let { url ->
                    options += eu.kanade.presentation.entries.components.CoverOption(
                        key = "source",
                        label = state.source.name,
                        data = url,
                    )
                }
                push()
                coverOptionsLoadedAt = System.currentTimeMillis()
                return@launchIO
            }
            bridge.byWorkId(workId)?.coverUrl?.let { url ->
                options += eu.kanade.presentation.entries.components.CoverOption(
                    key = "mikodb",
                    label = "MikoDB",
                    data = url,
                )
            }
            // The art Auto currently resolves to (pipeline-picked provider
            // art) — pickable on its own, first after Auto, unless another
            // tile already carries it.
            manga.thumbnailUrl?.takeIf { url -> url.isNotBlank() && options.none { it.data == url } }
                ?.let { url ->
                    options.add(
                        0,
                        eu.kanade.presentation.entries.components.CoverOption(
                            key = "current",
                            label = eu.kanade.presentation.entries.components.coverProviderLabel(url)
                                ?: "Current",
                            data = url,
                        ),
                    )
                }
            push()
            runCatching {
                canonicalChapterRouter.forEachCarrierCover(
                    workId,
                    eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_MANGA,
                ) { pkg, url ->
                    val name = state.carrierMatrix?.carriers
                        ?.firstOrNull { it.pkgName == pkg }?.displayName
                        ?: pkg.substringAfterLast('.')
                    options += eu.kanade.presentation.entries.components.CoverOption(
                        key = "carrier:$pkg",
                        label = name,
                        data = url,
                    )
                    push()
                }
            }
            coverOptionsLoadedAt = System.currentTimeMillis()
        }
    }

    fun showMigrateDialog(duplicate: Manga) {
        val manga = successState?.manga ?: return
        updateSuccessState { it.copy(dialog = Dialog.Migrate(newManga = manga, oldManga = duplicate)) }
    }

    // ---------- Canonical carrier matrix (16-04) ----------

    /** Fetch the work's carrier matrix into state (canonical entries only). */
    private fun loadCarriers(workId: String) {
        screenModelScope.launchIO {
            val matrix = workCarriers.forWork(
                workId,
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_MANGA,
            )
            updateSuccessState {
                it.copy(
                    carrierMatrix = matrix,
                    preferredCarrier = sourcePreferences.preferredCarrierFor(workId),
                )
            }
        }
    }

    /**
     * Canonical volume covers + boundaries (21-03) — one cached fetch
     * per work; only a groupable map reaches state so the chapter list
     * stays byte-equivalent to the flat view on miss/low confidence.
     */
    private fun loadVolumes(workId: String) {
        screenModelScope.launchIO {
            val volumes = canonicalVolumes.ensureFetched(workId)
            if (volumes?.groupable() == true) {
                updateSuccessState { it.copy(volumes = volumes) }
            }
        }
    }

    /** Chapter-level read state on a canonical entry: READY when an
     *  enabled carrier can serve the work, INSTALL_REQUIRED when only
     *  uninstalled carriers exist, UNAVAILABLE when nothing is bound.
     *  Unfetched matrices stay READY — never block a read on a fetch miss. */
    fun carrierStateFor(@Suppress("UNUSED_PARAMETER") chapter: Chapter): ChapterCarrierState {
        val matrix = successState?.carrierMatrix ?: return ChapterCarrierState.READY
        if (!matrix.fetched) return ChapterCarrierState.READY
        return when {
            matrix.readable.isNotEmpty() -> ChapterCarrierState.READY
            matrix.installable.isNotEmpty() -> ChapterCarrierState.INSTALL_REQUIRED
            else -> ChapterCarrierState.UNAVAILABLE
        }
    }

    /**
     * Chapter-tap gate: on canonical entries without an enabled carrier,
     * swap the read for the carrier sheet — the "you need an extension"
     * moment. Returns true when the tap was intercepted.
     */
    fun interceptChapterTap(chapter: Chapter): Boolean {
        val manga = successState?.manga ?: return false
        if (!eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.isCanonicalUrl(manga.url)) {
            return false
        }
        return when (carrierStateFor(chapter)) {
            ChapterCarrierState.READY -> false
            else -> {
                updateSuccessState { it.copy(dialog = Dialog.CarrierInstall(chapter)) }
                true
            }
        }
    }

    /** Install a bound-but-missing extension from the repo. */
    fun installCarrier(pkgName: String) {
        val ext = mangaExtensionManager.availableExtensionsFlow.value
            .firstOrNull { it.pkgName == pkgName } ?: return
        screenModelScope.launchIO {
            mangaExtensionManager.installExtension(ext).collect { step ->
                updateSuccessState {
                    it.copy(carrierInstallSteps = it.carrierInstallSteps + (pkgName to step))
                }
                if (step.isCompleted()) {
                    val manga = successState?.manga ?: return@collect
                    eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
                        .workIdFromMangaUrl(manga.url)
                        ?.let { workId ->
                            workCarriers.invalidate(workId)
                            loadCarriers(workId)
                        }
                }
            }
        }
    }

    /** Persist the user's preferred carrier for this work — the router
     *  ranks it above stickiness/tier on the next read. */
    fun setPreferredCarrier(pkgName: String?) {
        val manga = successState?.manga ?: return
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromMangaUrl(manga.url) ?: return
        sourcePreferences.setPreferredCarrier(workId, pkgName)
        updateSuccessState { it.copy(preferredCarrier = pkgName) }
    }

    /** Per-chapter carrier override — null clears back to the work
     *  default. Updates the state snapshot so row labels recompose. */
    fun setChapterCarrier(chapter: Chapter, pkgName: String?) {
        val manga = successState?.manga ?: return
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromMangaUrl(manga.url) ?: return
        val seq = eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter
            .seqFromCanonUrl(workId, chapter.url) ?: return
        sourcePreferences.setPreferredChapterCarrier(workId, seq, pkgName)
        updateSuccessState {
            it.copy(
                chapterCarriers = if (pkgName == null) {
                    it.chapterCarriers - seq
                } else {
                    it.chapterCarriers + (seq to pkgName)
                },
            )
        }
    }

    /** The override currently stored for [chapter]'s seq — picker
     *  preselection. */
    fun chapterCarrierFor(chapter: Chapter): String? {
        val manga = successState?.manga ?: return null
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromMangaUrl(manga.url) ?: return null
        val seq = eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter
            .seqFromCanonUrl(workId, chapter.url) ?: return null
        return sourcePreferences.preferredChapterCarrier(workId, seq)
    }

    /** Carrier pkg a row would route through (top direct candidate),
     *  resolved to a display name for the row label. Suspend; cheap —
     *  no source I/O. */
    suspend fun carrierLabelFor(chapter: Chapter): String? {
        val manga = successState?.manga ?: return null
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromMangaUrl(manga.url) ?: return null
        val seq = eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter
            .seqFromCanonUrl(workId, chapter.url) ?: return null
        val matrix = successState?.carrierMatrix
        val pkg = canonicalChapterRouter.peekCarrier(workId, seq)
            ?: matrix?.readable?.singleOrNull()?.pkgName
            ?: return null
        return matrix?.carriers
            ?.firstOrNull { it.pkgName == pkg }
            ?.displayName ?: pkg.substringAfterLast('.')
    }

    fun showCarrierPicker() {
        updateSuccessState { it.copy(dialog = Dialog.CarrierPicker()) }
    }

    /** Chapter-scoped carrier picker — the per-chapter override entry
     *  point on multi-carrier canonical rows. */
    fun showChapterCarrierPicker(chapter: Chapter) {
        updateSuccessState { it.copy(dialog = Dialog.CarrierPicker(chapter)) }
    }

    /** "wrong work? unbind" (Phase 20) — revoke this device's own
     *  provisional binding: the worker deletes the row, the matrix
     *  cache drops, and the sheet re-fetches so the row disappears.
     *  A 404 (row already gone / not ours) is still a refresh — the
     *  stale row must not linger either way. */
    private suspend fun unbindCarrierNow(
        workId: String,
        carrier: eu.kanade.tachiyomi.data.mikodb.WorkCarriers.Carrier,
    ): Boolean = when (
        workIdentityApi.unbindBinding(workId, carrier.pkgName, carrier.providerRef)
    ) {
        eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi.UnbindResult.Ok -> {
            workCarriers.invalidate(workId)
            loadCarriers(workId)
            true
        }
        eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi.UnbindResult.NotFound,
        eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi.UnbindResult.Failed,
        -> false
    }

    /** Carrier-sheet ⋯ vote toggle (20-06): 'up'|'down'|'clear' — the
     *  worker enforces the mutually-exclusive slot; a fresh GET of the
     *  matrix re-renders the icons from `my`. */
    fun voteCarrier(
        carrier: eu.kanade.tachiyomi.data.mikodb.WorkCarriers.Carrier,
        vote: String,
    ) {
        val manga = successState?.manga ?: return
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromMangaUrl(manga.url) ?: return
        screenModelScope.launchIO {
            when (
                workIdentityApi.voteBinding(workId, carrier.pkgName, carrier.providerRef, vote)
            ) {
                eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi.SignalResult.Ok -> {
                    workCarriers.invalidate(workId)
                    loadCarriers(workId)
                }
                eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi.SignalResult.NotFound,
                eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi.SignalResult.Failed,
                -> snackbarHostState.showSnackbar(
                    message = context.stringResource(MR.strings.miko_carrier_signal_failed),
                )
            }
        }
    }

    /** Staged-sheet report submission (20-06) — a reasoned signal row
     *  that stacks on the vote slot. */
    fun reportCarrier(
        carrier: eu.kanade.tachiyomi.data.mikodb.WorkCarriers.Carrier,
        kind: String,
    ) {
        val manga = successState?.manga ?: return
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromMangaUrl(manga.url) ?: return
        screenModelScope.launchIO {
            when (
                workIdentityApi.reportBinding(workId, carrier.pkgName, carrier.providerRef, kind)
            ) {
                eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi.SignalResult.Ok -> {
                    workCarriers.invalidate(workId)
                    loadCarriers(workId)
                }
                eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi.SignalResult.NotFound,
                eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi.SignalResult.Failed,
                -> snackbarHostState.showSnackbar(
                    message = context.stringResource(MR.strings.miko_carrier_signal_failed),
                )
            }
        }
    }

    /** Local removal (20-06): write the durable exclusion FIRST so a
     *  dead worker can never resurrect the row, then fire the −0.25
     *  signal best-effort. The exclusion survives re-mints and
     *  level-ups; Find Providers can still restore it. Own provisional
     *  mints route to unbind instead — no self-punishing signal. */
    fun removeCarrier(carrier: eu.kanade.tachiyomi.data.mikodb.WorkCarriers.Carrier) {
        val manga = successState?.manga ?: return
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromMangaUrl(manga.url) ?: return
        screenModelScope.launchIO {
            if (carrier.mine && carrier.status == "provisional") {
                val ok = unbindCarrierNow(workId, carrier)
                snackbarHostState.showSnackbar(
                    message = context.stringResource(
                        if (ok) MR.strings.miko_binding_unbound else MR.strings.miko_binding_unbind_failed,
                    ),
                )
                return@launchIO
            }
            carrierExclusions.exclude(workId, carrier.pkgName, carrier.providerRef)
            workIdentityApi.removeBindingSignal(
                workId,
                carrier.pkgName,
                carrier.providerRef,
                removed = true,
            )
            workCarriers.invalidate(workId)
            loadCarriers(workId)
            val result = snackbarHostState.showSnackbar(
                message = context.stringResource(
                    MR.strings.miko_carrier_removed_snack,
                    carrier.displayName,
                ),
                actionLabel = context.stringResource(MR.strings.action_undo),
                // M3 defaults an actionLabel snackbar to Indefinite — it
                // would sit until the screen leaves. Undo is a courtesy,
                // not a decision prompt: give it a bounded window.
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                restoreCarrier(workId, carrier.pkgName)
            }
        }
    }

    /** Find-Providers restore / snackbar undo — clears the local
     *  exclusion and retracts the remove signal (the stored
     *  provider_ref locates the binding; absent → local-only restore).
     *  Does NOT re-mint: the binding reappears only if the server
     *  still holds it. */
    fun restoreCarrier(workId: String, pkgName: String) {
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

    /** Find-Providers "Restore" on a previously-removed row. */
    fun restoreExcludedCarrier(pkgName: String) {
        val manga = successState?.manga ?: return
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromMangaUrl(manga.url) ?: return
        restoreCarrier(workId, pkgName)
    }

    fun setExcludedScanlators(excludedScanlators: Set<String>) {
        screenModelScope.launchIO {
            setExcludedScanlators.await(mangaId, excludedScanlators)
        }
    }

    // ---------- Find Content Provider (17-02) ----------

    /** Active collector of the finder's search flow — cancelled when a
     *  new search attaches so stale collectors can't overwrite picks. */
    private var providerSearchJob: kotlinx.coroutines.Job? = null

    /** Open the Find Provider sheet and stream per-extension search
     *  results into state (canonical entries only). The finder owns the
     *  search job — closing the sheet never cancels it; reopening within
     *  5 min reattaches to the cached/live flow instead of restarting. */
    fun showFindProviderDialog() {
        val manga = successState?.manga ?: return
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromMangaUrl(manga.url) ?: return
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
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_MANGA,
            )
            if (flow == null) {
                updateSuccessState { s ->
                    s.providerSearch?.let { s.copy(providerSearch = it.copy(searching = false)) } ?: s
                }
                return@launchIO
            }
            flow.collect { search ->
                updateSuccessState { s ->
                    // confirmed + recommendations are UI-side state —
                    // the finder's emissions never overwrite them.
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
     *  refresh carriers + chapters so the entry flips back to the
     *  comments button and routes to the new provider. Canonical rows
     *  have no real source — `fetchAllFromSource` is meaningless here;
     *  the canon map's process cache must be invalidated and the merged
     *  sequence re-materialized instead, or the chapter area spins
     *  until the screen is re-entered. */
    fun confirmProviderPicks() {
        val search = successState?.providerSearch ?: return
        val feeds = search.rows.filter { it.pkgName in search.confirmed }
            .mapNotNull { it.feed }
        val manga = successState?.manga ?: return
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromMangaUrl(manga.url) ?: return
        screenModelScope.launchIO {
            providerFinder.mint(workId, feeds)
            // Confirmed carriers carry the extension's own cover art —
            // adopt the highest-scoring pick when the canonical entry's
            // cover is blank or a non-preferred URL (same guard as the
            // pipeline's cover pass; recognized hi-res never restomps).
            feeds
                .filter { !it.details?.thumbnailUrl.isNullOrBlank() }
                .maxByOrNull { it.score }
                ?.details?.thumbnailUrl
                ?.let { cover ->
                    val stored = manga.thumbnailUrl
                    if (stored.isNullOrBlank() ||
                        !eu.kanade.tachiyomi.data.cover.HiResCoverResolver.isPreferredCoverUrl(stored)
                    ) {
                        updateManga.await(
                            tachiyomi.domain.entries.manga.model.MangaUpdate(
                                id = manga.id,
                                thumbnailUrl = cover,
                                coverLastModified = System.currentTimeMillis(),
                            ),
                        )
                        updateSuccessState {
                            it.copy(manga = mangaRepository.getMangaById(manga.id))
                        }
                    }
                }
            providerFinder.invalidate(
                workId,
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_MANGA,
            )
            workCarriers.invalidate(workId)
            Injekt.get<eu.kanade.tachiyomi.data.mikodb.CanonicalChapterMap>()
                .invalidate(workId)
            dismissDialog()
            updateSuccessState { it.copy(providerSearch = null) }
            loadCarriers(workId)
            Injekt.get<eu.kanade.tachiyomi.data.mikodb.CanonicalChapterSync>()
                .sync(manga, workId)
        }
    }

    /** Install a recommended (uninstalled, likely-to-carry) extension
     *  from the repo — progress reuses the carrier install steps map. */
    fun installRecommendedProvider(pkgName: String) = installCarrier(pkgName)

    /** Available-but-uninstalled extensions of this medium, ranked by
     *  the surveyed S/A/B/C/D tier — the "likely to have it" list. */
    private fun providerRecommendations(): List<eu.kanade.tachiyomi.data.mikodb.ProviderFinder.RecommendedExtension> {
        val installed = mangaExtensionManager.installedExtensionsFlow.value
            .map { it.pkgName }.toSet()
        return mangaExtensionManager.availableExtensionsFlow.value
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

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val manga: Manga,
            val source: MangaSource,
            val isFromSource: Boolean,
            val chapters: List<ChapterList.Item>,
            val availableScanlators: Set<String>,
            val excludedScanlators: Set<String>,
            val trackingCount: Int = 0,
            val hasLoggedInTrackers: Boolean = false,
            val isRefreshingData: Boolean = false,
            /** Canonical mint in flight — the screen shows the mint
             *  veil instead of the extension fetch's instant error. */
            val isMinting: Boolean = false,
            val dialog: Dialog? = null,
            val hasPromptedToAddBefore: Boolean = false,
            val accentColor: Color? = null,
            val intervalDays: Int? = null,
            val showInterval: Boolean = true,
            val suggestions: SuggestionState = SuggestionState.Idle,
            val commentsCount: Long? = null,
            /** Server bindings ∩ installed/enabled extensions (16-04);
             *  null until fetched — treat as unknown, never "no carriers". */
            val carrierMatrix: eu.kanade.tachiyomi.data.mikodb.WorkCarriers.Matrix? = null,
            /** pkgName → install progress for the carrier sheet. */
            val carrierInstallSteps: Map<String, eu.kanade.tachiyomi.extension.InstallStep> = emptyMap(),
            /** User-pinned carrier pkg for this work; null = auto. */
            val preferredCarrier: String? = null,
            /** Per-seq carrier overrides — reactivity snapshot of the
             *  `preferred_chapter_carriers` pref; chapter picker writes
             *  it, row labels read it. */
            val chapterCarriers: Map<Int, String> = emptyMap(),
            /** Find Content Provider sheet state (17-02) — non-null while
             *  the dialog is open; per-extension rows stream in. */
            val providerSearch: eu.kanade.tachiyomi.data.mikodb.ProviderFinder.Search? = null,
            /** Canonical volume covers + boundary map (21-03) — only set
             *  when the cached data clears the confidence floor; the
             *  screen keeps the flat list otherwise. */
            val volumes: eu.kanade.tachiyomi.data.mikodb.CanonicalVolumes.WorkVolumes? = null,
            /** Cover-picker candidates + the pinned tile's key (null =
             *  automatic cover resolution). Populated when the cover
             *  dialog opens. */
            val coverOptions: List<eu.kanade.presentation.entries.components.CoverOption> = emptyList(),
            val coverPinKey: String? = null,
        ) : State {
            val processedChapters by lazy {
                chapters.applyFilters(manga).toList()
            }

            val isAnySelected by lazy {
                chapters.fastAny { it.selected }
            }

            // "Missing X chapters" gap rows are disabled — sources with sparse
            // numbering produced noisy rows the user doesn't want.
            val chapterListItems by lazy { processedChapters }

            val scanlatorFilterActive: Boolean
                get() = excludedScanlators.intersect(availableScanlators).isNotEmpty()

            val filterActive: Boolean
                get() = scanlatorFilterActive || manga.chaptersFiltered()

            val trackingAvailable: Boolean
                get() = trackingCount > 0

            /**
             * Applies the view filters to the list of chapters obtained from the database.
             * @return an observable of the list of chapters filtered and sorted.
             */
            private fun List<ChapterList.Item>.applyFilters(manga: Manga): Sequence<ChapterList.Item> {
                val isLocalManga = manga.isLocal()
                val unreadFilter = manga.unreadFilter
                val downloadedFilter = manga.downloadedFilter
                val bookmarkedFilter = manga.bookmarkedFilter
                return asSequence()
                    .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
                    .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
                    .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalManga } }
                    .sortedWith { (chapter1), (chapter2) ->
                        getChapterSort(manga).invoke(
                            chapter1,
                            chapter2,
                        )
                    }
            }
        }
    }

    private companion object {
        /** How long an assembled cover-option strip stays fresh — a
         *  reopen inside the window replays it instead of re-walking
         *  volumes and carriers. */
        const val COVER_OPTIONS_TTL_MS = 5 * 60 * 1000L

        /** Warm-start cache — the last [State.Success] emitted per manga,
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

@Immutable
sealed class ChapterList {
    @Immutable
    data class MissingCount(
        val id: String,
        val count: Int,
    ) : ChapterList()

    @Immutable
    data class Item(
        val chapter: Chapter,
        val downloadState: MangaDownload.State,
        val downloadProgress: Int,
        val selected: Boolean = false,
    ) : ChapterList() {
        val id = chapter.id
        val isDownloaded = downloadState == MangaDownload.State.DOWNLOADED
    }
}

/**
 * Determine whether the update interval badge should be shown.
 *
 * Returns false when:
 * - The series status is COMPLETED
 * - The most recent chapter upload date is more than 2 months ago (stale)
 */
private fun shouldShowInterval(status: Long, chapters: List<*>): Boolean {
    // Hide for completed series
    if (status == eu.kanade.tachiyomi.source.model.SManga.COMPLETED.toLong()) return false

    // Check if the latest chapter is stale (no update in 2 months)
    val twoMonthsMs = 60L * 24L * 60L * 60L * 1000L // ~60 days in milliseconds
    val now = System.currentTimeMillis()
    val latestDate = chapters.maxOfOrNull { item ->
        when (item) {
            is Chapter -> maxOf(item.dateUpload, item.dateFetch)
            else -> 0L
        }
    } ?: return false

    // If the latest date is 0 (unknown), fall back to showing the interval
    if (latestDate == 0L) return true

    return (now - latestDate) < twoMonthsMs
}
