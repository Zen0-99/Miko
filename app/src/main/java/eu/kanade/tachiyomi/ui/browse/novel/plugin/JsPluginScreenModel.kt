package eu.kanade.tachiyomi.ui.browse.novel.plugin

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.extension.novel.JsNovelPluginManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.extension.novel.model.NovelPlugin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class JsPluginScreenModel(
    private val pluginManager: JsNovelPluginManager = Injekt.get(),
) : StateScreenModel<JsPluginScreenModel.State>(State()) {

    private val searchQuery = MutableStateFlow("")

    init {
        combine(
            pluginManager.installedPluginsFlow,
            pluginManager.availablePluginsFlow,
            searchQuery.debounce(150),
        ) { installed, available, query ->
            Triple(installed, available, query)
        }
            .distinctUntilChanged()
            .map { (installed, available, query) ->
                val filteredInstalled = if (query.isBlank()) installed
                else installed.filter { it.name.contains(query, ignoreCase = true) }
                val filteredAvailable = if (query.isBlank()) available
                else available.filter { it.name.contains(query, ignoreCase = true) }
                State(
                    isLoading = false,
                    installed = filteredInstalled,
                    available = filteredAvailable.filter { avail ->
                        installed.none { it.id == avail.id }
                    },
                )
            }
            .onEach { state -> mutableState.update { state } }
            .launchIn(screenModelScope)
    }

    fun search(query: String) {
        searchQuery.value = query
    }

    fun refresh() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true) }
            pluginManager.refreshAvailablePlugins()
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    fun installPlugin(plugin: NovelPlugin.Available) {
        screenModelScope.launchIO {
            pluginManager.installPlugin(plugin)
        }
    }

    fun uninstallPlugin(plugin: NovelPlugin.Installed) {
        screenModelScope.launchIO {
            pluginManager.uninstallPlugin(plugin)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val installed: List<NovelPlugin.Installed> = emptyList(),
        val available: List<NovelPlugin.Available> = emptyList(),
    )
}
