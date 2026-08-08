package eu.kanade.tachiyomi.ui.reader.novel

import android.content.Context
import androidx.core.content.edit

/**
 * Tracks when the user last visited a novel's detail screen.
 * Chapters with dateFetch > lastSeenTimestamp are considered "new".
 *
 * The "new" indicator (colored circle) shows for unread chapters that were
 * fetched after the last visit. When the user leaves the detail screen,
 * the lastSeenTimestamp is updated, clearing the "new" indicator.
 */
class NewChapterTracker(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "new_chapter_tracker"
        private const val KEY_PREFIX = "novel_seen_"

        fun getLastSeenTimestamp(context: Context, novelId: Long): Long {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getLong("$KEY_PREFIX$novelId", 0L)
        }
    }

    fun getLastSeenTimestamp(novelId: Long): Long {
        return prefs.getLong("$KEY_PREFIX$novelId", 0L)
    }

    fun markAsSeen(novelId: Long) {
        prefs.edit {
            putLong("$KEY_PREFIX$novelId", System.currentTimeMillis() / 1000)
        }
    }

    fun isNew(novelId: Long, dateFetch: Long): Boolean {
        val lastSeen = getLastSeenTimestamp(novelId)
        // If never seen before, don't mark all existing chapters as new
        // (lastSeen == 0 means first visit — show no new indicators)
        return lastSeen > 0 && dateFetch > lastSeen
    }
}
