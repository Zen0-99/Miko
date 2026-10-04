package eu.kanade.presentation.collection.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.asToggleableState
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.entries.components.accentTintedSurface
import eu.kanade.presentation.entries.components.rememberOnAccentColor
import eu.kanade.presentation.collection.visualName
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.collection.model.Collection
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun CollectionCreateDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String) -> Unit,
    collections: ImmutableList<String>,
) {
    var name by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name) { collections.contains(name) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = name.isNotEmpty() && !nameAlreadyExists,
                onClick = {
                    onCreate(name)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_add_collection))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier
                    .focusRequester(focusRequester),
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(text = stringResource(MR.strings.name))
                },
                supportingText = {
                    val msgRes = if (name.isNotEmpty() && nameAlreadyExists) {
                        MR.strings.error_collection_exists
                    } else {
                        MR.strings.information_required_plain
                    }
                    Text(text = stringResource(msgRes))
                },
                isError = name.isNotEmpty() && nameAlreadyExists,
                singleLine = true,
            )
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun CollectionRenameDialog(
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    collections: ImmutableList<String>,
    collection: String,
) {
    var name by remember { mutableStateOf(collection) }
    var valueHasChanged by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name) { collections.contains(name) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = valueHasChanged && !nameAlreadyExists,
                onClick = {
                    onRename(name)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_rename_collection))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.focusRequester(focusRequester),
                value = name,
                onValueChange = {
                    valueHasChanged = name != it
                    name = it
                },
                label = { Text(text = stringResource(MR.strings.name)) },
                supportingText = {
                    val msgRes = if (valueHasChanged && nameAlreadyExists) {
                        MR.strings.error_collection_exists
                    } else {
                        MR.strings.information_required_plain
                    }
                    Text(text = stringResource(msgRes))
                },
                isError = valueHasChanged && nameAlreadyExists,
                singleLine = true,
            )
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun CollectionDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    collection: String,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.delete_collection))
        },
        text = {
            Text(text = stringResource(MR.strings.delete_collection_confirmation, collection))
        },
    )
}

@Composable
fun ChangeCollectionDialog(
    initialSelection: ImmutableList<CheckboxState<Collection>>,
    onDismissRequest: () -> Unit,
    onEditCollections: () -> Unit,
    onConfirm: (List<Long>, List<Long>) -> Unit,
    accentColor: Color? = null,
) {
    // Bottom-sheet presentation (same surface/title/button language as the
    // catalog manage sheet) — slides on phones, centered dialog on tablets.
    // Controls take the detail-view accent, not theme primary — the same
    // tinting the chapter cards and carrier sheets use.
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = accent)
    val checkboxColors = CheckboxDefaults.colors(
        checkedColor = accent,
        // Black on light accents, white on dark — same rule as the
        // pills' selected-label color.
        checkmarkColor = rememberOnAccentColor(accentColor),
        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
        containerColor = accentTintedSurface(
            accent = accent,
            background = MaterialTheme.colorScheme.background,
            dimmed = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
        ) {
            Text(
                text = stringResource(MR.strings.action_move_collection),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp,
                ),
            )

            if (initialSelection.isEmpty()) {
                Text(
                    text = stringResource(MR.strings.information_empty_collection_dialog),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onEditCollections()
                        },
                        colors = buttonColors,
                    ) {
                        Text(text = stringResource(MR.strings.action_edit_collections))
                    }
                }
            } else {
                var selection by remember { mutableStateOf(initialSelection) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    selection.forEach { checkbox ->
                        val onChange: (CheckboxState<Collection>) -> Unit = {
                            val index = selection.indexOf(it)
                            if (index != -1) {
                                val mutableList = selection.toMutableList()
                                mutableList[index] = it.next()
                                selection = mutableList.toList().toImmutableList()
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // The 48dp checkbox touch target pads its
                                // visible box ~14dp — 10dp of row padding
                                // lands the box flush with the 24dp title.
                                .padding(horizontal = 10.dp)
                                .clickable { onChange(checkbox) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when (checkbox) {
                                is CheckboxState.TriState -> {
                                    TriStateCheckbox(
                                        state = checkbox.asToggleableState(),
                                        onClick = { onChange(checkbox) },
                                        colors = checkboxColors,
                                    )
                                }
                                is CheckboxState.State -> {
                                    Checkbox(
                                        checked = checkbox.isChecked,
                                        onCheckedChange = { onChange(checkbox) },
                                        colors = checkboxColors,
                                    )
                                }
                            }

                            Text(
                                text = checkbox.value.visualName,
                                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onEditCollections()
                        },
                        colors = buttonColors,
                    ) {
                        Text(text = stringResource(MR.strings.action_edit))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onDismissRequest,
                        colors = buttonColors,
                    ) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onConfirm(
                                selection
                                    .filter { it is CheckboxState.State.Checked || it is CheckboxState.TriState.Include }
                                    .map { it.value.id },
                                selection
                                    .filter { it is CheckboxState.State.None || it is CheckboxState.TriState.None }
                                    .map { it.value.id },
                            )
                        },
                        colors = buttonColors,
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                }
            }
        }
    }
}
