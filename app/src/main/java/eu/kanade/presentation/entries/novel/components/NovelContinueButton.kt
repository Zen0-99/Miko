package eu.kanade.presentation.entries.novel.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun NovelContinueButton(
    chapterItem: NovelChapterList.Item?,
    hasReadChapters: Boolean,
    accentColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val chapter = chapterItem?.chapter
    val text = if (chapter != null && hasReadChapters) {
        val chapterName = chapter.name
        val prefix = stringResource(MR.strings.action_continue)
        "$prefix: $chapterName"
    } else {
        stringResource(MR.strings.action_start)
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
