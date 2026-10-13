package miko.ui.carrier

import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import miko.domain.shows.SeasonDisplayMode
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.collection.model.Collection

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
    /** Source listing genre CSV — feeds catalog-row facet matching. */
    val genre: String? = null,
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
    /**
     * Shows — the source's declared fetch shape (Android's
     * `SAnime.fetch_type`): `Seasons` entries only call getSeasonList,
     * season/flat rows only getEpisodeList. Null on other carriers.
     */
    val fetchType: FetchType? = null,
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
    /**
     * Android's season model — the SAnime season row's url (identity for
     * the child-row upsert) and display number; null/-1 on flat sources.
     */
    val seasonUrl: String? = null,
    val seasonNumber: Double = -1.0,
    /** Season row's source title — feeds SeasonRecognition + child-row title. */
    val seasonTitle: String? = null,
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
    /** chapterFlags sorting bits — Android's SOURCE/NUMBER/UPLOAD/ALPHA. */
    val sortMode: ChapterSortMode = ChapterSortMode.NUMBER,
    /** chapterFlags bookmarked filter — Android's TriState. */
    val bookmarkedFilter: TriState = TriState.DISABLED,
    /** chapterFlags display bit — "Chapter N"/"Episode N" vs scanlator title. */
    val displayByNumber: Boolean = false,
    /** chapterFlags unread filter — Android's TriState. */
    val unreadFilter: TriState = TriState.DISABLED,
    /** episodeFlags fillermarked filter (shows) — Android's TriState. */
    val fillermarkedFilter: TriState = TriState.DISABLED,
    /** episodeFlags display bits (shows) — preview thumbs + summaries, default-on. */
    val showPreviews: Boolean = true,
    val showSummaries: Boolean = true,
    /** Carrier viewer flag — manga reading mode 0..5; 0 = use pref default. */
    val viewer: Int = 0,
    /** Manga viewerFlags 0x38 bits — ReaderOrientation flag; 0 = use pref default. */
    val viewerOrientation: Int = 0,
    /**
     * Shows — decoded `seasonFlags` (Android's `anime.season*` accessors),
     * powering the season list's filters, sort, display mode, and badges.
     */
    val seasonDownloadedFilter: TriState = TriState.DISABLED,
    val seasonUnseenFilter: TriState = TriState.DISABLED,
    val seasonStartedFilter: TriState = TriState.DISABLED,
    val seasonCompletedFilter: TriState = TriState.DISABLED,
    val seasonBookmarkedFilter: TriState = TriState.DISABLED,
    val seasonFillermarkedFilter: TriState = TriState.DISABLED,
    /** Raw sort bits — `Anime.SEASON_SORT_*`; ui maps to the comparator. */
    val seasonSorting: Long = 0,
    val seasonSortDescending: Boolean = false,
    val seasonDisplayGridMode: SeasonDisplayMode = SeasonDisplayMode.CompactGrid,
    /** Grid columns / list rows — 0 = auto. */
    val seasonDisplayGridSize: Int = 0,
    val seasonDownloadedOverlay: Boolean = false,
    val seasonUnseenOverlay: Boolean = true,
    val seasonLocalOverlay: Boolean = true,
    val seasonLangOverlay: Boolean = false,
    val seasonContinueOverlay: Boolean = true,
    /** `Anime.SEASON_DISPLAY_MODE_*` — source title vs "Season N". */
    val seasonDisplayMode: Long = 0,
    /**
     * Shows — `viewerFlags` ANIME_INTRO_MASK/`ANIME_INTRO_DISABLE_MASK`
     * accessors. 0 length = the `defaultIntroLength` pref; the disable
     * bit hides the player's skip-intro control for this entry.
     */
    val skipIntroLength: Int = 0,
    val skipIntroDisable: Boolean = false,
)

/**
 * Android's CHAPTER/EPISODE sorting-flag modes — carrier bindings map the
 * enum onto each carrier's flag bits. SOURCE keeps the remote list order.
 */
enum class ChapterSortMode { SOURCE, NUMBER, UPLOAD_DATE, ALPHABET }

/** Android's chapterFlags TriState predicate — include/exclude/off. */
internal fun triStateMatch(state: TriState, matches: Boolean) = when (state) {
    TriState.DISABLED -> true
    TriState.ENABLED_IS -> matches
    TriState.ENABLED_NOT -> !matches
}

/**
 * Orders [items] per the mode, then applies [descending] — Android's
 * sortDescending reverses even raw source order. Shared by the details list
 * and every reader/player item list so all sites sort identically.
 */
fun ChapterSortMode.ordered(items: List<RemoteItem>, descending: Boolean): List<RemoteItem> {
    val sorted = when (this) {
        ChapterSortMode.SOURCE -> items
        ChapterSortMode.UPLOAD_DATE -> items.sortedBy { it.dateUpload }
        ChapterSortMode.ALPHABET -> items.sortedBy { it.name.lowercase() }
        ChapterSortMode.NUMBER -> items.sortedBy { it.number }
    }
    return if (descending) sorted.asReversed() else sorted
}

/** Local chapter/episode row; [done] is read (books/manga) or seen (shows). */
data class LocalItem(
    val id: Long,
    val url: String,
    val name: String,
    /** chapterNumber/episodeNumber — migration state transfer matches on it. */
    val number: Double = -1.0,
    /** Android's date_upload — the UPLOAD_DATE chapter sort's key. */
    val dateUpload: Long = 0,
    val done: Boolean,
    /** In-item resume position — Android's `last_page_read` (manga) / `lastCharRead` (novel). */
    val lastPageRead: Int = 0,
    /** Chapter length in chars (novels) — lastPageRead/totalChars = read percent. */
    val totalChars: Long = 0,
    /** Playback resume position (shows) — Android's `lastSecondSeen`. */
    val lastSecondSeen: Long = 0,
    /** Season row's display number (shows) — -1 for flat sources. */
    val seasonNumber: Double = -1.0,
    /** Stream duration (shows) — drives the episode progress bar fraction. */
    val totalSeconds: Long = 0,
    /** Android's per-item bookmark flag. */
    val bookmarked: Boolean = false,
    /** Episode filler flag (shows) — `fillermark || canonicalFiller`. */
    val fillermarked: Boolean = false,
)

/**
 * A season row on a shows entry — Android's `SeasonAnime` aggregate
 * (child `Anime` row + per-season episode stats) flattened for the UI.
 */
data class LocalSeason(
    /** Child row id — the drill-in entry id. */
    val id: Long,
    val url: String,
    val title: String,
    val seasonNumber: Double,
    val sourceOrder: Long,
    val thumbnailUrl: String?,
    /** SAnime status int — Android's completed filter compares to COMPLETED. */
    val status: Int,
    /** Every episode seen — the pill's check / the seen filter. */
    val seen: Boolean,
    val unseenCount: Long,
    val hasStarted: Boolean,
    val hasBookmarks: Boolean,
    val hasFillermarks: Boolean,
    /** Sort keys — Android's SeasonSorter fields. */
    val latestUpload: Long,
    val fetchedAt: Long,
    val lastSeen: Long,
    /** Source language tag — Android's LanguageBadge on season covers. */
    val sourceLanguage: String = "",
)

/**
 * Android's `SetAnimeSeasonFlags` operations — the ui computes raw flag
 * values from `Anime.SEASON_*` constants; the binding persists via the
 * shared interactor so flag math stays in one place.
 */
sealed interface SeasonFlagOp {
    data class DownloadedFilter(val flag: Long) : SeasonFlagOp
    data class UnseenFilter(val flag: Long) : SeasonFlagOp
    data class StartedFilter(val flag: Long) : SeasonFlagOp
    data class CompletedFilter(val flag: Long) : SeasonFlagOp
    data class BookmarkedFilter(val flag: Long) : SeasonFlagOp
    data class FillermarkedFilter(val flag: Long) : SeasonFlagOp
    data class SortModeOrFlip(val flag: Long) : SeasonFlagOp
    data class GridMode(val mode: SeasonDisplayMode) : SeasonFlagOp
    data class GridSize(val size: Int) : SeasonFlagOp
    data class DownloadedOverlay(val on: Boolean) : SeasonFlagOp
    data class UnseenOverlay(val on: Boolean) : SeasonFlagOp
    data class LocalOverlay(val on: Boolean) : SeasonFlagOp
    data class LangOverlay(val on: Boolean) : SeasonFlagOp
    data class ContinueOverlay(val on: Boolean) : SeasonFlagOp
    data class DisplayMode(val flag: Long) : SeasonFlagOp
}

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

/**
 * A resolved video stream. [timestamps] are the extension's chapter
 * markers (`Video.timestamps`) — Android merges them into the player's
 * segment list that drives the skip-intro button and auto-skip.
 */
data class VideoEntry(
    val url: String,
    val title: String,
    val timestamps: List<TimeStamp> = emptyList(),
)

/** Resolved navigation target for an id-keyed row — urls + owning source. */
data class NavTarget(val sourceId: Long, val entryUrl: String, val itemUrl: String)

/**
 * Shared row-swipe vocabulary — domain ChapterSwipeAction maps
 * ToggleRead→ToggleDone, EpisodeSwipeAction maps ToggleSeen→ToggleDone and
 * keeps ToggleFillermark for shows.
 */
enum class ItemSwipeAction { ToggleDone, ToggleBookmark, ToggleFillermark, Download, Disabled }

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
     * Favorited entry urls for [sourceId] — drives the browse feed's
     * in-library badge (Android's `InLibraryBadge` + dimmed cover).
     */
    fun favoritedUrls(sourceId: Long): Flow<Set<String>> = flowOf(emptySet())

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

    /**
     * Android's `getNextUnread` — the entry's flag filters + sort applied,
     * then the first undone item in display order (descending picks the
     * list's last undone, matching `findLast` on the unreversed list).
     * Used by the library's continue-viewing button.
     */
    suspend fun nextUndoneItem(entryId: Long): NavTarget? {
        val local = localEntryById(entryId) ?: return null
        val items = getLocalItems(local.id).filter { item ->
            triStateMatch(local.unreadFilter, !item.done) &&
                triStateMatch(local.bookmarkedFilter, item.bookmarked) &&
                triStateMatch(local.fillermarkedFilter, item.fillermarked)
        }
        val sorted = when (local.sortMode) {
            // DB order stands in for the remote's source order — the
            // chapters table preserves insertion order.
            ChapterSortMode.SOURCE -> items
            ChapterSortMode.NUMBER -> items.sortedBy { it.number }
            ChapterSortMode.UPLOAD_DATE -> items.sortedBy { it.dateUpload }
            ChapterSortMode.ALPHABET -> items.sortedBy { it.name.lowercase() }
        }
        val next = if (local.sortDescending) {
            sorted.lastOrNull { !it.done }
        } else {
            sorted.firstOrNull { !it.done }
        }
        return next?.let { NavTarget(local.sourceId, local.url, it.url) }
    }

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

    /**
     * Playback position write (shows) — Android's
     * `EpisodeUpdate(lastSecondSeen, totalSeconds)`.
     */
    suspend fun setItemPlayback(itemId: Long, positionSeconds: Long, totalSeconds: Long) = Unit

    /** Android's per-item bookmark toggle. */
    suspend fun setItemBookmark(itemId: Long, bookmarked: Boolean) = Unit

    /** Episode filler flag (shows) — `EpisodeUpdate.fillermark`. */
    suspend fun setItemFillermark(itemId: Long, fillermarked: Boolean) = Unit

    /**
     * Scanlator exclusion — Android's `ScanlatorFilterDialog` backing
     * (manga only; other carriers report no scanlators). The local item
     * flow is already exclusion-filtered; the details model applies the
     * same set to remote items at display time.
     */
    suspend fun getItemScanlators(localId: Long): Set<String> = emptySet()
    fun excludedScanlators(localId: Long): Flow<Set<String>> = flowOf(emptySet())
    suspend fun setExcludedScanlators(localId: Long, excluded: Set<String>) = Unit

    /**
     * Season rows + aggregates (shows) — Android's
     * `getAnimeSeasonsByIdAsFlow`; empty for flat sources and other
     * carriers.
     */
    fun localSeasons(localId: Long): Flow<List<LocalSeason>> = flowOf(emptyList())

    /** Seeds `seasonFlags` from the shared defaults when still 0 — Android's `seasonFlags == 0L` guard. */
    suspend fun ensureSeasonDefaults(localId: Long) = Unit

    /** Single season-flag write — Android's `SetAnimeSeasonFlags` op. */
    suspend fun applySeasonFlag(localId: Long, op: SeasonFlagOp) = Unit

    /**
     * Android's "Set season settings as default" — copies this entry's
     * seasonFlags into the shared default prefs; [applyToExisting]
     * rewrites every favorite's flags (SetAnimeDefaultSeasonFlags.awaitAll).
     */
    suspend fun setSeasonSettingsAsDefault(localId: Long, applyToExisting: Boolean) = Unit

    /**
     * Android's chapter-settings overflow actions — "Set as default"
     * copies this entry's flags into the shared default prefs,
     * "Reset" rewrites the entry's flags from them, and
     * applyToExisting rewrites every favorite's flags.
     */
    suspend fun setEntryFlagsAsDefault(localId: Long) = Unit
    suspend fun resetEntryFlagsToDefault(localId: Long) = Unit
    suspend fun applyDefaultFlagsToAll() = Unit

    /** Visible collections — Android's GetVisibleXCollections. */
    fun collections(): Flow<List<Collection>> = flowOf(emptyList())

    /** Collections the entry belongs to — Android's GetXCollections(entryId). */
    fun entryCollections(localId: Long): Flow<List<Collection>> = flowOf(emptyList())

    /**
     * Which of [entryIds] belong to [collectionId] — the library's
     * collection filter. Defaults to an all-pass so carriers without
     * collections keep their grid unfiltered.
     */
    suspend fun filterEntriesByCollection(
        collectionId: Long,
        entryIds: List<Long>,
    ): Set<Long> = entryIds.toSet()

    /** Android's SetXCollections — replaces the entry's memberships. */
    suspend fun setEntryCollections(localId: Long, collectionIds: List<Long>) = Unit

    /** Android's CreateXCollectionWithName. Returns the new row id or -1. */
    suspend fun createCollection(name: String): Long = -1L

    /** Android's RenameXCollection — CollectionUpdate(name). */
    suspend fun renameCollection(collectionId: Long, name: String) = Unit

    /** Android's DeleteXCollection. */
    suspend fun deleteCollection(collectionId: Long) = Unit

    /** Chapter-list sort direction — the entry's chapterFlags sort bit. */
    suspend fun setChapterSortDescending(localId: Long, descending: Boolean) = Unit

    /** Chapter-list unread filter — the entry's chapterFlags unread bits. */
    suspend fun setChapterUnreadFilter(localId: Long, state: TriState) = Unit

    /** Chapter-list sort mode — the entry's chapterFlags sorting bits. */
    suspend fun setChapterSortMode(localId: Long, mode: ChapterSortMode) = Unit

    /** Chapter-list bookmarked filter — the chapterFlags bookmarked bits. */
    suspend fun setChapterBookmarkedFilter(localId: Long, state: TriState) = Unit

    /** Episode-list fillermarked filter (shows) — the episodeFlags filler bits. */
    suspend fun setChapterFillermarkedFilter(localId: Long, state: TriState) = Unit

    /** Episode-list preview thumbnails (shows) — the episodeFlags previews bit. */
    suspend fun setChapterShowPreviews(localId: Long, show: Boolean) = Unit

    /** Episode-list summary blurbs (shows) — the episodeFlags summaries bit. */
    suspend fun setChapterShowSummaries(localId: Long, show: Boolean) = Unit

    /**
     * Chapter-list display mode — the chapterFlags display bit; rows show
     * "Chapter N"/"Episode N" instead of the source title when set.
     */
    suspend fun setChapterDisplayByNumber(localId: Long, byNumber: Boolean) = Unit

    /**
     * Per-entry reading mode — manga only; [flag] is the mode's flagValue
     * (0 clears to the global pref). Other carriers no-op.
     */
    suspend fun setViewer(localId: Long, flag: Int) = Unit

    /**
     * Per-entry reader orientation — manga only; [flag] is a
     * ReaderOrientation flagValue (0 clears to the global pref).
     * Other carriers no-op.
     */
    suspend fun setViewerOrientation(localId: Long, flag: Int) = Unit

    /** Chapter body text — books only; other carriers return "". */
    suspend fun itemText(sourceId: Long, itemUrl: String): String = ""

    /**
     * Android's chapter/episode swipe prefs projected to a shared vocabulary —
     * (start-edge, end-edge) actions. Start is the swipe-right reveal; the
     * domain's EpisodeSwipeAction maps ToggleSeen→ToggleDone.
     */
    suspend fun swipeActions(): Pair<ItemSwipeAction, ItemSwipeAction> =
        ItemSwipeAction.Disabled to ItemSwipeAction.Disabled

    /** Reader pages — manga only; other carriers return [emptyList]. */
    suspend fun pageList(sourceId: Long, itemUrl: String): List<PageEntry> = emptyList()

    /**
     * Payload descriptor for an item download — what the DownloadManager
     * should write into the item's download dir. Manga maps pageList to
     * numbered image files, novels write the chapter text; shows return
     * null (hoster streams can't file-download).
     */
    suspend fun itemPayload(
        sourceId: Long,
        itemUrl: String,
    ): miko.ui.download.ItemPayload? = null

    /** Episode hosters — shows only; other carriers return [emptyList]. */
    suspend fun hosters(sourceId: Long, itemUrl: String): List<HosterEntry> = emptyList()

    /** Resolved streams for a hoster target — shows only. */
    suspend fun videos(sourceId: Long, target: String): List<VideoEntry> = emptyList()

    /**
     * Android's `PlayerViewModel.aniSkipResponse` — AniSkip timestamps for
     * this episode via the entry's track record (MAL id direct, AniList id
     * resolved through GraphQL). Empty until tracking lands (Phase 32) or
     * when the API has no data; shows only.
     */
    suspend fun skipTimes(
        localId: Long,
        episodeNumber: Int,
        durationSec: Double,
    ): List<TimeStamp> = emptyList()

    /**
     * Android's `SetAnimeViewerFlags.awaitSetSkipIntroLength` — writes the
     * per-entry intro length (seconds) into viewerFlags; 0 restores the
     * `defaultIntroLength` pref and also clears the disable bit.
     * Shows only; other carriers no-op.
     */
    suspend fun setSkipIntroLength(localId: Long, seconds: Int) = Unit

    /**
     * History row — recorded when an item opens for reading/watching
     * (Android upserts on reader/player open).
     */
    suspend fun recordItemOpen(itemId: Long) = Unit

    /** Android's RemoveXHistory.await(history) — resets one history row. */
    suspend fun resetHistoryRow(historyId: Long) = Unit

    /** Android's RemoveXHistory.await(entryId) — resets all rows for an entry. */
    suspend fun resetEntryHistory(entryId: Long) = Unit

    /** Android's RemoveXHistory.awaitAll — the history toolbar wipe. */
    suspend fun clearHistory(): Boolean = false

    /**
     * Session close — accumulates read/watch duration into the history row
     * for carriers that track it (books/manga); a no-op elsewhere.
     */
    suspend fun recordItemClose(itemId: Long) = Unit
}
