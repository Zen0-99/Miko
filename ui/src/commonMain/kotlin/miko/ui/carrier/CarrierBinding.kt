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
    /** Carrier chapterFlags/episodeFlags semantics — sort + unread filter. */
    val sortDescending: Boolean = false,
    val unreadOnly: Boolean = false,
    /** Carrier viewer flag — manga reading mode 0..5; 0 = use pref default. */
    val viewer: Int = 0,
)

/** Local chapter/episode row; [done] is read (books/manga) or seen (shows). */
data class LocalItem(
    val id: Long,
    val url: String,
    val name: String,
    val done: Boolean,
    /** Manga reader position — Android's `last_page_read`. */
    val lastPageRead: Int = 0,
    /** Android's per-item bookmark flag. */
    val bookmarked: Boolean = false,
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

/** Resolved navigation target for an id-keyed row — urls + owning source. */
data class NavTarget(val sourceId: Long, val entryUrl: String, val itemUrl: String)

/** A catalogue source exposed uniformly — chapters, episodes, or volumes. */
interface CarrierSource {
    val id: Long
    val name: String
    val lang: String
    val supportsLatest: Boolean

    /**
     * The carrier's live filter list (NovelFilterList / FilterList /
     * AnimeFilterList) wrapped per row for the feed UI, or empty when the
     * source exposes none. Mutable — filter edits write through to the
     * carrier objects so the next listing call picks them up.
     */
    fun filters(): List<FeedFilter> = emptyList()

    /**
     * Discards the active filter list and re-wraps a fresh one with
     * descriptor defaults — the sheet's Reset action.
     */
    fun resetFilters(): List<FeedFilter> = filters()

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

    /**
     * Maps an update/history row's entry+item ids to urls for navigation —
     * feed rows carry ids while details/reader destinations are url-keyed.
     * The source id comes from the local entry (history rows don't carry it).
     */
    suspend fun resolveNavTarget(entryId: Long, itemId: Long): NavTarget? {
        val local = localEntryById(entryId) ?: return null
        val item = getLocalItems(local.id).firstOrNull { it.id == itemId }
            ?: return null
        return NavTarget(local.sourceId, local.url, item.url)
    }
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

    /** Manga reader position — Android's `last_page_read` write. */
    suspend fun setItemPageProgress(itemId: Long, page: Int) = Unit

    /** Android's per-item bookmark toggle. */
    suspend fun setItemBookmark(itemId: Long, bookmarked: Boolean) = Unit

    /** Chapter-list sort direction — the entry's chapterFlags sort bit. */
    suspend fun setChapterSortDescending(localId: Long, descending: Boolean) = Unit

    /** Chapter-list unread filter — the entry's chapterFlags unread bits. */
    suspend fun setChapterUnreadOnly(localId: Long, onlyUnread: Boolean) = Unit

    /**
     * Per-entry reading mode — manga only; [flag] is the mode's flagValue
     * (0 clears to the global pref). Other carriers no-op.
     */
    suspend fun setViewer(localId: Long, flag: Int) = Unit

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
