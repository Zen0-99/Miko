package eu.kanade.tachiyomi.ui.readingorder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

class ReadingOrderListScreen : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { ReadingOrderListScreenModel() }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(AYMR.strings.reading_order_list),
                    navigateUp = { navigator.pop() },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(AYMR.strings.reading_order_create)) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = { screenModel.showCreateDialog() },
                )
            },
        ) { padding ->
            if (state.readingOrders.isEmpty() && !state.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.reading_order_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    items(state.readingOrders, key = { it.id }) { order ->
                        ReadingOrderRow(
                            order = order,
                            onClick = { navigator.push(ReadingOrderEditorScreen(order.id)) },
                            onDelete = { screenModel.showDeleteDialog(order) },
                        )
                    }
                }
            }
        }

        when (val dialog = state.dialog) {
            is ReadingOrderListScreenModel.Dialog.Create -> CreateDialog(
                onDismiss = screenModel::closeDialog,
                onCreate = { name, desc -> screenModel.createOrder(name, desc) },
            )
            is ReadingOrderListScreenModel.Dialog.DeleteConfirm -> DeleteConfirmDialog(
                orderName = dialog.order.name,
                onConfirm = { screenModel.deleteOrder(dialog.order.id) },
                onDismiss = screenModel::closeDialog,
            )
            null -> {}
        }
    }
}

@Composable
private fun ReadingOrderRow(
    order: tachiyomi.domain.readingorder.model.ReadingOrder,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = order.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val desc = order.description
        if (!desc.isNullOrBlank()) {
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    IconButton(onClick = onDelete) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = stringResource(AYMR.strings.reading_order_delete_confirm),
        )
    }
}

@Composable
private fun CreateDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.reading_order_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(AYMR.strings.reading_order_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(AYMR.strings.reading_order_description)) },
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), description.trim().ifBlank { null }) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(tachiyomi.i18n.MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(tachiyomi.i18n.MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(
    orderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.reading_order_delete_confirm)) },
        text = { Text(orderName) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(tachiyomi.i18n.MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(tachiyomi.i18n.MR.strings.action_cancel))
            }
        },
    )
}
