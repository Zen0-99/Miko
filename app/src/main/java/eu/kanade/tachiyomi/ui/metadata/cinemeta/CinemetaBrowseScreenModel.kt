package eu.kanade.tachiyomi.ui.metadata.cinemeta

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.metadata.MetadataSourceManager
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMetaShort
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CinemetaBrowseScreenModel(
    private val type: String = "movie",
    private val catalogId: String = "top",
    manager: MetadataSourceManager = Injekt.get(),
) : StateScreenModel<CinemetaBrowseScreenModel.State>(State()) {

    data class State(
        val loading: Boolean = false,
        val metas: List<MikoMetaShort> = emptyList(),
        val error: String? = null,
        val skip: Int = 0,
        val hasMore: Boolean = true,
        val searchQuery: String? = null,
    )

    private val source = manager.cinemeta

    init {
        loadNextPage()
    }

    fun loadNextPage() {
        if (state.value.loading || !state.value.hasMore) return
        screenModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val currentQuery = state.value.searchQuery
                val result = if (currentQuery != null) {
                    source.search(type, currentQuery, state.value.skip)
                } else {
                    source.getCatalog(type, catalogId, state.value.skip)
                }
                mutableState.update {
                    it.copy(
                        loading = false,
                        metas = it.metas + result.metas,
                        skip = it.skip + result.metas.size,
                        hasMore = result.metas.isNotEmpty(),
                        error = null,
                    )
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = e.message ?: "Unknown error") }
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
                val result = source.search(type, query, 0)
                mutableState.update {
                    it.copy(
                        loading = false,
                        metas = result.metas,
                        skip = result.metas.size,
                        hasMore = result.metas.isNotEmpty(),
                    )
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun clearSearch() {
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
            try {
                val result = source.getCatalog(type, catalogId, 0)
                mutableState.update {
                    it.copy(
                        loading = false,
                        metas = result.metas,
                        skip = result.metas.size,
                        hasMore = result.metas.isNotEmpty(),
                    )
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun retry() {
        if (state.value.searchQuery != null) {
            search(state.value.searchQuery!!)
        } else {
            mutableState.update { it.copy(error = null, skip = 0, metas = emptyList(), hasMore = true) }
            loadNextPage()
        }
    }
}
