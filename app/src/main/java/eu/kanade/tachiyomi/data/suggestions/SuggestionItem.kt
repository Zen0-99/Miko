package eu.kanade.tachiyomi.data.suggestions

import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import java.io.Serializable

/**
 * Reason describing where a [SuggestionItem] came from. Used to drive both
 * the final-score weighting (see [SuggestionSourceWeight]) and the
 * "source badge" rendered in the UI.
 */
enum class SuggestionReason {
    RELATED,
    EXTERNAL_ANILIST,
    EXTERNAL_MAL,
    EXTERNAL_MU,
    EXTERNAL_NU,
    SEARCH_TITLE,
    SEARCH_AUTHOR,
    SEARCH_GENRE,
    POPULAR_BACKFILL,
}

data class SuggestionItem(
    val title: String,
    val searchQueries: List<String> = listOf(title),
    val thumbnailUrl: String?,
    val providerName: String,
    val providerUrl: String,
    val providerId: String?,
    val mediaType: SuggestionMediaType,
    val reason: SuggestionReason = SuggestionReason.SEARCH_TITLE,
) : Serializable {

    val searchQuery: String
        get() = searchQueries.firstOrNull { it.isNotBlank() } ?: title

    val nativeSourceTarget: NativeSourceTarget?
        get() {
            val id = providerId ?: return null
            val separatorIndex = id.indexOf(':')
            if (separatorIndex <= 0 || separatorIndex == id.lastIndex) return null
            val sourceId = id.substring(0, separatorIndex).toLongOrNull() ?: return null
            val url = id.substring(separatorIndex + 1).takeIf { it.isNotBlank() } ?: return null
            return NativeSourceTarget(sourceId, url)
        }
}

data class NativeSourceTarget(
    val sourceId: Long,
    val url: String,
) : Serializable
