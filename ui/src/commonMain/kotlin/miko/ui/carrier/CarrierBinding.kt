package miko.ui.carrier

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Carrier-agnostic catalogue + library surface the shared screens consume.
 * One implementation per [BrowseCarrier] wraps that carrier's domain
 * source manager, entry repository, and item (chapter/episode) repository
 * so the feed/details/reader code paths are written once.
 */

data class FeedEntry(
    val url: String,
    val title: String,
    val coverUrl: String?,
    val subtitle: String? = null,
)

data class FeedPage(
    val entries: List<FeedEntry>,
    val hasNextPage: Boolean,
)

/** Remote entry details — flattened across SNovel/SManga/SAnime. */
data class RemoteDetails(
    val url: String,
    val title: String,
    val author: String?,
    val artist: String?,
    val description: String?,
    val genre: List<String>?,
    val status: Int,
    val thumbnailUrl: String?,
    val backgroundUrl: String? = null,
)

/** Remote chapter/episode. */
data class RemoteItem(
    val url: String,
    val name: String,
    val number: Double,
    val dateUpload: Long,
    val scanlator: String?,
    val summary: String? = null,
    val previewUrl: String? = null,
)

/** Local library row — flattened across Novel/Manga/Anime. */
data class LocalEntry(
    val id: Long,
    val title: String,
    val favorite: Boolean,
    val url: String = "",
    val sourceId: Long = 0,
)

/** Local chapter/episode row; [done] is read (books/manga) or seen (shows). */
data class LocalItem(
    val id: Long,
    val url: String,
    val name: String,
    val done: Boolean,
)

/** A manga reader page. */
data class PageEntry(
    val index: Int,
    val url: String,
    val imageUrl: String?,
)

/** An anime episode's stream host. */
data class HosterEntry(
    val url: String,
    val name: String,
    val internalData: String,
    val lazy: Boolean,
    val videos: List<VideoEntry>? = null,
)

/** A resolved video stream. */
data class VideoEntry(
    val url: String,
    val title: String,
)

/** A catalogue source exposed uniformly — chapters, episodes, or volumes. */
interface CarrierSource {
    val id: Long
    val name: String
    val lang: String
    val supportsLatest: Boolean

    suspend fun popular(page: Int): FeedPage
    suspend fun latest(page: Int): FeedPage
    suspend fun search(page: Int, query: String): FeedPage
    suspend fun details(entryUrl: String, stubTitle: String): RemoteDetails?
    suspend fun items(entryUrl: String): List<RemoteItem>
}

interface CarrierBinding {
    val isInitialized: StateFlow<Boolean>
    val catalogueSources: Flow<List<CarrierSource>>

    fun source(sourceId: Long): CarrierSource?

    fun localEntry(url: String, sourceId: Long): Flow<LocalEntry?>
    suspend fun localEntryById(id: Long): LocalEntry? = null
    suspend fun materialize(details: RemoteDetails, sourceId: Long): LocalEntry
    suspend fun setFavorite(localId: Long, favorite: Boolean)

    fun localItems(localId: Long): Flow<List<LocalItem>>
    suspend fun getLocalItems(localId: Long): List<LocalItem>

    /** Insert remote items missing locally (compares by url). */
    suspend fun addMissingItems(localId: Long, remote: List<RemoteItem>)

    /**
     * Manual library update — Android's refresh action. Refetches every
     * favorited entry's item list from its source and inserts the missing
     * chapters/episodes; new items land in the updates view because their
     * date_fetch postdates the entry's date_added. Returns entries fetched.
     */
    suspend fun refreshFavorites(): Int = 0

    suspend fun setItemDone(itemId: Long, done: Boolean)
    suspend fun setAllItemsDone(localId: Long, done: Boolean)

    /** Chapter body text — books only; other carriers return "". */
    suspend fun itemText(sourceId: Long, itemUrl: String): String = ""

    /** Reader pages — manga only; other carriers return [emptyList]. */
    suspend fun pageList(sourceId: Long, itemUrl: String): List<PageEntry> = emptyList()

    /** Episode hosters — shows only; other carriers return [emptyList]. */
    suspend fun hosters(sourceId: Long, itemUrl: String): List<HosterEntry> = emptyList()

    /** Resolved streams for a hoster target — shows only. */
    suspend fun videos(sourceId: Long, target: String): List<VideoEntry> = emptyList()

    /**
     * History row — recorded when an item opens for reading/watching
     * (Android upserts on reader/player open).
     */
    suspend fun recordItemOpen(itemId: Long) = Unit

    /**
     * Session close — accumulates read/watch duration into the history row
     * for carriers that track it (books/manga); a no-op elsewhere.
     */
    suspend fun recordItemClose(itemId: Long) = Unit
}
