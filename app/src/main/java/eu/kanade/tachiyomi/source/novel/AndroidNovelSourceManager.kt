package eu.kanade.tachiyomi.source.novel

import android.content.Context
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.repository.NovelStubSourceRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import java.util.concurrent.ConcurrentHashMap

class AndroidNovelSourceManager(
    private val context: Context,
    private val extensionManager: NovelExtensionManager,
    private val sourceRepository: NovelStubSourceRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * Optional flow of JS plugin sources. When provided, JS plugin sources
     * are merged alongside APK extension sources in the source manager.
     *
     * This is additive — APK extension sources continue to work exactly as
     * before. JS sources are simply added to the same map.
     */
    private val jsSourcesFlow: Flow<List<NovelSource>> = emptyFlow(),
    /**
     * Built-in, always-present sources (Audiobookshelf; LibriVox/IA join
     * in 22-03) — fixed negative ids that can't collide with extension
     * id-hashes. Seeded into the sources map on every extension emission
     * so they land before [_isInitialized] flips and never hit the
     * awaitSource polling window.
     */
    private val builtInSources: List<NovelSource> = emptyList(),
) : NovelSourceManager {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val scope = CoroutineScope(Job() + dispatcher)

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, NovelSource>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubNovelSource>()

    override val catalogueSources: Flow<List<NovelCatalogueSource>> = sourcesMapFlow.map {
        it.values.filterIsInstance<NovelCatalogueSource>()
    }

    init {
        scope.launch {
            // installedExtensionsFlow replays its initial empty list at
            // collection start — that emission is NOT "extensions
            // loaded", it's the StateFlow seed. isInitialized tracks the
            // extension manager's own flag so callers waiting on it
            // don't resolve an empty map; the map itself still populates
            // on every emission.
            combine(
                extensionManager.installedExtensionsFlow,
                extensionManager.isInitialized,
            ) { extensions, extInit -> extensions to extInit }
                .collectLatest { (extensions, extInit) ->
                    val mutableMap = ConcurrentHashMap<Long, NovelSource>()
                    extensions.forEach { extension ->
                        extension.sources.forEach {
                            mutableMap[it.id] = it
                            registerStubSource(StubNovelSource.from(it))
                        }
                    }
                    // Built-in sources are re-added on every emission so a
                    // re-scan can never drop them.
                    builtInSources.forEach { source ->
                        mutableMap[source.id] = source
                        registerStubSource(
                            StubNovelSource(id = source.id, lang = source.lang, name = source.name),
                        )
                    }
                    // Merge JS plugin sources alongside APK extension sources
                    val existingIds = mutableMap.keys.toSet()
                    sourcesMapFlow.value.forEach { (id, source) ->
                        if (id !in existingIds) {
                            mutableMap[id] = source
                        }
                    }
                    sourcesMapFlow.value = mutableMap
                    if (extInit) _isInitialized.value = true
                }
        }

        scope.launch {
            // Same gate — a JS-plugin emission must not flip
            // isInitialized before the extension scan completes.
            extensionManager.isInitialized.first { it }
            jsSourcesFlow
                .collectLatest { jsSources ->
                    val mutableMap = ConcurrentHashMap(sourcesMapFlow.value)
                    jsSources.forEach { source ->
                        mutableMap[source.id] = source
                        registerStubSource(
                            StubNovelSource(id = source.id, lang = source.lang, name = source.name),
                        )
                    }
                    sourcesMapFlow.value = mutableMap
                    _isInitialized.value = true
                }
        }

        scope.launch {
            sourceRepository.subscribeAllNovel()
                .collectLatest { sources ->
                    val mutableMap = stubSourcesMap.toMutableMap()
                    sources.forEach {
                        mutableMap[it.id] = it
                    }
                }
        }
    }

    override fun get(sourceKey: Long): NovelSource? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): NovelSource {
        return sourcesMapFlow.value[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            runBlocking { createStubSource(sourceKey) }
        }
    }

    override fun getOnlineSources() = sourcesMapFlow.value.values.filterIsInstance<NovelHttpSource>()

    override fun getCatalogueSources() = sourcesMapFlow.value.values.filterIsInstance<NovelCatalogueSource>()

    override fun getStubSources(): List<StubNovelSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    private fun registerStubSource(source: StubNovelSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubNovelSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubNovelSource(source.id, source.lang, source.name)
        }
    }

    private suspend fun createStubSource(id: Long): StubNovelSource {
        sourceRepository.getStubNovelSource(id)?.let {
            return it
        }
        extensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return it
        }
        return StubNovelSource(id = id, lang = "", name = "")
    }
}
