package eu.kanade.tachiyomi.data.catalogrows

import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver

/**
 * Facet-pill vocabulary for feeds with no enumerable genre source of
 * their own (the canonical Miko catalog — the worker owns no static
 * client-side list). `genreNames` matching is a case-insensitive
 * contains-match, so the names here are the common western tags the
 * worker's works carry.
 */
object CatalogGenreVocab {

    private val MANGA_TAGS = listOf(
        "Action", "Adventure", "Comedy", "Cooking", "Drama", "Fantasy",
        "Historical", "Horror", "Isekai", "Martial Arts", "Mecha",
        "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life",
        "Sports", "Supernatural", "Thriller", "Tragedy",
    )

    private val BOOK_TAGS = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Fantasy",
        "Historical", "Horror", "LitRPG", "Mystery", "Romance",
        "Sci-Fi", "Slice of Life", "Thriller",
    )

    fun forMedium(medium: String): List<String> = when (medium) {
        WorkIdResolver.MEDIUM_BOOKS -> BOOK_TAGS
        else -> MANGA_TAGS
    }
}
