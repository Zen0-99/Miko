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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.util.system.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.extension.novel.NovelPluginInstallerFacade
import tachiyomi.domain.extension.novel.api.JsPluginApiFacade
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository

/**
 * Available+installed JS plugin list for the browse tab's EXTENSIONS mode.
 * Mirrors the Android extensions screen model: installed plugins flow from
 * the repository; the available list is fetched from all saved repos on
 * [refresh] and merged against installed IDs at render time.
 */
class ExtensionsScreenModel(
    private val pluginRepository: NovelPluginRepository,
    private val jsPluginApi: JsPluginApiFacade,
    private val installer: NovelPluginInstallerFacade,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val scope = CoroutineScope(Job() + dispatcher)
    private val installMutex = Mutex()

    data class State(
        val installed: List<NovelPlugin.Installed> = emptyList(),
        val available: List<NovelPlugin.Available> = emptyList(),
        val installingIds: Set<String> = emptySet(),
        val availableLoaded: Boolean = false,
        val availableError: Boolean = false,
    )

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
                _state.value = _state.value.copy(
                    available = available,
                    availableLoaded = true,
                    availableError = false,
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "ExtensionsScreenModel: available fetch failed" }
                _state.value = _state.value.copy(availableLoaded = true, availableError = true)
            }
        }
    }

    fun install(plugin: NovelPlugin.Available) {
        scope.launch {
            installMutex.withLock {
                _state.value = _state.value.copy(
                    installingIds = _state.value.installingIds + plugin.id,
                )
                try {
                    installer.install(plugin)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Plugin install failed id=${plugin.id}" }
                } finally {
                    _state.value = _state.value.copy(
                        installingIds = _state.value.installingIds - plugin.id,
                    )
                }
            }
        }
    }

    fun uninstall(pluginId: String) {
        scope.launch {
            installMutex.withLock {
                try {
                    installer.uninstall(pluginId)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Plugin uninstall failed id=$pluginId" }
                }
            }
        }
    }

    fun dispose() = scope.cancel()
}
