package miko.ui.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import miko.ui.extensions.ApkExtensionRow
import miko.ui.extensions.ExtensionHeader
import miko.ui.extensions.ExtensionsScreenModel
import miko.ui.extensions.JsPluginRow
import tachiyomi.domain.extension.novel.langDisplayName
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Android's NovelSourcesScreen shape: an "Installed" section of sources
 * grouped by language, then a collapsible "Not Installed" section of
 * available extensions (APK rows display-only + installable JS plugins).
 */
@Composable
fun SourcesScreen(
    model: SourcesScreenModel,
    extensionsModel: ExtensionsScreenModel,
    onSourceClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val extState by extensionsModel.state.collectAsStateWithLifecycle()

    when {
        !state.isInitialized -> LoadingScreen(modifier = modifier)
        state.items.isEmpty() -> EmptyScreen(
            stringRes = MR.strings.source_empty_screen,
            modifier = modifier,
        )
        else -> LazyColumn(modifier = modifier.fillMaxSize()) {
            items(
                state.items,
                key = { item ->
                    when (item) {
                        is SourcesScreenModel.ListItem.Header ->
                            "header-${item.section.orEmpty()}-${item.key}"
                        is SourcesScreenModel.ListItem.Source -> "source-${item.source.id}"
                        is SourcesScreenModel.ListItem.JsExtension -> "js-${item.plugin.id}"
                        is SourcesScreenModel.ListItem.ApkExtension -> "apk-${item.entry.pkgName}"
                    }
                },
            ) { item ->
                when (item) {
                    is SourcesScreenModel.ListItem.Header -> when (item.key) {
                        SourcesScreenModel.INSTALLED_KEY -> ExtensionHeader(
                            stringResource(MR.strings.ext_installed),
                        )
                        SourcesScreenModel.NOT_INSTALLED_KEY -> NotInstalledHeader(
                            expanded = state.notInstalledExpanded,
                            onToggle = model::toggleNotInstalled,
                        )
                        else -> ExtensionHeader(langDisplayName(item.key))
                    }
                    is SourcesScreenModel.ListItem.Source -> SourceRow(
                        source = item.source,
                        onClick = { onSourceClick(item.source.id) },
                    )
                    is SourcesScreenModel.ListItem.JsExtension -> JsPluginRow(
                        plugin = item.plugin,
                        busy = item.plugin.id in extState.installingIds,
                        onInstall = { extensionsModel.install(item.plugin) },
                    )
                    is SourcesScreenModel.ListItem.ApkExtension -> ApkExtensionRow(
                        entry = item.entry,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotInstalledHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(MR.strings.ext_not_installed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (expanded) {
                    Icons.Outlined.KeyboardArrowUp
                } else {
                    Icons.Outlined.KeyboardArrowDown
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SourceRow(
    source: NovelCatalogueSource,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = source.name,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = langDisplayName(source.lang),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
