package eu.kanade.tachiyomi.ui.readingorder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import kotlinx.coroutines.launch
import tachiyomi.domain.readingorder.interactor.GetLockedReadingOrders
import tachiyomi.domain.readingorder.model.ReadingOrder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class ReadingOrderViewerScreen(
    private val orderId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { ReadingOrderViewerScreenModel(orderId) }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val prefs = remember { context.getSharedPreferences("reading_order_viewer", android.content.Context.MODE_PRIVATE) }
        var isGridView by remember {
            mutableStateOf(prefs.getBoolean("grid_view", false))
        }
        val getLockedReadingOrders = remember { Injekt.get<GetLockedReadingOrders>() }
        var lockDialog by remember { mutableStateOf<Pair<Long, List<ReadingOrder>>?>(null) }

        LaunchedEffect(state.order) {
            if (state.order == null && !state.isLoading) {
                navigator.pop()
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = state.order?.name ?: "Reading Order",
                    navigateUp = { navigator.pop() },
                    actions = {
                        IconButton(onClick = {
                            isGridView = !isGridView
                            prefs.edit().putBoolean("grid_view", isGridView).apply()
                        }) {
                            Icon(
                                imageVector = if (isGridView) {
                                    Icons.AutoMirrored.Filled.ViewList
                                } else {
                                    Icons.Filled.ViewModule
                                },
                                contentDescription = if (isGridView) "List view" else "Grid view",
                            )
                        }
                    },
                )
            },
        ) { padding ->
            if (state.layers.isEmpty() && !state.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "This reading order has no entries.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (isGridView) {
                GridContent(
                    layers = state.layers,
                    padding = padding,
                    lockedEntryIds = state.lockedEntryIds,
                    onDelete = { screenModel.showRemoveConfirm(it) },
                    onEntryClick = { entry ->
                        scope.launch {
                            val lockedOrders = getLockedReadingOrders.await(entry.id)
                            if (lockedOrders.isNotEmpty()) {
                                lockDialog = entry.id to lockedOrders
                            } else {
                                navigateToEntry(navigator, state.order?.entryKind, entry.id)
                            }
                        }
                    },
                )
            } else {
                ListContent(
                    layers = state.layers,
                    padding = padding,
                    lockedEntryIds = state.lockedEntryIds,
                    onDelete = { screenModel.showRemoveConfirm(it) },
                    onEntryClick = { entry ->
                        scope.launch {
                            val lockedOrders = getLockedReadingOrders.await(entry.id)
                            if (lockedOrders.isNotEmpty()) {
                                lockDialog = entry.id to lockedOrders
                            } else {
                                navigateToEntry(navigator, state.order?.entryKind, entry.id)
                            }
                        }
                    },
                )
            }
        }

        when (val dialog = state.dialog) {
            is ReadingOrderViewerScreenModel.Dialog.RemoveConfirm -> {
                RemoveConfirmSheet(
                    entryTitle = dialog.entry.title,
                    onDismiss = { screenModel.closeDialog() },
                    onConfirm = { screenModel.removeEntry(dialog.entry) },
                )
            }
            null -> {}
        }

        lockDialog?.let { (_, lockedOrders) ->
            ReadingOrderLockDialog(
                lockedOrders = lockedOrders,
                onDismiss = { lockDialog = null },
                onViewOrder = { roId ->
                    lockDialog = null
                    navigator.replace(ReadingOrderViewerScreen(roId))
                },
                entryKindLabel = state.order?.entryKind?.replaceFirstChar { it.uppercase() },
            )
        }
    }
}

private fun navigateToEntry(
    navigator: Navigator,
    entryKind: String?,
    entryId: Long,
) {
    when (entryKind) {
        "manga" -> navigator.push(MangaScreen(entryId))
        "anime" -> navigator.push(AnimeScreen(entryId))
        "novel" -> navigator.push(NovelScreen(entryId))
    }
}

@Composable
private fun ListContent(
    layers: List<List<ReadingOrderViewerScreenModel.EntryInfo>>,
    padding: androidx.compose.foundation.layout.PaddingValues,
    lockedEntryIds: Set<Long>,
    onDelete: (ReadingOrderViewerScreenModel.EntryInfo) -> Unit,
    onEntryClick: (ReadingOrderViewerScreenModel.EntryInfo) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        layers.forEachIndexed { layerIndex, entries ->
            item(key = "header-$layerIndex") {
                DepthHeader(depth = layerIndex + 1)
            }
            items(entries, key = { "entry-${it.id}" }) { entry ->
                ListRow(
                    entry = entry,
                    isLocked = entry.id in lockedEntryIds,
                    onDelete = { onDelete(entry) },
                    onClick = { onEntryClick(entry) },
                )
            }
        }
    }
}

@Composable
private fun GridContent(
    layers: List<List<ReadingOrderViewerScreenModel.EntryInfo>>,
    padding: androidx.compose.foundation.layout.PaddingValues,
    lockedEntryIds: Set<Long>,
    onDelete: (ReadingOrderViewerScreenModel.EntryInfo) -> Unit,
    onEntryClick: (ReadingOrderViewerScreenModel.EntryInfo) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        layers.forEachIndexed { layerIndex, entries ->
            item(key = "header-$layerIndex") {
                DepthHeader(depth = layerIndex + 1)
            }
            item(key = "grid-$layerIndex") {
                val columns = 3
                val rows = (entries.size + columns - 1) / columns
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (rowIndex in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (colIndex in 0 until columns) {
                                val index = rowIndex * columns + colIndex
                                if (index < entries.size) {
                                    val entry = entries[index]
                                    Box(modifier = Modifier.weight(1f)) {
                                        GridItem(
                                            entry = entry,
                                            isLocked = entry.id in lockedEntryIds,
                                            onDelete = { onDelete(entry) },
                                            onClick = { onEntryClick(entry) },
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
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
private fun DepthHeader(depth: Int) {
    Text(
        text = "Depth ${ordinal(depth)}",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ListRow(
    entry: ReadingOrderViewerScreenModel.EntryInfo,
    isLocked: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = 16.dp),
        ) {
            ItemCover.Book(
                data = entry.cover,
                modifier = Modifier
                    .fillMaxHeight()
                    .then(if (isLocked) Modifier.alpha(0.5f) else Modifier),
            )
            if (isLocked) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Text(
            text = entry.title,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Remove from reading order",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun GridItem(
    entry: ReadingOrderViewerScreenModel.EntryInfo,
    isLocked: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box {
            ItemCover.Book(
                data = entry.cover,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ItemCover.Book.ratio)
                    .then(if (isLocked) Modifier.alpha(0.5f) else Modifier),
            )
            if (isLocked) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Remove from reading order",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun ordinal(n: Int): String {
    val suffixes = arrayOf("th", "st", "nd", "rd")
    val v = n % 100
    if (v in 11..13) return "${n}th"
    return n.toString() + suffixes[n % 10.coerceAtMost(3)]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoveConfirmSheet(
    entryTitle: String,
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
                text = "Remove from Reading Order?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Remove \"$entryTitle\" from this reading order?",
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
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
