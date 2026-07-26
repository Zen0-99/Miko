package eu.kanade.tachiyomi.ui.browse.novel.source

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.novel.interactor.GetEnabledNovelSources
import eu.kanade.domain.source.novel.interactor.ToggleNovelSource
import eu.kanade.domain.source.novel.interactor.ToggleNovelSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.core.preference.asState
import eu.kanade.presentation.browse.novel.NovelSourceUiModel
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.novel.JsNovelPluginManager
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginId
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.LAST_USED_KEY
import eu.kanade.tachiyomi.util.system.PINNED_KEY
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.novel.model.NovelSource
import tachiyomi.domain.source.novel.model.Pin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class NovelSourcesScreenModel(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getEnabledSources: GetEnabledNovelSources = Injekt.get(),
    private val toggleSource: ToggleNovelSource = Injekt.get(),
    private val toggleSourcePin: ToggleNovelSourcePin = Injekt.get(),
    private val extensionManager: NovelExtensionManager = Injekt.get(),
    private val jsPluginManager: JsNovelPluginManager = Injekt.get(),
) : StateScreenModel<NovelSourcesScreenModel.State>(State()) {

    val swipeToHideSource by sourcePreferences.swipeToHideSource().asState(screenModelScope)

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            // Fetch available extensions from repos so the "Not Installed" section has data
            extensionManager.findAvailableExtensions()

            // Combine 6 flows by nesting: (sources + APK flows) combined with
            // (JS flows). combine() supports max 5 flows, so we pair them.
            val apkSide = combine(
                getEnabledSources.subscribe()
                    .catch {
                        logcat(LogPriority.ERROR, it)
                        _events.send(Event.FailedFetchingSources)
                        emit(emptyList<NovelSource>())
                    },
                extensionManager.availableExtensionsFlow,
                extensionManager.installedExtensionsFlow,
                extensionManager.untrustedExtensionsFlow,
            ) { sources, available, installed, untrusted ->
                ApkSide(sources, available, installed, untrusted)
            }

            val jsSide = combine(
                jsPluginManager.availablePluginsFlow,
                jsPluginManager.installedPluginsFlow,
            ) { available, installed ->
                JsSide(available, installed)
            }

            combine(apkSide, jsSide) { apk, js ->
                buildItems(apk.sources, apk.available, apk.installed, apk.untrusted, js)
            }
                .collectLatest { items ->
                    mutableState.update { it.copy(isLoading = false, items = items) }
                }
        }
    }

    private data class ApkSide(
        val sources: List<NovelSource>,
        val available: List<NovelExtension.Available>,
        val installed: List<NovelExtension.Installed>,
        val untrusted: List<NovelExtension.Untrusted>,
    )

    private data class JsSide(
        val available: List<tachiyomi.domain.extension.novel.model.NovelPlugin.Available>,
        val installed: List<tachiyomi.domain.extension.novel.model.NovelPlugin.Installed>,
    )

    private fun buildItems(
        sources: List<NovelSource>,
        available: List<NovelExtension.Available>,
        installed: List<NovelExtension.Installed>,
        untrusted: List<NovelExtension.Untrusted>,
        js: JsSide,
    ): ImmutableList<NovelSourceUiModel> {
        logcat(LogPriority.DEBUG) {
            "NovelSourcesScreenModel.buildItems: sources=${sources.size}, " +
                "apkAvailable=${available.size}, apkInstalled=${installed.size}, " +
                "jsAvailable=${js.available.size}, jsInstalled=${js.installed.size}"
        }
        val installedPkgNames = installed.map { it.pkgName }.toSet()
        // Set of source IDs that have a currently-installed extension. Sources
        // whose extension was uninstalled become stubs and should not appear in
        // the Installed section (they'll reappear in Not Installed once the
        // available extensions list refreshes).
        val installedSourceIds = installed.flatMap { it.sources.map { s -> s.id } }.toSet()
        // JS installed plugin IDs — used to filter out already-installed JS
        // plugins from the "Not Installed" section.
        val installedJsPluginIds = js.installed.map { it.id }.toSet()
        // JS source IDs — the source manager creates sources from installed JS
        // plugins. These IDs should be considered "installed" so they appear in
        // the Installed section.
        val jsSourceIds = js.installed.map { NovelPluginId.toSourceId(it.id) }.toSet()

        // Build the "Installed" section (sources grouped by language) — filter
        // out stub sources whose extension is no longer installed.
        val map = TreeMap<String, MutableList<NovelSource>> { d1, d2 ->
            when {
                d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                d1 == "" && d2 != "" -> 1
                d2 == "" && d1 != "" -> -1
                else -> d1.compareTo(d2)
            }
        }
        val byLang = sources
            .filter { it.id in installedSourceIds || it.id in jsSourceIds || !it.isStub }
            .groupByTo(map) {
                when {
                    it.isUsedLast -> LAST_USED_KEY
                    Pin.Actual in it.pin -> PINNED_KEY
                    else -> it.lang
                }
            }

        // Add JS installed plugins that don't have a source yet as stub
        // NovelSource items in the Installed section. The source manager
        // creates sources asynchronously from installedPluginsFlow, so there
        // can be a window where the plugin is installed but the source hasn't
        // been registered yet. Without this, the plugin disappears from "Not
        // Installed" (filtered by installedJsPluginIds) but doesn't appear in
        // "Installed" (no source yet).
        val sourceIds = sources.map { it.id }.toSet()
        val jsInstalledWithoutSource = js.installed.filter { NovelPluginId.toSourceId(it.id) !in sourceIds }
        for (plugin in jsInstalledWithoutSource) {
            val stubSource = NovelSource(
                id = NovelPluginId.toSourceId(plugin.id),
                lang = plugin.lang,
                name = plugin.name,
                supportsLatest = false,
                isStub = false,
            )
            val lang = plugin.lang.ifBlank { "" }
            map.getOrPut(lang) { mutableListOf() }.add(stubSource)
        }

        logcat(LogPriority.DEBUG) {
            "NovelSourcesScreenModel.buildItems: jsInstalledWithoutSource=${jsInstalledWithoutSource.size}, " +
                "byLang groups=${byLang.size}"
        }

        val installedItems = listOf(NovelSourceUiModel.Header(INSTALLED_KEY)) +
            byLang.flatMap {
                listOf(
                    NovelSourceUiModel.Header(it.key),
                    *it.value.map { source ->
                        NovelSourceUiModel.Item(source)
                    }.toTypedArray(),
                )
            }

        // Untrusted extensions (installed but not trusted — show with trust icon)
        val untrustedItems = untrusted.map { NovelSourceUiModel.UntrustedExtension(it) }

        // Build the "Not Installed" section (available extensions not yet installed)
        val notInstalled = available.filter { it.pkgName !in installedPkgNames }

        // Convert JS available plugins to NovelExtension.Available so they
        // appear in the Not Installed section alongside APK extensions.
        val jsAvailableAsExt = js.available
            .filter { it.id !in installedJsPluginIds }
            .map { plugin ->
                NovelExtension.Available(
                    name = plugin.name,
                    pkgName = JS_PLUGIN_PKG_PREFIX + plugin.id,
                    versionName = plugin.versionName,
                    versionCode = plugin.versionCode.toLong(),
                    libVersion = 1.0,
                    lang = plugin.lang,
                    isNsfw = plugin.isNsfw,
                    sources = listOf(
                        NovelExtension.Available.NovelSource(
                            id = NovelPluginId.toSourceId(plugin.id),
                            lang = plugin.lang,
                            name = plugin.name,
                            baseUrl = plugin.site,
                        ),
                    ),
                    apkName = "",
                    iconUrl = plugin.iconUrl ?: "",
                    repoUrl = plugin.repoUrl,
                )
            }

        val allNotInstalled = notInstalled + jsAvailableAsExt
        val notInstalledItems = if (allNotInstalled.isEmpty()) {
            emptyList()
        } else {
            // Group by language: "all" (multi) at top, then "en" (english),
            // then the rest sorted by display name.
            val grouped = allNotInstalled
                .groupBy { it.lang }
                .toSortedMap { a, b ->
                    when {
                        a == "all" -> -1
                        b == "all" -> 1
                        a == "en" -> -1
                        b == "en" -> 1
                        else -> LocaleHelper.getLocalizedDisplayName(a)
                            .compareTo(LocaleHelper.getLocalizedDisplayName(b))
                    }
                }

            listOf(NovelSourceUiModel.Header(NOT_INSTALLED_KEY)) +
                grouped.flatMap { (lang, exts) ->
                    listOf(NovelSourceUiModel.Header(lang)) +
                        exts.map { NovelSourceUiModel.AvailableExtension(it) }
                }
        }

        return (installedItems + untrustedItems + notInstalledItems).toImmutableList()
    }

    fun toggleSource(source: NovelSource) {
        toggleSource.await(source)
    }

    fun togglePin(source: NovelSource) {
        toggleSourcePin.await(source)
    }

    fun showSourceDialog(source: NovelSource) {
        mutableState.update { it.copy(dialog = Dialog(source)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun findAvailableExtensions() {
        logcat(LogPriority.DEBUG) { "NovelSourcesScreenModel.findAvailableExtensions() called" }
        screenModelScope.launchIO {
            mutableState.update { it.copy(isRefreshing = true) }

            // Refresh both APK extensions and JS plugins
            extensionManager.findAvailableExtensions()
            jsPluginManager.refreshAvailablePlugins()

            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Install a JS plugin (LNReader format) by its [NovelExtension.Available]
     * wrapper. The pkgName is prefixed with [JS_PLUGIN_PKG_PREFIX] to
     * distinguish JS plugins from APK extensions.
     */
    fun installJsPlugin(extension: NovelExtension.Available, onStep: (InstallStep) -> Unit) {
        if (!extension.pkgName.startsWith(JS_PLUGIN_PKG_PREFIX)) return
        screenModelScope.launchIO {
            val pluginId = extension.pkgName.removePrefix(JS_PLUGIN_PKG_PREFIX)
            val plugin = jsPluginManager.availablePluginsFlow.first()
                .find { it.id == pluginId }
            if (plugin != null) {
                onStep(InstallStep.Downloading)
                try {
                    jsPluginManager.installPlugin(plugin)
                    onStep(InstallStep.Installed)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "JS plugin install failed: ${plugin.id}" }
                    onStep(InstallStep.Error)
                }
            } else {
                onStep(InstallStep.Error)
            }
        }
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    data class Dialog(val source: NovelSource)

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: ImmutableList<NovelSourceUiModel> = persistentListOf(),
    ) {
        val isEmpty = items.isEmpty()
    }

    companion object {
        const val NOT_INSTALLED_KEY = "not_installed"
        const val INSTALLED_KEY = "installed"
        const val JS_PLUGIN_PKG_PREFIX = "js_"
    }
}
