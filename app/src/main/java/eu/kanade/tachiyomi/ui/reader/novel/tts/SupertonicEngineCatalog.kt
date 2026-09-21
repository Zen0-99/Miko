package eu.kanade.tachiyomi.ui.reader.novel.tts

import java.io.File

/**
 * Static metadata for the Supertonic-3 engine bundle — the sherpa-onnx
 * `supertonic-3-tts-int8` release running on `onnxruntime-android`
 * (phase-05B D-02/D-03). No download sources yet — 05B-02 adds the
 * archive pin and installer wiring; until then the bundle lands via
 * `adb push` exactly like spike 002.
 *
 * Two on-disk layouts are recognized (PB-1, per-file int8-first):
 *
 *  - **int8 (sherpa release):** `*.int8.onnx` ×4 + `tts.json` +
 *    `unicode_indexer.bin` + `voice.bin`
 *  - **fp32 (HF-style):** `*.onnx` ×4 + `tts.json` +
 *    `unicode_indexer.json` + `voice_styles/<name>.json`
 */
object SupertonicEngineCatalog {

    const val ENGINE_NAME = "supertonic-3-int8"

    /** Nominal output rate; the loaded `tts.json` `ae.sample_rate` wins at runtime. */
    const val SAMPLE_RATE = 44100

    val INT8_ONNX_FILES = listOf(
        "duration_predictor.int8.onnx",
        "text_encoder.int8.onnx",
        "vector_estimator.int8.onnx",
        "vocoder.int8.onnx",
    )

    val FP32_ONNX_FILES = listOf(
        "duration_predictor.onnx",
        "text_encoder.onnx",
        "vector_estimator.onnx",
        "vocoder.onnx",
    )

    /** Metadata files for the sherpa int8 layout. */
    val INT8_META_FILES = listOf(
        "tts.json",
        "unicode_indexer.bin",
        "voice.bin",
    )

    /** Metadata files for the HF fp32 layout (plus a `voice_styles/` dir). */
    val FP32_META_FILES = listOf(
        "tts.json",
        "unicode_indexer.json",
    )

    /** Install directory: `${filesDir}/engines/supertonic-3-int8/`. */
    fun engineDir(filesDir: File): File = File(filesDir, "engines/$ENGINE_NAME")

    /**
     * Cheap synchronous install gate. Int8 layout checked first, then the
     * fp32 fallback layout (PB-1). A `voice_styles/` dir with at least one
     * `.json` satisfies the fp32 voice requirement.
     */
    fun isInstalled(filesDir: File): Boolean {
        val dir = engineDir(filesDir)
        if (!dir.isDirectory) return false
        if (INT8_ONNX_FILES.all { File(dir, it).isFile } &&
            INT8_META_FILES.all { File(dir, it).isFile }
        ) {
            return true
        }
        if (!FP32_ONNX_FILES.all { File(dir, it).isFile }) return false
        if (!FP32_META_FILES.all { File(dir, it).isFile }) return false
        val stylesDir = File(dir, "voice_styles")
        return stylesDir.isDirectory &&
            (stylesDir.listFiles()?.any { it.isFile && it.extension == "json" } == true)
    }
}
