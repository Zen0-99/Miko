package eu.kanade.tachiyomi.ui.browse.novel.source

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.manga.ExtensionTrustDialog
import eu.kanade.presentation.browse.novel.NovelSourceOptionsDialog
import eu.kanade.presentation.browse.novel.NovelSourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelExtensionDetailsScreen
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.InstallStep
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.tachiyomi.util.system.toast
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun Screen.novelSourcesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val screenModel = rememberScreenModel { NovelSourcesScreenModel() }
    val state by screenModel.state.collectAsState()
    val extensionManager = remember { Injekt.get<NovelExtensionManager>() }

    // Track which source IDs have extension updates available + extension info for cards
    val installedExtensions by extensionManager.installedExtensionsFlow.collectAsState(emptyList())
    val sourcesWithUpdates by remember(installedExtensions) {
        derivedStateOf {
            installedExtensions.filter { it.hasUpdate }
                .flatMap { it.sources }
                .map { it.id }
                .toSet()
        }
    }
    // Map source ID → extension info for card rendering
    val sourceExtensionMap by remember(installedExtensions) {
        derivedStateOf {
            installedExtensions.flatMap { ext ->
                ext.sources.map { source -> source.id to ext }
            }.toMap()
        }
    }

    val sourcePreferences: SourcePreferences = remember { Injekt.get() }
    val cardDesignPref = sourcePreferences.browseCardDesign()
    val cardColumnsPref = sourcePreferences.browseCardColumns()
    val cardDesign by cardDesignPref.changes().collectAsState(cardDesignPref.get())
    val cardColumns by cardColumnsPref.changes().collectAsState(cardColumnsPref.get())

    return TabContent(
        titleRes = AYMR.strings.label_novel_sources,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { navigator.push(GlobalNovelSearchScreen()) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            var trustDialogExtension by remember { mutableStateOf<NovelExtension.Untrusted?>(null) }
            val scope = rememberCoroutineScope()
            val downloadStates = remember { mutableStateMapOf<String, InstallStep>() }

            NovelSourcesScreen(
                state = state,
                contentPadding = contentPadding,
                downloadStates = downloadStates,
                sourcesWithUpdates = sourcesWithUpdates,
                cardDesign = cardDesign,
                cardColumns = cardColumns,
                sourceExtensionMap = sourceExtensionMap,
                onRefresh = screenModel::findAvailableExtensions,
                onClickItem = { source, listing ->
                    Log.d("NovelSearch", "[novelSourcesTab] onClickItem - source=${source.name} (id=${source.id}), listing.query='${listing.query}', pushing BrowseNovelSourceScreen")
                    navigator.push(BrowseNovelSourceScreen(source.id, listing.query))
                },
                onClickPin = screenModel::togglePin,
                onLongClickItem = screenModel::showSourceDialog,
                onSwipeHide = screenModel::toggleSource,
                swipeToHideEnabled = screenModel.swipeToHideSource,
                onClickExtension = { source ->
                    val pkgName = sourceExtensionMap[source.id]?.pkgName
                    if (pkgName != null) {
                        navigator.push(NovelExtensionDetailsScreen(pkgName))
                    }
                },
                onClickUpdate = { source ->
                    val ext = sourceExtensionMap[source.id]
                    if (ext != null) {
                        scope.launch {
                            extensionManager.updateExtension(ext).collect { step ->
                                downloadStates[ext.pkgName] = step
                                if (step == InstallStep.Installed || step == InstallStep.Error) {
                                    downloadStates.remove(ext.pkgName)
                                }
                            }
                        }
                    }
                },
                onClickInstallExtension = { extension ->
                    scope.launch {
                        downloadStates[extension.pkgName] = InstallStep.Pending
                        extensionManager.installExtension(extension).collect { step ->
                            downloadStates[extension.pkgName] = step
                            if (step == InstallStep.Error) {
                                context.toast("Extension installation failed")
                            }
                        }
                    }
                },
                onClickTrustExtension = { extension ->
                    trustDialogExtension = extension
                },
            )

            trustDialogExtension?.let { extension ->
                ExtensionTrustDialog(
                    onClickConfirm = {
                        scope.launch { extensionManager.trust(extension) }
                        trustDialogExtension = null
                    },
                    onClickDismiss = {
                        scope.launch { extensionManager.uninstallExtension(extension) }
                        trustDialogExtension = null
                    },
                    onDismissRequest = {
                        trustDialogExtension = null
                    },
                )
            }

            state.dialog?.let { dialog ->
                val source = dialog.source
                NovelSourceOptionsDialog(
                    source = source,
                    onClickPin = {
                        screenModel.togglePin(source)
                        screenModel.closeDialog()
                    },
                    onClickDisable = {
                        screenModel.toggleSource(source)
                        screenModel.closeDialog()
                    },
                    onClickMigrate = {
                        val pkgName = sourceExtensionMap[source.id]?.pkgName
                        if (pkgName != null) {
                            navigator.push(NovelExtensionDetailsScreen(pkgName))
                        }
                        screenModel.closeDialog()
                    },
                    onClickUninstall = {
                        val pkgName = sourceExtensionMap[source.id]?.pkgName
                        if (pkgName != null) {
                            val ext = extensionManager.installedExtensionsFlow.value.find { it.pkgName == pkgName }
                            if (ext != null) {
                                extensionManager.uninstallExtension(ext)
                            }
                        }
                        screenModel.closeDialog()
                    },
                    onDismiss = screenModel::closeDialog,
                )
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        NovelSourcesScreenModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
