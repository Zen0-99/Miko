package eu.kanade.tachiyomi.data.library.novel

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.math.RoundingMode
import java.text.NumberFormat

class NovelLibraryUpdateNotifier(
    private val context: Context,
    private val securityPreferences: SecurityPreferences = Injekt.get(),
) {

    private val percentFormatter = NumberFormat.getPercentInstance().apply {
        roundingMode = RoundingMode.DOWN
        maximumFractionDigits = 0
    }

    private val cancelIntent by lazy {
        NotificationReceiver.cancelLibraryUpdatePendingBroadcast(context)
    }

    val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.app_name))
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                cancelIntent,
            )
        }
    }

    fun showProgressNotification(novels: List<Novel>, current: Int, total: Int) {
        progressNotificationBuilder
            .setContentTitle(
                context.stringResource(
                    MR.strings.notification_updating_progress,
                    percentFormatter.format(current.toFloat() / total),
                ),
            )

        if (!securityPreferences.hideNotificationContent().get()) {
            val updatingText = novels.joinToString("\n") { it.title.chop(40) }
            progressNotificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(updatingText))
        }

        context.notify(
            Notifications.ID_LIBRARY_PROGRESS,
            progressNotificationBuilder
                .setProgress(total, current, false)
                .build(),
        )
    }

    fun cancelProgressNotification() {
        context.cancelNotification(Notifications.ID_LIBRARY_PROGRESS)
    }

    /**
     * Shows the notification containing the result of the update done by the service.
     *
     * Mirrors the manga/anime notification format: a parent group notification
     * with a summary, plus per-novel notifications showing the novel title and
     * the new chapter names.
     *
     * @param updates a list of novels with new updates.
     */
    fun showUpdateNotifications(updates: List<Pair<Novel, List<NovelChapter>>>) {
        if (updates.isEmpty()) return

        // Parent group notification
        context.notify(
            Notifications.ID_NEW_CHAPTERS,
            Notifications.CHANNEL_NEW_CHAPTERS_EPISODES,
        ) {
            setContentTitle(context.stringResource(MR.strings.notification_new_chapters))
            if (updates.size == 1 && !securityPreferences.hideNotificationContent().get()) {
                setContentText(updates.first().first.title.chop(NOTIF_TITLE_MAX_LEN))
            } else {
                setContentText(
                    context.resources.getQuantityString(
                        R.plurals.notification_new_chapters_summary,
                        updates.size,
                        updates.size,
                    ),
                )

                if (!securityPreferences.hideNotificationContent().get()) {
                    setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            updates.joinToString("\n") {
                                it.first.title.chop(NOTIF_TITLE_MAX_LEN)
                            },
                        ),
                    )
                }
            }

            setSmallIcon(R.drawable.ic_ani)

            setGroup(Notifications.GROUP_NEW_CHAPTERS)
            setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            setGroupSummary(true)
            priority = NotificationCompat.PRIORITY_HIGH

            setContentIntent(getNotificationIntent())
            setAutoCancel(true)
        }

        // Per-novel notifications
        if (!securityPreferences.hideNotificationContent().get()) {
            launchUI {
                context.notify(
                    updates.map { (novel, chapters) ->
                        NotificationManagerCompat.NotificationWithIdAndTag(
                            novel.id.hashCode(),
                            createNewChaptersNotification(novel, chapters),
                        )
                    },
                )
            }
        }
    }

    private suspend fun createNewChaptersNotification(
        novel: Novel,
        chapters: List<NovelChapter>,
    ): Notification {
        val icon = getNovelIcon(novel)
        return context.notificationBuilder(Notifications.CHANNEL_NEW_CHAPTERS_EPISODES) {
            setContentTitle(novel.title)

            val description = getNewChaptersDescription(chapters)
            setContentText(description)
            setStyle(NotificationCompat.BigTextStyle().bigText(description))

            setSmallIcon(R.drawable.ic_ani)

            if (icon != null) {
                setLargeIcon(icon)
            }

            setGroup(Notifications.GROUP_NEW_CHAPTERS)
            setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            priority = NotificationCompat.PRIORITY_HIGH

            // Open the novel's chapter list on tap
            setContentIntent(getNovelNotificationIntent(novel.id))
            setAutoCancel(true)
        }.build()
    }

    private suspend fun getNovelIcon(novel: Novel): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(novel)
            .transformations(CircleCropTransformation())
            .size(NOTIF_ICON_SIZE)
            .build()
        val drawable = context.imageLoader.execute(request).image?.asDrawable(context.resources)
        return drawable?.getBitmapOrNull()
    }

    /**
     * Builds a description string listing the new chapter names, mirroring
     * the manga notification format. Since novel chapters often don't have
     * parsed chapter numbers, this uses chapter names instead.
     */
    private fun getNewChaptersDescription(chapters: List<NovelChapter>): String {
        // Try to use chapter numbers if available
        val numberedChapters = chapters.filter { it.isRecognizedNumber }
            .sortedBy { it.chapterNumber }
            .map { formatChapterNumber(it.chapterNumber) }
            .toSet()
            .toSet()

        return when (numberedChapters.size) {
            0 -> {
                // No parsed chapter numbers — show chapter names or generic count
                val names = chapters.sortedBy { it.sourceOrder }.map { it.name.chop(40) }
                if (names.size <= NOTIF_MAX_CHAPTERS) {
                    if (names.size == 1) {
                        names.first()
                    } else {
                        context.stringResource(
                            MR.strings.notification_chapters_multiple,
                            names.joinToString(", "),
                        )
                    }
                } else {
                    val remaining = names.size - NOTIF_MAX_CHAPTERS
                    context.resources.getQuantityString(
                        R.plurals.notification_chapters_multiple_and_more,
                        remaining,
                        names.take(NOTIF_MAX_CHAPTERS).joinToString(", "),
                        remaining,
                    )
                }
            }
            1 -> {
                val remaining = chapters.size - numberedChapters.size
                if (remaining == 0) {
                    context.stringResource(
                        MR.strings.notification_chapters_single,
                        numberedChapters.first(),
                    )
                } else {
                    context.stringResource(
                        MR.strings.notification_chapters_single_and_more,
                        numberedChapters.first(),
                        remaining,
                    )
                }
            }
            else -> {
                val shouldTruncate = numberedChapters.size > NOTIF_MAX_CHAPTERS
                if (shouldTruncate) {
                    val remaining = numberedChapters.size - NOTIF_MAX_CHAPTERS
                    val joined = numberedChapters.take(NOTIF_MAX_CHAPTERS).joinToString(", ")
                    context.resources.getQuantityString(
                        R.plurals.notification_chapters_multiple_and_more,
                        remaining,
                        joined,
                        remaining,
                    )
                } else {
                    context.stringResource(
                        MR.strings.notification_chapters_multiple,
                        numberedChapters.joinToString(", "),
                    )
                }
            }
        }
    }

    /**
     * Returns an intent to open the main activity.
     */
    private fun getNotificationIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Returns an intent to open a specific novel in the main activity.
     */
    private fun getNovelNotificationIntent(novelId: Long): PendingIntent {
        // Open the main activity; novel deep-linking is handled by the
        // shortcut/navigate system if available, otherwise the user lands
        // on the library.
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            novelId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val NOTIF_TITLE_MAX_LEN = 45
        private const val NOTIF_MAX_CHAPTERS = 5
        private const val NOTIF_ICON_SIZE = 96
    }
}
