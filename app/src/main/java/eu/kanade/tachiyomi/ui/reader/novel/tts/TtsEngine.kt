package eu.kanade.tachiyomi.ui.reader.novel.tts

/**
 * Interface for TTS engines. Implemented by [AndroidTtsEngine] and [NeuralTtsEngine].
 *
 * Engines are responsible for synthesizing text to speech and reporting progress
 * via [TtsCallback]. The caller is responsible for queueing text and managing
 * the reading session lifecycle.
 */
interface TtsEngine {

    /** Whether the engine is initialized and ready to speak. */
    val isReady: Boolean

    /**
     * Whether [speak] with `flush = false` appends to a queue instead of
     * interrupting current playback. When true the caller can enqueue
     * upcoming utterances ahead of time so synthesis overlaps playback;
     * when false each [speak] call plays immediately and callers must
     * chain utterances through [TtsCallback.onDone].
     */
    val supportsQueueing: Boolean

    /**
     * Whether [pause]/[resume] freeze playback in place (queue and position
     * preserved). When false, [pause] degrades to [stop] and the caller must
     * rebuild the queue on resume.
     */
    val supportsPause: Boolean

    /** Initialize the engine. Calls [onReady] when initialization completes. */
    fun initialize(onReady: () -> Unit, onError: (String) -> Unit)

    /**
     * Speak the given [text] under [utteranceId], which is echoed back in
     * [TtsCallback] events. If [flush] is true, clears any pending speech first.
     */
    fun speak(text: String, utteranceId: String, flush: Boolean = true, callback: TtsCallback? = null)

    /**
     * Begin synthesizing [text] in the background so a later [speak] of the
     * same text starts from buffered PCM instead of cold synthesis. Default
     * no-op; engines that can stream (Pocket) override. Called by the
     * controller when a paragraph starts playing, for the next paragraph
     * (D-06).
     */
    fun prefetch(text: String) {}

    /** Stop all speech and clear the queue. */
    fun stop()

    /** Pause current speech (if supported). */
    fun pause()

    /** Resume paused speech (if supported). */
    fun resume()

    /** Set the speech rate (0.5 to 4.0, 1.0 = normal). */
    fun setSpeechRate(rate: Float)

    /** Set the pitch (0.5 to 2.0, 1.0 = normal). */
    fun setPitch(pitch: Float)

    /** Set the voice by name. Use [getAvailableVoices] to discover valid names. */
    fun setVoice(voiceName: String)

    /** Set the language (ISO 639-1 code, e.g. "en"). */
    fun setLanguage(languageCode: String)

    /** Get available voices as (name, displayLabel) pairs. */
    fun getAvailableVoices(): List<TtsVoice>

    /** Release all resources. */
    fun shutdown()
}

/** A TTS voice option. */
data class TtsVoice(
    val name: String,
    val displayName: String,
    val language: String,
    val isNeural: Boolean = false,
)

/** Callback for TTS events. */
interface TtsCallback {
    fun onStart(utteranceId: String) {}
    fun onDone(utteranceId: String) {}
    fun onError(utteranceId: String, errorMessage: String) {}
    fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {}
}
