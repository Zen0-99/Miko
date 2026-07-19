package eu.kanade.tachiyomi.ui.browse.anime.extension.details

import android.content.Context
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.extension.anime.interactor.AnimeExtensionSourceItem
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionSources
import eu.kanade.domain.source.anime.interactor.ToggleAnimeIncognito
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionDetailsScreenModel(
    pkgName: String,
    context: Context,
    private val network: NetworkHelper = Injekt.get(),
    private val extensionManager: AnimeExtensionManager = Injekt.get(),
    private val getExtensionSources: GetAnimeExtensionSources = Injekt.get(),
    private val toggleSource: ToggleAnimeSource = Injekt.get(),
    private val toggleIncognito: ToggleAnimeIncognito = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
    private val getFavorites: GetAnimeFavorites = Injekt.get(),
) : StateScreenModel<AnimeExtensionDetailsScreenModel.State>(State()) {

    private val _events: Channel<AnimeExtensionDetailsEvent> = Channel()
    val events: Flow<AnimeExtensionDetailsEvent> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            launch {
                extensionManager.installedExtensionsFlow
                    .map { it.firstOrNull { extension -> extension.pkgName == pkgName } }
                    .collectLatest { extension ->
                        if (extension == null) {
                            _events.send(AnimeExtensionDetailsEvent.Uninstalled)
                            return@collectLatest
                        }
                        mutableState.update { state ->
                            state.copy(extension = extension)
                        }
                    }
            }
            launch {
                state.collectLatest { state ->
                    if (state.extension == null) return@collectLatest
                    getExtensionSources.subscribe(state.extension)
                        .map {
                            it.sortedWith(
                                compareBy(
                                    { !it.enabled },
                                    { item ->
                                        item.source.name.takeIf { item.labelAsName }
                                            ?: LocaleHelper.getSourceDisplayName(
                                                item.source.lang,
                                                context,
                                            ).lowercase()
                                    },
                                ),
                            )
                        }
                        .catch { throwable ->
                            logcat(LogPriority.ERROR, throwable)
                            mutableState.update { it.copy(_sources = persistentListOf()) }
                        }
                        .collectLatest { sources ->
                            mutableState.update { it.copy(_sources = sources.toImmutableList()) }
                        }
                }
            }
            launch {
                preferences.incognitoAnimeExtensions()
                    .changes()
                    .map { pkgName in it }
                    .distinctUntilChanged()
                    .collectLatest { isIncognito ->
                        mutableState.update { it.copy(isIncognito = isIncognito) }
                    }
            }
            // Load favorites (migrate items) for all sources in this extension
            launch {
                state
                    .map { it.sources }
                    .distinctUntilChanged()
                    .flatMapLatest { sources ->
                        if (sources.isEmpty()) {
                            kotlinx.coroutines.flow.flowOf(persistentListOf<MigrateAnimeItem>())
                        } else {
                            combine(
                                sources.map { source ->
                                    getFavorites.subscribe(source.source.id)
                                        .catch { throwable ->
                                            logcat(LogPriority.ERROR, throwable)
                                            emit(persistentListOf())
                                        }
                                        .map { anime ->
                                            anime.map {
                                                MigrateAnimeItem(
                                                    sourceId = source.source.id,
                                                    sourceName = source.source.name,
                                                    anime = it,
                                                )
                                            }
                                        }
                                },
                            ) { lists ->
                                lists.flatMap { it }.sortedWith(
                                    compareBy(String.CASE_INSENSITIVE_ORDER) { it.anime.title },
                                ).toImmutableList()
                            }
                        }
                    }
                    .collectLatest { items ->
                        mutableState.update { it.copy(_migrateItems = items) }
                    }
            }
        }
    }

    fun clearCookies() {
        val extension = state.value.extension ?: return

        val urls = extension.sources
            .filterIsInstance<HttpSource>()
            .mapNotNull { it.baseUrl.takeUnless { url -> url.isEmpty() } }
            .distinct()

        val cleared = urls.sumOf {
            try {
                network.cookieJar.remove(it.toHttpUrl())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to clear cookies for $it" }
                0
            }
        }

        logcat { "Cleared $cleared cookies for: ${urls.joinToString()}" }
    }

    fun uninstallExtension() {
        val extension = state.value.extension ?: return
        extensionManager.uninstallExtension(extension)
    }

    fun toggleSource(sourceId: Long) {
        toggleSource.await(sourceId)
    }

    fun toggleSources(enable: Boolean) {
        state.value.extension?.sources
            ?.map { it.id }
            ?.let { toggleSource.await(it, enable) }
    }

    fun toggleIncognito(enable: Boolean) {
        state.value.extension?.pkgName?.let { packageName ->
            toggleIncognito.await(packageName, enable)
        }
    }

    @Immutable
    data class MigrateAnimeItem(
        val sourceId: Long,
        val sourceName: String,
        val anime: Anime,
    )

    @Immutable
    data class State(
        val extension: AnimeExtension.Installed? = null,
        val isIncognito: Boolean = false,
        private val _sources: ImmutableList<AnimeExtensionSourceItem>? = null,
        private val _migrateItems: ImmutableList<MigrateAnimeItem>? = null,
    ) {

        val sources: ImmutableList<AnimeExtensionSourceItem>
            get() = _sources ?: persistentListOf()

        val migrateItems: ImmutableList<MigrateAnimeItem>
            get() = _migrateItems ?: persistentListOf()

        val isLoading: Boolean
            get() = extension == null || _sources == null
    }
}

sealed interface AnimeExtensionDetailsEvent {
    data object Uninstalled : AnimeExtensionDetailsEvent
}
