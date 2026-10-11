package miko.ui.reader

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

/** Mirror of app's NovelReaderEnums — same names so getEnum keys converge. */
enum class TextAlignment(val titleRes: StringResource) {
    LEFT(MR.strings.alignment_left),
    CENTER(MR.strings.alignment_center),
    JUSTIFY(MR.strings.alignment_justify),
    RIGHT(MR.strings.alignment_right),
}

/** Fixed reader backgrounds + SMART_THEME (Material background). */
enum class NovelReaderBackgroundColor(val titleRes: StringResource) {
    WHITE(MR.strings.white_background),
    BLACK(MR.strings.black_background),
    SMART_THEME(MR.strings.theme_system),
    GRAY(MR.strings.gray_background),
}

/**
 * Mirror of Android's `TappingInvertMode` — same names so the shared
 * `reader_tapping_inverted` pref round-trips with the Android app's value.
 */
enum class TappingInvertMode(
    val titleRes: StringResource,
    val shouldInvertHorizontal: Boolean = false,
    val shouldInvertVertical: Boolean = false,
) {
    NONE(MR.strings.tapping_inverted_none),
    HORIZONTAL(MR.strings.tapping_inverted_horizontal, shouldInvertHorizontal = true),
    VERTICAL(MR.strings.tapping_inverted_vertical, shouldInvertVertical = true),
    BOTH(
        MR.strings.tapping_inverted_both,
        shouldInvertHorizontal = true,
        shouldInvertVertical = true,
    ),
}

/** Mirror of Android's FlashColor — same names for getEnum round-trip. */
enum class ReaderFlashColor(val titleRes: StringResource) {
    BLACK(MR.strings.pref_flash_style_black),
    WHITE(MR.strings.pref_flash_style_white),
    WHITE_BLACK(MR.strings.pref_flash_style_white_black),
}

/** Chapter row for the in-reader chapter sheet — remote name + local done. */
data class ChapterRow(
    val url: String,
    val name: String,
    val number: Double,
    val done: Boolean,
)

/**
 * Adjacent chapter for Android's transition pages — neighbor name and
 * scanlator plus the `calculateChapterGap` missing-item count (0 = none).
 */
data class NeighborChapter(
    val name: String,
    val scanlator: String?,
    val gap: Int,
)

/**
 * Mirror of Android's `ReadingMode` — same flagValues so a manga's
 * `viewerFlags` low three bits carry identical semantics across platforms.
 * DEFAULT resolves through `pref_default_reading_mode_key`.
 */
enum class MangaReadingMode(val flagValue: Int, val titleRes: StringResource) {
    DEFAULT(0x00000000, MR.strings.label_default),
    LEFT_TO_RIGHT(0x00000001, MR.strings.left_to_right_viewer),
    RIGHT_TO_LEFT(0x00000002, MR.strings.right_to_left_viewer),
    VERTICAL(0x00000003, MR.strings.vertical_viewer),
    WEBTOON(0x00000004, MR.strings.webtoon_viewer),
    CONTINUOUS_VERTICAL(0x00000005, MR.strings.vertical_plus_viewer),
    ;

    val isPager: Boolean
        get() = this == LEFT_TO_RIGHT || this == RIGHT_TO_LEFT || this == VERTICAL

    companion object {
        const val MASK = 0x00000007
        fun fromFlag(flag: Int): MangaReadingMode =
            entries.firstOrNull { it.flagValue == flag } ?: DEFAULT
    }
}
