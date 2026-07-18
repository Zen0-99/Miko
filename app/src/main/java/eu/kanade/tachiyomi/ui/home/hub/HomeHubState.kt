package eu.kanade.tachiyomi.ui.home.hub

import androidx.compose.runtime.Immutable
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations

/**
 * Minimal Home Hub state — recently viewed + recently added only.
 * No greeting, no hero card (per feature plan decision).
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
) {
    val hasAnyRecent: Boolean
        get() = recentAnime.isNotEmpty() || recentManga.isNotEmpty() || recentNovels.isNotEmpty()

    val hasAnyRecentlyAdded: Boolean
        get() = recentlyAddedAnime.isNotEmpty() || recentlyAddedManga.isNotEmpty() ||
            recentlyAddedNovels.isNotEmpty()

    val isEmpty: Boolean
        get() = !isLoading && !hasAnyRecent && !hasAnyRecentlyAdded
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

enum class HomeHubMediaType {
    ANIME, MANGA, NOVEL
}
