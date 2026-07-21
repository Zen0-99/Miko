package eu.kanade.presentation.browse.manga

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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.extension.manga.interactor.MangaExtensionSourceItem
import eu.kanade.presentation.browse.manga.components.MangaExtensionIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TrailingWidgetBuffer
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.ui.browse.manga.extension.details.MangaExtensionDetailsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun MangaExtensionDetailsScreen(
    navigateUp: () -> Unit,
    state: MangaExtensionDetailsScreenModel.State,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickEnableAll: () -> Unit,
    onClickDisableAll: () -> Unit,
    onClickClearCookies: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickIncognito: (Boolean) -> Unit,
    onClickMigrate: (mangaId: Long) -> Unit = {},
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
        if (state.extension == null) {
            EmptyScreen(
                MR.strings.empty_screen,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        ExtensionDetails(
            contentPadding = paddingValues,
            extension = state.extension,
            sources = state.sources,
            migrateItems = state.migrateItems,
            incognitoMode = state.isIncognito,
            onClickSourcePreferences = onClickSourcePreferences,
            onClickEnableAll = onClickEnableAll,
            onClickDisableAll = onClickDisableAll,
            onClickUninstall = onClickUninstall,
            onClickSource = onClickSource,
            onClickIncognito = onClickIncognito,
            onClickMigrate = onClickMigrate,
        )
    }
}

@Composable
private fun ExtensionDetails(
    contentPadding: PaddingValues,
    extension: MangaExtension.Installed,
    sources: ImmutableList<MangaExtensionSourceItem>,
    migrateItems: ImmutableList<MangaExtensionDetailsScreenModel.MigrateMangaItem>,
    incognitoMode: Boolean,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickEnableAll: () -> Unit,
    onClickDisableAll: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickIncognito: (Boolean) -> Unit,
    onClickMigrate: (mangaId: Long) -> Unit = {},
) {
    val context = LocalContext.current
    var showNsfwWarning by remember { mutableStateOf(false) }

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
                extIncognitoMode = incognitoMode,
                isExtensionEnabled = sources.any { it.enabled },
                onToggleExtension = { enabled ->
                    if (enabled) onClickEnableAll() else onClickDisableAll()
                },
                onClickUninstall = onClickUninstall,
                onClickAppInfo = {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", extension.pkgName, null)
                        context.startActivity(this)
                    }
                    Unit
                }.takeIf { extension.isShared },
                onClickAgeRating = {
                    showNsfwWarning = true
                },
                onExtIncognitoChange = onClickIncognito,
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

        items(
            items = sources,
            key = { it.source.id },
        ) { source ->
            SourceSwitchPreference(
                modifier = Modifier.animateItem(),
                source = source,
                onClickSourcePreferences = onClickSourcePreferences,
                onClickSource = onClickSource,
            )
        }

        // Migration section — show actual favorite titles from this extension's sources
        item {
            Text(
                text = stringResource(MR.strings.label_migration),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
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
                key = { "migrate-${it.manga.id}" },
            ) { item ->
                MigrateListItem(
                    title = item.manga.title,
                    author = item.manga.author,
                    coverData = MangaCover(
                        mangaId = item.manga.id,
                        sourceId = item.manga.source,
                        isMangaFavorite = item.manga.favorite,
                        url = item.manga.thumbnailUrl,
                        lastModified = item.manga.coverLastModified,
                    ),
                    onClick = { onClickMigrate(item.manga.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
    if (showNsfwWarning) {
        NsfwWarningDialog(
            onClickConfirm = {
                showNsfwWarning = false
            },
        )
    }
}

@Composable
private fun DetailsHeader(
    extension: MangaExtension,
    extIncognitoMode: Boolean,
    isExtensionEnabled: Boolean,
    onToggleExtension: (Boolean) -> Unit,
    onClickAgeRating: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickAppInfo: (() -> Unit)?,
    onExtIncognitoChange: (Boolean) -> Unit,
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

                        if (extension is MangaExtension.Installed) {
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
            MangaExtensionIcon(
                modifier = Modifier
                    .size(48.dp)
                    .then(if (!isExtensionEnabled) Modifier.alpha(0.4f) else Modifier),
                extension = extension,
                density = DisplayMetrics.DENSITY_XXXHIGH,
            )

            Spacer(modifier = Modifier.width(MaterialTheme.padding.medium))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (!isExtensionEnabled) Modifier.alpha(0.4f) else Modifier),
            ) {
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

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
                            modifier = Modifier.clickable(onClick = onClickAgeRating),
                        )
                    }
                }
            }

            Switch(
                checked = isExtensionEnabled,
                onCheckedChange = onToggleExtension,
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

        // Extra spacing before the languages section
        Spacer(modifier = Modifier.height(MaterialTheme.padding.large))

        TextPreferenceWidget(
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.small),
            title = stringResource(MR.strings.pref_incognito_mode),
            subtitle = stringResource(MR.strings.pref_incognito_mode_extension_summary),
            icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
            widget = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = extIncognitoMode,
                        onCheckedChange = onExtIncognitoChange,
                        modifier = Modifier.padding(start = TrailingWidgetBuffer),
                    )
                }
            },
        )
    }
}

@Composable
private fun MigrateListItem(
    title: String,
    author: String?,
    coverData: MangaCover,
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
    source: MangaExtensionSourceItem,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
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
                if (source.source is ConfigurableSource) {
                    IconButton(onClick = { onClickSourcePreferences(source.source.id) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(MR.strings.label_settings),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

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

@Composable
fun NsfwWarningDialog(
    onClickConfirm: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(text = stringResource(MR.strings.ext_nsfw_warning))
        },
        confirmButton = {
            TextButton(onClick = onClickConfirm) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        onDismissRequest = onClickConfirm,
    )
}
