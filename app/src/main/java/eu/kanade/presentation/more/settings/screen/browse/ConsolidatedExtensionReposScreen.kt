package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoConfirmDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoConflictDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoCreateDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoDeleteDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionReposTabContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.domain.extensionrepo.service.ExtensionRepoService
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ConsolidatedExtensionReposScreen(
    private val url: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val animeScreenModel = rememberScreenModel { AnimeExtensionReposScreenModel() }
        val mangaScreenModel = rememberScreenModel { MangaExtensionReposScreenModel() }
        val novelScreenModel = rememberScreenModel { NovelExtensionReposScreenModel() }

        val animeState by animeScreenModel.state.collectAsState()
        val mangaState by mangaScreenModel.state.collectAsState()
        val novelState by novelScreenModel.state.collectAsState()

        val tabs = listOf(
            AYMR.strings.label_anime,
            AYMR.strings.label_manga,
            AYMR.strings.label_novel,
        )
        val pagerState = rememberPagerState { tabs.size }
        val scope = rememberCoroutineScope()
        val repoService = remember { Injekt.get<ExtensionRepoService>() }

        // Shared create dialog state — probes the repo type and routes to the
        // appropriate tab regardless of which tab the user is currently on.
        var showSharedCreateDialog by remember { mutableStateOf(false) }
        var isProbing by remember { mutableStateOf(false) }

        // All existing repo URLs (across all tabs) for duplicate detection
        val animeRepos = (animeState as? RepoScreenState.Success)?.repos ?: emptyList()
        val mangaRepos = (mangaState as? RepoScreenState.Success)?.repos ?: emptyList()
        val novelRepos = (novelState as? RepoScreenState.Success)?.repos ?: emptyList()
        val allRepoUrls = (animeRepos + mangaRepos + novelRepos).map { it.baseUrl }.toImmutableSet()

        LaunchedEffect(url) {
            url?.let { animeScreenModel.showDialog(RepoDialog.Confirm(it)) }
        }

        // Probes a repo URL and routes the create to the appropriate tab.
        suspend fun probeAndCreateRepo(repoUrl: String) {
            isProbing = true
            try {
                val type = repoService.probeRepoType(repoUrl)
                val targetIndex = when (type) {
                    "anime" -> 0
                    "novel" -> 2
                    else -> 1 // manga
                }
                // Navigate to the target tab
                if (pagerState.currentPage != targetIndex) {
                    pagerState.animateScrollToPage(targetIndex)
                }
                // Call the appropriate screen model's createRepo
                when (targetIndex) {
                    0 -> animeScreenModel.createRepo(repoUrl)
                    1 -> mangaScreenModel.createRepo(repoUrl)
                    2 -> novelScreenModel.createRepo(repoUrl)
                }
            } finally {
                isProbing = false
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    navigateUp = navigator::pop,
                    title = stringResource(MR.strings.label_extension_repos),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            onClick = {
                                when (pagerState.currentPage) {
                                    0 -> animeScreenModel.refreshRepos()
                                    1 -> mangaScreenModel.refreshRepos()
                                    2 -> novelScreenModel.refreshRepos()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(resource = MR.strings.action_webview_refresh),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(text = stringResource(MR.strings.action_add)) },
                    icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = "") },
                    onClick = { showSharedCreateDialog = true },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                ) {
                    tabs.forEachIndexed { index, titleRes ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { TabText(text = stringResource(titleRes)) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> RepoTab(
                            state = animeState,
                            onClickCreate = { animeScreenModel.showDialog(RepoDialog.Create) },
                            onOpenWebsite = { context.openInBrowser(it.website) },
                            onClickDelete = { animeScreenModel.showDialog(RepoDialog.Delete(it)) },
                        )
                        1 -> RepoTab(
                            state = mangaState,
                            onClickCreate = { mangaScreenModel.showDialog(RepoDialog.Create) },
                            onOpenWebsite = { context.openInBrowser(it.website) },
                            onClickDelete = { mangaScreenModel.showDialog(RepoDialog.Delete(it)) },
                        )
                        2 -> RepoTab(
                            state = novelState,
                            onClickCreate = { novelScreenModel.showDialog(RepoDialog.Create) },
                            onOpenWebsite = { context.openInBrowser(it.website) },
                            onClickDelete = { novelScreenModel.showDialog(RepoDialog.Delete(it)) },
                        )
                    }
                }
            }
        }

        // Dialogs for the currently selected tab.
        when (pagerState.currentPage) {
            0 -> RepoDialogs(
                state = animeState as? RepoScreenState.Success,
                onDismiss = animeScreenModel::dismissDialog,
                onCreate = { animeScreenModel.createRepo(it) },
                onDelete = { animeScreenModel.deleteRepo(it) },
                onMigrate = { animeScreenModel.replaceRepo(it) },
            )
            1 -> RepoDialogs(
                state = mangaState as? RepoScreenState.Success,
                onDismiss = mangaScreenModel::dismissDialog,
                onCreate = { mangaScreenModel.createRepo(it) },
                onDelete = { mangaScreenModel.deleteRepo(it) },
                onMigrate = { mangaScreenModel.replaceRepo(it) },
            )
            2 -> RepoDialogs(
                state = novelState as? RepoScreenState.Success,
                onDismiss = novelScreenModel::dismissDialog,
                onCreate = { novelScreenModel.createRepo(it) },
                onDelete = { novelScreenModel.deleteRepo(it) },
                onMigrate = { novelScreenModel.replaceRepo(it) },
            )
        }

        // Toast events for each screen model.
        LaunchedEffect(Unit) {
            animeScreenModel.events.collectLatest { event ->
                if (event is RepoEvent.LocalizedMessage) context.toast(event.stringRes)
            }
        }
        LaunchedEffect(Unit) {
            mangaScreenModel.events.collectLatest { event ->
                if (event is RepoEvent.LocalizedMessage) context.toast(event.stringRes)
            }
        }
        LaunchedEffect(Unit) {
            novelScreenModel.events.collectLatest { event ->
                if (event is RepoEvent.LocalizedMessage) context.toast(event.stringRes)
            }
        }

        // Shared create dialog — probes the repo type and routes to the
        // appropriate tab. Shown when the FAB is clicked, regardless of the
        // current tab.
        if (showSharedCreateDialog) {
            ExtensionRepoCreateDialog(
                onDismissRequest = { showSharedCreateDialog = false },
                onCreate = { repoUrl ->
                    showSharedCreateDialog = false
                    scope.launch { probeAndCreateRepo(repoUrl) }
                },
                repoUrls = allRepoUrls,
            )
        }
    }
}

@Composable
private fun RepoTab(
    state: RepoScreenState,
    onClickCreate: () -> Unit,
    onOpenWebsite: (mihon.domain.extensionrepo.model.ExtensionRepo) -> Unit,
    onClickDelete: (String) -> Unit,
) {
    when (state) {
        is RepoScreenState.Loading -> LoadingScreen()
        is RepoScreenState.Success -> ExtensionReposTabContent(
            state = state,
            onClickCreate = onClickCreate,
            onOpenWebsite = onOpenWebsite,
            onClickDelete = onClickDelete,
        )
    }
}

@Composable
private fun RepoDialogs(
    state: RepoScreenState.Success?,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMigrate: (mihon.domain.extensionrepo.model.ExtensionRepo) -> Unit,
) {
    val successState = state ?: return
    when (val dialog = successState.dialog) {
        null -> {}
        is RepoDialog.Create -> {
            ExtensionRepoCreateDialog(
                onDismissRequest = onDismiss,
                onCreate = onCreate,
                repoUrls = successState.repos.map { it.baseUrl }.toImmutableSet(),
            )
        }
        is RepoDialog.Delete -> {
            ExtensionRepoDeleteDialog(
                onDismissRequest = onDismiss,
                onDelete = { onDelete(dialog.repo) },
                repo = dialog.repo,
            )
        }
        is RepoDialog.Conflict -> {
            ExtensionRepoConflictDialog(
                onDismissRequest = onDismiss,
                onMigrate = { onMigrate(dialog.newRepo) },
                oldRepo = dialog.oldRepo,
                newRepo = dialog.newRepo,
            )
        }
        is RepoDialog.Confirm -> {
            ExtensionRepoConfirmDialog(
                onDismissRequest = onDismiss,
                onCreate = { onCreate(dialog.url) },
                repo = dialog.url,
            )
        }
    }
}
