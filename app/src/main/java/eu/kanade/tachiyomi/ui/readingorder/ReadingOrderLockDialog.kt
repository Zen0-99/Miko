package eu.kanade.tachiyomi.ui.readingorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingOrderLockDialog(
    lockedOrders: List<ReadingOrder>,
    onDismiss: () -> Unit,
    onViewOrder: (Long) -> Unit,
    onViewEntry: (() -> Unit)? = null,
    entryKindLabel: String? = null,
) {
    val firstOrder = lockedOrders.first()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val kindLabel = entryKindLabel ?: "Entry"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(AYMR.strings.reading_order_locked_title, kindLabel),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(AYMR.strings.reading_order_locked_message, firstOrder.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (lockedOrders.size > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "(${lockedOrders.size - 1} more reading orders also lock this entry)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onViewOrder(firstOrder.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(AYMR.strings.reading_order_locked_view))
                }
                if (onViewEntry != null) {
                    Button(
                        onClick = onViewEntry,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("View $kindLabel")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
