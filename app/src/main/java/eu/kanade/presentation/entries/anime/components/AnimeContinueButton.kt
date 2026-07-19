package eu.kanade.presentation.entries.anime.components

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
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Full-width continue/start watching button using the entry's accent color.
 * Mirrors [eu.kanade.presentation.entries.novel.components.NovelContinueButton].
 */
@Composable
fun AnimeContinueButton(
    nextEpisode: Episode?,
    hasSeenEpisodes: Boolean,
    accentColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val text = if (nextEpisode != null && hasSeenEpisodes) {
        val episodeName = nextEpisode.name
        val prefix = stringResource(MR.strings.action_continue)
        "$prefix: $episodeName"
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
