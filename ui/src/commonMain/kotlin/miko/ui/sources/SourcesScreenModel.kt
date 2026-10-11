package miko.ui.sources

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import mihon.domain.extensionrepo.service.ExtensionIndexEntry
import miko.ui.extensions.ExtensionCatalog
import tachiyomi.domain.extension.novel.langDisplayName
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.source.novel.service.NovelSourceManager

/**
 * Browse-tab sources list — Android's NovelSourcesScreenModel shape:
 * an "Installed" section (installed sources grouped by language), then a
 * collapsible "Not Installed" section (repo APK entries + available JS
 * plugins, minus the ones already installed — also grouped by language).
 */
class SourcesScreenModel(
    private val novelSourceManager: NovelSourceManager,
    private val catalog: ExtensionCatalog,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    sealed interface ListItem {
        data class Header(val key: String, val title: String, val section: String?) : ListItem
        data class Source(val source: NovelCatalogueSource) : ListItem
        data class JsExtension(val plugin: NovelPlugin.Available) : ListItem
        data class ApkExtension(val entry: ExtensionIndexEntry) : ListItem
    }

    data class State(
        val isInitialized: Boolean = false,
        val installedSources: List<NovelCatalogueSource> = emptyList(),
        val items: List<ListItem> = emptyList(),
        val notInstalledExpanded: Boolean = true,
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val expandedFlow = MutableStateFlow(true)

    init {
        scope.launch {
            combine(
                novelSourceManager.isInitialized,
                novelSourceManager.catalogueSources,
                catalog.state,
                expandedFlow,
            ) { initialized, sources, catalogState, expanded ->
                State(
                    isInitialized = initialized,
                    installedSources = sources,
                    items = buildItems(sources, catalogState, expanded),
                    notInstalledExpanded = expanded,
                )
            }.collect { _state.value = it }
        }
    }

    fun toggleNotInstalled() {
        expandedFlow.value = !expandedFlow.value
    }

    private fun buildItems(
        sources: List<NovelCatalogueSource>,
        catalog: ExtensionCatalog.State,
        expanded: Boolean,
    ): List<ListItem> {
        val items = mutableListOf<ListItem>()

        // Installed — per-language sub-headers inside the section.
        if (sources.isNotEmpty()) {
            items += ListItem.Header(INSTALLED_KEY, "", null)
            sources
                .groupBy { it.lang }
                .entries
                .sortedWith { a, b -> langComparator.compare(a.key, b.key) }
                .forEach { (lang, langSources) ->
                    items += ListItem.Header(lang, "", INSTALLED_KEY)
                    langSources.forEach { items += ListItem.Source(it) }
                }
        }

        // Not Installed — APK index rows then JS plugins per language,
        // excluding anything already installed.
        val installedIds = catalog.installed.map { it.id }.toSet()
        val jsNotInstalled = catalog.available
            .filterNot { it.isKotlinExtension }
            .filterNot { it.id in installedIds }
        val notInstalledCount = catalog.repoExtensions.size + jsNotInstalled.size
        if (notInstalledCount > 0) {
            items += ListItem.Header(NOT_INSTALLED_KEY, "", null)
            if (expanded) {
                val groups = mutableMapOf<String, MutableList<ListItem>>()
                catalog.repoExtensions.forEach { entry ->
                    groups.getOrPut(entry.lang.ifBlank { "all" }) { mutableListOf() }
                        .add(ListItem.ApkExtension(entry))
                }
                jsNotInstalled.forEach { plugin ->
                    groups.getOrPut(plugin.lang.ifBlank { "all" }) { mutableListOf() }
                        .add(ListItem.JsExtension(plugin))
                }
                groups.entries
                    .sortedWith { a, b -> langComparator.compare(a.key, b.key) }
                    .forEach { (lang, groupItems) ->
                        items += ListItem.Header(lang, "", NOT_INSTALLED_KEY)
                        items += groupItems
                    }
            }
        }

        return items
    }

    fun dispose() = scope.cancel()

    companion object {
        const val INSTALLED_KEY = "installed"
        const val NOT_INSTALLED_KEY = "not_installed"

        private val langComparator = Comparator<String> { a, b ->
            when {
                a == "all" && b != "all" -> -1
                b == "all" && a != "all" -> 1
                a == "en" && b != "en" -> -1
                b == "en" && a != "en" -> 1
                a == b -> 0
                else -> langDisplayName(a).lowercase().compareTo(langDisplayName(b).lowercase())
            }
        }
    }
}
