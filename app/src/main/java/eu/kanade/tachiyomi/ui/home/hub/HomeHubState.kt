package eu.kanade.tachiyomi.ui.home.hub

import androidx.compose.runtime.Immutable
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.model.ContentMode
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
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
    // --- Hidden categories (set of category IDs hidden from home) ---
    val hiddenAnimeCategories: Set<Long> = emptySet(),
    val hiddenMangaCategories: Set<Long> = emptySet(),
    val hiddenNovelCategories: Set<Long> = emptySet(),
) {
    val hasAnyRecent: Boolean
        get() = recentAnime.isNotEmpty() || recentManga.isNotEmpty() || recentNovels.isNotEmpty()

    val hasAnyRecentlyAdded: Boolean
        get() = recentlyAddedAnime.isNotEmpty() || recentlyAddedManga.isNotEmpty() ||
            recentlyAddedNovels.isNotEmpty()

    val isEmpty: Boolean
        get() = !isLoading && !hasAnyRecent && !hasAnyRecentlyAdded && hero == null && recommendations.isEmpty()

    // --- Card helpers for unified rendering ---

    val recentAnimeCards: List<HomeHubCardItem>
        get() = recentAnime.map {
            HomeHubCardItem(it.animeId, it.title, it.coverData, HomeHubMediaType.ANIME, "Ep. ${it.episodeNumber}")
        }

    val recentMangaCards: List<HomeHubCardItem>
        get() = recentManga.map {
            HomeHubCardItem(it.mangaId, it.title, it.coverData, HomeHubMediaType.MANGA, "Ch. ${it.chapterNumber}")
        }

    val recentNovelCards: List<HomeHubCardItem>
        get() = recentNovels.map {
            HomeHubCardItem(it.novelId, it.title, it.coverData, HomeHubMediaType.NOVEL, "Ch. ${it.chapterNumber}")
        }

    val recentlyAddedAnimeCards: List<HomeHubCardItem>
        get() = recentlyAddedAnime.map {
            HomeHubCardItem(it.id, it.title, it.coverData, HomeHubMediaType.ANIME)
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
    val coverData: Any?,
    val subId: Long,
    val mediaType: HomeHubMediaType,
)

enum class HomeHubMediaType {
    ANIME, MANGA, NOVEL,
    ;

    val key: String
        get() = name.lowercase()

    companion object {
        fun fromKey(key: String): HomeHubMediaType {
            return entries.firstOrNull { it.key == key } ?: ANIME
        }
    }
}
