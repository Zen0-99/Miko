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
