package eu.kanade.tachiyomi.ui.metadata.canonical

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
import eu.kanade.tachiyomi.data.mikodb.MangaNovelIdBridge
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Discovery surface for the manga/books carriers (NCM-007 / Phase 12-01).
 *
 * Backs the canonical catalog grid with map-backed search — one row per
 * canonical work, never per-extension duplicates. Unlike Cinemeta there
 * is no curated "top" feed, so the surface is search-driven.
 */
class CanonicalCatalogScreenModel(
    private val catalog: CanonicalCatalog = Injekt.get(),
) : StateScreenModel<CanonicalCatalogScreenModel.State>(State()) {

    @Immutable
    data class State(
        val medium: String = WorkIdResolver.MEDIUM_MANGA,
        val results: List<MangaNovelIdBridge.WorkRow> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val searchQuery: String? = null,
    )

    /** Called when the content mode switches — re-runs an active search. */
    fun setMedium(medium: String) {
        if (state.value.medium == medium) return
        mutableState.update { it.copy(medium = medium, results = emptyList()) }
        state.value.searchQuery?.let { search(it) }
    }

    fun search(query: String) {
        screenModelScope.launch {
            mutableState.update {
                it.copy(results = emptyList(), searchQuery = query, loading = true, error = null)
            }
            try {
                val results = catalog.search(query, state.value.medium)
                mutableState.update { it.copy(loading = false, results = results) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "canonical catalog search failed" }
                mutableState.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Unknown error (${e::class.simpleName})",
                    )
                }
            }
        }
    }

    fun clearSearch() {
        mutableState.update { it.copy(results = emptyList(), searchQuery = null, loading = false, error = null) }
    }

    fun retry() {
        state.value.searchQuery?.let { search(it) }
    }

    /**
     * Materializes the canonical entry row for [work] and returns the
     * database id to navigate to (MangaScreen / NovelScreen).
     */
    suspend fun getOrCreateEntryId(work: MangaNovelIdBridge.WorkRow): Long =
        if (state.value.medium == WorkIdResolver.MEDIUM_BOOKS) {
            catalog.materializeNovelId(work)
        } else {
            catalog.materializeMangaId(work)
        }
}
