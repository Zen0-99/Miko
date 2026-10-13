package miko.ui.catalog

/**
 * Android's `TmdbGenres` — static TMDB genre vocab (genre IDs are stable;
 * shipping a table beats fetching `genre/{type}/list` at runtime).
 * Movie and TV vocabularies differ.
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

    /** Constituent names for a (possibly combined) genre name. */
    fun constituents(name: String): List<String> = COMBINED_NAMES[name] ?: listOf(name)

    /** Genre name for an id across both vocabularies (chips display). */
    fun nameForId(id: Int): String? =
        (MOVIE + TV).firstOrNull { it.id == id }?.name
}

/**
 * Android's `TmdbSubGenreKeywords` — genre-name → TMDB keyword-ID map
 * backing the facet sheet's sub-genre chips. Selected chips feed
 * `with_keywords`/`without_keywords` on the discover endpoints.
 */
object TmdbSubGenreKeywords {

    data class SubGenre(val name: String, val keywordId: Int)

    /** Sub-genres for one genre name; combined TV genres ("Action &
     *  Adventure" etc.) resolve to the merged constituents. */
    fun getSubGenres(genreName: String): List<SubGenre> =
        TmdbGenres.constituents(genreName)
            .flatMap { GENRE_SUB_GENRES[it].orEmpty() }
            .distinctBy { it.keywordId }

    /** All (genre name → sub-genres) pairs to render, merged so combined
     *  TV genre names appear once with their union. */
    fun groupsFor(genreNames: Collection<String>): List<Pair<String, List<SubGenre>>> =
        genreNames.mapNotNull { name ->
            getSubGenres(name).takeIf { it.isNotEmpty() }?.let { name to it }
        }

    /** Keyword id → label, for rendering active-facet chips. */
    fun nameForKeyword(keywordId: Int): String? = REVERSE[keywordId]

    private val REVERSE: Map<Int, String> by lazy {
        GENRE_SUB_GENRES.values.flatten().associate { it.keywordId to it.name }
    }

    private val GENRE_SUB_GENRES: Map<String, List<SubGenre>> = mapOf(
        "Action" to listOf(
            SubGenre("Heist", 10051),
            SubGenre("Martial Arts", 779),
            SubGenre("Superhero", 9715),
            SubGenre("Spy", 470),
            SubGenre("Disaster", 11165),
        ),
        "Adventure" to listOf(
            SubGenre("Treasure Hunt", 2898),
            SubGenre("Exploration", 14643),
            SubGenre("Isekai", 201712),
            SubGenre("Pirate", 11185),
            SubGenre("Survival", 3560),
            SubGenre("Quest", 132251),
        ),
        "Animation" to listOf(
            SubGenre("Anime", 210024),
            SubGenre("Mecha", 184125),
            SubGenre("3D", 6513),
            SubGenre("Stop Motion", 10121),
            SubGenre("Musical", 180547),
            SubGenre("Adult", 180190),
        ),
        "Comedy" to listOf(
            SubGenre("Romantic", 9799),
            SubGenre("Dark", 6091),
            SubGenre("Parody", 4344),
            SubGenre("Slapstick", 167930),
            SubGenre("Satire", 2340),
            SubGenre("Stand-Up", 174586),
        ),
        "Crime" to listOf(
            SubGenre("Mafia", 10060),
            SubGenre("Heist", 10051),
            SubGenre("Detective", 11002),
            SubGenre("Drug", 6149),
            SubGenre("Prison", 10084),
            SubGenre("Serial Killer", 10714),
        ),
        "Documentary" to listOf(
            SubGenre("Nature", 1283),
            SubGenre("True Crime", 198117),
            SubGenre("Music", 5765),
            SubGenre("Sports", 9013),
            SubGenre("History", 1568),
            SubGenre("Science", 2990),
        ),
        "Drama" to listOf(
            SubGenre("True Story", 818),
            SubGenre("Courtroom", 10891),
            SubGenre("Coming of Age", 10683),
            SubGenre("Period Drama", 9840),
            SubGenre("Family", 10224),
            SubGenre("Political", 1484),
        ),
        "Family" to listOf(
            SubGenre("Animals", 14936),
            SubGenre("Holiday", 207317),
            SubGenre("Musical", 180547),
            SubGenre("Fairy Tale", 2313),
            SubGenre("Adventure", 14643),
            SubGenre("Coming of Age", 10683),
        ),
        "Fantasy" to listOf(
            SubGenre("Epic", 4325),
            SubGenre("Fairy Tale", 2313),
            SubGenre("Mythology", 11451),
            SubGenre("Dark Fantasy", 177895),
            SubGenre("Magic", 2343),
            SubGenre("Dragon", 6054),
        ),
        "History" to listOf(
            SubGenre("Biopic", 818),
            SubGenre("Ancient", 14602),
            SubGenre("Medieval", 12995),
            SubGenre("Royal", 12189),
            SubGenre("Revolution", 235),
            SubGenre("Empire", 4325),
        ),
        "Horror" to listOf(
            SubGenre("Slasher", 12339),
            SubGenre("Supernatural", 6152),
            SubGenre("Zombie", 12377),
            SubGenre("Psychological", 10349),
            SubGenre("Found Footage", 10683),
            SubGenre("Gothic", 162365),
        ),
        "Music" to listOf(
            SubGenre("Musical", 180547),
            SubGenre("Biopic", 818),
            SubGenre("Concert", 174586),
            SubGenre("Rock", 5765),
            SubGenre("Hip Hop", 207317),
            SubGenre("Classical", 310),
        ),
        "Mystery" to listOf(
            SubGenre("Whodunit", 10714),
            SubGenre("Detective", 11002),
            SubGenre("Noir", 229266),
            SubGenre("Psychological", 10349),
            SubGenre("Occult", 7035),
        ),
        "Romance" to listOf(
            SubGenre("Period Romance", 9840),
            SubGenre("Love Triangle", 10749),
            SubGenre("Forbidden Love", 161862),
            SubGenre("Romantic Comedy", 9799),
            SubGenre("Wedding", 10619),
            SubGenre("First Love", 10222),
        ),
        "Science Fiction" to listOf(
            SubGenre("Space", 3801),
            SubGenre("Dystopia", 3133),
            SubGenre("Cyberpunk", 12190),
            SubGenre("Time Travel", 4565),
            SubGenre("Alien", 9951),
            SubGenre("Robot", 9672),
        ),
        "Thriller" to listOf(
            SubGenre("Psychological", 10349),
            SubGenre("Conspiracy", 10092),
            SubGenre("Serial Killer", 10714),
            SubGenre("Survival", 3560),
            SubGenre("Detective", 11002),
            SubGenre("Techno", 6077),
        ),
        "War" to listOf(
            SubGenre("World War II", 12554),
            SubGenre("Vietnam War", 11347),
            SubGenre("Civil War", 14555),
            SubGenre("Anti-War", 11322),
            SubGenre("Military", 6092),
            SubGenre("Naval", 7035),
        ),
        "Western" to listOf(
            SubGenre("Spaghetti", 4862),
            SubGenre("Revisionist", 161176),
            SubGenre("Outlaw", 10292),
            SubGenre("Frontier", 14643),
            SubGenre("Gunfighter", 163083),
        ),
    )
}
