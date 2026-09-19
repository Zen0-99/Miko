package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PocketCatalogsTest {

    @TempDir
    lateinit var filesDir: File

    // ---- PocketEngineCatalog ----

    @Test
    fun `engine descriptor uses the locked engine name and sizes`() {
        PocketEngineCatalog.ENGINE_NAME shouldBe "pocket-tts-en-v2026_04"
        PocketEngineCatalog.POCKET_TTS_EN.name shouldBe "pocket-tts-en-v2026_04"
        PocketEngineCatalog.POCKET_TTS_EN.displayName shouldBe "Pocket TTS (English, 2026-04)"
        PocketEngineCatalog.POCKET_TTS_EN.downloadSizeBytes shouldBe 97_291_178L
        PocketEngineCatalog.POCKET_TTS_EN.installedSizeBytes shouldBe 217_288_756L
        PocketEngineCatalog.POCKET_TTS_EN.licenseNotice shouldBe "LICENSES/pocket-tts.md"
    }

    @Test
    fun `fallback archive carries the Marmalade v21 pin`() {
        val fallback = PocketEngineCatalog.FALLBACK_ARCHIVE
        fallback.url shouldBe
            "https://github.com/maxwhipw/marmalade-tts-android-engines/releases/download/v21/pocket-tts-en-v2026_04-mixed.tar.bz2"
        fallback.sha256 shouldBe
            "3a3ed2810afbae1f963f4f4e3ebde767c2d3c7c622cc544f23170f0ca755b452"
        fallback.sizeBytes shouldBe 97_291_178L
        fallback.archiveRoot shouldBe "pocket-tts-en/"
    }

    @Test
    fun `sources returns fallback only while the Miko primary is unpopulated`() {
        // MIKO_BUNDLE_* ship empty until the mirror is published (D-03).
        PocketEngineCatalog.MIKO_BUNDLE_SHA256 shouldBe ""
        PocketEngineCatalog.primarySource shouldBe null
        PocketEngineCatalog.sources() shouldBe listOf(PocketEngineCatalog.FALLBACK_ARCHIVE)
    }

    @Test
    fun `sources puts a populated primary ahead of the fallback`() {
        val primary = EngineArchiveSource(
            url = PocketEngineCatalog.MIKO_BUNDLE_URL,
            sha256 = "0".repeat(64),
            sizeBytes = 100_000_000L,
        )
        PocketEngineCatalog.sources(primary) shouldBe
            listOf(primary, PocketEngineCatalog.FALLBACK_ARCHIVE)
        // The fallback is never lost, whichever way the ordering goes.
        PocketEngineCatalog.sources(null) shouldBe
            listOf(PocketEngineCatalog.FALLBACK_ARCHIVE)
    }

    @Test
    fun `sha256 constants are 64 lowercase hex when populated`() {
        val hex = Regex("[0-9a-f]{64}")
        PocketEngineCatalog.FALLBACK_ARCHIVE.sha256.matches(hex) shouldBe true
        if (PocketEngineCatalog.MIKO_BUNDLE_SHA256.isNotEmpty()) {
            PocketEngineCatalog.MIKO_BUNDLE_SHA256.matches(hex) shouldBe true
        }
    }

    @Test
    fun `isInstalled gates on bundle json under engines dir`() {
        PocketEngineCatalog.isInstalled(filesDir) shouldBe false
        val engineDir = PocketEngineCatalog.engineDir(filesDir).apply { mkdirs() }
        File(engineDir, "bundle.json").writeText("{}")
        PocketEngineCatalog.isInstalled(filesDir) shouldBe true
    }

    // ---- PocketVoiceCatalog ----

    @Test
    fun `base voices are exactly the six commercial-safe voices`() {
        PocketVoiceCatalog.baseVoices.map { it.name } shouldBe
            listOf("alba", "azelma", "eponine", "fantine", "javert", "marius")
    }

    @Test
    fun `default voice is eponine`() {
        PocketVoiceCatalog.DEFAULT_VOICE shouldBe "eponine"
        PocketVoiceCatalog.baseVoices.any { it.name == PocketVoiceCatalog.DEFAULT_VOICE } shouldBe true
    }

    @Test
    fun `every base voice license is distributable`() {
        PocketVoiceCatalog.baseVoices.forEach { voice ->
            voice.license shouldBeIn setOf("CC-BY-4.0", "CC0")
        }
    }

    @Test
    fun `licenseFor maps base and vctk voices`() {
        PocketVoiceCatalog.licenseFor("eponine") shouldBe "CC-BY-4.0"
        PocketVoiceCatalog.licenseFor("alba") shouldBe "CC-BY-4.0"
        PocketVoiceCatalog.licenseFor("javert") shouldBe "CC0"
        PocketVoiceCatalog.licenseFor("marius") shouldBe "CC0"
        PocketVoiceCatalog.licenseFor("vctk_p225") shouldBe "CC-BY-4.0"
        PocketVoiceCatalog.licenseFor("cosette") shouldBe ""
    }

    @Test
    fun `extraVoiceName maps vctk wav filenames only`() {
        PocketVoiceCatalog.extraVoiceName("vctk_p225.wav") shouldBe "vctk_p225"
        PocketVoiceCatalog.extraVoiceName("vctk_p253.wav") shouldBe "vctk_p253"
        PocketVoiceCatalog.extraVoiceName("alba.wav") shouldBe null
        PocketVoiceCatalog.extraVoiceName("vctk_p225.bin") shouldBe null
        PocketVoiceCatalog.extraVoiceName("vctk_x999.wav") shouldBe null
        PocketVoiceCatalog.extraVoiceName("vctk_p2250.wav") shouldBe null
    }

    @Test
    fun `discoverVoices returns present base voices plus vctk extras`() {
        val engineDir = PocketEngineCatalog.engineDir(filesDir)
        val voicesDir = File(engineDir, "voices").apply { mkdirs() }
        File(voicesDir, "eponine.wav").writeBytes(byteArrayOf(1))
        File(voicesDir, "javert.wav").writeBytes(byteArrayOf(1))
        File(voicesDir, "vctk_p225.wav").writeBytes(byteArrayOf(1))
        File(voicesDir, "vctk_p253.wav").writeBytes(byteArrayOf(1))
        File(voicesDir, "notes.txt").writeBytes(byteArrayOf(1))

        val found = PocketVoiceCatalog.discoverVoices(engineDir)
        found.map { it.name } shouldBe listOf("eponine", "javert", "vctk_p225", "vctk_p253")
        found.last().license shouldBe "CC-BY-4.0"
    }

    @Test
    fun `discoverVoices on missing voices dir returns empty`() {
        PocketVoiceCatalog.discoverVoices(PocketEngineCatalog.engineDir(filesDir)) shouldBe emptyList()
    }
}
