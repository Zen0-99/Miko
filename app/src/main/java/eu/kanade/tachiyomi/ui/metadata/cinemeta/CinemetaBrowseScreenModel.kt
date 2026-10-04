package eu.kanade.tachiyomi.ui.metadata.cinemeta

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMetaShort
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CinemetaBrowseScreenModel(
    private val cinemetaCatalogFeed: eu.kanade.tachiyomi.metadata.cinemeta.CinemetaCatalogFeed = Injekt.get(),
) : StateScreenModel<CinemetaBrowseScreenModel.State>(State()) {

    data class State(
        val type: String = "movie",
        val catalogId: String = "top",
        val loading: Boolean = false,
        val metas: List<MikoMetaShort> = emptyList(),
        val error: String? = null,
        val skip: Int = 0,
        val hasMore: Boolean = true,
        val searchQuery: String? = null,
    )

    /** Cached catalog data per type, with a timestamp. Valid for 24h. */
    private data class CacheEntry(
        val metas: List<MikoMetaShort>,
        val skip: Int,
        val hasMore: Boolean,
        val timestamp: Long,
    )

    private val cache = mutableMapOf<String, CacheEntry>()
    private val cacheDurationMs = 24 * 60 * 60 * 1000L // 24 hours

    init {
        loadNextPage()
    }

    private fun isCacheValid(type: String): CacheEntry? {
        val entry = cache[type] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > cacheDurationMs) {
            cache.remove(type)
            return null
        }
        return entry
    }

    /**
     * Cached catalog items for a type without switching to it — pager
     * pages preview their own type during a swipe (the settled page owns
     * [State.metas]). Empty when nothing is cached.
     */
    fun peekCachedMetas(type: String): List<MikoMetaShort> =
        isCacheValid(type)?.metas.orEmpty()

    fun changeType(newType: String) {
        if (state.value.type == newType) return
        // Check cache first — if valid, restore from cache without network call
        val cached = isCacheValid(newType)
        if (cached != null) {
            mutableState.update {
                it.copy(
                    type = newType,
                    metas = cached.metas,
                    skip = cached.skip,
                    hasMore = cached.hasMore,
                    searchQuery = null,
                    loading = false,
                    error = null,
                )
            }
            return
        }
        screenModelScope.launch {
            mutableState.update {
                it.copy(
                    type = newType,
                    metas = emptyList(),
                    skip = 0,
                    hasMore = true,
                    searchQuery = null,
                    loading = true,
                    error = null,
                )
            }
            fetchFirstPage(newType)
        }
    }

    private fun describeError(e: Exception): String {
        logcat(LogPriority.ERROR, e) { "cinemeta browse fetch failed" }
        return e.message ?: "Unknown error (${e::class.simpleName})"
    }

    private fun dedupeMetas(metas: List<MikoMetaShort>): List<MikoMetaShort> =
        cinemetaCatalogFeed.dedupeMetas(metas)

    private suspend fun fetchFirstPage(type: String) {
        try {
            val result = cinemetaCatalogFeed.fetchCatalog(type, "top", 0)
            val metas = dedupeMetas(result.metas)
            val skip = metas.size
            val hasMore = metas.isNotEmpty()
            cache[type] = CacheEntry(metas, skip, hasMore, System.currentTimeMillis())
            mutableState.update {
                it.copy(
                    loading = false,
                    metas = metas,
                    skip = skip,
                    hasMore = hasMore,
                )
            }
        } catch (e: Exception) {
            mutableState.update { it.copy(loading = false, error = describeError(e)) }
        }
    }

    fun loadNextPage() {
        if (state.value.loading || !state.value.hasMore) return
        screenModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val s = state.value
                val result = if (s.searchQuery != null) {
                    cinemetaCatalogFeed.searchCatalog(s.type, s.searchQuery, s.skip)
                } else {
                    cinemetaCatalogFeed.fetchCatalog(s.type, s.catalogId, s.skip)
                }
                val newMetas = dedupeMetas(s.metas + result.metas)
                val newSkip = s.skip + result.metas.size
                val newHasMore = result.metas.isNotEmpty()
                // Update cache for catalog (non-search) loads
                if (s.searchQuery == null) {
                    cache[s.type] = CacheEntry(newMetas, newSkip, newHasMore, System.currentTimeMillis())
                }
                mutableState.update {
                    it.copy(
                        loading = false,
                        metas = newMetas,
                        skip = newSkip,
                        hasMore = newHasMore,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = describeError(e)) }
            }
        }
    }

    fun search(query: String) {
        screenModelScope.launch {
            mutableState.update {
                it.copy(
                    metas = emptyList(),
                    skip = 0,
                    hasMore = true,
                    searchQuery = query,
                    loading = true,
                    error = null,
                )
            }
            try {
                val result = cinemetaCatalogFeed.searchCatalog(state.value.type, query, 0)
                val metas = dedupeMetas(result.metas)
                mutableState.update {
                    it.copy(
                        loading = false,
                        metas = metas,
                        skip = result.metas.size,
                        hasMore = result.metas.isNotEmpty(),
                    )
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = describeError(e)) }
            }
        }
    }

    fun clearSearch() {
        // Restore from cache if available, otherwise re-fetch
        val cached = isCacheValid(state.value.type)
        if (cached != null) {
            mutableState.update {
                it.copy(
                    metas = cached.metas,
                    skip = cached.skip,
                    hasMore = cached.hasMore,
                    searchQuery = null,
                    loading = false,
                    error = null,
                )
            }
            return
        }
        screenModelScope.launch {
            mutableState.update {
                it.copy(
                    metas = emptyList(),
                    skip = 0,
                    hasMore = true,
                    searchQuery = null,
                    loading = true,
                    error = null,
                )
            }
            fetchFirstPage(state.value.type)
        }
    }

    /** Force refresh — clears cache and reloads. Called by pull-to-refresh. */
    fun refresh() {
        cache.remove(state.value.type)
        screenModelScope.launch {
            mutableState.update {
                it.copy(
                    metas = emptyList(),
                    skip = 0,
                    hasMore = true,
                    loading = true,
                    error = null,
                )
            }
            fetchFirstPage(state.value.type)
        }
    }

    fun retry() {
        if (state.value.searchQuery != null) {
            search(state.value.searchQuery!!)
        } else {
            cache.remove(state.value.type)
            mutableState.update { it.copy(error = null, skip = 0, metas = emptyList(), hasMore = true) }
            loadNextPage()
        }
    }

    /**
     * Converts a Cinemeta catalog item into a domain [Anime] record, inserts it
     * into the database (or returns the existing record), and returns the
     * database ID so the caller can navigate to ShowsScreen. Delegates to
     * [CinemetaCatalogFeed] — the same materialization home catalog rows use.
     */
    suspend fun getOrCreateAnimeId(meta: MikoMetaShort): Long =
        cinemetaCatalogFeed.getOrCreateAnimeId(meta)

    companion object {
        const val CINEMETA_SOURCE_ID =
            eu.kanade.tachiyomi.metadata.cinemeta.CinemetaCatalogFeed.CINEMETA_SOURCE_ID
    }
}
