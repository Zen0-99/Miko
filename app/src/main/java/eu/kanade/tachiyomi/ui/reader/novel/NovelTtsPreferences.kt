package eu.kanade.tachiyomi.ui.reader.novel

import tachiyomi.core.common.preference.PreferenceStore

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

    // Neural TTS (sherpa-onnx) specific preferences

    /** Path to the downloaded neural TTS model directory */
    fun neuralModelPath() = preferenceStore.getString("pref_novel_tts_neural_model_path", "")

    /** Neural TTS model type: "piper", "kokoro", "matcha", "zipvoice" */
    fun neuralModelType() = preferenceStore.getString("pref_novel_tts_neural_model_type", "piper")

    /** Number of neural TTS voices downloaded */
    fun neuralVoicesDownloaded() = preferenceStore.getInt("pref_novel_tts_neural_voices_count", 0)

    /** Whether neural TTS background playback is enabled */
    fun backgroundPlayback() = preferenceStore.getBoolean("pref_novel_tts_bg_playback", true)
}
