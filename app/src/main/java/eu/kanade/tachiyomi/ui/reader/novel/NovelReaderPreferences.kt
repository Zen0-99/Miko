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
    fun showEstimatedReadingTime() = preferenceStore.getBoolean("pref_novel_show_reading_time", false)

    /** Smart-fit margins: automatically adjust side padding based on screen width. */
    fun smartFitMargins() = preferenceStore.getBoolean("pref_novel_smart_fit_margins", false)

    /** Join consecutive chapters without chapter header separators in infinite scroll mode. */
    fun joinChapters() = preferenceStore.getBoolean("pref_novel_join_chapters", false)

    /** E-Ink binarization: force pure black text on white background for E-Ink displays. */
    fun eInkBinarization() = preferenceStore.getBoolean("pref_novel_eink_binarization", false)

    // --- Tier 3: Additive preferences ported from Tadami (typography, textures, auto-scroll, etc.) ---

    /** Typography preset: applies mathematical text-size/line-height ratios. */
    fun typographyPreset() = preferenceStore.getEnum("pref_novel_typography_preset", NovelReaderTypographyPreset.CUSTOM)

    /** Force paragraph indent at the start of each paragraph. */
    fun forceParagraphIndent() = preferenceStore.getBoolean("pref_novel_force_paragraph_indent", false)

    /** Preserve source text alignment in native renderer. */
    fun preserveSourceTextAlignInNative() = preferenceStore.getBoolean("pref_novel_preserve_source_align", true)

    /** Custom font family for the reader (empty = system default). */
    fun customFontFamily() = preferenceStore.getString("pref_novel_font_family", "")

    /** Force bold text rendering. */
    fun forceBoldText() = preferenceStore.getBoolean("pref_novel_force_bold", false)

    /** Force italic text rendering. */
    fun forceItalicText() = preferenceStore.getBoolean("pref_novel_force_italic", false)

    /** Text shadow: enables drop shadow behind text for readability in bright environments. */
    fun textShadowEnabled() = preferenceStore.getBoolean("pref_novel_text_shadow", false)

    /** Text shadow color (ARGB hex string, empty = auto from theme). */
    fun textShadowColor() = preferenceStore.getString("pref_novel_text_shadow_color", "")

    /** Text shadow blur radius. */
    fun textShadowBlur() = preferenceStore.getFloat("pref_novel_text_shadow_blur", 4f)

    /** Text shadow X offset. */
    fun textShadowX() = preferenceStore.getFloat("pref_novel_text_shadow_x", 0f)

    /** Text shadow Y offset. */
    fun textShadowY() = preferenceStore.getFloat("pref_novel_text_shadow_y", 1f)

    /** Page edge shadow: adds a subtle shadow at page edges for depth. */
    fun pageEdgeShadowEnabled() = preferenceStore.getBoolean("pref_novel_page_edge_shadow", false)

    /** Page edge shadow alpha (0.0–1.0). */
    fun pageEdgeShadowAlpha() = preferenceStore.getFloat("pref_novel_page_edge_shadow_alpha", 0.25f)

    /** Background texture overlay (paper grain, linen, parchment). */
    fun backgroundTexture() = preferenceStore.getEnum("pref_novel_bg_texture", NovelReaderBackgroundTexture.NONE)

    /** Native texture strength (0–100 percent). */
    fun nativeTextureStrength() = preferenceStore.getInt("pref_novel_texture_strength", 50)

    /** OLED edge gradient: adds a subtle dark gradient at screen edges for OLED displays. */
    fun oledEdgeGradient() = preferenceStore.getBoolean("pref_novel_oled_edge_gradient", false)

    /** Auto-scroll: enables automatic scrolling of the reader content. */
    fun autoScroll() = preferenceStore.getBoolean("pref_novel_auto_scroll", false)

    /** Smooth auto-scroll: uses continuous pixel-by-pixel scrolling instead of stepped intervals. */
    fun smoothAutoScroll() = preferenceStore.getBoolean("pref_novel_smooth_auto_scroll", false)

    /** Smooth auto-scroll speed (pixels per second). Range 1-200. Lower = slower. */
    fun smoothAutoScrollSpeed() = preferenceStore.getInt("pref_novel_smooth_auto_scroll_speed", 30)

    /** Auto-scroll interval (milliseconds between scroll steps). */
    fun autoScrollInterval() = preferenceStore.getInt("pref_novel_auto_scroll_interval", 3000)

    /** Auto-scroll offset (pixels per scroll step). */
    fun autoScrollOffset() = preferenceStore.getInt("pref_novel_auto_scroll_offset", 60)

    /** Auto-scroll adaptive delay: adjusts scroll speed based on content density. */
    fun autoScrollAdaptiveDelay() = preferenceStore.getBoolean("pref_novel_auto_scroll_adaptive", true)

    /** Auto-scroll chapter end behavior. */
    fun autoScrollChapterEndBehavior() = preferenceStore.getEnum("pref_novel_auto_scroll_end_behavior", NovelAutoScrollChapterEndBehavior.StopAtEnd)

    /** Show auto-scroll floating button for quick toggle. */
    fun showAutoScrollFloatingButton() = preferenceStore.getBoolean("pref_novel_auto_scroll_fab", false)

    /** Prefetch next chapter content for smoother reading. */
    fun prefetchNextChapter() = preferenceStore.getBoolean("pref_novel_prefetch_next", false)

    /** Show scroll percentage in the reader chrome. */
    fun showScrollPercentage() = preferenceStore.getBoolean("pref_novel_show_scroll_pct", true)

    /** Show battery and time in the reader chrome. */
    fun showBatteryAndTime() = preferenceStore.getBoolean("pref_novel_show_battery_time", false)

    /** Show time-to-end estimate (based on reading speed). */
    fun showTimeToEnd() = preferenceStore.getBoolean("pref_novel_show_time_to_end", false)

    /** Show word count for the current chapter. */
    fun showWordCount() = preferenceStore.getBoolean("pref_novel_show_word_count", false)

    /** Text selection enabled in the reader. */
    fun textSelectionEnabled() = preferenceStore.getBoolean("pref_novel_text_selection", false)

    /** Selected text translation: enables translation of highlighted text. */
    fun selectedTextTranslationEnabled() = preferenceStore.getBoolean("pref_novel_sel_text_translation", false)

    /** Selected text translation target language code. */
    fun selectedTextTranslationTargetLang() = preferenceStore.getString("pref_novel_sel_text_translation_lang", "en")

    /** Novel dictionary: enables Wiktionary lookup for highlighted words. */
    fun novelDictionaryEnabled() = preferenceStore.getBoolean("pref_novel_dictionary", false)

    /** Novel dictionary target language. */
    fun novelDictionaryTargetLang() = preferenceStore.getString("pref_novel_dictionary_lang", "en")

    /** Page transition style for page-mode reader. */
    fun pageTransitionStyle() = preferenceStore.getEnum("pref_novel_page_transition", NovelPageTransitionStyle.SLIDE)

    /** Swipe to next chapter gesture. */
    fun swipeToNextChapter() = preferenceStore.getBoolean("pref_novel_swipe_next_chapter", false)

    /** Swipe to previous chapter gesture. */
    fun swipeToPrevChapter() = preferenceStore.getBoolean("pref_novel_swipe_prev_chapter", false)

    /** Tap to scroll: tap left/right half of screen to scroll. */
    fun tapToScroll() = preferenceStore.getBoolean("pref_novel_tap_scroll", false)

    /** Vertical seekbar: display the seekbar vertically on the side. */
    fun verticalSeekbar() = preferenceStore.getBoolean("pref_novel_vertical_seekbar", false)

    /** Custom CSS injected into the reader WebView. */
    fun customCSS() = preferenceStore.getString("pref_novel_custom_css", "")

    /** Custom JS injected into the reader WebView. */
    fun customJS() = preferenceStore.getString("pref_novel_custom_js", "")

    // --- TTS (text-to-speech) ---

    /** Enable text-to-speech for novel chapters. */
    fun ttsEnabled() = preferenceStore.getBoolean("novel_reader_tts_enabled", false)

    /** TTS speech rate (0.5–2.0, where 1.0 is normal speed). */
    fun ttsSpeechRate() = preferenceStore.getFloat("novel_reader_tts_speech_rate", 1f)

    /** TTS pitch (0.5–2.0, where 1.0 is normal pitch). */
    fun ttsPitch() = preferenceStore.getFloat("novel_reader_tts_pitch", 1f)

    /** Word highlight mode during TTS playback. */
    fun ttsHighlightMode() =
        preferenceStore.getEnum("novel_reader_tts_highlight_mode", NovelTtsHighlightMode.AUTO)

    /** Highlight individual spoken words during playback. */
    fun ttsWordHighlightEnabled() =
        preferenceStore.getBoolean("novel_reader_tts_word_highlight_enabled", true)

    /** Automatically advance to the next chapter when current one finishes. */
    fun ttsAutoAdvanceChapter() =
        preferenceStore.getBoolean("novel_reader_tts_auto_advance_chapter", false)

    /** Follow spoken position: keep scroll/page aligned with speech progress. */
    fun ttsFollowAlong() = preferenceStore.getBoolean("novel_reader_tts_follow_along", true)

    /** Pause TTS when the user manually navigates. */
    fun ttsPauseOnManualNavigation() =
        preferenceStore.getBoolean("novel_reader_tts_pause_on_manual_navigation", true)

    /** Keep screen on during TTS playback. */
    fun ttsKeepScreenOnDuringPlayback() =
        preferenceStore.getBoolean("novel_reader_tts_keep_screen_on_during_playback", false)

    /** Prefer translated chapter text for TTS when available. */
    fun ttsPreferTranslatedText() =
        preferenceStore.getBoolean("novel_reader_tts_prefer_translated_text", false)

    /** Read the chapter title aloud before the chapter content. */
    fun ttsReadChapterTitle() =
        preferenceStore.getBoolean("novel_reader_tts_read_chapter_title", true)
}

enum class NovelTtsHighlightMode {
    AUTO,
    EXACT,
    ESTIMATED,
    OFF,
}
