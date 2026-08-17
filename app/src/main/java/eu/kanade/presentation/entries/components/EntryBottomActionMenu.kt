package eu.kanade.presentation.entries.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.EntryDownloadDropdownMenu
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

@Composable
fun EntryBottomActionMenu(
    visible: Boolean,
    isManga: Boolean,
    modifier: Modifier = Modifier,
    onBookmarkClicked: (() -> Unit)? = null,
    onRemoveBookmarkClicked: (() -> Unit)? = null,
    onFillermarkClicked: (() -> Unit)? = null,
    onRemoveFillermarkClicked: (() -> Unit)? = null,
    onMarkAsViewedClicked: (() -> Unit)? = null,
    onMarkAsUnviewedClicked: (() -> Unit)? = null,
    onMarkPreviousAsViewedClicked: (() -> Unit)? = null,
    onDownloadClicked: (() -> Unit)? = null,
    onDeleteClicked: (() -> Unit)? = null,
    onExternalClicked: (() -> Unit)? = null,
    onInternalClicked: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        val scope = rememberCoroutineScope()
        val playerPreferences: PlayerPreferences = Injekt.get()
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large.copy(
                bottomEnd = ZeroCornerSize,
                bottomStart = ZeroCornerSize,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val haptic = LocalHapticFeedback.current
            val confirm =
                remember {
                    mutableStateListOf(false, false, false, false, false, false, false, false, false, false, false)
                }
            val confirmRange = 0..<11
            var resetJob: Job? = remember { null }
            val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                (confirmRange).forEach { i -> confirm[i] = i == toConfirmIndex }
                resetJob?.cancel()
                resetJob = scope.launch {
                    delay(1.seconds)
                    if (isActive) confirm[toConfirmIndex] = false
                }
            }
            Row(
                modifier = Modifier
                    .padding(
                        WindowInsets.navigationBars
                            .only(WindowInsetsSides.Bottom)
                            .asPaddingValues(),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                if (onBookmarkClicked != null) {
                    val bookmark = if (isManga) MR.strings.action_bookmark else AYMR.strings.action_bookmark_episode
                    Button(
                        title = stringResource(bookmark),
                        icon = ImageVector.vectorResource(R.drawable.ic_bookmark_24dp),
                        toConfirm = confirm[0],
                        onLongClick = { onLongClickItem(0) },
                        onClick = onBookmarkClicked,
                    )
                }
                if (onRemoveBookmarkClicked != null) {
                    val removeBookmark = if (isManga) {
                        MR.strings.action_remove_bookmark
                    } else {
                        AYMR.strings.action_remove_bookmark_episode
                    }
                    Button(
                        title = stringResource(removeBookmark),
                        icon = ImageVector.vectorResource(R.drawable.ic_bookmark_off_24dp),
                        toConfirm = confirm[1],
                        onLongClick = { onLongClickItem(1) },
                        onClick = onRemoveBookmarkClicked,
                    )
                }
                if (onFillermarkClicked != null) {
                    Button(
                        title = stringResource(AYMR.strings.action_fillermark_episode),
                        icon = ImageVector.vectorResource(R.drawable.ic_label_24dp),
                        toConfirm = confirm[2],
                        onLongClick = { onLongClickItem(2) },
                        onClick = onFillermarkClicked,
                    )
                }
                if (onRemoveFillermarkClicked != null) {
                    Button(
                        title = stringResource(AYMR.strings.action_remove_fillermark_episode),
                        icon = ImageVector.vectorResource(R.drawable.ic_label_outline_24dp),
                        toConfirm = confirm[3],
                        onLongClick = { onLongClickItem(3) },
                        onClick = onRemoveFillermarkClicked,
                    )
                }
                if (onMarkAsViewedClicked != null) {
                    val viewed = if (isManga) MR.strings.action_mark_as_read else AYMR.strings.action_mark_as_seen
                    Button(
                        title = stringResource(viewed),
                        icon = ImageVector.vectorResource(R.drawable.ic_eye_24dp),
                        toConfirm = confirm[4],
                        onLongClick = { onLongClickItem(4) },
                        onClick = onMarkAsViewedClicked,
                    )
                }
                if (onMarkAsUnviewedClicked != null) {
                    val unviewed = if (isManga) MR.strings.action_mark_as_unread else AYMR.strings.action_mark_as_unseen
                    Button(
                        title = stringResource(unviewed),
                        icon = ImageVector.vectorResource(R.drawable.ic_eye_off_24dp),
                        toConfirm = confirm[5],
                        onLongClick = { onLongClickItem(5) },
                        onClick = onMarkAsUnviewedClicked,
                    )
                }
                if (onMarkPreviousAsViewedClicked != null) {
                    val previousUnviewed = if (isManga) {
                        MR.strings.action_mark_previous_as_read
                    } else {
                        AYMR.strings.action_mark_previous_as_seen
                    }
                    Button(
                        title = stringResource(previousUnviewed),
                        icon = ImageVector.vectorResource(R.drawable.ic_eye_down_24dp),
                        toConfirm = confirm[6],
                        onLongClick = { onLongClickItem(6) },
                        onClick = onMarkPreviousAsViewedClicked,
                    )
                }
                if (onDownloadClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_download),
                        icon = Icons.Outlined.Download,
                        toConfirm = confirm[7],
                        onLongClick = { onLongClickItem(7) },
                        onClick = onDownloadClicked,
                    )
                }
                if (onDeleteClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_delete),
                        icon = ImageVector.vectorResource(R.drawable.ic_delete_24dp),
                        toConfirm = confirm[8],
                        onLongClick = { onLongClickItem(8) },
                        onClick = onDeleteClicked,
                    )
                }
                if (!isManga && onExternalClicked != null && !playerPreferences.alwaysUseExternalPlayer().get()) {
                    Button(
                        title = stringResource(AYMR.strings.action_play_externally),
                        icon = ImageVector.vectorResource(R.drawable.ic_open_in_webview_24dp),
                        toConfirm = confirm[9],
                        onLongClick = { onLongClickItem(9) },
                        onClick = onExternalClicked,
                    )
                }
                if (!isManga && onInternalClicked != null && playerPreferences.alwaysUseExternalPlayer().get()) {
                    Button(
                        title = stringResource(AYMR.strings.action_play_internally),
                        icon = ImageVector.vectorResource(R.drawable.ic_play_arrow_24dp),
                        toConfirm = confirm[10],
                        onLongClick = { onLongClickItem(10) },
                        onClick = onInternalClicked,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.Button(
    title: String,
    icon: ImageVector,
    toConfirm: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    val animatedWeight by animateFloatAsState(
        targetValue = if (toConfirm) 2f else 1f,
        label = "weight",
    )
    Column(
        modifier = Modifier
            .size(48.dp)
            .weight(animatedWeight)
            .combinedClickable(
                enabled = enabled,
                interactionSource = null,
                indication = ripple(bounded = false),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedVisibility(
            visible = toConfirm,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Text(
                text = title,
                overflow = TextOverflow.Visible,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        content?.invoke()
    }
}

@Composable
fun LibraryBottomActionMenu(
    visible: Boolean,
    onChangeCollectionClicked: () -> Unit,
    onMarkAsViewedClicked: () -> Unit,
    onMarkAsUnviewedClicked: () -> Unit,
    onDownloadClicked: ((DownloadAction) -> Unit)?,
    onDeleteClicked: () -> Unit,
    isManga: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(delayMillis = 300)),
        exit = shrinkVertically(animationSpec = tween()),
    ) {
        val scope = rememberCoroutineScope()
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large.copy(
                bottomEnd = ZeroCornerSize,
                bottomStart = ZeroCornerSize,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val haptic = LocalHapticFeedback.current
            val confirm = remember { mutableStateListOf(false, false, false, false, false) }
            var resetJob: Job? = remember { null }
            val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                (0..<5).forEach { i -> confirm[i] = i == toConfirmIndex }
                resetJob?.cancel()
                resetJob = scope.launch {
                    delay(1.seconds)
                    if (isActive) confirm[toConfirmIndex] = false
                }
            }
            Row(
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.navigationBars
                            .only(WindowInsetsSides.Bottom),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Button(
                    title = stringResource(MR.strings.action_move_collection),
                    icon = ImageVector.vectorResource(R.drawable.ic_label_24dp),
                    toConfirm = confirm[0],
                    onLongClick = { onLongClickItem(0) },
                    onClick = onChangeCollectionClicked,
                )
                val viewed = if (isManga) MR.strings.action_mark_as_read else AYMR.strings.action_mark_as_seen
                Button(
                    title = stringResource(viewed),
                    icon = ImageVector.vectorResource(R.drawable.ic_eye_24dp),
                    toConfirm = confirm[1],
                    onLongClick = { onLongClickItem(1) },
                    onClick = onMarkAsViewedClicked,
                )
                val unviewed = if (isManga) MR.strings.action_mark_as_unread else AYMR.strings.action_mark_as_unseen
                Button(
                    title = stringResource(unviewed),
                    icon = ImageVector.vectorResource(R.drawable.ic_eye_off_24dp),
                    toConfirm = confirm[2],
                    onLongClick = { onLongClickItem(2) },
                    onClick = onMarkAsUnviewedClicked,
                )
                Button(
                    title = stringResource(MR.strings.action_delete),
                    icon = ImageVector.vectorResource(R.drawable.ic_delete_24dp),
                    toConfirm = confirm[4],
                    onLongClick = { onLongClickItem(4) },
                    onClick = onDeleteClicked,
                )
            }
        }
    }
}

/**
 * Bottom bar for reading-order selection mode. Shows Back / Next / Save
 * instead of the normal 5-button action menu.
 */
@Composable
fun ReadingOrderBottomBar(
    visible: Boolean,
    currentLayer: Int,
    canGoBack: Boolean,
    canAdvance: Boolean,
    canSave: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(delayMillis = 300)),
        exit = shrinkVertically(animationSpec = tween()),
    ) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large.copy(
                bottomEnd = ZeroCornerSize,
                bottomStart = ZeroCornerSize,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.navigationBars
                            .only(WindowInsetsSides.Bottom),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(
                    title = stringResource(MR.strings.action_webview_back),
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    toConfirm = false,
                    onLongClick = {},
                    onClick = onBack,
                )
                Button(
                    title = "Next (Layer ${currentLayer + 2})",
                    icon = Icons.AutoMirrored.Outlined.ArrowForward,
                    toConfirm = false,
                    onLongClick = {},
                    onClick = onNext,
                    enabled = canAdvance,
                )
                Button(
                    title = stringResource(MR.strings.action_save),
                    icon = Icons.Outlined.Save,
                    toConfirm = false,
                    onLongClick = {},
                    onClick = onSave,
                    enabled = canSave,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingOrderCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = "New Reading Order",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Reading order name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(MR.strings.action_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onCreate(name) },
                    enabled = name.isNotBlank(),
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ReadingOrderPickerDialog(
    orders: List<ReadingOrder>,
    onDismiss: () -> Unit,
    onSelectExisting: (ReadingOrder) -> Unit,
    onCreateNew: (String) -> Unit,
    onDelete: (ReadingOrder) -> Unit,
    onEdit: (ReadingOrder, String) -> Unit = { _, _ -> },
    onExport: (ReadingOrder) -> Unit = {},
    onImport: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by remember { mutableStateOf(PickerPage.List) }
    var editingOrder by remember { mutableStateOf<ReadingOrder?>(null) }
    var deletingOrder by remember { mutableStateOf<ReadingOrder?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(150))
            },
            modifier = Modifier.fillMaxWidth(),
            label = "pickerPage",
        ) { currentPage ->
            when (currentPage) {
                PickerPage.List -> PickerListPage(
                    orders = orders,
                    onSelectExisting = onSelectExisting,
                    onCreateNew = { page = PickerPage.CreateName },
                    onEdit = { order ->
                        editingOrder = order
                        page = PickerPage.EditName
                    },
                    onDelete = { order ->
                        deletingOrder = order
                        page = PickerPage.DeleteConfirm
                    },
                    onExport = onExport,
                    onImport = onImport,
                )
                PickerPage.CreateName -> PickerNamePage(
                    title = "New Reading Order",
                    initialName = "",
                    onDismiss = { page = PickerPage.List },
                    onConfirm = { name ->
                        onCreateNew(name)
                    },
                )
                PickerPage.EditName -> PickerNamePage(
                    title = "Edit Reading Order",
                    initialName = editingOrder?.name ?: "",
                    onDismiss = {
                        editingOrder = null
                        page = PickerPage.List
                    },
                    onConfirm = { newName ->
                        editingOrder?.let { onEdit(it, newName) }
                        editingOrder = null
                    },
                )
                PickerPage.DeleteConfirm -> {
                    val order = deletingOrder
                    if (order != null) {
                        PickerDeletePage(
                            orderName = order.name,
                            onDismiss = {
                                deletingOrder = null
                                page = PickerPage.List
                            },
                            onConfirm = {
                                onDelete(order)
                                deletingOrder = null
                            },
                        )
                    }
                }
            }
        }
    }
}

private enum class PickerPage { List, CreateName, EditName, DeleteConfirm }

@Composable
private fun PickerListPage(
    orders: List<ReadingOrder>,
    onSelectExisting: (ReadingOrder) -> Unit,
    onCreateNew: () -> Unit,
    onEdit: (ReadingOrder) -> Unit,
    onDelete: (ReadingOrder) -> Unit,
    onExport: (ReadingOrder) -> Unit,
    onImport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = "Reading Orders",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (orders.isEmpty()) {
            Text(
                text = "No reading orders yet. Create one to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        } else {
            orders.forEach { order ->
                var menuExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectExisting(order) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = order.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = {
                                    menuExpanded = false
                                    onEdit(order)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete(order)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export") },
                                onClick = {
                                    menuExpanded = false
                                    onExport(order)
                                },
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCreateNew,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Create New",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Import",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PickerNamePage(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Reading order name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(MR.strings.action_cancel))
            }
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PickerDeletePage(
    orderName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Delete Reading Order?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Delete \"$orderName\"? This cannot be undone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(MR.strings.action_cancel))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun ReadingOrderRemoveConfirmDialog(
    entryTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = "Remove from Reading Order?",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = "Remove \"$entryTitle\" from this reading order?",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(MR.strings.action_ok),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingOrderDeleteConfirmDialog(
    orderName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Delete Reading Order?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Delete \"$orderName\"? This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(MR.strings.action_cancel))
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ReadingOrderEditNameDialog(
    orderName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(orderName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Reading Order",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
fun ReadingOrderMoveDepthDialog(
    entryTitle: String,
    fromDepth: Int,
    toDepth: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.SwapVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = "Change Depth?",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = "\"$entryTitle\" is currently in Depth ${ordinal(fromDepth)}. Move it to Depth ${ordinal(toDepth)}?",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

private fun ordinal(n: Int): String {
    val suffixes = arrayOf("th", "st", "nd", "rd")
    val v = n % 100
    if (v in 11..13) return "${n}th"
    return n.toString() + suffixes[n % 10.coerceAtMost(3)]
}
