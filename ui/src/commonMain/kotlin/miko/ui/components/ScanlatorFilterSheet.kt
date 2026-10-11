package miko.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Port of Android's `ScanlatorFilterDialog` — checkbox list of the entry's
 * scanlator groups; checked groups are excluded from the chapter list.
 * Opened from the chapter settings sheet's Filter tab.
 */
@Composable
fun ScanlatorFilterSheet(
    scanlators: List<String>,
    initialExcluded: Set<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var excluded by remember(initialExcluded) { mutableStateOf(initialExcluded) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.exclude_scanlators)) },
        text = {
            if (scanlators.isEmpty()) {
                Text(stringResource(MR.strings.no_scanlators_found))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(scanlators, key = { it }) { scanlator ->
                        val checked = scanlator in excluded
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    excluded = if (checked) {
                                        excluded - scanlator
                                    } else {
                                        excluded + scanlator
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(
                                text = scanlator,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(excluded) },
                enabled = scanlators.isNotEmpty(),
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
