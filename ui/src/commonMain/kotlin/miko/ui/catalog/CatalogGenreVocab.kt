package miko.ui.catalog

/**
 * Android's `CatalogGenreVocab` — facet-pill vocabulary for feeds with no
 * enumerable genre source of their own (the canonical Miko catalog).
 * `genreNames` matching is a case-insensitive contains-match.
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
        CatalogRowSpec.MEDIUM_BOOKS -> BOOK_TAGS
        else -> MANGA_TAGS
    }
}
