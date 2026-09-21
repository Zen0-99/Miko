package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.tts.supertonic.VoiceBinReader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Static metadata for the Supertonic-3 engine bundle — the sherpa-onnx
 * `supertonic-3-tts-int8` release running on `onnxruntime-android`
 * (phase-05B D-02/D-03).
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

    /**
     * Sherpa-onnx GitHub release — the int8 tarball, sha256-pinned from the
     * real downloaded bytes (05B-01 staging). `archiveRoot` is the tarball's
     * wrapper dir, stripped during extraction so the on-device layout is
     * `${filesDir}/engines/supertonic-3-int8/<file>`.
     *
     * Single source today (PB-11): the dual-source ordering machinery stays
     * for when a Miko mirror appears — same empty-constant pattern as
     * `PocketEngineCatalog.MIKO_BUNDLE_*`.
     */
    val SHERPA_ARCHIVE = EngineArchiveSource(
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/" +
            "tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2",
        sha256 = "82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427",
        sizeBytes = 128_774_318L,
        archiveRoot = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11/",
    )

    /**
     * Static descriptor for the sherpa int8 bundle. `installedSizeBytes` is
     * the measured extracted size from 05B-01's staging; it is a progress-bar
     * denominator, not an integrity value (PB-12).
     */
    val SUPERTONIC_3 = EngineDescriptor(
        name = ENGINE_NAME,
        displayName = "Supertonic-3 (English, int8)",
        downloadSizeBytes = SHERPA_ARCHIVE.sizeBytes,
        installedSizeBytes = 145_316_356L,
        licenseNotice = "LICENSES/supertonic-3.md",
    )

    /**
     * Ordered download sources (D-03): the sherpa release is the only source
     * today (PB-11).
     */
    fun sources(): List<EngineArchiveSource> = listOf(SHERPA_ARCHIVE)

    /**
     * Supertonic sherpa int8 layout: the 4 `*.int8.onnx` graphs + `tts.json`
     * + `unicode_indexer.bin` + `voice.bin`, with three payload checks:
     *
     *  - `tts.json` must parse and declare `ae.sample_rate == 44100` (V5 —
     *    the sherpa docs table's 24000 claim is known-wrong, R8; a 24000
     *    bundle is Corrupt, not silently mis-pitched).
     *  - `voice.bin` length must equal `48 + n*(ttlD1*ttlD2 + dpD1*dpD2)*4`
     *    per its own int64 dims header (strict parse via [VoiceBinReader]).
     *  - `vector_estimator.int8.onnx` ≥ [MIN_MODEL_BYTES] — guards against a
     *    truncated extraction where headers landed but the body didn't.
     *
     * The fp32 quartet is deliberately NOT accepted here: the installer only
     * ever lays down the int8 bundle — the engine's per-file fp32 fallback
     * exists for adb-staged dirs, not for downloaded installs.
     *
     * Post-extract structural check invoked by [EngineInstaller] after the
     * meta-sha drift check — the catalog owns its own layout knowledge (PB-9).
     */
    internal fun checkLayout(dir: File): InstallState {
        for (name in INT8_ONNX_FILES + INT8_META_FILES) {
            val f = File(dir, name)
            if (!f.isFile || f.length() == 0L) return InstallState.Corrupt
        }
        val sampleRate = runCatching {
            ((TTS_JSON.parseToJsonElement(File(dir, "tts.json").readText()) as? JsonObject)
                ?.get("ae") as? JsonObject)
                ?.get("sample_rate")?.jsonPrimitive?.intOrNull
        }.getOrNull()
        if (sampleRate != SAMPLE_RATE) return InstallState.Corrupt
        // voice.bin: header-declared dims must match the file length exactly —
        // VoiceBinReader.parse throws IOException on any mismatch/truncation.
        runCatching { VoiceBinReader.parse(File(dir, "voice.bin")) }
            .getOrNull() ?: return InstallState.Corrupt
        if (File(dir, "vector_estimator.int8.onnx").length() < MIN_MODEL_BYTES) {
            return InstallState.Corrupt
        }
        return InstallState.Installed
    }

    /**
     * The [EngineSpec] [EngineInstaller] uses to serve this engine —
     * descriptor + ordered sources + the supertonic layout check (PB-9).
     */
    internal fun engineSpec(): EngineSpec = EngineSpec(SUPERTONIC_3, sources(), ::checkLayout)

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

    /**
     * `tts.json` parser for [checkLayout]. `ignoreUnknownKeys` because the
     * real bundle carries more fields than the layout check reads.
     * kotlinx-serialization, not org.json — org.json isn't on the JVM
     * unit-test classpath.
     */
    private val TTS_JSON = Json { ignoreUnknownKeys = true }

    // Sanity floor for the post-install layout check — same truncated-
    // extraction guard as PocketEngineCatalog's MIN_MODEL_BYTES convention.
    private const val MIN_MODEL_BYTES: Long = 1L * 1024L * 1024L
}
