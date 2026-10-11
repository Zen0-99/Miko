package miko.ios

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import miko.jssource.IosAnimeJsSource
import miko.jssource.IosJsRuntimeFactory
import miko.jssource.JsSourceIds
import tachiyomi.data.extension.novel.NovelPluginStorage
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * iOS [AnimeSourceManager]: JS plugins (kind=anime/show) are the ONLY anime
 * sources — mirrors [IosNovelSourceManager]'s merge/stub semantics with
 * the same plugin table filtered by `kind == "anime"`.
 */
class IosAnimeSourceManager(
    private val pluginRepository: NovelPluginRepository,
    private val pluginStorage: NovelPluginStorage,
    private val sourceRepository: AnimeStubSourceRepository,
    private val runtimeFactory: IosJsRuntimeFactory,
    private val httpClient: HttpClient,
    private val json: Json,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AnimeSourceManager {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val scope = CoroutineScope(Job() + dispatcher)

    private val sourcesMapFlow = MutableStateFlow<Map<Long, AnimeSource>>(emptyMap())
    private val sourcesByPluginId = mutableMapOf<String, IosAnimeJsSource>()
    private val installedVersions = mutableMapOf<String, Int>()
    private val stubSourcesMap = mutableMapOf<Long, StubAnimeSource>()
    private val stubMutex = Mutex()

    override val catalogueSources: Flow<List<AnimeCatalogueSource>> = sourcesMapFlow.map {
        it.values.filterIsInstance<AnimeCatalogueSource>()
    }

    init {
        scope.launch {
            pluginRepository.subscribeAll().collectLatest { plugins ->
                val pluginIds = plugins.map { it.id }.toSet()

                val removed = sourcesByPluginId.keys - pluginIds
                removed.forEach { pid ->
                    sourcesByPluginId.remove(pid)?.shutdown()
                    installedVersions.remove(pid)
                }

                val mutableMap = sourcesMapFlow.value.toMutableMap()
                plugins.forEach { plugin ->
                    if (installedVersions[plugin.id] != plugin.versionCode) {
                        sourcesByPluginId.remove(plugin.id)?.shutdown()
                        createSource(plugin)?.let { sourcesByPluginId[plugin.id] = it }
                        installedVersions[plugin.id] = plugin.versionCode
                    }
                    sourcesByPluginId[plugin.id]?.let { source ->
                        mutableMap[source.id] = source
                        registerStubSource(StubAnimeSource.from(source))
                    }
                }
                (mutableMap.keys - sourcesByPluginId.values.map { it.id }.toSet())
                    .forEach { mutableMap.remove(it) }
                sourcesMapFlow.value = mutableMap
                _isInitialized.value = true
            }
        }

        scope.launch {
            sourceRepository.subscribeAllAnime().collectLatest { sources ->
                stubMutex.withLock {
                    sources.forEach { stubSourcesMap[it.id] = it }
                }
            }
        }
    }

    override fun get(sourceKey: Long): AnimeSource? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): AnimeSource {
        sourcesMapFlow.value[sourceKey]?.let { return it }
        return runBlocking {
            stubMutex.withLock {
                stubSourcesMap[sourceKey]
                    ?: createStubSource(sourceKey).also { stubSourcesMap[sourceKey] = it }
            }
        }
    }

    override fun getOnlineSources() = getCatalogueSources()

    override fun getCatalogueSources() =
        sourcesMapFlow.value.values.filterIsInstance<AnimeCatalogueSource>()

    override fun getStubSources(): List<StubAnimeSource> {
        val liveIds = sourcesMapFlow.value.keys
        return runBlocking {
            stubMutex.withLock { stubSourcesMap.values.filterNot { it.id in liveIds } }
        }
    }

    private fun createSource(plugin: NovelPlugin.Installed): IosAnimeJsSource? {
        if (plugin.kind != "anime") return null
        val script = pluginStorage.readPluginScript(plugin.id)?.decodeToString()
            ?: return null
        return IosAnimeJsSource(
            runtimeProvider = { runtimeFactory.create(plugin.id, httpClient) },
            script = script,
            pluginId = plugin.id,
            json = json,
            id = JsSourceIds.toSourceId(plugin.id),
            name = plugin.name,
            lang = plugin.lang,
        )
    }

    private fun registerStubSource(source: StubAnimeSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubAnimeSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubAnimeSource(source.id, source.lang, source.name)
        }
    }

    private suspend fun createStubSource(id: Long): StubAnimeSource {
        sourceRepository.getStubAnimeSource(id)?.let { return it }
        pluginRepository.getAll()
            .firstOrNull { JsSourceIds.toSourceId(it.id) == id }
            ?.let {
                val stub = StubAnimeSource(
                    id = id,
                    lang = it.lang,
                    name = it.name,
                )
                registerStubSource(stub)
                return stub
            }
        return StubAnimeSource(id = id, lang = "", name = "")
    }
}
