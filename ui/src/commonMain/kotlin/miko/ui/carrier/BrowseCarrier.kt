package miko.ui.carrier

/**
 * The three content carriers the shared UI switches between — mirrors
 * Android's `ContentMode` (shows/manga/books). `kind` is the normalized
 * JS-plugin kind string stored on `novel_plugins.kind`.
 */
enum class BrowseCarrier(val kind: String) {
    SHOWS("anime"),
    MANGA("manga"),
    BOOKS("novel"),
    ;

    companion object {
        fun forKind(kind: String): BrowseCarrier = when (kind) {
            "anime" -> SHOWS
            "manga" -> MANGA
            else -> BOOKS
        }
    }
}
