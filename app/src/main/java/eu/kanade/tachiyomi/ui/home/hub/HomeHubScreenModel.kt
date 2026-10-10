package eu.kanade.tachiyomi.ui.home.hub

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.domain.entries.shows.model.toDomainAnime
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.presentation.util.sortGenresByPrevalence
import eu.kanade.tachiyomi.data.catalogrows.CatalogRowResolver
import eu.kanade.tachiyomi.data.catalogrows.CatalogRowSpec
import eu.kanade.tachiyomi.data.catalogrows.CatalogRowStore
import eu.kanade.tachiyomi.data.catalogrows.CatalogTitleLookup
import eu.kanade.tachiyomi.data.catalogrows.ExtensionUnavailableException
import eu.kanade.tachiyomi.data.catalogrows.Feed
import eu.kanade.tachiyomi.data.catalogrows.FeedNotImplementedException
import eu.kanade.tachiyomi.data.catalogrows.RowItem
import eu.kanade.tachiyomi.data.catalogrows.RowTarget
import eu.kanade.tachiyomi.data.catalogrows.TmdbKeyMissingException
import eu.kanade.tachiyomi.data.mikodb.SeenReporter
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.metadata.cinemeta.CinemetaCatalogFeed
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMetaShort
import eu.kanade.tachiyomi.metadata.tmdb.TmdbDiscover
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.entries.shows.ShowsScreen
import eu.kanade.tachiyomi.ui.reader.novel.ChapterHideManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.entries.shows.interactor.GetLibraryAnime
import tachiyomi.domain.entries.shows.model.AnimeCover
import tachiyomi.domain.entries.shows.model.asAnimeCover
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.history.novel.interactor.GetNovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.history.shows.interactor.GetAnimeHistory
import tachiyomi.domain.history.shows.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.shows.repository.AnimeHistoryRepository
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.shows.LibraryAnime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Clock

/**
 * Home Hub ScreenModel — combines history and recently added library items
 * across all three media types, plus greeting system, hero card, streak
 * counter, month stats, achievement display, and fast cache for instant
 * cold-start loading.
 *
 * Enhanced from the minimal Phase 2.3 implementation with features ported
 * from Tadami's Home Hub.
 */
class HomeHubScreenModel(
    private val context: Context = Injekt.get<android.app.Application>(),
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val getAnimeHistory: GetAnimeHistory = Injekt.get(),
    private val cinemetaEntryUnifier: eu.kanade.tachiyomi.metadata.stream.CinemetaEntryUnifier = Injekt.get(),
    private val canonicalEntryUnifier: eu.kanade.tachiyomi.data.mikodb.CanonicalEntryUnifier = Injekt.get(),
    private val getMangaHistory: GetMangaHistory = Injekt.get(),
    private val getNovelHistory: GetNovelHistory = Injekt.get(),
    private val animeHistoryRepository: AnimeHistoryRepository = Injekt.get(),
    private val mangaHistoryRepository: MangaHistoryRepository = Injekt.get(),
    private val novelHistoryRepository: NovelHistoryRepository = Injekt.get(),
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getLibraryNovels: GetLibraryNovels = Injekt.get(),
    private val activityDataRepository: ActivityDataRepository = Injekt.get(),
    private val achievementRepository: AchievementRepository = Injekt.get(),
    private val animeRepository: tachiyomi.domain.entries.shows.repository.AnimeRepository = Injekt.get(),
    private val mangaRepository: tachiyomi.domain.entries.manga.repository.MangaRepository = Injekt.get(),
    private val novelRepository: tachiyomi.domain.entries.novel.repository.NovelRepository = Injekt.get(),
    // --- User-built catalog rows (Phase 18) ---
    private val catalogRowStore: CatalogRowStore = Injekt.get(),
    private val catalogRowResolver: CatalogRowResolver = Injekt.get(),
    private val extensionCatalogFeed: eu.kanade.tachiyomi.data.catalogrows.ExtensionCatalogFeed = Injekt.get(),
    private val libraryCatalogFeed: eu.kanade.tachiyomi.data.catalogrows.LibraryCatalogFeed = Injekt.get(),
    private val tmdbDiscover: TmdbDiscover = Injekt.get(),
    private val cinemetaCatalogFeed: CinemetaCatalogFeed = Injekt.get(),
    private val workIdResolver: WorkIdResolver = Injekt.get(),
    private val seenReporter: SeenReporter = Injekt.get(),
    private val canonicalCatalog: eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog = Injekt.get(),
    private val networkToLocalManga: tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga = Injekt.get(),
    private val networkToLocalNovel: tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel = Injekt.get(),
    private val networkToLocalAnime: tachiyomi.domain.entries.shows.interactor.NetworkToLocalAnime = Injekt.get(),
    private val mangaSourceManager: tachiyomi.domain.source.manga.service.MangaSourceManager = Injekt.get(),
    private val animeSourceManager: tachiyomi.domain.source.anime.service.AnimeSourceManager = Injekt.get(),
    private val novelSourceManager: tachiyomi.domain.source.novel.service.NovelSourceManager = Injekt.get(),
    private val metadataSourceManager: eu.kanade.tachiyomi.metadata.MetadataSourceManager = Injekt.get(),
    private val mangaExtensionManager: eu.kanade.tachiyomi.extension.manga.MangaExtensionManager = Injekt.get(),
    private val animeExtensionManager: eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager = Injekt.get(),
    private val novelExtensionManager: eu.kanade.tachiyomi.extension.novel.NovelExtensionManager = Injekt.get(),
    private val sourcePreferences: eu.kanade.domain.source.service.SourcePreferences = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val getNovelWithChapters: GetNovelWithChapters = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
) : StateScreenModel<HomeHubState>(HomeHubState()) {

    private val fastCache = HomeHubFastCache(context)

    /** Latest history+library combine emission. A StateFlow, not a plain
     *  var, so catalog rows that compose before the first DB emission
     *  can suspend on it instead of bailing into a permanent shimmer. */
    private val lastCombinedData = MutableStateFlow<HomeHubCombinedData?>(null)

    // --- Catalog-row fetch bookkeeping (Phase 18) ---
    /** spec id → content key already fetched/fetching. */
    private val catalogFetchKeys = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val catalogFetchPermits = Semaphore(CATALOG_FETCH_CONCURRENCY)

    /** Minted/reported row items this session — [RowItem.mintKey] values. */
    private val mintedRowKeys: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val catalogMintPermits = Semaphore(CATALOG_MINT_CONCURRENCY)

    init {
        // --- Fast cache: apply cached snapshot synchronously for instant render ---
        val cached = fastCache.load()
        val hadCache = !cached.isEmpty || cached.isInitialized
        val currentMode = uiPreferences.contentMode().get()
        if (hadCache) {
            // Restore every cached page for the boot mode — the fresh
            // resolve later patches their resume labels in place and
            // appends newly-read entries after them.
            val modeKey = when (currentMode) {
                ContentMode.SHOWS -> HomeHubMediaType.SHOWS
                ContentMode.MANGA -> HomeHubMediaType.MANGA
                ContentMode.NOVEL -> HomeHubMediaType.NOVEL
            }.key
            val cachedHeroes = cached.heroesFor(modeKey).map { it.toHomeHubHero() }
            mutableState.update {
                it.copy(
                    isLoading = false,
                    userName = cached.userName.ifEmpty { uiPreferences.userName().get() },
                    greeting = resolveGreeting(),
                    greetingReady = true,
                    hero = cachedHeroes.firstOrNull(),
                    heroes = cachedHeroes,
                    currentMode = currentMode,
                    recommendations = emptyList(),
                    // History rows wait on the full DB combine (~2s cold);
                    // the snapshot paints them on the first frame and the
                    // live rows replace them in place.
                    catalogRows = cached.historyRows.mapValues { (_, items) ->
                        CatalogRowState(items = items.map { row -> row.toRowItem() })
                    },
                )
            }
        } else {
            mutableState.update {
                it.copy(
                    userName = uiPreferences.userName().get(),
                    greeting = resolveGreeting(),
                    greetingReady = true,
                    currentMode = currentMode,
                )
            }
        }

        screenModelScope.launchIO {
            // combine() supports max 5 flows — nest history and library combines
            val historyFlow = combine(
                // Unify extension-source rows onto their canonical Cinemeta
                // entry so recent/hero cards show the season-aware cover and
                // "S2 · Ep. 5" labels instead of the duplicate's static art.
                getAnimeHistory.subscribe("").map { list ->
                    list.map { cinemetaEntryUnifier.unify(it) }
                }.cursorRetry(),
                getMangaHistory.subscribe("").cursorRetry(),
                getNovelHistory.subscribe("").cursorRetry(),
            ) { animeHistory, mangaHistory, novelHistory ->
                Triple(animeHistory, mangaHistory, novelHistory)
            }
            val libraryFlow = combine(
                getLibraryAnime.subscribe(),
                getLibraryManga.subscribe(),
                getLibraryNovels.subscribe(),
            ) { animeLib, mangaLib, novelLib ->
                Triple(animeLib, mangaLib, novelLib)
            }

            // Combine achievements with their progress to count unlocked ones
            val achievementFlow = combine(
                achievementRepository.getAll(),
                achievementRepository.getAllProgress(),
            ) { achievements, progress ->
                val progressMap = progress.associateBy { it.achievementId }
                achievements to progressMap.count { it.value.isUnlocked }
            }

            // Combine history + library + activity data + achievements
            combine(
                historyFlow,
                libraryFlow,
                activityDataRepository.getActivityData(365),
                achievementFlow,
            ) {
                    (animeHistory, mangaHistory, novelHistory),
                    (animeLib, mangaLib, novelLib),
                    activityData,
                    (achievements, unlockedCount),
                ->
                HomeHubCombinedData(
                    animeHistory = animeHistory,
                    mangaHistory = mangaHistory,
                    novelHistory = novelHistory,
                    animeLib = animeLib,
                    mangaLib = mangaLib,
                    novelLib = novelLib,
                    activityData = activityData,
                    achievementTotal = achievements.size,
                    achievementUnlocked = unlockedCount,
                )
            }.collectLatest { data ->
                lastCombinedData.value = data
                // Merge against what's on screen: a card that survives
                // keeps its artwork/title and only its resume label moves
                // (cached "Continue: Ch. 15" → fresh "Continue: Ch. 18");
                // entries the cache didn't know append after it.
                val heroes = mergeHeroes(mutableState.value.heroes, resolveHeroes(data))
                val monthStats = runCatching { activityDataRepository.getCurrentMonthStats() }.getOrNull()
                val totalLibrarySize = data.animeLib.size + data.mangaLib.size + data.novelLib.size
                val streak = calculateCurrentStreak(data.activityData)

                mutableState.update {
                    // Filter out hidden chapters from novel history
                    val filteredNovelHistory = data.novelHistory.filter { h ->
                        h.chapterId !in ChapterHideManager.getHiddenChapterIds(context, h.novelId)
                    }
                    // Dedupe before take() — otherwise a binge on one show
                    // split across extension entries fills all 10 slots.
                    // historyView emits one row per chapter read, so a
                    // binge on a single work floods the feed; distinctBy
                    // must run before take() or the recents row collapses
                    // to that one work.
                    val recentAnime = data.animeHistory.distinctBy { h -> h.showKey }.take(10)
                    val recentManga = data.mangaHistory.distinctBy { it.mangaId }.take(10)
                    val recentNovels = filteredNovelHistory.distinctBy { it.novelId }.take(10)
                    // The row builder dedupes on the CANONICAL id — a work
                    // read through several extension entries collapses there,
                    // so the take(10) above would starve it. Feed it a wider
                    // candidate window; the loop itself stops at the row cap.
                    val rowAnime = data.animeHistory.distinctBy { h -> h.showKey }
                        .take(HISTORY_ROW_CANDIDATES)
                    val rowManga = data.mangaHistory.distinctBy { it.mangaId }
                        .take(HISTORY_ROW_CANDIDATES)
                    val rowNovels = filteredNovelHistory.distinctBy { it.novelId }
                        .take(HISTORY_ROW_CANDIDATES)
                    // History-backed catalog rows mirror the recents —
                    // refresh any row already materialized on screen.
                    val catalogRows = it.catalogRows.toMutableMap()
                    it.catalogSpecs.forEach { spec ->
                        if (spec.feed is Feed.History && spec.id in catalogRows) {
                            catalogRows[spec.id] = CatalogRowState(
                                items = historyRowItems(
                                    spec.medium,
                                    rowAnime,
                                    rowManga,
                                    rowNovels,
                                    data,
                                ),
                            )
                        }
                    }
                    it.copy(
                        isLoading = false,
                        recentAnime = recentAnime,
                        recentManga = recentManga,
                        recentNovels = recentNovels,
                        catalogRows = catalogRows,
                        greeting = resolveGreeting(),
                        greetingReady = true,
                        userName = uiPreferences.userName().get(),
                        hero = heroes.firstOrNull(),
                        heroes = heroes,
                        currentStreak = streak,
                        monthStats = monthStats,
                        librarySize = totalLibrarySize,
                        achievementCount = data.achievementUnlocked,
                        achievementTotal = data.achievementTotal,
                    )
                }

                // Save the merged list for the next cold start — same
                // cards the user is looking at now, resume fields included.
                saveCache(heroes, data)
            }
        }

        // --- Mirror persisted catalog specs into state (Phase 18) ---
        screenModelScope.launchIO {
            catalogRowStore.rows.collect { specs ->
                mutableState.update { it.copy(catalogSpecs = specs) }
            }
        }

        // --- Observe contentMode for mode-aware filtering ---
        screenModelScope.launchIO {
            uiPreferences.contentMode().changes().collect { mode ->
                // changes() emits the current value on subscribe — that
                // ignition is the boot mode init already rendered from the
                // fast cache. Re-resolving it here with no DB data yet
                // blanked the cached heroes for ~0.6s on every cold start.
                if (mode == mutableState.value.currentMode) return@collect
                val heroes = lastCombinedData.value?.let { data -> resolveHeroes(data) }
                    ?: fastCache.load().heroesFor(
                        when (mode) {
                            ContentMode.SHOWS -> HomeHubMediaType.SHOWS
                            ContentMode.MANGA -> HomeHubMediaType.MANGA
                            ContentMode.NOVEL -> HomeHubMediaType.NOVEL
                        }.key,
                    ).map { it.toHomeHubHero() }
                mutableState.update {
                    it.copy(
                        currentMode = mode,
                        // Re-resolve heroes for the new mode; empty if no
                        // history for it
                        hero = heroes.firstOrNull(),
                        heroes = heroes,
                    )
                }
            }
        }
    }

    fun updateUserName(name: String) {
        uiPreferences.userName().set(name)
        fastCache.updateUserName(name)
        mutableState.update { it.copy(userName = name) }
    }

    // --- Long-press to remove from recently read ---

    // --- Selection mode for long-press overlay ---

    fun toggleSelection(item: HomeHubCardItem) {
        mutableState.update { state ->
            val newSelection = if (item.id in state.selection) {
                state.selection - item.id
            } else {
                state.selection + item.id
            }
            state.copy(selection = newSelection)
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = emptySet()) }
    }

    /**
     * Delete all selected items. For recently-read items, deletes ALL history
     * for that entry (not just the single chapter shown on the home screen),
     * so the item disappears entirely as if never read. If the user reads it
     * again, a new history entry is created and it reappears normally.
     * For recently-added items, optimistically removes from state.
     */
    fun deleteSelectedItems() {
        val currentState = mutableState.value
        val selectedIds = currentState.selection
        screenModelScope.launchIO {
            // Delete ALL history for recently-read entries (by anime/manga/novel id,
            // not by individual history record id) so the item is fully removed
            // from the home screen as if never read. Uses DELETE instead of
            // reset (SET last_read=0) so the entry is completely gone.
            // One card can merge history rows from several extension entries
            // (same canonical show) — delete the history of every entry that
            // shares the selected showKey, not just the surviving row's.
            val selectedShowKeys = currentState.recentAnime
                .filter { it.animeId in selectedIds }
                .map { it.showKey }
                .toSet()
            val animeIdsToDelete = (lastCombinedData.value?.animeHistory ?: currentState.recentAnime)
                .filter { it.showKey in selectedShowKeys }
                .map { it.animeId }
                .toSet()
            animeIdsToDelete.forEach { animeHistoryRepository.deleteHistoryByAnimeId(it) }
            currentState.recentManga.filter { it.mangaId in selectedIds }.forEach {
                mangaHistoryRepository.deleteHistoryByMangaId(it.mangaId)
            }
            currentState.recentNovels.filter { it.novelId in selectedIds }.forEach {
                novelHistoryRepository.deleteHistoryByNovelId(it.novelId)
            }
            // Optimistically remove the deleted history items.
            mutableState.update { state ->
                state.copy(
                    recentAnime = state.recentAnime.filterNot { it.showKey in selectedShowKeys },
                    recentManga = state.recentManga.filterNot { it.mangaId in selectedIds },
                    recentNovels = state.recentNovels.filterNot { it.novelId in selectedIds },
                    selection = emptySet(),
                )
            }
        }
    }

    // --- User-built catalog rows (Phase 18) ---

    /**
     * Lazy fetch trigger — the row composable fires this on first
     * composition. The fetch result is keyed by the spec's content hash
     * so reorder/visibility edits reuse it while facet edits refetch.
     */
    fun onCatalogRowVisible(specId: String) {
        val spec = mutableState.value.catalogSpecs.firstOrNull { it.id == specId } ?: return
        // History rows are served live from state — no fetch, no cache.
        if (spec.feed is Feed.History) {
            screenModelScope.launchIO {
                // A row can compose before the DB combine has emitted —
                // suspend for the first emission instead of bailing,
                // which used to leave the row shimmered forever. The
                // timeout keeps ANY wait failure from re-creating that
                // bug: an empty row hides itself (history feeds render
                // nothing on empty) and the combine refresh still
                // back-fills it when data finally lands.
                val data = kotlinx.coroutines.withTimeoutOrNull(5_000) {
                    lastCombinedData.filterNotNull().first()
                }
                if (data == null) {
                    mutableState.update { s ->
                        s.copy(
                            catalogRows = s.catalogRows +
                                (specId to CatalogRowState(items = emptyList())),
                        )
                    }
                    return@launchIO
                }
                // Canonical-dedupe happens inside historyRowItems — feed it
                // the wider candidate window, not the take(10) recents slice.
                val recentAnime = data.animeHistory.distinctBy { it.showKey }
                    .take(HISTORY_ROW_CANDIDATES)
                val recentManga = data.mangaHistory.distinctBy { it.mangaId }
                    .take(HISTORY_ROW_CANDIDATES)
                val recentNovels = data.novelHistory
                    .filter { h ->
                        h.chapterId !in ChapterHideManager.getHiddenChapterIds(context, h.novelId)
                    }
                    .distinctBy { it.novelId }
                    .take(HISTORY_ROW_CANDIDATES)
                val items = historyRowItems(spec.medium, recentAnime, recentManga, recentNovels, data)
                mutableState.update { s ->
                    s.copy(
                        catalogRows = s.catalogRows +
                            (specId to CatalogRowState(items = items)),
                    )
                }
                fastCache.save(fastCache.load().copy(historyRows = historyRowsSnapshot()))
            }
            return
        }
        val key = catalogRowStore.contentKey(spec)
        // In-flight or already fetched for this exact content — skip.
        if (catalogFetchKeys[specId] == key) return
        catalogFetchKeys[specId] = key
        screenModelScope.launchIO {
            catalogFetchPermits.withPermit {
                mutableState.update { s ->
                    s.copy(
                        catalogRows = s.catalogRows +
                            (specId to CatalogRowState(loading = true)),
                    )
                }
                val result = runCatching { catalogRowResolver.resolve(spec) }
                mutableState.update { s ->
                    s.copy(
                        catalogRows = s.catalogRows +
                            (
                                specId to CatalogRowState(
                                    items = result.getOrDefault(emptyList()),
                                    loading = false,
                                    error = when (val e = result.exceptionOrNull()) {
                                        null -> null
                                        is TmdbKeyMissingException -> CatalogRowError.NO_TMDB_KEY
                                        is FeedNotImplementedException -> CatalogRowError.UNSUPPORTED_FEED
                                        is ExtensionUnavailableException -> CatalogRowError.EXTENSION_MISSING
                                        else -> {
                                            logcat(LogPriority.WARN) {
                                                "catalog row $specId fetch failed: ${e.message}"
                                            }
                                            CatalogRowError.FETCH_FAILED
                                        }
                                    },
                                )
                                ),
                    )
                }
            }
        }
    }

    /**
     * Mint-on-browse mirror of `DiscoveryScreenModel.onDiscoverItemRendered`:
     * each rendered card resolves + reports once per session (deduped on
     * [RowItem.mintKey]), capped by a dedicated permit pool so row
     * resolves can't starve the discovery surface's.
     */
    fun onCatalogItemRendered(item: RowItem) {
        val mintKey = item.mintKey ?: return
        val work = item.target as? RowTarget.CanonicalWork
        // Pre-minted canonical rows (mk:mb:/mk:hs:/browse ids) carry no
        // resolve work — report-only, and only when reportable.
        if (item.mintCandidate == null &&
            item.target !is RowTarget.CanonicalShows &&
            work != null &&
            !seenReporter.isReportable(mintKey)
        ) {
            return
        }
        if (!mintedRowKeys.add(mintKey)) return
        screenModelScope.launchIO {
            catalogMintPermits.withPermit {
                runCatching {
                    when {
                        // OL feed ids ARE the resolved id (mk:ol: is
                        // deterministic) — a prior report means resolve
                        // already ran; skip it too.
                        item.mintCandidate != null -> {
                            if (!seenReporter.wasReported(mintKey)) {
                                workIdResolver.resolve(item.mintCandidate!!)?.let { resolved ->
                                    seenReporter.report(resolved.value, resolved.medium)
                                }
                            }
                        }
                        work != null -> {
                            if (!seenReporter.wasReported(mintKey)) {
                                seenReporter.report(mintKey, work.medium)
                            }
                        }
                        // TMDB items need the async external_ids lookup
                        // before a Cinemeta-url candidate exists.
                        item.target is RowTarget.CanonicalShows -> {
                            val t = item.target as RowTarget.CanonicalShows
                            val kind = TmdbDiscover.cinemetaType(t.mediaType)
                            val imdb = tmdbDiscover.imdbId(t.mediaType, t.tmdbId)
                            workIdResolver.resolve(
                                WorkIdResolver.Candidate(
                                    medium = WorkIdResolver.MEDIUM_SHOWS,
                                    title = item.title,
                                    url = imdb?.let { "cinemeta:$kind:$it" },
                                    kindHint = kind,
                                ),
                            )?.let { seenReporter.report(it.value, it.medium) }
                        }
                    }
                }.onFailure {
                    logcat(LogPriority.DEBUG) { "catalog mint-on-browse failed for $mintKey: ${it.message}" }
                }
            }
        }
    }

    /**
     * Resolve a card tap to the Voyager screen to push plus the resolved
     * entry id, or null when the item can't route (e.g. a TMDB result
     * with no IMDB link). [previewCover] is the cover model the card
     * rendered — the detail skeleton/hero paints its already-decoded
     * bitmap via the cell memory key and pairs the shared element on
     * `cover-image-{entryId}`.
     */
    suspend fun openRowItem(
        item: RowItem,
        previewCover: Any? = null,
    ): Pair<Screen, Long>? = when (val t = item.target) {
        is RowTarget.CanonicalShows -> {
            // TMDB → IMDB → rebuild the Cinemeta meta → materialize the
            // canonical entry — the same path the discovery grid takes.
            val imdb = tmdbDiscover.imdbId(t.mediaType, t.tmdbId) ?: return null
            val meta = MikoMetaShort(
                id = imdb,
                name = item.title,
                type = TmdbDiscover.cinemetaType(t.mediaType),
                poster = item.coverUrl as? String,
                releaseInfo = item.releaseInfo,
                imdbRating = item.imdbRating,
            )
            val animeId = cinemetaCatalogFeed.getOrCreateAnimeId(meta)
            ShowsScreen(animeId, true, previewCover = previewCover, previewTitle = item.title) to animeId
        }
        is RowTarget.CinemetaEntry -> {
            val animeId = cinemetaCatalogFeed.getOrCreateAnimeId(t.meta)
            ShowsScreen(animeId, true, previewCover = previewCover, previewTitle = item.title) to animeId
        }
        is RowTarget.CanonicalWork -> {
            // Feed the WorkRow straight to materialization — the browse
            // response already carried it and byWorkId dead-ends on
            // mk:ol: works not yet in works_cache.
            if (t.medium == WorkIdResolver.MEDIUM_BOOKS) {
                val id = canonicalCatalog.materializeNovelId(t.work)
                NovelScreen(id, previewCover = previewCover, previewTitle = item.title) to id
            } else {
                val id = canonicalCatalog.materializeMangaId(t.work)
                MangaScreen(id, previewCover = previewCover, previewTitle = item.title) to id
            }
        }
        is RowTarget.ExtensionEntry -> {
            // Tap-time materialization — the listing row never inserted
            // stubs; rebuild the S* object and let toDomain+await attach
            // the persisted entry.
            val thumb = when (val c = item.coverUrl) {
                is MangaCover -> c.url
                is AnimeCover -> c.url
                is NovelCover -> c.url
                else -> c as? String
            }
            when (t.medium) {
                WorkIdResolver.MEDIUM_MANGA -> {
                    val id = networkToLocalManga.await(
                        eu.kanade.tachiyomi.source.model.SManga.create().apply {
                            url = t.url
                            title = item.title
                            thumbnail_url = thumb
                        }.toDomainManga(t.sourceId),
                    ).id
                    MangaScreen(id, true, previewCover = previewCover, previewTitle = item.title) to id
                }
                WorkIdResolver.MEDIUM_SHOWS -> {
                    val id = networkToLocalAnime.await(
                        eu.kanade.tachiyomi.animesource.model.SAnime.create().apply {
                            url = t.url
                            title = item.title
                            thumbnail_url = thumb
                        }.toDomainAnime(t.sourceId),
                    ).id
                    ShowsScreen(id, true, previewCover = previewCover, previewTitle = item.title) to id
                }
                else -> {
                    val id = networkToLocalNovel.await(
                        eu.kanade.tachiyomi.novelsource.model.SNovel.create().apply {
                            url = t.url
                            title = item.title
                            thumbnail_url = thumb
                        }.toDomainNovel(t.sourceId),
                    ).id
                    NovelScreen(id, true, previewCover = previewCover, previewTitle = item.title) to id
                }
            }
        }
        // Library rows are initialized local entries — plain detail nav.
        is RowTarget.LibraryEntry -> when (t.medium) {
            WorkIdResolver.MEDIUM_SHOWS ->
                ShowsScreen(t.entryId, previewCover = previewCover, previewTitle = item.title) to t.entryId
            WorkIdResolver.MEDIUM_MANGA ->
                MangaScreen(t.entryId, previewCover = previewCover, previewTitle = item.title) to t.entryId
            else ->
                NovelScreen(t.entryId, previewCover = previewCover, previewTitle = item.title) to t.entryId
        }
    }

    /**
     * Header title for a spec — resolves dynamic lookup data (watch-
     * provider names) on demand; the providers list is cached in
     * [TmdbDiscover] after the first call.
     */
    suspend fun catalogRowTitle(spec: CatalogRowSpec): String {
        val providerNames = if (spec.watchProviderIds.isNotEmpty()) {
            val type = (spec.feed as? Feed.Tmdb)?.tmdbMediaType() ?: MediaType.TV
            tmdbDiscover.watchProviders(type)
                .associate { it.provider_id to it.provider_name }
        } else {
            emptyMap()
        }
        val sourceName = (spec.feed as? Feed.Extension)?.let { f ->
            when (spec.medium) {
                WorkIdResolver.MEDIUM_MANGA -> mangaSourceManager.get(f.sourceId)?.name
                WorkIdResolver.MEDIUM_SHOWS -> animeSourceManager.get(f.sourceId)?.name
                else -> novelSourceManager.get(f.sourceId)?.name
            }
        }
        return spec.displayTitle(
            CatalogTitleLookup(providerNames = providerNames, sourceName = sourceName),
        )
    }

    // --- Manage/builder sheets (Phase 18-3) ---

    fun setDialog(dialog: HomeHubDialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    /** Switches the home content mode to the medium a builder category
     *  targets — the finished row then lands where the user is already
     *  looking when the sheet closes. No-op for unknown mediums or when
     *  the mode already matches. */
    fun activateCatalogMedium(medium: String) {
        val mode = Carrier.fromMedium(medium)?.pipeline ?: return
        if (uiPreferences.contentMode().get() != mode) {
            uiPreferences.contentMode().set(mode)
        }
    }

    fun upsertCatalogSpec(spec: CatalogRowSpec) = catalogRowStore.upsert(spec)
    fun deleteCatalogSpec(id: String) = catalogRowStore.delete(id)
    fun reorderCatalogSpecs(orderedIds: List<String>) = catalogRowStore.reorder(orderedIds)
    fun setCatalogSpecVisible(id: String, visible: Boolean) = catalogRowStore.setVisible(id, visible)

    /** One installed extension source's feed choices for the builder. */
    data class CatalogSourceOption(
        val sourceId: Long,
        val name: String,
        val pkgName: String,
        val supportsLatest: Boolean,
    )

    /** Everything the builder's feed step needs for one medium —
     *  Cinemeta manifest catalogs (shows only) + installed extension
     *  sources. Fetches once per sheet session; failures degrade to
     *  empty lists, never block the builder. */
    data class CatalogFeedOptions(
        val cinemetaCatalogs: List<eu.kanade.tachiyomi.metadata.miko.dto.MikoCatalogDescriptor> = emptyList(),
        val extensionSources: List<CatalogSourceOption> = emptyList(),
    )

    private var cinemetaCatalogsCache: List<eu.kanade.tachiyomi.metadata.miko.dto.MikoCatalogDescriptor>? = null

    suspend fun catalogFeedOptions(medium: String): CatalogFeedOptions {
        val catalogs = when (medium) {
            WorkIdResolver.MEDIUM_SHOWS -> runCatching {
                cinemetaCatalogsCache
                    ?: metadataSourceManager.cinemeta.getManifest().catalogs
                        .also { cinemetaCatalogsCache = it }
            }.getOrDefault(emptyList())
            else -> emptyList()
        }
        val sources = extensionSourceOptions(medium)
        return CatalogFeedOptions(catalogs, sources)
    }

    /**
     * The medium's installed extension sources, visible-set filtered:
     * `enabledLanguages` + `hidden_*_catalogues` + `show_nsfw_source` —
     * the same set the browse-sources list shows (the Find-Provider work
     * established that `getCatalogueSources()` alone races extension
     * load and ignores the user's disabled/language prefs). Awaits
     * `isInitialized` so a cold app start can't return a partial list.
     */
    private suspend fun extensionSourceOptions(medium: String): List<CatalogSourceOption> {
        // id/name/lang/pkg/supportsLatest flattened per medium — the three
        // catalogue-source hierarchies are parallel, not a shared type.
        data class Raw(
            val id: Long,
            val name: String,
            val lang: String,
            val pkg: String,
            val supportsLatest: Boolean,
        )

        val enabledLangs = sourcePreferences.enabledLanguages().get()
        val showNsfw = sourcePreferences.showNsfwSource().get()

        val (raw, disabled) = when (medium) {
            WorkIdResolver.MEDIUM_MANGA -> {
                kotlinx.coroutines.withTimeoutOrNull(EXTENSION_ENUM_TIMEOUT_MS) {
                    mangaSourceManager.isInitialized.first { it }
                }
                mangaSourceManager.getCatalogueSources().map { s ->
                    Raw(
                        s.id,
                        s.name,
                        s.lang,
                        mangaExtensionManager.getExtensionPackage(s.id).orEmpty(),
                        s.supportsLatest,
                    )
                } to sourcePreferences.disabledMangaSources().get()
            }
            WorkIdResolver.MEDIUM_SHOWS -> {
                kotlinx.coroutines.withTimeoutOrNull(EXTENSION_ENUM_TIMEOUT_MS) {
                    animeSourceManager.isInitialized.first { it }
                }
                animeSourceManager.getCatalogueSources().map { s ->
                    Raw(
                        s.id,
                        s.name,
                        s.lang,
                        animeExtensionManager.getExtensionPackage(s.id).orEmpty(),
                        s.supportsLatest,
                    )
                } to sourcePreferences.disabledAnimeSources().get()
            }
            else -> {
                kotlinx.coroutines.withTimeoutOrNull(EXTENSION_ENUM_TIMEOUT_MS) {
                    novelSourceManager.isInitialized.first { it }
                }
                novelSourceManager.getCatalogueSources().map { s ->
                    Raw(
                        s.id,
                        s.name,
                        s.lang,
                        novelExtensionManager.getExtensionPackage(s.id).orEmpty(),
                        s.supportsLatest,
                    )
                } to sourcePreferences.disabledNovelSources().get()
            }
        }

        fun nsfw(sourceId: Long) = when (medium) {
            WorkIdResolver.MEDIUM_MANGA -> mangaExtensionManager.isNsfwForSource(sourceId)
            WorkIdResolver.MEDIUM_SHOWS -> animeExtensionManager.isNsfwForSource(sourceId)
            else -> novelExtensionManager.isNsfwForSource(sourceId)
        }

        return raw.asSequence()
            .filter { it.lang in enabledLangs }
            .filterNot { it.id.toString() in disabled }
            .filter { showNsfw || !nsfw(it.id) }
            .map { CatalogSourceOption(it.id, it.name, it.pkg, it.supportsLatest) }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    /** TMDB watch-provider list for the streaming-service facet. */
    suspend fun catalogWatchProviders(mediaType: MediaType): List<TmdbDiscover.WatchProvider> =
        runCatching { tmdbDiscover.watchProviders(mediaType) }.getOrDefault(emptyList())

    /** The extension source's own genre options for the builder's facet
     *  pills — the names the resolver maps back onto its filters. */
    fun catalogExtensionGenreOptions(medium: String, sourceId: Long): List<String> =
        extensionCatalogFeed.genreOptions(medium, sourceId)

    /** Genres actually present on the medium's library entries. */
    suspend fun catalogLibraryGenreOptions(medium: String): List<String> =
        libraryCatalogFeed.genreOptions(medium)

    /**
     * Detail-parity resume target — the next unread/unseen item on the
     * entry the detail screen actually opens (canonical preferred), plus
     * real progress state. The history row's stored chapter number/name
     * can go stale (view-only opens, carrier rebinds that renumber the
     * chapter list), so heroes and continue rows derive their label from
     * this instead. [itemsLoaded] distinguishes "list is empty" from
     * "nothing unread" — callers fall back to lib/history signals only
     * when the entry's own list can't prove anything.
     */
    private data class ResumeInfo(
        val nextItemId: Long?,
        val nextNumber: Double?,
        val nextName: String?,
        val nextScanlator: String?,
        val hasProgress: Boolean,
        val itemsLoaded: Boolean,
        val allDone: Boolean,
    )

    private suspend fun mangaResumeInfo(manga: Manga): ResumeInfo {
        val chapters = runCatching {
            getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true)
        }.getOrDefault(emptyList())
        // Lowest-numbered unread — the detail screen's continue pick.
        val next = chapters.filter { !it.read }.minByOrNull { it.chapterNumber }
        return ResumeInfo(
            nextItemId = next?.id,
            nextNumber = next?.chapterNumber,
            nextName = next?.name,
            nextScanlator = null,
            hasProgress = chapters.any { it.read || it.lastPageRead > 0 },
            itemsLoaded = chapters.isNotEmpty(),
            allDone = chapters.isNotEmpty() && chapters.all { it.read },
        )
    }

    private suspend fun novelResumeInfo(novelId: Long): ResumeInfo {
        val chapters = runCatching { getNovelWithChapters.awaitChapters(novelId) }
            .getOrDefault(emptyList())
        val hiddenIds = ChapterHideManager.getHiddenChapterIds(context, novelId)
        val next = chapters
            .filter { !it.read && it.id !in hiddenIds }
            .minByOrNull { it.chapterNumber }
        return ResumeInfo(
            nextItemId = next?.id,
            nextNumber = next?.chapterNumber,
            nextName = next?.name?.let {
                eu.kanade.tachiyomi.data.mikodb.ChapterNumberParser
                    .qualifiedDisplayName(it)
                    ?: eu.kanade.tachiyomi.data.mikodb.ChapterNumberParser
                        .displayName(it)
                    ?: it
            },
            nextScanlator = null,
            hasProgress = chapters.any { it.read || it.lastCharRead > 0 },
            itemsLoaded = chapters.isNotEmpty(),
            allDone = chapters.isNotEmpty() && chapters.all { it.read },
        )
    }

    private suspend fun showsResumeInfo(animeId: Long): ResumeInfo {
        val episodes = runCatching { getEpisodesByAnimeId.await(animeId) }
            .getOrDefault(emptyList())
        val next = episodes.filter { !it.seen }.minByOrNull { it.episodeNumber }
        return ResumeInfo(
            nextItemId = next?.id,
            nextNumber = next?.episodeNumber,
            nextName = next?.name,
            nextScanlator = next?.scanlator,
            hasProgress = episodes.any { it.seen || it.lastSecondSeen > 0 },
            itemsLoaded = episodes.isNotEmpty(),
            allDone = episodes.isNotEmpty() && episodes.all { it.seen },
        )
    }

    /** "Season N|★R.R" scanlator encoding → season, Cinemeta entries only. */
    private fun scanlatorSeason(scanlator: String?, cinemeta: Boolean): Int? =
        scanlator?.substringBefore("|")
            ?.removePrefix("Season ")?.trim()?.toIntOrNull()
            ?.takeIf { it > 0 && cinemeta }

    /** The progress fragment the detail continue row renders — chapter or
     *  episode name; "Chapter N" for number-display manga. */
    private fun resumeContinueText(
        mediaType: HomeHubMediaType,
        nextName: String?,
        nextNumber: Double?,
        displayNumber: Boolean,
    ): String? = when (mediaType) {
        HomeHubMediaType.MANGA -> {
            val group = nextName?.let {
                eu.kanade.tachiyomi.util.chapter.ChapterNameGroup.parse(it)
            }
            when {
                displayNumber && nextNumber != null -> {
                    val base = context.stringResource(
                        MR.strings.display_mode_chapter,
                        eu.kanade.presentation.util.formatChapterNumber(nextNumber),
                    )
                    if (group != null) "$group $base" else base
                }
                group != null -> nextName?.let {
                    eu.kanade.tachiyomi.data.mikodb.ChapterNumberParser
                        .qualifiedDisplayName(it)
                } ?: nextName
                else -> nextName?.let { bareNumberOrRaw(it) }
            }
        }
        HomeHubMediaType.NOVEL -> nextName?.let { bareNumberOrRaw(it) }
        else -> nextName
    }

    /** Bare-number chapter names ("#3", "003") render "Chapter 3"; names
     *  carrying real words pass through unchanged. */
    private fun bareNumberOrRaw(name: String): String =
        eu.kanade.tachiyomi.data.mikodb.ChapterNumberParser.bareNumberDisplayName(name) ?: name

    /**
     * Cheap pre-check before the chapter/episode query — a library entry
     * with no finished items and no recorded session can't have real
     * progress, so skip its query entirely. Non-library entries pass
     * through to verification (their list may still show progress).
     */
    private fun cheapStartedGate(hasStarted: Boolean?, readDuration: Long): Boolean =
        hasStarted == null || hasStarted || readDuration > 0

    /**
     * Continue rows — the recents lists already flowing through state,
     * mapped onto RowItems (progress number as the card sub-text, plain
     * detail-screen taps). Entries that are fully caught up (no unseen
     * episodes, no unread chapters) are actively excluded — this row is
     * for resuming, and a caught-up entry has nothing to resume into.
     * Entries with no library row survive: an unmeasurable entry can't
     * be proven caught up. Local-only; never cached by
     * [catalogFetchKeys] so a recents update rewrites the row.
     */
    private suspend fun historyRowItems(
        medium: String,
        recentAnime: List<tachiyomi.domain.history.shows.model.AnimeHistoryWithRelations>,
        recentManga: List<MangaHistoryWithRelations>,
        recentNovels: List<NovelHistoryWithRelations>,
        data: HomeHubCombinedData,
    ): List<RowItem> {
        return when (medium) {
            WorkIdResolver.MEDIUM_SHOWS -> {
                val seenEntries = HashSet<Long>()
                buildList {
                    for (it in recentAnime) {
                        if (size >= eu.kanade.tachiyomi.data.catalogrows.LibraryCatalogFeed
                                .ROW_ITEM_LIMIT
                        ) {
                            break
                        }
                        if (!seenEntries.add(it.showKey)) continue
                        val lib = data.animeLib
                            .find { l -> l.anime.id == it.animeId || l.anime.id == it.showKey }
                        if (lib != null && lib.unseenCount <= 0L) continue
                        // Unwatched entries can't resume into anything.
                        if (!cheapStartedGate(lib?.hasStarted, 1L)) continue
                        // Episodes live on the canonical entry — its list
                        // decides the resume point and proves progress.
                        val info = showsResumeInfo(it.showKey)
                        if (info.itemsLoaded && !info.hasProgress) continue
                        if (!info.itemsLoaded && lib?.hasStarted != true) continue
                        val season = scanlatorSeason(info.nextScanlator, it.isCinemetaMovie.not())
                        add(
                            RowItem(
                                title = it.title,
                                coverUrl = it.coverData,
                                subtitle = when {
                                    it.isCinemetaMovie -> animeProgressLabel(it)
                                    info.nextNumber != null -> buildString {
                                        season?.let { s -> append("S$s · ") }
                                        append("Ep. ")
                                        append(
                                            eu.kanade.presentation.util
                                                .formatEpisodeNumber(info.nextNumber),
                                        )
                                    }
                                    else -> animeProgressLabel(it)
                                },
                                target = RowTarget.LibraryEntry(it.animeId, medium),
                                // Id-keyed — same-title entries (canonical +
                                // extension copies) must not collide on the
                                // row's lazy key.
                                mintKey = "lib:$medium:${it.animeId}",
                            ),
                        )
                    }
                }
            }
            // Manga/novel rows mirror the hero: collapse extension rows
            // onto their canonical entry so the row and the carousel
            // describe the same set of works.
            WorkIdResolver.MEDIUM_MANGA -> {
                val seenEntries = HashSet<Long>()
                buildList {
                    for (it in recentManga) {
                        if (size >= eu.kanade.tachiyomi.data.catalogrows.LibraryCatalogFeed
                                .ROW_ITEM_LIMIT
                        ) {
                            break
                        }
                        val entry = runCatching { mangaRepository.getMangaById(it.mangaId) }
                            .getOrNull()
                        val canonical = entry?.let { e ->
                            runCatching { canonicalEntryUnifier.resolveLocal(e) }.getOrNull()
                        }
                        if (!seenEntries.add(canonical?.id ?: it.mangaId)) continue
                        val lib = data.mangaLib
                            .find { l -> l.manga.id == it.mangaId || l.manga.id == canonical?.id }
                        if (lib != null && lib.unreadCount <= 0L) continue
                        if (!cheapStartedGate(lib?.hasStarted, it.readDuration)) continue
                        val target = canonical ?: entry ?: continue
                        val info = mangaResumeInfo(target)
                        if (info.itemsLoaded && !info.hasProgress) continue
                        if (!info.itemsLoaded &&
                            lib?.hasStarted != true &&
                            it.readDuration <= 0
                        ) {
                            continue
                        }
                        add(
                            RowItem(
                                title = canonical?.title ?: it.title,
                                coverUrl = canonical?.asMangaCover() ?: it.coverData,
                                subtitle = "Ch. ${
                                    eu.kanade.presentation.util.formatChapterNumber(
                                        info.nextNumber ?: it.chapterNumber,
                                    )
                                }",
                                target = RowTarget.LibraryEntry(
                                    canonical?.id ?: it.mangaId,
                                    medium,
                                ),
                                mintKey = "lib:$medium:${it.mangaId}",
                            ),
                        )
                    }
                }
            }
            else -> {
                val seenEntries = HashSet<Long>()
                buildList {
                    for (it in recentNovels) {
                        if (size >= eu.kanade.tachiyomi.data.catalogrows.LibraryCatalogFeed
                                .ROW_ITEM_LIMIT
                        ) {
                            break
                        }
                        val entry = runCatching { novelRepository.getNovelById(it.novelId) }
                            .getOrNull()
                        val canonical = entry?.let { e ->
                            runCatching { canonicalEntryUnifier.resolveLocal(e) }.getOrNull()
                        }
                        if (!seenEntries.add(canonical?.id ?: it.novelId)) continue
                        val lib = data.novelLib
                            .find { l -> l.novel.id == it.novelId || l.novel.id == canonical?.id }
                        if (lib != null && lib.unreadCount <= 0L) continue
                        if (!cheapStartedGate(lib?.hasStarted, it.readDuration)) continue
                        val target = canonical ?: entry ?: continue
                        val info = novelResumeInfo(target.id)
                        if (info.itemsLoaded && !info.hasProgress) continue
                        if (!info.itemsLoaded &&
                            lib?.hasStarted != true &&
                            it.readDuration <= 0
                        ) {
                            continue
                        }
                        add(
                            RowItem(
                                title = canonical?.title ?: it.title,
                                coverUrl = canonical?.asNovelCover() ?: it.coverData,
                                subtitle = info.nextName?.let { n -> bareNumberOrRaw(n) }
                                    ?: "Ch. ${
                                        eu.kanade.presentation.util.formatChapterNumber(
                                            it.chapterNumber,
                                        )
                                    }",
                                target = RowTarget.LibraryEntry(
                                    canonical?.id ?: it.novelId,
                                    medium,
                                ),
                                mintKey = "lib:$medium:${it.novelId}",
                            ),
                        )
                    }
                }
            }
        }
    }

    // --- Greeting system ---

    private fun resolveGreeting(): StringResource {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> AYMR.strings.home_greeting_morning
            in 12..16 -> AYMR.strings.home_greeting_afternoon
            in 17..21 -> AYMR.strings.home_greeting_evening
            else -> AYMR.strings.home_greeting_night
        }
    }

    // --- Hero card(s) ---

    /**
     * The hero carousel pages — one page per entry, newest-first within
     * each priority tier:
     *
     *  1. New content since last read (`latestUpload > lastRead`) —
     *     fresh chapters/episodes outrank everything else.
     *  2. Outstanding content (`unread > 0`) — entries with chapters or
     *     episodes still to consume.
     *  3. Caught-up entries — only surface when the first two tiers
     *     can't fill the carousel.
     *
     * If there is no history for the current mode, returns empty so the
     * placeholder is shown instead of heroes from a different mode.
     */
    private suspend fun resolveHeroes(data: HomeHubCombinedData): List<HomeHubHero> {
        val currentMode = uiPreferences.contentMode().get()
        // Two passes: cheap candidates (dedupe, lib lookups, cheap-started
        // gate, rank) first; chapter/episode queries only for ranked
        // candidates until HERO_PAGE_COUNT heroes are produced.
        val candidates = mutableListOf<Pair<Int, suspend () -> HomeHubHero?>>()
        when (currentMode) {
            ContentMode.SHOWS -> {
                val seenShows = HashSet<Long>()
                for (h in data.animeHistory) {
                    // One hero page per show — episodes watched through
                    // different extension entries share the unifier's
                    // canonical id.
                    if (!seenShows.add(h.showKey)) continue
                    val lib = data.animeLib
                        .find { l -> l.anime.id == h.animeId || l.anime.id == h.showKey }
                    if (!cheapStartedGate(lib?.hasStarted, 0L)) continue
                    candidates += heroRank(
                        hasOutstanding = lib?.let { l -> l.unseenCount > 0L } == true,
                        hasNewSinceLastRead = lib?.let { l ->
                            l.unseenCount > 0L && l.latestUpload > l.lastSeen
                        } == true,
                    ) to { showsHero(h, lib) }
                }
            }
            ContentMode.MANGA -> {
                // Dedupe per canonical entry, not per history row — two
                // extension duplicates of one `miko:` work would emit the
                // same shared-element key and fight in the transition
                // overlay. resolveLocal is cache-only (no Worker call):
                // a canonical row implies its work row was already
                // materialized into works_cache.
                val seenRows = HashSet<Long>()
                val seenEntries = HashSet<Long>()
                for (h in data.mangaHistory) {
                    if (!seenRows.add(h.mangaId)) continue
                    val entry = runCatching { mangaRepository.getMangaById(h.mangaId) }.getOrNull()
                    // Canonical row → its title/cover, matching what the
                    // detail screen renders after the canonical push — a
                    // mismatched title would crossfade inside the shared
                    // title element and read as a rebuild.
                    val canonical = entry?.let { e ->
                        runCatching { canonicalEntryUnifier.resolveLocal(e) }.getOrNull()
                    }
                    val canonicalId = canonical?.id
                    if (!seenEntries.add(canonicalId ?: h.mangaId)) continue
                    val lib = data.mangaLib
                        .find { l -> l.manga.id == h.mangaId || l.manga.id == canonicalId }
                    if (!cheapStartedGate(lib?.hasStarted, h.readDuration)) continue
                    candidates += heroRank(
                        hasOutstanding = lib?.unreadCount?.let { it > 0 } == true,
                        hasNewSinceLastRead = lib?.let { l ->
                            l.unreadCount > 0 && l.latestUpload > l.lastRead
                        } == true,
                    ) to { mangaHero(h, entry, canonical, lib) }
                }
            }
            ContentMode.NOVEL -> {
                val seenRows = HashSet<Long>()
                val seenEntries = HashSet<Long>()
                for (h in data.novelHistory) {
                    if (h.chapterId in ChapterHideManager.getHiddenChapterIds(context, h.novelId)) {
                        continue
                    }
                    if (!seenRows.add(h.novelId)) continue
                    val entry = runCatching { novelRepository.getNovelById(h.novelId) }.getOrNull()
                    val canonical = entry?.let { e ->
                        runCatching { canonicalEntryUnifier.resolveLocal(e) }.getOrNull()
                    }
                    val canonicalId = canonical?.id
                    if (!seenEntries.add(canonicalId ?: h.novelId)) continue
                    val lib = data.novelLib
                        .find { l -> l.novel.id == h.novelId || l.novel.id == canonicalId }
                    if (!cheapStartedGate(lib?.hasStarted, h.readDuration)) continue
                    candidates += heroRank(
                        hasOutstanding = lib?.unreadCount?.let { it > 0 } == true,
                        hasNewSinceLastRead = lib?.let { l ->
                            l.unreadCount > 0 && l.latestUpload > l.lastRead
                        } == true,
                    ) to { novelHero(h, entry, canonical, lib) }
                }
            }
        }
        // Stable sort — history order (most recent first) survives inside
        // each tier — then produce heroes until the carousel is full.
        candidates.sortBy { it.first }
        val heroes = buildList {
            for ((_, produce) in candidates) {
                if (size >= HERO_PAGE_COUNT) break
                produce()?.let(::add)
            }
        }
        // The hero should never be an empty frame: continue entries
        // first, then unstarted library entries ("Start" instead of
        // "Continue"), then the mode's top discovery items when the
        // library itself is empty.
        if (heroes.size < HERO_PAGE_COUNT) {
            return heroes + fallbackHeroes(data, currentMode, heroes)
        }
        return heroes
    }

    /**
     * Backfill heroes when history can't fill the carousel.
     *
     * Tier 1 — library entries the user hasn't started yet, newest-added
     * first; they render the "Start" label ([hasStarted]=false) and their
     * resume target is the first item.
     *
     * Tier 2 — the mode's top discovery items (the same feed resolver the
     * discovery rows use: works-browse for manga/books, Cinemeta top for
     * shows). Materializing mints a local entry so the hero's detail and
     * "Start" both work; the row itself has no chapters yet, so `subId`
     * stays -1 and the CTA routes to the detail screen.
     */
    private suspend fun fallbackHeroes(
        data: HomeHubCombinedData,
        mode: ContentMode,
        existing: List<HomeHubHero>,
    ): List<HomeHubHero> {
        val claimed = existing
            .flatMapTo(HashSet()) { setOfNotNull(it.canonicalId, it.entryId) }
        val heroes = mutableListOf<HomeHubHero>()
        fun room() = HERO_PAGE_COUNT - existing.size - heroes.size

        // --- Tier 1: unstarted library entries (newest additions first) ---
        when (mode) {
            ContentMode.SHOWS ->
                data.animeLib
                    .filter { !it.hasStarted && it.anime.id !in claimed }
                    .sortedByDescending { it.anime.dateAdded }
                    .forEach { lib ->
                        if (heroes.size >= room()) return@forEach
                        val info = showsResumeInfo(lib.anime.id)
                        val subId = info.nextItemId ?: return@forEach
                        heroes += HomeHubHero(
                            entryId = lib.anime.id,
                            title = lib.anime.title,
                            progressNumber = info.nextNumber ?: 0.0,
                            chapterTitle = info.nextName,
                            coverData = lib.anime.asAnimeCover(),
                            subId = subId,
                            mediaType = HomeHubMediaType.SHOWS,
                            hasStarted = false,
                            genres = sortGenresByPrevalence(lib.anime.genre.orEmpty()).take(3),
                            backdrop = lib.anime.backgroundUrl,
                        )
                    }
            ContentMode.MANGA ->
                data.mangaLib
                    .filter { !it.hasStarted && it.manga.id !in claimed }
                    .sortedByDescending { it.manga.dateAdded }
                    .forEach { lib ->
                        if (heroes.size >= room()) return@forEach
                        val info = mangaResumeInfo(lib.manga)
                        val subId = info.nextItemId ?: return@forEach
                        heroes += HomeHubHero(
                            entryId = lib.manga.id,
                            title = lib.manga.title,
                            progressNumber = info.nextNumber ?: 0.0,
                            chapterTitle = info.nextName,
                            coverData = lib.manga.asMangaCover(),
                            subId = subId,
                            mediaType = HomeHubMediaType.MANGA,
                            hasStarted = false,
                            continueText = resumeContinueText(
                                HomeHubMediaType.MANGA,
                                info.nextName,
                                info.nextNumber,
                                displayNumber = lib.manga.displayMode == Manga.CHAPTER_DISPLAY_NUMBER,
                            ),
                            genres = sortGenresByPrevalence(lib.manga.genre.orEmpty()).take(3),
                        )
                    }
            ContentMode.NOVEL ->
                data.novelLib
                    .filter { !it.hasStarted && it.novel.id !in claimed }
                    .sortedByDescending { it.novel.dateAdded }
                    .forEach { lib ->
                        if (heroes.size >= room()) return@forEach
                        val info = novelResumeInfo(lib.novel.id)
                        val subId = info.nextItemId ?: return@forEach
                        heroes += HomeHubHero(
                            entryId = lib.novel.id,
                            title = lib.novel.title,
                            progressNumber = info.nextNumber ?: 0.0,
                            chapterTitle = info.nextName,
                            coverData = lib.novel.asNovelCover(),
                            subId = subId,
                            mediaType = HomeHubMediaType.NOVEL,
                            hasStarted = false,
                            continueText = info.nextName,
                            genres = sortGenresByPrevalence(lib.novel.genre.orEmpty()).take(3),
                        )
                    }
        }
        if (room() <= 0) return heroes

        // --- Tier 2: the mode's discovery tops, materialized on use ---
        val spec = when (mode) {
            ContentMode.SHOWS -> CatalogRowSpec(
                id = "hero-fallback",
                medium = WorkIdResolver.MEDIUM_SHOWS,
                feed = Feed.Cinemeta(cinemetaType = "series", catalogId = "top"),
                order = 0,
            )
            ContentMode.MANGA -> CatalogRowSpec(
                id = "hero-fallback",
                medium = WorkIdResolver.MEDIUM_MANGA,
                feed = Feed.Canonical(kindsCsv = "manga,comic"),
                order = 0,
            )
            ContentMode.NOVEL -> CatalogRowSpec(
                id = "hero-fallback",
                medium = WorkIdResolver.MEDIUM_BOOKS,
                feed = Feed.Canonical(kindsCsv = "novel"),
                order = 0,
            )
        }
        val items = runCatching { catalogRowResolver.resolve(spec) }
            .onFailure { logcat(LogPriority.DEBUG) { "hero discovery fallback failed: ${it.message}" } }
            .getOrDefault(emptyList())
        for (item in items) {
            if (room() <= 0) break
            val hero = runCatching { discoveryHero(item, mode) }.getOrNull() ?: continue
            if (hero.entryId in claimed || hero.entryId in heroes.map { it.entryId }) continue
            heroes += hero
        }
        return heroes
    }

    /**
     * Materialize a discovery [RowItem] into a hero. `subId` stays -1 —
     * a freshly minted canonical row carries no chapters/episodes, so
     * the CTA falls back to opening the detail screen.
     */
    private suspend fun discoveryHero(item: RowItem, mode: ContentMode): HomeHubHero? {
        return when (val t = item.target) {
            is RowTarget.CanonicalShows -> {
                val imdb = tmdbDiscover.imdbId(t.mediaType, t.tmdbId) ?: return null
                val meta = MikoMetaShort(
                    id = imdb,
                    name = item.title,
                    type = TmdbDiscover.cinemetaType(t.mediaType),
                    poster = item.coverUrl as? String,
                    releaseInfo = item.releaseInfo,
                    imdbRating = item.imdbRating,
                )
                val animeId = cinemetaCatalogFeed.getOrCreateAnimeId(meta)
                val anime = runCatching { animeRepository.getAnimeById(animeId) }.getOrNull()
                HomeHubHero(
                    entryId = animeId, title = item.title,
                    progressNumber = 0.0, coverData = anime?.asAnimeCover() ?: item.coverUrl,
                    subId = -1, mediaType = HomeHubMediaType.SHOWS, hasStarted = false,
                    genres = anime?.genre.orEmpty().take(3), backdrop = anime?.backgroundUrl,
                )
            }
            is RowTarget.CinemetaEntry -> {
                val animeId = cinemetaCatalogFeed.getOrCreateAnimeId(t.meta)
                val anime = runCatching { animeRepository.getAnimeById(animeId) }.getOrNull()
                HomeHubHero(
                    entryId = animeId, title = item.title,
                    progressNumber = 0.0, coverData = anime?.asAnimeCover() ?: item.coverUrl,
                    subId = -1, mediaType = HomeHubMediaType.SHOWS, hasStarted = false,
                    genres = anime?.genre.orEmpty().take(3), backdrop = anime?.backgroundUrl,
                )
            }
            is RowTarget.CanonicalWork -> {
                if (t.medium == WorkIdResolver.MEDIUM_BOOKS) {
                    val id = canonicalCatalog.materializeNovelId(t.work)
                    val novel = runCatching { novelRepository.getNovelById(id) }.getOrNull()
                    HomeHubHero(
                        entryId = id,
                        title = item.title,
                        progressNumber = 0.0,
                        coverData = novel?.asNovelCover() ?: item.coverUrl,
                        subId = -1,
                        mediaType = HomeHubMediaType.NOVEL,
                        hasStarted = false,
                        genres = novel?.genre.orEmpty().take(3),
                    )
                } else {
                    val id = canonicalCatalog.materializeMangaId(t.work)
                    val manga = runCatching { mangaRepository.getMangaById(id) }.getOrNull()
                    HomeHubHero(
                        entryId = id,
                        title = item.title,
                        progressNumber = 0.0,
                        coverData = manga?.asMangaCover() ?: item.coverUrl,
                        subId = -1,
                        mediaType = HomeHubMediaType.MANGA,
                        hasStarted = false,
                        genres = manga?.genre.orEmpty().take(3),
                    )
                }
            }
            else -> null
        }
    }

    /**
     * Builds a shows hero — or null when the canonical episode list proves
     * the entry has no real progress (view-only history rows).
     */
    private suspend fun showsHero(
        h: AnimeHistoryWithRelations,
        lib: LibraryAnime?,
    ): HomeHubHero? {
        val info = showsResumeInfo(h.showKey)
        val started = info.hasProgress || (!info.itemsLoaded && lib?.hasStarted == true)
        if (!started) return null
        val allRead = if (info.itemsLoaded) {
            info.allDone
        } else {
            lib?.let { l -> l.unseenCount == 0L && l.totalCount > 0 } ?: false
        }
        // The canonical entry carries the rich metadata (Cinemeta genres,
        // landscape backdrop, logo pref key).
        val entry = runCatching { animeRepository.getAnimeById(h.showKey) }.getOrNull()
        val cinemeta = entry?.url?.startsWith("cinemeta:")
            ?: h.animeUrl.startsWith("cinemeta:")
        return HomeHubHero(
            entryId = h.showKey,
            title = h.title,
            progressNumber = info.nextNumber ?: h.episodeNumber,
            coverData = h.coverData,
            subId = info.nextItemId ?: h.episodeId,
            mediaType = HomeHubMediaType.SHOWS,
            allRead = allRead,
            hasStarted = true,
            continueText = info.nextName?.let { name ->
                eu.kanade.presentation.entries.components.qualifiedEpisodeLabel(
                    name,
                    info.nextNumber ?: 0.0,
                    info.nextScanlator.takeIf { cinemeta },
                )
            },
            season = if (info.nextNumber != null) {
                scanlatorSeason(info.nextScanlator, cinemeta)
            } else {
                h.cinemetaSeason
            },
            runtime = h.runtime,
            isCinemetaMovie = h.isCinemetaMovie,
            genres = sortGenresByPrevalence(entry?.genre.orEmpty()).take(3),
            backdrop = entry?.backgroundUrl,
            canonicalId = h.showKey,
        )
    }

    private suspend fun mangaHero(
        h: MangaHistoryWithRelations,
        entry: Manga?,
        canonical: Manga?,
        lib: LibraryManga?,
    ): HomeHubHero? {
        val target = canonical ?: entry ?: return null
        val info = mangaResumeInfo(target)
        val started = info.hasProgress ||
            (!info.itemsLoaded && (lib?.hasStarted == true || h.readDuration > 0))
        if (!started) return null
        val allRead = if (info.itemsLoaded) {
            info.allDone
        } else {
            lib?.let { l -> l.unreadCount == 0L && l.totalChapters > 0 } ?: false
        }
        return HomeHubHero(
            entryId = target.id,
            title = canonical?.title ?: h.title,
            progressNumber = info.nextNumber ?: h.chapterNumber,
            chapterTitle = info.nextName,
            coverData = canonical?.asMangaCover() ?: h.coverData,
            subId = info.nextItemId ?: h.chapterId,
            mediaType = HomeHubMediaType.MANGA,
            allRead = allRead,
            hasStarted = true,
            continueText = resumeContinueText(
                HomeHubMediaType.MANGA,
                info.nextName,
                info.nextNumber,
                displayNumber = target.displayMode == Manga.CHAPTER_DISPLAY_NUMBER,
            ),
            genres = sortGenresByPrevalence(entry?.genre.orEmpty()).take(3),
            canonicalId = canonical?.id,
        )
    }

    private suspend fun novelHero(
        h: NovelHistoryWithRelations,
        entry: tachiyomi.domain.entries.novel.model.Novel?,
        canonical: tachiyomi.domain.entries.novel.model.Novel?,
        lib: LibraryNovel?,
    ): HomeHubHero? {
        val target = canonical ?: entry ?: return null
        val info = novelResumeInfo(target.id)
        val started = info.hasProgress ||
            (!info.itemsLoaded && (lib?.hasStarted == true || h.readDuration > 0))
        if (!started) return null
        val allRead = if (info.itemsLoaded) {
            info.allDone
        } else {
            lib?.let { l -> l.unreadCount == 0L && l.totalChapters > 0 } ?: false
        }
        return HomeHubHero(
            entryId = target.id,
            title = canonical?.title ?: h.title,
            progressNumber = info.nextNumber ?: h.chapterNumber,
            chapterTitle = info.nextName ?: h.chapterName,
            coverData = canonical?.asNovelCover() ?: h.coverData,
            subId = info.nextItemId ?: h.chapterId,
            mediaType = HomeHubMediaType.NOVEL,
            allRead = allRead,
            hasStarted = true,
            continueText = info.nextName,
            genres = sortGenresByPrevalence(entry?.genre.orEmpty()).take(3),
            canonicalId = canonical?.id,
        )
    }

    /** 0 = fresh uploads since last read · 1 = chapters/episodes still
     *  to consume · 2 = caught up (or not measurable — absent lib row). */
    private fun heroRank(hasOutstanding: Boolean, hasNewSinceLastRead: Boolean): Int = when {
        hasNewSinceLastRead -> 0
        hasOutstanding -> 1
        else -> 2
    }

    // --- Streak calculation ---

    private fun calculateCurrentStreak(activities: List<DayActivity>): Int {
        if (activities.isEmpty()) return 0

        val activityByDate = activities.associateBy { it.date }
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val hasActivityToday = (activityByDate[today]?.level ?: 0) > 0
        var checkDate = if (hasActivityToday) today else today.minus(1, DateTimeUnit.DAY)
        var streak = 0

        while (true) {
            val level = activityByDate[checkDate]?.level ?: 0
            if (level <= 0) break
            streak++
            checkDate = checkDate.minus(1, DateTimeUnit.DAY)
        }

        return streak
    }

    /**
     * Fold freshly-resolved heroes into the currently-displayed list.
     * A card that survives keeps its artwork, title and position — only
     * the resume fields move — so a cached cold-start hero updates its
     * "Continue: Ch. 15 → Ch. 18" label in place instead of rebuilding
     * the whole page. Entries the display didn't know append after it.
     * Dropped cards (finished, or history gone) fall out naturally.
     */
    private fun mergeHeroes(displayed: List<HomeHubHero>, fresh: List<HomeHubHero>): List<HomeHubHero> {
        if (displayed.isEmpty()) return fresh.take(HERO_MERGE_CAP)
        fun keys(h: HomeHubHero) = setOfNotNull(h.canonicalId, h.entryId)
        val freshByKey = fresh.flatMap { f -> keys(f).map { it to f } }.toMap()
        val used = HashSet<Long>()
        val merged = displayed.mapNotNull { d ->
            val f = keys(d).firstNotNullOfOrNull { freshByKey[it] } ?: return@mapNotNull null
            used.addAll(keys(f))
            d.copy(
                progressNumber = f.progressNumber,
                chapterTitle = f.chapterTitle,
                subId = f.subId,
                allRead = f.allRead,
                hasStarted = f.hasStarted,
                continueText = f.continueText,
                season = f.season,
                runtime = f.runtime,
            )
        }
        // New content slots right after the first cached page — buried
        // at position 6 behind five cached cards it'd never be seen.
        return (
            listOfNotNull(merged.firstOrNull()) +
                fresh.filter { f -> keys(f).none(used::contains) } +
                merged.drop(1)
            ).take(HERO_MERGE_CAP)
    }

    // --- Fast cache ---

    private fun HomeHubHero.toCached(): CachedHeroItem {
        val (coverUrl, coverLastModified) = extractCoverInfo(coverData)
        return CachedHeroItem(
            entryId = entryId,
            title = title,
            progressNumber = progressNumber,
            coverUrl = coverUrl,
            coverLastModified = coverLastModified,
            subId = subId,
            mediaType = mediaType.key,
            backdropUrl = backdrop as? String,
            season = season,
            runtime = runtime,
            isCinemetaMovie = isCinemetaMovie,
            genres = genres,
            logoId = canonicalId,
            chapterTitle = chapterTitle,
            hasStarted = hasStarted,
            continueText = continueText,
        )
    }

    private fun saveCache(heroes: List<HomeHubHero>, data: HomeHubCombinedData) {
        val modeKey = heroes.firstOrNull()?.mediaType?.key
            ?: when (uiPreferences.contentMode().get()) {
                ContentMode.SHOWS -> HomeHubMediaType.SHOWS.key
                ContentMode.MANGA -> HomeHubMediaType.MANGA.key
                ContentMode.NOVEL -> HomeHubMediaType.NOVEL.key
            }
        // An empty resolve while history rows exist is ambiguous — the
        // chapter/episode queries may have dropped everything transiently.
        // Don't clobber a good snapshot with nothing; only write empty
        // when the mode has no history at all (authoritative-empty).
        val modeHistoryEmpty = when (modeKey) {
            HomeHubMediaType.SHOWS.key -> data.animeHistory.isEmpty()
            HomeHubMediaType.MANGA.key -> data.mangaHistory.isEmpty()
            else -> data.novelHistory.isEmpty()
        }
        val prev = fastCache.load()
        val heroesMap = if (heroes.isEmpty() && !modeHistoryEmpty) {
            prev.heroes
        } else {
            prev.heroes + (modeKey to heroes.map { it.toCached() })
        }
        val cachedHero = heroesMap[modeKey]?.firstOrNull()

        val cachedHistory = buildList {
            data.animeHistory.take(6).forEach { h ->
                add(
                    CachedHistoryItem(
                        h.animeId,
                        h.title,
                        h.episodeNumber,
                        h.coverData.url,
                        h.coverData.lastModified,
                        HomeHubMediaType.SHOWS.key,
                    ),
                )
            }
            data.mangaHistory.take(6).forEach { h ->
                add(
                    CachedHistoryItem(
                        h.mangaId,
                        h.title,
                        h.chapterNumber,
                        h.coverData.url,
                        h.coverData.lastModified,
                        HomeHubMediaType.MANGA.key,
                    ),
                )
            }
            data.novelHistory.take(6).forEach { h ->
                add(
                    CachedHistoryItem(
                        h.novelId,
                        h.title,
                        h.chapterNumber,
                        h.coverData.url,
                        h.coverData.lastModified,
                        HomeHubMediaType.NOVEL.key,
                    ),
                )
            }
        }

        // Cache recommendations
        val cachedRecs = mutableState.value.recommendations.map { rec ->
            CachedRecommendationItem(
                id = rec.id,
                title = rec.title,
                coverUrl = rec.coverData as? String,
                mediaType = rec.mediaType.key,
            )
        }

        fastCache.save(
            CachedHomeHubState(
                hero = cachedHero,
                heroes = heroesMap,
                history = cachedHistory,
                recommendations = cachedRecs,
                historyRows = historyRowsSnapshot(),
                userName = uiPreferences.userName().get(),
                isInitialized = true,
            ),
        )
    }

    /**
     * History rows as last computed from live data, merged over the
     * previous snapshot (rows not yet scrolled into view keep theirs).
     * Only called where DB data backs the state — a timed-out row never
     * overwrites a good snapshot with empty.
     */
    private fun historyRowsSnapshot(): Map<String, List<CachedRowItem>> {
        val s = mutableState.value
        val historyIds = s.catalogSpecs.filter { it.feed is Feed.History }.mapTo(HashSet()) { it.id }
        val live = s.catalogRows
            .filterKeys { it in historyIds }
            .mapValues { (_, row) -> row.items.mapNotNull { it.toCachedRow() } }
        return fastCache.load().historyRows + live
    }

    private fun RowItem.toCachedRow(): CachedRowItem? {
        val t = target as? RowTarget.LibraryEntry ?: return null
        val base = CachedRowItem(
            title = title,
            subtitle = subtitle,
            entryId = t.entryId,
            medium = t.medium,
            mintKey = mintKey,
            coverKind = "url",
        )
        return when (val c = coverUrl) {
            is MangaCover -> base.copy(
                coverKind = "manga",
                coverEntryId = c.mangaId,
                coverSourceId = c.sourceId,
                coverFavorite = c.isMangaFavorite,
                coverUrl = c.url,
                coverLastModified = c.lastModified,
            )
            is NovelCover -> base.copy(
                coverKind = "novel",
                coverEntryId = c.novelId,
                coverSourceId = c.sourceId,
                coverFavorite = c.isNovelFavorite,
                coverUrl = c.url,
                coverLastModified = c.lastModified,
            )
            is AnimeCover -> base.copy(
                coverKind = "anime",
                coverEntryId = c.animeId,
                coverSourceId = c.sourceId,
                coverFavorite = c.isAnimeFavorite,
                coverUrl = c.url,
                coverLastModified = c.lastModified,
            )
            is String -> base.copy(coverUrl = c)
            else -> null
        }
    }

    private fun CachedRowItem.toRowItem(): RowItem = RowItem(
        title = title,
        subtitle = subtitle,
        mintKey = mintKey,
        target = RowTarget.LibraryEntry(entryId, medium),
        coverUrl = when (coverKind) {
            "manga" -> MangaCover(coverEntryId, coverSourceId, coverFavorite, coverUrl, coverLastModified)
            "novel" -> NovelCover(coverEntryId, coverSourceId, coverFavorite, coverUrl, coverLastModified)
            "anime" -> AnimeCover(coverEntryId, coverSourceId, coverFavorite, coverUrl, coverLastModified)
            else -> coverUrl
        },
    )

    private fun extractCoverInfo(coverData: Any?): Pair<String?, Long> {
        return when (coverData) {
            is AnimeCover -> coverData.url to coverData.lastModified
            is MangaCover -> coverData.url to coverData.lastModified
            is NovelCover -> coverData.url to coverData.lastModified
            else -> null to 0L
        }
    }

    private data class HomeHubCombinedData(
        val animeHistory: List<AnimeHistoryWithRelations>,
        val mangaHistory: List<MangaHistoryWithRelations>,
        val novelHistory: List<NovelHistoryWithRelations>,
        val animeLib: List<LibraryAnime>,
        val mangaLib: List<LibraryManga>,
        val novelLib: List<LibraryNovel>,
        val activityData: List<DayActivity>,
        val achievementTotal: Int,
        val achievementUnlocked: Int,
    )
}

/** Hero carousel page cap — most recently consumed entries, one page each. */
private const val HERO_PAGE_COUNT = 5

/** Merged-list bound — cached pages plus fresh entries can exceed the
 *  page cap on a cold start; cap the union so the carousel stays sane. */
private const val HERO_MERGE_CAP = HERO_PAGE_COUNT * 2

/** Concurrent catalog-row fetches — below-the-fold rows shouldn't stampede. */
private const val CATALOG_FETCH_CONCURRENCY = 3

/** Concurrent catalog mint-on-browse resolves — a dedicated pool so rows
 *  can't starve the discovery surface's semaphore. */
private const val CATALOG_MINT_CONCURRENCY = 2

/** Candidate pool the history-backed ("Continue Reading") row scans —
 *  canonical dedupe inside the builder collapses extension copies, so a
 *  raw take(10) underfills the row; the loop still stops at the row cap. */
private const val HISTORY_ROW_CANDIDATES = 120

/** How long the catalog builder waits for a source manager's first
 *  extension emission before showing an empty source list — cold starts
 *  otherwise race `sourcesMapFlow` filling and show no sources. */
private const val EXTENSION_ENUM_TIMEOUT_MS = 5_000L

private fun CachedHeroItem.toHomeHubHero(): HomeHubHero {
    val coverData: Any? = when (mediaType) {
        // "anime" is the pre-15-04 fast-cache value — kept as a parse-boundary alias.
        "shows", "anime" -> AnimeCover(entryId, -1, true, coverUrl, coverLastModified)
        "manga" -> MangaCover(entryId, -1, true, coverUrl, coverLastModified)
        "novel" -> NovelCover(entryId, -1, true, coverUrl, coverLastModified)
        else -> null
    }
    return HomeHubHero(
        entryId = entryId,
        title = title,
        progressNumber = progressNumber,
        coverData = coverData,
        subId = subId,
        mediaType = HomeHubMediaType.fromKey(mediaType),
        backdrop = backdropUrl,
        season = season,
        runtime = runtime,
        isCinemetaMovie = isCinemetaMovie,
        genres = genres,
        canonicalId = logoId,
        chapterTitle = chapterTitle,
        hasStarted = hasStarted,
        continueText = continueText,
    )
}

/**
 * SQLDelight cursor-window transient: while the MikoDB sweep churns
 * chapters/mangas/history, an in-flight `mapToList` can refill its
 * window past a shrunk result set and read a NULL row → mapper `!!`
 * NPE. Resubscribing re-queries a consistent snapshot; bounded so a
 * genuine data NULL still surfaces after a few tries.
 */
private fun <T> kotlinx.coroutines.flow.Flow<T>.cursorRetry() =
    retryWhen { cause, attempt -> cause is NullPointerException && attempt < 3 }
