package tachiyomi.domain.library.model

/**
 * Display mode for library collections.
 *
 * - [TABBED]: Each collection on its own swipeable page with a tab strip (default, existing behavior).
 * - [CONTINUOUS]: All collections in a single scroll with section headers.
 */
enum class LibraryCollectionDisplay {
    TABBED,
    CONTINUOUS,
}
