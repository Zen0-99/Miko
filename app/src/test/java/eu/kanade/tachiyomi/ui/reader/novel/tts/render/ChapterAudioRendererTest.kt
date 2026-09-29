package eu.kanade.tachiyomi.ui.reader.novel.tts.render

import android.content.Context
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadProvider
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.PocketVoiceCatalog
import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.PocketEngine
import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.SynthAudio
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.EngineProfiles
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile

/**
 * JVM unit tests for [ChapterAudioRenderer] — the end-to-end render slice
 * (plan 05-01, Task 2 tracer).
 *
 * The Android-facing seams are injected: `profileFactory` supplies an
 * [EngineRenderProfile] whose `streamFactory` lambda emits fake
 * `Flow<SynthAudio>` per paragraph, `sinkFactory` supplies a recording fake
 * that runs the manifest through the REAL [Mp4AtomAppender] onto a temp
 * file, `chapterTextLoader` returns fixture HTML, `scratchDir` is a temp
 * dir, and the profile's `ensureLoaded` is a no-op (never loads ORT).
 * Everything else —
 * the real [eu.kanade.tachiyomi.ui.reader.novel.NovelParagraphSegmenter],
 * offset math, PCM scratch checkpointing, the rate/pitch bake chain, and the
 * manifest codec — is production code.
 *
 * Covered contract (05-01 must-haves):
 *  - paragraphs segmented identically to the reader's blank-filtered list
 *  - per-paragraph PCM16 scratch append; `paragraphOffsetsMs[i]` = cumulative
 *    shaped samples before paragraph i × 1000 / 24000
 *  - speechRate/pitch baked via shapeChunk→resampleLinear→PitchResampler and
 *    recorded in the manifest (SD-1)
 *  - finish() commits via the sink; manifest atom appended + readable
 *  - cancel mid-render → sink.abort(), scratch retained, CancellationException
 *    rethrown; abandon() deletes the scratch (D-10/D-11/SD-2)
 *  - resumeFromParagraph=N skips synthesis for paragraphs < N, preserves the
 *    existing scratch bytes, and keeps paragraph offsets intact
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChapterAudioRendererTest {

    private fun chunk(size: Int, fill: Int = 7, sampleRate: Int = 24000): SynthAudio =
        SynthAudio(ShortArray(size) { (it * fill % 1000).toShort() }, sampleRate = sampleRate)

    private val novel = Novel.create().copy(id = 7L, title = "Test Novel")
    private val chapter = NovelChapter.create().copy(id = 42L, novelId = 7L, name = "Chapter 1")
    private val source = mockk<NovelSource>(relaxed = true)

    /** Fake MP4 prefix the recording sink writes before the manifest atom. */
    private fun fakeMp4Prefix(): ByteArray {
        fun atom(fourcc: String, payload: ByteArray): ByteArray {
            val buf = ByteBuffer.allocate(8 + payload.size)
            buf.putInt(8 + payload.size)
            buf.put(fourcc.toByteArray(Charsets.US_ASCII))
            buf.put(payload)
            return buf.array()
        }
        return atom("ftyp", "M4A ".toByteArray()) + atom("mdat", ByteArray(16)) +
            atom("moov", ByteArray(8))
    }

    /**
     * Recording [ChapterAudioSink]: captures PCM chunks, and on [finish]
     * writes a fake moov-last MP4 to [outFile] then appends the manifest JSON
     * through the real [Mp4AtomAppender] — the same contract [SafM4aSink]
     * fulfills on device.
     */
    private class RecordingSink(private val outFile: File, private val mp4Prefix: ByteArray) : ChapterAudioSink {
        val written = mutableListOf<ShortArray>()
        var finishedJson: String? = null
        var abortCalls = 0
        var writeGate: CompletableDeferred<Unit>? = null

        override suspend fun write(chunk: ShortArray) {
            writeGate?.await()
            written += chunk
        }

        override suspend fun finish(manifestJson: String) {
            finishedJson = manifestJson
            outFile.writeBytes(mp4Prefix)
            Mp4AtomAppender.append(outFile, manifestJson.toByteArray(Charsets.UTF_8))
        }

        override suspend fun abort() {
            abortCalls++
        }
    }

    private fun readAtomJson(file: File): String? =
        FileChannel.open(file.toPath(), StandardOpenOption.READ).use { Mp4AtomReader.read(it) }

    private fun contextWithVoices(vararg voiceNames: String): Context {
        val dir = createTempDirectory().toFile()
        val voicesDir = File(dir, "engines/pocket-tts-en-v2026_04/voices")
        voicesDir.mkdirs()
        voiceNames.forEach { File(voicesDir, "$it.wav").writeBytes(byteArrayOf(0)) }
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns dir
        return context
    }

    /**
     * Fake [EngineRenderProfile] over lambdas — pocket-shaped by default
     * (24000 Hz, `engine` = the real Pocket engine name, whole rate baked),
     * with the REAL [PocketVoiceCatalog.resolveInstalledVoice] running over
     * the stubbed `filesDir` so voice resolution stays honest.
     */
    private fun fakeProfile(
        context: Context,
        streamFactory: (text: String, voiceId: String, speed: Float) -> Flow<SynthAudio>,
        engineName: String = PocketEngine.ENGINE_NAME,
        sampleRate: Int = 24000,
        nativeSpeedFor: (rate: Float) -> Float = { 1f },
        bakeRateFor: (rate: Float) -> Float = { it },
        resolveVoice: (requested: String) -> String =
            { PocketVoiceCatalog.resolveInstalledVoice(it, context.filesDir) },
        preprocessingRules: Set<String> = EngineProfiles.defaultsFor(PocketEngine.ENGINE_NAME),
        onEnsureLoaded: () -> Unit = {},
        onRelease: () -> Unit = {},
    ): EngineRenderProfile = EngineRenderProfile(
        engineName = engineName,
        sampleRate = sampleRate,
        nativeSpeedFor = nativeSpeedFor,
        bakeRateFor = bakeRateFor,
        ensureLoaded = { onEnsureLoaded() },
        releaseEngine = onRelease,
        resolveVoice = resolveVoice,
        preprocessingRules = preprocessingRules,
        streamFactory = streamFactory,
    )

    /** Supertonic-shaped fake profile (D-07): 44.1 kHz, native speed 0.5..2, residue baked. */
    private fun supertonicProfile(
        context: Context,
        onRelease: () -> Unit = {},
        streamFactory: (text: String, voiceId: String, speed: Float) -> Flow<SynthAudio>,
    ): EngineRenderProfile = fakeProfile(
        context = context,
        streamFactory = streamFactory,
        engineName = "supertonic-3-int8",
        sampleRate = 44100,
        nativeSpeedFor = { it.coerceIn(0.5f, 2f) },
        bakeRateFor = { rate -> rate / rate.coerceIn(0.5f, 2f) },
        resolveVoice = { "supertonic:F5" },
        onRelease = onRelease,
    )

    private fun makeRenderer(
        scratchDir: File,
        sink: RecordingSink,
        html: String,
        streamFactory: (text: String, voiceId: String, speed: Float) -> Flow<SynthAudio>,
        preferences: NovelTtsPreferences = NovelTtsPreferences(FakePreferenceStore()),
        context: Context = contextWithVoices("eponine"),
        profile: EngineRenderProfile? = null,
    ): ChapterAudioRenderer = ChapterAudioRenderer(
        context = context,
        provider = mockk<NovelDownloadProvider>(relaxed = true).also {
            every { it.getAudioFileName("Chapter 1", null) } returns "Chapter 1.m4a"
        },
        preferences = preferences,
        profileFactory = { profile ?: fakeProfile(context, streamFactory) },
        sinkFactory = { _, _, _ -> sink },
        chapterTextLoader = { _, _, _ -> html },
        scratchDir = scratchDir,
    )

    private fun scratchFile(scratchDir: File) = File(scratchDir, "${chapter.id}.pcm")

    // -- happy path -------------------------------------------------------------

    @Test
    fun `render writes per-paragraph PCM, computes offsets, and embeds a readable manifest`() = runTest {
        val scratchDir = createTempDirectory().toFile()
        val outFile = createTempFile(suffix = ".m4a").toFile()
        val sink = RecordingSink(outFile, fakeMp4Prefix())
        val calls = mutableListOf<Pair<String, String>>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Alpha.</p><p>Beta.</p>",
            streamFactory = { text, voice, _ ->
                calls += text to voice
                if (text.contains("Alpha")) flowOf(chunk(480), chunk(480)) else flowOf(chunk(240))
            },
        )

        val manifest = renderer.renderChapter(novel, chapter, source) { c, t -> progress += c to t }

        // Two stream calls, resolved voice.
        calls.size shouldBe 2
        calls.all { it.second == "eponine" } shouldBe true

        // 960 samples before paragraph 2 → 40000 ms at 24 kHz.
        manifest.paragraphOffsetsMs shouldBe listOf(0L, 960L * 1000L / 24000L)
        manifest.paragraphTexts shouldBe listOf("Alpha.", "Beta.")
        manifest.text shouldBe "Alpha.\n\nBeta."
        manifest.voice shouldBe "eponine"
        manifest.engine shouldBe "pocket-tts-en-v2026_04"
        manifest.sampleRate shouldBe 24000
        manifest.speechRate shouldBe 1.0f
        manifest.durationMs shouldBe 1200L * 1000L / 24000L

        // Sink received all 1200 shaped samples; finish ran once; no abort.
        sink.written.sumOf { it.size } shouldBe 1200
        sink.finishedJson.shouldNotBeNull()
        sink.abortCalls shouldBe 0

        // The atom on the produced file decodes to the same manifest.
        decodeManifest(readAtomJson(outFile)!!) shouldBe manifest

        // Scratch + index cleaned up after a successful render.
        scratchFile(scratchDir).exists() shouldBe false
        File(scratchDir, "${chapter.id}.idx").exists() shouldBe false

        progress shouldBe listOf(1 to 2, 2 to 2)
    }

    @Test
    fun `speech rate 2 halves the rendered PCM and is recorded in the manifest`() = runTest {
        val prefs = NovelTtsPreferences(FakePreferenceStore())
        prefs.speechRate().set(2.0f)
        val scratchDir = createTempDirectory().toFile()
        val sink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Fast.</p>",
            streamFactory = { _, _, _ -> flowOf(chunk(480)) },
            preferences = prefs,
        )

        val manifest = renderer.renderChapter(novel, chapter, source)

        sink.written.sumOf { it.size } shouldBe 240 // resampleLinear halves
        manifest.speechRate shouldBe 2.0f
    }

    // -- cancellation / resume (D-10, SD-2) --------------------------------------

    @Test
    fun `cancel during synthesis retains the scratch checkpoint and rethrows`() = runTest {
        val scratchDir = createTempDirectory().toFile()
        val sink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        val gate = CompletableDeferred<Unit>()
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Alpha.</p><p>Beta.</p>",
            streamFactory = { text, _, _ ->
                if (text.contains("Alpha")) {
                    flowOf(chunk(480))
                } else {
                    flow {
                        gate.await()
                        emit(chunk(10))
                    }
                }
            },
        )

        val job = async { renderer.renderChapter(novel, chapter, source) }
        testScheduler.runCurrent()
        job.cancelAndJoin()

        job.isCancelled shouldBe true
        scratchFile(scratchDir).length() shouldBe 480L * 2 // paragraph 1's PCM retained
        sink.abortCalls shouldBe 0 // sink never created during the synth phase

        // abandon() removes the checkpoint outright (D-11 cancel semantics).
        renderer.abandon(chapter.id)
        scratchFile(scratchDir).exists() shouldBe false
        File(scratchDir, "${chapter.id}.idx").exists() shouldBe false
    }

    @Test
    fun `cancel during encode aborts the sink and retains the scratch`() = runTest {
        val scratchDir = createTempDirectory().toFile()
        val sink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        sink.writeGate = CompletableDeferred()
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Alpha.</p>",
            streamFactory = { _, _, _ -> flowOf(chunk(480)) },
        )

        val job = async { renderer.renderChapter(novel, chapter, source) }
        testScheduler.runCurrent()
        job.cancelAndJoin()

        sink.abortCalls shouldBe 1
        sink.finishedJson shouldBe null
        scratchFile(scratchDir).exists() shouldBe true
    }

    @Test
    fun `resumeFromParagraph skips earlier synthesis and preserves offsets`() = runTest {
        val scratchDir = createTempDirectory().toFile()

        // Stage a checkpoint: render paragraph 1, cancel inside paragraph 2.
        val gate = CompletableDeferred<Unit>()
        val firstSink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        val first = makeRenderer(
            scratchDir = scratchDir,
            sink = firstSink,
            html = "<p>Alpha.</p><p>Beta.</p>",
            streamFactory = { text, _, _ ->
                if (text.contains("Alpha")) flowOf(chunk(480)) else flow { gate.await() }
            },
        )
        val job = async { first.renderChapter(novel, chapter, source) }
        testScheduler.runCurrent()
        job.cancelAndJoin()
        scratchFile(scratchDir).length() shouldBe 480L * 2

        // Resume — a fresh renderer instance over the same scratchDir.
        val calls = mutableListOf<Pair<String, String>>()
        val outFile = createTempFile(suffix = ".m4a").toFile()
        val secondSink = RecordingSink(outFile, fakeMp4Prefix())
        val second = makeRenderer(
            scratchDir = scratchDir,
            sink = secondSink,
            html = "<p>Alpha.</p><p>Beta.</p>",
            streamFactory = { text, voice, _ ->
                calls += text to voice
                flowOf(chunk(240))
            },
        )
        val manifest = second.renderChapter(novel, chapter, source, resumeFromParagraph = 1)

        calls.size shouldBe 1
        calls[0].first.contains("Beta") shouldBe true
        // Paragraph 1 offset preserved from the checkpoint (480 samples → 20ms).
        manifest.paragraphOffsetsMs shouldBe listOf(0L, 480L * 1000L / 24000L)
        manifest.durationMs shouldBe (480 + 240) * 1000L / 24000L
        decodeManifest(readAtomJson(outFile)!!)!!.paragraphOffsetsMs shouldBe
            listOf(0L, 480L * 1000L / 24000L)
        scratchFile(scratchDir).exists() shouldBe false
    }

    @Test
    fun `resume without a scratch checkpoint restarts from zero`() = runTest {
        val scratchDir = createTempDirectory().toFile()
        val sink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        val calls = mutableListOf<String>()
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Alpha.</p><p>Beta.</p>",
            streamFactory = { text, _, _ ->
                calls += text
                flowOf(chunk(120))
            },
        )

        renderer.renderChapter(novel, chapter, source, resumeFromParagraph = 1)

        calls.size shouldBe 2 // both paragraphs re-synthesized
    }

    @Test
    fun `chapter with no speakable paragraphs fails without touching the sink`() = runTest {
        val scratchDir = createTempDirectory().toFile()
        val sink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        var sinkCreated = false
        val context = contextWithVoices("eponine")
        val renderer = ChapterAudioRenderer(
            context = context,
            provider = mockk<NovelDownloadProvider>(relaxed = true),
            preferences = NovelTtsPreferences(FakePreferenceStore()),
            profileFactory = { fakeProfile(context, { _, _, _ -> flowOf(chunk(10)) }) },
            sinkFactory = { _, _, _ ->
                sinkCreated = true
                sink
            },
            chapterTextLoader = { _, _, _ -> "<p>   </p><p></p>" },
            scratchDir = scratchDir,
        )

        shouldThrow<RenderException> {
            renderer.renderChapter(novel, chapter, source)
        }
        sinkCreated shouldBe false
    }

    // -- engine profile seam (05B-03, D-07) ---------------------------------------

    @Test
    fun `supertonic profile render writes 44100 manifest and engine name`() = runTest {
        val scratchDir = createTempDirectory().toFile()
        val outFile = createTempFile(suffix = ".m4a").toFile()
        val sink = RecordingSink(outFile, fakeMp4Prefix())
        val context = contextWithVoices("eponine")
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Alpha.</p><p>Beta.</p>",
            streamFactory = { _, _, _ -> flowOf() },
            context = context,
            profile = supertonicProfile(context) { _, _, _ ->
                flowOf(chunk(441, sampleRate = 44100))
            },
        )

        val manifest = renderer.renderChapter(novel, chapter, source)

        manifest.engine shouldBe "supertonic-3-int8"
        manifest.sampleRate shouldBe 44100
        manifest.voice shouldBe "supertonic:F5"
        // 2 paragraphs × 441 samples → offsets + duration at 44.1 kHz.
        manifest.paragraphOffsetsMs shouldBe listOf(0L, 441L * 1000L / 44100L)
        manifest.durationMs shouldBe 882L * 1000L / 44100L
        sink.written.sumOf { it.size } shouldBe 882
        decodeManifest(readAtomJson(outFile)!!) shouldBe manifest
    }

    @Test
    fun `supertonic rate 2 rides native speed and skips the resample bake`() = runTest {
        val prefs = NovelTtsPreferences(FakePreferenceStore())
        prefs.speechRate().set(2.0f)
        val scratchDir = createTempDirectory().toFile()
        val sink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        val context = contextWithVoices("eponine")
        val speeds = mutableListOf<Float>()
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Fast.</p>",
            streamFactory = { _, _, _ -> flowOf() },
            preferences = prefs,
            context = context,
            profile = supertonicProfile(context) { _, _, speed ->
                speeds += speed
                flowOf(chunk(441, sampleRate = 44100))
            },
        )

        renderer.renderChapter(novel, chapter, source)

        // rate 2 is inside native range 0.5..2 → native=2, bake=1.
        speeds shouldBe listOf(2.0f)
        sink.written.sumOf { it.size } shouldBe 441 // PCM passthrough, no resample
    }

    @Test
    fun `supertonic rate 3 clamps native speed and bakes the residue`() = runTest {
        val prefs = NovelTtsPreferences(FakePreferenceStore())
        prefs.speechRate().set(3.0f)
        val scratchDir = createTempDirectory().toFile()
        val sink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        val context = contextWithVoices("eponine")
        val speeds = mutableListOf<Float>()
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Faster.</p>",
            streamFactory = { _, _, _ -> flowOf() },
            preferences = prefs,
            context = context,
            profile = supertonicProfile(context) { _, _, speed ->
                speeds += speed
                flowOf(chunk(480, sampleRate = 44100))
            },
        )

        renderer.renderChapter(novel, chapter, source)

        // rate 3 exceeds native range → native=2, bake=3/2=1.5.
        speeds shouldBe listOf(2.0f)
        sink.written.sumOf { it.size } shouldBe 320 // floor(480 / 1.5)
    }

    @Test
    fun `forEngine maps supertonic to the supertonic profile and anything else to pocket`() {
        val context = contextWithVoices("eponine")
        val prefs = NovelTtsPreferences(FakePreferenceStore())

        val supertonic = RenderProfiles.forEngine("supertonic", context, prefs)
        supertonic.engineName shouldBe "supertonic-3-int8"
        supertonic.sampleRate shouldBe 44100
        supertonic.nativeSpeedFor(3f) shouldBe 2f
        supertonic.bakeRateFor(3f) shouldBe 1.5f
        supertonic.bakeRateFor(2f) shouldBe 1.0f

        // PB-15: "pocket", "android", "neural", unknown → pocket profile.
        for (engineType in listOf("pocket", "android", "neural", "bogus")) {
            val pocket = RenderProfiles.forEngine(engineType, context, prefs)
            pocket.engineName shouldBe PocketEngine.ENGINE_NAME
            pocket.sampleRate shouldBe 24000
            pocket.nativeSpeedFor(3f) shouldBe 1f
            pocket.bakeRateFor(3f) shouldBe 3f
        }
    }

    @Test
    fun `releaseEngine releases the most recent render's profile engine`() = runTest {
        val scratchDir = createTempDirectory().toFile()
        val sink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        val context = contextWithVoices("eponine")
        var releases = 0
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Alpha.</p>",
            streamFactory = { _, _, _ -> flowOf() },
            context = context,
            profile = supertonicProfile(context, onRelease = { releases++ }) { _, _, _ ->
                flowOf(chunk(441, sampleRate = 44100))
            },
        )

        // Before any render there is no last profile — release is a no-op.
        val pristine = ChapterAudioRenderer(
            context = context,
            provider = mockk<NovelDownloadProvider>(relaxed = true),
            preferences = NovelTtsPreferences(FakePreferenceStore()),
            profileFactory = { fakeProfile(context, { _, _, _ -> flowOf() }, onRelease = { releases++ }) },
            sinkFactory = { _, _, _ -> sink },
            chapterTextLoader = { _, _, _ -> "<p>x</p>" },
            scratchDir = scratchDir,
        )
        pristine.releaseEngine()
        releases shouldBe 0

        renderer.renderChapter(novel, chapter, source)
        renderer.releaseEngine()
        releases shouldBe 1
    }

    // -- fakes -------------------------------------------------------------------

    private class FakePreference<T>(initial: T) : Preference<T> {
        private val state = MutableStateFlow(initial)

        override fun key(): String = "fake"
        override fun get(): T = state.value
        override fun set(value: T) {
            state.value = value
        }

        override fun isSet(): Boolean = true
        override fun delete() = Unit
        override fun defaultValue(): T = state.value
        override fun changes(): Flow<T> = state
        override fun stateIn(scope: CoroutineScope): StateFlow<T> = state
    }

    private class FakePreferenceStore : PreferenceStore {
        private val prefs = mutableMapOf<String, Preference<*>>()

        @Suppress("UNCHECKED_CAST")
        private fun <T> pref(key: String, defaultValue: T): Preference<T> =
            prefs.getOrPut(key) { FakePreference(defaultValue) } as Preference<T>

        override fun getString(key: String, defaultValue: String): Preference<String> =
            pref(key, defaultValue)

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            pref(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            pref(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            pref(key, defaultValue)

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            pref(key, defaultValue)

        override fun getStringSet(
            key: String,
            defaultValue: Set<String>,
        ): Preference<Set<String>> = pref(key, defaultValue)

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = pref(key, defaultValue)

        override fun getAll(): Map<String, *> = emptyMap<String, Any>()
    }
}
