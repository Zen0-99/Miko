package eu.kanade.tachiyomi.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadProvider
import eu.kanade.tachiyomi.ui.reader.novel.tts.render.ChapterAudioRenderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.interactor.GetNovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Debug-only adb hook for the in-process render queue — the queue lives in
 * [ChapterAudioRenderManager], so the only way to enqueue a chapter without
 * driving the UI is a broadcast into the app:
 *
 * ```
 * adb shell am broadcast \
 *   -a xyz.jmir.miko.debug.RENDER_CHAPTER \
 *   -n xyz.jmir.miko.dev/eu.kanade.tachiyomi.debug.RenderChapterReceiver \
 *   --el chapter_id <id>
 * ```
 *
 * Resolves chapter → novel from the DB and kicks [NovelDownloadManager]
 * (whose job keeps the process alive) when the text isn't on disk. The
 * pendingResult is finished right after the kick — holding it through the
 * download blew past the ~10s foreground-broadcast window and got the
 * process ANR-killed. Everything slow (source polling, waiting for the
 * text, enqueueing the render) continues on [scope] afterwards.
 * `--ez download_only true` skips the render enqueue — render-chapters.bat
 * renders on the PC and pushes the .m4a back. Debug builds only.
 */
class RenderChapterReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val chapterId = intent.getLongExtra(EXTRA_CHAPTER_ID, -1L)
        if (chapterId < 0) {
            logcat(LogPriority.ERROR) { "render-chapter: missing/invalid '$EXTRA_CHAPTER_ID' extra" }
            return
        }
        val downloadOnly = intent.getBooleanExtra(EXTRA_DOWNLOAD_ONLY, false)
        val pending = goAsync()
        scope.launch {
            try {
                val chapter = Injekt.get<GetNovelChapter>().await(chapterId)
                val novel = chapter?.let { Injekt.get<GetNovel>().await(it.novelId) }
                if (chapter == null || novel == null) {
                    logcat(LogPriority.ERROR) {
                        "render-chapter: unresolved chapterId=$chapterId " +
                            "(chapter=${chapter != null}, novel=${novel != null})"
                    }
                    return@launch
                }

                val downloadManager = Injekt.get<NovelDownloadManager>()
                val downloaded = downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    novel.title,
                    novel.source,
                    skipCache = true,
                )
                if (!downloaded) {
                    logcat { "render-chapter: downloading '${chapter.name}' first" }
                    // Starts NovelDownloadJob — holds the process once pending
                    // is finished and the broadcast window closes.
                    downloadManager.downloadChapters(novel, listOf(chapter))
                }
                pending.finish()

                if (downloadOnly) {
                    logcat {
                        "render-chapter: download-only '${
                            chapter.name
                        }' (downloaded=$downloaded)"
                    }
                } else {
                    waitAndEnqueue(chapter, novel, downloaded)
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "render-chapter: failed for chapterId=$chapterId" }
                pending.finish()
            }
        }
    }

    /**
     * Post-broadcast continuation: waits for the source to register (cold
     * start), waits for the chapter text, then enqueues the render. Runs
     * detached — the download job or render FGS is what actually keeps the
     * process alive.
     */
    private suspend fun waitAndEnqueue(
        chapter: NovelChapter,
        novel: Novel,
        alreadyDownloaded: Boolean,
    ) {
        val source = awaitSource(novel.source) ?: run {
            logcat(LogPriority.ERROR) {
                "render-chapter: source ${novel.source} never appeared for '${chapter.name}'"
            }
            return
        }

        if (!alreadyDownloaded && !awaitChapterText(chapter, novel)) {
            logcat(LogPriority.ERROR) { "render-chapter: download timed out for '${chapter.name}'" }
            return
        }

        Injekt.get<ChapterAudioRenderManager>().enqueueRender(novel, chapter, source)
        logcat { "render-chapter: enqueued '${chapter.name}' of '${novel.title}'" }
    }

    // isInitialized flips when EITHER the APK or JS source flow first emits —
    // not when every source is loaded — so poll get() for this specific id.
    private suspend fun awaitSource(sourceId: Long): eu.kanade.tachiyomi.novelsource.NovelSource? {
        val sourceManager = Injekt.get<NovelSourceManager>()
        var source = sourceManager.get(sourceId)
        var waited = 0L
        while (source == null && waited < SOURCE_WAIT_MS) {
            delay(500)
            waited += 500
            source = sourceManager.get(sourceId)
        }
        return source
    }

    private suspend fun awaitChapterText(chapter: NovelChapter, novel: Novel): Boolean {
        val provider = Injekt.get<NovelDownloadProvider>()
        val sourceManager = Injekt.get<NovelSourceManager>()
        var waited = 0L
        while (waited < DOWNLOAD_WAIT_MS) {
            val source = sourceManager.get(novel.source)
            if (source != null &&
                provider.findChapterDir(chapter.name, chapter.scanlator, novel.title, source) != null
            ) {
                return true
            }
            delay(2_000)
            waited += 2_000
        }
        return false
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        const val EXTRA_CHAPTER_ID = "chapter_id"
        const val EXTRA_DOWNLOAD_ONLY = "download_only"
        const val DOWNLOAD_WAIT_MS = 120_000L
        const val SOURCE_WAIT_MS = 30_000L
    }
}
