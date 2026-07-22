package eu.kanade.presentation.browse.novel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.browse.novel.migration.all.MigrateNovelAllScreenModel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrateNovelAllContent(
    title: String,
    state: MigrateNovelAllScreenModel.State,
    navigateUp: () -> Unit,
    onSkip: (Long) -> Unit,
    onMigrateNow: (oldNovel: Novel, newNovel: Novel) -> Unit,
    onCopyNow: (oldNovel: Novel, newNovel: Novel) -> Unit,
    onSearchManually: (Long) -> Unit,
    onClickOldNovel: (Long) -> Unit,
    onClickRecommendedNovel: (Long) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(AYMR.strings.action_migrate_all),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Progress bar
            if (state.total > 0 && !state.allDone) {
                LinearProgressIndicator(
                    progress = { if (state.total > 0) state.processed / state.total.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(AYMR.strings.migrate_all_progress, state.processed, state.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                )
            } else if (state.allDone) {
                Text(
                    text = stringResource(AYMR.strings.migrate_all_done),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                )
            }

            if (state.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(
                        items = state.items,
                        key = { it.oldNovel.id },
                    ) { item ->
                        MigrationRow(
                            item = item,
                            onClickOldNovel = { onClickOldNovel(item.oldNovel.id) },
                            onClickRecommendedNovel = {
                                item.recommendedNovel?.let { onClickRecommendedNovel(it.id) }
                            },
                            onSkip = { onSkip(item.oldNovel.id) },
                            onMigrateNow = {
                                item.recommendedNovel?.let { onMigrateNow(item.oldNovel, it) }
                            },
                            onCopyNow = {
                                item.recommendedNovel?.let { onCopyNow(item.oldNovel, it) }
                            },
                            onSearchManually = { onSearchManually(item.oldNovel.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MigrationRow(
    item: MigrateNovelAllScreenModel.MigrationItem,
    onClickOldNovel: () -> Unit,
    onClickRecommendedNovel: () -> Unit,
    onSkip: () -> Unit,
    onMigrateNow: () -> Unit,
    onCopyNow: () -> Unit,
    onSearchManually: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Old novel (left side)
        NovelCoverBlock(
            novel = item.oldNovel,
            onClick = onClickOldNovel,
            modifier = Modifier.weight(1f),
        )

        // Arrow
        Text(
            text = ">",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        // Recommended novel (right side)
        Box(modifier = Modifier.weight(1f)) {
            when (item.status) {
                MigrateNovelAllScreenModel.MigrationStatus.Searching -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(AYMR.strings.migrate_all_searching),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MigrateNovelAllScreenModel.MigrationStatus.NotFound -> {
                    Text(
                        text = stringResource(AYMR.strings.migrate_all_not_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MigrateNovelAllScreenModel.MigrationStatus.Skipped -> {
                    Text(
                        text = stringResource(AYMR.strings.migrate_all_skip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MigrateNovelAllScreenModel.MigrationStatus.Migrated -> {
                    Text(
                        text = stringResource(MR.strings.migrate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MigrateNovelAllScreenModel.MigrationStatus.Found -> {
                    item.recommendedNovel?.let { newNovel ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NovelCoverBlock(
                                novel = newNovel,
                                onClick = onClickRecommendedNovel,
                                modifier = Modifier.weight(1f),
                                subtitle = stringResource(
                                    AYMR.strings.migrate_all_recommended,
                                    item.recommendedChapterCount.toString(),
                                ),
                            )
                            // Three-dot menu
                            MoreOptionsButton(
                                onSkip = onSkip,
                                onMigrateNow = onMigrateNow,
                                onCopyNow = onCopyNow,
                                onSearchManually = onSearchManually,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelCoverBlock(
    novel: Novel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .height(96.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemCover.Book(
            modifier = Modifier.fillMaxHeight(),
            data = NovelCover(
                novelId = novel.id,
                sourceId = novel.source,
                isNovelFavorite = novel.favorite,
                url = novel.thumbnailUrl,
                lastModified = novel.coverLastModified,
            ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = novel.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MoreOptionsButton(
    onSkip: () -> Unit,
    onMigrateNow: () -> Unit,
    onCopyNow: () -> Unit,
    onSearchManually: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.migrate_all_migrate_now)) },
                onClick = {
                    expanded = false
                    onMigrateNow()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.migrate_all_copy_now)) },
                onClick = {
                    expanded = false
                    onCopyNow()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.migrate_all_search_manually)) },
                onClick = {
                    expanded = false
                    onSearchManually()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.migrate_all_skip)) },
                onClick = {
                    expanded = false
                    onSkip()
                },
            )
        }
    }
}
