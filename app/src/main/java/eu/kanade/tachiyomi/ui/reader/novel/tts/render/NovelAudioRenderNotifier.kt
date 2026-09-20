package eu.kanade.tachiyomi.ui.reader.novel.tts.render

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Notification surface for the chapter-audio render queue (plan 05-02, D-09):
 * a determinate progress notification on [Notifications.CHANNEL_NOVEL_AUDIO_RENDER]
 * with a Cancel action that redelivers [NovelAudioRenderService.ACTION_CANCEL]
 * to the service. No MediaStyle/MediaSession — this is a job, not playback.
 *
 * The first-ever render carries the one-time heat/battery warning (D-15/D-17),
 * gated on [NovelTtsPreferences.audioRenderWarned] — set true only after the
 * first post actually lands.
 *
 * Errors post a one-shot auto-cancel notification on the same channel/ID —
 * it replaces the now-stale progress notification.
 */
internal class NovelAudioRenderNotifier(
    private val context: Context,
    private val preferences: NovelTtsPreferences = Injekt.get(),
) {

    private val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_NOVEL_AUDIO_RENDER) {
            setAutoCancel(false)
            setOnlyAlertOnce(true)
            setOngoing(true)
            setSmallIcon(R.drawable.ic_audio_item_24dp)
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                cancelPendingIntent(),
            )
        }
    }

    private val errorNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_NOVEL_AUDIO_RENDER) {
            setAutoCancel(true)
            setOngoing(false)
            setProgress(0, 0, false)
            setSmallIcon(R.drawable.ic_warning_white_24dp)
        }
    }

    private fun NotificationCompat.Builder.show(id: Int) {
        context.notify(id, build())
    }

    /**
     * Bare notification for the mandatory `startForeground` call made before
     * queue state has been observed — replaced by [onProgress] the moment a
     * render is running.
     */
    fun buildNotification(): Notification = progressNotificationBuilder
        .setContentTitle("Rendering chapter audio")
        .setProgress(0, 0, true)
        .build()

    /** Determinate progress for the currently-rendering item (D-09). */
    fun onProgress(render: ChapterRender) {
        val chapterText = render.chapter.name.chop(40)
        val metric = if (render.totalParagraphs > 0) {
            "${render.completedParagraphs}/${render.totalParagraphs}"
        } else {
            "${render.progress.coerceIn(0, 100)}%"
        }
        val showWarning = !preferences.audioRenderWarned().get()
        with(progressNotificationBuilder) {
            setContentTitle(render.novel.title.chop(30))
            setContentText("$chapterText — $metric")
            if (showWarning) {
                setStyle(
                    NotificationCompat.BigTextStyle().bigText("$chapterText — $metric\n\n$WARNING_COPY"),
                )
            } else {
                setStyle(null)
            }
            setOngoing(true)
            setProgress(100, render.progress.coerceIn(0, 100), false)
            show(Notifications.ID_NOVEL_AUDIO_RENDER)
        }
        // Persist only after the warning was actually posted (D-17 one-shot).
        if (showWarning) {
            preferences.audioRenderWarned().set(true)
        }
    }

    /** One-shot failure notification — replaces the stale progress post. */
    fun onError(novel: Novel, chapter: NovelChapter, message: String?) {
        with(errorNotificationBuilder) {
            setContentTitle("Audio render failed")
            setContentText("${novel.title.chop(30)}: ${chapter.name.chop(40)}")
            setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    message ?: context.stringResource(MR.strings.download_notifier_unknown_error),
                ),
            )
            show(Notifications.ID_NOVEL_AUDIO_RENDER)
        }
    }

    /** Queue drained — dismiss the progress notification; no completion post. */
    fun onComplete() {
        context.cancelNotification(Notifications.ID_NOVEL_AUDIO_RENDER)
    }

    private fun cancelPendingIntent(): PendingIntent {
        val intent = Intent(context, NovelAudioRenderService::class.java)
            .setAction(NovelAudioRenderService.ACTION_CANCEL)
        return PendingIntent.getService(
            context,
            NovelAudioRenderService.ACTION_CANCEL.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        /** D-15/D-17 exact copy — do not paraphrase. */
        const val WARNING_COPY =
            "Rendering runs at full speed — the device may get warm and drain the battery faster."
    }
}
