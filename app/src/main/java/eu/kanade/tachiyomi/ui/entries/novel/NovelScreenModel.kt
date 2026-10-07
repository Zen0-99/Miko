package eu.kanade.tachiyomi.ui.entries.novel

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.addOrRemove
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.chaptersFiltered
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.items.chapter.interactor.SetNovelReadStatus
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.novel.interactor.RefreshNovelTracks
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.components.adjustForTheme
import eu.kanade.presentation.entries.novel.components.NovelChapterAudioAction
import eu.kanade.presentation.entries.novel.components.NovelChapterAudioState
import eu.kanade.presentation.entries.novel.components.NovelChapterDownloadAction
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadProvider
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.suggestions.SuggestionCache
import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionSourceWeight
import eu.kanade.tachiyomi.data.suggestions.SuggestionState
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.novel.NovelFallbackOutcome
import eu.kanade.tachiyomi.data.suggestions.novel.NovelRelatedSuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.novel.NovelSearchFallbackEngine
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.data.suggestions.util.bestMatchScoreFor
import eu.kanade.tachiyomi.data.suggestions.util.dedupeByCleanTitle
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.ChapterHideManager
import eu.kanade.tachiyomi.ui.reader.novel.NewChapterTracker
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlayerController
import eu.kanade.tachiyomi.ui.reader.novel.tts.render.ChapterAudioRenderManager
import eu.kanade.tachiyomi.ui.reader.novel.tts.render.ChapterRender
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.novel.NovelCoverMetadata
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.maybeShowDnsToast
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
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
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.collection.novel.interactor.SetNovelCollections
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.entries.novel.interactor.GetDuplicateLibraryNovel
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.interactor.SetNovelChapterFlags
import tachiyomi.domain.entries.novel.interactor.SetNovelDefaultChapterFlags
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.chapter.interactor.GetNovelChapter
import tachiyomi.domain.items.chapter.interactor.UpdateNovelChapter
import tachiyomi.domain.items.chapter.model.NoChaptersException
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapterUpdate
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository
import tachiyomi.domain.items.chapter.service.getNovelChapterSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelScreenModel(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val novelId: Long,
    private val isFromSource: Boolean,
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val basePreferences: eu.kanade.domain.base.BasePreferences = Injekt.get(),
    private val getNovelAndChapters: GetNovelWithChapters = Injekt.get(),
    private val getDuplicateLibraryNovel: GetDuplicateLibraryNovel = Injekt.get(),
    private val setNovelChapterFlags: SetNovelChapterFlags = Injekt.get(),
    private val setNovelDefaultChapterFlags: SetNovelDefaultChapterFlags = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val updateNovelChapter: UpdateNovelChapter = Injekt.get(),
    private val setReadStatus: SetNovelReadStatus = Injekt.get(),
    private val getCollections: GetNovelCollections = Injekt.get(),
    private val setNovelCollections: SetNovelCollections = Injekt.get(),
    private val novelRepository: NovelRepository = Injekt.get(),
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val getNovelChapter: GetNovelChapter = Injekt.get(),
    private val downloadManager: NovelDownloadManager = Injekt.get(),
    private val downloadCache: NovelDownloadCache = Injekt.get(),
    private val downloadProvider: NovelDownloadProvider = Injekt.get(),
    private val audioRenderManager: ChapterAudioRenderManager = Injekt.get(),
    private val audiobookPlayer: AudiobookPlayerController = Injekt.get(),
    private val fetchInterval: tachiyomi.domain.entries.novel.interactor.NovelFetchInterval = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val threadIdResolver: eu.kanade.tachiyomi.data.comments.ThreadIdResolver = Injekt.get(),
    private val commentsApi: eu.kanade.tachiyomi.data.comments.CommentsApi = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val suggestionCoordinator: SuggestionCoordinator = Injekt.get(),
    private val searchFallbackEngine: NovelSearchFallbackEngine = Injekt.get(),
    private val relatedSuggestionCoordinator: NovelRelatedSuggestionCoordinator = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val getTracks: GetNovelTracks = Injekt.get(),
    private val canonicalEntryUnifier: eu.kanade.tachiyomi.data.mikodb.CanonicalEntryUnifier = Injekt.get(),
    private val libraryCanonizer: eu.kanade.tachiyomi.data.mikodb.LibraryCanonizer = Injekt.get(),
    private val canonicalPipelineRunner: eu.kanade.tachiyomi.data.mikodb.CanonicalPipelineRunner = Injekt.get(),
    private val workCarriers: eu.kanade.tachiyomi.data.mikodb.WorkCarriers = Injekt.get(),
    private val carrierExclusions: eu.kanade.tachiyomi.data.mikodb.CarrierExclusions = Injekt.get(),
    private val workIdentityApi: eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi = Injekt.get(),
    private val providerFinder: eu.kanade.tachiyomi.data.mikodb.ProviderFinder = Injekt.get(),
    private val providerTiers: eu.kanade.tachiyomi.data.mikodb.ProviderTiers = Injekt.get(),
    private val novelExtensionManager: eu.kanade.tachiyomi.extension.novel.NovelExtensionManager = Injekt.get(),
    private val bridge: eu.kanade.tachiyomi.data.mikodb.MangaNovelIdBridge = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<NovelScreenModel.State>(State.Loading) {

    /**
     * Set when this entry resolves to a materialized canonical `miko:`
     * row — the screen replaces itself with that entry so there's one
     * entry per real work instead of a duplicate per extension.
     */
    val redirectToEntryId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    private val chapterHideManager = ChapterHideManager(context)
    private val newChapterTracker = NewChapterTracker(context)

    private val successState: State.Success?
        get() = state.value as? State.Success

    val novel: Novel?
        get() = successState?.novel

    val source: NovelSource?
        get() = successState?.source

    private val isFavorited: Boolean
        get() = novel?.favorite ?: false

    private val allChapters: List<NovelChapterList.Item>?
        get() = successState?.chapters

    private val filteredChapters: List<NovelChapterList.Item>?
        get() = successState?.processedChapters

    val chapterSwipeStartAction = libraryPreferences.swipeChapterEndAction().get()
    val chapterSwipeEndAction = libraryPreferences.swipeChapterStartAction().get()

    internal var isFromChangeCollection: Boolean = false

    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    private val selectedChapterIds: HashSet<Long> = HashSet()

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
        val seed = buildSuggestionSeed(success.novel)
        SuggestionCache.invalidateForSeed(seed, success.novel.url)
        loadSuggestions(
            seed,
            novel = success.novel,
            source = success.novel.toCatalogueSource(),
            force = true,
        )
    }

    private fun buildSuggestionSeed(novel: Novel): SuggestionSeed {
        val title = novel.title
        val candidates = SuggestionTitleResolver.resolveCandidates(
            title = title,
            description = novel.description,
            url = novel.url,
        )
        return SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = title,
            candidateTitles = candidates,
            description = novel.description,
            author = novel.author,
            genres = novel.genre,
        )
    }

    private fun Novel.toCatalogueSource(): NovelCatalogueSource? =
        sourceManager.getOrStub(source) as? NovelCatalogueSource

    private fun emitProgressiveSuggestions(list: List<SuggestionItem>, currentNovel: Novel?) {
        val seed = suggestionSeedUsed ?: return
        val sorted = synchronized(list) {
            list.dedupeByCleanTitle(seed)
                .filter { item ->
                    val isSelf = SuggestionTitleResolver.isSameProviderEntry(item, currentNovel?.url)
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
        novel: Novel? = null,
        source: NovelCatalogueSource? = null,
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

        val currentNovel = novel ?: successState?.novel
        val currentSource = source ?: (
            currentNovel?.let {
                sourceManager.getOrStub(it.source)
            } as? NovelCatalogueSource
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
                                    val isSelf = SuggestionTitleResolver.isSameProviderEntry(item, currentNovel?.url)
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
                                    emitProgressiveSuggestions(suggestionsList, currentNovel)
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logcat(LogPriority.DEBUG) { "[NovelScreenModel] External suggestions failed: ${e.message}" }
                        }
                    }

                    // Task 2: Search Fallback suggestions
                    if (currentNovel != null && currentSource != null) {
                        launch {
                            try {
                                val outcome = searchFallbackEngine.fetchSearchFallback(
                                    novel = currentNovel,
                                    source = currentSource,
                                    seed = seed,
                                    maxResults = 40,
                                    onProgress = { progressItems ->
                                        synchronized(suggestionsList) {
                                            val existingUrls = suggestionsList.map { it.providerUrl }.toSet()
                                            val newItems = progressItems.filter { it.providerUrl !in existingUrls }
                                            suggestionsList.addAll(newItems)
                                        }
                                        emitProgressiveSuggestions(suggestionsList, currentNovel)
                                    },
                                )
                                if (outcome is NovelFallbackOutcome.Success && outcome.items.isNotEmpty()) {
                                    synchronized(suggestionsList) {
                                        val existingUrls = suggestionsList.map { it.providerUrl }.toSet()
                                        val newItems = outcome.items.filter { it.providerUrl !in existingUrls }
                                        suggestionsList.addAll(newItems)
                                    }
                                    emitProgressiveSuggestions(suggestionsList, currentNovel)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                logcat(LogPriority.DEBUG) {
                                    "[NovelScreenModel] Native search fallback failed: ${e.message}"
                                }
                            }
                        }
                    }

                    // Task 3: Related novels from source (if supported)
                    if (currentNovel != null && currentSource != null) {
                        launch {
                            try {
                                val relatedOutcome = relatedSuggestionCoordinator.fetchRelatedSuggestions(
                                    novel = currentNovel,
                                    source = currentSource,
                                    seed = seed,
                                    maxResults = 20,
                                )
                                if (relatedOutcome is NovelFallbackOutcome.Success &&
                                    relatedOutcome.items.isNotEmpty()
                                ) {
                                    synchronized(suggestionsList) {
                                        val existingUrls = suggestionsList.map { it.providerUrl }.toSet()
                                        val newItems = relatedOutcome.items.filter { it.providerUrl !in existingUrls }
                                        suggestionsList.addAll(newItems)
                                    }
                                    emitProgressiveSuggestions(suggestionsList, currentNovel)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                logcat(LogPriority.DEBUG) {
                                    "[NovelScreenModel] Related novels fetch failed: ${e.message}"
                                }
                            }
                        }
                    }
                }

                val finalCombined = synchronized(suggestionsList) {
                    suggestionsList.dedupeByCleanTitle(seed)
                        .filter { item ->
                            val isSelf = SuggestionTitleResolver.isSameProviderEntry(item, currentNovel?.url)
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
                logcat(LogPriority.DEBUG) { "NovelScreenModel suggestions fetch failed: ${e.message}" }
                updateSuccessState { it.copy(suggestions = SuggestionState.Error(e.message ?: "Unknown error")) }
            }
        }
    }

    init {
        // Warm-start: reopening an entry renders the last Success instantly
        // instead of flashing the spinner — the flows below overwrite every
        // field within a frame anyway. Cleared dialogs/refresh flags.
        lastSuccess[novelId]?.let {
            mutableState.value = it.copy(dialog = null, isRefreshingData = false)
        }
        screenModelScope.launch {
            state.collect { s ->
                if (s is State.Success) {
                    synchronized(lastSuccess) { lastSuccess[s.novel.id] = s.copy(dialog = null) }
                }
            }
        }

        screenModelScope.launchIO {
            combine(
                getNovelAndChapters.subscribe(novelId).distinctUntilChanged(),
                downloadCache.changes,
                downloadManager.queueState,
                audioRenderManager.queueState,
            ) { novelAndChapters, _, _, _ -> novelAndChapters }
                .flowWithLifecycle(lifecycle)
                .collectLatest { (novel, chapters) ->
                    updateSuccessState {
                        it.copy(
                            novel = novel,
                            chapters = chapters.toNovelChapterListItems(novel),
                        )
                    }
                }
        }

        observeDownloads()
        observeAudioRenders()

        screenModelScope.launchIO {
            val novel = getNovelAndChapters.awaitNovel(novelId)

            // Unify extension-source entries with a materialized canonical
            // `miko:` row (MikoDB identity). Redirect instead of loading
            // this duplicate — one entry per real work.
            val canonical = try {
                canonicalEntryUnifier.resolve(novel)
            } catch (e: Exception) {
                null
            }
            if (canonical != null && canonical.id != novelId) {
                logcat(LogPriority.DEBUG) {
                    "NovelScreenModel: redirecting '${novel.title}' (id=$novelId, source=${novel.source}) to canonical entry id=${canonical.id}"
                }
                redirectToEntryId.value = canonical.id
                return@launchIO
            }

            // Extension-source favorites never unify (read state lives on
            // this row) — kick the canonizer: resolve → materialize →
            // canonize → move the favorite onto the canonical row. On
            // success this screen swaps to the canonical entry whose
            // chapters come from the carrier matrix, not one source.
            if (novel.favorite &&
                novel.source != eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.CANONICAL_SOURCE_ID &&
                !eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.isCanonicalUrl(novel.url)
            ) {
                // Veil first, then mint — Success may not exist yet, so
                // wait for it before flagging. The redirect lands when
                // the mint resolves.
                screenModelScope.launchIO {
                    state.first { it is NovelScreenModel.State.Success }
                    updateSuccessState { it.copy(isMinting = true) }
                    val c = runCatching { libraryCanonizer.migrateNovel(novel, heavy = true) }.getOrNull()
                    updateSuccessState { it.copy(isMinting = false) }
                    if (c != null) redirectToEntryId.value = c.id
                }
            }

            // Independent DB reads run in parallel — first-open latency is
            // dominated by these round-trips back to back.
            val chaptersDeferred = async {
                getNovelAndChapters.awaitChapters(novelId)
                    .toNovelChapterListItems(novel)
            }
            val sourceDeferred = async { Injekt.get<NovelSourceManager>().getOrStub(novel.source) }
            val hiddenIdsDeferred = async { chapterHideManager.getHiddenChapterIds(novelId) }
            val flagsDeferred = async {
                if (!novel.favorite) setNovelDefaultChapterFlags.await(novel)
            }

            val chapters = chaptersDeferred.await()

            // Canonical rows have no backing extension — details/chapters
            // come from the canonical chapter pipeline (12-04), not the
            // stub source.
            val isCanonical = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.isCanonicalUrl(novel.url)
            val needRefreshInfo = !novel.initialized && !isCanonical
            // For book sources (Anna's Archive), don't auto-fetch chapters when
            // empty — the user must manually press "Download book" to trigger
            // the EPUB download + splitting, since it's a heavy operation.
            val source = sourceDeferred.await()
            val isBookSource = source.id == 6400L
            val needRefreshChapter = chapters.isEmpty() && !isBookSource && !isCanonical

            flagsDeferred.await()

            // Load cached accent color synchronously to avoid flash on open —
            // keyed to the current cover so a changed cover can't seed a stale
            // accent (the composable extractor would then flash the correction).
            val isDark =
                (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            val cachedAccentColor = eu.kanade.tachiyomi.util.EntryCoverMetadata.getBaseColor(
                eu.kanade.tachiyomi.util.EntryCoverMetadata.EntryType.NOVEL,
                novel.id,
                eu.kanade.presentation.entries.components.coverKeyOf(novel.asNovelCover()),
            )?.let {
                Color(adjustForTheme(it, isDark))
            }

            val intervalDays = fetchInterval.calculateInterval(
                chapters.map { it.chapter },
            )
            // Hide interval badge if series is completed or last update is
            // older than 2 months (stale schedule).
            val showInterval = shouldShowInterval(novel.status, chapters.map { it.chapter })

            mutableState.update { prev ->
                State.Success(
                    novel = novel,
                    source = source,
                    isFromSource = isFromSource,
                    chapters = chapters,
                    hiddenChapterIds = hiddenIdsDeferred.await(),
                    isRefreshingData = needRefreshInfo || needRefreshChapter,
                    dialog = null,
                    // Never erase an accent the composable already set —
                    // this update replaces the whole Success and the
                    // extractor's LaunchedEffect wouldn't refire (key
                    // unchanged), leaving the theme color stuck.
                    accentColor = cachedAccentColor
                        ?: (prev as? State.Success)?.accentColor,
                    intervalDays = intervalDays,
                    showInterval = showInterval,
                )
            }

            // Start observe tracking since it only needs novelId
            observeTrackers()

            // "View comments (N)" badge — resolves the work thread once
            // and counts its visible comments. Silent on failure.
            if (commentsEnabled) {
                screenModelScope.launchIO {
                    val threadId = threadIdResolver.forNovel(novel) ?: return@launchIO
                    val result = commentsApi.threadCounts(listOf(threadId))
                    if (result is eu.kanade.tachiyomi.data.comments.CommentsApi.Result.Ok) {
                        updateSuccessState {
                            it.copy(commentsCount = result.value.counts[threadId] ?: 0L)
                        }
                    }
                }
            }

            // Canonical rows collect+canonize chapter feeds in the
            // background (13-02), same as the manga entry path, then
            // materialize canon rows as chapter list entries.
            if (isCanonical) {
                eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
                    .workIdFromNovelUrl(novel.url)
                    ?.let { workId ->
                        canonicalPipelineRunner.launchNovel(workId, novel)
                        loadCarriers(workId)
                        // Books (kind=book) carry no chapter feed — they
                        // resolve to an Anna's Archive entry that owns
                        // the EPUB download + split path.
                        if (bridge.byWorkId(workId)?.type == "book") {
                            updateSuccessState { it.copy(isBookWork = true) }
                            resolveBookCarrier(workId)
                        }
                    }
            }

            // Fetch suggestions asynchronously
            loadSuggestions(buildSuggestionSeed(novel))

            if (screenModelScope.isActive) {
                val fetchFromSourceTasks = listOf(
                    async { if (needRefreshInfo) fetchNovelFromSource() },
                    async { if (needRefreshChapter) fetchChaptersFromSource() },
                )
                fetchFromSourceTasks.awaitAll()
            }

            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    private suspend fun fetchNovelFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        try {
            withIOContext {
                val source = state.source as? NovelCatalogueSource ?: return@withIOContext
                val networkNovel = source.getNovelDetails(state.novel.toSNovel())
                android.util.Log.d(
                    "NovelScreenModel",
                    "fetchNovelFromSource: novel=${state.novel.title} remoteThumbnailUrl=${networkNovel.thumbnail_url} localThumbnailUrl=${state.novel.thumbnailUrl}",
                )
                updateNovel.awaitUpdateFromSource(
                    localNovel = state.novel,
                    remoteTitle = networkNovel.title,
                    remoteAuthor = networkNovel.author,
                    remoteArtist = networkNovel.artist,
                    remoteDescription = networkNovel.description,
                    remoteGenre = networkNovel.getGenres(),
                    remoteThumbnailUrl = networkNovel.thumbnail_url,
                    remoteStatus = networkNovel.status.toLong(),
                    remoteUpdateStrategy = networkNovel.update_strategy,
                    manualFetch = manualFetch,
                )
            }
        } catch (e: Throwable) {
            if (e is HttpException && e.code == 103) return
            android.util.Log.e("NovelScreenModel", "fetchNovelFromSource FAILED: novel=${state.novel.title}", e)
            logcat(LogPriority.ERROR, e)
            context.maybeShowDnsToast(e, state.novel.thumbnailUrl)
            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = with(context) { e.formattedMessage })
            }
        }
    }

    private suspend fun fetchChaptersFromSource(
        manualFetch: Boolean = false,
        bookCarrier: BookCarrier? = null,
    ) {
        val state = successState ?: return
        var primaryError: Throwable? = null
        var primarySucceeded = false

        // --- Primary source fetch ---
        try {
            withIOContext {
                // Canonical books route to the resolved carrier source
                // (Anna's Archive) + its entry url — state.source is the
                // canonical stub, which can't serve chapters.
                val (source, snovel) = if (bookCarrier != null) {
                    val s = sourceManager.get(bookCarrier.sourceId) as? NovelCatalogueSource
                        ?: return@withIOContext
                    s to eu.kanade.tachiyomi.novelsource.model.SNovel.create()
                        .apply { url = bookCarrier.url }
                } else {
                    (state.source as? NovelCatalogueSource ?: return@withIOContext) to
                        state.novel.toSNovel()
                }
                val remoteChapters = source.getChapterList(snovel)

                if (remoteChapters.isEmpty()) {
                    throw NoChaptersException()
                }

                val localChapters = state.chapters.map { it.chapter }
                val newChapters = remoteChapters.mapIndexed { i, sNovelChapter ->
                    tachiyomi.domain.items.chapter.model.NovelChapter.create().copy(
                        novelId = state.novel.id,
                        url = sNovelChapter.url,
                        name = sNovelChapter.name,
                        chapterNumber = sNovelChapter.chapter_number.toDouble(),
                        sourceOrder = i.toLong(),
                        dateFetch = sNovelChapter.date_upload,
                        dateUpload = sNovelChapter.date_upload,
                    )
                }

                val mergedChapters = mergeChapters(localChapters, newChapters)
                val toAdd = mergedChapters.filter { it.id == -1L }
                val toUpdate = mergedChapters.filter { it.id != -1L }
                    .filter { mc -> localChapters.any { it.id == mc.id && it != mc } }

                // Delete stale local chapters that are no longer in the remote list.
                // This handles cases like Anna's Archive where the chapter list changes
                // from a single chapter to multiple split chapters — the old single
                // chapter (with a different URL) needs to be removed.
                val remoteUrls = newChapters.map { it.url }.toSet()
                val toDelete = localChapters.filter { it.url !in remoteUrls }
                    .filter { !it.read } // Don't delete read chapters (preserve reading history)
                    .map { it.id }

                if (toAdd.isNotEmpty()) {
                    novelChapterRepository.addAllNovelChapters(toAdd)
                }
                if (toUpdate.isNotEmpty()) {
                    updateNovelChapter.awaitAll(
                        toUpdate.map {
                            NovelChapterUpdate(
                                id = it.id,
                                name = it.name,
                                url = it.url,
                                chapterNumber = it.chapterNumber,
                                dateFetch = it.dateFetch,
                                dateUpload = it.dateUpload,
                                sourceOrder = it.sourceOrder,
                            )
                        },
                    )
                }
                if (toDelete.isNotEmpty()) {
                    novelChapterRepository.removeChaptersWithIds(toDelete)
                }
                primarySucceeded = true
            }
        } catch (e: Throwable) {
            primaryError = e
        }

        if (primaryError != null) {
            logcat(LogPriority.ERROR, primaryError)
            if (!primarySucceeded) {
                screenModelScope.launch {
                    val message = if (primaryError is NoChaptersException) {
                        with(context) { primaryError.formattedMessage }
                    } else {
                        with(context) { primaryError.formattedMessage }
                    }
                    snackbarHostState.showSnackbar(message = message)
                }
            }
        }
    }

    private fun mergeChapters(
        localChapters: List<NovelChapter>,
        remoteChapters: List<NovelChapter>,
    ): List<NovelChapter> {
        val localMap = localChapters.associateBy { it.url }
        return remoteChapters.map { remote ->
            val local = localMap[remote.url]
            if (local != null) {
                local.copyFrom(remote)
            } else {
                remote
            }
        }
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        // Manual refresh on an extension-linked favorite is also a
        // migration retry — canonize may have failed earlier (empty
        // feed, offline, no carriers) and init only kicks it once.
        val mintCandidate = successState?.novel
            ?.takeIf {
                manualFetch && it.favorite &&
                    it.source != eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.CANONICAL_SOURCE_ID &&
                    !eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.isCanonicalUrl(it.url)
            }
        if (mintCandidate != null) {
            updateSuccessState { it.copy(isMinting = true) }
            screenModelScope.launchIO {
                val canonical = runCatching {
                    libraryCanonizer.migrateNovel(mintCandidate, heavy = true)
                }.getOrNull()
                updateSuccessState { it.copy(isMinting = false, isRefreshingData = false) }
                if (canonical != null) {
                    redirectToEntryId.value = canonical.id
                } else {
                    // Mint couldn't take over — run the deferred fetch so
                    // a failure still surfaces honestly.
                    fetchAllFromSourceBody(manualFetch)
                }
            }
            return
        }
        screenModelScope.launch { fetchAllFromSourceBody(manualFetch) }
    }

    private suspend fun fetchAllFromSourceBody(manualFetch: Boolean) {
        updateSuccessState { it.copy(isRefreshingData = true) }
        try {
            // Canonical rows have no extension source to refresh —
            // pull-to-refresh must re-run enrich + canonize + sync
            // (force: refresh is the explicit "retry now" action).
            val state = successState ?: return
            val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
                .workIdFromNovelUrl(state.novel.url)
            if (workId != null) {
                workCarriers.invalidate(workId)
                canonicalPipelineRunner.launchNovel(workId, state.novel, force = true)
                loadCarriers(workId)
            } else {
                kotlinx.coroutines.coroutineScope {
                    val tasks = listOf(
                        async { fetchNovelFromSource(manualFetch) },
                        async { fetchChaptersFromSource(manualFetch) },
                    )
                    tasks.awaitAll()
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "fetchAllFromSource failed" }
        } finally {
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    /**
     * Fetch only new chapters beyond what the user already has.
     * Uses the source's getLatestChapters for incremental fetching if supported.
     */
    fun fetchNewChapters() {
        screenModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            try {
                withIOContext {
                    val state = successState ?: return@withIOContext
                    val source = state.source as? NovelCatalogueSource ?: return@withIOContext
                    val existingCount = state.chapters.size

                    logcat(LogPriority.DEBUG) {
                        "NovelScreenModel: fetchNewChapters source=${source.name} existingCount=$existingCount"
                    }
                    val remoteChapters = source.getLatestChapters(state.novel.toSNovel(), existingCount)
                    logcat(LogPriority.DEBUG) {
                        "NovelScreenModel: fetchNewChapters got ${remoteChapters.size} remote chapters"
                    }

                    if (remoteChapters.isNotEmpty()) {
                        val localChapters = state.chapters.map { it.chapter }
                        val newChapters = remoteChapters.mapIndexed { i, sNovelChapter ->
                            tachiyomi.domain.items.chapter.model.NovelChapter.create().copy(
                                novelId = state.novel.id,
                                url = sNovelChapter.url,
                                name = sNovelChapter.name,
                                chapterNumber = sNovelChapter.chapter_number.toDouble(),
                                sourceOrder = i.toLong(),
                                dateFetch = sNovelChapter.date_upload,
                                dateUpload = sNovelChapter.date_upload,
                            )
                        }

                        val mergedChapters = mergeChapters(localChapters, newChapters)
                        val toAdd = mergedChapters.filter { it.id == -1L }
                        val toUpdate = mergedChapters.filter { it.id != -1L }
                            .filter { mc -> localChapters.any { it.id == mc.id && it != mc } }

                        if (toAdd.isNotEmpty()) {
                            novelChapterRepository.addAllNovelChapters(toAdd)
                        }
                        if (toUpdate.isNotEmpty()) {
                            updateNovelChapter.awaitAll(
                                toUpdate.map {
                                    NovelChapterUpdate(
                                        id = it.id,
                                        name = it.name,
                                        url = it.url,
                                        chapterNumber = it.chapterNumber,
                                        dateFetch = it.dateFetch,
                                        dateUpload = it.dateUpload,
                                        sourceOrder = it.sourceOrder,
                                    )
                                },
                            )
                        }

                        screenModelScope.launch {
                            snackbarHostState.showSnackbar(
                                "Added ${toAdd.size} new chapter(s)",
                            )
                        }
                    } else {
                        screenModelScope.launch {
                            snackbarHostState.showSnackbar(
                                "No new chapters found (site may be blocking requests)",
                            )
                        }
                    }
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR) {
                    "NovelScreenModel: fetchNewChapters error: ${e::class.simpleName}: ${e.message}"
                }
                logcat(LogPriority.ERROR, e)
                screenModelScope.launch {
                    snackbarHostState.showSnackbar(message = with(context) { e.formattedMessage })
                }
            } finally {
                updateSuccessState { it.copy(isRefreshingData = false) }
            }
        }
    }

    /**
     * Fetch all chapters from the source with a progress notification.
     * Fetches page-by-page and saves each batch to the database immediately,
     * so chapters appear in the UI as they're fetched.
     */
    fun fetchAllChaptersWithProgress() {
        screenModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            val notificationBuilder = context.notificationBuilder(Notifications.CHANNEL_NOVEL_CHAPTER_FETCH) {
                setAutoCancel(false)
                setOnlyAlertOnce(true)
                setOngoing(true)
                setSmallIcon(android.R.drawable.stat_sys_download)
            }
            try {
                withIOContext {
                    val state = successState ?: return@withIOContext
                    val source = state.source as? NovelCatalogueSource ?: return@withIOContext
                    val novelTitle = state.novel.title
                    val novelId = state.novel.id

                    notificationBuilder
                        .setContentTitle(novelTitle.chop(30))
                        .setContentText("Fetching chapters...")
                        .setProgress(0, 0, true)
                    context.notify(Notifications.ID_NOVEL_CHAPTER_FETCH, notificationBuilder.build())

                    var totalPages = 1
                    var totalAdded = 0
                    var totalUpdated = 0
                    var totalFetched = 0
                    var sourceOrder = 0L
                    val seenUrls = mutableSetOf<String>()
                    var consecutiveDuplicatePages = 0

                    for (page in 1..125) { // hard cap of 125 pages
                        val pageResult = source.getChapterListPage(state.novel.toSNovel(), page)

                        if (page == 1) {
                            totalPages = pageResult.totalPages
                            if (totalPages <= 0) totalPages = 1
                            logcat(LogPriority.DEBUG) {
                                "NovelScreenModel: fetchAllChaptersWithProgress totalPages=$totalPages"
                            }
                        }

                        if (pageResult.chapters.isEmpty()) {
                            logcat(LogPriority.DEBUG) {
                                "NovelScreenModel: page $page/$totalPages returned 0 chapters, skipping"
                            }
                            if (page >= totalPages) break
                            continue
                        }

                        // Convert and merge with current local chapters
                        // Deduplicate within this fetch session — skip chapters we've
                        // already seen on a previous page to prevent the same chapters
                        // being added thousands of times when a source returns the same
                        // data on every page (e.g. Ranobes embeds all chapters in
                        // window.__DATA__ regardless of the page URL).
                        val localChapters = novelChapterRepository.getNovelChaptersByNovelId(novelId)
                        val newChapters = pageResult.chapters
                            .filter { sNovelChapter -> seenUrls.add(sNovelChapter.url) }
                            .map { sNovelChapter ->
                                tachiyomi.domain.items.chapter.model.NovelChapter.create().copy(
                                    novelId = novelId,
                                    url = sNovelChapter.url,
                                    name = sNovelChapter.name,
                                    chapterNumber = sNovelChapter.chapter_number.toDouble(),
                                    sourceOrder = sourceOrder++,
                                    dateFetch = sNovelChapter.date_upload,
                                    dateUpload = sNovelChapter.date_upload,
                                )
                            }

                        if (newChapters.isEmpty()) {
                            logcat(LogPriority.DEBUG) {
                                "NovelScreenModel: page $page returned only duplicates, incrementing duplicate counter"
                            }
                            consecutiveDuplicatePages++
                            if (consecutiveDuplicatePages >= 2) {
                                logcat(LogPriority.DEBUG) {
                                    "NovelScreenModel: 2 consecutive duplicate pages, stopping fetch"
                                }
                                break
                            }
                            if (page >= totalPages) break
                            continue
                        }
                        consecutiveDuplicatePages = 0

                        val mergedChapters = mergeChapters(localChapters, newChapters)
                        val toAdd = mergedChapters.filter { it.id == -1L }
                        val toUpdate = mergedChapters.filter { it.id != -1L }
                            .filter { mc -> localChapters.any { it.id == mc.id && it != mc } }

                        if (toAdd.isNotEmpty()) {
                            novelChapterRepository.addAllNovelChapters(toAdd)
                        }
                        if (toUpdate.isNotEmpty()) {
                            updateNovelChapter.awaitAll(
                                toUpdate.map {
                                    NovelChapterUpdate(
                                        id = it.id,
                                        name = it.name,
                                        url = it.url,
                                        chapterNumber = it.chapterNumber,
                                        dateFetch = it.dateFetch,
                                        dateUpload = it.dateUpload,
                                        sourceOrder = it.sourceOrder,
                                    )
                                },
                            )
                        }

                        totalAdded += toAdd.size
                        totalUpdated += toUpdate.size
                        totalFetched += pageResult.chapters.size

                        // Update notification with progress
                        notificationBuilder
                            .setContentText("Page $page/$totalPages — $totalFetched chapters fetched")
                            .setProgress(totalPages, page, false)
                        context.notify(Notifications.ID_NOVEL_CHAPTER_FETCH, notificationBuilder.build())

                        logcat(LogPriority.DEBUG) {
                            "NovelScreenModel: page $page/$totalPages done, +${toAdd.size} new, +${toUpdate.size} updated"
                        }

                        if (page >= totalPages) break
                    }

                    notificationBuilder
                        .setContentText("Done: $totalFetched chapters ($totalAdded new, $totalUpdated updated)")
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setAutoCancel(true)
                    context.notify(Notifications.ID_NOVEL_CHAPTER_FETCH, notificationBuilder.build())

                    screenModelScope.launch {
                        snackbarHostState.showSnackbar(
                            "Fetched $totalFetched chapters ($totalAdded new)",
                        )
                    }
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR) {
                    "NovelScreenModel: fetchAllChaptersWithProgress error: ${e::class.simpleName}: ${e.message}"
                }
                logcat(LogPriority.ERROR, e)
                val errorText = when (e) {
                    is NoChaptersException -> "No chapters found (site may be blocking requests)"
                    else -> "Error: ${e.message ?: e::class.simpleName}"
                }
                notificationBuilder
                    .setContentText(errorText)
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setAutoCancel(true)
                context.notify(Notifications.ID_NOVEL_CHAPTER_FETCH, notificationBuilder.build())
                screenModelScope.launch {
                    snackbarHostState.showSnackbar(message = with(context) { e.formattedMessage })
                }
            } finally {
                updateSuccessState { it.copy(isRefreshingData = false) }
                // Auto-dismiss after 3 seconds
                kotlinx.coroutines.delay(3000)
                context.cancelNotification(Notifications.ID_NOVEL_CHAPTER_FETCH)
            }
        }
    }

    fun toggleFavorite() {
        toggleFavorite(
            onRemoved = {},
        )
    }

    fun toggleFavorite(
        onRemoved: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        screenModelScope.launchIO {
            val novel = state.novel

            if (isFavorited) {
                if (updateNovel.awaitUpdateFavorite(novel.id, false)) {
                    withUIContext { onRemoved() }
                }
            } else {
                if (checkDuplicate) {
                    val duplicate = getDuplicateLibraryNovel.await(novel).getOrNull(0)
                    if (duplicate != null) {
                        updateSuccessState {
                            it.copy(dialog = Dialog.DuplicateNovel(novel, duplicate))
                        }
                        return@launchIO
                    }
                }

                val collections = getCollections.await(novel.id)
                val defaultCollectionId = libraryPreferences.defaultMangaCollection().get().toLong()
                val defaultCollection = collections.find { it.id == defaultCollectionId }
                when {
                    defaultCollection != null -> {
                        val result = updateNovel.awaitUpdateFavorite(novel.id, true)
                        if (!result) return@launchIO
                        moveNovelToCollection(defaultCollection)
                    }
                    defaultCollectionId == 0L || collections.isEmpty() -> {
                        val result = updateNovel.awaitUpdateFavorite(novel.id, true)
                        if (!result) return@launchIO
                        moveNovelToCollection(null)
                    }
                    else -> {
                        isFromChangeCollection = true
                        showChangeCollectionDialog()
                    }
                }
            }
        }
    }

    fun showChangeCollectionDialog() {
        val novel = successState?.novel ?: return
        screenModelScope.launch {
            val collections = getCollections.await(novel.id)
            val selection = getNovelCollectionIds(novel)
            updateSuccessState { successState ->
                successState.copy(
                    dialog = Dialog.ChangeCollection(
                        novel = novel,
                        initialSelection = collections.mapAsCheckboxState { it.id in selection }.toImmutableList(),
                    ),
                )
            }
        }
    }

    private suspend fun getNovelCollectionIds(novel: Novel): Set<Long> {
        return getCollections.await(novel.id).map { it.id }.toSet()
    }

    fun moveNovelToCollection(collection: Collection?) {
        val novel = successState?.novel ?: return
        screenModelScope.launchIO {
            setNovelCollections.await(novel.id, listOfNotNull(collection?.id))
        }
    }

    fun moveNovelToCollectionsAndAddToLibrary(novel: Novel, collectionIds: List<Long>) {
        screenModelScope.launchIO {
            setNovelCollections.await(novel.id, collectionIds)
            updateNovel.awaitUpdateFavorite(novel.id, true)
        }
    }

    fun setUnreadFilter(state: TriState) {
        val novel = successState?.novel ?: return
        val flag = when (state) {
            TriState.DISABLED -> Novel.SHOW_ALL
            TriState.ENABLED_IS -> Novel.CHAPTER_SHOW_UNREAD
            TriState.ENABLED_NOT -> Novel.CHAPTER_SHOW_READ
        }
        screenModelScope.launchNonCancellable {
            setNovelChapterFlags.awaitSetUnreadFilter(novel, flag)
        }
    }

    fun setDownloadedFilter(state: TriState) {
        val novel = successState?.novel ?: return
        val flag = when (state) {
            TriState.DISABLED -> Novel.SHOW_ALL
            TriState.ENABLED_IS -> Novel.CHAPTER_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Novel.CHAPTER_SHOW_NOT_DOWNLOADED
        }
        screenModelScope.launchNonCancellable {
            setNovelChapterFlags.awaitSetDownloadedFilter(novel, flag)
        }
    }

    fun setBookmarkedFilter(state: TriState) {
        val novel = successState?.novel ?: return
        val flag = when (state) {
            TriState.DISABLED -> Novel.SHOW_ALL
            TriState.ENABLED_IS -> Novel.CHAPTER_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Novel.CHAPTER_SHOW_NOT_BOOKMARKED
        }
        screenModelScope.launchNonCancellable {
            setNovelChapterFlags.awaitSetBookmarkFilter(novel, flag)
        }
    }

    fun setDisplayMode(mode: Long) {
        val novel = successState?.novel ?: return
        screenModelScope.launchNonCancellable {
            setNovelChapterFlags.awaitSetDisplayMode(novel, mode)
        }
    }

    fun setSorting(sort: Long) {
        val novel = successState?.novel ?: return
        screenModelScope.launchNonCancellable {
            setNovelChapterFlags.awaitSetSortingModeOrFlipOrder(novel, sort)
        }
    }

    fun setAsDefault(applyToExisting: Boolean) {
        val novel = successState?.novel ?: return
        screenModelScope.launchNonCancellable {
            libraryPreferences.setNovelChapterSettingsDefault(novel)
            if (applyToExisting) {
                setNovelDefaultChapterFlags.awaitAll()
            }
        }
    }

    fun resetToDefaultSettings() {
        val novel = successState?.novel ?: return
        screenModelScope.launchNonCancellable {
            setNovelDefaultChapterFlags.await(novel)
        }
    }

    fun bookmarkChapters(chapters: List<NovelChapter>, bookmarked: Boolean) {
        screenModelScope.launchNonCancellable {
            updateNovelChapter.awaitAll(
                chapters.map {
                    NovelChapterUpdate(id = it.id, bookmark = bookmarked)
                },
            )
        }
    }

    fun markChaptersRead(chapters: List<NovelChapter>, read: Boolean) {
        screenModelScope.launchNonCancellable {
            setReadStatus.await(read, *chapters.toTypedArray())
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
        downloadManager.deleteNovel(state.novel, state.source)
    }

    /**
     * Anna's Archive "Download book" action.
     * Triggers a chapter list refresh which downloads the EPUB and splits it
     * into chapters, then queues all chapters for download. The UI shows a
     * progress indicator via [State.Success.isRefreshingData] while the EPUB
     * is being downloaded and split.
     */
    fun downloadBook() {
        val state = successState ?: return
        screenModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            try {
                // Canonical books fetch via the resolved carrier entry —
                // that's the AA EPUB download + split path.
                fetchChaptersFromSource(manualFetch = true, bookCarrier = state.bookCarrier)
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "downloadBook: fetchChaptersFromSource failed" }
            } finally {
                updateSuccessState { it.copy(isRefreshingData = false) }
            }
            // After chapters are fetched (EPUB downloaded + split), queue all
            // chapters for download so their content is cached for offline reading.
            val chaptersToDownload = successState?.chapters?.map { it.chapter } ?: emptyList()
            if (chaptersToDownload.isNotEmpty()) {
                downloadManager.downloadChapters(state.novel, chaptersToDownload)
            }
        }
    }

    /**
     * Anna's Archive "Delete book" action.
     * Deletes all downloaded chapter content AND removes all chapters from
     * the database, bringing back the "Download book" button.
     */
    fun deleteBook() {
        val state = successState ?: return
        screenModelScope.launchNonCancellable {
            try {
                // Delete downloaded content
                val chapters = state.chapters.map { it.chapter }
                if (chapters.isNotEmpty()) {
                    downloadManager.deleteChapters(chapters, state.novel, state.source)
                }
                // Remove all chapters from the database
                novelChapterRepository.removeChaptersWithIds(chapters.map { it.id })
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    fun deleteNonBookmarkedDownloads() {
        val state = successState ?: return
        val chapters = state.chapters.filter { !it.chapter.bookmark }.map { it.chapter }
        if (chapters.isNotEmpty()) {
            screenModelScope.launchNonCancellable {
                downloadManager.deleteChapters(chapters, state.novel, state.source)
            }
        }
    }

    fun deleteReadDownloads() {
        val state = successState ?: return
        val chapters = state.chapters.filter { it.chapter.read }.map { it.chapter }
        if (chapters.isNotEmpty()) {
            screenModelScope.launchNonCancellable {
                downloadManager.deleteChapters(chapters, state.novel, state.source)
            }
        }
    }

    fun refreshTracking() {
        screenModelScope.launchIO {
            refreshTrackers()
        }
    }

    private suspend fun refreshTrackers(
        refreshTracks: RefreshNovelTracks = Injekt.get(),
    ) {
        refreshTracks.await(novelId)
            .filter { it.first != null }
            .forEach { (track, e) ->
                logcat(LogPriority.ERROR, e) {
                    "Failed to refresh track data novelId=$novelId for service ${track!!.name}"
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

    // Track sheet - start

    private fun observeTrackers() {
        val novel = successState?.novel ?: return

        screenModelScope.launchIO {
            combine(
                getTracks.subscribe(novel.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInNovelTrackersFlow(),
            ) { novelTracks, loggedInTrackers ->
                val supportedTrackerIds = loggedInTrackers.map { it.id }.toHashSet()
                novelTracks.filter { it.trackerId in supportedTrackerIds }.size to loggedInTrackers.isNotEmpty()
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

    fun showTrackSheet() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    fun showCommentsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.Comments) }
    }

    val commentsEnabled: Boolean
        get() = basePreferences.commentsEnabled().get()

    // Track sheet - end

    // ---------- Find Content Provider (17-02) ----------

    /** Fetch the work's carrier matrix into state (canonical entries only). */
    private fun loadCarriers(workId: String) {
        screenModelScope.launchIO {
            val matrix = workCarriers.forWork(
                workId,
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_BOOKS,
            )
            updateSuccessState { it.copy(carrierMatrix = matrix) }
        }
    }

    /** Active collector of the finder's search flow — cancelled when a
     *  new search attaches so stale collectors can't overwrite picks. */
    private var providerSearchJob: kotlinx.coroutines.Job? = null

    /** Open the Find Provider sheet and stream per-extension search
     *  results into state (canonical entries only). The finder owns the
     *  search job — closing the sheet never cancels it; reopening within
     *  5 min reattaches to the cached/live flow instead of restarting. */
    fun showFindProviderDialog() {
        val novel = successState?.novel ?: return
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromNovelUrl(novel.url) ?: return
        updateSuccessState {
            it.copy(
                dialog = Dialog.FindProvider,
                providerSearch = (it.providerSearch
                    ?: eu.kanade.tachiyomi.data.mikodb.ProviderFinder.Search())
                    .copy(recommendations = providerRecommendations()),
            )
        }
        providerSearchJob?.cancel()
        providerSearchJob = screenModelScope.launchIO {
            val flow = providerFinder.search(
                workId,
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_BOOKS,
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
     *  refresh carriers + chapters so the entry flips back to the
     *  comments button. Canonical rows have no real source —
     *  `fetchAllFromSource` is meaningless here; the canon map's
     *  process cache must be invalidated and the merged sequence
     *  re-materialized instead, or the chapter area spins until the
     *  screen is re-entered. */
    fun confirmProviderPicks() {
        val search = successState?.providerSearch ?: return
        val feeds = search.rows.filter { it.pkgName in search.confirmed }
            .mapNotNull { it.feed }
        val novel = successState?.novel ?: return
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromNovelUrl(novel.url) ?: return
        screenModelScope.launchIO {
            providerFinder.mint(workId, feeds)
            providerFinder.invalidate(
                workId,
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_BOOKS,
            )
            workCarriers.invalidate(workId)
            Injekt.get<eu.kanade.tachiyomi.data.mikodb.CanonicalChapterMap>()
                .invalidate(workId)
            dismissDialog()
            updateSuccessState { it.copy(providerSearch = null) }
            loadCarriers(workId)
            Injekt.get<eu.kanade.tachiyomi.data.mikodb.CanonicalChapterSync>()
                .syncNovel(novel, workId)
        }
    }

    /** Find-Providers "Restore" on a previously-removed row (20-06):
     *  clears the local carrier exclusion and retracts the remove
     *  signal; does not re-mint — the binding returns only if the
     *  server still holds it. */
    fun restoreExcludedCarrier(pkgName: String) {
        val novel = successState?.novel ?: return
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromNovelUrl(novel.url) ?: return
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

    /**
     * Resolve the carrier entry a canonical book maps to. A minted
     * binding wins ([WorkCarriers.Carrier.providerRef] is the carrier's
     * internal entry url); otherwise the finder's live search IS the
     * resolution — Anna's Archive is the only book carrier, so the
     * first FOUND feed supplies the download url and feeds the entry's
     * description/genre/cover (books come through AA, not OpenLibrary).
     */
    private fun resolveBookCarrier(workId: String) {
        screenModelScope.launchIO {
            val bound = workCarriers.forWork(
                workId,
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_BOOKS,
            ).readable.firstOrNull { it.providerRef.isNotBlank() && it.sourceIds.isNotEmpty() }
            if (bound != null) {
                applyBookCarrier(bound.sourceIds.first(), bound.providerRef, feed = null)
                return@launchIO
            }
            val flow = providerFinder.search(
                workId,
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_BOOKS,
            ) ?: return@launchIO
            val resolved = flow.first { s ->
                s.rows.any {
                    it.status == eu.kanade.tachiyomi.data.mikodb.ProviderFinder.Status.FOUND
                } || !s.searching
            }
            val feed = resolved.rows.firstOrNull {
                it.status == eu.kanade.tachiyomi.data.mikodb.ProviderFinder.Status.FOUND
            }?.feed ?: return@launchIO
            applyBookCarrier(feed.sourceId, feed.url, feed)
        }
    }

    /** Store the resolved carrier and pour its details into the local
     *  row — description/genre/cover the canonical work doesn't carry. */
    private suspend fun applyBookCarrier(
        sourceId: Long,
        url: String,
        feed: eu.kanade.tachiyomi.data.mikodb.CanonicalFeedCollector.Feed?,
    ) {
        updateSuccessState { it.copy(bookCarrier = BookCarrier(sourceId, url)) }
        val state = successState ?: return
        val details = feed?.details ?: return
        if (details.isEmpty) return
        updateNovel.awaitUpdateFromSource(
            localNovel = state.novel,
            remoteTitle = state.novel.title,
            remoteAuthor = details.author ?: state.novel.author,
            remoteArtist = null,
            remoteDescription = details.description,
            remoteGenre = details.genre,
            remoteThumbnailUrl = details.thumbnailUrl ?: state.novel.thumbnailUrl,
            remoteStatus = details.status?.toLong() ?: state.novel.status,
            remoteUpdateStrategy = eu.kanade.tachiyomi.novelsource.model.NovelUpdateStrategy.ALWAYS_UPDATE,
            manualFetch = true,
        )
        getNovelAndChapters.awaitNovel(state.novel.id).let { refreshed ->
            updateSuccessState { it.copy(novel = refreshed) }
        }
    }

    /** Install a recommended extension from the repo — progress feeds
     *  the sheet's install buttons. */
    fun installRecommendedProvider(pkgName: String) {
        val ext = novelExtensionManager.availableExtensionsFlow.value
            .firstOrNull { it.pkgName == pkgName } ?: return
        screenModelScope.launchIO {
            novelExtensionManager.installExtension(ext).collect { step ->
                updateSuccessState {
                    it.copy(carrierInstallSteps = it.carrierInstallSteps + (pkgName to step))
                }
            }
        }
    }

    /** Available-but-uninstalled novel extensions ranked by the surveyed
     *  S/A/B/C/D tier — the "likely to have it" list. */
    private fun providerRecommendations(): List<eu.kanade.tachiyomi.data.mikodb.ProviderFinder.RecommendedExtension> {
        val installed = novelExtensionManager.installedExtensionsFlow.value
            .map { it.pkgName }.toSet()
        return novelExtensionManager.availableExtensionsFlow.value
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

    fun updateNovelMetadata(
        title: String,
        author: String,
        description: String,
        status: Long,
        tags: List<String>,
    ) {
        val novel = successState?.novel ?: return
        screenModelScope.launchNonCancellable {
            updateNovel.await(
                tachiyomi.domain.entries.novel.model.NovelUpdate(
                    id = novel.id,
                    title = title.takeIf { it.isNotBlank() },
                    author = author.takeIf { it.isNotBlank() },
                    description = description.takeIf { it.isNotBlank() },
                    status = status,
                    genre = tags.takeIf { it.isNotEmpty() },
                ),
            )
        }
    }

    fun markPreviousChapterRead(chapter: NovelChapter) {
        markPreviousChaptersRead(chapter, true)
    }

    fun markPreviousChaptersRead(chapter: NovelChapter, read: Boolean) {
        val state = successState ?: return
        val novel = state.novel
        val chapters = state.processedChapters
        // Chapter-group pills (Book/Volume…): scope "previous" to the
        // pointer's own group — marking must not leak across groups.
        val pointerGroup = eu.kanade.tachiyomi.util.chapter.ChapterNameGroup.parse(chapter.name)
        val scoped = if (pointerGroup != null) {
            chapters.filter {
                eu.kanade.tachiyomi.util.chapter.ChapterNameGroup.parse(it.chapter.name) == pointerGroup
            }
        } else {
            chapters
        }
        val prevChapters = if (novel.sortDescending()) scoped.asReversed() else scoped
        val chapterIndex = prevChapters.indexOfFirst { it.chapter.id == chapter.id }
        if (chapterIndex == -1) return
        val chaptersToMark = prevChapters.take(chapterIndex).map { it.chapter }
        if (chaptersToMark.isNotEmpty()) {
            markChaptersRead(chaptersToMark, read)
        }
    }

    fun chapterSwipe(item: NovelChapterList.Item, action: LibraryPreferences.ChapterSwipeAction) {
        val chapter = item.chapter
        when (action) {
            LibraryPreferences.ChapterSwipeAction.ToggleRead -> {
                markChaptersRead(listOf(chapter), !chapter.read)
            }
            LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> {
                bookmarkChapters(listOf(chapter), !chapter.bookmark)
            }
            LibraryPreferences.ChapterSwipeAction.Download -> {
                val downloadAction: NovelChapterDownloadAction = when (item.downloadState) {
                    NovelDownload.State.ERROR,
                    NovelDownload.State.NOT_DOWNLOADED,
                    -> NovelChapterDownloadAction.START_NOW
                    NovelDownload.State.QUEUE,
                    NovelDownload.State.DOWNLOADING,
                    -> NovelChapterDownloadAction.CANCEL
                    NovelDownload.State.DOWNLOADED -> NovelChapterDownloadAction.DELETE
                }
                runChapterDownloadActions(
                    items = listOf(item),
                    action = downloadAction,
                )
            }
            LibraryPreferences.ChapterSwipeAction.Disabled -> {}
        }
    }

    fun toggleSelection(
        item: NovelChapterList.Item,
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
                selectedChapterIds.addOrRemove(item.chapter.id, selected)

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1..<selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1)..<selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            range = IntRange.EMPTY
                        }
                        range.forEach {
                            val inbetweenItem = get(it)
                            if (!inbetweenItem.selected) {
                                selectedChapterIds.add(inbetweenItem.chapter.id)
                                set(it, inbetweenItem.copy(selected = true))
                            }
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
                selectedChapterIds.addOrRemove(it.chapter.id, selected)
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
                selectedChapterIds.addOrRemove(it.chapter.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(chapters = newChapters)
        }
    }

    fun getNextUnreadChapter(): NovelChapter? {
        return successState?.processedChapters
            ?.firstOrNull { !it.chapter.read }
            ?.chapter
    }

    fun hideChapter(chapter: NovelChapter) {
        chapterHideManager.hideChapter(novelId, chapter.id)
        refreshHiddenChapters()
    }

    fun unhideAllChapters() {
        chapterHideManager.unhideAllChapters(novelId)
        refreshHiddenChapters()
    }

    fun hasHiddenChapters(): Boolean {
        return chapterHideManager.hasHiddenChapters(novelId)
    }

    fun markChaptersAsSeen() {
        newChapterTracker.markAsSeen(novelId)
    }

    private fun refreshHiddenChapters() {
        val hiddenIds = chapterHideManager.getHiddenChapterIds(novelId)
        updateSuccessState { it.copy(hiddenChapterIds = hiddenIds) }
    }

    private fun hasDownloads(): Boolean {
        val novel = successState?.novel ?: return false
        return downloadManager.getDownloadCount(novel) > 0
    }

    private fun deleteDownloads() {
        val state = successState ?: return
        downloadManager.deleteNovel(state.novel, state.source)
    }

    private fun observeDownloads() {
        screenModelScope.launchIO {
            downloadManager.statusFlow()
                .filter { it.novel.id == successState?.novel?.id }
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
                .filter { it.novel.id == successState?.novel?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }
    }

    private fun updateDownloadState(download: NovelDownload) {
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

    /**
     * Stream render-queue state into the chapter rows (D-13): status changes
     * (queued -> rendering -> done/error, preempt flips) and progress ticks.
     * Queue *membership* changes (enqueue, cancel, preempt reorder) don't emit
     * here — the chapter-list rebuild combine on `queueState` re-seeds those.
     */
    private fun observeAudioRenders() {
        screenModelScope.launchIO {
            audioRenderManager.statusFlow()
                .filter { it.novel.id == successState?.novel?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateAudioState(it)
                    }
                }
        }

        screenModelScope.launchIO {
            audioRenderManager.progressFlow()
                .filter { it.novel.id == successState?.novel?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateAudioState(it)
                    }
                }
        }
    }

    private fun updateAudioState(render: ChapterRender) {
        updateSuccessState { successState ->
            val modifiedIndex = successState.chapters.indexOfFirst { it.id == render.chapter.id }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newChapters = successState.chapters.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    .copy(
                        audioState = render.status.toAudioState(),
                        audioProgress = render.progress,
                        audioError = render.errorMessage,
                    )
                add(modifiedIndex, item)
            }
            successState.copy(chapters = newChapters)
        }
    }

    /**
     * Explicit idle reset for actions whose queue effect emits no status
     * event (cancel removes the item; delete removes the `.m4a`) — mirrors
     * [updateDownloadState]'s manual NOT_DOWNLOADED reset in [cancelDownload].
     */
    private fun clearAudioState(chapterIds: List<Long>) {
        updateSuccessState { successState ->
            successState.copy(
                chapters = successState.chapters.map { item ->
                    if (item.chapter.id in chapterIds) {
                        item.copy(
                            audioState = NovelChapterAudioState.NOT_RENDERED,
                            audioProgress = 0,
                            audioError = null,
                        )
                    } else {
                        item
                    }
                },
            )
        }
    }

    private fun getUnreadChapters(): List<NovelChapter> {
        val novel = successState?.novel ?: return emptyList()
        // Filter by unread AND not already downloaded — matches manga behavior.
        return allChapters.orEmpty()
            .filter { !it.chapter.read && it.downloadState == NovelDownload.State.NOT_DOWNLOADED }
            .map { it.chapter }
    }

    private fun getUnreadChaptersSorted(): List<NovelChapter> {
        // Always sort by chapter number ascending so "next 5" downloads
        // the next 5 chapters from lowest to highest chapter number,
        // regardless of the user's display sort preference.
        return getUnreadChapters().sortedBy { it.chapterNumber }
    }

    private fun startDownload(
        chapters: List<NovelChapter>,
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
        items: List<NovelChapterList.Item>,
        action: NovelChapterDownloadAction,
        forcedCarrierPkg: String? = null,
    ) {
        when (action) {
            NovelChapterDownloadAction.START -> {
                startDownload(items.map { it.chapter }, false, forcedCarrierPkg)
                if (items.any { it.downloadState == NovelDownload.State.ERROR }) {
                    downloadManager.startDownloads()
                }
            }
            NovelChapterDownloadAction.START_NOW -> {
                val chapter = items.singleOrNull()?.chapter ?: return
                startDownload(listOf(chapter), true, forcedCarrierPkg)
            }
            NovelChapterDownloadAction.CANCEL -> {
                val chapterId = items.singleOrNull()?.id ?: return
                cancelDownload(chapterId)
            }
            NovelChapterDownloadAction.DELETE -> {
                deleteChapters(items.map { it.chapter })
            }
        }
    }

    /**
     * Dispatch for the chapter-row audio icon (D-07/D-08/D-14). PLAY is
     * intercepted at the screen call site — it navigates to the reader with
     * `autoStartTts`, it never reaches this function.
     */
    fun runChapterAudioActions(
        items: List<NovelChapterList.Item>,
        action: NovelChapterAudioAction,
    ) {
        val state = successState ?: return
        when (action) {
            NovelChapterAudioAction.RENDER -> {
                // D-13: enqueue while another render runs shows NO snackbar —
                // the icon's flip to QUEUED is the feedback. Only a
                // synchronous enqueue failure surfaces via snackbar.
                items.forEach { item ->
                    try {
                        audioRenderManager.enqueueRender(state.novel, item.chapter, state.source)
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e)
                        screenModelScope.launch {
                            snackbarHostState.showSnackbar(
                                message = with(context) { e.formattedMessage },
                            )
                        }
                    }
                }
            }
            NovelChapterAudioAction.RENDER_NOW -> {
                val chapterId = items.singleOrNull()?.id ?: return
                audioRenderManager.startRenderNow(chapterId)
            }
            NovelChapterAudioAction.CANCEL -> {
                val chapterId = items.singleOrNull()?.id ?: return
                audioRenderManager.cancelRender(chapterId)
                clearAudioState(listOf(chapterId))
            }
            NovelChapterAudioAction.DELETE -> {
                // D-11/D-08: immediate, no confirmation — deletes only the
                // .m4a; the chapter stays downloaded and the icon reverts
                // to idle.
                screenModelScope.launchNonCancellable {
                    try {
                        items.forEach { item ->
                            audioRenderManager.deleteAudio(state.novel, item.chapter, state.source)
                            // PB-21: prune the saved position and stop playback
                            // if this chapter's file was under the playhead.
                            audiobookPlayer.onAudioDeleted(item.chapter.id)
                        }
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e)
                    }
                    clearAudioState(items.map { it.chapter.id })
                }
            }
            NovelChapterAudioAction.RE_RENDER -> {
                // Fresh render with current voice/speed prefs (D-08): drop the
                // old file + scratch first, then enqueue like RENDER.
                screenModelScope.launchNonCancellable {
                    items.forEach { item ->
                        try {
                            audioRenderManager.deleteAudio(state.novel, item.chapter, state.source)
                            audiobookPlayer.onAudioDeleted(item.chapter.id)
                            audioRenderManager.enqueueRender(state.novel, item.chapter, state.source)
                        } catch (e: Throwable) {
                            logcat(LogPriority.ERROR, e)
                            screenModelScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = with(context) { e.formattedMessage },
                                )
                            }
                        }
                    }
                }
            }
            NovelChapterAudioAction.SHOW_ERROR -> {
                // The row icon carries no text — surface the failure reason
                // the notification already logged, with an inline retry.
                val item = items.singleOrNull() ?: return
                screenModelScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = item.audioError
                            ?: context.stringResource(AYMR.strings.novel_audio_render_failed),
                        actionLabel = context.stringResource(MR.strings.action_retry),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        try {
                            audioRenderManager.enqueueRender(state.novel, item.chapter, state.source)
                        } catch (e: Throwable) {
                            logcat(LogPriority.ERROR, e)
                            snackbarHostState.showSnackbar(
                                message = with(context) { e.formattedMessage },
                            )
                        }
                    }
                }
            }
            NovelChapterAudioAction.PLAY -> Unit // handled by the screen (navigation)
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
        updateDownloadState(activeDownload.apply { status = NovelDownload.State.NOT_DOWNLOADED })
    }

    private suspend fun downloadChapters(chapters: List<NovelChapter>, forcedCarrierPkg: String? = null) {
        val novel = successState?.novel ?: return
        downloadManager.downloadChapters(novel, chapters, forcedCarrierPkg = forcedCarrierPkg)
        toggleAllSelection(false)
    }

    fun deleteChapters(chapters: List<NovelChapter>) {
        screenModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    downloadManager.deleteChapters(
                        chapters,
                        state.novel,
                        state.source,
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private suspend fun List<NovelChapter>.toNovelChapterListItems(novel: Novel): List<NovelChapterList.Item> {
        // Seed audio state once per list build: one SAF `audio/` listing for
        // the novel + the in-memory render queue — never a per-row SAF hit on
        // scroll (T-05-04-02). An unreadable audio dir seeds all-idle rather
        // than blanking the icon (UI-SPEC partial-state lift).
        val source = sourceManager.getOrStub(novel.source)
        val renderedAudioNames = runCatching {
            audioRenderManager.renderedAudioFileNames(novel, source)
        }.getOrElse {
            logcat(LogPriority.WARN, it) { "audio dir listing failed for '${novel.title}'" }
            emptySet()
        }
        val audioQueue = audioRenderManager.queueState.value

        // Canon rows keep downloads under the serving carrier's dir —
        // "downloaded" = exists under ANY bound carrier.
        val isCanonical = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.isCanonicalUrl(novel.url)
        val carrierSourceIds = if (isCanonical) {
            eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.workIdFromNovelUrl(novel.url)
                ?.let { workId ->
                    runCatching {
                        workCarriers.forWork(
                            workId,
                            eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_BOOKS,
                        ).carriers
                    }.getOrNull()
                }
                ?.flatMap { it.sourceIds }
                .orEmpty()
        } else {
            emptyList()
        }

        return map { chapter ->
            val activeDownload = downloadManager.getQueuedDownloadOrNull(chapter.id)
            val downloaded = if (isCanonical) {
                carrierSourceIds.any {
                    downloadManager.isChapterDownloaded(
                        chapter.name,
                        chapter.scanlator,
                        novel.title,
                        it,
                    )
                }
            } else {
                downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    novel.title,
                    novel.source,
                )
            }
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> NovelDownload.State.DOWNLOADED
                else -> NovelDownload.State.NOT_DOWNLOADED
            }

            val activeRender = audioQueue.find { it.chapter.id == chapter.id }
            val audioState = when {
                activeRender != null -> activeRender.status.toAudioState()
                downloadProvider.getAudioFileName(chapter.name, chapter.scanlator) in renderedAudioNames ->
                    NovelChapterAudioState.RENDERED
                else -> NovelChapterAudioState.NOT_RENDERED
            }

            val readProgress = if (!chapter.read && chapter.lastCharRead > 0 && chapter.totalCharCount > 0) {
                // Decode character position from packed Long (new format
                // packs charPos + itemIndex + pixelOffset into lastCharRead).
                val charPos = eu.kanade.tachiyomi.ui.reader.novel.NovelScrollPositionCodec
                    .decodeCharacterPosition(chapter.lastCharRead)
                ((charPos.toFloat() / chapter.totalCharCount) * 100)
                    .toInt()
                    .coerceIn(1, 99)
            } else {
                null
            }

            NovelChapterList.Item(
                chapter = chapter,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                audioState = audioState,
                audioProgress = activeRender?.progress ?: 0,
                audioError = activeRender?.errorMessage,
                selected = chapter.id in selectedChapterIds,
                readProgress = readProgress,
                isNew = !chapter.read && newChapterTracker.isNew(novel.id, chapter.dateFetch),
            )
        }
    }

    fun showDeleteChapterDialog(chapters: List<NovelChapter>) {
        updateSuccessState { it.copy(dialog = Dialog.DeleteChapters(chapters)) }
    }

    fun showSettingsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun showFullCoverDialog() {
        updateSuccessState { it.copy(dialog = Dialog.FullCover) }
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    fun showMigrateDialog(duplicate: Novel) {
        val novel = successState?.novel ?: return
        updateSuccessState { it.copy(dialog = Dialog.Migrate(newNovel = novel, oldNovel = duplicate)) }
    }

    sealed interface Dialog {
        data class ChangeCollection(
            val novel: Novel,
            val initialSelection: ImmutableList<CheckboxState<Collection>>,
        ) : Dialog
        data class DeleteChapters(val chapters: List<NovelChapter>) : Dialog
        data class DuplicateNovel(val novel: Novel, val duplicate: Novel) : Dialog
        data class Migrate(val newNovel: Novel, val oldNovel: Novel) : Dialog
        data object SettingsSheet : Dialog
        data object FullCover : Dialog
        data object TrackSheet : Dialog
        data object Comments : Dialog
        /** Find Content Provider — zero-bound canonical entry (17-02). */
        data object FindProvider : Dialog
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val novel: Novel,
            val source: NovelSource,
            val isFromSource: Boolean,
            val chapters: List<NovelChapterList.Item>,
            val hiddenChapterIds: Set<Long> = emptySet(),
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
            /** Server bindings ∩ installed/enabled extensions (17-02);
             *  null until fetched — treat as unknown, never "no carriers". */
            val carrierMatrix: eu.kanade.tachiyomi.data.mikodb.WorkCarriers.Matrix? = null,
            /** pkgName → install progress for recommended extensions. */
            val carrierInstallSteps: Map<String, eu.kanade.tachiyomi.extension.InstallStep> = emptyMap(),
            /** Find Content Provider sheet state (17-02). */
            val providerSearch: eu.kanade.tachiyomi.data.mikodb.ProviderFinder.Search? = null,
            /** Canonical work kind is `book` — the entry resolves to an
             *  Anna's Archive download instead of a chapter feed. */
            val isBookWork: Boolean = false,
            /** Resolved carrier entry (source + internal url) the
             *  Download Book action drives on canonical books. */
            val bookCarrier: BookCarrier? = null,
        ) : State {
            val processedChapters by lazy {
                chapters.applyFilters(novel).toList()
            }

            val isAnySelected by lazy {
                chapters.fastAny { it.selected }
            }

            val filterActive: Boolean
                get() = novel.chaptersFiltered()

            val nextContinueChapter: NovelChapterList.Item?
                get() = chapters
                    .filter { !it.chapter.read && it.chapter.id !in hiddenChapterIds }
                    .minByOrNull { it.chapter.chapterNumber }

            val allChaptersRead: Boolean
                get() = chapters.isNotEmpty() && chapters.all { it.chapter.read }

            /**
             * True if this source provides whole books (e.g. Anna's Archive).
             * Book sources have a different UX: a "Download book" button replaces
             * the chapter list when empty, per-chapter download indicators are
             * hidden, and a "Delete book" action replaces the download dropdown.
             */
            val isBookSource: Boolean
                get() = source.id == 6400L || isBookWork

            private fun List<NovelChapterList.Item>.applyFilters(novel: Novel): Sequence<NovelChapterList.Item> {
                val unreadFilter = novel.unreadFilter
                val bookmarkedFilter = novel.bookmarkedFilter
                val hiddenIds = hiddenChapterIds
                return asSequence()
                    .filter { (chapter) -> chapter.id !in hiddenIds }
                    .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
                    .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
                    .sortedWith { (chapter1), (chapter2) ->
                        getNovelChapterSort(novel).invoke(chapter1, chapter2)
                    }
            }
        }
    }

    private companion object {
        /** Warm-start cache — the last [State.Success] emitted per novel,
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

/** A resolved carrier entry for a canonical book — the extension's
 *  source id + its internal entry url (drives the AA EPUB path). */
data class BookCarrier(val sourceId: Long, val url: String)

@Immutable
sealed class NovelChapterList {
    @Immutable
    data class Item(
        val chapter: NovelChapter,
        val selected: Boolean = false,
        val downloadState: NovelDownload.State = NovelDownload.State.NOT_DOWNLOADED,
        val downloadProgress: Int = 0,
        val audioState: NovelChapterAudioState = NovelChapterAudioState.NOT_RENDERED,
        val audioProgress: Int = 0,
        val audioError: String? = null,
        val readProgress: Int? = null,
        val isNew: Boolean = false,
    ) : NovelChapterList() {
        val id = chapter.id
    }
}

private fun ChapterRender.State.toAudioState(): NovelChapterAudioState = when (this) {
    ChapterRender.State.QUEUED -> NovelChapterAudioState.QUEUED
    ChapterRender.State.RENDERING -> NovelChapterAudioState.RENDERING
    ChapterRender.State.DONE -> NovelChapterAudioState.RENDERED
    ChapterRender.State.ERROR -> NovelChapterAudioState.ERROR
}

/**
 * Determine whether the update interval badge should be shown.
 *
 * Returns false when:
 * - The series status is COMPLETED
 * - The most recent chapter upload date is more than 2 months ago (stale)
 */
private fun shouldShowInterval(status: Long, chapters: List<*>): Boolean {
    if (status == eu.kanade.tachiyomi.novelsource.model.SNovel.COMPLETED.toLong()) return false

    val twoMonthsMs = 60L * 24L * 60L * 60L * 1000L
    val now = System.currentTimeMillis()
    val latestDate = chapters.maxOfOrNull { item ->
        when (item) {
            is tachiyomi.domain.items.chapter.model.NovelChapter -> maxOf(item.dateUpload, item.dateFetch)
            else -> 0L
        }
    } ?: return false

    if (latestDate == 0L) return true
    return (now - latestDate) < twoMonthsMs
}
