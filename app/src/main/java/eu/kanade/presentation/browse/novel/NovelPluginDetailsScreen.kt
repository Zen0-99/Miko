package eu.kanade.presentation.browse.novel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.novel.components.NovelPluginIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelPluginDetailsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.domain.entries.novel.model.NovelCover
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun NovelPluginDetailsScreen(
    navigateUp: () -> Unit,
    state: NovelPluginDetailsScreenModel.State,
    onClickUninstall: () -> Unit,
    onClickToggle: (Boolean) -> Unit,
    onClickMigrate: (novelId: Long) -> Unit = {},
    onClickMigrateAll: () -> Unit = {},
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_extension_info),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (state.plugin == null) {
            EmptyScreen(
                MR.strings.empty_screen,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        NovelPluginDetails(
            contentPadding = paddingValues,
            plugin = state.plugin,
            source = state.source,
            migrateItems = state.migrateItems,
            onClickUninstall = onClickUninstall,
            onClickToggle = onClickToggle,
            onClickMigrate = onClickMigrate,
            onClickMigrateAll = onClickMigrateAll,
        )
    }
}

@Composable
private fun NovelPluginDetails(
    contentPadding: PaddingValues,
    plugin: tachiyomi.domain.extension.novel.model.NovelPlugin.Installed,
    source: tachiyomi.domain.source.novel.model.NovelSource?,
    migrateItems: ImmutableList<NovelPluginDetailsScreenModel.MigrateNovelItem>,
    onClickUninstall: () -> Unit,
    onClickToggle: (Boolean) -> Unit,
    onClickMigrate: (novelId: Long) -> Unit = {},
    onClickMigrateAll: () -> Unit = {},
) {
    val context = LocalContext.current
    val isEnabled = source != null

    ScrollbarLazyColumn(
        contentPadding = contentPadding,
    ) {
        item {
            PluginDetailsHeader(
                plugin = plugin,
                source = source,
                isEnabled = isEnabled,
                onToggle = onClickToggle,
                onClickUninstall = onClickUninstall,
            )
        }

        item {
            Text(
                text = stringResource(MR.strings.label_languages),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* source settings — future */ }
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = LocaleHelper.getSourceDisplayName(plugin.lang, context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Migration section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(MR.strings.label_migration),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (migrateItems.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onClickMigrateAll),
                    ) {
                        Text(
                            text = stringResource(AYMR.strings.action_migrate_all),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = ">",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
        if (migrateItems.isEmpty()) {
            item {
                Text(
                    text = stringResource(MR.strings.information_no_entries_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                )
            }
        } else {
            items(
                items = migrateItems,
                key = { "migrate-${it.novel.id}" },
            ) { item ->
                MigrateListItem(
                    title = item.novel.title,
                    author = item.novel.author,
                    coverData = NovelCover(
                        novelId = item.novel.id,
                        sourceId = item.novel.source,
                        isNovelFavorite = item.novel.favorite,
                        url = item.novel.thumbnailUrl,
                        lastModified = item.novel.coverLastModified,
                    ),
                    onClick = { onClickMigrate(item.novel.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun PluginDetailsHeader(
    plugin: tachiyomi.domain.extension.novel.model.NovelPlugin.Installed,
    source: tachiyomi.domain.source.novel.model.NovelSource?,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClickUninstall: () -> Unit,
) {
    val context = LocalContext.current

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium)
                .padding(
                    top = MaterialTheme.padding.medium,
                    bottom = MaterialTheme.padding.small,
                )
                .clickable {
                    val debugInfo = buildString {
                        append(
                            """
                            Plugin name: ${plugin.name} (lang: ${plugin.lang}; id: ${plugin.id})
                            Plugin version: ${plugin.versionName} (code: ${plugin.versionCode})
                            NSFW: ${plugin.isNsfw}
                            Repository: ${plugin.repoUrl}
                            Site: ${plugin.site}
                            """.trimIndent(),
                        )
                    }
                    context.copyToClipboard("Plugin Debug information", debugInfo)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon — use iconUrl if available
            NovelPluginIcon(
                modifier = Modifier
                    .size(48.dp)
                    .then(if (!isEnabled) Modifier.alpha(0.4f) else Modifier),
                iconUrl = plugin.iconUrl,
            )

            Spacer(modifier = Modifier.width(MaterialTheme.padding.medium))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (!isEnabled) Modifier.alpha(0.4f) else Modifier),
            ) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = plugin.versionName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = LocaleHelper.getSourceDisplayName(plugin.lang, context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (source?.supportsComments == true) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Comment,
                            contentDescription = "Supports comments",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    if (plugin.isNsfw) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(MR.strings.ext_nsfw_short),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
            )
        }

        Row(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium)
                .padding(top = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onClickUninstall,
            ) {
                Text(stringResource(MR.strings.ext_uninstall))
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.padding.large))
    }
}

@Composable
private fun MigrateListItem(
    title: String,
    author: String?,
    coverData: NovelCover,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayTitle = if (title.isBlank()) stringResource(MR.strings.unknown) else title
    val displayAuthor = if (author.isNullOrBlank()) stringResource(MR.strings.unknown) else author
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemCover.Book(
            modifier = Modifier.fillMaxHeight(),
            data = coverData,
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
        ) {
            Text(
                text = displayTitle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = displayAuthor,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
