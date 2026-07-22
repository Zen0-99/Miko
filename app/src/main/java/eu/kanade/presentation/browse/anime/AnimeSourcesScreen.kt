package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.anime.components.AnimeExtensionIcon
import eu.kanade.presentation.browse.anime.components.BaseAnimeSourceItem
import eu.kanade.presentation.browse.components.ExtensionCard
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.ui.browse.anime.source.AnimeSourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.Pin
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.source.local.entries.anime.LocalAnimeSource

@Composable
fun AnimeSourcesScreen(
    state: AnimeSourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (AnimeSource, Listing) -> Unit,
    onClickPin: (AnimeSource) -> Unit,
    onLongClickItem: (AnimeSource) -> Unit,
    onSwipeHide: (AnimeSource) -> Unit,
    swipeToHideEnabled: Boolean,
    onClickExtension: (AnimeSource) -> Unit = {},
    onClickInstallExtension: (AnimeExtension.Available) -> Unit = {},
    onClickTrustExtension: (AnimeExtension.Untrusted) -> Unit = {},
    downloadStates: SnapshotStateMap<String, InstallStep> = mutableStateMapOf(),
    sourcesWithUpdates: Set<Long> = emptySet(),
    cardDesign: Boolean = false,
    cardColumns: Int = 2,
    sourceExtensionMap: Map<Long, AnimeExtension.Installed> = emptyMap(),
    onClickUpdate: (AnimeSource) -> Unit = {},
    onRefresh: () -> Unit = {},
    onClickUpdateAll: () -> Unit = {},
) {
    var notInstalledExpanded by remember { mutableStateOf(false) }

    // Filter out available extensions when collapsed
    val visibleItems = remember(state.items, notInstalledExpanded) {
        if (notInstalledExpanded) {
            state.items
        } else {
            state.items.filterNot { it is AnimeSourceUiModel.AvailableExtension }
        }
    }

    PullRefresh(
        refreshing = state.isRefreshing,
        onRefresh = onRefresh,
        enabled = !state.isLoading,
    ) {
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
            state.isEmpty -> EmptyScreen(
                stringRes = MR.strings.source_empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            else -> {
                if (cardDesign) {
                    AnimeSourcesCardView(
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
                    onClickUpdateAll = onClickUpdateAll,
                )
            } else {
            FastScrollLazyColumn(
                contentPadding = contentPadding + topSmallPaddingValues,
            ) {
                items(
                    items = visibleItems,
                    contentType = {
                        when (it) {
                            is AnimeSourceUiModel.Header -> "header"
                            is AnimeSourceUiModel.Item -> "item"
                            is AnimeSourceUiModel.AvailableExtension -> "available-extension"
                            is AnimeSourceUiModel.UntrustedExtension -> "untrusted-extension"
                        }
                    },
                    key = {
                        when (it) {
                            is AnimeSourceUiModel.Header -> it.hashCode()
                            is AnimeSourceUiModel.Item -> "source-${it.source.key()}"
                            is AnimeSourceUiModel.AvailableExtension -> "available-${it.extension.pkgName}"
                            is AnimeSourceUiModel.UntrustedExtension -> "untrusted-${it.extension.pkgName}"
                        }
                    },
                ) { model ->
                    when (model) {
                        is AnimeSourceUiModel.Header -> {
                            when (model.language) {
                                AnimeSourcesScreenModel.NOT_INSTALLED_KEY -> {
                                    AnimeSourceSectionHeader(
                                        modifier = Modifier.animateItem(),
                                        text = stringResource(MR.strings.ext_not_installed),
                                        expanded = notInstalledExpanded,
                                        onClick = { notInstalledExpanded = !notInstalledExpanded },
                                    )
                                }
                                AnimeSourcesScreenModel.INSTALLED_KEY -> {
                                    AnimeSourceSectionHeader(
                                        modifier = Modifier.animateItem(),
                                        text = stringResource(MR.strings.ext_installed),
                                        action = {
                                            if (sourcesWithUpdates.isNotEmpty()) {
                                                IconButton(onClick = onClickUpdateAll) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Download,
                                                        contentDescription = stringResource(MR.strings.ext_update_all),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                                else -> {
                                    AnimeSourceHeader(
                                        modifier = Modifier.animateItem(),
                                        language = model.language,
                                    )
                                }
                            }
                        }
                        is AnimeSourceUiModel.Item -> AnimeSourceItem(
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
                        is AnimeSourceUiModel.AvailableExtension -> {
                            AnimeAvailableExtensionItem(
                                modifier = Modifier.animateItem(),
                                extension = model.extension,
                                onClickInstall = onClickInstallExtension,
                                installStep = downloadStates[model.extension.pkgName] ?: InstallStep.Idle,
                            )
                        }
                        is AnimeSourceUiModel.UntrustedExtension -> {
                            AnimeUntrustedExtensionItem(
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
}

@Composable
private fun AnimeSourcesCardView(
    items: List<AnimeSourceUiModel>,
    contentPadding: PaddingValues,
    notInstalledExpanded: Boolean,
    onToggleNotInstalled: () -> Unit,
    cardColumns: Int,
    sourcesWithUpdates: Set<Long>,
    sourceExtensionMap: Map<Long, AnimeExtension.Installed>,
    onClickItem: (AnimeSource, Listing) -> Unit,
    onLongClickItem: (AnimeSource) -> Unit,
    onClickExtension: (AnimeSource) -> Unit,
    onClickUpdate: (AnimeSource) -> Unit,
    onClickInstallExtension: (AnimeExtension.Available) -> Unit,
    onClickTrustExtension: (AnimeExtension.Untrusted) -> Unit,
    downloadStates: SnapshotStateMap<String, InstallStep>,
    onClickUpdateAll: () -> Unit = {},
) {
    // Group items by section (header + items)
    val sectionedItems = remember(items) {
        val sections = mutableListOf<Pair<AnimeSourceUiModel.Header?, List<AnimeSourceUiModel>>>()
        var currentHeader: AnimeSourceUiModel.Header? = null
        var currentItems = mutableListOf<AnimeSourceUiModel>()
        for (item in items) {
            when (item) {
                is AnimeSourceUiModel.Header -> {
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
                        AnimeSourcesScreenModel.NOT_INSTALLED_KEY -> {
                            AnimeSourceSectionHeader(
                                text = stringResource(MR.strings.ext_not_installed),
                                expanded = notInstalledExpanded,
                                onClick = onToggleNotInstalled,
                            )
                        }
                        AnimeSourcesScreenModel.INSTALLED_KEY -> {
                            AnimeSourceSectionHeader(
                                text = stringResource(MR.strings.ext_installed),
                                action = {
                                    if (sourcesWithUpdates.isNotEmpty()) {
                                        IconButton(onClick = onClickUpdateAll) {
                                            Icon(
                                                imageVector = Icons.Outlined.Download,
                                                contentDescription = stringResource(MR.strings.ext_update_all),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        else -> {
                            AnimeSourceHeader(language = header.language)
                        }
                    }
                }
            }

            // Render items as cards — each row is a separate lazy item so
            // LazyColumn only composes visible rows (fixes freeze with many
            // not-installed extensions).
            if (header?.language != AnimeSourcesScreenModel.NOT_INSTALLED_KEY || notInstalledExpanded) {
                val rows = sectionItems.chunked(cardColumns)
                rows.forEachIndexed { rowIndex, rowItems ->
                    item(key = "cards-${header?.hashCode() ?: "no-header"}-$rowIndex") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowItems.forEach { model ->
                                when (model) {
                                    is AnimeSourceUiModel.Item -> {
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
                                            isInstalled = true,
                                            isObsolete = ext?.isObsolete == true,
                                            onClick = { onClickItem(model.source, Listing.Popular) },
                                            onLongClick = { onLongClickItem(model.source) },
                                            onCogClick = {
                                                if (hasUpdate) onClickUpdate(model.source)
                                                else onClickExtension(model.source)
                                            },
                                        )
                                    }
                                    is AnimeSourceUiModel.AvailableExtension -> {
                                        ExtensionCard(
                                            modifier = Modifier.weight(1f),
                                            title = model.extension.name,
                                            lang = model.extension.lang.uppercase(),
                                            version = model.extension.versionName,
                                            iconUrl = model.extension.iconUrl,
                                            isInstalled = false,
                                            onClick = { onClickInstallExtension(model.extension) },
                                        )
                                    }
                                    else -> {}
                                }
                            }
                            repeat(cardColumns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeSourceHeader(
    language: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val text = when (language) {
        AnimeSourcesScreenModel.NOT_INSTALLED_KEY -> stringResource(MR.strings.ext_not_installed)
        AnimeSourcesScreenModel.INSTALLED_KEY -> stringResource(MR.strings.ext_installed)
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
private fun AnimeSourceSectionHeader(
    text: String,
    expanded: Boolean? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
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
        // Reserve a fixed-size slot for the action so the title doesn't shift
        // when the action content appears/disappears (e.g. download-all icon).
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            with(this@Row) { action() }
        }
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
private fun AnimeSourceItem(
    source: AnimeSource,
    onClickItem: (AnimeSource, Listing) -> Unit,
    onLongClickItem: (AnimeSource) -> Unit,
    onClickPin: (AnimeSource) -> Unit,
    onSwipeHide: (AnimeSource) -> Unit,
    swipeToHideEnabled: Boolean,
    onClickExtension: (AnimeSource) -> Unit = {},
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
        BaseAnimeSourceItem(
            modifier = Modifier,
            source = source,
            onClickItem = { onClickItem(source, Listing.Popular) },
            onLongClickItem = { onLongClickItem(source) },
            action = {
                // Cog icon â€” opens extension details. Changes to download icon when update available.
                if (source.id != LocalAnimeSource.ID) {
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

    if (swipeToHideEnabled && source.id != LocalAnimeSource.ID) {
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
private fun AnimeSourcePinButton(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeSourceOptionsDialog(
    source: AnimeSource,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    onDismiss: () -> Unit,
    onClickMigrate: () -> Unit = {},
    onClickUninstall: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Text(
            text = source.visualName,
            style = MaterialTheme.typography.titleSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            val pinText = stringResource(
                if (Pin.Pinned in source.pin) MR.strings.action_unfavorite else MR.strings.action_favorite,
            )
            AnimeSourceSheetOption(
                label = pinText,
                icon = if (Pin.Pinned in source.pin) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                onClick = { onClickPin(); onDismiss() },
            )
            if (source.id != LocalAnimeSource.ID) {
                AnimeSourceSheetOption(
                    label = stringResource(MR.strings.action_migrate),
                    icon = Icons.Outlined.SwapHoriz,
                    onClick = { onClickMigrate(); onDismiss() },
                )
                AnimeSourceSheetOption(
                    label = stringResource(MR.strings.action_disable),
                    icon = Icons.Outlined.Block,
                    onClick = { onClickDisable(); onDismiss() },
                )
                AnimeSourceSheetOption(
                    label = stringResource(MR.strings.action_uninstall),
                    icon = Icons.Outlined.Delete,
                    onClick = { onClickUninstall(); onDismiss() },
                )
            }
        }
    }
}

@Composable
private fun AnimeSourceSheetOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AnimeUntrustedExtensionItem(
    extension: AnimeExtension.Untrusted,
    onClickTrust: (AnimeExtension.Untrusted) -> Unit,
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
private fun AnimeAvailableExtensionItem(
    extension: AnimeExtension.Available,
    onClickInstall: (AnimeExtension.Available) -> Unit,
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
        AnimeExtensionIcon(
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

sealed interface AnimeSourceUiModel {
    data class Item(val source: AnimeSource) : AnimeSourceUiModel
    data class Header(val language: String) : AnimeSourceUiModel
    data class AvailableExtension(val extension: AnimeExtension.Available) : AnimeSourceUiModel
    data class UntrustedExtension(val extension: AnimeExtension.Untrusted) : AnimeSourceUiModel
}
