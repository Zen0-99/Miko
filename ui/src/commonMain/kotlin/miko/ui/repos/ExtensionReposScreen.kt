package miko.ui.repos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import mihon.domain.extensionrepo.model.ExtensionRepo
import miko.ui.repos.ExtensionReposScreenModel.RepoDialog
import miko.ui.repos.ExtensionReposScreenModel.RepoEvent
import miko.ui.repos.ExtensionReposScreenModel.RepoTab
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Mirrors `ConsolidatedExtensionReposScreen`: three type tabs (Shows / Manga /
 * Books), per-tab repo rows, add FAB → URL dialog → probe → create in every
 * matching tab, delete + fingerprint-conflict dialogs.
 */
@Composable
fun ExtensionReposScreen(
    model: ExtensionReposScreenModel,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current

    val tabs = listOf(
        RepoTab.ANIME to AYMR.strings.carrier_shows,
        RepoTab.MANGA to AYMR.strings.carrier_manga,
        RepoTab.NOVEL to AYMR.strings.carrier_books,
    )
    val pagerState = rememberPagerState { tabs.size }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Probe result → jump to the first matching tab (Android parity).
    LaunchedEffect(state.targetTab) {
        val target = state.targetTab ?: return@LaunchedEffect
        val index = tabs.indexOfFirst { it.first == target }
        if (index != -1 && pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
        model.consumeTargetTab()
    }

    val invalidUrlMessage = stringResource(MR.strings.invalid_repo_name)
    val repoExistsMessage = stringResource(MR.strings.error_repo_exists)
    LaunchedEffect(state.event) {
        val event = state.event ?: return@LaunchedEffect
        val message = when (event) {
            RepoEvent.InvalidUrl -> invalidUrlMessage
            RepoEvent.RepoAlreadyExists -> repoExistsMessage
        }
        snackbarHostState.showSnackbar(message)
        model.consumeEvent()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(MR.strings.label_extension_repos)) },
                actions = {
                    IconButton(
                        onClick = {
                            model.refreshTab(tabs[pagerState.currentPage].first)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(MR.strings.action_webview_refresh),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(MR.strings.action_add)) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                onClick = { showCreateDialog = true },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, (tab, titleRes) ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(titleRes)) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val tab = tabs[page].first
                RepoTabContent(
                    repos = state.reposFor(tab),
                    isInitialized = state.isInitialized,
                    onOpenWebsite = { repo ->
                        if (repo.website.isNotBlank()) uriHandler.openUri(repo.website)
                    },
                    onCopyIndexUrl = { repo ->
                        clipboard.setText(AnnotatedString("${repo.baseUrl}/index.min.json"))
                    },
                    onClickDelete = { repo -> model.showDialog(RepoDialog.Delete(tab, repo)) },
                )
            }
        }
    }

    if (showCreateDialog) {
        RepoCreateDialog(
            isProbing = state.isProbing,
            existingUrls = tabs.flatMap { state.reposFor(it.first) }.map { it.baseUrl }.toSet(),
            onDismiss = { showCreateDialog = false },
            onCreate = { url ->
                model.addRepo(url)
                showCreateDialog = false
            },
        )
    }

    when (val dialog = state.dialog) {
        is RepoDialog.Delete -> RepoDeleteDialog(
            repo = dialog.repo,
            onDismiss = model::dismissDialog,
            onDelete = {
                model.deleteRepo(dialog.type, dialog.repo.baseUrl)
                model.dismissDialog()
            },
        )
        is RepoDialog.Conflict -> RepoConflictDialog(
            oldRepo = dialog.oldRepo,
            newRepo = dialog.newRepo,
            onDismiss = model::dismissDialog,
            onMigrate = {
                model.migrateConflict(dialog.type, dialog.newRepo)
                model.dismissDialog()
            },
        )
        null -> {}
    }
}

@Composable
private fun RepoTabContent(
    repos: List<ExtensionRepo>,
    isInitialized: Boolean,
    onOpenWebsite: (ExtensionRepo) -> Unit,
    onCopyIndexUrl: (ExtensionRepo) -> Unit,
    onClickDelete: (ExtensionRepo) -> Unit,
) {
    when {
        !isInitialized -> LoadingScreen(modifier = Modifier.fillMaxSize())
        repos.isEmpty() -> EmptyScreen(
            stringRes = MR.strings.label_extension_repos,
            modifier = Modifier.fillMaxSize(),
        )
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(repos, key = { it.baseUrl }) { repo ->
                RepoRow(
                    repo = repo,
                    onOpenWebsite = { onOpenWebsite(repo) },
                    onCopyIndexUrl = { onCopyIndexUrl(repo) },
                    onDelete = { onClickDelete(repo) },
                )
            }
        }
    }
}

@Composable
private fun RepoRow(
    repo: ExtensionRepo,
    onOpenWebsite: () -> Unit,
    onCopyIndexUrl: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Label,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = repo.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = repo.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onOpenWebsite) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = stringResource(MR.strings.action_open_in_browser),
            )
        }
        IconButton(onClick = onCopyIndexUrl) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete_repo),
            )
        }
    }
}

@Composable
private fun RepoCreateDialog(
    isProbing: Boolean,
    existingUrls: Set<String>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val nameAlreadyExists = existingUrls.contains(name.trim().trimEnd('/')) ||
        existingUrls.contains(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.action_add_repo)) },
        text = {
            Column {
                Text(stringResource(MR.strings.action_add_repo_message))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(MR.strings.label_add_repo_input)) },
                    supportingText = {
                        val msgRes = if (name.isNotEmpty() && nameAlreadyExists) {
                            MR.strings.error_repo_exists
                        } else {
                            MR.strings.information_required_plain
                        }
                        Text(stringResource(msgRes))
                    },
                    isError = name.isNotEmpty() && nameAlreadyExists,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                )
                if (isProbing) {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotEmpty() && !nameAlreadyExists && !isProbing,
                onClick = { onCreate(name) },
            ) {
                Text(stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun RepoDeleteDialog(
    repo: ExtensionRepo,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.action_delete_repo)) },
        text = { Text(stringResource(MR.strings.delete_repo_confirmation, repo.baseUrl)) },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun RepoConflictDialog(
    oldRepo: ExtensionRepo,
    newRepo: ExtensionRepo,
    onDismiss: () -> Unit,
    onMigrate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.action_replace_repo_title)) },
        text = {
            Text(stringResource(MR.strings.action_replace_repo_message, newRepo.name, oldRepo.name))
        },
        confirmButton = {
            TextButton(onClick = onMigrate) {
                Text(stringResource(MR.strings.action_replace_repo))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
