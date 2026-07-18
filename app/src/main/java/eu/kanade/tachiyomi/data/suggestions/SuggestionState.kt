package eu.kanade.tachiyomi.data.suggestions

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SuggestionState {
    data object Idle : SuggestionState
    data object Loading : SuggestionState
    data object Disabled : SuggestionState
    data class Empty(val message: String? = null) : SuggestionState

    @Immutable
    data class Success(val items: List<SuggestionItem>, val hasMore: Boolean = false) : SuggestionState
    data class Error(val message: String) : SuggestionState
}
