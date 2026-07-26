package eu.kanade.tachiyomi.ui.browse.anime.source

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSource
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.core.preference.asState
import eu.kanade.presentation.browse.anime.AnimeSourceUiModel
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
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
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.Pin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class AnimeSourcesScreenModel(
    private val preferences: BasePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getEnabledAnimeSources: GetEnabledAnimeSources = Injekt.get(),
    private val toggleSource: ToggleAnimeSource = Injekt.get(),
    private val toggleSourcePin: ToggleAnimeSourcePin = Injekt.get(),
    private val extensionManager: AnimeExtensionManager = Injekt.get(),
) : StateScreenModel<AnimeSourcesScreenModel.State>(State()) {

    val swipeToHideSource by sourcePreferences.swipeToHideSource().asState(screenModelScope)

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            // Fetch available extensions from repos so the "Not Installed" section has data
            extensionManager.findAvailableExtensions()

            combine(
                getEnabledAnimeSources.subscribe()
                    .catch {
                        logcat(LogPriority.ERROR, it)
                        _events.send(Event.FailedFetchingSources)
                        emit(emptyList<AnimeSource>())
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
        sources: List<AnimeSource>,
        available: List<AnimeExtension.Available>,
        installed: List<AnimeExtension.Installed>,
        untrusted: List<AnimeExtension.Untrusted>,
    ): ImmutableList<AnimeSourceUiModel> {
        val installedPkgNames = installed.map { it.pkgName }.toSet()
        // Set of source IDs that have a currently-installed extension. Sources
        // whose extension was uninstalled become stubs and should not appear in
        // the Installed section.
        val installedSourceIds = installed.flatMap { it.sources.map { s -> s.id } }.toSet()

        // Build the "Installed" section (sources grouped by language) — filter
        // out stub sources whose extension is no longer installed.
        val map = TreeMap<String, MutableList<AnimeSource>> { d1, d2 ->
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

        val installedItems = listOf(AnimeSourceUiModel.Header(INSTALLED_KEY)) +
            byLang.flatMap {
                listOf(
                    AnimeSourceUiModel.Header(it.key),
                *it.value.map { source ->
                    AnimeSourceUiModel.Item(source)
                }.toTypedArray(),
            )
        }

        // Untrusted extensions (installed but not trusted — show with trust icon)
        val untrustedItems = untrusted.map { AnimeSourceUiModel.UntrustedExtension(it) }

        // Build the "Not Installed" section (available extensions not yet installed)
        val notInstalled = available.filter { it.pkgName !in installedPkgNames }
        val notInstalledItems = if (notInstalled.isEmpty()) {
            emptyList()
        } else {
            listOf(AnimeSourceUiModel.Header(NOT_INSTALLED_KEY)) +
                notInstalled.map { AnimeSourceUiModel.AvailableExtension(it) }
        }

        return (installedItems + untrustedItems + notInstalledItems).toImmutableList()
    }

    fun toggleSource(source: AnimeSource) {
        toggleSource.await(source)
    }

    fun togglePin(source: AnimeSource) {
        toggleSourcePin.await(source)
    }

    fun showSourceDialog(source: AnimeSource) {
        mutableState.update { it.copy(dialog = Dialog(source)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun findAvailableExtensions() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isRefreshing = true) }

            extensionManager.findAvailableExtensions()

            // Fake slower refresh so it doesn't seem like it's not doing anything
            delay(1.seconds)

            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    data class Dialog(val source: AnimeSource)

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: ImmutableList<AnimeSourceUiModel> = persistentListOf(),
    ) {
        val isEmpty = items.isEmpty()
    }

    companion object {
        const val NOT_INSTALLED_KEY = "not_installed"
        const val INSTALLED_KEY = "installed"
    }
}
