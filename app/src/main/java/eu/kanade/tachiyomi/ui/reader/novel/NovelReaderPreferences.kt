package eu.kanade.tachiyomi.ui.reader.novel

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class NovelReaderPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun textSize() = preferenceStore.getFloat("pref_novel_text_size", 16f)

    fun lineHeight() = preferenceStore.getFloat("pref_novel_line_height", 1.5f)

    fun paragraphSpacing() = preferenceStore.getInt("pref_novel_paragraph_spacing", 16)

    fun textAlignment() = preferenceStore.getEnum("pref_novel_text_alignment", TextAlignment.JUSTIFY)

    fun readingMode() = preferenceStore.getEnum("pref_novel_reading_mode", NovelReadingMode.DEFAULT)

    /** Background color mode (white/black/gray/smart-by-page/smart-by-theme). */
    fun backgroundColorMode() = preferenceStore.getEnum("pref_novel_bg_color_mode", NovelReaderBackgroundColor.GRAY)

    /** Raw ARGB background color used when [backgroundColorMode] is a fixed color. */
    fun backgroundColor() = preferenceStore.getInt("pref_novel_bg_color", 0xFFFFFFFF.toInt())

    fun textColor() = preferenceStore.getInt("pref_novel_text_color", 0xFF000000.toInt())

    /** Screen orientation for the novel reader. */
    fun orientation() = preferenceStore.getEnum("pref_novel_orientation", NovelOrientation.FREE)

    fun keepScreenOn() = preferenceStore.getBoolean("pref_novel_keep_screen_on", true)

    fun showReadingProgress() = preferenceStore.getBoolean("pref_novel_show_progress", true)

    fun bionicReading() = preferenceStore.getBoolean("pref_novel_bionic_reading", false)

    /** Horizontal side padding (in dp) for the reader text. */
    fun sidePadding() = preferenceStore.getInt("pref_novel_side_padding", 16)

    /** Whether the novel reader hides system bars (fullscreen). Defaults to false. */
    fun fullscreen() = preferenceStore.getBoolean("pref_novel_fullscreen", false)

    /** Whether to extend content into display cutout areas when fullscreen. */
    fun cutoutShort() = preferenceStore.getBoolean("pref_novel_cutout_short", true)

    /** Whether to use the novel's cover-derived accent color in the reader UI. */
    fun useCoverAccentColor() = preferenceStore.getBoolean("pref_novel_use_cover_accent", false)

    /** Whether to show in-line phone info (time + battery) at the bottom of the reader. */
    fun inlinePhoneInfo() = preferenceStore.getBoolean("pref_novel_inline_phone_info", false)

    /** Show estimated reading time for the current chapter in the reader chrome. */
    fun showEstimatedReadingTime() = preferenceStore.getBoolean("pref_novel_show_reading_time", true)

    /** Smart-fit margins: automatically adjust side padding based on screen width. */
    fun smartFitMargins() = preferenceStore.getBoolean("pref_novel_smart_fit_margins", false)

    /** Join consecutive chapters without chapter header separators in infinite scroll mode. */
    fun joinChapters() = preferenceStore.getBoolean("pref_novel_join_chapters", false)

    /** E-Ink binarization: force pure black text on white background for E-Ink displays. */
    fun eInkBinarization() = preferenceStore.getBoolean("pref_novel_eink_binarization", false)
}
