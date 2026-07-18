package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.Locale

/**
 * TTS engine backed by Android's built-in [TextToSpeech].
 *
 * This is the default engine and works on all Android devices without
 * additional dependencies. Quality depends on the system TTS engine
 * (e.g. Google TTS, Samsung TTS).
 */
class AndroidTtsEngine(
    private val context: Context,
) : TtsEngine {

    private var tts: TextToSpeech? = null
    private var callback: TtsCallback? = null
    private var ready = false
    private var pendingRate: Float = 1.0f
    private var pendingPitch: Float = 1.0f
    private var pendingVoice: String? = null
    private var pendingLanguage: String? = null
    private var utteranceCounter = 0

    override val isReady: Boolean
        get() = ready && tts != null

    override fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(pendingRate)
                tts?.setPitch(pendingPitch)
                pendingVoice?.let { setVoice(it) }
                pendingLanguage?.let { setLanguage(it) }
                setupProgressListener()
                onReady()
            } else {
                logcat(LogPriority.ERROR) { "Android TTS init failed: status=$status" }
                onError("TTS initialization failed (status=$status)")
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                callback?.onStart(utteranceId)
            }

            override fun onDone(utteranceId: String) {
                callback?.onDone(utteranceId)
            }

            override fun onError(utteranceId: String?) {
                callback?.onError(utteranceId ?: "", "TTS error")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?, errorCode: Int) {
                callback?.onError(utteranceId ?: "", "TTS error code=$errorCode")
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                callback?.onRangeStart(utteranceId ?: "", start, end, frame)
            }
        })
    }

    override fun speak(text: String, flush: Boolean, callback: TtsCallback?) {
        this.callback = callback
        val id = "tts_${utteranceCounter++}"
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, mode, null, id)
        } else {
            @Suppress("DEPRECATION")
            val params = hashMapOf(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to id)
            tts?.speak(text, mode, params)
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun pause() {
        // Android TTS doesn't support pause/resume natively
        // We simulate pause by stopping and tracking position
        tts?.stop()
    }

    override fun resume() {
        // Not supported — caller must re-queue text
    }

    override fun setSpeechRate(rate: Float) {
        pendingRate = rate
        if (ready) tts?.setSpeechRate(rate)
    }

    override fun setPitch(pitch: Float) {
        pendingPitch = pitch
        if (ready) tts?.setPitch(pitch)
    }

    override fun setVoice(voiceName: String) {
        pendingVoice = voiceName
        if (!ready) return
        val voices = tts?.voices ?: return
        val match = voices.find { it.name == voiceName }
        if (match != null) {
            tts?.voice = match
        }
    }

    override fun setLanguage(languageCode: String) {
        pendingLanguage = languageCode
        if (ready) {
            tts?.language = Locale(languageCode)
        }
    }

    override fun getAvailableVoices(): List<TtsVoice> {
        if (!ready) return emptyList()
        val voices = tts?.voices ?: return emptyList()
        return voices
            .filter { it.quality != Voice.QUALITY_VERY_LOW }
            .sortedByDescending { it.quality }
            .map { voice ->
                TtsVoice(
                    name = voice.name,
                    displayName = voice.locale.displayName,
                    language = voice.locale.language,
                    isNeural = false,
                )
            }
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
