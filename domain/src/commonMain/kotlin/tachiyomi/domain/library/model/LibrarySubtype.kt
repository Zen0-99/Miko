package tachiyomi.domain.library.model

/**
 * Sub-category filter inside a library mode — orthogonal to collections
 * and display modes. One enum shared by all three carriers; each mode
 * only offers its own pair plus [ALL]:
 *
 * - Manga mode: [MANGA] / [WEBTOON]
 * - Books mode: [BOOK] / [NOVEL] / [AUDIOBOOK]
 * - Shows mode: [SERIES] / [MOVIE]
 *
 * The selection applies across every collection in the mode and is
 * persisted per carrier (see LibraryPreferences).
 */
enum class LibrarySubtype {
    ALL,
    MANGA,
    WEBTOON,
    BOOK,
    NOVEL,
    AUDIOBOOK,
    SERIES,
    MOVIE,
}
