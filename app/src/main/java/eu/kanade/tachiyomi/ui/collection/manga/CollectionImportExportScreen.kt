package eu.kanade.tachiyomi.ui.collection.manga

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.collection.model.Collection
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

class CollectionImportExportScreen : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { CollectionImportExportScreenModel() }
        val state by screenModel.state.collectAsState()
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                screenModel.importCollection(context, uri)
            }
        }

        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                screenModel.exportCollection(context, uri)
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(AYMR.strings.collection_export) + " / " +
                        stringResource(AYMR.strings.collection_import),
                    navigateUp = { navigator.pop() },
                )
            },
            floatingActionButton = {
                if (state.selectedCollectionIds.isNotEmpty()) {
                    androidx.compose.material3.ExtendedFloatingActionButton(
                        text = { Text(stringResource(AYMR.strings.collection_export)) },
                        icon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                        onClick = { screenModel.startExport() },
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text(
                        text = " " + stringResource(AYMR.strings.collection_import),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                // Reading order toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.collection_include_reading_orders),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.includeReadingOrders,
                        onCheckedChange = { screenModel.setIncludeReadingOrders(it) },
                    )
                }

                Text(
                    text = stringResource(AYMR.strings.collection_select_to_export),
                    style = MaterialTheme.typography.titleMedium,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.collections, key = { it.id }) { collection ->
                        CollectionExportRow(
                            collection = collection,
                            isSelected = collection.id in state.selectedCollectionIds,
                            onToggle = { screenModel.toggleCollection(collection.id) },
                        )
                    }
                }
            }
        }

        // Export button (FAB-like at bottom or as part of the column)
        if (state.selectedCollectionIds.isNotEmpty()) {
            val pendingWarnings = state.crossCollectionWarnings
            if (pendingWarnings != null && pendingWarnings.isNotEmpty() && !state.warningShown) {
                // Cross-collection warning dialog
                AlertDialog(
                    onDismissRequest = {
                        screenModel.dismissWarning()
                    },
                    title = { Text(stringResource(AYMR.strings.collection_cross_collection_warning_title)) },
                    text = {
                        Column {
                            Text(stringResource(AYMR.strings.collection_cross_collection_warning_body))
                            pendingWarnings.forEach { warning ->
                                Text(
                                    text = "\n• ${warning.readingOrderName}: ${warning.orphanedMangaTitles.joinToString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            screenModel.dismissWarning()
                            exportLauncher.launch(generateExportFileName(state.selectedCollectionIds, state.collections))
                        }) {
                            Text(stringResource(AYMR.strings.collection_export_anyway))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { screenModel.dismissWarning() }) {
                            Text(stringResource(tachiyomi.i18n.MR.strings.action_cancel))
                        }
                    },
                )
            } else if (state.warningShown || pendingWarnings == null) {
                // No warnings or already dismissed — proceed directly
                androidx.compose.runtime.LaunchedEffect(state.pendingExport) {
                    if (state.pendingExport) {
                        exportLauncher.launch(generateExportFileName(state.selectedCollectionIds, state.collections))
                        screenModel.clearPendingExport()
                    }
                }
            }
        }

        // Result dialog
        state.resultMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { screenModel.clearResult() },
                title = { Text(stringResource(AYMR.strings.collection_import)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { screenModel.clearResult() }) {
                        Text(stringResource(tachiyomi.i18n.MR.strings.action_ok))
                    }
                },
            )
        }

        // Unmatched titles dialog
        state.unmatchedTitles?.let { titles ->
            AlertDialog(
                onDismissRequest = { screenModel.clearResult() },
                title = { Text(stringResource(AYMR.strings.collection_import_unmatched)) },
                text = {
                    Column {
                        Text(stringResource(AYMR.strings.collection_import_unmatched_detail))
                        titles.forEach { title ->
                            Text(
                                text = "• $title",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { screenModel.clearResult() }) {
                        Text(stringResource(tachiyomi.i18n.MR.strings.action_ok))
                    }
                },
            )
        }
    }
}

private fun generateExportFileName(
    selectedIds: Set<Long>,
    collections: List<Collection>,
): String {
    val names = collections.filter { it.id in selectedIds }.map { it.name }
    val base = if (names.size == 1) names.first() else "collections"
    return base.replace(Regex("[^a-zA-Z0-9-_]"), "_") + ".mcoll"
}

@Composable
private fun CollectionExportRow(
    collection: Collection,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
        )
        Text(
            text = collection.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Default.FileUpload, contentDescription = null)
    }
}
