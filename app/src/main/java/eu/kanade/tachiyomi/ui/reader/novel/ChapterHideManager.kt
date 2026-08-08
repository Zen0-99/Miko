package eu.kanade.tachiyomi.ui.reader.novel

import android.content.Context
import androidx.core.content.edit

/**
 * Manages hidden chapters for novels using SharedPreferences.
 * Hidden chapters are excluded from:
 * - Chapter list display
 * - Continue reading
 * - Home view
 * - Stats
 * - Download eligibility
 *
 * Uses a preference key per novel: "hidden_chapters_<novelId>"
 * Stores a set of chapter IDs as strings.
 */
class ChapterHideManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "hidden_chapters"
        private const val KEY_PREFIX = "novel_"

        fun getHiddenChapterIds(context: Context, novelId: Long): Set<Long> {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getStringSet("$KEY_PREFIX$novelId", emptySet())
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet()
                ?: emptySet()
        }
    }

    fun getHiddenChapterIds(novelId: Long): Set<Long> {
        return prefs.getStringSet("$KEY_PREFIX$novelId", emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    fun hideChapter(novelId: Long, chapterId: Long) {
        val current = getHiddenChapterIds(novelId).toMutableSet()
        current.add(chapterId)
        prefs.edit {
            putStringSet("$KEY_PREFIX$novelId", current.map { it.toString() }.toSet())
        }
    }

    fun unhideChapter(novelId: Long, chapterId: Long) {
        val current = getHiddenChapterIds(novelId).toMutableSet()
        current.remove(chapterId)
        prefs.edit {
            putStringSet("$KEY_PREFIX$novelId", current.map { it.toString() }.toSet())
        }
    }

    fun unhideAllChapters(novelId: Long) {
        prefs.edit {
            remove("$KEY_PREFIX$novelId")
        }
    }

    fun hasHiddenChapters(novelId: Long): Boolean {
        return getHiddenChapterIds(novelId).isNotEmpty()
    }

    fun isChapterHidden(novelId: Long, chapterId: Long): Boolean {
        return getHiddenChapterIds(novelId).contains(chapterId)
    }
}
