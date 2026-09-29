package eu.kanade.tachiyomi.ui.reader.novel.tts.supertonic

import eu.kanade.tachiyomi.ui.reader.novel.tts.SupertonicEngineCatalog
import eu.kanade.tachiyomi.ui.reader.novel.tts.SupertonicVoiceCatalog
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * JVM tests for [SupertonicVoiceCatalog] (namespaced voices, F5 fallback)
 * and [SupertonicEngineCatalog] (int8/fp32 install gates).
 */
class SupertonicVoiceCatalogTest {

    @TempDir
    lateinit var filesDir: File

    private val engineDir: File get() = SupertonicEngineCatalog.engineDir(filesDir)

    /** Stage a synthetic int8-layout bundle: voice.bin + the ONNX/meta files. */
    private fun stageInt8Bundle(voiceCount: Int = 10): File {
        engineDir.mkdirs()
        SupertonicTestFixtures.writeVoiceBin(File(engineDir, "voice.bin"), n = voiceCount)
        File(engineDir, "unicode_indexer.bin").writeBytes(ByteArray(16))
        File(engineDir, "tts.json").writeText("{}")
        SupertonicEngineCatalog.INT8_ONNX_FILES.forEach {
            File(engineDir, it).writeBytes(byteArrayOf(0))
        }
        return engineDir
    }

    // ---- SupertonicVoiceCatalog -------------------------------------------

    @Test
    fun `sid order is F1 to F5 then M1 to M5 and sid 4 is F5`() {
        SupertonicVoiceCatalog.SID_ORDER shouldBe
            listOf("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5")
        SupertonicVoiceCatalog.sidFor("F5") shouldBe 4
        SupertonicVoiceCatalog.sidFor("supertonic:F5") shouldBe 4
        SupertonicVoiceCatalog.sidFor("M5") shouldBe 9
        SupertonicVoiceCatalog.sidFor("eponine") shouldBe null
    }

    @Test
    fun `default voice is namespaced F5`() {
        SupertonicVoiceCatalog.DEFAULT_VOICE shouldBe "supertonic:F5"
        SupertonicVoiceCatalog.voices.map { it.name } shouldBe
            SupertonicVoiceCatalog.SID_ORDER.map { "supertonic:$it" }
    }

    @Test
    fun `stale pocket voice name falls back to the default`() {
        // R6: a Pocket-era pref value ("eponine") must never reach the engine.
        stageInt8Bundle()
        SupertonicVoiceCatalog.resolveInstalledVoice("eponine", filesDir) shouldBe
            SupertonicVoiceCatalog.DEFAULT_VOICE
        SupertonicVoiceCatalog.resolveInstalledVoice("supertonic:Q9", filesDir) shouldBe
            SupertonicVoiceCatalog.DEFAULT_VOICE
        SupertonicVoiceCatalog.resolveInstalledVoice("", filesDir) shouldBe
            SupertonicVoiceCatalog.DEFAULT_VOICE
        SupertonicVoiceCatalog.resolveInstalledVoice("   ", filesDir) shouldBe
            SupertonicVoiceCatalog.DEFAULT_VOICE
    }

    @Test
    fun `bare and namespaced sid names resolve to the namespaced form`() {
        stageInt8Bundle()
        SupertonicVoiceCatalog.resolveInstalledVoice("F5", filesDir) shouldBe "supertonic:F5"
        SupertonicVoiceCatalog.resolveInstalledVoice("supertonic:M2", filesDir) shouldBe
            "supertonic:M2"
        SupertonicVoiceCatalog.resolveInstalledVoice("M1", filesDir) shouldBe "supertonic:M1"
    }

    @Test
    fun `voice bin with too few rows falls back for missing sids`() {
        stageInt8Bundle(voiceCount = 3)
        SupertonicVoiceCatalog.resolveInstalledVoice("supertonic:M2", filesDir) shouldBe
            SupertonicVoiceCatalog.DEFAULT_VOICE
        SupertonicVoiceCatalog.resolveInstalledVoice("F3", filesDir) shouldBe "supertonic:F3"
    }

    @Test
    fun `fp32 voice_styles json also satisfies resolution`() {
        // fp32 layout: no voice.bin — voice_styles/<name>.json instead.
        engineDir.mkdirs()
        File(engineDir, "voice_styles").mkdirs()
        File(engineDir, "voice_styles/M2.json").writeText("{}")
        SupertonicVoiceCatalog.resolveInstalledVoice("supertonic:M2", filesDir) shouldBe
            "supertonic:M2"
        SupertonicVoiceCatalog.resolveInstalledVoice("F5", filesDir) shouldBe
            SupertonicVoiceCatalog.DEFAULT_VOICE
    }

    @Test
    fun `discoverVoices returns sid-order voices for the voice bin count`() {
        val dir = stageInt8Bundle(voiceCount = 4)
        val found = SupertonicVoiceCatalog.discoverVoices(dir)
        found.map { it.name } shouldBe
            listOf("supertonic:F1", "supertonic:F2", "supertonic:F3", "supertonic:F4")
        found.map { it.sid } shouldBe listOf(0, 1, 2, 3)
    }

    @Test
    fun `discoverVoices falls back to voice_styles json names`() {
        engineDir.mkdirs()
        File(engineDir, "voice_styles").mkdirs()
        File(engineDir, "voice_styles/F2.json").writeText("{}")
        File(engineDir, "voice_styles/M4.json").writeText("{}")
        File(engineDir, "voice_styles/NotAStyle.json").writeText("{}")
        SupertonicVoiceCatalog.discoverVoices(engineDir).map { it.name } shouldBe
            listOf("supertonic:F2", "supertonic:M4")
    }

    @Test
    fun `discoverVoices on missing or corrupt voice data returns empty`() {
        engineDir.mkdirs()
        SupertonicVoiceCatalog.discoverVoices(engineDir) shouldBe emptyList()
        File(engineDir, "voice.bin").writeBytes(ByteArray(10))
        SupertonicVoiceCatalog.discoverVoices(engineDir) shouldBe emptyList()
    }

    @Test
    fun `labelFor renders style name plus display name`() {
        val f5 = SupertonicVoiceCatalog.voices.first { it.styleName == "F5" }
        val m1 = SupertonicVoiceCatalog.voices.first { it.styleName == "M1" }
        SupertonicVoiceCatalog.labelFor(f5) shouldBe "F5 · Willow"
        SupertonicVoiceCatalog.labelFor(m1) shouldBe "M1 · Felix"
    }

    @Test
    fun `every voice has a descriptor and description`() {
        SupertonicVoiceCatalog.voices.forEach { voice ->
            SupertonicVoiceCatalog.descriptionFor(voice).isNotBlank() shouldBe true
        }
    }

    // ---- SupertonicEngineCatalog -------------------------------------------

    @Test
    fun `catalog exposes the locked engine name and sample rate`() {
        SupertonicEngineCatalog.ENGINE_NAME shouldBe "supertonic-3-int8"
        SupertonicEngineCatalog.SAMPLE_RATE shouldBe 44100
        SupertonicEngineCatalog.engineDir(filesDir) shouldBe
            File(filesDir, "engines/supertonic-3-int8")
    }

    @Test
    fun `isInstalled gates on the int8 layout`() {
        SupertonicEngineCatalog.isInstalled(filesDir) shouldBe false
        stageInt8Bundle()
        SupertonicEngineCatalog.isInstalled(filesDir) shouldBe true
    }

    @Test
    fun `isInstalled is false with a partially staged int8 bundle`() {
        engineDir.mkdirs()
        File(engineDir, "tts.json").writeText("{}")
        File(engineDir, "voice.bin").writeBytes(ByteArray(48))
        File(engineDir, "unicode_indexer.bin").writeBytes(ByteArray(16))
        // only 3 of the 4 int8 onnx files
        SupertonicEngineCatalog.INT8_ONNX_FILES.drop(1).forEach {
            File(engineDir, it).writeBytes(byteArrayOf(0))
        }
        SupertonicEngineCatalog.isInstalled(filesDir) shouldBe false
    }

    @Test
    fun `isInstalled accepts the fp32 fallback layout`() {
        engineDir.mkdirs()
        SupertonicEngineCatalog.FP32_ONNX_FILES.forEach {
            File(engineDir, it).writeBytes(byteArrayOf(0))
        }
        File(engineDir, "tts.json").writeText("{}")
        File(engineDir, "unicode_indexer.json").writeText("[]")
        SupertonicEngineCatalog.isInstalled(filesDir) shouldBe false // no voice_styles yet
        File(engineDir, "voice_styles").mkdirs()
        File(engineDir, "voice_styles/F5.json").writeText("{}")
        SupertonicEngineCatalog.isInstalled(filesDir) shouldBe true
    }
}
