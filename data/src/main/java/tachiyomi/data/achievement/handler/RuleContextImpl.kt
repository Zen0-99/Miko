package tachiyomi.data.achievement.handler

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.achievement.rules.GenreAliases
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository

class RuleContextImpl(
    private val mangaHandler: MangaDatabaseHandler,
    private val animeHandler: AnimeDatabaseHandler,
    private val novelHandler: NovelDatabaseHandler,
    private val mangaRepository: MangaRepository,
    private val animeRepository: AnimeRepository,
    private val novelRepository: NovelRepository,
    private val diversityChecker: DiversityAchievementChecker,
    private val streakChecker: StreakAchievementChecker,
    private val featureCollector: FeatureUsageCollector,
    private val allProgress: Map<String, AchievementProgress>,
    private val allAchievementsMap: Map<String, Achievement>,
) : RuleContext {

    override suspend fun getChaptersRead(category: AchievementCategory): Int {
        return when (category) {
            AchievementCategory.MANGA -> {
                mangaHandler.awaitOneOrNull { chaptersQueries.getTotalReadChapterCount() }?.toInt() ?: 0
            }
            AchievementCategory.ANIME -> {
                animeHandler.awaitOneOrNull { episodesQueries.getTotalSeenEpisodeCount() }?.toInt() ?: 0
            }
            AchievementCategory.NOVEL -> {
                novelHandler.awaitOneOrNull { novelchaptersQueries.getTotalReadNovelChapterCount() }?.toInt() ?: 0
            }
            AchievementCategory.BOTH, AchievementCategory.SECRET -> {
                val manga = mangaHandler.awaitOneOrNull { chaptersQueries.getTotalReadChapterCount() }?.toInt() ?: 0
                val anime = animeHandler.awaitOneOrNull { episodesQueries.getTotalSeenEpisodeCount() }?.toInt() ?: 0
                val novel = novelHandler.awaitOneOrNull { novelchaptersQueries.getTotalReadNovelChapterCount() }?.toInt() ?: 0
                manga + anime + novel
            }
        }
    }

    override suspend fun getLibraryCount(category: AchievementCategory): Int {
        return when (category) {
            AchievementCategory.MANGA -> mangaRepository.getLibraryManga().size
            AchievementCategory.ANIME -> animeRepository.getLibraryAnime().size
            AchievementCategory.NOVEL -> novelRepository.getLibraryNovels().size
            AchievementCategory.BOTH, AchievementCategory.SECRET -> {
                mangaRepository.getLibraryManga().size +
                    animeRepository.getLibraryAnime().size +
                    novelRepository.getLibraryNovels().size
            }
        }
    }

    override suspend fun getCompletedCount(category: AchievementCategory): Int {
        val completedStatus = SManga.COMPLETED.toLong()
        return when (category) {
            AchievementCategory.MANGA -> mangaRepository.getLibraryManga().count { it.manga.status == completedStatus }
            AchievementCategory.ANIME -> animeRepository.getLibraryAnime().count { it.anime.status == SAnime.COMPLETED.toLong() }
            AchievementCategory.NOVEL -> novelRepository.getLibraryNovels().count { it.novel.status == completedStatus }
            AchievementCategory.BOTH, AchievementCategory.SECRET -> {
                mangaRepository.getLibraryManga().count { it.manga.status == completedStatus } +
                    animeRepository.getLibraryAnime().count { it.anime.status == SAnime.COMPLETED.toLong() } +
                    novelRepository.getLibraryNovels().count { it.novel.status == completedStatus }
            }
        }
    }

    override suspend fun getOngoingCount(category: AchievementCategory): Int {
        val ongoingStatus = SManga.ONGOING.toLong()
        return when (category) {
            AchievementCategory.MANGA -> mangaRepository.getLibraryManga().count { it.manga.status == ongoingStatus }
            AchievementCategory.ANIME -> animeRepository.getLibraryAnime().count { it.anime.status == SAnime.ONGOING.toLong() }
            AchievementCategory.NOVEL -> novelRepository.getLibraryNovels().count { it.novel.status == ongoingStatus }
            AchievementCategory.BOTH, AchievementCategory.SECRET -> {
                mangaRepository.getLibraryManga().count { it.manga.status == ongoingStatus } +
                    animeRepository.getLibraryAnime().count { it.anime.status == SAnime.ONGOING.toLong() } +
                    novelRepository.getLibraryNovels().count { it.novel.status == ongoingStatus }
            }
        }
    }

    override suspend fun getGenreDiversity(category: AchievementCategory): Int {
        return when (category) {
            AchievementCategory.MANGA -> diversityChecker.getMangaGenreDiversity()
            AchievementCategory.ANIME -> diversityChecker.getAnimeGenreDiversity()
            AchievementCategory.NOVEL -> diversityChecker.getNovelGenreDiversity()
            AchievementCategory.BOTH, AchievementCategory.SECRET -> diversityChecker.getGenreDiversity()
        }
    }

    override suspend fun getSourceDiversity(category: AchievementCategory): Int {
        return when (category) {
            AchievementCategory.MANGA -> diversityChecker.getMangaSourceDiversity()
            AchievementCategory.ANIME -> diversityChecker.getAnimeSourceDiversity()
            AchievementCategory.NOVEL -> diversityChecker.getNovelSourceDiversity()
            AchievementCategory.BOTH, AchievementCategory.SECRET -> diversityChecker.getSourceDiversity()
        }
    }

    override suspend fun hasCompletedWithMinChapters(category: AchievementCategory, minChapters: Int): Boolean {
        return when (category) {
            AchievementCategory.MANGA -> {
                mangaHandler.awaitOneOrNull { mangasQueries.hasCompletedLibraryMangaWithMinReadChapters(minChapters.toLong()) } ?: false
            }
            AchievementCategory.ANIME -> {
                // No anime equivalent query — anime don't have "chapters"
                false
            }
            AchievementCategory.NOVEL -> {
                novelHandler.awaitOneOrNull { novelsQueries.hasCompletedLibraryNovelWithMinReadChapters(minChapters.toLong()) } ?: false
            }
            AchievementCategory.BOTH, AchievementCategory.SECRET -> {
                val manga = mangaHandler.awaitOneOrNull { mangasQueries.hasCompletedLibraryMangaWithMinReadChapters(minChapters.toLong()) } ?: false
                val novel = novelHandler.awaitOneOrNull { novelsQueries.hasCompletedLibraryNovelWithMinReadChapters(minChapters.toLong()) } ?: false
                manga || novel
            }
        }
    }

    override suspend fun hasLibraryGenre(genre: String): Int {
        // NOTE: matching is done in Kotlin instead of via SQL LOWER() because
        // SQLite's built-in LOWER() only lowercases ASCII - it leaves Cyrillic
        // (and other non-Latin scripts) untouched, which made the SQL genre
        // comparison effectively case-SENSITIVE for localized genres like
        // "Гарем"/"гарем". GenreAliases.genreMatches uses Kotlin's Unicode-aware
        // lowercase() plus ё/э folding and alias expansion, so a library entry
        // counts regardless of the casing/spelling the source used.
        val canonical = listOf(genre)
        val mangaGenres = mangaRepository.getLibraryManga().mapNotNull { it.manga.genre }
        val animeGenres = animeRepository.getLibraryAnime().mapNotNull { it.anime.genre }
        val novelGenres = novelRepository.getLibraryNovels().mapNotNull { it.novel.genre }
        return (mangaGenres + animeGenres + novelGenres).count { entryGenres ->
            entryGenres.orEmpty().any { g -> GenreAliases.genreMatches(g, canonical) }
        }
    }

    override suspend fun hasLibraryTitleLike(pattern: String): Boolean {
        return GenreAliases.allTitleSearchTerms(pattern).any { term ->
            val manga = mangaRepository.getLibraryManga().any { it.manga.title.contains(term, ignoreCase = true) }
            val anime = animeRepository.getLibraryAnime().any { it.anime.title.contains(term, ignoreCase = true) }
            val novel = novelRepository.getLibraryNovels().any { it.novel.title.contains(term, ignoreCase = true) }
            manga || anime || novel
        }
    }

    override suspend fun getCurrentStreak(): Int {
        return streakChecker.getCurrentStreak()
    }

    override suspend fun hasSessionInTimeRange(startHour: Int, endHour: Int): Boolean {
        return featureCollector.hasSessionInTimeRange(startHour, endHour)
    }

    override suspend fun getFeatureCount(feature: AchievementEvent.Feature): Int {
        return featureCollector.getFeatureCount(feature)
    }

    override suspend fun getMaxSessionDuration(): Long {
        return featureCollector.getMaxSessionDuration()
    }

    override suspend fun getUnlockedAchievementsCountExcluding(metaIds: Set<String>): Int {
        return allProgress.values
            .count { it.isUnlocked && it.achievementId !in metaIds }
    }

    override suspend fun getCurrentPoints(): Int {
        return allProgress.values
            .filter { it.isUnlocked }
            .sumOf { progress ->
                val achievement = allAchievementsMap[progress.achievementId] ?: return@sumOf 0
                if (achievement.isTiered) {
                    achievement.tiers?.take(progress.currentTier)?.sumOf { it.points } ?: 0
                } else {
                    achievement.points
                }
            }
    }
}
