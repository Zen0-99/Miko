package eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook

/**
 * Full-player overlay theme, ported from Retro Music Player's "now playing
 * screen" variants (only the ones that map onto an audiobook player —
 * music-only layouts like Fit/Tiny/Peak/Adaptive are not carried over).
 */
enum class AudiobookPlayerTheme {
    /** Blurred cover backdrop + dark scrim (the original look). */
    BLUR,

    /** Blurred cover backdrop + cover on an elevated card. */
    BLUR_CARD,

    /** Flat backdrop; the cover sits on an elevated card. */
    CARD,

    /** Cover art fills the screen edge-to-edge behind a gradient scrim. */
    FULL,

    /** Cover-accent wash fading into the dark base. */
    COLOR,

    /**
     * Accent wash + softly blurred cover + gradient-to-base — the same
     * recipe the entry-detail header uses.
     */
    GRADIENT,

    /** Flat near-black backdrop, no cover treatment. */
    PLAIN,
}
