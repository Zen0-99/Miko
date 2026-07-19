package tachiyomi.domain.library.model

/**
 * Display mode for library categories.
 *
 * - [TABBED]: Each category on its own swipeable page with a tab strip (default, existing behavior).
 * - [CONTINUOUS]: All categories in a single scroll with section headers.
 */
enum class LibraryCategoryDisplay {
    TABBED,
    CONTINUOUS,
}
