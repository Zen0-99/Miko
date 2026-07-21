package eu.kanade.presentation.entries.novel.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.entries.components.EntryChapterHeader
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.i18n.MR

/**
 * Deprecated — use [eu.kanade.presentation.entries.components.EntryChapterHeader] instead.
 * Kept as a thin delegate for backward compatibility.
 */
@Composable
fun NovelChapterHeader(
    itemCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    onFetchNewChapters: (() -> Unit)? = null,
    onFetchAllChapters: (() -> Unit)? = null,
    intervalDays: Int? = null,
) {
    EntryChapterHeader(
        itemCountText = pluralStringResource(MR.plurals.manga_num_chapters, count = itemCount, itemCount),
        onClick = onClick,
        modifier = modifier,
        accentColor = accentColor,
        onFetchNew = onFetchNewChapters,
        onFetchAll = onFetchAllChapters,
        intervalDays = intervalDays,
    )
}
