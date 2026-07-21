package eu.kanade.presentation.track.novel

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import eu.kanade.presentation.track.manga.MangaTrackerSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch

@Composable
fun NovelTrackerSearch(
    state: TextFieldState,
    onDispatchQuery: () -> Unit,
    queryResult: Result<List<MangaTrackSearch>>?,
    selected: MangaTrackSearch?,
    onSelectedChange: (MangaTrackSearch) -> Unit,
    onConfirmSelection: (private: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    supportsPrivateTracking: Boolean,
) {
    MangaTrackerSearch(
        state = state,
        onDispatchQuery = onDispatchQuery,
        queryResult = queryResult,
        selected = selected,
        onSelectedChange = onSelectedChange,
        onConfirmSelection = onConfirmSelection,
        onDismissRequest = onDismissRequest,
        supportsPrivateTracking = supportsPrivateTracking,
    )
}
