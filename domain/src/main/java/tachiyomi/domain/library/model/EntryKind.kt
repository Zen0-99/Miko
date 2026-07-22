package tachiyomi.domain.library.model

/**
 * The kind of library entry: manga, anime, or novel.
 * Used by the FailedFetch model and the in-app update progress bus.
 */
enum class EntryKind { MANGA, ANIME, NOVEL }
