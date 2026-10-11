package miko.ui.extensions

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mihon.domain.extensionrepo.service.ExtensionIndexEntry
import tachiyomi.core.common.util.system.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.extension.novel.api.JsPluginApiFacade
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository

/**
 * Shared extension catalog for the Browse tab — one fetch of the JS plugin
 * index + repo APK index across all saved repos, consumed by both the
 * sources screen (Not-Installed section) and the extensions list, mirroring
 * how Android's screens read the same manager flows.
 */
class ExtensionCatalog(
    private val jsPluginApi: JsPluginApiFacade,
    private val pluginRepository: NovelPluginRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class State(
        val installed: List<NovelPlugin.Installed> = emptyList(),
        val available: List<NovelPlugin.Available> = emptyList(),
        val repoExtensions: List<ExtensionIndexEntry> = emptyList(),
        val loaded: Boolean = false,
        val error: Boolean = false,
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            pluginRepository.subscribeAll().collect { plugins ->
                _state.value = _state.value.copy(installed = plugins)
            }
        }
        refresh()
    }

    fun refresh() {
        scope.launch {
            try {
                val available = jsPluginApi.fetchAvailablePlugins()
                val repoExtensions = jsPluginApi.fetchRepoExtensions()
                _state.value = _state.value.copy(
                    available = available,
                    repoExtensions = repoExtensions,
                    loaded = true,
                    error = false,
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "ExtensionCatalog: fetch failed" }
                _state.value = _state.value.copy(loaded = true, error = true)
            }
        }
    }

    fun dispose() = scope.cancel()
}
