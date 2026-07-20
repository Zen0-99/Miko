package eu.kanade.tachiyomi.ui.home.hub

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.history.novel.interactor.GetNovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.library.anime.LibraryAnime
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
    private val suggestionCoordinator: SuggestionCoordinator = Injekt.get(),
) : StateScreenModel<HomeHubState>(HomeHubState()) {

    private val fastCache = HomeHubFastCache(context)
    private var lastCombinedData: HomeHubCombinedData? = null

    init {
        // --- Fast cache: apply cached snapshot synchronously for instant render ---
        val cached = fastCache.load()
        val hadCache = !cached.isEmpty || cached.isInitialized
        val currentMode = uiPreferences.contentMode().get()
        if (hadCache) {
            // Only use cached hero if its media type matches the current mode
            val modeAwareHero = cached.hero?.toHomeHubHero()?.takeIf {
                it.mediaType == when (currentMode) {
                    ContentMode.ANIME -> HomeHubMediaType.ANIME
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
                    currentMode = currentMode,
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
                getAnimeHistory.subscribe(""),
                getMangaHistory.subscribe(""),
                getNovelHistory.subscribe(""),
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
            ) { (animeHistory, mangaHistory, novelHistory), (animeLib, mangaLib, novelLib), activityData, (achievements, unlockedCount) ->
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
                val hero = resolveHero(data)
                val monthStats = runCatching { activityDataRepository.getCurrentMonthStats() }.getOrNull()
                val totalLibrarySize = data.animeLib.size + data.mangaLib.size + data.novelLib.size
                val streak = calculateCurrentStreak(data.activityData)

                mutableState.update {
                    it.copy(
                        isLoading = false,
                        recentAnime = data.animeHistory.take(10),
                        recentManga = data.mangaHistory.take(10),
                        recentNovels = data.novelHistory.take(10),
                        recentlyAddedAnime = data.animeLib
                            .sortedByDescending { it.anime.dateAdded }
                            .take(10)
                            .map { it.toRecentlyAdded(HomeHubMediaType.ANIME) },
                        recentlyAddedManga = data.mangaLib
                            .sortedByDescending { it.manga.dateAdded }
                            .take(10)
                            .map { it.toRecentlyAdded(HomeHubMediaType.MANGA) },
                        recentlyAddedNovels = data.novelLib
                            .sortedByDescending { it.novel.dateAdded }
                            .take(10)
                            .map { it.toRecentlyAdded(HomeHubMediaType.NOVEL) },
                        greeting = resolveGreeting(),
                        greetingReady = true,
                        userName = uiPreferences.userName().get(),
                        hero = hero,
                        currentStreak = streak,
                        monthStats = monthStats,
                        librarySize = totalLibrarySize,
                        achievementCount = data.achievementUnlocked,
                        achievementTotal = data.achievementTotal,
                    )
                }

                // Save to fast cache for next cold start
                saveCache(hero, data)
            }
        }

        // --- Observe contentMode for mode-aware filtering ---
        screenModelScope.launchIO {
            uiPreferences.contentMode().changes().collect { mode ->
                mutableState.update {
                    it.copy(
                        currentMode = mode,
                        // Re-resolve hero for the new mode; null if no history for it
                        hero = lastCombinedData?.let { data -> resolveHero(data) },
                    )
                }
            }
        }

        // --- Load recommendations (once, based on most recent library item) ---
        screenModelScope.launchIO {
            // Wait until we have library data loaded, then fetch recommendations once
            state
                .map { Triple(it.recentAnime, it.recentManga, it.recentNovels) }
                .filter { it.first.isNotEmpty() || it.second.isNotEmpty() || it.third.isNotEmpty() }
                .distinctUntilChanged { _, _ -> false } // Only emit once
                .collect { (animeHistory, mangaHistory, novelHistory) ->
                    loadRecommendations(animeHistory, mangaHistory, novelHistory)
                }
        }
    }

    fun updateUserName(name: String) {
        uiPreferences.userName().set(name)
        fastCache.updateUserName(name)
        mutableState.update { it.copy(userName = name) }
    }

    // --- Long-press to remove from recently read ---

    fun deleteHistoryItem(item: HomeHubCardItem) {
        screenModelScope.launchIO {
            val currentState = mutableState.value
            when (item.mediaType) {
                HomeHubMediaType.ANIME -> {
                    val history = currentState.recentAnime.find { it.animeId == item.id }
                    history?.let { animeHistoryRepository.resetAnimeHistory(it.id) }
                }
                HomeHubMediaType.MANGA -> {
                    val history = currentState.recentManga.find { it.mangaId == item.id }
                    history?.let { mangaHistoryRepository.resetMangaHistory(it.id) }
                }
                HomeHubMediaType.NOVEL -> {
                    val history = currentState.recentNovels.find { it.novelId == item.id }
                    history?.let { novelHistoryRepository.resetNovelHistory(it.id) }
                }
            }
        }
    }

    fun removeRecentlyAddedItem(item: HomeHubCardItem) {
        // Optimistically remove from state — the item will reappear on next library refresh
        // if it's still in the library. This provides instant visual feedback.
        mutableState.update { state ->
            when (item.mediaType) {
                HomeHubMediaType.ANIME -> state.copy(
                    recentlyAddedAnime = state.recentlyAddedAnime.filterNot { it.id == item.id },
                )
                HomeHubMediaType.MANGA -> state.copy(
                    recentlyAddedManga = state.recentlyAddedManga.filterNot { it.id == item.id },
                )
                HomeHubMediaType.NOVEL -> state.copy(
                    recentlyAddedNovels = state.recentlyAddedNovels.filterNot { it.id == item.id },
                )
            }
        }
    }

    // --- Recommendations ---

    private suspend fun loadRecommendations(
        animeHistory: List<AnimeHistoryWithRelations>,
        mangaHistory: List<MangaHistoryWithRelations>,
        novelHistory: List<NovelHistoryWithRelations>,
    ) {
        // Find most recent history item across all types and build a seed
        val animeFirst = animeHistory.firstOrNull()
        val mangaFirst = mangaHistory.firstOrNull()
        val novelFirst = novelHistory.firstOrNull()

        val animeTime = animeFirst?.seenAt?.time ?: 0L
        val mangaTime = mangaFirst?.readAt?.time ?: 0L
        val novelTime = novelFirst?.readAt?.time ?: 0L

        val seed: SuggestionSeed? = when {
            animeFirst != null && animeTime >= mangaTime && animeTime >= novelTime -> SuggestionSeed(
                mediaType = SuggestionMediaType.ANIME,
                primaryTitle = animeFirst.title,
                candidateTitles = listOf(animeFirst.title),
                description = null,
                author = null,
                genres = null,
            )
            mangaFirst != null && mangaTime >= novelTime -> SuggestionSeed(
                mediaType = SuggestionMediaType.MANGA,
                primaryTitle = mangaFirst.title,
                candidateTitles = listOf(mangaFirst.title),
                description = null,
                author = null,
                genres = null,
            )
            novelFirst != null -> SuggestionSeed(
                mediaType = SuggestionMediaType.NOVEL,
                primaryTitle = novelFirst.title,
                candidateTitles = listOf(novelFirst.title),
                description = null,
                author = null,
                genres = null,
            )
            else -> null
        }

        if (seed == null) return

        try {
            val result = suggestionCoordinator.fetchSuggestions(seed, limit = 10)
            val recItems = result.items.take(10).map { item ->
                HomeHubCardItem(
                    id = item.providerUrl.hashCode().toLong(),
                    title = item.title,
                    coverData = item.thumbnailUrl,
                    mediaType = when (item.mediaType) {
                        SuggestionMediaType.ANIME -> HomeHubMediaType.ANIME
                        SuggestionMediaType.MANGA -> HomeHubMediaType.MANGA
                        SuggestionMediaType.NOVEL -> HomeHubMediaType.NOVEL
                    },
                )
            }
            mutableState.update { it.copy(recommendations = recItems) }
        } catch (e: Exception) {
            // Silently fail — recommendations are optional
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

    // --- Hero card ---

    private fun resolveHero(data: HomeHubCombinedData): HomeHubHero? {
        // Mode-aware: only pick from the current content mode's history.
        // If there is no history for the current mode, return null so the
        // placeholder is shown instead of a hero from a different mode.
        val currentMode = uiPreferences.contentMode().get()
        return when (currentMode) {
            ContentMode.ANIME -> data.animeHistory.firstOrNull()?.let {
                HomeHubHero(
                    entryId = it.animeId,
                    title = it.title,
                    progressNumber = it.episodeNumber,
                    coverData = it.coverData,
                    subId = it.episodeId,
                    mediaType = HomeHubMediaType.ANIME,
                )
            }
            ContentMode.MANGA -> data.mangaHistory.firstOrNull()?.let {
                HomeHubHero(
                    entryId = it.mangaId,
                    title = it.title,
                    progressNumber = it.chapterNumber,
                    coverData = it.coverData,
                    subId = it.chapterId,
                    mediaType = HomeHubMediaType.MANGA,
                )
            }
            ContentMode.NOVEL -> data.novelHistory.firstOrNull()?.let {
                HomeHubHero(
                    entryId = it.novelId,
                    title = it.title,
                    progressNumber = it.chapterNumber,
                    coverData = it.coverData,
                    subId = it.chapterId,
                    mediaType = HomeHubMediaType.NOVEL,
                )
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
            )
        }

        val cachedHistory = buildList {
            data.animeHistory.take(6).forEach { h ->
                add(CachedHistoryItem(h.animeId, h.title, h.episodeNumber, h.coverData.url, h.coverData.lastModified, HomeHubMediaType.ANIME.key))
            }
            data.mangaHistory.take(6).forEach { h ->
                add(CachedHistoryItem(h.mangaId, h.title, h.chapterNumber, h.coverData.url, h.coverData.lastModified, HomeHubMediaType.MANGA.key))
            }
            data.novelHistory.take(6).forEach { h ->
                add(CachedHistoryItem(h.novelId, h.title, h.chapterNumber, h.coverData.url, h.coverData.lastModified, HomeHubMediaType.NOVEL.key))
            }
        }

        fastCache.save(
            CachedHomeHubState(
                hero = cachedHero,
                history = cachedHistory,
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

private fun CachedHeroItem.toHomeHubHero(): HomeHubHero {
    val coverData: Any? = when (mediaType) {
        "anime" -> AnimeCover(entryId, -1, true, coverUrl, coverLastModified)
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
    )
}

private fun LibraryAnime.toRecentlyAdded(type: HomeHubMediaType) = RecentlyAddedItem(
    id = anime.id,
    title = anime.title,
    coverData = anime,
    sourceId = anime.source,
    url = anime.url,
    mediaType = type,
)

private fun LibraryManga.toRecentlyAdded(type: HomeHubMediaType) = RecentlyAddedItem(
    id = manga.id,
    title = manga.title,
    coverData = manga,
    sourceId = manga.source,
    url = manga.url,
    mediaType = type,
)

private fun LibraryNovel.toRecentlyAdded(type: HomeHubMediaType) = RecentlyAddedItem(
    id = novel.id,
    title = novel.title,
    coverData = novel,
    sourceId = novel.source,
    url = novel.url,
    mediaType = type,
)
