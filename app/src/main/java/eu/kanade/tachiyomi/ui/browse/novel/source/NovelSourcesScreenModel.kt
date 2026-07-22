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
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.util.system.LAST_USED_KEY
import eu.kanade.tachiyomi.util.system.PINNED_KEY
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration.Companion.seconds
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
) : StateScreenModel<NovelSourcesScreenModel.State>(State()) {

    val swipeToHideSource by sourcePreferences.swipeToHideSource().asState(screenModelScope)

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            // Fetch available extensions from repos so the "Not Installed" section has data
            extensionManager.findAvailableExtensions()

            combine(
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
                buildItems(sources, available, installed, untrusted)
            }
                .collectLatest { items ->
                    mutableState.update { it.copy(isLoading = false, items = items) }
                }
        }
    }

    private fun buildItems(
        sources: List<NovelSource>,
        available: List<NovelExtension.Available>,
        installed: List<NovelExtension.Installed>,
        untrusted: List<NovelExtension.Untrusted>,
    ): ImmutableList<NovelSourceUiModel> {
        val installedPkgNames = installed.map { it.pkgName }.toSet()
        // Set of source IDs that have a currently-installed extension. Sources
        // whose extension was uninstalled become stubs and should not appear in
        // the Installed section (they'll reappear in Not Installed once the
        // available extensions list refreshes).
        val installedSourceIds = installed.flatMap { it.sources.map { s -> s.id } }.toSet()

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
            .filter { it.id in installedSourceIds || !it.isStub }
            .groupByTo(map) {
                when {
                    it.isUsedLast -> LAST_USED_KEY
                    Pin.Actual in it.pin -> PINNED_KEY
                    else -> it.lang
                }
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
        val notInstalledItems = if (notInstalled.isEmpty()) {
            emptyList()
        } else {
            listOf(NovelSourceUiModel.Header(NOT_INSTALLED_KEY)) +
                notInstalled.map { NovelSourceUiModel.AvailableExtension(it) }
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

            val before = extensionManager.installedExtensionsFlow.value.count { it.hasUpdate }
            logcat(LogPriority.DEBUG) { "Before refresh: $before installed extensions have hasUpdate=true" }
            logcat(LogPriority.DEBUG) { "Installed extensions: ${extensionManager.installedExtensionsFlow.value.size}, Available: ${extensionManager.availableExtensionsFlow.value.size}" }

            extensionManager.findAvailableExtensions()

            delay(1.seconds)

            val after = extensionManager.installedExtensionsFlow.value.count { it.hasUpdate }
            val installed = extensionManager.installedExtensionsFlow.value
            val available = extensionManager.availableExtensionsFlow.value
            logcat(LogPriority.DEBUG) { "After refresh: $after installed extensions have hasUpdate=true" }
            logcat(LogPriority.DEBUG) { "Installed: ${installed.size}, Available: ${available.size}" }
            installed.forEach { ext ->
                val avail = available.find { it.pkgName == ext.pkgName }
                logcat(LogPriority.DEBUG) {
                    "  ${ext.pkgName}: installed code=${ext.versionCode}, available code=${avail?.versionCode}, hasUpdate=${ext.hasUpdate}"
                }
            }

            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    data class Dialog(val source: NovelSource)

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val items: ImmutableList<NovelSourceUiModel> = persistentListOf(),
    ) {
        val isEmpty = items.isEmpty()
    }

    companion object {
        const val NOT_INSTALLED_KEY = "not_installed"
        const val INSTALLED_KEY = "installed"
    }
}
