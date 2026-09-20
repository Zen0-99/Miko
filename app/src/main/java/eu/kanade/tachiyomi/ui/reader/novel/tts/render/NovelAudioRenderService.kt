package eu.kanade.tachiyomi.ui.reader.novel.tts.render

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * `dataSync` foreground-service keep-alive for the chapter-audio render queue
 * (plan 05-02, D-09). The service owns ZERO queue state — it observes
 * [ChapterAudioRenderManager.queueState]/`statusFlow`/`progressFlow` and
 * renders notifications; the manager starts it when work is enqueued and
 * stops it when the queue drains.
 *
 * `startForeground` is called on EVERY `onStartCommand` — the
 * `startForegroundService()` ~5s obligation applies per-start, not just the
 * first (ANR precedent at `NovelTtsPlaybackService.onStartCommand`).
 */
class NovelAudioRenderService : Service() {

    companion object {
        const val ACTION_CANCEL = "eu.kanade.tachiyomi.NOVEL_AUDIO_RENDER_CANCEL"

        /** Start the keep-alive service — called when the queue becomes non-empty. */
        fun start(context: Context) {
            val intent = Intent(context, NovelAudioRenderService::class.java)
            context.startForegroundService(intent)
        }

        /** Stop the service — called when the queue drains. */
        fun stop(context: Context) {
            val intent = Intent(context, NovelAudioRenderService::class.java)
            context.stopService(intent)
        }
    }

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private val renderManager: ChapterAudioRenderManager = Injekt.get()
    private lateinit var notifier: NovelAudioRenderNotifier

    override fun onCreate() {
        super.onCreate()
        notifier = NovelAudioRenderNotifier(this)
        // Belt-and-suspenders with the onStartCommand call below — covers
        // bind-only startups where onStartCommand never runs.
        startForeground(Notifications.ID_NOVEL_AUDIO_RENDER, notifier.buildNotification())
        observeQueue()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Mandatory on EVERY start — idempotent when already foreground.
        startForeground(Notifications.ID_NOVEL_AUDIO_RENDER, notifier.buildNotification())

        if (intent?.action == ACTION_CANCEL) {
            renderManager.queueState.value
                .firstOrNull { it.status == ChapterRender.State.RENDERING }
                ?.let { renderManager.cancelRender(it.chapter.id) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun observeQueue() {
        // Queue drained -> drop the notification and stop (T-05-02-02: the
        // FGS must never linger past the last pending render).
        serviceScope.launch {
            renderManager.queueState.collect { queue ->
                if (queue.isEmpty()) {
                    notifier.onComplete()
                    stopSelf()
                }
            }
        }
        serviceScope.launch {
            renderManager.statusFlow().collect { render ->
                when (render.status) {
                    ChapterRender.State.RENDERING -> notifier.onProgress(render)
                    ChapterRender.State.ERROR ->
                        notifier.onError(render.novel, render.chapter, render.errorMessage)
                    else -> Unit
                }
            }
        }
        serviceScope.launch {
            renderManager.progressFlow().collect { render ->
                if (render.status == ChapterRender.State.RENDERING) {
                    notifier.onProgress(render)
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): NovelAudioRenderService = this@NovelAudioRenderService
    }
}
