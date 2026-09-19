package eu.kanade.tachiyomi.ui.reader.novel

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/**
 * Preferences for the novel reader TTS (Text-to-Speech) system.
 *
 * Supports both Android TTS (system) and Pocket TTS (neural) engines.
 */
class NovelTtsPreferences(
    private val preferenceStore: PreferenceStore,
) {
    /** TTS engine type: "android" or "pocket" */
    fun engineType() = preferenceStore.getString("pref_novel_tts_engine", "android")

    /** Speech rate (0.5 to 4.0, default 1.0) */
    fun speechRate() = preferenceStore.getFloat("pref_novel_tts_rate", 1.0f)

    /** Pitch (0.5 to 2.0, default 1.0) */
    fun pitch() = preferenceStore.getFloat("pref_novel_tts_pitch", 1.0f)

    /** Selected voice name (Android TTS voice or Pocket voice ID) */
    fun voiceName() = preferenceStore.getString("pref_novel_tts_voice", "")

    /** Language preference for TTS (ISO 639-1, empty = use system default) */
    fun language() = preferenceStore.getString("pref_novel_tts_language", "")

    /** Whether to continue reading the next paragraph automatically */
    fun autoContinue() = preferenceStore.getBoolean("pref_novel_tts_auto_continue", true)

    /** Whether to show TTS controls in the reader chrome */
    fun showTtsControls() = preferenceStore.getBoolean("pref_novel_tts_show_controls", true)

    // --- TTS behavior preferences (consolidated from NovelReaderPreferences) ---

    /** Whether TTS is enabled in the reader */
    fun enabled() = preferenceStore.getBoolean("pref_novel_tts_enabled", false)

    /** Word highlight mode during TTS playback */
    fun highlightMode() =
        preferenceStore.getEnum("pref_novel_tts_highlight_mode", NovelTtsHighlightMode.AUTO)

    /** Highlight individual spoken words during playback */
    fun wordHighlightEnabled() =
        preferenceStore.getBoolean("pref_novel_tts_word_highlight_enabled", true)

    /** Automatically advance to the next chapter when current one finishes */
    fun autoAdvanceChapter() =
        preferenceStore.getBoolean("pref_novel_tts_auto_advance_chapter", false)

    /** Follow spoken position: keep scroll/page aligned with speech progress */
    fun followAlong() = preferenceStore.getBoolean("pref_novel_tts_follow_along", true)

    /** Pause TTS when the user manually navigates */
    fun pauseOnManualNavigation() =
        preferenceStore.getBoolean("pref_novel_tts_pause_on_manual_navigation", true)

    /** Keep screen on during TTS playback */
    fun keepScreenOnDuringPlayback() =
        preferenceStore.getBoolean("pref_novel_tts_keep_screen_on_during_playback", false)

    /** Prefer translated chapter text for TTS when available */
    fun preferTranslatedText() =
        preferenceStore.getBoolean("pref_novel_tts_prefer_translated_text", false)

    /** Read the chapter title aloud before the chapter content */
    fun readChapterTitle() =
        preferenceStore.getBoolean("pref_novel_tts_read_chapter_title", true)

    /** Whether TTS background playback is enabled */
    fun backgroundPlayback() = preferenceStore.getBoolean("pref_novel_tts_bg_playback", true)

    // Pocket TTS (onnxruntime) specific preferences

    /** Pocket engine intra-op threads: 0 = auto-detect via CPU cluster detection */
    fun pocketIntraOpThreads() = preferenceStore.getInt("pref_novel_tts_pocket_threads", 0)

    /** XNNPACK execution provider mode: "auto" (attempt, CPU EP fallback), "on", "off" */
    fun pocketXnnpackMode() = preferenceStore.getString("pref_novel_tts_pocket_xnnpack", "auto")
}

enum class NovelTtsHighlightMode {
    AUTO,
    EXACT,
    ESTIMATED,
    OFF,
}
