package miko.ui.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Mirrors the Android extensions list: an "Installed" section then the
 * available plugins grouped by language. All kinds list and install —
 * novel-kind ones feed the iOS JS source bridge; other kinds park until
 * their bridges exist.
 */
@Composable
fun ExtensionsScreen(
    model: ExtensionsScreenModel,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()

    when {
        !state.availableLoaded && state.installed.isEmpty() -> {
            LoadingScreen(modifier = modifier)
        }
        state.installed.isEmpty() && state.available.isEmpty() -> {
            if (state.availableError) {
                EmptyScreen(
                    stringRes = MR.strings.extension_api_error,
                    modifier = modifier,
                    actions = persistentListOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.action_retry,
                            icon = Icons.Outlined.Refresh,
                            onClick = model::refresh,
                        ),
                    ),
                )
            } else {
                EmptyScreen(
                    stringRes = MR.strings.label_extensions,
                    modifier = modifier,
                )
            }
        }
        else -> ExtensionList(model = model, state = state, modifier = modifier)
    }
}

@Composable
private fun ExtensionList(
    model: ExtensionsScreenModel,
    state: ExtensionsScreenModel.State,
    modifier: Modifier = Modifier,
) {
    val installedIds = state.installed.map { it.id }.toSet()
    val notInstalled = state.available
        .filterNot { it.id in installedIds }
        .filterNot { it.isKotlinExtension }
    // Language groups mirror Android's per-lang available sections: "all"
    // first, then alphabetical.
    val availableByLang = notInstalled
        .groupBy { it.lang.ifBlank { "?" } }
        .toSortedMap(compareBy({ it != "all" }, { it }))

    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (state.installed.isNotEmpty()) {
            item(key = "header-installed") {
                SectionHeader(stringResource(MR.strings.ext_installed))
            }
            items(state.installed, key = { "installed-${it.id}" }) { plugin ->
                InstalledPluginRow(
                    plugin = plugin,
                    busy = plugin.id in state.installingIds,
                    onUninstall = { model.uninstall(plugin.id) },
                )
            }
        }

        if (notInstalled.isNotEmpty()) {
            item(key = "header-not-installed") {
                SectionHeader(stringResource(MR.strings.ext_not_installed))
            }
            availableByLang.forEach { (lang, plugins) ->
                item(key = "lang-$lang") {
                    Text(
                        text = lang.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                items(plugins, key = { "available-${it.id}" }) { plugin ->
                    AvailablePluginRow(
                        plugin = plugin,
                        busy = plugin.id in state.installingIds,
                        onInstall = { model.install(plugin) },
                    )
                }
            }
        }

        if (state.availableError && state.installed.isNotEmpty()) {
            item(key = "available-error") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = model::refresh) {
                        Text(stringResource(MR.strings.extension_api_error))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun InstalledPluginRow(
    plugin: NovelPlugin.Installed,
    busy: Boolean,
    onUninstall: () -> Unit,
) {
    PluginRowBase(
        iconUrl = plugin.iconUrl,
        name = plugin.name,
        subtitle = pluginSubtitle(plugin.versionName, plugin.lang, plugin.site, plugin.kind),
        isNsfw = plugin.isNsfw,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onUninstall) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(MR.strings.action_cancel),
                )
            }
        }
    }
}

@Composable
private fun AvailablePluginRow(
    plugin: NovelPlugin.Available,
    busy: Boolean,
    onInstall: () -> Unit,
) {
    PluginRowBase(
        iconUrl = plugin.iconUrl,
        name = plugin.name,
        subtitle = pluginSubtitle(
            plugin.versionName,
            plugin.lang,
            plugin.site.ifBlank { plugin.repoName },
            plugin.kind,
        ),
        isNsfw = plugin.isNsfw,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onInstall) {
                Icon(
                    imageVector = Icons.Outlined.GetApp,
                    contentDescription = stringResource(MR.strings.action_add),
                )
            }
        }
    }
}

@Composable
private fun PluginRowBase(
    iconUrl: String?,
    name: String,
    subtitle: String,
    isNsfw: Boolean,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (iconUrl != null) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                if (isNsfw) {
                    Badge { Text(stringResource(MR.strings.ext_nsfw_short)) }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        action()
    }
}

private fun pluginSubtitle(
    versionName: String,
    lang: String,
    siteOrRepo: String,
    kind: String,
): String {
    return buildList {
        if (lang.isNotBlank()) add(lang.uppercase())
        if (versionName.isNotBlank()) add(versionName)
        if (kind != "novel") add(kind.uppercase())
        if (siteOrRepo.isNotBlank()) add(siteOrRepo)
    }.joinToString(" · ")
}
