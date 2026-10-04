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
import eu.kanade.tachiyomi.ui.entries.shows.ShowsScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.reader.novel.ChapterHideManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.entries.shows.model.AnimeCover
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.entries.shows.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.history.shows.interactor.GetAnimeHistory
import tachiyomi.domain.history.shows.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.shows.repository.AnimeHistoryRepository
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.history.novel.interactor.GetNovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.library.shows.LibraryAnime
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

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
) : StateScreenModel<HomeHubState>(HomeHubState()) {

    private val fastCache = HomeHubFastCache(context)
    private var lastCombinedData: HomeHubCombinedData? = null

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
            // Only use cached hero if its media type matches the current mode
            val modeAwareHero = cached.hero?.toHomeHubHero()?.takeIf {
                it.mediaType == when (currentMode) {
                    ContentMode.SHOWS -> HomeHubMediaType.SHOWS
                    ContentMode.MANGA -> HomeHubMediaType.MANGA
                    ContentMode.NOVEL -> HomeHubMediaType.NOVEL
                }
            }
            mutableState.update {
                it.copy(
                    isLoading = false,
                    userName = cached.userName.ifEmpty { uiPreferences.userName().get() },
                    greeting = resolveGreeting(),
                    greetingReady = true,
                    hero = modeAwareHero,
                    heroes = listOfNotNull(modeAwareHero),
                    currentMode = currentMode,
                    recommendations = emptyList(),
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
                lastCombinedData = data
                val heroes = resolveHeroes(data)
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
                    val recentAnime = data.animeHistory.distinctBy { h -> h.showKey }.take(10)
                    val recentManga = data.mangaHistory.take(10)
                    val recentNovels = filteredNovelHistory.take(10)
                    // History-backed catalog rows mirror the recents —
                    // refresh any row already materialized on screen.
                    val catalogRows = it.catalogRows.toMutableMap()
                    it.catalogSpecs.forEach { spec ->
                        if (spec.feed is Feed.History && spec.id in catalogRows) {
                            catalogRows[spec.id] = CatalogRowState(
                                items = historyRowItems(
                                    spec.medium, recentAnime, recentManga, recentNovels,
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

                // Save to fast cache for next cold start
                saveCache(heroes.firstOrNull(), data)
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
                val heroes = lastCombinedData?.let { data -> resolveHeroes(data) }.orEmpty()
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
            val animeIdsToDelete = (lastCombinedData?.animeHistory ?: currentState.recentAnime)
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
            mutableState.update { s ->
                s.copy(
                    catalogRows = s.catalogRows +
                        (
                            specId to CatalogRowState(
                                items = historyRowItems(
                                    spec.medium, s.recentAnime, s.recentManga, s.recentNovels,
                                ),
                            )
                            ),
                )
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
        if (item.mintCandidate == null && item.target !is RowTarget.CanonicalShows &&
            work != null && !seenReporter.isReportable(mintKey)
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
     * Resolve a card tap to the Voyager screen to push, or null when the
     * item can't route (e.g. a TMDB result with no IMDB link).
     */
    suspend fun openRowItem(item: RowItem): Screen? = when (val t = item.target) {
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
            ShowsScreen(cinemetaCatalogFeed.getOrCreateAnimeId(meta), true)
        }
        is RowTarget.CinemetaEntry ->
            ShowsScreen(cinemetaCatalogFeed.getOrCreateAnimeId(t.meta), true)
        is RowTarget.CanonicalWork -> {
            // Feed the WorkRow straight to materialization — the browse
            // response already carried it and byWorkId dead-ends on
            // mk:ol: works not yet in works_cache.
            if (t.medium == WorkIdResolver.MEDIUM_BOOKS) {
                NovelScreen(canonicalCatalog.materializeNovelId(t.work))
            } else {
                MangaScreen(canonicalCatalog.materializeMangaId(t.work))
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
                WorkIdResolver.MEDIUM_MANGA -> MangaScreen(
                    networkToLocalManga.await(
                        eu.kanade.tachiyomi.source.model.SManga.create().apply {
                            url = t.url
                            title = item.title
                            thumbnail_url = thumb
                        }.toDomainManga(t.sourceId),
                    ).id,
                    true,
                )
                WorkIdResolver.MEDIUM_SHOWS -> ShowsScreen(
                    networkToLocalAnime.await(
                        eu.kanade.tachiyomi.animesource.model.SAnime.create().apply {
                            url = t.url
                            title = item.title
                            thumbnail_url = thumb
                        }.toDomainAnime(t.sourceId),
                    ).id,
                    true,
                )
                else -> NovelScreen(
                    networkToLocalNovel.await(
                        eu.kanade.tachiyomi.novelsource.model.SNovel.create().apply {
                            url = t.url
                            title = item.title
                            thumbnail_url = thumb
                        }.toDomainNovel(t.sourceId),
                    ).id,
                    true,
                )
            }
        }
        // Library rows are initialized local entries — plain detail nav.
        is RowTarget.LibraryEntry -> when (t.medium) {
            WorkIdResolver.MEDIUM_SHOWS -> ShowsScreen(t.entryId)
            WorkIdResolver.MEDIUM_MANGA -> MangaScreen(t.entryId)
            else -> NovelScreen(t.entryId)
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
        data class Raw(val id: Long, val name: String, val lang: String,
                       val pkg: String, val supportsLatest: Boolean)

        val enabledLangs = sourcePreferences.enabledLanguages().get()
        val showNsfw = sourcePreferences.showNsfwSource().get()

        val (raw, disabled) = when (medium) {
            WorkIdResolver.MEDIUM_MANGA -> {
                kotlinx.coroutines.withTimeoutOrNull(EXTENSION_ENUM_TIMEOUT_MS) {
                    mangaSourceManager.isInitialized.first { it }
                }
                mangaSourceManager.getCatalogueSources().map { s ->
                    Raw(s.id, s.name, s.lang,
                        mangaExtensionManager.getExtensionPackage(s.id).orEmpty(),
                        s.supportsLatest)
                } to sourcePreferences.disabledMangaSources().get()
            }
            WorkIdResolver.MEDIUM_SHOWS -> {
                kotlinx.coroutines.withTimeoutOrNull(EXTENSION_ENUM_TIMEOUT_MS) {
                    animeSourceManager.isInitialized.first { it }
                }
                animeSourceManager.getCatalogueSources().map { s ->
                    Raw(s.id, s.name, s.lang,
                        animeExtensionManager.getExtensionPackage(s.id).orEmpty(),
                        s.supportsLatest)
                } to sourcePreferences.disabledAnimeSources().get()
            }
            else -> {
                kotlinx.coroutines.withTimeoutOrNull(EXTENSION_ENUM_TIMEOUT_MS) {
                    novelSourceManager.isInitialized.first { it }
                }
                novelSourceManager.getCatalogueSources().map { s ->
                    Raw(s.id, s.name, s.lang,
                        novelExtensionManager.getExtensionPackage(s.id).orEmpty(),
                        s.supportsLatest)
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
     * History-feed row items — the recents lists already flowing through
     * state, mapped onto RowItems (progress number as the card sub-text,
     * plain detail-screen taps). Local-only; never cached by
     * [catalogFetchKeys] so a recents update rewrites the row.
     */
    private fun historyRowItems(
        medium: String,
        recentAnime: List<tachiyomi.domain.history.shows.model.AnimeHistoryWithRelations>,
        recentManga: List<MangaHistoryWithRelations>,
        recentNovels: List<NovelHistoryWithRelations>,
    ): List<RowItem> {
        return when (medium) {
            WorkIdResolver.MEDIUM_SHOWS -> recentAnime.map {
                RowItem(
                    title = it.title,
                    coverUrl = it.coverData,
                    subtitle = animeProgressLabel(it),
                    target = RowTarget.LibraryEntry(it.animeId, medium),
                    // Id-keyed — same-title entries (canonical + extension
                    // copies) must not collide on the row's lazy key.
                    mintKey = "lib:$medium:${it.animeId}",
                )
            }
            WorkIdResolver.MEDIUM_MANGA -> recentManga.distinctBy { it.mangaId }.map {
                RowItem(
                    title = it.title,
                    coverUrl = it.coverData,
                    subtitle = "Ch. ${eu.kanade.presentation.util.formatChapterNumber(it.chapterNumber)}",
                    target = RowTarget.LibraryEntry(it.mangaId, medium),
                    mintKey = "lib:$medium:${it.mangaId}",
                )
            }
            else -> recentNovels.distinctBy { it.novelId }.map {
                RowItem(
                    title = it.title,
                    coverUrl = it.coverData,
                    subtitle = "Ch. ${eu.kanade.presentation.util.formatChapterNumber(it.chapterNumber)}",
                    target = RowTarget.LibraryEntry(it.novelId, medium),
                    mintKey = "lib:$medium:${it.novelId}",
                )
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
     * The hero carousel pages — the most recently consumed entries in the
     * current mode, one page per entry, newest first. If there is no
     * history for the current mode, returns empty so the placeholder is
     * shown instead of heroes from a different mode.
     */
    private suspend fun resolveHeroes(data: HomeHubCombinedData): List<HomeHubHero> {
        val currentMode = uiPreferences.contentMode().get()
        return when (currentMode) {
            ContentMode.SHOWS -> data.animeHistory
                // One hero page per show — episodes watched through different
                // extension entries share the unifier's canonical id.
                .distinctBy { it.showKey }
                .take(HERO_PAGE_COUNT)
                .map {
                    val allRead = data.animeLib.find { lib -> lib.anime.id == it.animeId }
                        ?.let { lib -> lib.unseenCount == 0L && lib.totalCount > 0 } ?: false
                    // The canonical entry carries the rich metadata (Cinemeta
                    // genres, landscape backdrop, logo pref key).
                    val entry = runCatching { animeRepository.getAnimeById(it.showKey) }.getOrNull()
                    HomeHubHero(
                        entryId = it.animeId,
                        title = it.title,
                        progressNumber = it.episodeNumber,
                        coverData = it.coverData,
                        subId = it.episodeId,
                        mediaType = HomeHubMediaType.SHOWS,
                        allRead = allRead,
                        season = it.cinemetaSeason,
                        runtime = it.runtime,
                        isCinemetaMovie = it.isCinemetaMovie,
                        genres = sortGenresByPrevalence(entry?.genre.orEmpty()).take(3),
                        backdrop = entry?.backgroundUrl,
                        canonicalId = it.showKey,
                    )
                }
            ContentMode.MANGA -> buildList {
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
                    val allRead = data.mangaLib.find { lib -> lib.manga.id == h.mangaId }
                        ?.let { lib -> lib.unreadCount == 0L && lib.totalChapters > 0 } ?: false
                    add(
                        HomeHubHero(
                            entryId = h.mangaId,
                            title = canonical?.title ?: h.title,
                            progressNumber = h.chapterNumber,
                            coverData = canonical?.asMangaCover() ?: h.coverData,
                            subId = h.chapterId,
                            mediaType = HomeHubMediaType.MANGA,
                            allRead = allRead,
                            genres = sortGenresByPrevalence(entry?.genre.orEmpty()).take(3),
                            canonicalId = canonicalId,
                        ),
                    )
                    if (size >= HERO_PAGE_COUNT) break
                }
            }
            ContentMode.NOVEL -> buildList {
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
                    val allRead = data.novelLib.find { lib -> lib.novel.id == h.novelId }
                        ?.let { lib -> lib.unreadCount == 0L && lib.totalChapters > 0 } ?: false
                    add(
                        HomeHubHero(
                            entryId = h.novelId,
                            title = canonical?.title ?: h.title,
                            progressNumber = h.chapterNumber,
                            chapterTitle = h.chapterName,
                            coverData = canonical?.asNovelCover() ?: h.coverData,
                            subId = h.chapterId,
                            mediaType = HomeHubMediaType.NOVEL,
                            allRead = allRead,
                            genres = sortGenresByPrevalence(entry?.genre.orEmpty()).take(3),
                            canonicalId = canonicalId,
                        ),
                    )
                    if (size >= HERO_PAGE_COUNT) break
                }
            }
        }
    }

    // --- Streak calculation ---

    private fun calculateCurrentStreak(activities: List<DayActivity>): Int {
        if (activities.isEmpty()) return 0

        val activityByDate = activities.associateBy { it.date }
        val today = LocalDate.now()
        val hasActivityToday = (activityByDate[today]?.level ?: 0) > 0
        var checkDate = if (hasActivityToday) today else today.minusDays(1)
        var streak = 0

        while (true) {
            val level = activityByDate[checkDate]?.level ?: 0
            if (level <= 0) break
            streak++
            checkDate = checkDate.minusDays(1)
        }

        return streak
    }

    // --- Fast cache ---

    private fun saveCache(hero: HomeHubHero?, data: HomeHubCombinedData) {
        val cachedHero = hero?.let { h ->
            val (coverUrl, coverLastModified) = extractCoverInfo(h.coverData)
            CachedHeroItem(
                entryId = h.entryId,
                title = h.title,
                progressNumber = h.progressNumber,
                coverUrl = coverUrl,
                coverLastModified = coverLastModified,
                subId = h.subId,
                mediaType = h.mediaType.key,
                backdropUrl = h.backdrop as? String,
                season = h.season,
                runtime = h.runtime,
                isCinemetaMovie = h.isCinemetaMovie,
                genres = h.genres,
                logoId = h.canonicalId,
                chapterTitle = h.chapterTitle,
            )
        }

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
                history = cachedHistory,
                recommendations = cachedRecs,
                userName = uiPreferences.userName().get(),
                isInitialized = true,
            ),
        )
    }

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

/** Concurrent catalog-row fetches — below-the-fold rows shouldn't stampede. */
private const val CATALOG_FETCH_CONCURRENCY = 3

/** Concurrent catalog mint-on-browse resolves — a dedicated pool so rows
 *  can't starve the discovery surface's semaphore. */
private const val CATALOG_MINT_CONCURRENCY = 2

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
