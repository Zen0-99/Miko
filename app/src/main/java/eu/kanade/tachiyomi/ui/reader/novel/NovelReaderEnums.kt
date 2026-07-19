package eu.kanade.tachiyomi.ui.reader.novel

enum class TextAlignment(val value: Int) {
    LEFT(0),
    CENTER(1),
    JUSTIFY(2),
    RIGHT(3),
}

enum class NovelReadingMode(val prefValue: Int) {
    DEFAULT(0),
    INFINITE_SCROLL(1),
    OVERSCROLL(3),
}

/**
 * Background color mode for the novel reader. Mirrors Miko's ReaderBackgroundColor.
 *
 * - [WHITE], [GRAY], [BLACK]: fixed colors.
 * - [SMART_THEME]: follows the app's Material theme background color.
 */
enum class NovelReaderBackgroundColor(val prefValue: Int) {
    WHITE(0),
    BLACK(1),
    SMART_THEME(2),
    GRAY(3),
}

/**
 * Screen orientation for the novel reader. Mirrors Miko's NovelOrientationType.
 */
enum class NovelOrientation(val prefValue: Int) {
    FREE(0),
    PORTRAIT(1),
    LANDSCAPE(2),
    LOCKED_PORTRAIT(3),
    LOCKED_LANDSCAPE(4),
}
