package eu.kanade.tachiyomi.ui.browse.anime.source

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
import eu.kanade.presentation.browse.anime.AnimeSourceOptionsDialog
import eu.kanade.presentation.browse.anime.AnimeSourcesScreen
import eu.kanade.presentation.browse.manga.ExtensionTrustDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeExtensionDetailsScreen
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.domain.source.service.SourcePreferences
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
fun Screen.animeSourcesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val screenModel = rememberScreenModel { AnimeSourcesScreenModel() }
    val state by screenModel.state.collectAsState()
    val extensionManager = remember { Injekt.get<AnimeExtensionManager>() }

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
    val cardDesign by sourcePreferences.browseCardDesign().changes().collectAsState(false)
    val cardColumns by sourcePreferences.browseCardColumns().changes().collectAsState(2)

    return TabContent(
        titleRes = AYMR.strings.label_anime_sources,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { navigator.push(GlobalAnimeSearchScreen()) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            var trustDialogExtension by remember { mutableStateOf<AnimeExtension.Untrusted?>(null) }
            val scope = rememberCoroutineScope()
            val downloadStates = remember { mutableStateMapOf<String, InstallStep>() }

            AnimeSourcesScreen(
                state = state,
                contentPadding = contentPadding,
                downloadStates = downloadStates,
                sourcesWithUpdates = sourcesWithUpdates,
                cardDesign = cardDesign,
                cardColumns = cardColumns,
                sourceExtensionMap = sourceExtensionMap,
                onClickItem = { source, listing ->
                    navigator.push(BrowseAnimeSourceScreen(source.id, listing.query))
                },
                onClickPin = screenModel::togglePin,
                onLongClickItem = screenModel::showSourceDialog,
                onSwipeHide = screenModel::toggleSource,
                swipeToHideEnabled = screenModel.swipeToHideSource,
                onClickExtension = { source ->
                    val pkgName = extensionManager.getExtensionPackage(source.id)
                    if (pkgName != null) {
                        navigator.push(AnimeExtensionDetailsScreen(pkgName))
                    }
                },
                onClickUpdate = { source ->
                    val ext = sourceExtensionMap[source.id]
                    if (ext != null) {
                        scope.launch {
                            extensionManager.updateExtension(ext).collect { step ->
                                downloadStates[ext.pkgName] = step
                                // Clear state when update completes or errors so icon reverts to cog
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
                AnimeSourceOptionsDialog(
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
                        val pkgName = extensionManager.getExtensionPackage(source.id)
                        if (pkgName != null) {
                            navigator.push(AnimeExtensionDetailsScreen(pkgName))
                        }
                        screenModel.closeDialog()
                    },
                    onClickUninstall = {
                        val pkgName = extensionManager.getExtensionPackage(source.id)
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
                        AnimeSourcesScreenModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
