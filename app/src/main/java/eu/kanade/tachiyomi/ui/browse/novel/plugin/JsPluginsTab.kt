package eu.kanade.tachiyomi.ui.browse.novel.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.extension.novel.JsNovelPluginManager
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun novelJsPluginsTab(
    jsPluginScreenModel: JsPluginScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow

    return TabContent(
        titleRes = AYMR.strings.label_novel_extensions,
        searchEnabled = true,
        actions = kotlinx.collections.immutable.persistentListOf(),
        content = { contentPadding, _ ->
            JsPluginScreen(
                screenModel = jsPluginScreenModel,
                contentPadding = contentPadding,
            )
        },
    )
}

@Composable
private fun JsPluginScreen(
    screenModel: JsPluginScreenModel,
    contentPadding: PaddingValues,
) {
    val state by screenModel.state.collectAsState()

    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        if (state.installed.isNotEmpty()) {
            item {
                Text(
                    text = "Installed JS Plugins",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(state.installed, key = { it.id }) { plugin ->
                JsPluginRow(
                    name = plugin.name,
                    lang = plugin.lang.uppercase(),
                    version = plugin.versionName,
                    isInstalled = true,
                    onAction = { screenModel.uninstallPlugin(plugin) },
                )
                HorizontalDivider()
            }
        }

        if (state.available.isNotEmpty()) {
            item {
                Text(
                    text = "Available JS Plugins",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(state.available, key = { it.id }) { plugin ->
                JsPluginRow(
                    name = plugin.name,
                    lang = plugin.lang.uppercase(),
                    version = plugin.versionName,
                    isInstalled = false,
                    onAction = { screenModel.installPlugin(plugin) },
                )
                HorizontalDivider()
            }
        }

        if (state.installed.isEmpty() && state.available.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No JS plugins found. Pull to refresh or add a plugin repo in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun JsPluginRow(
    name: String,
    lang: String,
    version: String,
    isInstalled: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "$lang - $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onAction) {
            Icon(
                imageVector = if (isInstalled) Icons.Filled.Delete else Icons.Filled.Download,
                contentDescription = if (isInstalled) "Uninstall" else "Install",
                tint = if (isInstalled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
