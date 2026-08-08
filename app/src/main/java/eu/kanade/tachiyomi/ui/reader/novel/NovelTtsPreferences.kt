package eu.kanade.tachiyomi.ui.reader.novel

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/**
 * Preferences for the novel reader TTS (Text-to-Speech) system.
 *
 * Supports both Android TTS and neural TTS (sherpa-onnx) engines.
 */
class NovelTtsPreferences(
    private val preferenceStore: PreferenceStore,
) {
    /** TTS engine type: "android" or "neural" */
    fun engineType() = preferenceStore.getString("pref_novel_tts_engine", "android")

    /** Speech rate (0.5 to 4.0, default 1.0) */
    fun speechRate() = preferenceStore.getFloat("pref_novel_tts_rate", 1.0f)

    /** Pitch (0.5 to 2.0, default 1.0) */
    fun pitch() = preferenceStore.getFloat("pref_novel_tts_pitch", 1.0f)

    /** Selected voice name (Android TTS voice or neural voice ID) */
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

    // Neural TTS (sherpa-onnx) specific preferences

    /** Path to the downloaded neural TTS model directory */
    fun neuralModelPath() = preferenceStore.getString("pref_novel_tts_neural_model_path", "")

    /** Neural TTS model type: "piper", "kokoro", "matcha", "zipvoice" */
    fun neuralModelType() = preferenceStore.getString("pref_novel_tts_neural_model_type", "piper")

    /** Number of neural TTS voices downloaded */
    fun neuralVoicesDownloaded() = preferenceStore.getInt("pref_novel_tts_neural_voices_count", 0)

    /** Whether neural TTS background playback is enabled */
    fun backgroundPlayback() = preferenceStore.getBoolean("pref_novel_tts_bg_playback", true)

    /** Use NNAPI hardware acceleration for neural TTS (default false) */
    fun neuralUseNnapi() = preferenceStore.getBoolean("pref_novel_tts_neural_nnapi", false)

    /** Number of threads for neural TTS synthesis (default 2) */
    fun neuralNumThreads() = preferenceStore.getInt("pref_novel_tts_neural_threads", 2)

    /** Max sentences per synthesis call (default 2) */
    fun neuralMaxSentences() = preferenceStore.getInt("pref_novel_tts_neural_max_sentences", 2)

    /** Speaker ID for multi-speaker models (default 0) */
    fun neuralSpeakerId() = preferenceStore.getInt("pref_novel_tts_neural_speaker_id", 0)

    /** Length scale for VITS/Piper models (default 1.0) */
    fun neuralLengthScale() = preferenceStore.getFloat("pref_novel_tts_neural_length_scale", 1.0f)

    /** Noise scale for VITS/Piper models (default 0.667) */
    fun neuralNoiseScale() = preferenceStore.getFloat("pref_novel_tts_neural_noise_scale", 0.667f)

    /** Noise scale W for VITS/Piper models (default 0.8) */
    fun neuralNoiseScaleW() = preferenceStore.getFloat("pref_novel_tts_neural_noise_scale_w", 0.8f)

    /** Silence inserted between sentences in neural TTS, in milliseconds (default 0 = no pause) */
    fun neuralSentencePauseMs() = preferenceStore.getInt("pref_novel_tts_neural_sentence_pause_ms", 0)
}

enum class NovelTtsHighlightMode {
    AUTO,
    EXACT,
    ESTIMATED,
    OFF,
}
