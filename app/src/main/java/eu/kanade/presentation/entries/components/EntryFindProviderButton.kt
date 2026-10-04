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
 * Full-width "Find Content Provider" button for canonical entries with
 * zero bound carriers (17-02) — same footprint as
 * [EntryViewCommentsButton], which it replaces on the entry page until a
 * provider is minted (then comments return).
 */
@Composable
fun EntryFindProviderButton(
    accentColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, accent),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
    ) {
        Text(text = stringResource(AYMR.strings.find_content_provider))
    }
}
