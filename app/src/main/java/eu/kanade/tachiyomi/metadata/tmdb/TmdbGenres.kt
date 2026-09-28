package eu.kanade.tachiyomi.metadata.tmdb

/**
 * Static TMDB genre vocab for the Discovery facet sheet — genre IDs are
 * stable and small enough to ship as a table instead of fetching
 * `genre/movie/list` / `genre/tv/list` at runtime.
 *
 * Movie and TV vocabularies differ: TV folds some movie genres into
 * combined entries (10759 "Action & Adventure", 10765 "Sci-Fi & Fantasy",
 * 10768 "War & Politics") and adds TV-only kinds (Kids, News, Reality,
 * Soap, Talk).
 */
object TmdbGenres {

    data class Genre(val id: Int, val name: String)

    enum class MediaType { MOVIE, TV }

    val MOVIE: List<Genre> = listOf(
        Genre(28, "Action"),
        Genre(12, "Adventure"),
        Genre(16, "Animation"),
        Genre(35, "Comedy"),
        Genre(80, "Crime"),
        Genre(99, "Documentary"),
        Genre(18, "Drama"),
        Genre(10751, "Family"),
        Genre(14, "Fantasy"),
        Genre(36, "History"),
        Genre(27, "Horror"),
        Genre(10402, "Music"),
        Genre(9648, "Mystery"),
        Genre(10749, "Romance"),
        Genre(878, "Science Fiction"),
        Genre(10770, "TV Movie"),
        Genre(53, "Thriller"),
        Genre(10752, "War"),
        Genre(37, "Western"),
    )

    val TV: List<Genre> = listOf(
        Genre(10759, "Action & Adventure"),
        Genre(16, "Animation"),
        Genre(35, "Comedy"),
        Genre(80, "Crime"),
        Genre(99, "Documentary"),
        Genre(18, "Drama"),
        Genre(10751, "Family"),
        Genre(10762, "Kids"),
        Genre(9648, "Mystery"),
        Genre(10763, "News"),
        Genre(10764, "Reality"),
        Genre(10765, "Sci-Fi & Fantasy"),
        Genre(10766, "Soap"),
        Genre(10767, "Talk"),
        Genre(10768, "War & Politics"),
        Genre(37, "Western"),
    )

    /** Combined TV genres → the movie-style genre names they fold
     *  together, for sub-genre keyword lookup. */
    private val COMBINED_NAMES: Map<String, List<String>> = mapOf(
        "Action & Adventure" to listOf("Action", "Adventure"),
        "Sci-Fi & Fantasy" to listOf("Science Fiction", "Fantasy"),
        "War & Politics" to listOf("War", "History"),
    )

    fun forType(type: MediaType): List<Genre> = when (type) {
        MediaType.MOVIE -> MOVIE
        MediaType.TV -> TV
    }

    /** Constituent names for a (possibly combined) genre name — the name
     *  itself when it isn't a combined entry. */
    fun constituents(name: String): List<String> = COMBINED_NAMES[name] ?: listOf(name)

    /** Genre name for an id across both vocabularies (chips display). */
    fun nameForId(id: Int): String? =
        (MOVIE + TV).firstOrNull { it.id == id }?.name
}
