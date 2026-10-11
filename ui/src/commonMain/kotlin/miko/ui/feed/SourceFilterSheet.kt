package miko.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DisabledByDefault
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import miko.ui.carrier.FeedFilter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CollapsibleBox
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Port of Android's SourceFilterXxxDialog — a bottom sheet whose header
 * holds Reset / Filter actions over the rows of a FeedFilter list. Edits
 * write through to the carrier filter objects; the Filter button applies
 * them by reloading the feed.
 */
@Composable
fun SourceFilterSheet(model: SourceFeedScreenModel) {
    val state by model.state.collectAsStateWithLifecycle()
    // FeedFilter mutations are plain var writes on delegates — a local
    // tick forces row recomposition after each edit.
    var tick by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = model::closeFilterSheet) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = model::resetFilters) {
                Text(
                    text = stringResource(MR.strings.action_reset),
                    style = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(onClick = {
                model.closeFilterSheet()
                model.onFilterChanged()
            }) {
                Text(stringResource(MR.strings.action_filter))
            }
        }
        HorizontalDivider()

        LazyColumn {
            items(state.filters) { filter ->
                @Suppress("UNUSED_EXPRESSION")
                tick
                FeedFilterItem(filter = filter, onUpdate = { tick++ })
            }
        }
    }
}

@Composable
private fun FeedFilterItem(filter: FeedFilter, onUpdate: () -> Unit) {
    when (filter.kind) {
        FeedFilter.Kind.Header -> Text(
            text = filter.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        FeedFilter.Kind.Separator -> HorizontalDivider()
        FeedFilter.Kind.CheckBox -> FilterCheckboxRow(
            label = filter.name,
            checked = filter.checked,
            onClick = {
                filter.checked = !filter.checked
                onUpdate()
            },
        )
        FeedFilter.Kind.TriState -> FilterTriStateRow(
            label = filter.name,
            state = filter.triState,
            onClick = {
                filter.triState = when (filter.triState) {
                    FeedFilter.STATE_IGNORE -> FeedFilter.STATE_INCLUDE
                    FeedFilter.STATE_INCLUDE -> FeedFilter.STATE_EXCLUDE
                    else -> FeedFilter.STATE_IGNORE
                }
                onUpdate()
            },
        )
        FeedFilter.Kind.Text -> FilterTextRow(filter = filter, onUpdate = onUpdate)
        FeedFilter.Kind.Select -> FilterSelectRow(filter = filter, onUpdate = onUpdate)
        FeedFilter.Kind.Sort -> CollapsibleBox(heading = filter.name) {
            Column {
                filter.options.forEachIndexed { index, item ->
                    FilterSortRow(
                        label = item,
                        sortDescending = if (index == filter.sortIndex) {
                            !filter.sortAscending
                        } else {
                            null
                        },
                        onClick = {
                            val ascending = if (index == filter.sortIndex) {
                                !filter.sortAscending
                            } else {
                                filter.sortAscending
                            }
                            filter.setSort(index, ascending)
                            onUpdate()
                        },
                    )
                }
            }
        }
        FeedFilter.Kind.Group -> CollapsibleBox(heading = filter.name) {
            Column {
                filter.children.forEach { child ->
                    FeedFilterItem(filter = child, onUpdate = onUpdate)
                }
            }
        }
    }
}

@Composable
private fun FilterRowBase(
    label: String,
    onClick: () -> Unit,
    widget: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        widget()
    }
}

@Composable
private fun FilterCheckboxRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    FilterRowBase(label = label, onClick = onClick) {
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun FilterTriStateRow(
    label: String,
    state: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(
            imageVector = when (state) {
                FeedFilter.STATE_INCLUDE -> Icons.Rounded.CheckBox
                FeedFilter.STATE_EXCLUDE -> Icons.Rounded.DisabledByDefault
                else -> Icons.Rounded.CheckBoxOutlineBlank
            },
            contentDescription = null,
            tint = if (state == FeedFilter.STATE_IGNORE) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FilterSortRow(
    label: String,
    sortDescending: Boolean?,
    onClick: () -> Unit,
) {
    FilterRowBase(label = label, onClick = onClick) {
        when (sortDescending) {
            true -> Icon(
                imageVector = Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            false -> Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            null -> Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun FilterTextRow(filter: FeedFilter, onUpdate: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    FilterRowBase(label = filter.name, onClick = { showDialog = true }) {
        Text(
            text = filter.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDialog) {
        var draft by remember { mutableStateOf(filter.text) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(filter.name) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    filter.text = draft
                    onUpdate()
                    showDialog = false
                }) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun FilterSelectRow(filter: FeedFilter, onUpdate: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = filter.options.getOrNull(filter.selectedIndex).orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            label = { Text(filter.name) },
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            filter.options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        filter.selectedIndex = index
                        onUpdate()
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
