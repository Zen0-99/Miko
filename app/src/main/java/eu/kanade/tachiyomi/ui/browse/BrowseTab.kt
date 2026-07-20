package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.globalOverflowActions
import eu.kanade.presentation.components.useSharedTopBar
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.presentation.more.settings.screen.browse.ConsolidatedExtensionReposScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.anime.source.animeSourcesTab
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.mangaSourcesTab
import eu.kanade.tachiyomi.ui.browse.novel.source.novelSourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object BrowseTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current is BrowseTab
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalAnimeSearchScreen())
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val contentMode by uiPreferences.contentMode().collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        // Mode-aware source tab
        val sourceTab = when (contentMode) {
            ContentMode.ANIME -> animeSourcesTab()
            ContentMode.MANGA -> mangaSourcesTab()
            ContentMode.NOVEL -> novelSourcesTab()
        }

        val titleRes = MR.strings.browse

        // Card design preferences
        val sourcePreferences: SourcePreferences = remember { Injekt.get() }
        val cardDesign by sourcePreferences.browseCardDesign().changes().collectAsState(false)
        val cardColumns by sourcePreferences.browseCardColumns().changes().collectAsState(2)
        var showColumnSelector by remember { mutableStateOf(false) }

        // Register with shared top bar
        val extensionAction = listOf<AppBar.AppBarAction>(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.label_extension_repos),
                onClick = { navigator.push(ConsolidatedExtensionReposScreen()) },
            ),
        )
        val columnSelectorAction = if (cardDesign) {
            listOf<AppBar.AppBarAction>(
                AppBar.Action(
                    title = "Columns",
                    icon = Icons.Outlined.GridView,
                    onClick = { showColumnSelector = true },
                ),
            )
        } else {
            emptyList()
        }
        val allActions = (sourceTab.actions + columnSelectorAction + extensionAction + globalOverflowActions(
            onClickSettings = { navigator.push(SettingsScreen()) },
        )).toImmutableList()
        useSharedTopBar(
            title = stringResource(titleRes),
            actions = allActions,
        )

        // Column selector dialog
        if (showColumnSelector) {
            ColumnSelectorDialog(
                currentColumns = cardColumns,
                onSelect = { cols ->
                    sourcePreferences.browseCardColumns().set(cols)
                    showColumnSelector = false
                },
                onDismiss = { showColumnSelector = false },
            )
        }

        Scaffold(
            topBar = {},
            snackbarHost = {
                SnackbarHost(
                    snackbarHostState,
                    modifier = Modifier.padding(bottom = 80.dp),
                )
            },
        ) { padding ->
            val hostBottom = eu.kanade.presentation.components.LocalHostScaffoldContentPadding.current
                ?.calculateBottomPadding() ?: androidx.compose.ui.unit.Dp.Hairline
            val resolvedPadding = androidx.compose.foundation.layout.PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + hostBottom,
            )
            sourceTab.content(resolvedPadding, snackbarHostState)
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

@Composable
private fun ColumnSelectorDialog(
    currentColumns: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(1 to "1 column", 2 to "2 columns", 3 to "3 columns")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Card columns") },
        text = {
            Column {
                options.forEach { (cols, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(cols) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = cols == currentColumns,
                            onClick = { onSelect(cols) },
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}