package miko.ui.navigation

import miko.ui.carrier.BrowseCarrier

/**
 * A screen destination in the shared navigation back-stack.
 *
 * Hand-rolled navigation (Phase 27 architecture decision D3): destinations are
 * plain values rendered by the nav host's `when` dispatch. Voyager migration
 * is deferred to the `app/` convergence phase.
 */
sealed interface Destination {

    /** Landing tab — greeting + continue rail (Android's HomeHub, scoped). */
    data object Home : Destination

    /** List of installed sources the user can browse. */
    data object Browse : Destination

    /** Paged feed of entries served by a single source. */
    data class SourceFeed(
        val sourceId: Long,
        val sourceName: String,
        val carrier: BrowseCarrier = BrowseCarrier.BOOKS,
    ) : Destination

    /** Details for one catalogue entry; [entryKey] is the source-local key. */
    data class Details(
        val sourceId: Long,
        val entryKey: String,
        val title: String,
        val carrier: BrowseCarrier = BrowseCarrier.BOOKS,
    ) : Destination

    /** Saved entries (favorites) backed by the local database. */
    data object Library : Destination

    /** Recent chapter/episode updates + read/watch history — Android's UpdatesTab. */
    data object Updates : Destination

    /** Android's MoreTab — stats/settings entry points. */
    data object More : Destination

    /** Per-carrier library/reading stats — pushed from More. */
    data object Stats : Destination

    /** Achievement list + activity stats — Android's AchievementScreenVoyager. */
    data object Achievements : Destination

    /** App preferences — pushed from More. */
    data object Settings : Destination

    /** Collection management — Android's CollectionsTab, pushed from More/details. */
    data object Collections : Destination

    /** Pending/failed item downloads — Android's DownloadQueue, pushed from More. */
    data object DownloadQueue : Destination

    /** Extension-repo management — consolidated per-type tabs + add/delete. */
    data object ExtensionRepos : Destination

    /** Cross-source search results — Android's GlobalSearchScreen. */
    data class GlobalSearch(
        val query: String,
        val carrier: BrowseCarrier = BrowseCarrier.BOOKS,
    ) : Destination

    /** Novel chapter reader — [chapterUrl] identifies the open chapter. */
    data class Reader(val sourceId: Long, val novelUrl: String, val chapterUrl: String) : Destination

    /** Manga image reader — [chapterUrl] identifies the open chapter. */
    data class MangaReader(val sourceId: Long, val mangaUrl: String, val chapterUrl: String) : Destination

    /** Anime episode player — resolves hosters/streams for [episodeUrl]. */
    data class EpisodePlayer(
        val sourceId: Long,
        val entryUrl: String,
        val episodeUrl: String,
    ) : Destination
}
