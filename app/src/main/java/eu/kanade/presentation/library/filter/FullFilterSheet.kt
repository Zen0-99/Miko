package eu.kanade.presentation.library.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Full filter sheet — a modal bottom sheet showing all filters with Apply/Clear buttons.
 * Also has a reorder button to open the ReorderFiltersDialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullFilterSheet(
    sections: List<FilterSectionData>,
    onDismiss: () -> Unit,
    onClearFilters: () -> Unit,
    onReorderClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showReorderDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(MR.strings.action_filter),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showReorderDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(MR.strings.reorder_filters),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = {
                        scope.launch { sheetState.hide(); onDismiss() }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(MR.strings.action_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Filter sections
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(sections, key = { it.id }) { section ->
                    FilterSection(section)
                }
            }

            // Bottom buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onClearFilters()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        text = stringResource(MR.strings.clear_filters),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Button(
                    onClick = {
                        scope.launch { sheetState.hide(); onDismiss() }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = stringResource(MR.strings.action_apply),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }

    if (showReorderDialog) {
        ReorderFiltersDialog(
            onReorder = { newOrder ->
                onReorderClick(newOrder)
                showReorderDialog = false
            },
            onDismiss = { showReorderDialog = false },
        )
    }
}

@Composable
private fun FilterSection(section: FilterSectionData) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(section.titleRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        section.items.forEach { option ->
            FilterOptionRow(option)
        }
    }
}

@Composable
private fun FilterOptionRow(option: FilterOptionData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (option.isSelected) MaterialTheme.colorScheme.surfaceVariant
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .clickable(onClick = option.onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (option.isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (option.isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
