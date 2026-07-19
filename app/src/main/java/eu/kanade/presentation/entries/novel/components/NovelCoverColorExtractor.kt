package eu.kanade.presentation.entries.novel.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.entries.components.PreExtractEntryCoverColor
import eu.kanade.presentation.entries.components.adjustForTheme
import eu.kanade.presentation.entries.components.extractEntryCoverBaseColor
import eu.kanade.presentation.entries.components.isDark
import eu.kanade.presentation.entries.components.rememberEntryAccentColor
import eu.kanade.tachiyomi.util.EntryCoverMetadata
import eu.kanade.tachiyomi.util.novel.NovelCoverMetadata
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.entries.novel.model.asNovelCover

/**
 * Deprecated — use [eu.kanade.presentation.entries.components.rememberEntryAccentColor]
 * with [EntryCoverMetadata.EntryType.NOVEL] instead. Kept as a thin delegate for
 * backward compatibility.
 */
@Composable
fun rememberNovelAccentColor(
    novel: Novel?,
    enabled: Boolean = true,
): Color? {
    val cover = novel?.asNovelCover()
    return rememberEntryAccentColor(
        entryId = novel?.id,
        cover = cover,
        type = EntryCoverMetadata.EntryType.NOVEL,
        enabled = enabled,
    )
}

/**
 * Deprecated — use [eu.kanade.presentation.entries.components.PreExtractEntryCoverColor]
 * with [EntryCoverMetadata.EntryType.NOVEL] instead.
 */
@Composable
fun PreExtractNovelCoverColor(
    novelId: Long,
    cover: NovelCover,
) {
    PreExtractEntryCoverColor(
        entryId = novelId,
        cover = cover,
        type = EntryCoverMetadata.EntryType.NOVEL,
    )
}

/**
 * Deprecated — use [eu.kanade.presentation.entries.components.extractEntryCoverBaseColor] instead.
 */
suspend fun extractNovelCoverBaseColor(
    context: Context,
    cover: NovelCover,
    size: Int = 128,
): Int? = extractEntryCoverBaseColor(context, cover, size)
