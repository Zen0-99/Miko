package eu.kanade.tachiyomi.ui.reader.novel.tts

/**
 * A single entry in the curated neural TTS voice catalog.
 *
 * Each entry describes a downloadable sherpa-onnx voice bundle (tar.bz2)
 * that can be fetched, verified, and extracted by [NeuralVoiceManager].
 */
data class NeuralVoiceEntry(
    val id: String, // e.g. "en_US-amy-medium"
    val displayName: String, // e.g. "Amy (US English, Medium)"
    val family: String, // "piper", "kokoro", "matcha", "kitten", "zipvoice"
    val language: String, // BCP-47 tag, e.g. "en-US"
    val gender: String, // "M", "F", or ""
    val sampleRateHz: Int, // e.g. 22050
    val approxSizeMb: Int, // approximate download size
    val bundleUrl: String, // tar.bz2 download URL
    val sha256: String? = null, // checksum for verification
    val speakers: Int = 1, // number of speakers
)

/**
 * Curated catalog of sherpa-onnx neural TTS voices available for download.
 *
 * Bundle URLs point to the k2-fsa/sherpa-onnx GitHub releases, which host
 * tar.bz2 archives containing model.onnx + tokens.txt (+ espeak-ng-data for
 * Piper voices). See [NeuralVoiceManager] for download/extraction logic.
 *
 * Source: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
 */
object NeuralTtsVoiceCatalog {

    private const val SHERPA_BASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

    /**
     * The curated voice catalog. A subset of the sherpa-onnx tts-models
     * release covering the most popular languages and qualities.
     */
    val VOICE_CATALOG: List<NeuralVoiceEntry> = listOf(
        // ---- Piper English (US) ----
        NeuralVoiceEntry(
            id = "vits-piper-en_US-amy-low",
            displayName = "Amy (US English, Low)",
            family = "piper",
            language = "en-US",
            gender = "F",
            sampleRateHz = 16000,
            approxSizeMb = 26,
            bundleUrl = "$SHERPA_BASE/vits-piper-en_US-amy-low.tar.bz2",
        ),
        NeuralVoiceEntry(
            id = "vits-piper-en_US-amy-medium",
            displayName = "Amy (US English, Medium)",
            family = "piper",
            language = "en-US",
            gender = "F",
            sampleRateHz = 22050,
            approxSizeMb = 61,
            bundleUrl = "$SHERPA_BASE/vits-piper-en_US-amy-medium.tar.bz2",
        ),
        NeuralVoiceEntry(
            id = "vits-piper-en_US-ryan-high",
            displayName = "Ryan (US English, High)",
            family = "piper",
            language = "en-US",
            gender = "M",
            sampleRateHz = 22050,
            approxSizeMb = 108,
            bundleUrl = "$SHERPA_BASE/vits-piper-en_US-ryan-high.tar.bz2",
        ),

        // ---- Piper English (GB) ----
        NeuralVoiceEntry(
            id = "vits-piper-en_GB-alba-medium",
            displayName = "Alba (UK English, Medium)",
            family = "piper",
            language = "en-GB",
            gender = "F",
            sampleRateHz = 22050,
            approxSizeMb = 61,
            bundleUrl = "$SHERPA_BASE/vits-piper-en_GB-alba-medium.tar.bz2",
        ),
        NeuralVoiceEntry(
            id = "vits-piper-en_GB-semaine-medium",
            displayName = "Semaine (UK English, Medium)",
            family = "piper",
            language = "en-GB",
            gender = "F",
            sampleRateHz = 22050,
            approxSizeMb = 61,
            bundleUrl = "$SHERPA_BASE/vits-piper-en_GB-semaine-medium.tar.bz2",
        ),

        // ---- Piper Chinese ----
        NeuralVoiceEntry(
            id = "vits-piper-zh_CN-huayan-medium",
            displayName = "Huayan (Mandarin Chinese, Medium)",
            family = "piper",
            language = "zh-CN",
            gender = "F",
            sampleRateHz = 22050,
            approxSizeMb = 61,
            bundleUrl = "$SHERPA_BASE/vits-piper-zh_CN-huayan-medium.tar.bz2",
        ),

        // ---- Piper Japanese ----
        NeuralVoiceEntry(
            id = "vits-piper-ja_JP-kokoro-medium",
            displayName = "Kokoro (Japanese, Medium)",
            family = "piper",
            language = "ja-JP",
            gender = "F",
            sampleRateHz = 22050,
            approxSizeMb = 61,
            bundleUrl = "$SHERPA_BASE/vits-piper-ja_JP-kokoro-medium.tar.bz2",
        ),

        // ---- Piper Korean ----
        NeuralVoiceEntry(
            id = "vits-piper-ko_KR-kss-medium",
            displayName = "KSS (Korean, Medium)",
            family = "piper",
            language = "ko-KR",
            gender = "F",
            sampleRateHz = 22050,
            approxSizeMb = 61,
            bundleUrl = "$SHERPA_BASE/vits-piper-ko_KR-kss-medium.tar.bz2",
        ),

        // ---- Piper Spanish ----
        NeuralVoiceEntry(
            id = "vits-piper-es_ES-carlfm-x-low",
            displayName = "Carl FM (Spanish, X-Low)",
            family = "piper",
            language = "es-ES",
            gender = "M",
            sampleRateHz = 16000,
            approxSizeMb = 26,
            bundleUrl = "$SHERPA_BASE/vits-piper-es_ES-carlfm-x-low.tar.bz2",
        ),

        // ---- Piper French ----
        NeuralVoiceEntry(
            id = "vits-piper-fr_FR-siwis-medium",
            displayName = "Siwis (French, Medium)",
            family = "piper",
            language = "fr-FR",
            gender = "F",
            sampleRateHz = 22050,
            approxSizeMb = 61,
            bundleUrl = "$SHERPA_BASE/vits-piper-fr_FR-siwis-medium.tar.bz2",
        ),

        // ---- Piper German ----
        NeuralVoiceEntry(
            id = "vits-piper-de_DE-thorsten-medium",
            displayName = "Thorsten (German, Medium)",
            family = "piper",
            language = "de-DE",
            gender = "M",
            sampleRateHz = 22050,
            approxSizeMb = 61,
            bundleUrl = "$SHERPA_BASE/vits-piper-de_DE-thorsten-medium.tar.bz2",
        ),

        // ---- Kokoro (multilingual / English) ----
        NeuralVoiceEntry(
            id = "kokoro-multi-lang-v1_0",
            displayName = "Kokoro Multilingual v1.0",
            family = "kokoro",
            language = "multi",
            gender = "",
            sampleRateHz = 24000,
            approxSizeMb = 327,
            bundleUrl = "$SHERPA_BASE/kokoro-multi-lang-v1_0.tar.bz2",
        ),
        NeuralVoiceEntry(
            id = "kokoro-en-v0_19",
            displayName = "Kokoro English v0.19",
            family = "kokoro",
            language = "en-US",
            gender = "",
            sampleRateHz = 24000,
            approxSizeMb = 86,
            bundleUrl = "$SHERPA_BASE/kokoro-en-v0_19.tar.bz2",
        ),

        // ---- Matcha ----
        NeuralVoiceEntry(
            id = "matcha-en-us-ryan",
            displayName = "Matcha Ryan (US English)",
            family = "matcha",
            language = "en-US",
            gender = "M",
            sampleRateHz = 22050,
            approxSizeMb = 56,
            bundleUrl = "$SHERPA_BASE/matcha-en-us-ryan.tar.bz2",
        ),

        // ---- Kitten (compact English) ----
        NeuralVoiceEntry(
            id = "kitten-micro-en-v0_8",
            displayName = "Kitten Micro (English v0.8)",
            family = "kitten",
            language = "en-US",
            gender = "",
            sampleRateHz = 24000,
            approxSizeMb = 18,
            bundleUrl = "$SHERPA_BASE/kitten-micro-en-v0_8.tar.bz2",
        ),
    )

    /** Look up a catalog entry by voice id. */
    fun findById(id: String): NeuralVoiceEntry? = VOICE_CATALOG.firstOrNull { it.id == id }

    /** All voice ids in the catalog. */
    fun ids(): List<String> = VOICE_CATALOG.map { it.id }
}
