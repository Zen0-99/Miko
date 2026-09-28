package eu.kanade.tachiyomi.metadata.tmdb

/**
 * Genre-name → TMDB keyword-ID map backing the Discovery sheet's
 * sub-genre chips. Selected chips feed `with_keywords` /
 * `without_keywords` on the discover endpoints. Keyword IDs come from
 * TMDB's keyword API; the table is curated (sub-genres are an
 * editorial grouping, not a TMDB concept).
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
