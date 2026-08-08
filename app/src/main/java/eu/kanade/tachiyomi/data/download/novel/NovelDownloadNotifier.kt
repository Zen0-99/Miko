package eu.kanade.tachiyomi.data.download.novel

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.util.regex.Pattern

internal class NovelDownloadNotifier(private val context: Context) {

    private val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setAutoCancel(false)
            setOnlyAlertOnce(true)
        }
    }

    private val errorNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_ERROR) {
            setAutoCancel(false)
        }
    }

    private var isDownloading = false

    private fun androidx.core.app.NotificationCompat.Builder.show(id: Int) {
        context.notify(id, build())
    }

    fun dismissProgress() {
        context.cancelNotification(Notifications.ID_DOWNLOAD_CHAPTER_PROGRESS)
    }

    fun onProgressChange(download: NovelDownload) {
        with(progressNotificationBuilder) {
            if (!isDownloading) {
                setSmallIcon(android.R.drawable.stat_sys_download)
                clearActions()
                setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
                isDownloading = true
                addAction(
                    R.drawable.ic_pause_24dp,
                    context.stringResource(MR.strings.action_pause),
                    NotificationReceiver.pauseDownloadsPendingBroadcast(context),
                )
            }

            setContentTitle(download.novel.title.chop(30))
            setContentText(download.chapter.name.chop(40))

            // Indeterminate progress — novel chapter downloads are atomic
            // (the whole chapter text is fetched in one request), so we
            // can't show byte-level progress. Show an indeterminate spinner.
            setProgress(0, 0, true)
            setOngoing(true)

            show(Notifications.ID_DOWNLOAD_CHAPTER_PROGRESS)
        }
    }

    fun onPaused() {
        with(progressNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.action_pause))
            setContentText(context.stringResource(MR.strings.download_notifier_download_paused))
            setSmallIcon(R.drawable.ic_pause_24dp)
            setProgress(0, 0, false)
            setOngoing(false)
            clearActions()
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            addAction(
                R.drawable.ic_play_arrow_24dp,
                context.stringResource(MR.strings.action_resume),
                NotificationReceiver.resumeDownloadsPendingBroadcast(context),
            )
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel_all),
                NotificationReceiver.clearDownloadsPendingBroadcast(context),
            )

            show(Notifications.ID_DOWNLOAD_CHAPTER_PROGRESS)
        }

        isDownloading = false
    }

    fun onComplete() {
        dismissProgress()
        isDownloading = false
    }

    fun onWarning(reason: String, timeout: Long? = null, contentIntent: android.app.PendingIntent? = null) {
        with(errorNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.download_notifier_downloader_title))
            setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(reason))
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setAutoCancel(true)
            clearActions()
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            setProgress(0, 0, false)
            timeout?.let { setTimeoutAfter(it) }
            contentIntent?.let { setContentIntent(it) }

            show(Notifications.ID_DOWNLOAD_CHAPTER_ERROR)
        }

        isDownloading = false
    }

    fun onError(error: String? = null, chapter: String? = null, novelTitle: String? = null, novelId: Long? = null) {
        with(errorNotificationBuilder) {
            setContentTitle(
                novelTitle?.plus(": $chapter") ?: context.stringResource(
                    MR.strings.download_notifier_downloader_title,
                ),
            )
            setContentText(error ?: context.stringResource(MR.strings.download_notifier_unknown_error))
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            clearActions()
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            setProgress(0, 0, false)

            show(Notifications.ID_DOWNLOAD_CHAPTER_ERROR)
        }

        isDownloading = false
    }
}
