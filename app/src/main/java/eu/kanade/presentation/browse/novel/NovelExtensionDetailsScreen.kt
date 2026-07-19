package eu.kanade.presentation.browse.novel

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
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
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.extension.novel.interactor.NovelExtensionSourceItem
import eu.kanade.presentation.browse.novel.components.NovelExtensionIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TrailingWidgetBuffer
import eu.kanade.tachiyomi.novelsource.ConfigurableNovelSource
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelExtensionDetailsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.domain.entries.novel.model.NovelCover
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun NovelExtensionDetailsScreen(
    navigateUp: () -> Unit,
    state: NovelExtensionDetailsScreenModel.State,
    onClickEnableAll: () -> Unit,
    onClickDisableAll: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickMigrate: (novelId: Long) -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val url = remember(state.extension) {
        val regex = """https://raw.githubusercontent.com/(.+?)/(.+?)/.+""".toRegex()
        regex.find(state.extension?.repoUrl.orEmpty())
            ?.let {
                val (user, repo) = it.destructured
                "https://github.com/$user/$repo"
            }
            ?: state.extension?.repoUrl
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_extension_info),
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        actions = persistentListOf<AppBar.AppBarAction>().builder()
                            .apply {
                                if (url != null) {
                                    add(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_open_repo),
                                            icon = Icons.AutoMirrored.Outlined.Launch,
                                            onClick = {
                                                uriHandler.openUri(url)
                                            },
                                        ),
                                    )
                                }
                                addAll(
                                    listOf(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_enable_all),
                                            onClick = onClickEnableAll,
                                        ),
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_disable_all),
                                            onClick = onClickDisableAll,
                                        ),
                                    ),
                                )
                            }
                            .build(),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (state.extension == null) {
            EmptyScreen(
                MR.strings.empty_screen,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        NovelExtensionDetails(
            contentPadding = paddingValues,
            extension = state.extension,
            sources = state.sources,
            migrateItems = state.migrateItems,
            onClickUninstall = onClickUninstall,
            onClickSource = onClickSource,
            onClickMigrate = onClickMigrate,
        )
    }
}

@Composable
private fun NovelExtensionDetails(
    contentPadding: PaddingValues,
    extension: NovelExtension.Installed,
    sources: ImmutableList<NovelExtensionSourceItem>,
    migrateItems: ImmutableList<NovelExtensionDetailsScreenModel.MigrateNovelItem>,
    onClickUninstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickMigrate: (novelId: Long) -> Unit = {},
) {
    val context = LocalContext.current

    ScrollbarLazyColumn(
        contentPadding = contentPadding,
    ) {
        if (extension.isObsolete) {
            item {
                WarningBanner(MR.strings.obsolete_extension_message)
            }
        }

        item {
            DetailsHeader(
                extension = extension,
                onClickUninstall = onClickUninstall,
                onClickAppInfo = {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", extension.pkgName, null)
                        context.startActivity(this)
                    }
                    Unit
                }.takeIf { extension.isShared },
            )
        }

        item {
            Text(
                text = stringResource(MR.strings.label_languages),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
            )
        }

        items(
            items = sources,
            key = { it.source.id },
        ) { source ->
            SourceSwitchPreference(
                modifier = Modifier.animateItem(),
                source = source,
                onClickSource = onClickSource,
            )
        }

        // Migration section — show actual favorite titles from this extension's sources
        item {
            Text(
                text = stringResource(MR.strings.label_migration),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
            )
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
private fun DetailsHeader(
    extension: NovelExtension,
    onClickUninstall: () -> Unit,
    onClickAppInfo: (() -> Unit)?,
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
                    val extDebugInfo = buildString {
                        append(
                            """
                            Extension name: ${extension.name} (lang: ${extension.lang}; package: ${extension.pkgName})
                            Extension version: ${extension.versionName} (lib: ${extension.libVersion}; version code: ${extension.versionCode})
                            NSFW: ${extension.isNsfw}
                            """.trimIndent(),
                        )

                        if (extension is NovelExtension.Installed) {
                            append("\n\n")
                            append(
                                """
                                Update available: ${extension.hasUpdate}
                                Obsolete: ${extension.isObsolete}
                                Shared: ${extension.isShared}
                                Repository: ${extension.repoUrl}
                                """.trimIndent(),
                            )
                        }
                    }
                    context.copyToClipboard("Extension Debug information", extDebugInfo)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NovelExtensionIcon(
                modifier = Modifier
                    .size(72.dp),
                extension = extension,
                density = DisplayMetrics.DENSITY_XXXHIGH,
            )

            Spacer(modifier = Modifier.width(MaterialTheme.padding.medium))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                val displayUrl = when (extension) {
                    is NovelExtension.Installed -> {
                        extension.repoUrl
                            ?: extension.sources.firstNotNullOfOrNull { (it as? NovelHttpSource)?.baseUrl }
                    }
                    is NovelExtension.Available -> extension.repoUrl
                    else -> null
                }
                if (!displayUrl.isNullOrBlank()) {
                    Text(
                        text = displayUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Version and language inline, separated by a dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = extension.versionName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = LocaleHelper.getSourceDisplayName(extension.lang, context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (extension.isNsfw) {
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

            if (onClickAppInfo != null) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onClickAppInfo,
                ) {
                    Text(
                        text = stringResource(MR.strings.ext_app_info),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        HorizontalDivider()
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

@Composable
private fun InfoText(
    primaryText: String,
    secondaryText: String,
    modifier: Modifier = Modifier,
    primaryTextStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = null, indication = null, onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier.then(clickableModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = primaryText,
            textAlign = TextAlign.Center,
            style = primaryTextStyle,
        )

        Text(
            text = secondaryText + if (onClick != null) " ⓘ" else "",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun InfoDivider() {
    VerticalDivider(
        modifier = Modifier.height(20.dp),
    )
}

@Composable
private fun SourceSwitchPreference(
    source: NovelExtensionSourceItem,
    onClickSource: (sourceId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    TextPreferenceWidget(
        modifier = modifier,
        title = if (source.labelAsName) {
            source.source.toString()
        } else {
            LocaleHelper.getSourceDisplayName(source.source.lang, context)
        },
        widget = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = source.enabled,
                    onCheckedChange = null,
                    modifier = Modifier.padding(start = TrailingWidgetBuffer),
                )
            }
        },
        onPreferenceClick = { onClickSource(source.source.id) },
    )
}
