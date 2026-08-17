package eu.kanade.tachiyomi.ui.metadata.cinemeta

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.metadata.MetadataSourceManager
import eu.kanade.tachiyomi.metadata.miko.dto.MikoEpisode
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMeta
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CinemetaDetailScreenModel(
    private val type: String,
    private val id: String,
    manager: MetadataSourceManager = Injekt.get(),
) : StateScreenModel<CinemetaDetailScreenModel.State>(State()) {

    data class State(
        val loading: Boolean = true,
        val meta: MikoMeta? = null,
        val error: String? = null,
    )

    private val source = manager.cinemeta

    init {
        loadMeta()
    }

    private fun loadMeta() {
        screenModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val meta = source.getMeta(type, id)
                mutableState.update { it.copy(loading = false, meta = meta, error = null) }
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun retry() {
        loadMeta()
    }

    val episodesBySeason: Map<Int, List<MikoEpisode>>
        get() = state.value.meta?.videos
            ?.groupBy { it.season }
            ?.toSortedMap()
            ?: emptyMap()
}
