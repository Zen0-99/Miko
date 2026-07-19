package eu.kanade.tachiyomi.ui.entries.novel

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.entries.components.adjustForTheme
import eu.kanade.tachiyomi.util.novel.NovelCoverMetadata
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.addOrRemove
import eu.kanade.domain.entries.novel.model.chaptersFiltered
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.items.chapter.interactor.SetNovelReadStatus
import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.domain.items.chapter.interactor.UpdateNovelChapter
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.novel.components.NovelChapterDownloadAction
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
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
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.lang.chop
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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
import tachiyomi.domain.entries.applyFilter
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetDuplicateLibraryNovel
import tachiyomi.domain.entries.novel.interactor.GetLinkedNovels
import tachiyomi.domain.entries.novel.interactor.GetNovelLinkedId
import tachiyomi.domain.entries.novel.interactor.GetNovelFavorites
import tachiyomi.domain.entries.novel.interactor.LinkNovels
import tachiyomi.domain.entries.novel.interactor.UnlinkNovel
import tachiyomi.domain.entries.novel.interactor.MakeLinkedPrimary
import tachiyomi.domain.entries.novel.interactor.MergeLinkedNovelChapters
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.interactor.SetNovelChapterFlags
import tachiyomi.domain.entries.novel.interactor.SetNovelDefaultChapterFlags
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelLink
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.chapter.interactor.GetNovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapterUpdate
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository
import tachiyomi.domain.items.chapter.service.getNovelChapterSort
import tachiyomi.domain.library.service.LibraryPreferences
import eu.kanade.tachiyomi.novelsource.NovelSource
import tachiyomi.domain.source.novel.service.NovelSourceManager
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
    private val getNovelAndChapters: GetNovelWithChapters = Injekt.get(),
    private val getDuplicateLibraryNovel: GetDuplicateLibraryNovel = Injekt.get(),
    private val setNovelChapterFlags: SetNovelChapterFlags = Injekt.get(),
    private val setNovelDefaultChapterFlags: SetNovelDefaultChapterFlags = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val updateNovelChapter: UpdateNovelChapter = Injekt.get(),
    private val setReadStatus: SetNovelReadStatus = Injekt.get(),
    private val getCategories: GetNovelCategories = Injekt.get(),
    private val setNovelCategories: SetNovelCategories = Injekt.get(),
    private val novelRepository: NovelRepository = Injekt.get(),
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val getNovelChapter: GetNovelChapter = Injekt.get(),
    private val downloadManager: NovelDownloadManager = Injekt.get(),
    private val downloadCache: NovelDownloadCache = Injekt.get(),
    private val fetchInterval: tachiyomi.domain.entries.novel.interactor.NovelFetchInterval = Injekt.get(),
    private val getLinkedNovels: GetLinkedNovels = Injekt.get(),
    private val getNovelLinkedId: GetNovelLinkedId = Injekt.get(),
    private val getNovelFavorites: GetNovelFavorites = Injekt.get(),
    private val linkNovelsInteractor: LinkNovels = Injekt.get(),
    private val unlinkNovelInteractor: UnlinkNovel = Injekt.get(),
    private val makeLinkedPrimary: MakeLinkedPrimary = Injekt.get(),
    private val mergeLinkedNovelChapters: MergeLinkedNovelChapters = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val suggestionCoordinator: SuggestionCoordinator = Injekt.get(),
    private val searchFallbackEngine: NovelSearchFallbackEngine = Injekt.get(),
    private val relatedSuggestionCoordinator: NovelRelatedSuggestionCoordinator = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<NovelScreenModel.State>(State.Loading) {

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

    internal var isFromChangeCategory: Boolean = false

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
                                logcat(LogPriority.DEBUG) { "[NovelScreenModel] Native search fallback failed: ${e.message}" }
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
                                if (relatedOutcome is NovelFallbackOutcome.Success && relatedOutcome.items.isNotEmpty()) {
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
                                logcat(LogPriority.DEBUG) { "[NovelScreenModel] Related novels fetch failed: ${e.message}" }
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
        screenModelScope.launchIO {
            combine(
                getNovelAndChapters.subscribe(novelId).distinctUntilChanged(),
                downloadCache.changes,
                downloadManager.queueState,
            ) { novelAndChapters, _, _ -> novelAndChapters }
                .flowWithLifecycle(lifecycle)
                .collectLatest { (novel, chapters) ->
                    // Merge chapters from linked sources (dedup by chapter number)
                    val mergedChapters = mergeWithLinkedSources(novel, chapters)
                    updateSuccessState {
                        it.copy(
                            novel = novel,
                            chapters = mergedChapters.toNovelChapterListItems(novel),
                        )
                    }
                }
        }

        observeDownloads()

        screenModelScope.launchIO {
            val novel = getNovelAndChapters.awaitNovel(novelId)
            val chapters = getNovelAndChapters.awaitChapters(novelId)
                .toNovelChapterListItems(novel)

            val needRefreshInfo = !novel.initialized
            val needRefreshChapter = chapters.isEmpty()

            if (!novel.favorite) {
                setNovelDefaultChapterFlags.await(novel)
            }

            // Load cached accent color synchronously to avoid flash on open
            val isDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val cachedAccentColor = NovelCoverMetadata.getBaseColor(novel.id)?.let {
                Color(adjustForTheme(it, isDark))
            }

            val intervalDays = fetchInterval.calculateInterval(
                chapters.map { it.chapter },
            )

            mutableState.update {
                State.Success(
                    novel = novel,
                    source = Injekt.get<NovelSourceManager>().getOrStub(novel.source),
                    isFromSource = isFromSource,
                    chapters = chapters,
                    isRefreshingData = needRefreshInfo || needRefreshChapter,
                    dialog = null,
                    accentColor = cachedAccentColor,
                    intervalDays = intervalDays,
                )
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
            logcat(LogPriority.ERROR, e)
            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = with(context) { e.formattedMessage })
            }
        }
    }

    private suspend fun fetchChaptersFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        var primaryError: Throwable? = null
        var primarySucceeded = false

        // --- Primary source fetch ---
        try {
            withIOContext {
                val source = state.source as? NovelCatalogueSource ?: return@withIOContext
                val remoteChapters = source.getChapterList(state.novel.toSNovel())

                if (remoteChapters.isEmpty()) {
                    throw tachiyomi.domain.items.chapter.model.NoChaptersException()
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

                if (toAdd.isNotEmpty()) {
                    novelChapterRepository.addAllNovelChapters(toAdd)
                }
                if (toUpdate.isNotEmpty()) {
                    updateNovelChapter.awaitAll(toUpdate.map {
                        NovelChapterUpdate(
                            id = it.id,
                            name = it.name,
                            url = it.url,
                            chapterNumber = it.chapterNumber,
                            dateFetch = it.dateFetch,
                            dateUpload = it.dateUpload,
                            sourceOrder = it.sourceOrder,
                        )
                    })
                }
                primarySucceeded = true
            }
        } catch (e: Throwable) {
            primaryError = e
        }

        // --- Linked source refresh (always runs, like Mohyeong) ---
        val linkedNew = try {
            withIOContext { refreshLinkedSourceChapters(manualFetch) }
        } catch (t: Throwable) {
            logcat(LogPriority.ERROR, t) { "Linked-source refresh failed" }
            emptyList()
        }

        // --- Fallback: if primary failed but linked sources delivered chapters,
        //     surface a soft warning instead of a hard error ---
        if (primaryError != null) {
            if (linkedNew.isNotEmpty() || primarySucceeded) {
                // Linked sources covered for the primary failure — log but don't block
                logcat(LogPriority.WARN, primaryError) {
                    "Primary source failed but linked sources delivered chapters"
                }
            } else {
                // Both primary and linked failed — surface the primary error
                logcat(LogPriority.ERROR, primaryError)
                screenModelScope.launch {
                    val message = if (primaryError is tachiyomi.domain.items.chapter.model.NoChaptersException) {
                        with(context) { primaryError.formattedMessage }
                    } else {
                        with(context) { primaryError.formattedMessage }
                    }
                    snackbarHostState.showSnackbar(message = message)
                }
            }
        }
    }

    /**
     * Fetches and syncs chapters from every linked source under its own novelId.
     * Errors per source are logged but do not abort the rest — one broken mirror
     * shouldn't stop the others from refreshing.
     *
     * Returns the list of newly added chapters across all linked sources.
     */
    private suspend fun refreshLinkedSourceChapters(manualFetch: Boolean): List<NovelChapter> {
        val primary = novel ?: return emptyList()
        val linkedId = getNovelLinkedId.await(primary.id) ?: return emptyList()
        val linkedList = getLinkedNovels.await(linkedId).filter { !it.isPrimary }
        if (linkedList.isEmpty()) return emptyList()

        val collectedNew = mutableListOf<NovelChapter>()
        for (link in linkedList) {
            val linkedNovel = runCatching {
                novelRepository.getNovelById(link.novelId)
            }.getOrNull() ?: continue
            val linkedSource = sourceManager.get(linkedNovel.source) as? NovelCatalogueSource ?: continue
            try {
                val remoteChapters = linkedSource.getChapterList(linkedNovel.toSNovel())
                if (remoteChapters.isEmpty()) continue

                val newChapters = remoteChapters.mapIndexed { i, sNovelChapter ->
                    tachiyomi.domain.items.chapter.model.NovelChapter.create().copy(
                        novelId = linkedNovel.id,
                        url = sNovelChapter.url,
                        name = sNovelChapter.name,
                        chapterNumber = sNovelChapter.chapter_number.toDouble(),
                        sourceOrder = i.toLong(),
                        dateFetch = sNovelChapter.date_upload,
                        dateUpload = sNovelChapter.date_upload,
                    )
                }

                val localLinkedChapters = novelChapterRepository.getNovelChaptersByNovelId(linkedNovel.id)
                val merged = mergeChapters(localLinkedChapters, newChapters)
                val toAdd = merged.filter { it.id == -1L }
                val toUpdate = merged.filter { it.id != -1L }
                    .filter { mc -> localLinkedChapters.any { it.id == mc.id && it != mc } }

                if (toAdd.isNotEmpty()) {
                    novelChapterRepository.addAllNovelChapters(toAdd)
                    collectedNew.addAll(toAdd)
                }
                if (toUpdate.isNotEmpty()) {
                    updateNovelChapter.awaitAll(toUpdate.map {
                        NovelChapterUpdate(
                            id = it.id,
                            name = it.name,
                            url = it.url,
                            chapterNumber = it.chapterNumber,
                            dateFetch = it.dateFetch,
                            dateUpload = it.dateUpload,
                            sourceOrder = it.sourceOrder,
                        )
                    })
                }
            } catch (t: Throwable) {
                logcat(LogPriority.WARN, t) {
                    "Linked-source refresh failed for ${linkedNovel.url} on ${linkedSource.name}"
                }
            }
        }
        return collectedNew
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

    /**
     * Merges the primary novel's chapters with chapters from all linked sources,
     * deduplicating by chapter number. The primary source always wins for a given
     * chapter number; among linked sources, the one with the lowest priority wins.
     *
     * This is the display-layer merge — it reads already-stored chapters from the
     * DB for each linked novel and combines them with the primary's chapters.
     */
    private suspend fun mergeWithLinkedSources(
        primaryNovel: Novel,
        primaryChapters: List<NovelChapter>,
    ): List<NovelChapter> {
        val linkedId = getNovelLinkedId.await(primaryNovel.id) ?: return primaryChapters
        val linkedList = getLinkedNovels.await(linkedId).filter { !it.isPrimary }
        if (linkedList.isEmpty()) return primaryChapters

        val linkedChapters = mutableListOf<Pair<NovelLink, List<NovelChapter>>>()
        for (link in linkedList) {
            val chapters = runCatching {
                novelChapterRepository.getNovelChaptersByNovelId(link.novelId)
            }.getOrNull() ?: emptyList()
            if (chapters.isNotEmpty()) {
                linkedChapters.add(link to chapters)
            }
        }
        if (linkedChapters.isEmpty()) return primaryChapters

        return mergeLinkedNovelChapters.await(primaryChapters, linkedChapters)
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        screenModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            val tasks = listOf(
                async { fetchNovelFromSource(manualFetch) },
                async { fetchChaptersFromSource(manualFetch) },
            )
            tasks.awaitAll()
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

                    logcat(LogPriority.DEBUG) { "NovelScreenModel: fetchNewChapters source=${source.name} existingCount=$existingCount" }
                    val remoteChapters = source.getLatestChapters(state.novel.toSNovel(), existingCount)
                    logcat(LogPriority.DEBUG) { "NovelScreenModel: fetchNewChapters got ${remoteChapters.size} remote chapters" }

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
                            updateNovelChapter.awaitAll(toUpdate.map {
                                NovelChapterUpdate(
                                    id = it.id,
                                    name = it.name,
                                    url = it.url,
                                    chapterNumber = it.chapterNumber,
                                    dateFetch = it.dateFetch,
                                    dateUpload = it.dateUpload,
                                    sourceOrder = it.sourceOrder,
                                )
                            })
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
                logcat(LogPriority.ERROR) { "NovelScreenModel: fetchNewChapters error: ${e::class.simpleName}: ${e.message}" }
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
                            logcat(LogPriority.DEBUG) { "NovelScreenModel: fetchAllChaptersWithProgress totalPages=$totalPages" }
                        }

                        if (pageResult.chapters.isEmpty()) {
                            logcat(LogPriority.DEBUG) { "NovelScreenModel: page $page/${totalPages} returned 0 chapters, skipping" }
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
                            logcat(LogPriority.DEBUG) { "NovelScreenModel: page $page returned only duplicates, incrementing duplicate counter" }
                            consecutiveDuplicatePages++
                            if (consecutiveDuplicatePages >= 2) {
                                logcat(LogPriority.DEBUG) { "NovelScreenModel: 2 consecutive duplicate pages, stopping fetch" }
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
                            updateNovelChapter.awaitAll(toUpdate.map {
                                NovelChapterUpdate(
                                    id = it.id,
                                    name = it.name,
                                    url = it.url,
                                    chapterNumber = it.chapterNumber,
                                    dateFetch = it.dateFetch,
                                    dateUpload = it.dateUpload,
                                    sourceOrder = it.sourceOrder,
                                )
                            })
                        }

                        totalAdded += toAdd.size
                        totalUpdated += toUpdate.size
                        totalFetched += pageResult.chapters.size

                        // Update notification with progress
                        notificationBuilder
                            .setContentText("Page $page/$totalPages — ${totalFetched} chapters fetched")
                            .setProgress(totalPages, page, false)
                        context.notify(Notifications.ID_NOVEL_CHAPTER_FETCH, notificationBuilder.build())

                        logcat(LogPriority.DEBUG) { "NovelScreenModel: page $page/$totalPages done, +${toAdd.size} new, +${toUpdate.size} updated" }

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
                logcat(LogPriority.ERROR) { "NovelScreenModel: fetchAllChaptersWithProgress error: ${e::class.simpleName}: ${e.message}" }
                logcat(LogPriority.ERROR, e)
                val errorText = when (e) {
                    is tachiyomi.domain.items.chapter.model.NoChaptersException -> "No chapters found (site may be blocking requests)"
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

                val categories = getCategories.await(novel.id)
                val defaultCategoryId = libraryPreferences.defaultMangaCategory().get().toLong()
                val defaultCategory = categories.find { it.id == defaultCategoryId }
                when {
                    defaultCategory != null -> {
                        val result = updateNovel.awaitUpdateFavorite(novel.id, true)
                        if (!result) return@launchIO
                        moveNovelToCategory(defaultCategory)
                    }
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        val result = updateNovel.awaitUpdateFavorite(novel.id, true)
                        if (!result) return@launchIO
                        moveNovelToCategory(null)
                    }
                    else -> {
                        isFromChangeCategory = true
                        showChangeCategoryDialog()
                    }
                }
            }
        }
    }

    fun showChangeCategoryDialog() {
        val novel = successState?.novel ?: return
        screenModelScope.launch {
            val categories = getCategories.await(novel.id)
            val selection = getNovelCategoryIds(novel)
            updateSuccessState { successState ->
                successState.copy(
                    dialog = Dialog.ChangeCategory(
                        novel = novel,
                        initialSelection = categories.mapAsCheckboxState { it.id in selection }.toImmutableList(),
                    ),
                )
            }
        }
    }

    private suspend fun getNovelCategoryIds(novel: Novel): Set<Long> {
        return getCategories.await(novel.id).map { it.id }.toSet()
    }

    fun moveNovelToCategory(category: Category?) {
        val novel = successState?.novel ?: return
        screenModelScope.launchIO {
            setNovelCategories.await(novel.id, listOfNotNull(category?.id))
        }
    }

    fun moveNovelToCategoriesAndAddToLibrary(novel: Novel, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setNovelCategories.await(novel.id, categoryIds)
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
        // Tracking refresh - placeholder for now
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
        val state = successState ?: return
        val chapters = state.processedChapters
        val chapterIndex = chapters.indexOfFirst { it.chapter.id == chapter.id }
        if (chapterIndex == -1) return
        val prevChapters = chapters.take(chapterIndex).map { it.chapter }
        if (prevChapters.isNotEmpty()) {
            markChaptersRead(prevChapters, true)
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

    private fun getUnreadChapters(): List<NovelChapter> {
        val novel = successState?.novel ?: return emptyList()
        val chapters = allChapters.orEmpty().map { it.chapter }
        return chapters.filter { !it.read }
    }

    private fun getUnreadChaptersSorted(): List<NovelChapter> {
        val novel = successState?.novel ?: return emptyList()
        // Always sort ascending for downloads so "next chapter" means
        // the first unread chapter after the user's current position,
        // regardless of the user's display sort/descending preference.
        return getUnreadChapters().sortedWith(getNovelChapterSort(novel, sortDescending = false))
    }

    private fun startDownload(
        chapters: List<NovelChapter>,
        startNow: Boolean,
    ) {
        val successState = successState ?: return

        screenModelScope.launchNonCancellable {
            if (startNow) {
                val chapterId = chapters.singleOrNull()?.id ?: return@launchNonCancellable
                downloadManager.startDownloadNow(chapterId)
            } else {
                downloadChapters(chapters)
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
    ) {
        when (action) {
            NovelChapterDownloadAction.START -> {
                startDownload(items.map { it.chapter }, false)
                if (items.any { it.downloadState == NovelDownload.State.ERROR }) {
                    downloadManager.startDownloads()
                }
            }
            NovelChapterDownloadAction.START_NOW -> {
                val chapter = items.singleOrNull()?.chapter ?: return
                startDownload(listOf(chapter), true)
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

    private fun downloadChapters(chapters: List<NovelChapter>) {
        val novel = successState?.novel ?: return
        downloadManager.downloadChapters(novel, chapters)
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

    private fun List<NovelChapter>.toNovelChapterListItems(novel: Novel): List<NovelChapterList.Item> {
        return map { chapter ->
            val activeDownload = downloadManager.getQueuedDownloadOrNull(chapter.id)
            val downloaded = downloadManager.isChapterDownloaded(
                chapter.name,
                chapter.scanlator,
                novel.title,
                novel.source,
            )
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> NovelDownload.State.DOWNLOADED
                else -> NovelDownload.State.NOT_DOWNLOADED
            }

            val readProgress = if (!chapter.read && chapter.lastCharRead > 0 && chapter.totalCharCount > 0) {
                ((chapter.lastCharRead.toFloat() / chapter.totalCharCount) * 100)
                    .toInt()
                    .coerceIn(1, 99)
            } else {
                null
            }

            NovelChapterList.Item(
                chapter = chapter,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                selected = chapter.id in selectedChapterIds,
                readProgress = readProgress,
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

    fun showLinkedSourcesDialog() {
        updateSuccessState { it.copy(dialog = Dialog.LinkedSources) }
    }

    suspend fun loadLinkedNovelsForDialog(): List<Novel> {
        val id = novel?.id ?: return emptyList()
        val linkedId = getNovelLinkedId.await(id) ?: return emptyList()
        val links = getLinkedNovels.await(linkedId).filter { !it.isPrimary }
        return links.mapNotNull { link ->
            runCatching { novelRepository.getNovelById(link.novelId) }.getOrNull()
        }
    }

    suspend fun loadFavoritesForLinking(): List<Novel> {
        val current = novel ?: return emptyList()
        val linkedId = getNovelLinkedId.await(current.id)
        val linkedIds = if (linkedId != null) {
            getLinkedNovels.await(linkedId).map { it.novelId }.toSet()
        } else {
            emptySet()
        }
        // Get all favorite novels, filter out current and already-linked
        return getNovelFavorites.await()
            .filter { it.id != current.id && it.id !in linkedIds }
    }

    fun linkNovelSource(targetId: Long) {
        val currentNovel = novel ?: return
        val currentSourceId = source?.id ?: return
        screenModelScope.launchNonCancellable {
            // Get target novel to find its source ID
            val target = novelRepository.getNovelById(targetId)
            linkNovelsInteractor.await(
                primaryNovelId = currentNovel.id,
                primarySourceId = currentSourceId,
                primaryExtensionType = "apk",
                linkedNovelId = targetId,
                linkedSourceId = target.source,
                linkedExtensionType = "apk",
            )
        }
    }

    fun unlinkNovelSource(targetId: Long) {
        screenModelScope.launchNonCancellable {
            unlinkNovelInteractor.await(targetId)
        }
    }

    fun makeLinkedPrimary(targetId: Long) {
        val currentPrimary = novel ?: return
        if (targetId == currentPrimary.id) return
        screenModelScope.launchNonCancellable {
            makeLinkedPrimary.await(currentPrimary.id, targetId)
        }
    }

    fun refreshLinkedSources() {
        screenModelScope.launchNonCancellable {
            refreshLinkedSourceChapters(manualFetch = true)
        }
    }

    sealed interface Dialog {
        data class ChangeCategory(
            val novel: Novel,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteChapters(val chapters: List<NovelChapter>) : Dialog
        data class DuplicateNovel(val novel: Novel, val duplicate: Novel) : Dialog
        data class Migrate(val newNovel: Novel, val oldNovel: Novel) : Dialog
        data object SettingsSheet : Dialog
        data object FullCover : Dialog
        data object LinkedSources : Dialog
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
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
            val hasPromptedToAddBefore: Boolean = false,
            val accentColor: Color? = null,
            val intervalDays: Int? = null,
            val suggestions: SuggestionState = SuggestionState.Idle,
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
                get() = chapters.firstOrNull { !it.chapter.read } ?: chapters.firstOrNull()

            private fun List<NovelChapterList.Item>.applyFilters(novel: Novel): Sequence<NovelChapterList.Item> {
                val unreadFilter = novel.unreadFilter
                val bookmarkedFilter = novel.bookmarkedFilter
                return asSequence()
                    .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
                    .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
                    .sortedWith { (chapter1), (chapter2) ->
                        getNovelChapterSort(novel).invoke(chapter1, chapter2)
                    }
            }
        }
    }
}

@Immutable
sealed class NovelChapterList {
    @Immutable
    data class Item(
        val chapter: NovelChapter,
        val selected: Boolean = false,
        val downloadState: NovelDownload.State = NovelDownload.State.NOT_DOWNLOADED,
        val downloadProgress: Int = 0,
        val readProgress: Int? = null,
    ) : NovelChapterList() {
        val id = chapter.id
    }
}
