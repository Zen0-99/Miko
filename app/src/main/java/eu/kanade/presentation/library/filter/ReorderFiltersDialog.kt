package eu.kanade.presentation.library.filter

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private val ITEM_HEIGHT_DP = 48.dp

/**
 * Dialog to reorder filter chips with drag and drop.
 * The order is persisted as a character string.
 */
@Composable
fun ReorderFiltersDialog(
    onReorder: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultOrder = remember {
        LibraryFilterId.entries.filter { it != LibraryFilterId.TRACKED }.toMutableList()
    }
    val localOrder = remember { mutableStateListOf(*defaultOrder.toTypedArray()) }

    var draggedItemIndex by remember { mutableIntStateOf(-1) }
    var cumulativeDragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    val density = LocalDensity.current
    val itemHeightPx = with(density) { ITEM_HEIGHT_DP.toPx() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(MR.strings.reorder_filters),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 32.dp),
            )
        },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(localOrder, key = { _, item -> item.name }) { index, filterId ->
                    val isDragging = index == draggedItemIndex
                    val visualOffset = if (isDragging) cumulativeDragOffset else 0f

                    DraggableFilterItem(
                        filterId = filterId,
                        isDragging = isDragging,
                        visualOffset = visualOffset,
                        onDragStart = {
                            draggedItemIndex = index
                            cumulativeDragOffset = 0f
                        },
                        onDrag = { delta ->
                            if (draggedItemIndex >= 0) {
                                cumulativeDragOffset += delta
                                val positionsToMove = (cumulativeDragOffset / itemHeightPx).toInt()
                                val targetIndex = (draggedItemIndex + positionsToMove).coerceIn(0, localOrder.lastIndex)
                                if (targetIndex != draggedItemIndex) {
                                    val item = localOrder.removeAt(draggedItemIndex)
                                    localOrder.add(targetIndex, item)
                                    val positionsMoved = targetIndex - draggedItemIndex
                                    cumulativeDragOffset -= positionsMoved * itemHeightPx
                                    draggedItemIndex = targetIndex
                                }
                            }
                        },
                        onDragEnd = {
                            draggedItemIndex = -1
                            cumulativeDragOffset = 0f
                        },
                        modifier = Modifier.zIndex(if (isDragging) 1f else 0f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newOrder = localOrder.joinToString("") { it.char.toString() }
                    onReorder(newOrder)
                },
            ) {
                Text(
                    text = stringResource(MR.strings.reorder),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(MR.strings.action_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    )
}

@Composable
private fun DraggableFilterItem(
    filterId: LibraryFilterId,
    isDragging: Boolean,
    visualOffset: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelRes = when (filterId) {
        LibraryFilterId.DOWNLOADED -> MR.strings.label_downloaded
        LibraryFilterId.UNREAD -> MR.strings.action_filter_unread
        LibraryFilterId.STARTED -> MR.strings.label_started
        LibraryFilterId.BOOKMARKED -> MR.strings.action_filter_bookmarked
        LibraryFilterId.COMPLETED -> MR.strings.completed
        LibraryFilterId.TRACKED -> MR.strings.action_filter_tracked
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = visualOffset }
            .shadow(
                elevation = if (isDragging) 8.dp else 0.dp,
                shape = RoundedCornerShape(8.dp),
            )
            .scale(if (isDragging) 1.02f else 1f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                )
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT_DP)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(MR.strings.reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isDragging) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}
