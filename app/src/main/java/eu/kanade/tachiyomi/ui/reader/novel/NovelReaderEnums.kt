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
    CUSTOM(4),
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

/**
 * Typography presets based on mathematical text-size/line-height ratios.
 * - SUPERGOLDEN: 1.618 ratio (golden ratio) — very airy
 * - GOLDEN: 1.33 ratio — balanced
 * - CUSTOM: user-defined values
 */
enum class NovelReaderTypographyPreset {
    CUSTOM,
    SUPERGOLDEN,
    GOLDEN,
}

/**
 * Background texture overlays for the novel reader.
 */
enum class NovelReaderBackgroundTexture {
    NONE,
    PAPER_GRAIN,
    LINEN,
    PARCHMENT,
}

/**
 * Auto-scroll behavior when reaching the end of a chapter.
 */
enum class NovelAutoScrollChapterEndBehavior {
    StopAtEnd,
    AdvanceAndStop,
    ContinuousReading,
}

/**
 * Page transition styles for page-mode reader.
 */
enum class NovelPageTransitionStyle {
    INSTANT,
    SLIDE,
    DEPTH,
    BOOK,
    CURL,
    BOOK_FLIP,
}
