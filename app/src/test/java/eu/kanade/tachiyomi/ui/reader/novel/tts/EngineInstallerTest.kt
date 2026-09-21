package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * JVM unit tests for [EngineInstaller].
 *
 * Drives the installer through its `internal` `installViaDescriptor` /
 * `verifyDescriptor` entry points against an in-memory [FakeHttpFetcher]
 * (no real sockets, no Android `Context`, no Robolectric — the
 * `EngineFilesDir`/`HttpFetcher`/`NativeEngineHandle` fun-interface seams
 * exist precisely so this suite runs on the bare JVM). The fetcher serves
 * synthetic tar.bz2 archives built in-test with commons-compress — the same
 * library the installer extracts with, so any compat mismatch surfaces here.
 *
 * The public `install(engineName)` path is exercised only for the
 * unknown-name gate; the production source ordering lives in
 * [PocketEngineCatalog.sources] and is covered by `PocketCatalogsTest`.
 */
class EngineInstallerTest {

    @TempDir
    lateinit var filesDir: File

    private lateinit var installer: EngineInstaller
    private lateinit var fakeEngine: FakeNativeEngineHandle
    private lateinit var fetcher: FakeHttpFetcher

    @BeforeEach
    fun setUp() {
        fakeEngine = FakeNativeEngineHandle()
        fetcher = FakeHttpFetcher()
        installer = EngineInstaller(
            filesDir = EngineFilesDir { filesDir },
            engineHandle = fakeEngine,
            httpFetcher = fetcher,
        )
    }

    // -- happy path ---------------------------------------------------------

    @Test
    fun `install downloads verifies sha extracts and atomically renames`() = runTest {
        val bundle = stageBundle(POCKET_LAYOUT)

        val progressEvents = mutableListOf<InstallState.Downloading>()
        val result = installer.installViaDescriptor(bundle.descriptor, listOf(bundle.source), PocketEngineCatalog::checkLayout) { progress ->
            progressEvents += progress
        }

        result.isSuccess shouldBe true
        val engineDir = File(filesDir, "engines/${bundle.name}")
        engineDir.isDirectory shouldBe true
        // Spot-check extracted files (archiveRoot pocket-tts-en/ stripped).
        (File(engineDir, "flow_lm_main_int8.onnx").length() >= 1L * 1024L * 1024L) shouldBe true
        File(engineDir, "bundle.json").isFile shouldBe true
        File(engineDir, "voices/eponine.wav").isFile shouldBe true
        // Scratch dir + archive scratch cleaned up.
        File(filesDir, "engines/${bundle.name}.tmp").exists() shouldBe false
        File(filesDir, "engines/${bundle.name}.archive.tmp").exists() shouldBe false
        // .install_meta.json written after the rename, recording the source sha.
        val meta = File(engineDir, ".install_meta.json")
        meta.isFile shouldBe true
        meta.readText() shouldContain bundle.source.sha256
        // Progress reported at least once and the final emission is 100%.
        progressEvents.isNotEmpty() shouldBe true
        progressEvents.last().bytesFetched shouldBe progressEvents.last().totalBytes
        // Hot state flow landed on Installed.
        installer.state(bundle.name).first() shouldBe InstallState.Installed
    }

    // -- sha mismatch ---------------------------------------------------------

    @Test
    fun `sha mismatch on every source fails install and cleans scratch`() = runTest {
        val archive = buildArchive(POCKET_LAYOUT, ARCHIVE_ROOT)
        val sourceA = source("https://test/pocket-a.tar.bz2", archive, shaOverride = "deadbeef".repeat(8))
        val sourceB = source("https://test/pocket-b.tar.bz2", archive, shaOverride = "cafebabe".repeat(8))
        val descriptor = pocketDescriptor(archive)
        fetcher.payloads[sourceA.url] = archive
        fetcher.payloads[sourceB.url] = archive

        val result = installer.installViaDescriptor(descriptor, listOf(sourceA, sourceB), PocketEngineCatalog::checkLayout) {}

        result.isFailure shouldBe true
        (result.exceptionOrNull()?.message ?: "") shouldContain "SHA-256 mismatch"
        // Both sources were tried in order before failing.
        fetcher.requestedUrls shouldBe listOf(sourceA.url, sourceB.url)
        File(filesDir, "engines/${descriptor.name}").exists() shouldBe false
        File(filesDir, "engines/${descriptor.name}.tmp").exists() shouldBe false
        // sha mismatch = known-bad bytes → partial archive wiped.
        File(filesDir, "engines/${descriptor.name}.archive.tmp").exists() shouldBe false
        installer.state(descriptor.name).first() shouldBe InstallState.Failed(
            "all 2 sources failed — last: ${result.exceptionOrNull()?.message}",
        )
    }

    // -- dual-source fallback (D-03) -------------------------------------------

    @Test
    fun `primary source failure falls back to next source`() = runTest {
        val archive = buildArchive(POCKET_LAYOUT, ARCHIVE_ROOT)
        val primary = source("https://test/primary.tar.bz2", archive)
        val fallback = source("https://test/fallback.tar.bz2", archive)
        val descriptor = pocketDescriptor(archive)
        // Primary URL deliberately NOT registered — the fake fetcher throws
        // IOException, simulating a 404/transport error on the primary host.
        fetcher.payloads[fallback.url] = archive

        val result = installer.installViaDescriptor(descriptor, listOf(primary, fallback), PocketEngineCatalog::checkLayout) {}

        result.isSuccess shouldBe true
        // Second source reached only after the first failed.
        fetcher.requestedUrls shouldBe listOf(primary.url, fallback.url)
        val engineDir = File(filesDir, "engines/${descriptor.name}")
        engineDir.isDirectory shouldBe true
        File(engineDir, "bundle.json").isFile shouldBe true
        installer.state(descriptor.name).first() shouldBe InstallState.Installed
    }

    // -- zip-slip protection ----------------------------------------------------

    @Test
    fun `zip slip entry is rejected and writes nothing outside the scratch dir`() = runTest {
        // Archive entry climbs out of the wrapper dir: after archiveRoot
        // stripping, the entry's relative path is "../evil.txt", which would
        // land in engines/ next to the scratch dir without the canonical-path
        // check.
        val maliciousLayout = mapOf("pocket-tts-en/../evil.txt" to "haha".toByteArray())
        val archive = buildArchive(maliciousLayout, archiveRootName = "")
        val descriptor = pocketDescriptor(archive)
        val source = source("https://test/evil.tar.bz2", archive)
        fetcher.payloads[source.url] = archive

        val result = installer.installViaDescriptor(descriptor, listOf(source), PocketEngineCatalog::checkLayout) {}

        result.isFailure shouldBe true
        (result.exceptionOrNull()?.message ?: "") shouldContain "escapes destination"
        // Nothing was written at the escaped path (or anywhere else).
        File(filesDir, "engines/evil.txt").exists() shouldBe false
        File(filesDir, "engines/${descriptor.name}").exists() shouldBe false
        File(filesDir, "engines/${descriptor.name}.tmp").exists() shouldBe false
        File(filesDir.parentFile, "evil.txt").exists() shouldBe false
    }

    // -- Range resume -----------------------------------------------------------

    @Test
    fun `aborted download resumes via range request on retry`() = runTest {
        val archive = buildArchive(POCKET_LAYOUT, ARCHIVE_ROOT)
        val descriptor = pocketDescriptor(archive)
        val source = source("https://test/pocket.tar.bz2", archive)
        fetcher.payloads[source.url] = archive

        // First attempt aborts mid-stream; the partial .archive.tmp is kept
        // (non-sha failure on the last source) so the retry can resume.
        fetcher.abortAfterBytes = (archive.size / 2).toLong()
        val first = installer.installViaDescriptor(descriptor, listOf(source), PocketEngineCatalog::checkLayout) {}
        first.isFailure shouldBe true
        val archiveTmp = File(filesDir, "engines/${descriptor.name}.archive.tmp")
        archiveTmp.isFile shouldBe true
        (archiveTmp.length() in 1 until archive.size.toLong()) shouldBe true

        // Second attempt: the fetcher honors openRange(fromBytes) with the
        // partial offset, and sha256 verifies over resumed + appended bytes.
        val second = installer.installViaDescriptor(descriptor, listOf(source), PocketEngineCatalog::checkLayout) {}
        second.isSuccess shouldBe true
        File(filesDir, "engines/${descriptor.name}/bundle.json").isFile shouldBe true
        installer.state(descriptor.name).first() shouldBe InstallState.Installed
    }

    @Test
    fun `server ignoring range discards the partial and restarts`() = runTest {
        val archive = buildArchive(POCKET_LAYOUT, ARCHIVE_ROOT)
        val descriptor = pocketDescriptor(archive)
        val source = source("https://test/pocket.tar.bz2", archive)
        fetcher.payloads[source.url] = archive

        fetcher.abortAfterBytes = (archive.size / 2).toLong()
        installer.installViaDescriptor(descriptor, listOf(source), PocketEngineCatalog::checkLayout) {}.isFailure shouldBe true

        // Server doesn't speak Range (HTTP 200 full body): the installer must
        // discard the partial and restart rather than append onto it.
        fetcher.honorRange = false
        val result = installer.installViaDescriptor(descriptor, listOf(source), PocketEngineCatalog::checkLayout) {}

        result.isSuccess shouldBe true
        File(filesDir, "engines/${descriptor.name}/bundle.json").isFile shouldBe true
    }

    // -- verify -----------------------------------------------------------------

    @Test
    fun `verify distinguishes notinstalled corrupt installed and outdated`() = runTest {
        val archive = buildArchive(POCKET_LAYOUT, ARCHIVE_ROOT)
        val descriptor = pocketDescriptor(archive)
        val expectedSource = source("https://test/pocket.tar.bz2", archive)

        // Absent dir.
        installer.verifyDescriptor(descriptor, expectedSource, PocketEngineCatalog::checkLayout) shouldBe InstallState.NotInstalled

        // Complete layout + meta stamped with the expected sha → Installed.
        val dir = File(filesDir, "engines/${descriptor.name}").apply { mkdirs() }
        stagePocketLayout(dir)
        File(dir, ".install_meta.json")
            .writeText("""{"archive_sha256":"${expectedSource.sha256}","archive_url":"${expectedSource.url}"}""")
        installer.verifyDescriptor(descriptor, expectedSource, PocketEngineCatalog::checkLayout) shouldBe InstallState.Installed

        // Missing required file → Corrupt.
        File(dir, "tokenizer.model").delete()
        installer.verifyDescriptor(descriptor, expectedSource, PocketEngineCatalog::checkLayout) shouldBe InstallState.Corrupt

        // sha drift in .install_meta.json → Outdated (layout itself is intact).
        stagePocketLayout(dir)
        File(dir, ".install_meta.json")
            .writeText("""{"archive_sha256":"${"f".repeat(64)}","archive_url":"https://test/old.tar.bz2"}""")
        installer.verifyDescriptor(descriptor, expectedSource, PocketEngineCatalog::checkLayout) shouldBe InstallState.Outdated(
            installedSha256 = "f".repeat(64),
            expectedSha256 = expectedSource.sha256,
        )
    }

    @Test
    fun `verify on missing model sized file returns corrupt`() = runTest {
        val archive = buildArchive(POCKET_LAYOUT, ARCHIVE_ROOT)
        val descriptor = pocketDescriptor(archive)
        val expectedSource = source("https://test/pocket.tar.bz2", archive)

        val dir = File(filesDir, "engines/${descriptor.name}").apply { mkdirs() }
        stagePocketLayout(dir)
        // Overwrite flow_lm_main with a sub-1-MiB file — should flip to Corrupt.
        File(dir, "flow_lm_main_int8.onnx").writeText("too small")

        installer.verifyDescriptor(descriptor, expectedSource, PocketEngineCatalog::checkLayout) shouldBe InstallState.Corrupt
    }

    @Test
    fun `verify on unknown engine returns notinstalled`() = runTest {
        installer.verify("bogus-engine") shouldBe InstallState.NotInstalled
    }

    // -- uninstall ---------------------------------------------------------------

    @Test
    fun `uninstall removes the engine dir including voice cache`() = runTest {
        val engineDir = File(filesDir, "engines/${PocketEngineCatalog.ENGINE_NAME}").apply { mkdirs() }
        File(engineDir, "bundle.json").writeText("{}")
        File(engineDir, "voice_cache").mkdirs()
        File(engineDir, "voice_cache/eponine.emb").writeText("emb")

        val result = installer.uninstall(PocketEngineCatalog.ENGINE_NAME)

        result.isSuccess shouldBe true
        engineDir.exists() shouldBe false
        File(engineDir, "voice_cache/eponine.emb").exists() shouldBe false
        fakeEngine.released shouldBe true
        installer.state(PocketEngineCatalog.ENGINE_NAME).first() shouldBe InstallState.NotInstalled
    }

    @Test
    fun `uninstall on not installed engine is a noop`() = runTest {
        installer.uninstall(PocketEngineCatalog.ENGINE_NAME).isSuccess shouldBe true
    }

    @Test
    fun `uninstall on unknown engine fails`() = runTest {
        installer.uninstall("bogus-engine").isFailure shouldBe true
    }

    // -- reinstall / failure resilience -------------------------------------------

    @Test
    fun `reinstall replaces previous files atomically`() = runTest {
        // First install.
        val v1 = stageBundle(POCKET_LAYOUT)
        val first = installer.installViaDescriptor(v1.descriptor, listOf(v1.source), PocketEngineCatalog::checkLayout) {}
        first.isSuccess shouldBe true
        File(filesDir, "engines/${v1.name}/tokenizer.model").readText() shouldBe "sentencepiece"

        // Second install with different content at the same URL.
        val v2Layout = POCKET_LAYOUT.toMutableMap().apply {
            this["tokenizer.model"] = "sentencepiece-v2".toByteArray()
        }
        val v2Archive = buildArchive(v2Layout, ARCHIVE_ROOT)
        val v2Source = source(v1.source.url, v2Archive)
        fetcher.payloads[v2Source.url] = v2Archive

        val second = installer.installViaDescriptor(v1.descriptor, listOf(v2Source), PocketEngineCatalog::checkLayout) {}
        second.isSuccess shouldBe true

        File(filesDir, "engines/${v1.name}/tokenizer.model").readText() shouldBe "sentencepiece-v2"
        // Native handle was released before the reinstall touched the
        // existing engine dir.
        fakeEngine.released shouldBe true
        // The new meta records the sha of the source that produced v2.
        File(filesDir, "engines/${v1.name}/.install_meta.json")
            .readText() shouldContain v2Source.sha256
    }

    @Test
    fun `leftover fully downloaded archive installs without refetching`() = runTest {
        // A previous attempt downloaded the whole archive, then failed after
        // the download (e.g. disk full during extraction — the installer keeps
        // the archive). A resume request for `bytes=<totalBytes>-` would be
        // HTTP 416 from a real server; the installer must skip the network
        // entirely. Prove it by NOT registering the URL — any fetch throws.
        val archive = buildArchive(POCKET_LAYOUT, ARCHIVE_ROOT)
        val descriptor = pocketDescriptor(archive)
        val source = source("https://test/pocket.tar.bz2", archive)
        val archiveTmp = File(filesDir, "engines/${descriptor.name}.archive.tmp")
        archiveTmp.parentFile!!.mkdirs()
        archiveTmp.writeBytes(archive)

        val result = installer.installViaDescriptor(descriptor, listOf(source), PocketEngineCatalog::checkLayout) {}

        result.isSuccess shouldBe true
        File(filesDir, "engines/${descriptor.name}/bundle.json").isFile shouldBe true
        archiveTmp.exists() shouldBe false
    }

    @Test
    fun `failed update keeps the existing engine intact`() = runTest {
        val bundle = stageBundle(POCKET_LAYOUT)
        val first = installer.installViaDescriptor(bundle.descriptor, listOf(bundle.source), PocketEngineCatalog::checkLayout) {}
        first.isSuccess shouldBe true

        // Update attempt whose download fails (URL no longer registered).
        fetcher.payloads.remove(bundle.source.url)
        val second = installer.installViaDescriptor(bundle.descriptor, listOf(bundle.source), PocketEngineCatalog::checkLayout) {}
        second.isFailure shouldBe true

        // The previously-working engine must survive the failed update.
        val engineDir = File(filesDir, "engines/${bundle.name}")
        engineDir.isDirectory shouldBe true
        File(engineDir, "bundle.json").isFile shouldBe true
        installer.verifyDescriptor(bundle.descriptor, bundle.source, PocketEngineCatalog::checkLayout) shouldBe InstallState.Installed
    }

    // -- catalog gating --------------------------------------------------------------

    @Test
    fun `install of unknown engine fails fast`() = runTest {
        val result = installer.install("bogus-engine")

        result.isFailure shouldBe true
        (result.exceptionOrNull()?.message ?: "") shouldContain "Unknown engine"
        installer.state("bogus-engine").first() shouldBe InstallState.Failed("Unknown engine: bogus-engine")
        File(filesDir, "engines/bogus-engine").exists() shouldBe false
    }

    // -- supertonic: spec dispatch ---------------------------------------------------

    @Test
    fun `install supertonic engine name dispatches to the sherpa source not unknown engine`() = runTest {
        // The fake fetcher has no payload registered for the real
        // SHERPA_ARCHIVE.url, so the attempt fails on the fetch — the point
        // is that specFor routed to the supertonic spec at all instead of
        // failing fast with "Unknown engine".
        val result = installer.install(SupertonicEngineCatalog.ENGINE_NAME)

        result.isFailure shouldBe true
        // Dispatch proof: the fetcher was asked for the real sherpa URL —
        // the Result carries the last source error, the state flow the
        // aggregated "all sources failed" reason.
        fetcher.requestedUrls shouldBe listOf(SupertonicEngineCatalog.SHERPA_ARCHIVE.url)
        installer.state(SupertonicEngineCatalog.ENGINE_NAME).first() shouldBe
            InstallState.Failed("all 1 sources failed — last: ${result.exceptionOrNull()?.message}")
    }

    // -- supertonic: install ----------------------------------------------------------

    @Test
    fun `supertonic install extracts sherpa layout and stamps meta`() = runTest {
        // Inject the test source through installViaDescriptor (same
        // convention as the pocket cases — production constants stay
        // immutable) while reusing the catalog's real spec for the
        // descriptor + layout check.
        val spec = SupertonicEngineCatalog.engineSpec()
        val archive = buildArchive(SUPERTONIC_LAYOUT, SUPERTONIC_ARCHIVE_ROOT)
        val source = source(
            "https://test/supertonic.tar.bz2",
            archive,
            archiveRoot = SUPERTONIC_ARCHIVE_ROOT,
        )
        fetcher.payloads[source.url] = archive

        val result = installer.installViaDescriptor(spec.descriptor, listOf(source), spec.layoutCheck) {}

        result.isSuccess shouldBe true
        val engineDir = File(filesDir, "engines/${SupertonicEngineCatalog.ENGINE_NAME}")
        engineDir.isDirectory shouldBe true
        // archiveRoot stripped: files land at the top level of the engine dir.
        File(engineDir, "vector_estimator.int8.onnx").isFile shouldBe true
        File(engineDir, "tts.json").isFile shouldBe true
        File(engineDir, "voice.bin").isFile shouldBe true
        File(engineDir, "unicode_indexer.bin").isFile shouldBe true
        val meta = File(engineDir, ".install_meta.json")
        meta.isFile shouldBe true
        meta.readText() shouldContain source.sha256
        installer.state(SupertonicEngineCatalog.ENGINE_NAME).first() shouldBe InstallState.Installed
        // The cheap catalog gate agrees with the installer's verdict.
        SupertonicEngineCatalog.isInstalled(filesDir) shouldBe true
    }

    @Test
    fun `supertonic zip slip entry is rejected`() = runTest {
        val maliciousLayout = mapOf("$SUPERTONIC_ARCHIVE_ROOT../evil.txt" to "haha".toByteArray())
        val archive = buildArchive(maliciousLayout, archiveRootName = "")
        val spec = SupertonicEngineCatalog.engineSpec()
        val source = source(
            "https://test/evil-supertonic.tar.bz2",
            archive,
            archiveRoot = SUPERTONIC_ARCHIVE_ROOT,
        )
        fetcher.payloads[source.url] = archive

        val result = installer.installViaDescriptor(spec.descriptor, listOf(source), spec.layoutCheck) {}

        result.isFailure shouldBe true
        (result.exceptionOrNull()?.message ?: "") shouldContain "escapes destination"
        File(filesDir, "engines/evil.txt").exists() shouldBe false
        File(filesDir, "engines/${spec.descriptor.name}").exists() shouldBe false
    }

    // -- supertonic: verify ------------------------------------------------------------

    @Test
    fun `supertonic verify distinguishes notinstalled corrupt installed and outdated`() = runTest {
        val spec = SupertonicEngineCatalog.engineSpec()
        val expectedSource = source(
            "https://test/supertonic.tar.bz2",
            buildArchive(SUPERTONIC_LAYOUT, SUPERTONIC_ARCHIVE_ROOT),
            archiveRoot = SUPERTONIC_ARCHIVE_ROOT,
        )

        // Absent dir.
        installer.verifyDescriptor(spec.descriptor, expectedSource, spec.layoutCheck) shouldBe
            InstallState.NotInstalled

        // Complete layout + meta stamped with the expected sha → Installed.
        val dir = File(filesDir, "engines/${spec.descriptor.name}").apply { mkdirs() }
        stageSupertonicLayout(dir)
        File(dir, ".install_meta.json")
            .writeText("""{"archive_sha256":"${expectedSource.sha256}","archive_url":"${expectedSource.url}"}""")
        installer.verifyDescriptor(spec.descriptor, expectedSource, spec.layoutCheck) shouldBe
            InstallState.Installed

        // Missing required file → Corrupt.
        File(dir, "voice.bin").delete()
        installer.verifyDescriptor(spec.descriptor, expectedSource, spec.layoutCheck) shouldBe
            InstallState.Corrupt

        // sha drift in .install_meta.json → Outdated (layout itself is intact).
        stageSupertonicLayout(dir)
        File(dir, ".install_meta.json")
            .writeText("""{"archive_sha256":"${"f".repeat(64)}","archive_url":"https://test/old.tar.bz2"}""")
        installer.verifyDescriptor(spec.descriptor, expectedSource, spec.layoutCheck) shouldBe
            InstallState.Outdated(
                installedSha256 = "f".repeat(64),
                expectedSha256 = expectedSource.sha256,
            )
    }

    @Test
    fun `supertonic verify on wrong sample rate returns corrupt`() = runTest {
        // V5 negative case: the sherpa docs table claims 24000 but the real
        // int8 tts.json declares ae.sample_rate = 44100 — a 24000 bundle is
        // corrupt, never "Installed".
        val spec = SupertonicEngineCatalog.engineSpec()
        val expectedSource = source(
            "https://test/supertonic.tar.bz2",
            buildArchive(SUPERTONIC_LAYOUT, SUPERTONIC_ARCHIVE_ROOT),
            archiveRoot = SUPERTONIC_ARCHIVE_ROOT,
        )

        val dir = File(filesDir, "engines/${spec.descriptor.name}").apply { mkdirs() }
        stageSupertonicLayout(dir)
        File(dir, "tts.json").writeText(
            """{"ae":{"sample_rate":24000,"base_chunk_size":512},"ttl":{"chunk_compress_factor":6,"latent_dim":24}}""",
        )
        File(dir, ".install_meta.json")
            .writeText("""{"archive_sha256":"${expectedSource.sha256}","archive_url":"${expectedSource.url}"}""")

        installer.verifyDescriptor(spec.descriptor, expectedSource, spec.layoutCheck) shouldBe
            InstallState.Corrupt
    }

    @Test
    fun `supertonic verify on malformed tts json returns corrupt`() = runTest {
        val spec = SupertonicEngineCatalog.engineSpec()
        val expectedSource = source(
            "https://test/supertonic.tar.bz2",
            buildArchive(SUPERTONIC_LAYOUT, SUPERTONIC_ARCHIVE_ROOT),
            archiveRoot = SUPERTONIC_ARCHIVE_ROOT,
        )

        val dir = File(filesDir, "engines/${spec.descriptor.name}").apply { mkdirs() }
        stageSupertonicLayout(dir)
        File(dir, "tts.json").writeText("{not json")
        File(dir, ".install_meta.json")
            .writeText("""{"archive_sha256":"${expectedSource.sha256}","archive_url":"${expectedSource.url}"}""")

        installer.verifyDescriptor(spec.descriptor, expectedSource, spec.layoutCheck) shouldBe
            InstallState.Corrupt
    }

    @Test
    fun `supertonic verify on bad voice bin length returns corrupt`() = runTest {
        val spec = SupertonicEngineCatalog.engineSpec()
        val expectedSource = source(
            "https://test/supertonic.tar.bz2",
            buildArchive(SUPERTONIC_LAYOUT, SUPERTONIC_ARCHIVE_ROOT),
            archiveRoot = SUPERTONIC_ARCHIVE_ROOT,
        )

        val dir = File(filesDir, "engines/${spec.descriptor.name}").apply { mkdirs() }
        stageSupertonicLayout(dir)
        // Append one stray float: header dims no longer match the file length.
        File(dir, "voice.bin").appendBytes(ByteArray(4))
        File(dir, ".install_meta.json")
            .writeText("""{"archive_sha256":"${expectedSource.sha256}","archive_url":"${expectedSource.url}"}""")

        installer.verifyDescriptor(spec.descriptor, expectedSource, spec.layoutCheck) shouldBe
            InstallState.Corrupt
    }

    @Test
    fun `supertonic verify on undersized vector estimator returns corrupt`() = runTest {
        val spec = SupertonicEngineCatalog.engineSpec()
        val expectedSource = source(
            "https://test/supertonic.tar.bz2",
            buildArchive(SUPERTONIC_LAYOUT, SUPERTONIC_ARCHIVE_ROOT),
            archiveRoot = SUPERTONIC_ARCHIVE_ROOT,
        )

        val dir = File(filesDir, "engines/${spec.descriptor.name}").apply { mkdirs() }
        stageSupertonicLayout(dir)
        File(dir, "vector_estimator.int8.onnx").writeText("too small")
        File(dir, ".install_meta.json")
            .writeText("""{"archive_sha256":"${expectedSource.sha256}","archive_url":"${expectedSource.url}"}""")

        installer.verifyDescriptor(spec.descriptor, expectedSource, spec.layoutCheck) shouldBe
            InstallState.Corrupt
    }

    // -- fixture machinery ----------------------------------------------------------

    /**
     * A descriptor + the single source that would serve it, with the
     * archive bytes kept around so tests can derive further sources.
     */
    private class TestBundle(
        val descriptor: EngineDescriptor,
        val source: EngineArchiveSource,
        val archive: ByteArray,
    ) {
        val name: String get() = descriptor.name
    }

    /**
     * Build the layout's archive, register it with the fake fetcher under a
     * test URL, and return the matching descriptor + source pair.
     */
    private fun stageBundle(files: Map<String, ByteArray>): TestBundle {
        val archive = buildArchive(files, ARCHIVE_ROOT)
        val descriptor = pocketDescriptor(archive)
        val source = source("https://test/pocket/bundle.tar.bz2", archive)
        fetcher.payloads[source.url] = archive
        return TestBundle(descriptor, source, archive)
    }

    private fun pocketDescriptor(archive: ByteArray) = EngineDescriptor(
        name = PocketEngineCatalog.ENGINE_NAME,
        displayName = "test",
        downloadSizeBytes = archive.size.toLong().coerceAtLeast(1L),
        installedSizeBytes = (archive.size.toLong() * 2L).coerceAtLeast(2L),
        licenseNotice = "n/a",
    )

    private fun source(
        url: String,
        archive: ByteArray,
        shaOverride: String? = null,
        archiveRoot: String = ARCHIVE_ROOT,
    ) = EngineArchiveSource(
        url = url,
        sha256 = shaOverride ?: sha256Hex(archive),
        sizeBytes = archive.size.toLong().coerceAtLeast(1L),
        archiveRoot = archiveRoot,
    )

    /** Write a complete pocket on-disk layout into [dir] (for verify tests). */
    private fun stagePocketLayout(dir: File) {
        for ((name, bytes) in POCKET_LAYOUT) {
            File(dir, name).apply {
                parentFile?.mkdirs()
                writeBytes(bytes)
            }
        }
    }

    /** Write a complete supertonic on-disk layout into [dir] (for verify tests). */
    private fun stageSupertonicLayout(dir: File) {
        for ((name, bytes) in SUPERTONIC_LAYOUT) {
            File(dir, name).apply {
                parentFile?.mkdirs()
                writeBytes(bytes)
            }
        }
    }

    private companion object {
        // Wrapper directory inside the production bundle tarball; the
        // installer strips it during extraction.
        private const val ARCHIVE_ROOT = "pocket-tts-en/"

        private val BASE_VOICES = listOf("alba", "azelma", "eponine", "fantine", "javert", "marius")

        /**
         * Minimal-but-parseable bundle.json. `PocketBundle.load` requires the
         * scalar fields, `predefined_voices`, `onnx_files`, and both state
         * manifests (empty arrays are valid).
         */
        private val BUNDLE_JSON = """
            {
              "sample_rate": 24000,
              "frame_rate": 12.5,
              "samples_per_frame": 1920,
              "latent_dim": 32,
              "conditioning_dim": 1024,
              "max_token_per_chunk": 50,
              "insert_bos_before_voice": true,
              "predefined_voices": ["alba", "azelma", "eponine", "fantine", "javert", "marius"],
              "onnx_files": {
                "text_conditioner": "text_conditioner.onnx",
                "mimi_encoder": "mimi_encoder.onnx",
                "mimi_decoder": "mimi_decoder.onnx",
                "flow_lm_main": "flow_lm_main_int8.onnx",
                "flow_lm_flow": "flow_lm_flow.onnx"
              },
              "quantization_variant": "mixed-fp32-mimi",
              "flow_lm_state_manifest": [],
              "mimi_state_manifest": []
            }
        """.trimIndent()

        /**
         * Synthetic pocket payload large enough to pass post-install
         * verification: flow_lm_main > 1 MiB (MIN_MODEL_BYTES floor), the
         * other 4 onnx files + tokenizer + npy + bundle.json non-empty, and
         * the 6 base voice WAVs.
         */
        private val POCKET_LAYOUT: Map<String, ByteArray> = buildMap {
            put("bundle.json", BUNDLE_JSON.toByteArray())
            put("tokenizer.model", "sentencepiece".toByteArray())
            put("bos_before_voice.npy", byteArrayOf(0x93.toByte(), 0x4E, 0x55, 0x4D, 0x50, 0x59))
            put("text_conditioner.onnx", "tc".toByteArray())
            put("mimi_encoder.onnx", "me".toByteArray())
            put("mimi_decoder.onnx", "md".toByteArray())
            // 1.5 MB clears the 1 MiB MIN_MODEL_BYTES floor; bytes don't
            // matter — the installer only checks the archive's sha.
            put("flow_lm_main_int8.onnx", ByteArray(1_500_000) { 0x42 })
            put("flow_lm_flow.onnx", "fl".toByteArray())
            for (v in BASE_VOICES) {
                put("voices/$v.wav", tinyWav())
            }
        }

        /** Minimal 44-byte RIFF/WAVE header — verify only needs non-empty files. */
        private fun tinyWav(): ByteArray =
            ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt(36)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(24000)
                putInt(48000)
                putShort(2)
                putShort(16)
                put("data".toByteArray())
                putInt(0)
            }.array()

        // -- supertonic fixtures -----------------------------------------------------

        // Wrapper directory inside the real sherpa tarball; the installer
        // strips it during extraction (same role as pocket's ARCHIVE_ROOT).
        private const val SUPERTONIC_ARCHIVE_ROOT = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11/"

        /**
         * Matches the real int8 bundle's tts.json shape: ae.sample_rate=44100
         * is the V5 gate — the sherpa docs table's 24000 claim is known-wrong.
         */
        private const val SUPERTONIC_TTS_JSON = """
            {"ae":{"sample_rate":44100,"base_chunk_size":512},"ttl":{"chunk_compress_factor":6,"latent_dim":24}}
        """

        /**
         * A `voice.bin` whose 48-byte int64 dims header matches its payload:
         * `48 + n*(ttlD1*ttlD2 + dpD1*dpD2)*4` bytes total. Payload floats are
         * zeros — the layout check only verifies the header/length contract.
         */
        private fun voiceBin(n: Long, ttlD1: Long, ttlD2: Long, dpD1: Long, dpD2: Long): ByteArray {
            val payloadFloats = (n * ttlD1 * ttlD2 + n * dpD1 * dpD2).toInt()
            return ByteBuffer.allocate(48 + payloadFloats * 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    putLong(n)
                    putLong(ttlD1)
                    putLong(ttlD2)
                    putLong(n)
                    putLong(dpD1)
                    putLong(dpD2)
                }
                .array()
        }

        /**
         * Synthetic supertonic payload: the int8 quartet (vector_estimator
         * ≥ 1 MiB for the MIN_MODEL_BYTES floor) + tts.json declaring
         * ae.sample_rate=44100 + a self-consistent voice.bin + an
         * LE-int32 unicode_indexer.bin.
         */
        private val SUPERTONIC_LAYOUT: Map<String, ByteArray> = buildMap {
            put("duration_predictor.int8.onnx", "dp".toByteArray())
            put("text_encoder.int8.onnx", "te".toByteArray())
            put("vector_estimator.int8.onnx", ByteArray(1_500_000) { 0x42 })
            put("vocoder.int8.onnx", "voc".toByteArray())
            put("tts.json", SUPERTONIC_TTS_JSON.trimIndent().toByteArray())
            put(
                "unicode_indexer.bin",
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(32).putInt(64).array(),
            )
            put("voice.bin", voiceBin(n = 2, ttlD1 = 1, ttlD2 = 4, dpD1 = 1, dpD2 = 2))
        }
    }
}

// -- shared test doubles ------------------------------------------------------

/** Native-handle double that just records whether `release()` was called. */
internal class FakeNativeEngineHandle : NativeEngineHandle {
    var released: Boolean = false
        private set

    override fun release() {
        released = true
    }
}

/**
 * In-memory HTTP fetcher. Tests register bytes for a URL; unregistered URLs
 * throw `IOException` (mirroring the 404 path in the real fetcher).
 *
 * Failure knobs:
 *  - [abortAfterBytes]: the next served stream throws mid-read after that
 *    many bytes (one-shot), simulating a network abort.
 *  - [honorRange]: when false, [openRange] returns the full body with
 *    `startedAtBytes = 0`, simulating a server that ignores `Range` (HTTP 200).
 */
internal class FakeHttpFetcher : HttpFetcher {
    val payloads: MutableMap<String, ByteArray> = mutableMapOf()
    val requestedUrls: MutableList<String> = mutableListOf()
    var abortAfterBytes: Long? = null
    var honorRange: Boolean = true

    override fun open(url: String): InputStream {
        requestedUrls += url
        val payload = payloads[url] ?: throw IOException("HTTP 404 fetching $url")
        return maybeAborting(ByteArrayInputStream(payload))
    }

    override fun openRange(url: String, fromBytes: Long): RangeResult {
        requestedUrls += url
        val payload = payloads[url] ?: throw IOException("HTTP 404 fetching $url")
        if (!honorRange || fromBytes <= 0L) {
            return RangeResult(maybeAborting(ByteArrayInputStream(payload)), startedAtBytes = 0L)
        }
        val offset = fromBytes.coerceAtMost(payload.size.toLong()).toInt()
        return RangeResult(
            maybeAborting(ByteArrayInputStream(payload, offset, payload.size - offset)),
            startedAtBytes = fromBytes,
        )
    }

    private fun maybeAborting(stream: InputStream): InputStream {
        val limit = abortAfterBytes ?: return stream
        abortAfterBytes = null // abort once only — the retry/resume must succeed
        return object : FilterInputStream(stream) {
            private var count = 0L

            override fun read(): Int {
                if (count >= limit) throw IOException("simulated network abort")
                val b = super.read()
                if (b >= 0) count++
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (count >= limit) throw IOException("simulated network abort")
                val n = super.read(b, off, minOf(len.toLong(), limit - count).toInt())
                if (n > 0) count += n
                return n
            }
        }
    }
}

/**
 * Build a tar.bz2 archive in-memory from a {entry-name -> bytes} map.
 *
 * If [archiveRootName] is non-empty, every entry name is prefixed with it —
 * mirrors the production bundle's `pocket-tts-en/` wrapper directory. Pass an
 * empty root to hand full control of entry names to the test (e.g. zip-slip).
 */
internal fun buildArchive(
    files: Map<String, ByteArray>,
    archiveRootName: String,
): ByteArray {
    val out = ByteArrayOutputStream()
    BZip2CompressorOutputStream(out).use { bzOut ->
        TarArchiveOutputStream(bzOut).use { tarOut ->
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            for ((name, bytes) in files) {
                val entryName = if (archiveRootName.isNotEmpty()) {
                    archiveRootName + name
                } else {
                    name
                }
                val entry = TarArchiveEntry(entryName)
                entry.size = bytes.size.toLong()
                tarOut.putArchiveEntry(entry)
                tarOut.write(bytes)
                tarOut.closeArchiveEntry()
            }
            tarOut.finish()
        }
    }
    return out.toByteArray()
}

internal fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
        val v = b.toInt() and 0xFF
        sb.append("0123456789abcdef"[v ushr 4])
        sb.append("0123456789abcdef"[v and 0x0F])
    }
    return sb.toString()
}
