package eu.kanade.presentation.entries.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A circular icon-only action button. Renders nothing if [onClick] is null.
 *
 * Extracted from [eu.kanade.presentation.entries.novel.components.NovelActionRow]'s
 * private `NovelIconAction` so manga and anime can reuse it.
 */
@Composable
fun EntryIconAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: (() -> Unit)?,
) {
    if (onClick == null) return
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
