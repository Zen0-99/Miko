package miko.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Android's `ManageCatalogsSheet` — specs filtered to one medium at a
 * time by the pills under the title (rows are medium-scoped; showing
 * them together invited the "where did my row go" confusion). Drag-
 * reorder persists through [onReorder] — it reorders within the
 * filtered medium only, which is all the row order means anyway.
 */
@Composable
fun ManageCatalogsSheet(
    specs: List<CatalogRowSpec>,
    currentMedium: String,
    resolveTitle: suspend (CatalogRowSpec) -> String,
    onReorder: (List<String>) -> Unit,
    onSetVisible: (String, Boolean) -> Unit,
    onEdit: (CatalogRowSpec) -> Unit,
    onDelete: (String) -> Unit,
    onMediumPicked: (String) -> Unit,
    onAddNew: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var mediumFilter by remember { mutableStateOf(currentMedium) }
    val filteredSpecs = specs.filter { it.medium == mediumFilter }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
        ) {
            Text(
                text = stringResource(AYMR.strings.action_manage_catalogs),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = 24.dp,
                    top = 8.dp,
                    end = 24.dp,
                    bottom = 12.dp,
                ),
            )

            // One medium at a time — the same full-width pill track as
            // the discovery sub-type row. Picking also switches the home
            // mode behind the sheet so the row you create lands where
            // you're looking.
            val mediumPills = listOf(
                CatalogRowSpec.MEDIUM_MANGA to AYMR.strings.carrier_manga,
                CatalogRowSpec.MEDIUM_SHOWS to AYMR.strings.carrier_shows,
                CatalogRowSpec.MEDIUM_BOOKS to AYMR.strings.carrier_books,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    )
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                mediumPills.forEach { (m, labelRes) ->
                    val isSelected = mediumFilter == m
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .then(
                                if (isSelected) {
                                    Modifier.background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clickable {
                                if (mediumFilter != m) {
                                    mediumFilter = m
                                    onMediumPicked(m)
                                }
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            var pendingDelete by remember { mutableStateOf<String?>(null) }
            pendingDelete?.let { id ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    text = { Text(stringResource(AYMR.strings.catalog_delete_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            onDelete(id)
                            pendingDelete = null
                        }) {
                            Text(stringResource(MR.strings.action_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) {
                            Text(stringResource(MR.strings.action_cancel))
                        }
                    },
                )
            }

            if (filteredSpecs.isEmpty()) {
                Text(
                    text = stringResource(AYMR.strings.catalog_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            } else {
                val lazyListState = rememberLazyListState()
                // Drag-mirror resyncs only when idle — exactly the
                // collection idiom or drags jitter.
                val specsState = remember { filteredSpecs.toMutableStateList() }
                val reorderableState = rememberReorderableLazyListState(
                    lazyListState,
                    PaddingValues(0.dp),
                ) { from, to ->
                    val item = specsState.removeAt(from.index)
                    specsState.add(to.index, item)
                    onReorder(specsState.map { it.id })
                }
                LaunchedEffect(filteredSpecs) {
                    if (!reorderableState.isAnyItemDragging) {
                        specsState.clear()
                        specsState.addAll(filteredSpecs)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    state = lazyListState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    items(specsState, key = { it.id }) { spec ->
                        ReorderableItem(reorderableState, spec.id) {
                            CatalogSpecRow(
                                spec = spec,
                                resolveTitle = resolveTitle,
                                onEdit = { onEdit(spec) },
                                onSetVisible = { onSetVisible(spec.id, !spec.visible) },
                                onDelete = { pendingDelete = spec.id },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onAddNew(mediumFilter) }) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(stringResource(AYMR.strings.catalog_new))
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.CatalogSpecRow(
    spec: CatalogRowSpec,
    resolveTitle: suspend (CatalogRowSpec) -> String,
    onEdit: () -> Unit,
    onSetVisible: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title by produceState(initialValue = spec.title ?: spec.displayTitle(), spec.id) {
        value = resolveTitle(spec)
    }
    ElevatedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(vertical = MaterialTheme.padding.small)
                .padding(start = MaterialTheme.padding.small, end = MaterialTheme.padding.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = null,
                modifier = Modifier
                    .padding(MaterialTheme.padding.medium)
                    .draggableHandle(),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = specSubtitle(spec),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = null,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (spec.visible) {
                                        AYMR.strings.catalog_action_hide
                                    } else {
                                        AYMR.strings.catalog_action_show
                                    },
                                ),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (spec.visible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onSetVisible()
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_edit)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                        },
                        onClick = {
                            onEdit()
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_delete)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                        },
                        onClick = {
                            onDelete()
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
}

/** Compact "{feed} · {medium}" subtitle — static labels only; dynamic
 *  names already appear inside the resolved title. On iOS an extension
 *  spec's `pkgName` carries the source's display name (plugin ids can't
 *  round-trip through `ui`), so it renders as the feed label directly. */
@Composable
private fun specSubtitle(spec: CatalogRowSpec): String {
    val feedLabel = when (val f = spec.feed) {
        is Feed.Tmdb -> stringResource(AYMR.strings.catalog_feed_tmdb)
        is Feed.Cinemeta -> f.catalogId.replaceFirstChar { it.uppercase() }
        is Feed.Canonical -> if (f.kindsCsv == "ol") {
            stringResource(AYMR.strings.catalog_feed_openlibrary)
        } else {
            stringResource(AYMR.strings.catalog_feed_canonical)
        }
        is Feed.Extension -> spec.pkgLabel().ifBlank {
            stringResource(MR.strings.label_extensions)
        }
        is Feed.Library -> stringResource(MR.strings.label_library)
        Feed.History -> stringResource(
            when (spec.medium) {
                CatalogRowSpec.MEDIUM_SHOWS -> AYMR.strings.label_recent_anime
                CatalogRowSpec.MEDIUM_MANGA -> AYMR.strings.label_recently_read
                else -> AYMR.strings.label_recent_novels
            },
        )
    }
    val mediumLabel = when {
        spec.medium == CatalogRowSpec.MEDIUM_SHOWS &&
            (spec.feed as? Feed.Tmdb)?.tmdbMediaType()?.name == "MOVIE" ->
            stringResource(AYMR.strings.catalog_type_movie)
        spec.medium == CatalogRowSpec.MEDIUM_SHOWS ->
            stringResource(AYMR.strings.catalog_type_series)
        spec.medium == CatalogRowSpec.MEDIUM_MANGA -> stringResource(AYMR.strings.carrier_manga)
        else -> stringResource(AYMR.strings.carrier_books)
    }
    return "$feedLabel · $mediumLabel"
}

private fun CatalogRowSpec.pkgLabel(): String =
    (feed as? Feed.Extension)?.pkgName?.substringAfterLast('.') ?: ""
