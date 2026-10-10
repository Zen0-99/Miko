package miko.ios

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
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
import miko.jssource.IosJsRuntimeFactory
import miko.jssource.IosNovelJsSource
import miko.jssource.NovelPluginId
import tachiyomi.data.extension.novel.NovelPluginStorage
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.repository.NovelStubSourceRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager

/**
 * iOS [NovelSourceManager]: JS plugins are the ONLY novel sources — no APK
 * extensions exist on iOS. Mirrors AndroidNovelSourceManager's merge/stub
 * semantics with `pluginRepository.subscribeAll()` standing in for
 * `extensionManager.installedExtensionsFlow`.
 *
 * Source instances are reused across emissions so their lazily-created
 * runtimes and caches survive; a plugin version bump tears down and rebuilds
 * its source. Maps live only inside [CoroutineScope] collections and the
 * read paths below, so no lock is needed (K/N has no ConcurrentHashMap).
 */
class IosNovelSourceManager(
    private val pluginRepository: NovelPluginRepository,
    private val pluginStorage: NovelPluginStorage,
    private val sourceRepository: NovelStubSourceRepository,
    private val runtimeFactory: IosJsRuntimeFactory,
    private val httpClient: HttpClient,
    private val json: Json,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : NovelSourceManager {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val scope = CoroutineScope(Job() + dispatcher)

    private val sourcesMapFlow = MutableStateFlow<Map<Long, NovelSource>>(emptyMap())
    private val sourcesByPluginId = mutableMapOf<String, IosNovelJsSource>()
    private val installedVersions = mutableMapOf<String, Int>()
    private val stubSourcesMap = mutableMapOf<Long, StubNovelSource>()
    private val stubMutex = Mutex()

    override val catalogueSources: Flow<List<NovelCatalogueSource>> = sourcesMapFlow.map {
        it.values.filterIsInstance<NovelCatalogueSource>()
    }

    init {
        scope.launch {
            pluginRepository.subscribeAll().collectLatest { plugins ->
                val pluginIds = plugins.map { it.id }.toSet()

                // Drop uninstalled plugins (and release their runtimes).
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
                        registerStubSource(StubNovelSource.from(source))
                    }
                }
                // Prune map entries whose sources no longer exist.
                (mutableMap.keys - sourcesByPluginId.values.map { it.id }.toSet())
                    .forEach { mutableMap.remove(it) }
                sourcesMapFlow.value = mutableMap
                _isInitialized.value = true
            }
        }

        scope.launch {
            sourceRepository.subscribeAllNovel().collectLatest { sources ->
                stubMutex.withLock {
                    sources.forEach { stubSourcesMap[it.id] = it }
                }
            }
        }
    }

    override fun get(sourceKey: Long): NovelSource? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): NovelSource {
        sourcesMapFlow.value[sourceKey]?.let { return it }
        return runBlocking {
            stubMutex.withLock {
                stubSourcesMap[sourceKey]
                    ?: createStubSource(sourceKey).also { stubSourcesMap[sourceKey] = it }
            }
        }
    }

    // iOS has no NovelHttpSource type — every JS catalogue source is
    // online-capable, so this returns all live catalogue sources.
    override fun getOnlineSources() = getCatalogueSources()

    override fun getCatalogueSources() =
        sourcesMapFlow.value.values.filterIsInstance<NovelCatalogueSource>()

    override fun getStubSources(): List<StubNovelSource> {
        val liveIds = sourcesMapFlow.value.keys
        return runBlocking {
            stubMutex.withLock { stubSourcesMap.values.filterNot { it.id in liveIds } }
        }
    }

    // Missing script bytes skip the source entirely — same contract as
    // Android's NovelJsSourceFactory.create returning null.
    private fun createSource(plugin: NovelPlugin.Installed): IosNovelJsSource? {
        val script = pluginStorage.readPluginScript(plugin.id)?.decodeToString()
            ?: return null
        return IosNovelJsSource(
            runtimeProvider = { runtimeFactory.create(plugin.id, httpClient) },
            script = script,
            pluginId = plugin.id,
            json = json,
            id = NovelPluginId.toSourceId(plugin.id),
            name = plugin.name,
            lang = plugin.lang,
        )
    }

    private fun registerStubSource(source: StubNovelSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubNovelSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubNovelSource(source.id, source.lang, source.name)
        }
    }

    private suspend fun createStubSource(id: Long): StubNovelSource {
        sourceRepository.getStubNovelSource(id)?.let { return it }
        // Reverse-map through installed plugins — name/lang for a source id
        // survives plugin removal this way.
        pluginRepository.getAll()
            .firstOrNull { NovelPluginId.toSourceId(it.id) == id }
            ?.let {
                val stub = StubNovelSource(
                    id = id,
                    lang = it.lang,
                    name = it.name,
                )
                registerStubSource(stub)
                return stub
            }
        return StubNovelSource(id = id, lang = "", name = "")
    }
}
