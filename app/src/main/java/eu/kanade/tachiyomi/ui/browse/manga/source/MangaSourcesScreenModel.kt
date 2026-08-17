package eu.kanade.tachiyomi.ui.browse.manga.source

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.manga.interactor.GetEnabledMangaSources
import eu.kanade.domain.source.manga.interactor.ToggleExcludeFromMangaDataSaver
import eu.kanade.domain.source.manga.interactor.ToggleMangaSource
import eu.kanade.domain.source.manga.interactor.ToggleMangaSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.source.service.SourcePreferences.DataSaver
import eu.kanade.core.preference.asState
import eu.kanade.presentation.browse.manga.MangaSourceUiModel
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.util.system.LAST_USED_KEY
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.PINNED_KEY
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration.Companion.seconds
import tachiyomi.domain.source.manga.model.Pin
import tachiyomi.domain.source.manga.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class MangaSourcesScreenModel(
    private val preferences: BasePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getEnabledSources: GetEnabledMangaSources = Injekt.get(),
    private val toggleSource: ToggleMangaSource = Injekt.get(),
    private val toggleSourcePin: ToggleMangaSourcePin = Injekt.get(),
    private val extensionManager: MangaExtensionManager = Injekt.get(),
    // SY -->
    private val toggleExcludeFromMangaDataSaver: ToggleExcludeFromMangaDataSaver = Injekt.get(),
    // SY <--
) : StateScreenModel<MangaSourcesScreenModel.State>(State()) {

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
                        emit(emptyList<Source>())
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
        // SY -->
        sourcePreferences.dataSaver().changes()
            .onEach {
                mutableState.update {
                    it.copy(
                        dataSaverEnabled = sourcePreferences.dataSaver().get() != DataSaver.NONE,
                    )
                }
            }
            .launchIn(screenModelScope)
        // SY <--
    }

    private fun buildItems(
        sources: List<Source>,
        available: List<MangaExtension.Available>,
        installed: List<MangaExtension.Installed>,
        untrusted: List<MangaExtension.Untrusted>,
    ): ImmutableList<MangaSourceUiModel> {
        val installedPkgNames = installed.map { it.pkgName }.toSet()
        // Set of source IDs that have a currently-installed extension. Sources
        // whose extension was uninstalled become stubs and should not appear in
        // the Installed section.
        val installedSourceIds = installed.flatMap { it.sources.map { s -> s.id } }.toSet()

        // Build the "Installed" section (sources grouped by language) — filter
        // out stub sources whose extension is no longer installed.
        val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
            // Sources without a lang defined will be placed at the end
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

        val installedItems = listOf(MangaSourceUiModel.Header(INSTALLED_KEY)) +
            byLang.flatMap {
                listOf(
                    MangaSourceUiModel.Header(it.key),
                    *it.value.map { source ->
                        MangaSourceUiModel.Item(source)
                    }.toTypedArray(),
                )
            }

        // Build the "Not Installed" section (available extensions not yet installed
        // and not currently untrusted — untrusted extensions are shown separately
        // with a trust button, not as installable available extensions)
        val untrustedPkgNames = untrusted.map { it.pkgName }.toSet()
        val notInstalled = available.filter { it.pkgName !in installedPkgNames && it.pkgName !in untrustedPkgNames }
        val notInstalledItems = if (notInstalled.isEmpty()) {
            emptyList()
        } else {
            // Group by language: "all" (multi) at top, then "en" (english),
            // then the rest sorted by display name.
            val grouped = notInstalled
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

            listOf(MangaSourceUiModel.Header(NOT_INSTALLED_KEY)) +
                grouped.flatMap { (lang, exts) ->
                    listOf(MangaSourceUiModel.Header(lang)) +
                        exts.map { MangaSourceUiModel.AvailableExtension(it) }
                }
        }

        // Untrusted extensions (installed but not trusted — show with trust icon)
        val untrustedItems = untrusted.map { MangaSourceUiModel.UntrustedExtension(it) }

        return (installedItems + untrustedItems + notInstalledItems).toImmutableList()
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun togglePin(source: Source) {
        toggleSourcePin.await(source)
    }

    // SY -->
    fun toggleExcludeFromMangaDataSaver(source: Source) {
        toggleExcludeFromMangaDataSaver.await(source)
    }
    // SY <--

    fun showSourceDialog(source: Source) {
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

    data class Dialog(val source: Source)

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: ImmutableList<MangaSourceUiModel> = persistentListOf(),
        // SY -->
        val dataSaverEnabled: Boolean = false,
        // SY <--
    ) {
        val isEmpty = items.isEmpty()
    }

    companion object {
        const val NOT_INSTALLED_KEY = "not_installed"
        const val INSTALLED_KEY = "installed"
    }
}
