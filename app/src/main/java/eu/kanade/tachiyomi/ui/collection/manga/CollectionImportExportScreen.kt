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
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.collection.model.Collection
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

class CollectionImportExportScreen : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { CollectionImportExportScreenModel() }
        val state by screenModel.state.collectAsState()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
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
            val pendingId = state.pendingExportCollectionId
            if (uri != null && pendingId != null) {
                screenModel.exportCollection(context, uri, pendingId)
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(AYMR.strings.collection_export) + " / " + stringResource(AYMR.strings.collection_import),
                    navigateUp = { navigator.pop() },
                )
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

                Text(
                    text = "Collections",
                    style = MaterialTheme.typography.titleMedium,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.collections, key = { it.id }) { collection ->
                        CollectionExportRow(
                            collection = collection,
                            onExport = {
                                screenModel.setPendingExport(collection.id)
                                exportLauncher.launch(collection.name + ".mcoll")
                            },
                        )
                    }
                }
            }
        }

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

@Composable
private fun CollectionExportRow(
    collection: Collection,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExport)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = collection.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Default.FileUpload, contentDescription = null)
    }
}
