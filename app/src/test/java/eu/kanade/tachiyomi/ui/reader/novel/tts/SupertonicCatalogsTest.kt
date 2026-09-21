package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * JVM unit tests for [SupertonicEngineCatalog] — the sherpa int8 archive pin
 * and the two on-disk layout gates. Mirrors PocketCatalogsTest; the deeper
 * `checkLayout` assertions live in EngineInstallerTest's supertonic section.
 */
class SupertonicCatalogsTest {

    @TempDir
    lateinit var filesDir: File

    // ---- SupertonicEngineCatalog ----

    @Test
    fun `engine descriptor uses the locked engine name`() {
        SupertonicEngineCatalog.ENGINE_NAME shouldBe "supertonic-3-int8"
        SupertonicEngineCatalog.SUPERTONIC_3.name shouldBe "supertonic-3-int8"
        SupertonicEngineCatalog.SUPERTONIC_3.displayName shouldBe "Supertonic-3 (English, int8)"
        SupertonicEngineCatalog.SUPERTONIC_3.downloadSizeBytes shouldBe 128_774_318L
        SupertonicEngineCatalog.SUPERTONIC_3.installedSizeBytes shouldBe 145_316_356L
        SupertonicEngineCatalog.SUPERTONIC_3.licenseNotice shouldBe "LICENSES/supertonic-3.md"
    }

    @Test
    fun `sherpa archive carries the real int8 pin`() {
        val archive = SupertonicEngineCatalog.SHERPA_ARCHIVE
        archive.url shouldBe
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
            "sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2"
        archive.sha256 shouldBe
            "82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427"
        archive.sizeBytes shouldBe 128_774_318L
        archive.archiveRoot shouldBe "sherpa-onnx-supertonic-3-tts-int8-2026-05-11/"
    }

    @Test
    fun `sources returns the single sherpa source`() {
        // PB-11: sherpa GitHub release only; dual-source ordering machinery
        // stays for when a Miko mirror appears.
        SupertonicEngineCatalog.sources() shouldBe listOf(SupertonicEngineCatalog.SHERPA_ARCHIVE)
    }

    @Test
    fun `archive pin format is well formed`() {
        val archive = SupertonicEngineCatalog.SHERPA_ARCHIVE
        archive.url shouldStartWith "https://"
        archive.sha256.matches(Regex("[0-9a-f]{64}")) shouldBe true
        archive.archiveRoot shouldEndWith "/"
        archive.sizeBytes shouldBe 128_774_318L
    }

    @Test
    fun `engineDir resolves under filesDir engines`() {
        SupertonicEngineCatalog.engineDir(filesDir) shouldBe
            File(filesDir, "engines/supertonic-3-int8")
    }

    @Test
    fun `isInstalled accepts the int8 layout`() {
        val dir = SupertonicEngineCatalog.engineDir(filesDir).apply { mkdirs() }
        for (name in SupertonicEngineCatalog.INT8_ONNX_FILES + SupertonicEngineCatalog.INT8_META_FILES) {
            File(dir, name).writeBytes(byteArrayOf(1))
        }
        SupertonicEngineCatalog.isInstalled(filesDir) shouldBe true
    }

    @Test
    fun `isInstalled rejects an int8 layout missing a graph`() {
        val dir = SupertonicEngineCatalog.engineDir(filesDir).apply { mkdirs() }
        for (name in SupertonicEngineCatalog.INT8_ONNX_FILES + SupertonicEngineCatalog.INT8_META_FILES) {
            if (name == "vocoder.int8.onnx") continue
            File(dir, name).writeBytes(byteArrayOf(1))
        }
        SupertonicEngineCatalog.isInstalled(filesDir) shouldBe false
    }

    @Test
    fun `isInstalled accepts the fp32 fallback layout`() {
        // PB-1: adb-staged dirs may carry the HF-style fp32 bundle —
        // *.onnx ×4 + tts.json + unicode_indexer.json + voice_styles/<name>.json.
        val dir = SupertonicEngineCatalog.engineDir(filesDir).apply { mkdirs() }
        for (name in SupertonicEngineCatalog.FP32_ONNX_FILES + SupertonicEngineCatalog.FP32_META_FILES) {
            File(dir, name).writeBytes(byteArrayOf(1))
        }
        val stylesDir = File(dir, "voice_styles").apply { mkdirs() }
        File(stylesDir, "F1.json").writeBytes(byteArrayOf(1))
        SupertonicEngineCatalog.isInstalled(filesDir) shouldBe true
    }

    @Test
    fun `isInstalled rejects fp32 layout with no voice styles`() {
        val dir = SupertonicEngineCatalog.engineDir(filesDir).apply { mkdirs() }
        for (name in SupertonicEngineCatalog.FP32_ONNX_FILES + SupertonicEngineCatalog.FP32_META_FILES) {
            File(dir, name).writeBytes(byteArrayOf(1))
        }
        File(dir, "voice_styles").mkdirs() // present but empty
        SupertonicEngineCatalog.isInstalled(filesDir) shouldBe false
    }

    @Test
    fun `isInstalled on absent dir returns false`() {
        SupertonicEngineCatalog.isInstalled(filesDir) shouldBe false
    }
}
