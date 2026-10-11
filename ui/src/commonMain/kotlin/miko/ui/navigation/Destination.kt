package miko.ui.navigation

/**
 * A screen destination in the shared navigation back-stack.
 *
 * Hand-rolled navigation (Phase 27 architecture decision D3): destinations are
 * plain values rendered by the nav host's `when` dispatch. Voyager migration
 * is deferred to the `app/` convergence phase.
 */
sealed interface Destination {

    /** List of installed sources the user can browse. */
    data object Browse : Destination

    /** Paged feed of entries served by a single source. */
    data class SourceFeed(val sourceId: Long) : Destination

    /** Details for one catalogue entry; [entryKey] is the source-local key. */
    data class Details(val sourceId: Long, val entryKey: String) : Destination

    /** Saved entries (favorites) backed by the local database. */
    data object Library : Destination

    /** Extension-repo management — consolidated per-type tabs + add/delete. */
    data object ExtensionRepos : Destination

    /** Cross-source novel search results — Android's GlobalNovelSearchScreen. */
    data class GlobalSearch(val query: String) : Destination
}
