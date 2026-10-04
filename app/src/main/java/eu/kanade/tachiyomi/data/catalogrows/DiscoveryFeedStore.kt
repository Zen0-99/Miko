package eu.kanade.tachiyomi.data.catalogrows

import eu.kanade.tachiyomi.data.mikodb.MangaNovelIdBridge.WorkRow
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * Disk persistence for the discovery feed cache — one JSON file under
 * `filesDir` holding every cached feed's items, paging cursor, and
 * last-known library bindings. Discovery renders hydrated rows on the
 * first frame after a cold start; the background warmer refreshes the
 * file with whatever's new.
 *
 * Writes are atomic (tmp + rename), reads tolerate schema drift via
 * field defaults — a corrupt or old file just degrades to empty.
 */
class DiscoveryFeedStore(
    private val file: File,
    private val json: Json,
) {

    data class Stored(
        val items: List<CatalogItem>,
        val page: Int,
        val hasMore: Boolean,
        val savedAt: Long,
        val bound: Map<String, Long>,
        /** workId → bound library row's cover — keeps bound-card art
         *  consistent with the library across cold starts. */
        val boundCovers: Map<String, String>,
    )

    /** All persisted feeds, keyed by feed key. Empty on any failure. */
    suspend fun load(): Map<String, Stored> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyMap()
        runCatching {
            json.decodeFromString<FeedFile>(file.readText()).feeds
                .mapValues { (_, f) ->
                    Stored(
                        items = f.items.map { it.toCatalogItem() },
                        page = f.page,
                        hasMore = f.hasMore,
                        savedAt = f.savedAt,
                        bound = f.bound,
                        boundCovers = f.boundCovers,
                    )
                }
        }.onFailure {
            logcat(LogPriority.WARN) { "DiscoveryFeedStore: decode failed: ${it.message}" }
        }.getOrDefault(emptyMap())
    }

    /** Insert or replace one feed's snapshot — full-file rewrite (the
     *  file is small; per-feed files would multiply tmp-rename work). */
    suspend fun save(
        feed: String,
        items: List<CatalogItem>,
        page: Int,
        hasMore: Boolean,
        bound: Map<String, Long>,
        boundCovers: Map<String, String> = emptyMap(),
    ) = withContext(Dispatchers.IO) {
        val current = runCatching {
            if (file.exists()) json.decodeFromString<FeedFile>(file.readText()).feeds else emptyMap()
        }.getOrDefault(emptyMap()).toMutableMap()
        current[feed] = StoredFeed(
            items = items.map { it.toStored() },
            page = page,
            hasMore = hasMore,
            savedAt = System.currentTimeMillis(),
            bound = bound,
            boundCovers = boundCovers,
        )
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(FeedFile(current)))
            java.nio.file.Files.move(
                tmp.toPath(),
                file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }.onFailure {
            logcat(LogPriority.WARN) { "DiscoveryFeedStore: save failed: ${it.message}" }
        }
    }

    @Serializable
    private data class FeedFile(
        val feeds: Map<String, StoredFeed> = emptyMap(),
    )

    @Serializable
    private data class StoredFeed(
        val items: List<StoredItem> = emptyList(),
        val page: Int = 0,
        val hasMore: Boolean = false,
        val savedAt: Long = 0,
        val bound: Map<String, Long> = emptyMap(),
        val boundCovers: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class StoredItem(
        val workId: String,
        val type: String = "",
        val title: String,
        val author: String? = null,
        val year: Long? = null,
        val status: String? = null,
        val nsfw: Boolean = false,
        val genres: List<String>? = null,
        val coverUrl: String? = null,
        val description: String? = null,
        val externalIds: Map<String, String> = emptyMap(),
        val resolutionSource: String = "",
        // Mint candidate — null fields = canonical row, nothing to resolve.
        val cMedium: String? = null,
        val cTitle: String? = null,
        val cUrl: String? = null,
        val cAuthor: String? = null,
        val cWebviewUrls: List<String> = emptyList(),
        val cIsbn: String? = null,
        val cIsBook: Boolean = false,
        val cKindHint: String? = null,
    )

    companion object {
        private fun CatalogItem.toStored(): StoredItem {
            val c = mintCandidate
            return StoredItem(
                workId = work.mikoWorkId,
                type = work.type,
                title = work.title,
                author = work.author,
                year = work.year,
                status = work.status,
                nsfw = work.nsfw,
                genres = work.genres,
                coverUrl = coverUrl ?: work.coverUrl,
                description = work.description,
                externalIds = work.externalIds,
                resolutionSource = work.resolutionSource,
                cMedium = c?.medium,
                cTitle = c?.title,
                cUrl = c?.url,
                cAuthor = c?.author,
                cWebviewUrls = c?.webviewUrls.orEmpty(),
                cIsbn = c?.isbn,
                cIsBook = c?.isBook ?: false,
                cKindHint = c?.kindHint,
            )
        }

        private fun StoredItem.toCatalogItem(): CatalogItem = CatalogItem(
            work = WorkRow(
                mikoWorkId = workId,
                type = type,
                title = title,
                author = author,
                year = year,
                status = status,
                nsfw = nsfw,
                genres = genres,
                coverUrl = coverUrl,
                description = description,
                externalIds = externalIds,
                resolutionSource = resolutionSource,
            ),
            coverUrl = coverUrl,
            mintCandidate = cTitle?.let {
                WorkIdResolver.Candidate(
                    medium = cMedium ?: WorkIdResolver.MEDIUM_MANGA,
                    title = it,
                    url = cUrl,
                    author = cAuthor,
                    webviewUrls = cWebviewUrls,
                    isbn = cIsbn,
                    isBook = cIsBook,
                    kindHint = cKindHint,
                )
            },
        )
    }
}
