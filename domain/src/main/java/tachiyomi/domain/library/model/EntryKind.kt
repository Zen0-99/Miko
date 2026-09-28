package tachiyomi.domain.library.model

/**
 * The kind of library entry: manga, anime, or novel.
 * Used by the FailedFetch model and the in-app update progress bus.
 */
enum class EntryKind {
    MANGA,
    SHOWS,
    NOVEL,
    ;

    companion object {
        /** Legacy stored values/exports may carry "ANIME" — alias at the parse boundary. */
        fun fromString(value: String): EntryKind =
            if (value.equals("ANIME", ignoreCase = true)) SHOWS else valueOf(value)
    }
}
