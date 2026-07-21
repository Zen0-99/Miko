package eu.kanade.presentation.browse.novel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Download
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.components.ExtensionCard
import eu.kanade.presentation.browse.novel.components.BaseNovelSourceItem
import eu.kanade.presentation.browse.novel.components.NovelExtensionIcon
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.ui.browse.novel.source.NovelSourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.novel.model.NovelSource
import tachiyomi.domain.source.novel.model.LocalNovelSource
import tachiyomi.domain.source.novel.model.Pin
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus

@Composable
fun NovelSourcesScreen(
    state: NovelSourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (NovelSource, Listing) -> Unit,
    onClickPin: (NovelSource) -> Unit,
    onLongClickItem: (NovelSource) -> Unit,
    onSwipeHide: (NovelSource) -> Unit,
    swipeToHideEnabled: Boolean,
    onClickExtension: (NovelSource) -> Unit = {},
    onClickInstallExtension: (NovelExtension.Available) -> Unit = {},
    onClickTrustExtension: (NovelExtension.Untrusted) -> Unit = {},
    downloadStates: SnapshotStateMap<String, InstallStep> = mutableStateMapOf(),
    sourcesWithUpdates: Set<Long> = emptySet(),
    cardDesign: Boolean = false,
    cardColumns: Int = 2,
    sourceExtensionMap: Map<Long, NovelExtension.Installed> = emptyMap(),
    onClickUpdate: (NovelSource) -> Unit = {},
) {
    var notInstalledExpanded by remember { mutableStateOf(false) }

    // Filter out available extensions when collapsed
    val visibleItems = remember(state.items, notInstalledExpanded) {
        if (notInstalledExpanded) {
            state.items
        } else {
            state.items.filterNot { it is NovelSourceUiModel.AvailableExtension }
        }
    }

    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        state.isEmpty -> EmptyScreen(
            stringRes = MR.strings.source_empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        else -> {
            if (cardDesign) {
                NovelSourcesCardView(
                    items = visibleItems,
                    contentPadding = contentPadding + topSmallPaddingValues,
                    notInstalledExpanded = notInstalledExpanded,
                    onToggleNotInstalled = { notInstalledExpanded = !notInstalledExpanded },
                    cardColumns = cardColumns,
                    sourcesWithUpdates = sourcesWithUpdates,
                    sourceExtensionMap = sourceExtensionMap,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    onClickExtension = onClickExtension,
                    onClickUpdate = onClickUpdate,
                    onClickInstallExtension = onClickInstallExtension,
                    onClickTrustExtension = onClickTrustExtension,
                    downloadStates = downloadStates,
                )
            } else {
            FastScrollLazyColumn(
                contentPadding = contentPadding + topSmallPaddingValues,
            ) {
                items(
                    items = visibleItems,
                    contentType = {
                        when (it) {
                            is NovelSourceUiModel.Header -> "header"
                            is NovelSourceUiModel.Item -> "item"
                            is NovelSourceUiModel.AvailableExtension -> "available-extension"
                            is NovelSourceUiModel.UntrustedExtension -> "untrusted-extension"
                        }
                    },
                    key = {
                        when (it) {
                            is NovelSourceUiModel.Header -> it.hashCode()
                            is NovelSourceUiModel.Item -> "source-${it.source.key()}"
                            is NovelSourceUiModel.AvailableExtension -> "available-${it.extension.pkgName}"
                            is NovelSourceUiModel.UntrustedExtension -> "untrusted-${it.extension.pkgName}"
                        }
                    },
                ) { model ->
                    when (model) {
                        is NovelSourceUiModel.Header -> {
                            when (model.language) {
                                NovelSourcesScreenModel.NOT_INSTALLED_KEY -> {
                                    NovelSourceSectionHeader(
                                        modifier = Modifier.animateItem(),
                                        text = stringResource(MR.strings.ext_not_installed),
                                        expanded = notInstalledExpanded,
                                        onClick = { notInstalledExpanded = !notInstalledExpanded },
                                    )
                                }
                                NovelSourcesScreenModel.INSTALLED_KEY -> {
                                    NovelSourceSectionHeader(
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
                        is NovelSourceUiModel.Item -> SourceItem(
                            modifier = Modifier.animateItem(),
                            source = model.source,
                            onClickItem = onClickItem,
                            onLongClickItem = onLongClickItem,
                            onClickPin = onClickPin,
                            onSwipeHide = onSwipeHide,
                            swipeToHideEnabled = swipeToHideEnabled,
                            onClickExtension = onClickExtension,
                            hasUpdate = model.source.id in sourcesWithUpdates,
                        )
                        is NovelSourceUiModel.AvailableExtension -> {
                            NovelAvailableExtensionItem(
                                modifier = Modifier.animateItem(),
                                extension = model.extension,
                                onClickInstall = onClickInstallExtension,
                                installStep = downloadStates[model.extension.pkgName] ?: InstallStep.Idle,
                            )
                        }
                        is NovelSourceUiModel.UntrustedExtension -> {
                            NovelUntrustedExtensionItem(
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
}

@Composable
private fun NovelSourcesCardView(
    items: List<NovelSourceUiModel>,
    contentPadding: PaddingValues,
    notInstalledExpanded: Boolean,
    onToggleNotInstalled: () -> Unit,
    cardColumns: Int,
    sourcesWithUpdates: Set<Long>,
    onClickItem: (NovelSource, Listing) -> Unit,
    onLongClickItem: (NovelSource) -> Unit,
    onClickExtension: (NovelSource) -> Unit,
    onClickUpdate: (NovelSource) -> Unit,
    onClickInstallExtension: (NovelExtension.Available) -> Unit,
    onClickTrustExtension: (NovelExtension.Untrusted) -> Unit,
    downloadStates: SnapshotStateMap<String, InstallStep>,
    sourceExtensionMap: Map<Long, NovelExtension.Installed>,
) {
    // Group items by section (header + items)
    val sectionedItems = remember(items) {
        val sections = mutableListOf<Pair<NovelSourceUiModel.Header?, List<NovelSourceUiModel>>>()
        var currentHeader: NovelSourceUiModel.Header? = null
        var currentItems = mutableListOf<NovelSourceUiModel>()
        for (item in items) {
            when (item) {
                is NovelSourceUiModel.Header -> {
                    if (currentHeader != null || currentItems.isNotEmpty()) {
                        sections.add(currentHeader to currentItems)
                    }
                    currentHeader = item
                    currentItems = mutableListOf()
                }
                else -> currentItems.add(item)
            }
        }
        if (currentHeader != null || currentItems.isNotEmpty()) {
            sections.add(currentHeader to currentItems)
        }
        sections
    }

    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        sectionedItems.forEach { (header, sectionItems) ->
            item(key = header?.hashCode() ?: "no-header") {
                if (header != null) {
                    when (header.language) {
                        NovelSourcesScreenModel.NOT_INSTALLED_KEY -> {
                            NovelSourceSectionHeader(
                                text = stringResource(MR.strings.ext_not_installed),
                                expanded = notInstalledExpanded,
                                onClick = onToggleNotInstalled,
                            )
                        }
                        NovelSourcesScreenModel.INSTALLED_KEY -> {
                            NovelSourceSectionHeader(
                                text = stringResource(MR.strings.ext_installed),
                            )
                        }
                        else -> {
                            SourceHeader(language = header.language)
                        }
                    }
                }
            }

            // Render items as cards in FlowRow
            if (header?.language != NovelSourcesScreenModel.NOT_INSTALLED_KEY || notInstalledExpanded) {
                item(key = "cards-${header?.hashCode() ?: "no-header"}") {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = cardColumns,
                    ) {
                        sectionItems.forEach { model ->
                            when (model) {
                                is NovelSourceUiModel.Item -> {
                                    val ext = sourceExtensionMap[model.source.id]
                                    val hasUpdate = model.source.id in sourcesWithUpdates
                                    val isUpdating = ext != null && downloadStates[ext.pkgName]?.let {
                                        it == InstallStep.Pending || it == InstallStep.Downloading
                                    } == true
                                    ExtensionCard(
                                        modifier = Modifier.weight(1f),
                                        title = model.source.name,
                                        lang = model.source.lang.uppercase(),
                                        version = ext?.versionName ?: "",
                                        iconDrawable = ext?.icon,
                                        hasUpdate = hasUpdate,
                                        isUpdating = isUpdating,
                                        onClick = { onClickItem(model.source, Listing.Popular) },
                                        onCogClick = {
                                            if (hasUpdate) onClickUpdate(model.source)
                                            else onClickExtension(model.source)
                                        },
                                    )
                                }
                                is NovelSourceUiModel.AvailableExtension -> {
                                    ExtensionCard(
                                        modifier = Modifier.weight(1f),
                                        title = model.extension.name,
                                        lang = model.extension.lang.uppercase(),
                                        version = model.extension.versionName,
                                        iconUrl = model.extension.iconUrl,
                                        onClick = { onClickInstallExtension(model.extension) },
                                        onCogClick = {},
                                    )
                                }
                                else -> {}
                            }
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
        NovelSourcesScreenModel.NOT_INSTALLED_KEY -> stringResource(MR.strings.ext_not_installed)
        NovelSourcesScreenModel.INSTALLED_KEY -> stringResource(MR.strings.ext_installed)
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
private fun NovelSourceSectionHeader(
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
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
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
private fun NovelAvailableExtensionItem(
    extension: NovelExtension.Available,
    onClickInstall: (NovelExtension.Available) -> Unit,
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
        NovelExtensionIcon(
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

@Composable
private fun NovelUntrustedExtensionItem(
    extension: NovelExtension.Untrusted,
    onClickTrust: (NovelExtension.Untrusted) -> Unit,
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
private fun SourceItem(
    source: NovelSource,
    onClickItem: (NovelSource, Listing) -> Unit,
    onLongClickItem: (NovelSource) -> Unit,
    onClickPin: (NovelSource) -> Unit,
    onSwipeHide: (NovelSource) -> Unit,
    swipeToHideEnabled: Boolean,
    onClickExtension: (NovelSource) -> Unit = {},
    hasUpdate: Boolean = false,
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
        BaseNovelSourceItem(
            modifier = Modifier,
            source = source,
            onClickItem = { onClickItem(source, Listing.Popular) },
            onLongClickItem = { onLongClickItem(source) },
            action = {
                // Cog icon — opens extension details. Changes to download icon when update available.
                if (source.id != LocalNovelSource.ID) {
                    IconButton(onClick = { onClickExtension(source) }) {
                        Icon(
                            imageVector = if (hasUpdate) Icons.Outlined.Download else Icons.Outlined.Settings,
                            contentDescription = stringResource(MR.strings.label_extension_info),
                            tint = if (hasUpdate) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA),
                        )
                    }
                }
            },
        )
    }

    if (swipeToHideEnabled) {
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
fun NovelSourceOptionsDialog(
    source: NovelSource,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
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
                if (source.id != LocalNovelSource.ID) {
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
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

sealed interface NovelSourceUiModel {
    data class Item(val source: NovelSource) : NovelSourceUiModel
    data class Header(val language: String) : NovelSourceUiModel
    data class AvailableExtension(val extension: NovelExtension.Available) : NovelSourceUiModel
    data class UntrustedExtension(val extension: NovelExtension.Untrusted) : NovelSourceUiModel
}
