package eu.kanade.tachiyomi.data.coil

import androidx.core.net.toUri
import coil3.Extras
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.getOrDefault
import coil3.request.Options
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.data.coil.NovelCoverFetcher.Companion.USE_CUSTOM_COVER_KEY
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import eu.kanade.tachiyomi.source.novel.NovelImageRequestSource
import eu.kanade.tachiyomi.source.novel.NovelPluginImageSource
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import logcat.LogPriority
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException

/**
 * A [Fetcher] that fetches cover image for [Novel] object.
 *
 * It uses [Novel.thumbnailUrl] if custom cover is not set by the user.
 * Disk caching for library items is handled by [NovelCoverCache], otherwise
 * handled by Coil's [DiskCache].
 *
 * Available request parameter:
 * - [USE_CUSTOM_COVER_KEY]: Use custom cover if set by user, default is true
 */
class NovelCoverFetcher(
    private val url: String?,
    private val isLibraryNovel: Boolean,
    private val options: Options,
    private val coverFileLazy: Lazy<File?>,
    private val customCoverFileLazy: Lazy<File>,
    private val diskCacheKeyLazy: Lazy<String>,
    private val sourceLazy: Lazy<NovelSource?>,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val imageLoader: ImageLoader,
) : Fetcher {

    private val diskCacheKey: String
        get() = diskCacheKeyLazy.value

    override suspend fun fetch(): FetchResult {
        android.util.Log.d("NovelCoverFetcher", "fetch: url=$url isLibraryNovel=$isLibraryNovel diskCacheKey=$diskCacheKey")
        // Use custom cover if exists
        val useCustomCover = options.extras.getOrDefault(USE_CUSTOM_COVER_KEY)
        if (useCustomCover) {
            val customCoverFile = customCoverFileLazy.value
            if (customCoverFile.exists()) {
                android.util.Log.d("NovelCoverFetcher", "fetch: using custom cover")
                return fileLoader(customCoverFile)
            }
        }

        // diskCacheKey is thumbnail_url
        if (url == null) error("No cover specified")
        return when (getResourceType(url)) {
            Type.URL -> httpLoader()
            Type.File -> fileLoader(File(url.substringAfter("file://")))
            Type.URI -> uniFileLoader(url)
            null -> error("Invalid image")
        }
    }

    private fun uniFileLoader(urlString: String): FetchResult {
        val uniFile = UniFile.fromUri(options.context, urlString.toUri())!!
        val tempFile = uniFile.openInputStream().source().buffer()
        return SourceFetchResult(
            source = ImageSource(source = tempFile, fileSystem = FileSystem.SYSTEM),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private fun fileLoader(file: File): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
                diskCacheKey = diskCacheKey,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun httpLoader(): FetchResult {
        // Only cache separately if it's a library item
        val libraryCoverCacheFile = if (isLibraryNovel) {
            coverFileLazy.value ?: error("No cover specified")
        } else {
            null
        }
        val coverExists = libraryCoverCacheFile?.exists() == true
        android.util.Log.d("NovelCoverFetcher", "httpLoader: url=$url coverExists=$coverExists coverFile=$libraryCoverCacheFile")
        if (coverExists && options.diskCachePolicy.readEnabled) {
            android.util.Log.d("NovelCoverFetcher", "httpLoader: returning from cover cache")
            return fileLoader(libraryCoverCacheFile!!)
        }

        var snapshot = readFromDiskCache()
        try {
            // Fetch from disk cache
            if (snapshot != null) {
                android.util.Log.d("NovelCoverFetcher", "httpLoader: found disk cache snapshot")
                val snapshotCoverCache = moveSnapshotToCoverCache(snapshot, libraryCoverCacheFile)
                if (snapshotCoverCache != null) {
                    // Read from cover cache after added to library
                    return fileLoader(snapshotCoverCache)
                }

                // Read from snapshot
                return SourceFetchResult(
                    source = snapshot.toImageSource(),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK,
                )
            }

            // Fetch from plugin's fetchImage() when available (LNReader JS plugins).
            val source = sourceLazy.value
            if (source is NovelPluginImageSource) {
                android.util.Log.d("NovelCoverFetcher", "httpLoader: trying plugin fetchImage")
                val pluginResult = pluginImageFetch(source, libraryCoverCacheFile)
                if (pluginResult != null) {
                    return pluginResult
                }
            }

            // Fall back to direct HTTP request
            android.util.Log.d("NovelCoverFetcher", "httpLoader: executing network request to $url")
            val response = executeNetworkRequest()
            android.util.Log.d("NovelCoverFetcher", "httpLoader: network response code=${response.code}")
            val responseBody = checkNotNull(response.body) { "Null response source" }
            try {
                // Read from cover cache after library novel cover updated
                val responseCoverCache = writeResponseToCoverCache(response, libraryCoverCacheFile)
                if (responseCoverCache != null) {
                    android.util.Log.d("NovelCoverFetcher", "httpLoader: wrote to cover cache, returning")
                    return fileLoader(responseCoverCache)
                }

                // Read from disk cache
                snapshot = writeToDiskCache(response)
                if (snapshot != null) {
                    return SourceFetchResult(
                        source = snapshot.toImageSource(),
                        mimeType = "image/*",
                        dataSource = DataSource.NETWORK,
                    )
                }

                // Read from response if cache is unused or unusable
                return SourceFetchResult(
                    source = ImageSource(source = responseBody.source(), fileSystem = FileSystem.SYSTEM),
                    mimeType = "image/*",
                    dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
                )
            } catch (e: Exception) {
                responseBody.close()
                throw e
            }
        } catch (e: Exception) {
            android.util.Log.e("NovelCoverFetcher", "httpLoader: FAILED for url=$url: ${e::class.simpleName}: ${e.message}")
            snapshot?.close()
            throw e
        }
    }

    /**
     * Fetch cover image via the plugin's [fetchImage] method. This uses the plugin's
     * own HTTP client and header logic (e.g. Referer, User-Agent), which may differ
     * from a raw OkHttp request with only static [getImageRequestHeaders] headers.
     * Returns null if the plugin doesn't support fetchImage or the fetch fails, so
     * the caller can fall back to a direct HTTP request.
     */
    private suspend fun pluginImageFetch(
        source: NovelPluginImageSource,
        libraryCoverCacheFile: File?,
    ): FetchResult? {
        val payload = source.fetchImage(url!!) ?: return null
        return try {
            // Write to cover cache if library item
            val coverCacheFile = writeBytesToCoverCache(payload.bytes, libraryCoverCacheFile)
            if (coverCacheFile != null) {
                return fileLoader(coverCacheFile)
            }

            // Write to disk cache
            val snapshot = writeBytesToDiskCache(payload.bytes)
            if (snapshot != null) {
                return SourceFetchResult(
                    source = snapshot.toImageSource(),
                    mimeType = payload.mimeType.ifBlank { "image/*" },
                    dataSource = DataSource.NETWORK,
                )
            }

            // Read directly from bytes if cache is unused or unusable
            SourceFetchResult(
                source = ImageSource(
                    source = okio.Buffer().write(payload.bytes),
                    fileSystem = FileSystem.SYSTEM,
                ),
                mimeType = payload.mimeType.ifBlank { "image/*" },
                dataSource = DataSource.NETWORK,
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to process plugin image for $url" }
            null
        }
    }

    private fun writeBytesToCoverCache(bytes: ByteArray, cacheFile: File?): File? {
        if (cacheFile == null || !options.diskCachePolicy.writeEnabled) return null
        return try {
            okio.Buffer().write(bytes).use { input ->
                writeSourceToCoverCache(input, cacheFile)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write plugin image to cover cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeBytesToDiskCache(bytes: ByteArray): DiskCache.Snapshot? {
        val diskCache = imageLoader.diskCache ?: return null
        val editor = diskCache.openEditor(diskCacheKey) ?: return null
        try {
            diskCache.fileSystem.write(editor.data) {
                val buffer = okio.Buffer()
                buffer.write(bytes)
                buffer.readAll(this)
            }
            return editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            try {
                editor.abort()
            } catch (ignored: Exception) {
            }
            return null
        }
    }

    private suspend fun executeNetworkRequest(): Response {
        val source = sourceLazy.value
        val client = (source as? NovelHttpSource)?.client ?: callFactoryLazy.value
        val response = client.newCall(newRequest(source)).await()
        if (!response.isSuccessful && response.code != HTTP_NOT_MODIFIED) {
            response.close()
            throw IOException(response.message)
        }
        return response
    }

    private suspend fun newRequest(source: NovelSource? = sourceLazy.value): Request {
        val request = Request.Builder().apply {
            url(url!!)

            // Use headers from NovelHttpSource if available
            val httpSource = source as? NovelHttpSource
            if (httpSource != null) {
                val sourceHeaders = httpSource.headers
                if (sourceHeaders != null) {
                    headers(sourceHeaders)
                }
            } else if (source is NovelImageRequestSource) {
                // JS plugin sources provide image request headers asynchronously
                val imageHeaders = source.getImageRequestHeaders().toMutableMap()
                // Match the JS runtime's fetch behavior: if no Referer is set,
                // add the plugin's site URL as Referer. Many image servers
                // serve different (smaller/watermarked) images without a Referer.
                if (imageHeaders.keys.none { it.equals("Referer", ignoreCase = true) }) {
                    val siteSource = source as? NovelSiteSource
                    val siteUrl = siteSource?.siteUrl
                    if (!siteUrl.isNullOrBlank()) {
                        imageHeaders["Referer"] = "$siteUrl/"
                    }
                }
                if (imageHeaders.isNotEmpty()) {
                    val headerBuilder = okhttp3.Headers.Builder()
                    imageHeaders.forEach { (key, value) -> headerBuilder.add(key, value) }
                    headers(headerBuilder.build())
                }
            }
        }

        when {
            options.networkCachePolicy.readEnabled -> {
                // don't take up okhttp cache
                request.cacheControl(CACHE_CONTROL_NO_STORE)
            }
            else -> {
                // This causes the request to fail with a 504 Unsatisfiable Request.
                request.cacheControl(CACHE_CONTROL_NO_NETWORK_NO_CACHE)
            }
        }

        return request.build()
    }

    private fun moveSnapshotToCoverCache(snapshot: DiskCache.Snapshot, cacheFile: File?): File? {
        if (cacheFile == null) return null
        return try {
            imageLoader.diskCache?.run {
                fileSystem.source(snapshot.data).use { input ->
                    writeSourceToCoverCache(input, cacheFile)
                }
                remove(diskCacheKey)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write snapshot data to cover cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeResponseToCoverCache(response: Response, cacheFile: File?): File? {
        if (cacheFile == null || !options.diskCachePolicy.writeEnabled) return null
        return try {
            response.peekBody(Long.MAX_VALUE).source().use { input ->
                writeSourceToCoverCache(input, cacheFile)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write response data to cover cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeSourceToCoverCache(input: Source, cacheFile: File) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.delete()
        try {
            cacheFile.sink().buffer().use { output ->
                output.writeAll(input)
            }
        } catch (e: Exception) {
            cacheFile.delete()
            throw e
        }
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? {
        return if (options.diskCachePolicy.readEnabled) {
            imageLoader.diskCache?.openSnapshot(diskCacheKey)
        } else {
            null
        }
    }

    private fun writeToDiskCache(
        response: Response,
    ): DiskCache.Snapshot? {
        val diskCache = imageLoader.diskCache
        val editor = diskCache?.openEditor(diskCacheKey) ?: return null
        try {
            diskCache.fileSystem.write(editor.data) {
                response.body.source().readAll(this)
            }
            return editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            try {
                editor.abort()
            } catch (ignored: Exception) {
            }
            throw e
        }
    }

    private fun DiskCache.Snapshot.toImageSource(): ImageSource {
        return ImageSource(
            file = data,
            fileSystem = FileSystem.SYSTEM,
            diskCacheKey = diskCacheKey,
            closeable = this,
        )
    }

    private fun getResourceType(cover: String?): Type? {
        return when {
            cover.isNullOrEmpty() -> null
            cover.startsWith("http", true) || cover.startsWith("Custom-", true) -> Type.URL
            cover.startsWith("/") || cover.startsWith("file://") -> Type.File
            cover.startsWith("content") -> Type.URI
            else -> null
        }
    }

    private enum class Type {
        File,
        URL,
        URI,
    }

    class NovelFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<Novel> {

        private val coverCache: NovelCoverCache by injectLazy()
        private val sourceManager: NovelSourceManager by injectLazy()

        override fun create(data: Novel, options: Options, imageLoader: ImageLoader): Fetcher {
            return NovelCoverFetcher(
                url = data.thumbnailUrl,
                isLibraryNovel = data.favorite,
                options = options,
                coverFileLazy = lazy { coverCache.getCoverFile(data.thumbnailUrl) },
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.id) },
                diskCacheKeyLazy = lazy { imageLoader.components.key(data, options)!! },
                sourceLazy = lazy { sourceManager.get(data.source) },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    class NovelCoverFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<NovelCover> {

        private val coverCache: NovelCoverCache by injectLazy()
        private val sourceManager: NovelSourceManager by injectLazy()

        override fun create(data: NovelCover, options: Options, imageLoader: ImageLoader): Fetcher {
            return NovelCoverFetcher(
                url = data.url,
                isLibraryNovel = data.isNovelFavorite,
                options = options,
                coverFileLazy = lazy { coverCache.getCoverFile(data.url) },
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.novelId) },
                diskCacheKeyLazy = lazy { imageLoader.components.key(data, options)!! },
                sourceLazy = lazy { sourceManager.get(data.sourceId) },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    companion object {
        val USE_CUSTOM_COVER_KEY = Extras.Key(true)

        private val CACHE_CONTROL_NO_STORE = CacheControl.Builder().noStore().build()
        private val CACHE_CONTROL_NO_NETWORK_NO_CACHE = CacheControl.Builder().noCache().onlyIfCached().build()

        private const val HTTP_NOT_MODIFIED = 304
    }
}
