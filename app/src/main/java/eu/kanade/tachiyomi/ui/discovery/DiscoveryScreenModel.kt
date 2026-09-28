package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.ui.model.Carrier
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.update

/**
 * Discovery surface state for [DiscoveryTab] (Phase 15-05).
 *
 * The tab is keyed by [Carrier] — the active carrier is derived from
 * `UiPreferences.contentMode()` via [Carrier.fromContentMode] and selects
 * which catalog surface renders (Cinemeta for shows, canonical catalog for
 * manga/books). This model owns the UI-level state that must survive tab
 * disposal: the shared-top-bar search query/submission and the per-carrier
 * sub-type pill selection (consumed by the carrier pills / filter state
 * landing in 15-06; the shows Movies/Series toggle itself lives inside
 * `CinemetaBrowseScreenModel`).
 *
 * Search submission is delegated by the tab to the catalog screen models —
 * they hold the results; this model only tracks that a search is open.
 */
class DiscoveryScreenModel : StateScreenModel<DiscoveryScreenModel.State>(State()) {

    @Immutable
    data class State(
        /** Non-null while the shared top-bar search field is open. */
        val searchQuery: String? = null,
        /** True once the user has submitted the query (IME search). */
        val searchSubmitted: Boolean = false,
        /** Last selected sub-type pill index per carrier. */
        val subTypeSelections: PersistentMap<Carrier, Int> = persistentMapOf(),
    )

    fun setSearchQuery(query: String?) {
        // Typing un-submits — catalog results only swap in on submit; closing
        // the search field (null) also clears the submitted flag so reopening
        // starts from the idle catalog.
        mutableState.update { it.copy(searchQuery = query, searchSubmitted = false) }
    }

    fun setSearchSubmitted(submitted: Boolean) {
        mutableState.update { it.copy(searchSubmitted = submitted) }
    }

    fun selectSubType(carrier: Carrier, index: Int) {
        mutableState.update {
            it.copy(subTypeSelections = it.subTypeSelections.put(carrier, index))
        }
    }
}
