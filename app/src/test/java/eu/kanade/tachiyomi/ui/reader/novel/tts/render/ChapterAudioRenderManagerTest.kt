package eu.kanade.tachiyomi.ui.reader.novel.tts.render

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadProvider
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.coAnswers
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter

/**
 * JVM unit tests for [ChapterAudioRenderManager] — the in-memory render
 * queue (plan 05-02, Task 1; D-10/D-11/D-13/D-14, SD-2).
 *
 * The Android/engine-facing seams are injected: `rendererFactory` supplies a
 * mockk [ChapterAudioRenderer] stub whose `renderChapter` records its
 * `resumeFromParagraph` argument and whose behavior is scriptable per
 * chapter id (gate / throw / progress), `scratchDir` is a temp dir, the
 * `serviceController` lambda records FGS start/stop transitions without a
 * Context, and the queue runs on a [StandardTestDispatcher] so
 * `runCurrent`/`advanceUntilIdle` step it deterministically.
 *
 * Covered contract (05-02 must-haves):
 *  - enqueue -> QUEUED -> RENDERING -> DONE; queue drains -> service stop +
 *    engine release
 *  - a second enqueue sits QUEUED while one render runs and starts
 *    automatically on drain of the head item (D-13)
 *  - startRenderNow (D-14): running item's job cancelled, item reinserted
 *    QUEUED at head with `completedParagraphs` retained (scratch kept — no
 *    `abandon`), tapped item renders first, preempted item then RESUMES at
 *    `resumeFromParagraph = completedParagraphs`
 *  - cancelRender (D-11): running or queued item removed AND
 *    `abandon(chapterId)` invoked — the partial render is destroyed
 *  - renderer failure -> item ERROR, scratch retained (no abandon), siblings
 *    unaffected (SupervisorJob); re-enqueue resumes at the checkpoint
 *  - first queue access sweeps stale scratch files (D-10 process-death
 *    cleanup)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChapterAudioRenderManagerTest {

    private val novel = Novel.create().copy(id = 7L, title = "Test Novel")
    private val source = mockk<NovelSource>(relaxed = true)

    private fun chapter(id: Long, name: String = "Chapter $id") =
        NovelChapter.create().copy(id = id, novelId = novel.id, name = name)

    private fun manifest(): ChapterAudioManifest = ChapterAudioManifest(
        voice = "eponine",
        engine = "pocket-tts-en-v2026_04",
        chapterTitle = "t",
        speechRate = 1.0f,
        pitch = 1.0f,
        durationMs = 0,
        paragraphOffsetsMs = emptyList(),
        paragraphTexts = emptyList(),
        text = "",
    )

    private fun TestScope.makeManager(
        renderer: ChapterAudioRenderer,
        scratchDir: File = createTempDirectory().toFile(),
        serviceEvents: MutableList<Boolean> = mutableListOf(),
        provider: NovelDownloadProvider = mockk(relaxed = true),
    ): ChapterAudioRenderManager = ChapterAudioRenderManager(
        context = mockk<Context>(relaxed = true),
        rendererFactory = { renderer },
        provider = provider,
        preferences = NovelTtsPreferences(FakePreferenceStore()),
        dispatcher = StandardTestDispatcher(testScheduler),
        scratchDir = scratchDir,
        serviceController = { started -> serviceEvents += started },
    )

    // -- happy path -----------------------------------------------------------

    @Test
    fun `enqueue renders the chapter then drains the queue`() = runTest {
        val renderer = FakeRenderer()
        val serviceEvents = mutableListOf<Boolean>()
        val manager = makeManager(renderer.instance, serviceEvents = serviceEvents)

        manager.enqueueRender(novel, chapter(1), source)
        testScheduler.advanceUntilIdle()

        renderer.calls shouldBe listOf(FakeRenderer.Call(1L, 0))
        manager.queueState.value.shouldBeEmpty()
        // Service starts on first enqueue and stops when the queue drains;
        // the engine is released with it.
        serviceEvents shouldBe listOf(true, false)
        renderer.releaseCalls shouldBe 1
    }

    @Test
    fun `second enqueue waits QUEUED then starts automatically`() = runTest {
        val gateA = CompletableDeferred<Unit>()
        val renderer = FakeRenderer().apply {
            impl = { id, _ -> if (id == 1L) gateA.await() }
        }
        val manager = makeManager(renderer.instance)

        manager.enqueueRender(novel, chapter(1), source)
        manager.enqueueRender(novel, chapter(2), source)
        testScheduler.runCurrent()

        val queue = manager.queueState.value
        queue.map { it.chapter.id } shouldBe listOf(1L, 2L)
        queue[0].status shouldBe ChapterRender.State.RENDERING
        queue[1].status shouldBe ChapterRender.State.QUEUED

        gateA.complete(Unit)
        testScheduler.advanceUntilIdle()

        renderer.calls shouldBe listOf(FakeRenderer.Call(1L, 0), FakeRenderer.Call(2L, 0))
        manager.queueState.value.shouldBeEmpty()
    }

    @Test
    fun `enqueueRender ignores a chapter that is already queued`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val renderer = FakeRenderer().apply { impl = { _, _ -> gate.await() } }
        val manager = makeManager(renderer.instance)

        manager.enqueueRender(novel, chapter(1), source)
        manager.enqueueRender(novel, chapter(1), source)
        testScheduler.runCurrent()

        manager.queueState.value.size shouldBe 1
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        renderer.calls.size shouldBe 1
    }

    @Test
    fun `enqueueRender ignores a chapter that already has rendered audio`() = runTest {
        val provider = mockk<NovelDownloadProvider>(relaxed = true)
        every { provider.findAudioFile("Chapter 1", null, "Test Novel", source) } returns mockk<UniFile>()
        val renderer = FakeRenderer()
        val serviceEvents = mutableListOf<Boolean>()
        val manager = makeManager(renderer.instance, provider = provider, serviceEvents = serviceEvents)

        manager.enqueueRender(novel, chapter(1), source)
        testScheduler.advanceUntilIdle()

        renderer.calls.shouldBeEmpty()
        manager.queueState.value.shouldBeEmpty()
        serviceEvents.shouldBeEmpty()
    }

    // -- preempt (D-14, SD-2) ---------------------------------------------------

    @Test
    fun `startRenderNow preempts the running render and resumes it at its checkpoint`() = runTest {
        val gateA = CompletableDeferred<Unit>()
        var aCallCount = 0
        val renderer = FakeRenderer().apply {
            impl = { id, onProgress ->
                if (id == 1L && aCallCount == 0) {
                    aCallCount++
                    onProgress(3, 10) // three paragraphs checkpointed
                    gateA.await()
                }
            }
        }
        val manager = makeManager(renderer.instance)
        val chA = chapter(1)
        val chB = chapter(2)

        manager.enqueueRender(novel, chA, source)
        manager.enqueueRender(novel, chB, source)
        testScheduler.runCurrent()

        manager.startRenderNow(chB.id)
        testScheduler.advanceUntilIdle()

        // A was cancelled mid-render and reinserted QUEUED behind B; after B
        // finished, A resumed at paragraph 3 — its scratch was retained.
        renderer.calls shouldBe listOf(
            FakeRenderer.Call(1L, 0),
            FakeRenderer.Call(2L, 0),
            FakeRenderer.Call(1L, 3),
        )
        renderer.abandoned.shouldBeEmpty() // preempt never abandons scratch
        manager.queueState.value.shouldBeEmpty()
    }

    @Test
    fun `startRenderNow on the running chapter is a no-op`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val renderer = FakeRenderer().apply { impl = { _, _ -> gate.await() } }
        val manager = makeManager(renderer.instance)

        manager.enqueueRender(novel, chapter(1), source)
        testScheduler.runCurrent()
        manager.startRenderNow(1L)
        testScheduler.runCurrent()

        renderer.calls.size shouldBe 1 // still the first render
        manager.queueState.value.single().status shouldBe ChapterRender.State.RENDERING
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
    }

    // -- cancel (D-11) ----------------------------------------------------------

    @Test
    fun `cancelRender abandons a running render and lets the queue continue`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val renderer = FakeRenderer().apply {
            impl = { id, _ -> if (id == 1L) gate.await() }
        }
        val manager = makeManager(renderer.instance)

        manager.enqueueRender(novel, chapter(1), source)
        manager.enqueueRender(novel, chapter(2), source)
        testScheduler.runCurrent()

        manager.cancelRender(1L)
        testScheduler.advanceUntilIdle()

        renderer.abandoned shouldContain 1L
        // B rendered after A's cancel; the queue fully drained.
        renderer.calls.map { it.chapterId } shouldBe listOf(1L, 2L)
        manager.queueState.value.shouldBeEmpty()
    }

    @Test
    fun `cancelRender on a queued item removes it and sweeps retained scratch`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val renderer = FakeRenderer().apply {
            impl = { id, _ -> if (id == 1L) gate.await() }
        }
        val manager = makeManager(renderer.instance)

        manager.enqueueRender(novel, chapter(1), source)
        manager.enqueueRender(novel, chapter(2), source)
        testScheduler.runCurrent()

        manager.cancelRender(2L)
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        // B never rendered; abandon still ran to sweep any scratch a prior
        // preempt may have left behind.
        renderer.calls.map { it.chapterId } shouldBe listOf(1L)
        renderer.abandoned shouldContain 2L
        manager.queueState.value.shouldBeEmpty()
    }

    // -- error + retry ------------------------------------------------------------

    @Test
    fun `renderer failure marks the item ERROR, keeps siblings running, and re-enqueue resumes`() = runTest {
        var fail = true
        val renderer = FakeRenderer().apply {
            impl = { id, onProgress ->
                if (id == 1L) {
                    onProgress(2, 5)
                    if (fail) {
                        fail = false
                        throw RenderException("boom")
                    }
                }
            }
        }
        val manager = makeManager(renderer.instance)

        manager.enqueueRender(novel, chapter(1), source)
        manager.enqueueRender(novel, chapter(2), source)
        testScheduler.advanceUntilIdle()

        // SupervisorJob: A's failure did not cancel B.
        val errored = manager.queueState.value.single()
        errored.chapter.id shouldBe 1L
        errored.status shouldBe ChapterRender.State.ERROR
        renderer.abandoned.shouldBeEmpty() // scratch retained for retry
        renderer.calls.map { it.chapterId } shouldBe listOf(1L, 2L)

        // Re-enqueue resumes the checkpointed render (completedParagraphs=2).
        manager.enqueueRender(novel, chapter(1), source)
        testScheduler.advanceUntilIdle()

        renderer.calls.last() shouldBe FakeRenderer.Call(1L, 2)
        manager.queueState.value.shouldBeEmpty()
    }

    // -- scratch sweep + state queries ---------------------------------------------

    @Test
    fun `first queue access sweeps stale scratch files`() = runTest {
        val scratchDir = createTempDirectory().toFile()
        File(scratchDir, "99.pcm").writeBytes(byteArrayOf(1, 2))
        File(scratchDir, "99.idx").writeBytes(ByteArray(8))
        File(scratchDir, "stray.tmp").writeBytes(byteArrayOf(0))

        val manager = makeManager(FakeRenderer().instance, scratchDir = scratchDir)
        manager.audioStateFor(chapter(9), novel, source)

        scratchDir.listFiles().orEmpty().shouldBeEmpty()
    }

    @Test
    fun `audioStateFor reports queue status then disk DONE then null`() = runTest {
        val provider = mockk<NovelDownloadProvider>(relaxed = true)
        val gate = CompletableDeferred<Unit>()
        val renderer = FakeRenderer().apply { impl = { _, _ -> gate.await() } }
        val manager = makeManager(renderer.instance, provider = provider)

        manager.enqueueRender(novel, chapter(1), source)
        testScheduler.runCurrent()
        manager.audioStateFor(chapter(1), novel, source) shouldBe ChapterRender.State.RENDERING

        every { provider.findAudioFile("Chapter 5", null, "Test Novel", source) } returns mockk<UniFile>()
        manager.audioStateFor(chapter(5), novel, source) shouldBe ChapterRender.State.DONE
        manager.audioStateFor(chapter(6), novel, source) shouldBe null

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
    }

    // -- fakes ----------------------------------------------------------------------

    private fun chapterManifestFor(@Suppress("UNUSED_PARAMETER") id: Long) = manifest()

    /**
     * Stubbed [ChapterAudioRenderer]: `renderChapter` records
     * (chapterId, resumeFromParagraph) then delegates to [impl] so tests can
     * gate, throw, or report progress; `abandon`/`releaseEngine` are counted.
     */
    private inner class FakeRenderer {
        data class Call(val chapterId: Long, val resumeFrom: Int)

        val calls = mutableListOf<Call>()
        val abandoned = mutableListOf<Long>()
        var releaseCalls = 0

        /** Suspends/throws/reports per chapter id; completes the render on return. */
        var impl: suspend (chapterId: Long, onProgress: (Int, Int) -> Unit) -> Unit = { _, _ -> }

        val instance: ChapterAudioRenderer = mockk<ChapterAudioRenderer>().apply {
            coEvery { renderChapter(any(), any(), any(), any(), any()) } coAnswers {
                val ch = arg<NovelChapter>(1)
                val resume = arg<Int>(3)
                val onProgress = arg<(Int, Int) -> Unit>(4)
                calls += Call(ch.id, resume)
                impl(ch.id, onProgress)
                manifest()
            }
            every { abandon(any()) } answers { abandoned += arg(0) }
            every { releaseEngine() } answers { releaseCalls++ }
        }
    }

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
