package eu.kanade.tachiyomi.ui.home.hub

import androidx.compose.runtime.Immutable
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.model.ContentMode
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.domain.history.shows.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.i18n.aniyomi.AYMR

@Immutable
data class HomeHubCardItem(
    val id: Long,
    val title: String,
    val coverData: Any?,
    val mediaType: HomeHubMediaType,
    val progressText: String? = null,
)

/**
 * Home Hub state — recently viewed + recently added + greeting + hero card +
 * streak counter + month stats + achievement count + recommendations.
 *
 * Enhanced from the minimal Phase 2.3 implementation with features ported
 * from Tadami's Home Hub.
 */
@Immutable
data class HomeHubState(
    val isLoading: Boolean = true,
    val recentAnime: List<AnimeHistoryWithRelations> = emptyList(),
    val recentManga: List<MangaHistoryWithRelations> = emptyList(),
    val recentNovels: List<NovelHistoryWithRelations> = emptyList(),
    val recentlyAddedAnime: List<RecentlyAddedItem> = emptyList(),
    val recentlyAddedManga: List<RecentlyAddedItem> = emptyList(),
    val recentlyAddedNovels: List<RecentlyAddedItem> = emptyList(),
    // --- Greeting system ---
    val greeting: StringResource = AYMR.strings.home_greeting_default,
    val greetingReady: Boolean = false,
    val userName: String = "",
    // --- Hero card ---
    val hero: HomeHubHero? = null,
    // --- Streak counter ---
    val currentStreak: Int = 0,
    // --- Month stats ---
    val monthStats: MonthStats? = null,
    val librarySize: Int = 0,
    // --- Achievement display ---
    val achievementCount: Int = 0,
    val achievementTotal: Int = 0,
    // --- Recommendations ---
    val recommendations: List<HomeHubCardItem> = emptyList(),
    // --- Mode-aware filtering ---
    val currentMode: ContentMode = ContentMode.MANGA,
    // --- Hidden collections (set of collection IDs hidden from home) ---
    val hiddenAnimeCollections: Set<Long> = emptySet(),
    val hiddenMangaCollections: Set<Long> = emptySet(),
    val hiddenNovelCollections: Set<Long> = emptySet(),
    // --- Selection mode for long-press overlay ---
    val selection: Set<Long> = emptySet(),
) {
    val selectionMode: Boolean
        get() = selection.isNotEmpty()
    val hasAnyRecent: Boolean
        get() = recentAnime.isNotEmpty() || recentManga.isNotEmpty() || recentNovels.isNotEmpty()

    val hasAnyRecentlyAdded: Boolean
        get() = recentlyAddedAnime.isNotEmpty() ||
            recentlyAddedManga.isNotEmpty() ||
            recentlyAddedNovels.isNotEmpty()

    val isEmpty: Boolean
        get() = !isLoading && !hasAnyRecent && !hasAnyRecentlyAdded && hero == null && recommendations.isEmpty()

    // --- Card helpers for unified rendering ---

    val recentAnimeCards: List<HomeHubCardItem>
        get() = recentAnime
            .distinctBy { it.animeId }
            .map {
                HomeHubCardItem(it.animeId, it.title, it.coverData, HomeHubMediaType.SHOWS, animeProgressLabel(it))
            }

    val recentMangaCards: List<HomeHubCardItem>
        get() = recentManga
            .distinctBy { it.mangaId }
            .map {
                HomeHubCardItem(
                    it.mangaId,
                    it.title,
                    it.coverData,
                    HomeHubMediaType.MANGA,
                    "Ch. ${eu.kanade.presentation.util.formatChapterNumber(it.chapterNumber)}",
                )
            }

    val recentNovelCards: List<HomeHubCardItem>
        get() = recentNovels
            .distinctBy { it.novelId }
            .map {
                HomeHubCardItem(
                    it.novelId,
                    it.title,
                    it.coverData,
                    HomeHubMediaType.NOVEL,
                    "Ch. ${eu.kanade.presentation.util.formatChapterNumber(it.chapterNumber)}",
                )
            }

    val recentlyAddedAnimeCards: List<HomeHubCardItem>
        get() = recentlyAddedAnime.map {
            HomeHubCardItem(it.id, it.title, it.coverData, HomeHubMediaType.SHOWS)
        }

    val recentlyAddedMangaCards: List<HomeHubCardItem>
        get() = recentlyAddedManga.map {
            HomeHubCardItem(it.id, it.title, it.coverData, HomeHubMediaType.MANGA)
        }

    val recentlyAddedNovelCards: List<HomeHubCardItem>
        get() = recentlyAddedNovels.map {
            HomeHubCardItem(it.id, it.title, it.coverData, HomeHubMediaType.NOVEL)
        }
}

/**
 * Progress label for a recent-anime card / hero:
 * - Cinemeta movies: runtime ("2h 07min") — a movie has one virtual episode,
 *   so "Ep. 1" is meaningless.
 * - Cinemeta series: "S2 · Ep. 5" — the season is encoded in the episode's
 *   scanlator field ("Season N|★R.R").
 * - Everything else: "Ep. 5".
 */
internal fun animeProgressLabel(item: AnimeHistoryWithRelations): String {
    return when {
        item.isCinemetaMovie ->
            eu.kanade.presentation.util.formatRuntime(item.runtime) ?: "Movie"
        else -> buildString {
            item.cinemetaSeason?.takeIf { it > 0 }?.let { append("S$it · ") }
            append("Ep. ")
            append(eu.kanade.presentation.util.formatEpisodeNumber(item.episodeNumber))
        }
    }
}

@Immutable
data class RecentlyAddedItem(
    val id: Long,
    val title: String,
    val coverData: Any?,
    val sourceId: Long,
    val url: String,
    val mediaType: HomeHubMediaType,
)

/**
 * Hero card data — the most recently watched/read entry with progress info.
 */
@Immutable
data class HomeHubHero(
    val entryId: Long,
    val title: String,
    val progressNumber: Double,
    val chapterTitle: String? = null,
    val coverData: Any?,
    val subId: Long,
    val mediaType: HomeHubMediaType,
    val allRead: Boolean = false,
    // Anime-only extras for the resume label (season prefix, movie runtime).
    val season: Int? = null,
    val runtime: String? = null,
    val isCinemetaMovie: Boolean = false,
)

enum class HomeHubMediaType {
    SHOWS,
    MANGA,
    NOVEL,
    ;

    val key: String
        get() = name.lowercase()

    companion object {
        fun fromKey(key: String): HomeHubMediaType {
            // Legacy fast-cache entries carry the pre-15-04 "anime" key.
            if (key == "anime") return SHOWS
            return entries.firstOrNull { it.key == key } ?: SHOWS
        }
    }
}
