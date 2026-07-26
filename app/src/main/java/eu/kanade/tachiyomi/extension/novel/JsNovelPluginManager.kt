package eu.kanade.tachiyomi.extension.novel

import eu.kanade.tachiyomi.extension.novel.api.NovelPluginApiFacade
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import tachiyomi.data.extension.novel.NovelPluginDownloader
import tachiyomi.data.extension.novel.NovelPluginInstallerFacade
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository

/**
 * Manager for JS-based novel plugins (LNReader-compatible format).
 *
 * This manager handles install, update, and uninstall of JS plugins from
 * LNReader plugin repositories. It is separate from the APK-based
 * [NovelExtensionManager] which handles compiled Kotlin extensions.
 *
 * JS plugins are JavaScript files interpreted at runtime by
 * [eu.kanade.tachiyomi.extension.novel.js.NovelJsRuntime], while APK
 * extensions are compiled Android packages loaded via DexClassLoader.
 */
class JsNovelPluginManager(
    private val repository: NovelPluginRepository,
    private val api: NovelPluginApiFacade,
    private val installer: NovelPluginInstallerFacade,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _installedPlugins = MutableStateFlow<List<NovelPlugin.Installed>>(emptyList())
    val installedPluginsFlow: Flow<List<NovelPlugin.Installed>> = _installedPlugins.asStateFlow()

    private val _availablePlugins = MutableStateFlow<List<NovelPlugin.Available>>(emptyList())
    val availablePluginsFlow: Flow<List<NovelPlugin.Available>> = _availablePlugins.asStateFlow()

    private val _updates = MutableStateFlow<List<NovelPlugin.Installed>>(emptyList())
    val updatesFlow: Flow<List<NovelPlugin.Installed>> = _updates.asStateFlow()

    init {
        scope.launch {
            repository.subscribeAll().collect { plugins ->
                _installedPlugins.value = plugins.map { it.withNormalizedLang() }
                updatePendingUpdates()
            }
        }
    }

    /**
     * Fetch available plugins from all configured repos.
     * The user must manually add repos — no default repo is seeded.
     */
    suspend fun refreshAvailablePlugins() {
        _availablePlugins.value = api.fetchAvailablePlugins().map { it.withNormalizedLang() }
        updatePendingUpdates()
    }

    /**
     * Install a JS plugin from an [available] plugin entry.
     * Downloads the script, verifies checksum, saves to storage, and persists metadata.
     */
    suspend fun installPlugin(plugin: NovelPlugin.Available): NovelPlugin.Installed {
        val installed = installer.install(plugin).withNormalizedLang()
        _installedPlugins.value = _installedPlugins.value
            .filterNot { it.id == installed.id } + installed
        updatePendingUpdates()
        return installed
    }

    /**
     * Uninstall a JS plugin by its ID.
     * Removes script files and deletes metadata from the database.
     */
    suspend fun uninstallPlugin(plugin: NovelPlugin.Installed) {
        uninstallPlugin(plugin.id)
    }

    /**
     * Uninstall a JS plugin by its ID.
     */
    suspend fun uninstallPlugin(pluginId: String) {
        installer.uninstall(pluginId)
        _installedPlugins.value = _installedPlugins.value.filterNot { it.id == pluginId }
        updatePendingUpdates()
    }

    /**
     * Replace an installed plugin with a different version from the available list.
     * Uninstalls the current version and installs the replacement.
     */
    suspend fun replacePlugin(
        installed: NovelPlugin.Installed,
        replacement: NovelPlugin.Available,
    ): NovelPlugin.Installed {
        uninstallPlugin(installed.id)
        return installPlugin(replacement)
    }

    /**
     * Check if an update is available for the given installed plugin.
     */
    fun hasUpdate(installed: NovelPlugin.Installed): Boolean {
        return _availablePlugins.value.any { available ->
            available.id == installed.id && available.versionCode > installed.versionCode
        }
    }

    /**
     * Get the best available update for an installed plugin, if any.
     */
    fun getAvailableUpdate(installed: NovelPlugin.Installed): NovelPlugin.Available? {
        return _availablePlugins.value
            .filter { it.id == installed.id && it.versionCode > installed.versionCode }
            .maxByOrNull { it.versionCode }
    }

    /**
     * Get the installed plugin by ID.
     */
    fun getInstalledPlugin(pluginId: String): NovelPlugin.Installed? {
        return _installedPlugins.value.firstOrNull { it.id == pluginId }
    }

    /**
     * Get the installed plugin by ID as a flow.
     */
    fun getInstalledPluginAsFlow(pluginId: String): Flow<NovelPlugin.Installed?> {
        return _installedPlugins.map { plugins ->
            plugins.firstOrNull { it.id == pluginId }
        }.distinctUntilChanged()
    }

    /**
     * Get the available plugin by ID.
     */
    fun getAvailablePlugin(pluginId: String): NovelPlugin.Available? {
        return _availablePlugins.value.firstOrNull { it.id == pluginId }
    }

    private fun updatePendingUpdates() {
        val bestAvailableByIdVersion = _availablePlugins.value
            .groupBy { it.id }
            .mapValues { (_, plugins) -> plugins.maxByOrNull { it.versionCode } }
        _updates.value = _installedPlugins.value.filter { installed ->
            val best = bestAvailableByIdVersion[installed.id] ?: return@filter false
            best.versionCode > installed.versionCode
        }
    }
}

/**
 * Network-based implementation of [NovelPluginDownloader] that fetches
 * plugin assets via OkHttp.
 */
class NetworkNovelPluginDownloader(
    private val client: OkHttpClient,
) : NovelPluginDownloader {
    override suspend fun download(url: String): ByteArray {
        return client.newCall(GET(url))
            .awaitSuccess()
            .use { response -> response.body.bytes() }
    }
}
