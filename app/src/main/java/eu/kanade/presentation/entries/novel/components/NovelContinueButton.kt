package eu.kanade.presentation.entries.novel.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterList
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun NovelContinueButton(
    chapterItem: NovelChapterList.Item?,
    hasReadChapters: Boolean,
    allChaptersRead: Boolean,
    accentColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBookSource: Boolean = false,
    isDownloadingBook: Boolean = false,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val chapter = chapterItem?.chapter

    val (text, enabled, buttonColor) = when {
        // Book source (Anna's Archive) with no chapters: show "Download book"
        isBookSource && chapter == null && !allChaptersRead -> Triple(
            stringResource(MR.strings.download_book),
            !isDownloadingBook,
            accent,
        )
        // Book source downloading: show disabled state
        isBookSource && isDownloadingBook -> Triple(
            stringResource(MR.strings.download_book),
            false,
            MaterialTheme.colorScheme.surfaceVariant,
        )
        allChaptersRead -> Triple(
            stringResource(AYMR.strings.fetching_overlay_all_up_to_date),
            false,
            MaterialTheme.colorScheme.surfaceVariant,
        )
        chapter != null && hasReadChapters -> Triple(
            "${stringResource(MR.strings.action_continue)}: ${chapter.name}",
            true,
            accent,
        )
        else -> Triple(
            stringResource(MR.strings.action_start),
            true,
            accent,
        )
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            contentColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
