package eu.kanade.presentation.entries.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Full-width "View comments (N)" button for detail views — same footprint
 * as the primary action bar (16dp gutters, 16dp corners) but accent
 * outline only, no fill. Opens the shared comments bottom sheet.
 * [commentsCount] null = count not loaded yet; the label drops the badge.
 */
@Composable
fun EntryViewCommentsButton(
    commentsCount: Long?,
    accentColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, accent),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
    ) {
        Text(
            text = if (commentsCount != null) {
                stringResource(AYMR.strings.action_view_comments_count, commentsCount)
            } else {
                stringResource(AYMR.strings.action_view_comments)
            },
        )
    }
}
