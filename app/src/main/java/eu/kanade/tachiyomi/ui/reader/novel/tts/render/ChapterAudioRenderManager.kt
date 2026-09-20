package eu.kanade.tachiyomi.ui.reader.novel.tts.render

import android.content.Context
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadProvider
import eu.kanade.tachiyomi.data.download.novel.NovelDownloader
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * In-memory chapter-audio render queue (plan 05-02): the
 * `NovelDownloader`/`NovelDownloadManager` analog minus persistence — D-10
 * means nothing survives process death (stale `cacheDir/chapter_audio` PCM
 * scratch is swept on first queue access instead).
 *
 * One render runs at a time; queued items are observable via [queueState]
 * and each item's `statusFlow`/`progressFlow` (D-13). Manual preempt
 * ([startRenderNow], D-14) cancels the running render, reinserts it QUEUED
 * with its PCM-scratch checkpoint retained, and starts the tapped item —
 * which is *different* from user cancel ([cancelRender], D-11) only in that
 * the scratch survives (SD-2).
 *
 * The FGS owns zero queue state: [serviceController] starts the service when
 * work is enqueued and stops it — along with the renderer's engine — when no
 * QUEUED/RENDERING work remains (T-05-02-02: the service must not linger).
 */
class ChapterAudioRenderManager(
    private val context: Context,
    internal val rendererFactory: () -> ChapterAudioRenderer = { ChapterAudioRenderer(context) },
    private val provider: NovelDownloadProvider = Injekt.get(),
    private val preferences: NovelTtsPreferences = Injekt.get(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    internal val scratchDir: File = File(context.cacheDir, "chapter_audio"),
    // FGS start/stop seam — NovelAudioRenderService in production; tests
    // inject a recorder so no Context is needed.
    internal val serviceController: (Boolean) -> Unit = { started ->
        if (started) {
            NovelAudioRenderService.start(context)
        } else {
            NovelAudioRenderService.stop(context)
        }
    },
) {

    private val _queueState = MutableStateFlow<List<ChapterRender>>(emptyList())
    val queueState = _queueState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var renderJob: Job? = null

    // Single renderer for the session — scratch continuity lives in
    // scratchDir, not the instance, but one instance keeps the engine warm.
    private var rendererOrNull: ChapterAudioRenderer? = null

    @Volatile
    private var scratchSwept = false

    private fun renderer(): ChapterAudioRenderer =
        rendererOrNull ?: rendererFactory().also { rendererOrNull = it }

    /**
     * D-10 restart-from-zero: any scratch left by a dead process is stale.
     * Runs once, lazily, on first queue access — not at construction (the
     * Injekt singleton may be built without any renders ever happening).
     */
    private fun sweepScratchOnce() {
        if (scratchSwept) return
        scratchSwept = true
        scratchDir.listFiles()?.forEach { it.delete() }
    }

    // -- public API -------------------------------------------------------------

    /**
     * Queue a downloaded chapter for rendering (D-13). Ignored if the chapter
     * is already queued/rendering or already has a rendered `.m4a`; an ERROR
     * item is re-queued and resumes at its retained checkpoint.
     */
    fun enqueueRender(novel: Novel, chapter: NovelChapter, source: NovelSource) {
        sweepScratchOnce()
        val existing = queueState.value.find { it.chapter.id == chapter.id }
        if (existing != null) {
            if (existing.status == ChapterRender.State.ERROR) {
                // Retry resumes at the retained scratch checkpoint (D-10).
                existing.cancelledForPreempt = false
                existing.status = ChapterRender.State.QUEUED
                serviceController(true)
                pump()
            }
            return
        }
        if (provider.findAudioFile(chapter.name, chapter.scanlator, novel.title, source) != null) {
            return
        }
        _queueState.value = queueState.value + ChapterRender(novel, chapter, source)
        serviceController(true)
        pump()
    }

    /**
     * D-14 "Start render now": move the tapped queued item to head-of-queue.
     * A running render is cancelled and reinserted QUEUED right behind it —
     * its PCM scratch and paragraph checkpoint are retained, so it resumes
     * where it left off once the tapped item finishes (SD-2).
     */
    fun startRenderNow(chapterId: Long) {
        sweepScratchOnce()
        val queue = queueState.value
        val tapped = queue.find { it.chapter.id == chapterId && it.status == ChapterRender.State.QUEUED }
            ?: return
        val running = queue.find { it.status == ChapterRender.State.RENDERING }

        if (running != null) {
            running.cancelledForPreempt = true
            running.status = ChapterRender.State.QUEUED
        }
        _queueState.value = buildList {
            add(tapped)
            if (running != null) add(running)
            queue.forEach { if (it !== tapped && it !== running) add(it) }
        }
        renderJob?.cancel()
        pump()
    }

    /**
     * D-11 user cancel: the partial render is destroyed — the item leaves the
     * queue and [ChapterAudioRenderer.abandon] deletes its PCM scratch. A
     * running render's tmp `.m4a` doc is torn down by the renderer's own
     * cancellation path (`sink.abort()`), which runs as the job unwinds.
     */
    fun cancelRender(chapterId: Long) {
        sweepScratchOnce()
        val item = queueState.value.find { it.chapter.id == chapterId } ?: return
        item.cancelledForPreempt = false
        when (item.status) {
            ChapterRender.State.RENDERING -> renderJob?.cancel()
            else -> Unit
        }
        _queueState.value = queueState.value - item
        rendererOrNull?.abandon(chapterId)
        pump()
    }

    /**
     * Delete a chapter's rendered audio without touching the download —
     * the icon-menu Delete path (D-08); cancels a queued/running render for
     * it first so a live render can't resurrect the deleted file (T-05-02-01).
     */
    suspend fun deleteAudio(novel: Novel, chapter: NovelChapter, source: NovelSource) {
        sweepScratchOnce()
        if (queueState.value.any { it.chapter.id == chapter.id }) {
            cancelRender(chapter.id)
        }
        provider.deleteAudioFile(chapter.name, chapter.scanlator, novel.title, source)
    }

    /** Rendered `.m4a` filenames under the novel's `audio/` dir (05-04 seeds icon state). */
    suspend fun renderedAudioFileNames(novel: Novel, source: NovelSource): Set<String> {
        sweepScratchOnce()
        return provider.renderedAudioFileNames(novel.title, source)
    }

    /**
     * Queue status for a chapter, else [ChapterRender.State.DONE] when a
     * rendered `.m4a` exists on disk, else null (never rendered).
     */
    suspend fun audioStateFor(chapter: NovelChapter, novel: Novel, source: NovelSource): ChapterRender.State? {
        sweepScratchOnce()
        queueState.value.find { it.chapter.id == chapter.id }?.let { return it.status }
        return if (provider.findAudioFile(chapter.name, chapter.scanlator, novel.title, source) != null) {
            ChapterRender.State.DONE
        } else {
            null
        }
    }

    /** Merged per-item status changes — mirrors `NovelDownloadManager.statusFlow`. */
    fun statusFlow(): Flow<ChapterRender> = queueState
        .flatMapLatest { renders ->
            renders
                .map { render ->
                    render.statusFlow.drop(1).map { render }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { it.status == ChapterRender.State.RENDERING }.asFlow(),
            )
        }

    /** Merged per-item progress changes — mirrors `NovelDownloadManager.progressFlow`. */
    fun progressFlow(): Flow<ChapterRender> = queueState
        .flatMapLatest { renders ->
            renders
                .map { render ->
                    render.progressFlow.drop(1).map { render }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { it.status == ChapterRender.State.RENDERING }.asFlow(),
            )
        }

    // -- queue pump ---------------------------------------------------------------

    /**
     * Start the head-of-queue item if nothing is rendering. When no
     * QUEUED/RENDERING work remains the queue is drained: the FGS stops and
     * the engine is released (reload cost ~1-2s is absorbed by the next
     * render).
     */
    private fun pump() {
        if (renderJob?.isActive == true) return
        val next = queueState.value.firstOrNull { it.status == ChapterRender.State.QUEUED }
        if (next == null) {
            if (queueState.value.none { it.status == ChapterRender.State.RENDERING }) {
                onQueueDrained()
            }
            return
        }
        renderJob = scope.launch {
            try {
                renderItem(next)
            } finally {
                onRenderJobFinished(coroutineContext[Job])
            }
        }
    }

    private fun onRenderJobFinished(job: Job?) {
        // A preempt may already have launched a replacement — only the job
        // currently registered gets to chain the next item.
        if (renderJob !== job) return
        renderJob = null
        pump()
    }

    private suspend fun renderItem(item: ChapterRender) {
        // Per-render partial-cleanup (D-10): drop a stale `_tmp` audio doc a
        // previous interrupted render may have left for this chapter — the
        // `_tmp` matcher convention of NovelDownloadCache/NovelDownloader.
        runCatching {
            provider.findAudioDir(item.novel.title, item.source)
                ?.findFile(
                    provider.getAudioFileName(item.chapter.name, item.chapter.scanlator) +
                        NovelDownloader.TMP_DIR_SUFFIX,
                )
                ?.delete()
        }

        item.status = ChapterRender.State.RENDERING
        try {
            renderer().renderChapter(
                novel = item.novel,
                chapter = item.chapter,
                source = item.source,
                resumeFromParagraph = item.completedParagraphs,
                onProgress = { done, total ->
                    item.completedParagraphs = done
                    item.totalParagraphs = total
                    if (total > 0) item.progress = done * 100 / total
                },
            )
            item.progress = 100
            item.status = ChapterRender.State.DONE
            _queueState.value = queueState.value - item
        } catch (t: CancellationException) {
            if (item.cancelledForPreempt) {
                // SD-2: scratch retained; the item was already reinserted
                // QUEUED by startRenderNow — leave it for the next pump.
                item.status = ChapterRender.State.QUEUED
            } else {
                // D-11 user cancel — post-unwind abandon guarantees the
                // scratch can't be recreated by an in-flight append.
                _queueState.value = queueState.value - item
                rendererOrNull?.abandon(item.chapter.id)
            }
            throw t
        } catch (t: Throwable) {
            logcat(LogPriority.ERROR, t) { "chapter audio render failed for '${item.chapter.name}'" }
            // ERROR retains the scratch — a retry resumes at the checkpoint.
            item.errorMessage = t.message
            item.status = ChapterRender.State.ERROR
        }
    }

    private fun onQueueDrained() {
        serviceController(false)
        rendererOrNull?.let { runCatching { it.releaseEngine() } }
    }
}
