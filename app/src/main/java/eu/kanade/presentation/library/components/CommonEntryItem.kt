package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.ImageRequest
import eu.kanade.presentation.entries.components.HeroSharedKind
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.entries.components.PreExtractEntryCoverColor
import eu.kanade.presentation.entries.components.heroSharedBoundsModifier
import eu.kanade.tachiyomi.util.EntryCoverMetadata
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.entries.shows.model.AnimeCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground
import tachiyomi.domain.entries.EntryCover as EntryCoverModel

object CommonEntryItemDefaults {
    val GridHorizontalSpacer = 4.dp
    val GridVerticalSpacer = 4.dp

    @Suppress("ConstPropertyName")
    const val BrowseFavoriteCoverAlpha = 0.34f
}

private val ContinueViewingButtonSizeSmall = 28.dp
private val ContinueViewingButtonSizeLarge = 30.dp

private val ContinueViewingButtonIconSizeSmall = 16.dp
private val ContinueViewingButtonIconSizeLarge = 18.dp

private val ContinueViewingButtonGridPadding = 6.dp
private val ContinueViewingButtonListSpacing = 8.dp

private const val GRID_SELECTED_COVER_ALPHA = 0.76f

/** Cover-to-hero shared element: the library cover flies to the detail
 *  hero artwork, keyed by the entry's domain id (the same id detail uses
 *  for "hero-image-…"). Only for surfaces where an entry's cover appears
 *  at most once — duplicate keys inside one screen break pairing. */
private fun coverSharedKey(coverData: EntryCoverModel): String? =
    coverEntryIdOf(coverData)?.let { "cover-image-$it" }

private fun coverEntryIdOf(coverData: EntryCoverModel?): Long? = when (coverData) {
    is MangaCover -> coverData.mangaId
    is NovelCover -> coverData.novelId
    is AnimeCover -> coverData.animeId
    else -> null
}

private fun coverEntryTypeOf(coverData: EntryCoverModel?): EntryCoverMetadata.EntryType? = when (coverData) {
    is MangaCover -> EntryCoverMetadata.EntryType.MANGA
    is NovelCover -> EntryCoverMetadata.EntryType.NOVEL
    is AnimeCover -> EntryCoverMetadata.EntryType.SHOWS
    else -> null
}

/**
 * The library cell's cover-to-hero modifier ("cover-image-{id}" sharedBounds,
 * [HeroSharedKind.Cover]). [entryId] overrides the cover model's id for
 * catalog/discovery cells whose cover is keyed by a placeholder until the
 * tap resolves the real entry; null with [enabled] keys off the model.
 */
@Composable
internal fun sharedCoverModifier(
    coverData: EntryCoverModel,
    enabled: Boolean,
    entryId: Long? = null,
): Modifier = when {
    entryId != null -> heroSharedBoundsModifier("cover-image-$entryId", HeroSharedKind.Cover)
    enabled -> heroSharedBoundsModifier(coverSharedKey(coverData), HeroSharedKind.Cover)
    else -> Modifier
}

/**
 * Tap-time arming for cells whose shared cover key is only known after
 * resolution: carries the placeholder-keyed accent over to [entryId] (the
 * detail skeleton reads it by real id), runs [arm] to re-key the cell, then
 * waits for the re-keyed cell to be laid out — a key that first appears in
 * the push frame has no source bounds and the morph silently doesn't run.
 * Hold the latch [arm] sets in `rememberSaveable`: the source screen leaves
 * composition while covered, and a plain `remember` latch comes back null
 * on pop — the cell has no key to fly home to, so the pop only fades.
 */
suspend fun armSharedCover(coverData: EntryCoverModel, entryId: Long, arm: () -> Unit) {
    val type = coverEntryTypeOf(coverData)
    val placeholderId = coverEntryIdOf(coverData)
    if (type != null &&
        placeholderId != null &&
        placeholderId != entryId &&
        EntryCoverMetadata.getBaseColor(type, entryId) == null
    ) {
        EntryCoverMetadata.getBaseColor(type, placeholderId)
            ?.let { EntryCoverMetadata.setBaseColor(type, entryId, it) }
    }
    arm()
    repeat(2) { withFrameNanos { } }
}

/** Deterministic memory-cache key for a cell's cover bitmap, shared with
 *  the detail hero's `placeholderMemoryCacheKey`: the hero request paints
 *  this exact bitmap from frame 0 (a placeholder lookup skips Coil's size
 *  validation — the ~400px cell bitmap would never satisfy a ~1344px hero
 *  request) instead of decoding the same file at hero size mid-flight.
 *  url+lastModified invalidate on cover changes, same as the cover keyers. */
internal fun coverCellMemoryKey(coverData: EntryCoverModel?): String? = when (coverData) {
    is MangaCover -> "covercell;manga;${coverData.mangaId};${coverData.url};${coverData.lastModified}"
    is NovelCover -> "covercell;novel;${coverData.novelId};${coverData.url};${coverData.lastModified}"
    is AnimeCover -> "covercell;anime;${coverData.animeId};${coverData.url};${coverData.lastModified}"
    else -> null
}

/** The cover model wrapped in a request carrying [coverCellMemoryKey], so
 *  the decoded bitmap is stored under the key the detail hero looks up. */
@Composable
private fun keyedCoverData(coverData: EntryCoverModel): Any {
    val context = LocalContext.current
    return remember(coverData) {
        coverCellMemoryKey(coverData)?.let { key ->
            ImageRequest.Builder(context)
                .data(coverData)
                .memoryCacheKey(key)
                .build()
        } ?: coverData
    }
}

/** Pre-extracts the cover accent for any medium — detail screens seed
 *  from [EntryCoverMetadata] so the accent lands with no flash. */
@Composable
private fun PreExtractItemCoverColor(coverData: EntryCoverModel) {
    when (coverData) {
        is MangaCover -> PreExtractEntryCoverColor(coverData.mangaId, coverData, EntryCoverMetadata.EntryType.MANGA)
        is NovelCover -> PreExtractEntryCoverColor(coverData.novelId, coverData, EntryCoverMetadata.EntryType.NOVEL)
        is AnimeCover -> PreExtractEntryCoverColor(coverData.animeId, coverData, EntryCoverMetadata.EntryType.SHOWS)
        else -> Unit
    }
}

/**
 * Layout of grid list item with title overlaying the cover.
 * Accepts null [title] for a cover-only view.
 */
@Composable
fun EntryCompactGridItem(
    coverData: EntryCoverModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    title: String? = null,
    onClickContinueViewing: (() -> Unit)? = null,
    coverAlpha: Float = 1f,
    customCover: (@Composable BoxScope.() -> Unit)? = null,
    coverBadgeStart: @Composable (RowScope.() -> Unit)? = null,
    coverBadgeEnd: @Composable (RowScope.() -> Unit)? = null,
    topEndBadge: (@Composable BoxScope.() -> Unit)? = null,
    bottomStartBadge: (@Composable BoxScope.() -> Unit)? = null,
    sharedCoverTransition: Boolean = false,
    /** Library cover-to-hero transition keyed by an explicit entry id —
     *  for cells whose cover model carries a placeholder id until a tap
     *  resolves the real entry (see [armSharedCover]). */
    sharedCoverEntryId: Long? = null,
) {
    GridItemSelectable(
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        EntryGridCover(
            modifier = sharedCoverModifier(coverData, sharedCoverTransition, sharedCoverEntryId),
            cover = {
                if (customCover != null) {
                    customCover()
                } else {
                    ItemCover.Book(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isSelected) GRID_SELECTED_COVER_ALPHA else coverAlpha),
                        data = keyedCoverData(coverData),
                    )
                    PreExtractItemCoverColor(coverData)
                }
            },
            badgesStart = coverBadgeStart,
            badgesEnd = coverBadgeEnd,
            topEndBadge = topEndBadge,
            bottomStartBadge = bottomStartBadge,
            content = {
                if (title != null) {
                    CoverTextOverlay(
                        title = title,
                        onClickContinueViewing = onClickContinueViewing,
                    )
                } else if (onClickContinueViewing != null) {
                    ContinueViewingButton(
                        size = ContinueViewingButtonSizeLarge,
                        iconSize = ContinueViewingButtonIconSizeLarge,
                        onClick = onClickContinueViewing,
                        modifier = Modifier
                            .padding(ContinueViewingButtonGridPadding)
                            .align(Alignment.BottomEnd),
                    )
                }
            },
        )
    }
}

/**
 * Title overlay for [EntryCompactGridItem]
 */
@Composable
private fun BoxScope.CoverTextOverlay(
    title: String,
    onClickContinueViewing: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0xAA000000),
                ),
            )
            .fillMaxHeight(0.33f)
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
    )
    Row(
        modifier = Modifier.align(Alignment.BottomStart),
        verticalAlignment = Alignment.Bottom,
    ) {
        GridItemTitle(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
            title = title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = Color.White,
                shadow = Shadow(
                    color = Color.Black,
                    blurRadius = 4f,
                ),
            ),
            minLines = 1,
        )
        if (onClickContinueViewing != null) {
            ContinueViewingButton(
                size = ContinueViewingButtonSizeSmall,
                iconSize = ContinueViewingButtonIconSizeSmall,
                onClick = onClickContinueViewing,
                modifier = Modifier.padding(
                    end = ContinueViewingButtonGridPadding,
                    bottom = ContinueViewingButtonGridPadding,
                ),
            )
        }
    }
}

/**
 * Layout of grid list item with title below the cover.
 */
@Composable
fun EntryComfortableGridItem(
    isSelected: Boolean = false,
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    titleMaxLines: Int = 2,
    coverData: EntryCoverModel,
    coverAlpha: Float = 1f,
    customCover: (@Composable BoxScope.() -> Unit)? = null,
    coverBadgeStart: (@Composable RowScope.() -> Unit)? = null,
    coverBadgeEnd: (@Composable RowScope.() -> Unit)? = null,
    topEndBadge: (@Composable BoxScope.() -> Unit)? = null,
    bottomStartBadge: (@Composable BoxScope.() -> Unit)? = null,
    onClickContinueViewing: (() -> Unit)? = null,
    subtitle: String? = null,
    sharedCoverTransition: Boolean = false,
    /** See [EntryCompactGridItem]. */
    sharedCoverEntryId: Long? = null,
) {
    GridItemSelectable(
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Column {
            EntryGridCover(
                modifier = sharedCoverModifier(coverData, sharedCoverTransition, sharedCoverEntryId),
                cover = {
                    if (customCover != null) {
                        customCover()
                    } else {
                        ItemCover.Book(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isSelected) GRID_SELECTED_COVER_ALPHA else coverAlpha),
                            data = keyedCoverData(coverData),
                        )
                        PreExtractItemCoverColor(coverData)
                    }
                },
                badgesStart = coverBadgeStart,
                badgesEnd = coverBadgeEnd,
                topEndBadge = topEndBadge,
                bottomStartBadge = bottomStartBadge,
                content = {
                    if (onClickContinueViewing != null) {
                        ContinueViewingButton(
                            size = ContinueViewingButtonSizeLarge,
                            iconSize = ContinueViewingButtonIconSizeLarge,
                            onClick = onClickContinueViewing,
                            modifier = Modifier
                                .padding(ContinueViewingButtonGridPadding)
                                .align(Alignment.BottomEnd),
                        )
                    }
                },
            )
            // Title + optional subtitle (author) — subtitle shows only when title fits in 1 line
            if (subtitle != null) {
                Column(modifier = Modifier.padding(4.dp)) {
                    GridItemTitle(
                        title = title,
                        style = MaterialTheme.typography.titleSmall,
                        minLines = 1,
                        maxLines = titleMaxLines,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                GridItemTitle(
                    modifier = Modifier.padding(4.dp),
                    title = title,
                    style = MaterialTheme.typography.titleSmall,
                    minLines = 2,
                    maxLines = titleMaxLines,
                )
            }
        }
    }
}

/**
 * Common cover layout to add contents to be drawn on top of the cover.
 */
@Composable
private fun EntryGridCover(
    modifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit = {},
    badgesStart: (@Composable RowScope.() -> Unit)? = null,
    badgesEnd: (@Composable RowScope.() -> Unit)? = null,
    topEndBadge: (@Composable BoxScope.() -> Unit)? = null,
    bottomStartBadge: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ItemCover.Book.ratio),
    ) {
        cover()
        content?.invoke(this)
        if (badgesStart != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
                content = badgesStart,
            )
        }

        if (badgesEnd != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopEnd),
                content = badgesEnd,
            )
        }

        if (topEndBadge != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                topEndBadge.invoke(this@Box)
            }
        }

        if (bottomStartBadge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            ) {
                bottomStartBadge.invoke(this@Box)
            }
        }
    }
}

@Composable
private fun GridItemTitle(
    title: String,
    style: TextStyle,
    minLines: Int,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    Text(
        modifier = modifier,
        text = title,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        minLines = minLines,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = style,
    )
}

/**
 * Wrapper for grid items to handle selection state, click and long click.
 */
@Composable
private fun GridItemSelectable(
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .selectedOutline(isSelected = isSelected, color = MaterialTheme.colorScheme.secondary)
            .padding(4.dp),
    ) {
        val contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onSecondary
        } else {
            LocalContentColor.current
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * @see GridItemSelectable
 */
private fun Modifier.selectedOutline(
    isSelected: Boolean,
    color: Color,
) = drawBehind { if (isSelected) drawRect(color = color) }

/**
 * Layout of list item.
 */
@Composable
fun EntryListItem(
    isSelected: Boolean = false,
    title: String,
    coverData: EntryCoverModel,
    coverAlpha: Float = 1f,
    customCover: (@Composable BoxScope.() -> Unit)? = null,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    badge: @Composable (RowScope.() -> Unit),
    onClickContinueViewing: (() -> Unit)? = null,
    entries: Int = 0,
    containerHeight: Int = 0,
    subtitle: String? = null,
    sharedCoverTransition: Boolean = false,
    /** See [EntryCompactGridItem]. */
    sharedCoverEntryId: Long? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectedBackground(isSelected)
            .height(
                when (entries) {
                    0 -> 56.dp
                    else -> {
                        val density = LocalDensity.current
                        with(density) { (containerHeight / entries).toDp() } - (3 / entries).dp
                    }
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .then(sharedCoverModifier(coverData, sharedCoverTransition, sharedCoverEntryId))
                .fillMaxHeight()
                .aspectRatio(ItemCover.Book.ratio),
        ) {
            if (customCover != null) {
                customCover()
            } else {
                ItemCover.Book(
                    modifier = Modifier
                        .fillMaxHeight()
                        .alpha(coverAlpha),
                    data = keyedCoverData(coverData),
                )
                PreExtractItemCoverColor(coverData)
            }
        }
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
        ) {
            Text(
                text = title,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        BadgeGroup(content = badge)
        if (onClickContinueViewing != null) {
            ContinueViewingButton(
                size = ContinueViewingButtonSizeSmall,
                iconSize = ContinueViewingButtonIconSizeSmall,
                onClick = onClickContinueViewing,
                modifier = Modifier.padding(start = ContinueViewingButtonListSpacing),
            )
        }
    }
}

@Composable
private fun ContinueViewingButton(
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(Color(0xAD212121))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = stringResource(MR.strings.action_resume),
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}
