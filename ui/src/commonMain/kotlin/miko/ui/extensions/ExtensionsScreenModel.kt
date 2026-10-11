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
import mihon.domain.extensionrepo.service.ExtensionIndexEntry
import tachiyomi.core.common.util.system.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.extension.novel.NovelPluginInstallerFacade
import tachiyomi.domain.extension.novel.model.NovelPlugin

/**
 * Available+installed extension list for the browse tab's EXTENSIONS mode.
 * Mirrors the Android extensions screen model: installed flows from the
 * repository; the available list is fetched from all saved repos on
 * [refresh] — JS plugins (installable) plus repo APK-index entries
 * (display-only on iOS). [search] filters locally like Android's queryFilter:
 * extension name, source site/baseUrl, or exact id.
 */
class ExtensionsScreenModel(
    private val catalog: ExtensionCatalog,
    private val installer: NovelPluginInstallerFacade,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val scope = CoroutineScope(Job() + dispatcher)
    private val installMutex = Mutex()

    data class State(
        val installed: List<NovelPlugin.Installed> = emptyList(),
        val available: List<NovelPlugin.Available> = emptyList(),
        val repoExtensions: List<ExtensionIndexEntry> = emptyList(),
        val installingIds: Set<String> = emptySet(),
        val searchQuery: String? = null,
        val availableLoaded: Boolean = false,
        val availableError: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            catalog.state.collect { c ->
                _state.value = _state.value.copy(
                    installed = c.installed,
                    available = c.available,
                    repoExtensions = c.repoExtensions,
                    availableLoaded = c.loaded,
                    availableError = c.error,
                )
            }
        }
    }

    fun refresh() = catalog.refresh()

    fun search(query: String?) {
        _state.value = _state.value.copy(searchQuery = query)
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

internal fun NovelPlugin.Available.matchesExtensionQuery(query: String): Boolean {
    return name.contains(query, ignoreCase = true) ||
        site.contains(query, ignoreCase = true) ||
        id.equals(query, ignoreCase = true)
}

internal fun NovelPlugin.Installed.matchesExtensionQuery(query: String): Boolean {
    return name.contains(query, ignoreCase = true) ||
        site.contains(query, ignoreCase = true) ||
        id.equals(query, ignoreCase = true)
}

internal fun ExtensionIndexEntry.matchesExtensionQuery(query: String): Boolean {
    return name.contains(query, ignoreCase = true) ||
        pkgName.equals(query, ignoreCase = true) ||
        sources.any {
            it.name.contains(query, ignoreCase = true) ||
                it.baseUrl.contains(query, ignoreCase = true)
        }
}
