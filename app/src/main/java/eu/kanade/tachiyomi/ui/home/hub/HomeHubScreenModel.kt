package eu.kanade.tachiyomi.ui.home.hub

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.history.novel.interactor.GetNovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
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
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getLibraryNovels: GetLibraryNovels = Injekt.get(),
    private val activityDataRepository: ActivityDataRepository = Injekt.get(),
    private val achievementRepository: AchievementRepository = Injekt.get(),
) : StateScreenModel<HomeHubState>(HomeHubState()) {

    private val fastCache = HomeHubFastCache(context)

    init {
        // --- Fast cache: apply cached snapshot synchronously for instant render ---
        val cached = fastCache.load()
        val hadCache = !cached.isEmpty || cached.isInitialized
        if (hadCache) {
            mutableState.update {
                it.copy(
                    isLoading = false,
                    userName = cached.userName.ifEmpty { uiPreferences.userName().get() },
                    greeting = resolveGreeting(),
                    greetingReady = true,
                    hero = cached.hero?.toHomeHubHero(),
                )
            }
        } else {
            mutableState.update {
                it.copy(
                    userName = uiPreferences.userName().get(),
                    greeting = resolveGreeting(),
                    greetingReady = true,
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
                val hero = resolveHero(data)
                val monthStats = runCatching { activityDataRepository.getCurrentMonthStats() }.getOrNull()
                val totalLibrarySize = data.animeLib.size + data.mangaLib.size + data.novelLib.size
                val streak = calculateCurrentStreak(data.activityData)

                mutableState.update {
                    HomeHubState(
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
    }

    fun updateUserName(name: String) {
        uiPreferences.userName().set(name)
        fastCache.updateUserName(name)
        mutableState.update { it.copy(userName = name) }
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
        // Pick the most recent history item across all media types.
        val animeHero = data.animeHistory.firstOrNull()
        val mangaHero = data.mangaHistory.firstOrNull()
        val novelHero = data.novelHistory.firstOrNull()

        // Compare by seenAt/readAt timestamps to find the truly most recent
        val animeTime = animeHero?.seenAt?.time ?: 0L
        val mangaTime = mangaHero?.readAt?.time ?: 0L
        val novelTime = novelHero?.readAt?.time ?: 0L

        return when {
            animeHero != null && animeTime >= mangaTime && animeTime >= novelTime -> HomeHubHero(
                entryId = animeHero.animeId,
                title = animeHero.title,
                progressNumber = animeHero.episodeNumber,
                coverData = animeHero.coverData,
                subId = animeHero.episodeId,
                mediaType = HomeHubMediaType.ANIME,
            )
            mangaHero != null && mangaTime >= novelTime -> HomeHubHero(
                entryId = mangaHero.mangaId,
                title = mangaHero.title,
                progressNumber = mangaHero.chapterNumber,
                coverData = mangaHero.coverData,
                subId = mangaHero.chapterId,
                mediaType = HomeHubMediaType.MANGA,
            )
            novelHero != null -> HomeHubHero(
                entryId = novelHero.novelId,
                title = novelHero.title,
                progressNumber = novelHero.chapterNumber,
                coverData = novelHero.coverData,
                subId = novelHero.chapterId,
                mediaType = HomeHubMediaType.NOVEL,
            )
            else -> null
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
