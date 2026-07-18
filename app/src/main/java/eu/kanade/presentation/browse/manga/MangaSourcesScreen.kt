package eu.kanade.presentation.browse.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.manga.components.BaseMangaSourceItem
import eu.kanade.presentation.browse.manga.components.MangaExtensionIcon
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.ui.browse.manga.source.MangaSourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.manga.model.Pin
import tachiyomi.domain.source.manga.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.source.local.entries.manga.LocalMangaSource

@Composable
fun MangaSourcesScreen(
    state: MangaSourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (Source, Listing) -> Unit,
    onClickPin: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onSwipeHide: (Source) -> Unit,
    swipeToHideEnabled: Boolean,
    onClickExtension: (Source) -> Unit = {},
    onClickInstallExtension: (MangaExtension.Available) -> Unit = {},
    onClickTrustExtension: (MangaExtension.Untrusted) -> Unit = {},
    downloadStates: SnapshotStateMap<String, InstallStep> = mutableStateMapOf(),
) {
    var notInstalledExpanded by remember { mutableStateOf(false) }

    // Filter out available extensions when collapsed
    val visibleItems = remember(state.items, notInstalledExpanded) {
        if (notInstalledExpanded) {
            state.items
        } else {
            state.items.filterNot { it is MangaSourceUiModel.AvailableExtension }
        }
    }

    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        state.isEmpty -> EmptyScreen(
            stringRes = MR.strings.source_empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        else -> {
            FastScrollLazyColumn(
                contentPadding = contentPadding + topSmallPaddingValues,
            ) {
                items(
                    items = visibleItems,
                    contentType = {
                        when (it) {
                            is MangaSourceUiModel.Header -> "header"
                            is MangaSourceUiModel.Item -> "item"
                            is MangaSourceUiModel.AvailableExtension -> "available-extension"
                            is MangaSourceUiModel.UntrustedExtension -> "untrusted-extension"
                        }
                    },
                    key = {
                        when (it) {
                            is MangaSourceUiModel.Header -> it.hashCode()
                            is MangaSourceUiModel.Item -> "source-${it.source.key()}"
                            is MangaSourceUiModel.AvailableExtension -> "available-${it.extension.pkgName}"
                            is MangaSourceUiModel.UntrustedExtension -> "untrusted-${it.extension.pkgName}"
                        }
                    },
                ) { model ->
                    when (model) {
                        is MangaSourceUiModel.Header -> {
                            when (model.language) {
                                MangaSourcesScreenModel.NOT_INSTALLED_KEY -> {
                                    MangaSourceSectionHeader(
                                        modifier = Modifier.animateItem(),
                                        text = stringResource(MR.strings.ext_not_installed),
                                        expanded = notInstalledExpanded,
                                        onClick = { notInstalledExpanded = !notInstalledExpanded },
                                    )
                                }
                                MangaSourcesScreenModel.INSTALLED_KEY -> {
                                    MangaSourceSectionHeader(
                                        modifier = Modifier.animateItem(),
                                        text = stringResource(MR.strings.ext_installed),
                                    )
                                }
                                else -> {
                                    SourceHeader(
                                        modifier = Modifier.animateItem(),
                                        language = model.language,
                                    )
                                }
                            }
                        }
                        is MangaSourceUiModel.Item -> SourceItem(
                            modifier = Modifier.animateItem(),
                            source = model.source,
                            onClickItem = onClickItem,
                            onLongClickItem = onLongClickItem,
                            onClickPin = onClickPin,
                            onSwipeHide = onSwipeHide,
                            swipeToHideEnabled = swipeToHideEnabled,
                            onClickExtension = onClickExtension,
                        )
                        is MangaSourceUiModel.AvailableExtension -> {
                            MangaAvailableExtensionItem(
                                modifier = Modifier.animateItem(),
                                extension = model.extension,
                                onClickInstall = onClickInstallExtension,
                                installStep = downloadStates[model.extension.pkgName] ?: InstallStep.Idle,
                            )
                        }
                        is MangaSourceUiModel.UntrustedExtension -> {
                            MangaUntrustedExtensionItem(
                                modifier = Modifier.animateItem(),
                                extension = model.extension,
                                onClickTrust = onClickTrustExtension,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceHeader(
    language: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val text = when (language) {
        MangaSourcesScreenModel.NOT_INSTALLED_KEY -> stringResource(MR.strings.ext_not_installed)
        MangaSourcesScreenModel.INSTALLED_KEY -> stringResource(MR.strings.ext_installed)
        else -> LocaleHelper.getSourceDisplayName(language, context)
    }
    Text(
        text = text,
        modifier = modifier
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        style = MaterialTheme.typography.header,
    )
}

@Composable
private fun SourceItem(
    source: Source,
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
    onSwipeHide: (Source) -> Unit,
    swipeToHideEnabled: Boolean,
    onClickExtension: (Source) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onSwipeHide(source)
                true
            } else {
                false
            }
        },
        positionalThreshold = { distance -> distance * 0.5f },
    )

    val content: @Composable () -> Unit = {
        BaseMangaSourceItem(
            modifier = Modifier,
            source = source,
            onClickItem = { onClickItem(source, Listing.Popular) },
            onLongClickItem = { onLongClickItem(source) },
            action = {
                // Cog icon — opens extension details
                if (source.id != LocalMangaSource.ID) {
                    IconButton(onClick = { onClickExtension(source) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(MR.strings.label_extension_info),
                            tint = MaterialTheme.colorScheme.onBackground.copy(
                                alpha = SECONDARY_ALPHA,
                            ),
                        )
                    }
                }
            },
        )
    }

    if (swipeToHideEnabled && source.id != LocalMangaSource.ID) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {},
            modifier = modifier,
            content = { content() },
        )
    } else {
        content()
    }
}

@Composable
private fun SourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
    val tint = if (isPinned) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(
            alpha = SECONDARY_ALPHA,
        )
    }
    val description = if (isPinned) MR.strings.action_unpin else MR.strings.action_pin
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            tint = tint,
            contentDescription = stringResource(description),
        )
    }
}

@Composable
fun MangaSourceOptionsDialog(
    source: Source,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    // SY -->
    onClickToggleDataSaver: (() -> Unit)?,
    // SY <--
    onDismiss: () -> Unit,
    onClickMigrate: () -> Unit = {},
    onClickUninstall: () -> Unit = {},
) {
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column {
                val textId = if (Pin.Pinned in source.pin) MR.strings.action_unfavorite else MR.strings.action_favorite
                Text(
                    text = stringResource(textId),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                if (source.id != LocalMangaSource.ID) {
                    Text(
                        text = stringResource(MR.strings.action_migrate),
                        modifier = Modifier
                            .clickable(onClick = onClickMigrate)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                    Text(
                        text = stringResource(MR.strings.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                    Text(
                        text = stringResource(MR.strings.action_uninstall),
                        modifier = Modifier
                            .clickable(onClick = onClickUninstall)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // SY -->
                if (onClickToggleDataSaver != null) {
                    Text(
                        text = if (source.isExcludedFromDataSaver) {
                            stringResource(AYMR.strings.data_saver_stop_exclude)
                        } else {
                            stringResource(AYMR.strings.data_saver_exclude)
                        },
                        modifier = Modifier
                            .clickable(onClick = onClickToggleDataSaver)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // SY <--
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

@Composable
private fun MangaSourceSectionHeader(
    text: String,
    expanded: Boolean? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.medium,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (expanded != null) {
            Icon(
                imageVector = if (expanded) {
                    Icons.Outlined.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MangaUntrustedExtensionItem(
    extension: MangaExtension.Untrusted,
    onClickTrust: (MangaExtension.Untrusted) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClickTrust(extension) }
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = extension.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(MR.strings.ext_untrusted).uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.Outlined.VerifiedUser,
            contentDescription = stringResource(MR.strings.ext_trust),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MangaAvailableExtensionItem(
    extension: MangaExtension.Available,
    onClickInstall: (MangaExtension.Available) -> Unit,
    installStep: InstallStep,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClickInstall(extension) }
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaExtensionIcon(
            extension = extension,
            modifier = Modifier.size(40.dp),
        )
        Column(
            modifier = Modifier
                .padding(start = MaterialTheme.padding.medium)
                .weight(1f),
        ) {
            Text(
                text = extension.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = LocaleHelper.getSourceDisplayName(extension.lang, context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        when (installStep) {
            InstallStep.Pending, InstallStep.Downloading, InstallStep.Installing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
            InstallStep.Error -> {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(MR.strings.action_retry),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Outlined.GetApp,
                    contentDescription = stringResource(MR.strings.action_install),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

sealed interface MangaSourceUiModel {
    data class Item(val source: Source) : MangaSourceUiModel
    data class Header(val language: String) : MangaSourceUiModel
    data class AvailableExtension(val extension: MangaExtension.Available) : MangaSourceUiModel
    data class UntrustedExtension(val extension: MangaExtension.Untrusted) : MangaSourceUiModel
}
