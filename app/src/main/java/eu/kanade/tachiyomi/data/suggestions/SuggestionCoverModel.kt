package eu.kanade.tachiyomi.data.suggestions

import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.NovelCover

/**
 * Builds a cover model for a [SuggestionItem] suitable for Coil's AsyncImage.
 *
 * Returns the raw URL string when no native source target is available.
 */
fun suggestionCoverModel(item: SuggestionItem): Any? {
    val url = item.thumbnailUrl?.takeIf { it.isNotBlank() } ?: return null
    val sourceId = item.nativeSourceTarget?.sourceId

    return when (item.mediaType) {
        SuggestionMediaType.MANGA -> if (sourceId != null) {
            MangaCover(
                mangaId = SUGGESTION_TEMP_ENTRY_ID,
                sourceId = sourceId,
                isMangaFavorite = false,
                url = url,
                lastModified = 0L,
            )
        } else {
            url
        }
        SuggestionMediaType.ANIME -> if (sourceId != null) {
            AnimeCover(
                animeId = SUGGESTION_TEMP_ENTRY_ID,
                sourceId = sourceId,
                isAnimeFavorite = false,
                url = url,
                lastModified = 0L,
            )
        } else {
            url
        }
        SuggestionMediaType.NOVEL -> if (sourceId != null) {
            NovelCover(
                novelId = SUGGESTION_TEMP_ENTRY_ID,
                sourceId = sourceId,
                isNovelFavorite = false,
                url = url,
                lastModified = 0L,
            )
        } else {
            url
        }
    }
}

private const val SUGGESTION_TEMP_ENTRY_ID = -1L
