package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * Neural TTS engine backed by sherpa-onnx (Piper/Kokoro/Matcha/ZipVoice models).
 *
 * This engine provides high-quality offline neural TTS with 186+ voices.
 * It requires downloading model files separately (via voice management UI).
 *
 * NOTE: The sherpa-onnx native library dependency must be added to build.gradle
 * to enable actual neural TTS. Until then, this class falls back to reporting
 * not-ready and the caller should fall back to [AndroidTtsEngine].
 *
 * To enable:
 * 1. Add sherpa-onnx AAR dependency to app/build.gradle.kts
 * 2. Download model files (e.g. Piper en_US-amy-medium.onnx + tokens.txt)
 * 3. Set model path via NovelTtsPreferences.neuralModelPath()
 * 4. Set engine type to "neural" via NovelTtsPreferences.engineType()
 *
 * Supported model types:
 * - Piper: fast, good quality, many voices
 * - Kokoro: highest quality, slower
 * - Matcha: multilingual, good quality
 * - ZipVoice: compact, lower quality
 */
class NeuralTtsEngine(
    private val context: Context,
    private val modelPath: String,
    private val modelType: String = "piper",
) : TtsEngine {

    private var ready = false
    private var callback: TtsCallback? = null
    private var currentRate: Float = 1.0f
    private var currentPitch: Float = 1.0f

    // Placeholder for sherpa-onnx OfflineTts instance
    // private var sherpaTts: com.k2fsa.sherpa.onnx.OfflineTts? = null

    override val isReady: Boolean
        get() = ready

    override fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
        val modelDir = File(modelPath)
        if (!modelDir.exists() || !modelDir.isDirectory) {
            onError("Neural TTS model not found at $modelPath. Please download a model first.")
            return
        }

        // Check for required model files based on type
        val modelFile = when (modelType) {
            "piper" -> modelDir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") }
            "kokoro" -> modelDir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") }
            "matcha" -> modelDir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") }
            "zipvoice" -> modelDir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") }
            else -> null
        }
        if (modelFile == null) {
            onError("No .onnx model file found in $modelPath")
            return
        }

        val tokensFile = File(modelDir, "tokens.txt")
        if (!tokensFile.exists()) {
            onError("tokens.txt not found in $modelPath")
            return
        }

        try {
            // TODO: Initialize sherpa-onnx OfflineTts when dependency is added:
            //
            // val config = com.k2fsa.sherpa.onnx.OfflineTtsConfig(
            //     model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(
            //         vits = com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig(
            //             model = modelFile.absolutePath,
            //             tokens = tokensFile.absolutePath,
            //             dataDir = modelDir.absolutePath,
            //         ),
            //     ),
            // )
            // sherpaTts = com.k2fsa.sherpa.onnx.OfflineTts(config)
            // ready = true
            // onReady()

            // Until sherpa-onnx is added, report not available
            onError("Neural TTS (sherpa-onnx) is not yet available. Using Android TTS fallback.")
            logcat(LogPriority.WARN) {
                "NeuralTtsEngine: sherpa-onnx dependency not linked. " +
                    "Model found at $modelPath but cannot initialize."
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Neural TTS init failed" }
            onError("Neural TTS init failed: ${e.message}")
        }
    }

    override fun speak(text: String, flush: Boolean, callback: TtsCallback?) {
        this.callback = callback
        // TODO: Implement when sherpa-onnx is added:
        //
        // val audio = sherpaTts?.generate(text, speed = currentRate)
        // Play audio via AudioTrack
        // callback?.onStart(utteranceId)
        // callback?.onDone(utteranceId)
        logcat(LogPriority.WARN) { "NeuralTtsEngine.speak called but not implemented" }
        callback?.onError("", "Neural TTS not implemented")
    }

    override fun stop() {
        // TODO: Stop AudioTrack playback
    }

    override fun pause() {
        // TODO: Pause AudioTrack
    }

    override fun resume() {
        // TODO: Resume AudioTrack
    }

    override fun setSpeechRate(rate: Float) {
        currentRate = rate
    }

    override fun setPitch(pitch: Float) {
        currentPitch = pitch
    }

    override fun setVoice(voiceName: String) {
        // TODO: Switch model file based on voice name
    }

    override fun setLanguage(languageCode: String) {
        // TODO: Switch to a model matching the language
    }

    override fun getAvailableVoices(): List<TtsVoice> {
        // TODO: Return downloaded voices from model directory
        val modelDir = File(modelPath)
        if (!modelDir.exists()) return emptyList()
        return modelDir.listFiles()
            ?.filter { it.name.endsWith(".onnx") }
            ?.map { file ->
                TtsVoice(
                    name = file.nameWithoutExtension,
                    displayName = file.nameWithoutExtension.replace("_", " "),
                    language = modelType,
                    isNeural = true,
                )
            }
            ?: emptyList()
    }

    override fun shutdown() {
        stop()
        // sherpaTts = null
        ready = false
    }

    companion object {
        /** Known neural TTS voice catalog (186 voices across Piper/Kokoro/Matcha/ZipVoice). */
        val VOICE_CATALOG = listOf(
            "en_US-amy-medium",
            "en_US-amy-low",
            "en_US-arctic-medium",
            "en_US-danny-low",
            "en_US-hfc_male-medium",
            "en_US-kathleen-low",
            "en_US-l2arctic-medium",
            "en_US-libritts-high",
            "en_US-ryan-high",
            "en_US-ryan-medium",
            "en_GB-alba-medium",
            "en_GB-jenny_dioco-medium",
            "en_GB-northern_english_male-medium",
            "en_GB-semaine-medium",
            "zh_CN-huayan-medium",
            "ja_JP-kokoro-medium",
            "ko_KR-kss-medium",
            "es_ES-carlfm-x-low",
            "fr_FR-siwis-medium",
            "de_DE-thorsten-medium",
        )

        /** Piper model download URLs (Hugging Face) */
        const val PIPER_BASE_URL = "https://huggingface.co/rhasspy/piper-voices/resolve/main"
        const val KOKORO_BASE_URL = "https://huggingface.co/hexgrad/Kokoro-82M/resolve/main"
    }
}
