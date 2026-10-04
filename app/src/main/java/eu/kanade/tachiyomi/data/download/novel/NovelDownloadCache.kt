package eu.kanade.tachiyomi.data.download.novel

import android.app.Application
import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.util.size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class NovelDownloadCache(
    private val context: Context,
    private val provider: NovelDownloadProvider = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
) {

    private val extensionManager: NovelExtensionManager by injectLazy()

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .onStart { emit(Unit) }
        .shareIn(scope, SharingStarted.Lazily, 1)

    private val renewInterval = 1.hours.inWholeMilliseconds
    private var lastRenew = 0L
    private var renewalJob: Job? = null

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing = _isInitializing
        .debounce(1000L)
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    private val diskCacheFile: File
        get() = File(context.cacheDir, "novel_dl_index_cache_v2")

    private val rootDownloadsDirMutex = Mutex()
    private var rootDownloadsDir = NovelRootDirectory(storageManager.getDownloadsDirectory())

    init {
        scope.launch {
            rootDownloadsDirMutex.withLock {
                try {
                    if (diskCacheFile.exists()) {
                        val diskCache = diskCacheFile.inputStream().use {
                            ProtoBuf.decodeFromByteArray<NovelRootDirectory>(it.readBytes())
                        }
                        rootDownloadsDir = diskCache
                        lastRenew = System.currentTimeMillis()
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Failed to initialize from disk cache" }
                    diskCacheFile.delete()
                }
            }
        }

        storageManager.changes
            .onEach { invalidateCache() }
            .launchIn(scope)
    }

    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        novelTitle: String,
        sourceId: Long,
        skipCache: Boolean,
    ): Boolean {
        if (skipCache) {
            val source = sourceManager.getOrStub(sourceId)
            return provider.findChapterDir(
                chapterName,
                chapterScanlator,
                novelTitle,
                source,
            ) != null
        }

        renewCache()

        val sourceDir = rootDownloadsDir.sourceDirs[sourceId]
        if (sourceDir != null) {
            val novelDir = sourceDir.novelDirs[provider.getNovelDirName(novelTitle)]
            if (novelDir != null) {
                return provider.getValidChapterDirNames(chapterName, chapterScanlator)
                    .any { it in novelDir.chapterDirs }
            }
        }
        return false
    }

    fun getTotalDownloadCount(): Int {
        renewCache()
        return rootDownloadsDir.sourceDirs.values.sumOf { sourceDir ->
            sourceDir.novelDirs.values.sumOf { novelDir ->
                novelDir.chapterDirs.size
            }
        }
    }

    fun getDownloadCount(novel: Novel): Int {
        renewCache()
        val sourceDir = rootDownloadsDir.sourceDirs[novel.source]
        if (sourceDir != null) {
            val novelDir = sourceDir.novelDirs[provider.getNovelDirName(novel.title)]
            if (novelDir != null) {
                return novelDir.chapterDirs.size
            }
        }
        return 0
    }

    fun getTotalDownloadSize(): Long {
        renewCache()
        return rootDownloadsDir.sourceDirs.values.sumOf { sourceDir ->
            sourceDir.dir?.size() ?: 0L
        }
    }

    fun getDownloadSize(novel: Novel): Long {
        renewCache()
        return rootDownloadsDir.sourceDirs[novel.source]?.novelDirs?.get(
            provider.getNovelDirName(novel.title),
        )?.dir?.size() ?: 0
    }

    suspend fun addChapter(chapterDirName: String, novelUniFile: UniFile, novel: Novel) {
        rootDownloadsDirMutex.withLock {
            var sourceDir = rootDownloadsDir.sourceDirs[novel.source]
            if (sourceDir == null) {
                val source = sourceManager.get(novel.source) ?: return
                val sourceUniFile = provider.findSourceDir(source) ?: return
                sourceDir = NovelSourceDirectory(sourceUniFile)
                rootDownloadsDir.sourceDirs += novel.source to sourceDir
            }

            val novelDirName = provider.getNovelDirName(novel.title)
            var novelDir = sourceDir.novelDirs[novelDirName]
            if (novelDir == null) {
                novelDir = NovelDirectory(novelUniFile)
                sourceDir.novelDirs += novelDirName to novelDir
            }

            novelDir.chapterDirs += chapterDirName
        }

        notifyChanges()
    }

    suspend fun removeChapter(chapter: NovelChapter, novel: Novel) {
        rootDownloadsDirMutex.withLock {
            val sourceDir = rootDownloadsDir.sourceDirs[novel.source] ?: return
            val novelDir = sourceDir.novelDirs[provider.getNovelDirName(novel.title)] ?: return
            provider.getValidChapterDirNames(chapter.name, chapter.scanlator).forEach {
                if (it in novelDir.chapterDirs) {
                    novelDir.chapterDirs -= it
                }
            }
        }
        notifyChanges()
    }

    suspend fun removeChapters(chapters: List<NovelChapter>, novel: Novel) {
        rootDownloadsDirMutex.withLock {
            val sourceDir = rootDownloadsDir.sourceDirs[novel.source] ?: return
            val novelDir = sourceDir.novelDirs[provider.getNovelDirName(novel.title)] ?: return
            chapters.forEach { chapter ->
                provider.getValidChapterDirNames(chapter.name, chapter.scanlator).forEach {
                    if (it in novelDir.chapterDirs) {
                        novelDir.chapterDirs -= it
                    }
                }
            }
        }
        notifyChanges()
    }

    suspend fun removeNovel(novel: Novel) {
        rootDownloadsDirMutex.withLock {
            val sourceDir = rootDownloadsDir.sourceDirs[novel.source] ?: return
            val novelDirName = provider.getNovelDirName(novel.title)
            if (sourceDir.novelDirs.containsKey(novelDirName)) {
                sourceDir.novelDirs -= novelDirName
            }
        }
        notifyChanges()
    }

    suspend fun removeSource(source: NovelSource) {
        rootDownloadsDirMutex.withLock {
            rootDownloadsDir.sourceDirs -= source.id
        }
        notifyChanges()
    }

    fun invalidateCache() {
        lastRenew = 0L
        renewalJob?.cancel()
        diskCacheFile.delete()
        renewCache()
    }

    private fun renewCache() {
        if (lastRenew + renewInterval >= System.currentTimeMillis() || renewalJob?.isActive == true) {
            return
        }

        renewalJob = scope.launchIO {
            if (lastRenew == 0L) {
                _isInitializing.emit(true)
            }

            var sources = emptyList<NovelSource>()
            withTimeoutOrNull(30.seconds) {
                extensionManager.isInitialized.first { it }
                sourceManager.isInitialized.first { it }
                sources = getSources()
            }

            val sourceMap = sources.associate { provider.getSourceDirName(it).lowercase() to it.id }

            rootDownloadsDirMutex.withLock {
                val updatedRootDir = NovelRootDirectory(storageManager.getDownloadsDirectory())

                updatedRootDir.sourceDirs = updatedRootDir.dir?.listFiles().orEmpty()
                    .filter { it.isDirectory && !it.name.isNullOrBlank() }
                    .mapNotNull { dir ->
                        val sourceId = sourceMap[dir.name!!.lowercase()]
                        sourceId?.let { it to NovelSourceDirectory(dir) }
                    }
                    .toMap()

                updatedRootDir.sourceDirs.values.map { sourceDir ->
                    async {
                        sourceDir.novelDirs = sourceDir.dir?.listFiles().orEmpty()
                            .filter { it.isDirectory && !it.name.isNullOrBlank() }
                            .associate { it.name!! to NovelDirectory(it) }
                        sourceDir.novelDirs.values.forEach { novelDir ->
                            val chapterDirs = novelDir.dir?.listFiles().orEmpty()
                                .mapNotNull {
                                    when {
                                        it.name?.endsWith(NovelDownloader.TMP_DIR_SUFFIX) == true -> null
                                        it.isFile && it.extension == "txt" -> it.name
                                        else -> null
                                    }
                                }
                                .toMutableSet()

                            novelDir.chapterDirs = chapterDirs
                        }
                    }
                }
                    .awaitAll()

                rootDownloadsDir = updatedRootDir
            }

            _isInitializing.emit(false)
        }.also {
            it.invokeOnCompletion(onCancelling = true) { exception ->
                if (exception != null && exception !is CancellationException) {
                    logcat(LogPriority.ERROR, exception) { "NovelDownloadCache: failed to create cache" }
                }
                lastRenew = System.currentTimeMillis()
                notifyChanges()
            }
        }

        notifyChanges()
    }

    private fun getSources(): List<NovelSource> {
        return sourceManager.getOnlineSources() + sourceManager.getStubSources()
    }

    private fun notifyChanges() {
        scope.launchNonCancellable {
            _changes.send(Unit)
        }
        updateDiskCache()
    }

    private var updateDiskCacheJob: Job? = null
    private fun updateDiskCache() {
        updateDiskCacheJob?.cancel()
        updateDiskCacheJob = scope.launchIO {
            delay(1000)
            ensureActive()
            val bytes = ProtoBuf.encodeToByteArray(rootDownloadsDir)
            ensureActive()
            try {
                diskCacheFile.writeBytes(bytes)
            } catch (e: Throwable) {
                logcat(
                    priority = LogPriority.ERROR,
                    throwable = e,
                    message = { "Failed to write disk cache file" },
                )
            }
        }
    }
}

@Serializable
private class NovelRootDirectory(
    @Serializable(with = NovelUniFileAsStringSerializer::class)
    val dir: UniFile?,
    var sourceDirs: Map<Long, NovelSourceDirectory> = mapOf(),
)

@Serializable
private class NovelSourceDirectory(
    @Serializable(with = NovelUniFileAsStringSerializer::class)
    val dir: UniFile?,
    var novelDirs: Map<String, NovelDirectory> = mapOf(),
)

@Serializable
private class NovelDirectory(
    @Serializable(with = NovelUniFileAsStringSerializer::class)
    val dir: UniFile?,
    var chapterDirs: MutableSet<String> = mutableSetOf(),
)

private object NovelUniFileAsStringSerializer : KSerializer<UniFile?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UniFile", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UniFile?) {
        return if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value.uri.toString())
        }
    }

    override fun deserialize(decoder: Decoder): UniFile? {
        return if (decoder.decodeNotNullMark()) {
            UniFile.fromUri(Injekt.get<Application>(), decoder.decodeString().toUri())
        } else {
            decoder.decodeNull()
        }
    }
}
