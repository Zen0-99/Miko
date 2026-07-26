package eu.kanade.tachiyomi.ui.browse.novel.extension.details

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.novel.interactor.ToggleNovelSource
import eu.kanade.tachiyomi.extension.novel.JsNovelPluginManager
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.novel.interactor.GetNovelFavorites
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.source.novel.model.NovelSource
import tachiyomi.domain.source.novel.repository.NovelSourceRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelPluginDetailsScreenModel(
    private val pluginId: String,
    private val jsPluginManager: JsNovelPluginManager = Injekt.get(),
    private val toggleSource: ToggleNovelSource = Injekt.get(),
    private val getFavorites: GetNovelFavorites = Injekt.get(),
    private val sourceRepository: NovelSourceRepository = Injekt.get(),
) : StateScreenModel<NovelPluginDetailsScreenModel.State>(State()) {

    private val _events: Channel<NovelPluginDetailsEvent> = Channel()
    val events: Flow<NovelPluginDetailsEvent> = _events.receiveAsFlow()

    private val sourceId = NovelPluginId.toSourceId(pluginId)

    init {
        screenModelScope.launch {
            launch {
                jsPluginManager.installedPluginsFlow
                    .map { plugins -> plugins.firstOrNull { it.id == pluginId } }
                    .collectLatest { plugin ->
                        if (plugin == null) {
                            _events.send(NovelPluginDetailsEvent.Uninstalled)
                            return@collectLatest
                        }
                        mutableState.update { it.copy(plugin = plugin) }
                    }
            }
            launch {
                // Subscribe to the source from the source repository so we
                // get the enabled/disabled state and display name.
                sourceRepository.getNovelSources()
                    .map { sources -> sources.firstOrNull { it.id == sourceId } }
                    .distinctUntilChanged()
                    .collectLatest { source ->
                        mutableState.update { it.copy(source = source) }
                    }
            }
            // Load favorites (migrate items) for this plugin's source
            launch {
                getFavorites.subscribe(sourceId)
                    .collectLatest { novels ->
                        val items = novels.map { novel ->
                            MigrateNovelItem(
                                sourceId = sourceId,
                                sourceName = state.value.plugin?.name ?: "",
                                novel = novel,
                            )
                        }.sortedWith(
                            compareBy(String.CASE_INSENSITIVE_ORDER) { it.novel.title },
                        ).toImmutableList()
                        mutableState.update { it.copy(_migrateItems = items) }
                    }
            }
        }
    }

    fun uninstallPlugin() {
        screenModelScope.launch {
            jsPluginManager.uninstallPlugin(pluginId)
        }
    }

    fun toggleSource(enable: Boolean) {
        screenModelScope.launch {
            toggleSource.await(sourceId, enable)
        }
    }

    @Immutable
    data class MigrateNovelItem(
        val sourceId: Long,
        val sourceName: String,
        val novel: Novel,
    )

    @Immutable
    data class State(
        val plugin: tachiyomi.domain.extension.novel.model.NovelPlugin.Installed? = null,
        val source: NovelSource? = null,
        private val _migrateItems: ImmutableList<MigrateNovelItem>? = null,
    ) {
        val migrateItems: ImmutableList<MigrateNovelItem>
            get() = _migrateItems ?: persistentListOf()

        val isLoading: Boolean
            get() = plugin == null
    }
}

sealed interface NovelPluginDetailsEvent {
    data object Uninstalled : NovelPluginDetailsEvent
}
