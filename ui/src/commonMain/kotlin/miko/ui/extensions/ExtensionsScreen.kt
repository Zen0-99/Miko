package miko.ui.extensions

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import mihon.domain.extensionrepo.service.ExtensionIndexEntry
import tachiyomi.domain.extension.novel.langDisplayName
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * Mirrors the Android extensions list: an "Installed" section, then the
 * not-installed items grouped by language display name. Each language group
 * merges repo APK-index rows (display-only on iOS — install disabled) with
 * installable JS plugins, same merge order as Android.
 */
@Composable
fun ExtensionsScreen(
    model: ExtensionsScreenModel,
    onOpenExtensionRepos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val query = state.searchQuery

    val installed = state.installed.filter { query.isNullOrBlank() || it.matchesExtensionQuery(query) }
    val jsAvailable = state.available
        .filterNot { it.isKotlinExtension }
        .filter { query.isNullOrBlank() || it.matchesExtensionQuery(query) }
    val apkEntries = state.repoExtensions
        .filter { query.isNullOrBlank() || it.matchesExtensionQuery(query) }

    val installedIds = state.installed.map { it.id }.toSet()
    val jsNotInstalled = jsAvailable.filterNot { it.id in installedIds }

    // Language groups mirror Android: APK available rows first, then JS
    // plugins appended — keyed by language display name, "all" group first.
    val langGroups = buildMap<String, List<ExtensionListItem>> {
        val groups = mutableMapOf<String, MutableList<ExtensionListItem>>()
        apkEntries.forEach { entry ->
            groups.getOrPut(entry.lang.ifBlank { "all" }) { mutableListOf() }
                .add(ExtensionListItem.Apk(entry))
        }
        jsNotInstalled.forEach { plugin ->
            groups.getOrPut(plugin.lang.ifBlank { "all" }) { mutableListOf() }
                .add(ExtensionListItem.Js(plugin))
        }
        groups.entries
            .sortedWith(compareBy({ it.key != "all" }, { it.key }))
            .forEach { (lang, items) -> put(lang, items) }
    }

    val isEmpty = installed.isEmpty() && langGroups.isEmpty()

    when {
        !state.availableLoaded && state.installed.isEmpty() -> {
            LoadingScreen(modifier = modifier)
        }
        isEmpty -> {
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
                    stringRes = if (!query.isNullOrBlank()) {
                        MR.strings.no_results_found
                    } else {
                        MR.strings.empty_screen
                    },
                    modifier = modifier,
                    actions = persistentListOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.label_extension_repos,
                            icon = Icons.Outlined.Settings,
                            onClick = onOpenExtensionRepos,
                        ),
                    ),
                )
            }
        }
        else -> LazyColumn(modifier = modifier.fillMaxSize()) {
            if (installed.isNotEmpty()) {
                item(key = "header-installed") {
                    ExtensionHeader(stringResource(MR.strings.ext_installed))
                }
                items(installed, key = { "installed-${it.id}" }) { plugin ->
                    InstalledExtensionRow(
                        plugin = plugin,
                        busy = plugin.id in state.installingIds,
                        onUninstall = { model.uninstall(plugin.id) },
                    )
                }
            }

            langGroups.forEach { (lang, items) ->
                item(key = "lang-$lang") {
                    ExtensionHeader(langDisplayName(lang))
                }
                items(items, key = { it.key }) { item ->
                    when (item) {
                        is ExtensionListItem.Js -> JsPluginRow(
                            plugin = item.plugin,
                            busy = item.plugin.id in state.installingIds,
                            onInstall = { model.install(item.plugin) },
                        )
                        is ExtensionListItem.Apk -> ApkExtensionRow(entry = item.entry)
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
}

internal sealed interface ExtensionListItem {
    val key: String

    data class Js(val plugin: NovelPlugin.Available) : ExtensionListItem {
        override val key get() = "js-${plugin.id}"
    }

    data class Apk(val entry: ExtensionIndexEntry) : ExtensionListItem {
        override val key get() = "apk-${entry.pkgName}"
    }
}

@Composable
internal fun ExtensionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
internal fun InstalledExtensionRow(
    plugin: NovelPlugin.Installed,
    busy: Boolean,
    onUninstall: () -> Unit,
) {
    ExtensionRowBase(
        iconUrl = plugin.iconUrl,
        name = plugin.name,
        lang = plugin.lang,
        versionName = plugin.versionName,
        isNsfw = plugin.isNsfw,
        kind = plugin.kind,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
internal fun JsPluginRow(
    plugin: NovelPlugin.Available,
    busy: Boolean,
    onInstall: () -> Unit,
) {
    ExtensionRowBase(
        iconUrl = plugin.iconUrl,
        name = plugin.name,
        lang = plugin.lang,
        versionName = plugin.versionName,
        isNsfw = plugin.isNsfw,
        kind = plugin.kind,
        onRowClick = onInstall,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onInstall) {
                Icon(
                    imageVector = Icons.Outlined.GetApp,
                    contentDescription = stringResource(MR.strings.ext_install),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Repo APK-index row — same body as installable rows but the install button
 * is disabled: APKs can't run on iOS.
 */
@Composable
internal fun ApkExtensionRow(entry: ExtensionIndexEntry) {
    ExtensionRowBase(
        iconUrl = entry.iconUrl,
        name = entry.name,
        lang = entry.lang,
        versionName = entry.versionName,
        isNsfw = entry.isNsfw,
        kind = null,
    ) {
        IconButton(onClick = {}, enabled = false) {
            Icon(
                imageVector = Icons.Outlined.GetApp,
                contentDescription = stringResource(MR.strings.ext_install),
            )
        }
    }
}

@Composable
internal fun ExtensionRowBase(
    iconUrl: String?,
    name: String,
    lang: String,
    versionName: String,
    isNsfw: Boolean,
    kind: String?,
    onRowClick: () -> Unit = {},
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onRowClick, onLongClick = {})
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (iconUrl != null) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.GetApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(
                modifier = Modifier.secondaryItemAlpha(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                    Text(text = langDisplayName(lang))
                    if (versionName.isNotBlank()) {
                        Text(text = versionName)
                    }
                    if (kind != null && kind != "novel") {
                        Text(text = kind.uppercase())
                    }
                    if (isNsfw) {
                        Text(
                            text = stringResource(MR.strings.ext_nsfw_short).uppercase(),
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        action()
    }
}
