package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Controller that manages TTS engine lifecycle, text queuing, and reading session.
 *
 * Supports both Android TTS and neural TTS (sherpa-onnx) engines.
 * Handles paragraph-by-paragraph reading with auto-continue.
 */
class TtsController(
    private val context: Context,
    private val preferences: eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences,
) {

    private var engine: TtsEngine? = null
    private var paragraphs: List<String> = emptyList()
    private var currentIndex: Int = 0
    private var isPlaying: Boolean = false
    private var onParagraphChanged: ((Int) -> Unit)? = null
    private var onComplete: (() -> Unit)? = null

    val isSpeaking: Boolean
        get() = isPlaying

    val currentParagraphIndex: Int
        get() = currentIndex

    /**
     * Create and initialize the appropriate TTS engine based on preferences.
     */
    fun initializeEngine(
        onReady: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val engineType = preferences.engineType().get()
        val engineImpl: TtsEngine = if (engineType == "neural") {
            val modelPath = preferences.neuralModelPath().get()
            val modelType = preferences.neuralModelType().get()
            NeuralTtsEngine(
                context = context,
                modelPath = modelPath,
                modelType = modelType,
                useNnapi = preferences.neuralUseNnapi().get(),
                numThreads = preferences.neuralNumThreads().get(),
                maxNumSentences = preferences.neuralMaxSentences().get(),
                speakerId = preferences.neuralSpeakerId().get(),
                lengthScale = preferences.neuralLengthScale().get(),
                noiseScale = preferences.neuralNoiseScale().get(),
                noiseScaleW = preferences.neuralNoiseScaleW().get(),
            )
        } else {
            AndroidTtsEngine(context)
        }

        engineImpl.setSpeechRate(preferences.speechRate().get())
        engineImpl.setPitch(preferences.pitch().get())

        val voice = preferences.voiceName().get()
        if (voice.isNotEmpty()) {
            engineImpl.setVoice(voice)
        }

        val lang = preferences.language().get()
        if (lang.isNotEmpty()) {
            engineImpl.setLanguage(lang)
        }

        engineImpl.initialize(
            onReady = {
                engine = engineImpl
                onReady()
            },
            onError = { error ->
                // Fall back to Android TTS if neural fails
                if (engineType == "neural") {
                    logcat(LogPriority.WARN) { "Neural TTS failed: $error — falling back to Android TTS" }
                    val fallback = AndroidTtsEngine(context)
                    fallback.setSpeechRate(preferences.speechRate().get())
                    fallback.setPitch(preferences.pitch().get())
                    fallback.initialize(
                        onReady = {
                            engine = fallback
                            onReady()
                        },
                        onError = { fallbackError ->
                            onError(fallbackError)
                        },
                    )
                } else {
                    onError(error)
                }
            },
        )
    }

    /**
     * Start reading a list of paragraphs from [startIndex].
     */
    fun startReading(
        paragraphs: List<String>,
        startIndex: Int = 0,
        onParagraphChanged: (Int) -> Unit,
        onComplete: () -> Unit,
    ) {
        this.paragraphs = paragraphs
        this.currentIndex = startIndex
        this.onParagraphChanged = onParagraphChanged
        this.onComplete = onComplete
        isPlaying = true
        speakCurrent()
    }

    private fun speakCurrent() {
        if (currentIndex >= paragraphs.size) {
            isPlaying = false
            onComplete?.invoke()
            return
        }

        val text = paragraphs[currentIndex]
        engine?.speak(
            text = text,
            flush = true,
            callback = object : TtsCallback {
                override fun onDone(utteranceId: String) {
                    if (isPlaying && preferences.autoContinue().get()) {
                        currentIndex++
                        onParagraphChanged?.invoke(currentIndex)
                        speakCurrent()
                    }
                }

                override fun onError(utteranceId: String, errorMessage: String) {
                    logcat(LogPriority.ERROR) { "TTS error: $errorMessage" }
                    isPlaying = false
                }
            },
        )
    }

    fun pause() {
        isPlaying = false
        engine?.pause()
    }

    fun resume() {
        if (!isPlaying && currentIndex < paragraphs.size) {
            isPlaying = true
            speakCurrent()
        }
    }

    fun next() {
        if (currentIndex < paragraphs.size - 1) {
            currentIndex++
            onParagraphChanged?.invoke(currentIndex)
            if (isPlaying) speakCurrent()
        }
    }

    fun previous() {
        if (currentIndex > 0) {
            currentIndex--
            onParagraphChanged?.invoke(currentIndex)
            if (isPlaying) speakCurrent()
        }
    }

    fun stop() {
        isPlaying = false
        engine?.stop()
    }

    fun setSpeechRate(rate: Float) {
        preferences.speechRate().set(rate)
        engine?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        preferences.pitch().set(pitch)
        engine?.setPitch(pitch)
    }

    fun getAvailableVoices(): List<TtsVoice> {
        return engine?.getAvailableVoices() ?: emptyList()
    }

    fun setVoice(voiceName: String) {
        preferences.voiceName().set(voiceName)
        engine?.setVoice(voiceName)
    }

    fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
    }
}
