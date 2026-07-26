package eu.kanade.tachiyomi.ui.readingorder

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReadingOrderLockDialog(
    lockedOrders: List<ReadingOrder>,
    onDismiss: () -> Unit,
    onViewOrder: (Long) -> Unit,
) {
    val firstOrder = lockedOrders.first()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.reading_order_locked_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(AYMR.strings.reading_order_locked_message, firstOrder.name),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (lockedOrders.size > 1) {
                    Text(
                        text = "(${lockedOrders.size - 1} more reading orders also lock this entry)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onViewOrder(firstOrder.id) }) {
                Text(stringResource(AYMR.strings.reading_order_locked_view))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(AYMR.strings.reading_order_locked_dismiss))
            }
        },
    )
}
