package eu.kanade.tachiyomi.ui.history.novel

import java.time.LocalDate
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations

sealed interface NovelHistoryUiModel {
    data class Header(val date: LocalDate) : NovelHistoryUiModel
    data class Item(val item: NovelHistoryWithRelations) : NovelHistoryUiModel

    /**
     * A batched group of history entries for the same novel whose readAt
     * timestamps fall within a short window (e.g. mass mark-as-read).
     * Renders as "Chapter X - Y" with a time range "X AM - Y AM".
     */
    data class Batch(
        val novelId: Long,
        val title: String,
        val firstChapter: Double,
        val lastChapter: Double,
        val firstReadAt: java.util.Date,
        val lastReadAt: java.util.Date,
        val coverData: tachiyomi.domain.entries.novel.model.NovelCover,
        val historyIds: List<Long>,
    ) : NovelHistoryUiModel
}
