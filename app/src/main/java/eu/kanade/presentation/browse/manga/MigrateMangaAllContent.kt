package eu.kanade.presentation.browse.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.browse.manga.migration.all.MigrateMangaAllScreenModel
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/** Width of each cover — sized like a 3-column library grid item. */
private val CoverWidth = 100.dp

/** Height of a 2:3 cover at [CoverWidth]. Used to vertically center the arrow and menu. */
private val CoverHeight = CoverWidth * 1.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrateMangaAllContent(
    title: String,
    state: MigrateMangaAllScreenModel.State,
    navigateUp: () -> Unit,
    onSkip: (Long) -> Unit,
    onMigrateNow: (oldManga: Manga, newManga: Manga) -> Unit,
    onSearchManually: (Long) -> Unit,
    onClickOldManga: (Long) -> Unit,
    onClickRecommendedManga: (Long) -> Unit,
) {
    val isSingle = state.total <= 1
    Scaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isSingle) {
                                stringResource(MR.strings.action_migrate) + " " + title
                            } else {
                                stringResource(AYMR.strings.action_migrate_all)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!isSingle) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
            if (!isSingle) {
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.items,
                        key = { it.oldManga.id },
                    ) { item ->
                        MigrationCard(
                            item = item,
                            onClickOldManga = { onClickOldManga(item.oldManga.id) },
                            onClickRecommendedManga = {
                                item.recommendedManga?.let { onClickRecommendedManga(it.id) }
                            },
                            onSkip = { onSkip(item.oldManga.id) },
                            onMigrateNow = {
                                item.recommendedManga?.let { onMigrateNow(item.oldManga, it) }
                            },
                            onSearchManually = { onSearchManually(item.oldManga.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MigrationCard(
    item: MigrateMangaAllScreenModel.MigrationItem,
    onClickOldManga: () -> Unit,
    onClickRecommendedManga: () -> Unit,
    onSkip: () -> Unit,
    onMigrateNow: () -> Unit,
    onSearchManually: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CoverColumn(
                manga = item.oldManga,
                chapterCount = item.oldChapterCount,
                onClick = onClickOldManga,
            )
            Box(
                modifier = Modifier.height(CoverHeight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            when (item.status) {
                MigrateMangaAllScreenModel.MigrationStatus.Found -> {
                    val newManga = item.recommendedManga
                    if (newManga != null) {
                        CoverColumn(
                            manga = newManga,
                            chapterCount = item.recommendedChapterCount,
                            onClick = onClickRecommendedManga,
                        )
                    } else {
                        PlaceholderColumn { }
                    }
                }
                MigrateMangaAllScreenModel.MigrationStatus.Searching -> PlaceholderColumn(
                    phaseText = item.searchPhase,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
                MigrateMangaAllScreenModel.MigrationStatus.NotFound -> PlaceholderColumn {
                    StatusText(
                        text = stringResource(AYMR.strings.migrate_all_not_found),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                MigrateMangaAllScreenModel.MigrationStatus.Skipped -> PlaceholderColumn {
                    StatusText(
                        text = stringResource(AYMR.strings.migrate_all_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MigrateMangaAllScreenModel.MigrationStatus.Migrated -> PlaceholderColumn {
                    StatusText(
                        text = stringResource(MR.strings.migrate),
                        color = MaterialTheme.colorScheme.primary,
                        bold = true,
                    )
                }
            }
            Box(
                modifier = Modifier.height(CoverHeight),
                contentAlignment = Alignment.Center,
            ) {
                val canMigrateNow = item.status == MigrateMangaAllScreenModel.MigrationStatus.Found &&
                    item.recommendedManga != null
                MoreOptionsButton(
                    onSkip = onSkip,
                    onMigrateNow = onMigrateNow.takeIf { canMigrateNow },
                    onSearchManually = onSearchManually,
                )
            }
        }
    }
}

@Composable
private fun CoverColumn(
    manga: Manga,
    chapterCount: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(CoverWidth),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onClick),
        ) {
            ItemCover.Book(
                modifier = Modifier.fillMaxSize(),
                data = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight(0.4f)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color(0xCC000000),
                        ),
                    ),
            )
            Text(
                text = manga.title,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(
                    shadow = Shadow(color = Color.Black, blurRadius = 4f),
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 5.dp, vertical = 4.dp),
            )
        }

        Text(
            text = if (chapterCount > 0) {
                stringResource(AYMR.strings.migrate_all_chapter_count, chapterCount.toString())
            } else {
                ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun PlaceholderColumn(
    phaseText: String = "",
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.width(CoverWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Text(
            text = phaseText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun StatusText(
    text: String,
    color: Color,
    bold: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun MoreOptionsButton(
    onSkip: () -> Unit,
    onMigrateNow: (() -> Unit)?,
    onSearchManually: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(AYMR.strings.migrate_all_migrate_now),
                        color = if (onMigrateNow != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    )
                },
                enabled = onMigrateNow != null,
                onClick = {
                    expanded = false
                    onMigrateNow?.invoke()
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
