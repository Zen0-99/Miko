package miko.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

private const val HERO_HEIGHT = 380

/**
 * Port of Android's `NovelScreen`: full-bleed cover backdrop with scrims,
 * centered display title + meta row, the accent continue bar flanked by
 * favourite / mark-all toggle boxes, clickable genre text line, expandable
 * description, and read-state-aware chapter rows.
 */
@Composable
fun NovelDetailsScreen(
    model: NovelDetailsScreenModel,
    onChapterClick: (SNovelChapter) -> Unit,
    onGenreClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            NovelHero(
                coverUrl = state.novel?.thumbnail_url,
                title = state.novel?.title ?: state.local?.title ?: "",
                metaItems = listOfNotNull(
                    state.novel?.author,
                    state.novel?.status?.let { statusLabel(it) },
                    state.sourceName.ifBlank { null },
                ),
            )
        }

        item {
            ContinueRow(
                favorite = state.favorite,
                allRead = state.allChaptersRead,
                hasStarted = state.hasStarted,
                nextChapterName = state.nextChapter?.name,
                enabled = state.nextChapter != null || state.allChaptersRead,
                onContinueClick = { state.nextChapter?.let(onChapterClick) },
                onFavoriteClick = model::toggleFavorite,
                onToggleDoneClick = model::toggleAllChaptersRead,
            )
        }

        state.novel?.getGenres()?.takeIf { it.isNotEmpty() }?.let { genres ->
            item {
                GenreRow(
                    genres = genres,
                    onGenreClick = onGenreClick,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        state.novel?.description?.takeIf { it.isNotBlank() }?.let { description ->
            item {
                ExpandableDescription(description = description)
            }
        }

        item {
            Text(
                text = pluralStringResource(
                    MR.plurals.manga_num_chapters,
                    count = state.chapters.size,
                    state.chapters.size,
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        when {
            state.chaptersLoading -> item { LoadingScreen() }
            state.chapters.isEmpty() -> item {
                Text(
                    text = stringResource(MR.strings.no_results_found),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            else -> {
                val chapters = state.chapters
                val readUrls = state.readUrls
                items(chapters, key = { it.url }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        read = chapter.url in readUrls,
                        onClick = { onChapterClick(chapter) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

/**
 * Android's `EnhancedEntryInfoBox` (reduced): full-bleed cover, top scrim for
 * the app-bar icons, transparent→background bottom scrim, and the centered
 * display title + meta tail pinned to the bottom edge.
 */
@Composable
private fun NovelHero(
    coverUrl: String?,
    title: String,
    metaItems: List<String>,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT.dp),
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, backgroundColor),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title.ifBlank { stringResource(MR.strings.unknown_title) },
                style = MaterialTheme.typography.displaySmall.merge(
                    TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.86f),
                            offset = Offset(0f, 2.5f),
                            blurRadius = 10f,
                        ),
                    ),
                ),
                color = Color.White,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (metaItems.isNotEmpty()) {
                Text(
                    text = metaItems.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * Android's `EntryContinueRow`: a 16dp-rounded accent bar (book icon + Start /
 * Continue / All up to date) flanked by 44dp rounded-square toggle boxes —
 * favourite left, mark-all-done right.
 */
@Composable
private fun ContinueRow(
    favorite: Boolean,
    allRead: Boolean,
    hasStarted: Boolean,
    nextChapterName: String?,
    enabled: Boolean,
    onContinueClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onToggleDoneClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary

    val label = when {
        allRead -> stringResource(AYMR.strings.fetching_overlay_all_up_to_date)
        nextChapterName != null && hasStarted ->
            stringResource(MR.strings.action_continue_named, nextChapterName)
        else -> stringResource(MR.strings.action_start)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleBoxAction(
            icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(
                if (favorite) MR.strings.in_library else MR.strings.add_to_library,
            ),
            active = favorite,
            activeColor = accent,
            onActiveColor = onAccent,
            onClick = onFavoriteClick,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (enabled) {
                        accent
                    } else {
                        accent.copy(alpha = 0.15f)
                    },
                )
                .clickable(enabled = enabled, onClickLabel = null, onClick = onContinueClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val contentColor = if (enabled) onAccent else accent
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (allRead) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.AutoMirrored.Filled.MenuBook
                    },
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(start = 6.dp),
                )
            }
        }
        ToggleBoxAction(
            icon = if (allRead) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
            contentDescription = stringResource(
                if (allRead) {
                    MR.strings.action_mark_all_as_unread
                } else {
                    MR.strings.action_mark_all_as_read
                },
            ),
            active = allRead,
            activeColor = accent,
            onActiveColor = onAccent,
            onClick = onToggleDoneClick,
        )
    }
}

@Composable
private fun ToggleBoxAction(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    activeColor: Color,
    onActiveColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .then(
                if (active) {
                    Modifier.background(activeColor)
                } else {
                    Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        shape,
                    )
                },
            )
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) {
                onActiveColor
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Android's `EntryGenreRow`: genres as one centered line of accent-coloured,
 * individually clickable spans separated by muted dots; scrolls horizontally
 * when it overflows instead of ellipsizing.
 */
@Composable
private fun GenreRow(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val genreColor = MaterialTheme.colorScheme.primary
    val separatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    val text = remember(genres, genreColor, separatorColor, onGenreClick) {
        buildAnnotatedString {
            genres.forEachIndexed { index, genre ->
                if (index > 0) {
                    withStyle(SpanStyle(color = separatorColor)) {
                        append(" · ")
                    }
                }
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "genre",
                        linkInteractionListener = { onGenreClick(genre) },
                    ),
                ) {
                    withStyle(SpanStyle(color = genreColor)) {
                        append(genre)
                    }
                }
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

/**
 * Android's `ExpandableEntryDescription` — "Description" header, bodyMedium
 * text clamped to 3 lines (overflow-detected, click only when overflowing),
 * and a right-aligned "Less" row when expanded.
 */
@Composable
private fun ExpandableDescription(
    description: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var isOverflowing by remember { mutableStateOf(false) }
    val descColor = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA)
    val accent = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.padding(bottom = 8.dp)) {
        Text(
            text = stringResource(MR.strings.description),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(top = 12.dp, bottom = 4.dp)
                .padding(horizontal = 16.dp),
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clipToBounds()
                .then(
                    if (isOverflowing) {
                        Modifier.clickable { expanded = !expanded }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = description.replace(whitespaceLineRegex, "\n").trimEnd(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = descColor,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.fillMaxWidth(),
                    onTextLayout = { result ->
                        isOverflowing = if (expanded) {
                            result.lineCount > 3
                        } else {
                            result.hasVisualOverflow || result.lineCount > 3
                        }
                    },
                )
                if (expanded && isOverflowing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { expanded = false },
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(MR.strings.manga_info_collapse),
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent,
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private val whitespaceLineRegex = Regex("\\s*\n\\s*")

@Composable
private fun ChapterRow(
    chapter: SNovelChapter,
    read: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = chapter.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (read) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (chapter.chapter_number >= 0f) {
            Text(
                text = "Ch. ${chapter.chapter_number}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (read) 0.5f else 1f,
                ),
            )
        }
    }
}

@Composable
private fun statusLabel(status: Int): String = stringResource(
    when (status) {
        SNovel.ONGOING -> AYMR.strings.status_ongoing
        SNovel.COMPLETED -> AYMR.strings.status_completed
        SNovel.LICENSED -> AYMR.strings.status_licensed
        SNovel.PUBLISHING_FINISHED -> AYMR.strings.status_publishing_finished
        SNovel.CANCELLED -> AYMR.strings.status_cancelled
        SNovel.ON_HIATUS -> AYMR.strings.status_on_hiatus
        else -> AYMR.strings.status_unknown
    },
)
