package eu.kanade.tachiyomi.ui.browse.novel.extension.details

import android.content.Context
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.extension.novel.interactor.GetNovelExtensionSources
import eu.kanade.domain.extension.novel.interactor.NovelExtensionSourceItem
import eu.kanade.domain.source.novel.interactor.ToggleNovelSource
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
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
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.interactor.GetNovelFavorites
import tachiyomi.domain.entries.novel.model.Novel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelExtensionDetailsScreenModel(
    pkgName: String,
    context: Context,
    private val extensionManager: NovelExtensionManager = Injekt.get(),
    private val getExtensionSources: GetNovelExtensionSources = Injekt.get(),
    private val toggleSource: ToggleNovelSource = Injekt.get(),
    private val getFavorites: GetNovelFavorites = Injekt.get(),
) : StateScreenModel<NovelExtensionDetailsScreenModel.State>(State()) {

    private val _events: Channel<NovelExtensionDetailsEvent> = Channel()
    val events: Flow<NovelExtensionDetailsEvent> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            launch {
                extensionManager.installedExtensionsFlow
                    .map { it.firstOrNull { extension -> extension.pkgName == pkgName } }
                    .collectLatest { extension ->
                        if (extension == null) {
                            _events.send(NovelExtensionDetailsEvent.Uninstalled)
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
            // Load favorites (migrate items) for all sources in this extension
            launch {
                state
                    .map { it.sources }
                    .distinctUntilChanged()
                    .flatMapLatest { sources ->
                        if (sources.isEmpty()) {
                            kotlinx.coroutines.flow.flowOf(persistentListOf<MigrateNovelItem>())
                        } else {
                            combine(
                                sources.map { source ->
                                    getFavorites.subscribe(source.source.id)
                                        .catch { throwable ->
                                            logcat(LogPriority.ERROR, throwable)
                                            emit(persistentListOf())
                                        }
                                        .map { novels ->
                                            novels.map {
                                                MigrateNovelItem(
                                                    sourceId = source.source.id,
                                                    sourceName = source.source.name,
                                                    novel = it,
                                                )
                                            }
                                        }
                                },
                            ) { lists ->
                                lists.flatMap { it }.sortedWith(
                                    compareBy(String.CASE_INSENSITIVE_ORDER) { it.novel.title },
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

    @Immutable
    data class MigrateNovelItem(
        val sourceId: Long,
        val sourceName: String,
        val novel: Novel,
    )

    @Immutable
    data class State(
        val extension: NovelExtension.Installed? = null,
        private val _sources: ImmutableList<NovelExtensionSourceItem>? = null,
        private val _migrateItems: ImmutableList<MigrateNovelItem>? = null,
    ) {

        val sources: ImmutableList<NovelExtensionSourceItem>
            get() = _sources ?: persistentListOf()

        val migrateItems: ImmutableList<MigrateNovelItem>
            get() = _migrateItems ?: persistentListOf()

        val isLoading: Boolean
            get() = extension == null || _sources == null
    }
}

sealed interface NovelExtensionDetailsEvent {
    data object Uninstalled : NovelExtensionDetailsEvent
}
