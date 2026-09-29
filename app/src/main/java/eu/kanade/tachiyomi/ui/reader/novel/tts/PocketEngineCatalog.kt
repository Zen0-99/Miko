package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.PocketBundle
import java.io.File

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   EngineInstaller.install()  (plan 04-03; engine-generic since 05B-02)
//     │
//     ├── PocketEngineCatalog.sources() ──► ordered List<EngineArchiveSource>
//     │       (Miko-hosted primary first once populated, Marmalade v21
//     │        fallback always present — each carries its own sha256 pin)
//     │
//     ▼
//   HTTP GET → sha256 check → tar.bz2 extract → atomic rename into
//   ${filesDir}/engines/pocket-tts-en-v2026_04/
// -----------------------------------------------------------------------------

/**
 * One downloadable archive that makes up the Pocket engine bundle.
 *
 * The installer tries sources in order; each source is independently
 * sha256-pinned so integrity does not depend on which host served the bytes
 * (phase-04 decision D-03).
 *
 * @property url Absolute HTTPS URL to a tar.bz2 archive whose top-level
 *               directory ([archiveRoot]) contains the engine payload. The
 *               installer strips that wrapper dir so the on-device layout is
 *               `${filesDir}/engines/<name>/<file>` regardless of the
 *               archive's wrapper-dir name.
 * @property sha256 Hex-encoded SHA-256 of the archive bytes, lower case.
 * @property sizeBytes Expected archive size in bytes (for progress UI).
 * @property archiveRoot Wrapper directory inside the archive to strip during
 *               extraction, with trailing `/` (`"pocket-tts-en/"` for this
 *               bundle).
 */
data class EngineArchiveSource(
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val archiveRoot: String = "pocket-tts-en/",
) {
    init {
        require(url.startsWith("https://")) { "archive url must be https: $url" }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "sha256 must be 64 lowercase hex" }
        require(sizeBytes > 0L) { "sizeBytes must be positive" }
    }
}

/**
 * Static metadata for an engine bundle — the single source of truth
 * for "what does the user have to download to use this engine". No I/O.
 * Engine-generic since 05B-02 (was `PocketEngineDescriptor`); each engine's
 * catalog object owns one instance.
 *
 * @property name Engine identifier; also the install directory under
 *                `${filesDir}/engines/`.
 * @property displayName User-facing label.
 * @property downloadSizeBytes Compressed archive size on the wire. Tracks the
 *                fallback archive until the Miko mirror is published.
 * @property installedSizeBytes Approximate on-disk size after extraction.
 * @property licenseNotice Repo-relative path to the long-form license notice.
 */
data class EngineDescriptor(
    val name: String,
    val displayName: String,
    val downloadSizeBytes: Long,
    val installedSizeBytes: Long,
    val licenseNotice: String,
)

/**
 * Catalog entry for the Pocket TTS English v2026_04 bundle
 * (`pocket-tts-en-v2026_04-mixed`), Kyutai Labs' Latent Space Diffusion model
 * running on `onnxruntime-android` directly.
 *
 * Dual-source per D-03: the Miko-hosted UK-expanded bundle
 * (`pocket-tts-en-v2026_04-mixed-uk.tar.bz2`, built by
 * `tools/build-pocket-bundle.py`) is primary once published; Marmalade's
 * `marmalade-tts-android-engines` release v21 is the always-present fallback.
 * `MIKO_BUNDLE_*` constants ship empty/zero until the mirror exists — an
 * empty [MIKO_BUNDLE_SHA256] means "primary not yet live" and [sources]
 * returns the fallback alone, so installs work today.
 *
 * Selective quantization per upstream PR #147: only the `flow_lm_main`
 * transformer is int8; mimi encoder/decoder, flow head and text conditioner
 * stay fp32. The 6 bundled voices are commercial-safe (CC-BY-4.0 / CC0 — see
 * [PocketVoiceCatalog]).
 */
object PocketEngineCatalog {

    const val ENGINE_NAME = "pocket-tts-en-v2026_04"

    /**
     * Miko-hosted UK-expanded bundle (built by tools/build-pocket-bundle.py,
     * published under the miko-engines GitHub release). Empty sha256 = the
     * mirror is not live yet; leave these constants empty/zero until the
     * emitted sha256 + size are pasted in.
     */
    const val MIKO_BUNDLE_URL =
        "https://github.com/Zen0-99/miko-engines/releases/download/" +
            "pocket-tts-en-v2026_04-uk1/pocket-tts-en-v2026_04-mixed-uk.tar.bz2"
    const val MIKO_BUNDLE_SHA256 = ""
    const val MIKO_BUNDLE_SIZE_BYTES = 0L

    /** Marmalade engines-repo v21 release — the effective source today. */
    val FALLBACK_ARCHIVE = EngineArchiveSource(
        url = "https://github.com/maxwhipw/marmalade-tts-android-engines/releases/download/" +
            "v21/pocket-tts-en-v2026_04-mixed.tar.bz2",
        sha256 = "3a3ed2810afbae1f963f4f4e3ebde767c2d3c7c622cc544f23170f0ca755b452",
        sizeBytes = 97_291_178L,
        archiveRoot = "pocket-tts-en/",
    )

    val POCKET_TTS_EN = EngineDescriptor(
        name = ENGINE_NAME,
        displayName = "Pocket TTS (English, 2026-04)",
        downloadSizeBytes = FALLBACK_ARCHIVE.sizeBytes,
        installedSizeBytes = 217_288_756L,
        licenseNotice = "LICENSES/pocket-tts.md",
    )

    /**
     * The Miko primary source, or null while [MIKO_BUNDLE_SHA256] is empty
     * (mirror not yet published). Non-null only when all three MIKO_BUNDLE_*
     * constants are populated, so a half-filled pin can never produce a
     * source with an unverifiable payload.
     */
    val primarySource: EngineArchiveSource? =
        if (
            MIKO_BUNDLE_URL.isNotBlank() &&
            MIKO_BUNDLE_SHA256.isNotBlank() &&
            MIKO_BUNDLE_SIZE_BYTES > 0L
        ) {
            EngineArchiveSource(
                url = MIKO_BUNDLE_URL,
                sha256 = MIKO_BUNDLE_SHA256,
                sizeBytes = MIKO_BUNDLE_SIZE_BYTES,
            )
        } else {
            null
        }

    /**
     * Ordered download sources (D-03): populated primary first, fallback
     * always last and never absent. [primary] defaults to [primarySource];
     * the parameter exists so the ordering is directly testable.
     */
    fun sources(primary: EngineArchiveSource? = primarySource): List<EngineArchiveSource> =
        listOfNotNull(primary, FALLBACK_ARCHIVE)

    /** Install directory: `${filesDir}/engines/pocket-tts-en-v2026_04/`. */
    fun engineDir(filesDir: File): File = File(filesDir, "engines/$ENGINE_NAME")

    /** Cheap synchronous install gate for controller/UI paths. */
    fun isInstalled(filesDir: File): Boolean =
        File(engineDir(filesDir), "bundle.json").isFile

    /**
     * The [EngineSpec] [EngineInstaller] uses to serve this engine —
     * descriptor + ordered sources + the Pocket layout check (PB-9).
     */
    internal fun engineSpec(): EngineSpec = EngineSpec(POCKET_TTS_EN, sources(), ::checkLayout)

    /**
     * Pocket TTS layout: 5 ONNX graphs + tokenizer.model +
     * bos_before_voice.npy + bundle.json + voices/ subdir with the six
     * commercial-safe base WAVs. Voice cloning lands files under
     * `voice_cache/`/`cloned_voices/` later; their absence at install time
     * is fine, and Miko's extra `vctk_*` WAVs are optional extras — present
     * in the Miko bundle, absent from the Marmalade fallback, required
     * neither way.
     *
     * Post-extract structural check invoked by [EngineInstaller] after the
     * meta-sha drift check — moved here verbatim (behavior preserved, D-08)
     * so the catalog owns its own layout knowledge (PB-9).
     */
    internal fun checkLayout(dir: File): InstallState {
        // Always-present, variant-independent files. The 5 ONNX filenames are
        // variant-specific (declared in bundle.json's `onnx_files` block) and
        // checked separately below.
        val requiredFiles = listOf(
            "tokenizer.model",
            "bos_before_voice.npy",
            "bundle.json",
        )
        for (name in requiredFiles) {
            val f = File(dir, name)
            if (!f.isFile || f.length() == 0L) return InstallState.Corrupt
        }
        // Read the onnx filenames from bundle.json. Parse failure ⇒ corrupt.
        val bundleSpec = runCatching {
            PocketBundle.load(File(dir, "bundle.json"))
        }.getOrNull() ?: return InstallState.Corrupt
        val onnxFiles = bundleSpec.onnxFiles
        val onnxNames = listOf(
            onnxFiles.textConditioner,
            onnxFiles.mimiEncoder,
            onnxFiles.mimiDecoder,
            onnxFiles.flowLmMain,
            onnxFiles.flowLmFlow,
        )
        for (name in onnxNames) {
            val f = File(dir, name)
            if (!f.isFile || f.length() == 0L) return InstallState.Corrupt
        }
        // At least the flow_lm_main file should be appreciably-sized — guards
        // against a truncated extraction where the headers landed but the body
        // didn't (a minimum-model-size sanity check). flow_lm_main is the
        // largest of the five graphs across all variants.
        val mainOnnx = File(dir, onnxFiles.flowLmMain)
        if (mainOnnx.length() < MIN_MODEL_BYTES) return InstallState.Corrupt

        val voicesDir = File(dir, "voices")
        if (!voicesDir.isDirectory) return InstallState.Corrupt
        // Check the six commercial-safe Kyutai predefined names match what the
        // voice catalog promises (PocketVoiceCatalog.baseVoices is the source
        // of truth — the six hardcoded names here mirror it). The upstream
        // `cosette` (Expresso) and `jean` (EARS) voices are CC-BY-NC-4.0 and
        // are deliberately absent. Miko's `vctk_*` extras are not required.
        val expectedVoices = listOf("alba", "azelma", "eponine", "fantine", "javert", "marius")
        for (name in expectedVoices) {
            val wav = File(voicesDir, "$name.wav")
            if (!wav.isFile || wav.length() == 0L) return InstallState.Corrupt
        }
        return InstallState.Installed
    }

    // Sanity floor for the post-install layout check. Tuned to catch
    // truncated extractions without pinning to the exact upstream
    // numbers (which would force a code change every bundle bump).
    private const val MIN_MODEL_BYTES: Long = 1L * 1024L * 1024L
}
