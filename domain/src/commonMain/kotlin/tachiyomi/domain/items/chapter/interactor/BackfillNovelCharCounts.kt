package tachiyomi.domain.items.chapter.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository

/**
 * Backfill character counts for read chapters that were marked as read before
 * char count tracking was implemented (total_char_count = 0).
 *
 * For each novel with missing char counts:
 * 1. Calculate the average total_char_count from read chapters that DO have one
 * 2. Apply that average to all read chapters with total_char_count = 0
 *
 * If a novel has NO chapters with char counts (none were read in the reader),
 * it is skipped — the backfill will work once the user reads at least one
 * chapter in the reader.
 *
 * Returns the number of novels that were backfilled.
 */
class BackfillNovelCharCounts(
    private val novelChapterRepository: NovelChapterRepository,
) {

    suspend fun await(): Int {
        var backfilledCount = 0
        try {
            val novelIds = novelChapterRepository.getNovelIdsWithMissingReadCharCount()
            for (novelId in novelIds) {
                val avgCharCount = novelChapterRepository.getAverageReadCharCountForNovel(novelId)
                if (avgCharCount > 0) {
                    novelChapterRepository.backfillReadCharCountForNovel(novelId, avgCharCount)
                    backfilledCount++
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
        return backfilledCount
    }
}
