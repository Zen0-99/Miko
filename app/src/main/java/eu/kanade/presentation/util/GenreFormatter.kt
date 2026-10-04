package eu.kanade.presentation.util

/** Words that stay lowercase mid-phrase in title-cased genre labels. */
private val GENRE_MINOR_WORDS = setOf(
    "a", "an", "and", "as", "at", "but", "by", "for", "in",
    "nor", "of", "on", "or", "per", "the", "to", "vs", "via",
)

/**
 * Display form for source/catalog genre tags: `school_life` → "School
 * Life", `slice_of_life` → "Slice of Life". Snake_case tags come from
 * MangaBaka and some extensions; stored values are untouched — this is
 * render-only, so click payloads still carry the raw tag.
 */
fun formatGenreLabel(genre: String): String =
    genre.replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .mapIndexed { index, word ->
            when {
                index > 0 && word.lowercase() in GENRE_MINOR_WORDS -> word.lowercase()
                else -> word.replaceFirstChar { it.titlecase() }
            }
        }
        .joinToString(" ")

/**
 * Genre prevalence order — the most common genres across the TMDB movie/TV
 * catalogues and the manga/novel tag vocabularies, ranked by how prevalent
 * they are in practice. Used to order the entry genre row so the two visible
 * lines lead with the genres a reader is most likely to care about rather
 * than whatever order the source happened to return.
 *
 * Matching is normalized: lowercase, `_`/`-`/`&` treated as separators, so
 * "sci-fi", "sci_fi" and "Sci-Fi & Fantasy" all resolve. Unknown tags keep
 * their relative order after every ranked genre.
 */
private val GENRE_PREVALENCE: Map<String, Int> = listOf(
    "action",
    "adventure",
    "comedy",
    "drama",
    "fantasy",
    "romance",
    "thriller",
    "sci fi",
    "science fiction",
    "mystery",
    "horror",
    "slice of life",
    "supernatural",
    "crime",
    "animation",
    "sports",
    "psychological",
    "family",
    "historical",
    "history",
    "music",
    "school",
    "school life",
    "martial arts",
    "war",
    "war politics",
    "mecha",
    "isekai",
    "documentary",
    "kids",
    "military",
    "parody",
    "demons",
    "vampire",
    "game",
    "space",
    "harem",
    "ecchi",
    "seinen",
    "shounen",
    "shounen ai",
    "shoujo",
    "shoujo ai",
    "josei",
    "boys love",
    "yaoi",
    "girls love",
    "yuri",
    "tv movie",
    "news",
    "reality",
    "soap",
    "talk",
    "western",
    "adult",
    "mature",
).withIndex().associate { (index, genre) -> genre to index }

private fun normalizeGenre(genre: String): String =
    genre.lowercase()
        .replace('_', ' ')
        .replace('-', ' ')
        .replace("&", " ")
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ")

/** Orders [genres] by catalog prevalence; unranked tags go last, stably. */
fun sortGenresByPrevalence(genres: List<String>): List<String> =
    genres.sortedBy { GENRE_PREVALENCE[normalizeGenre(it)] ?: Int.MAX_VALUE }
