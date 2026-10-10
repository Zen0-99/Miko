package eu.kanade.tachiyomi.ui.home.catalog

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.kanade.presentation.library.components.armSharedCover
import eu.kanade.presentation.library.components.coverCellMemoryKey
import eu.kanade.presentation.library.components.sharedCoverModifier
import eu.kanade.tachiyomi.data.catalogrows.CatalogRowSpec
import eu.kanade.tachiyomi.data.catalogrows.Feed
import eu.kanade.tachiyomi.data.catalogrows.RowItem
import eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.Companion.CANONICAL_SOURCE_ID
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.metadata.cinemeta.CinemetaCatalogFeed.Companion.CINEMETA_SOURCE_ID
import eu.kanade.tachiyomi.ui.home.hub.CatalogRowError
import eu.kanade.tachiyomi.ui.home.hub.CatalogRowState
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.entries.shows.model.AnimeCover
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.domain.entries.EntryCover as EntryCoverModel

/**
 * One user-built catalog row on the home hub (Phase 18) — section header
 * plus a `LazyRow` of gradient-scrim cover cards, mirroring the recents
 * rows' card family (`HistoryRow` is file-private to the tab, so this
 * carries its own card rather than hoisting the shared one).
 */
@Composable
fun CatalogRow(
    spec: CatalogRowSpec,
    rowState: CatalogRowState?,
    resolveTitle: suspend (CatalogRowSpec) -> String,
    /** Tap-time resolution → (screen, entryId). The card re-keys its
     *  cover under `cover-image-{entryId}` before the push so it morphs
     *  into the detail hero like a library cell; [previewCover] is the
     *  same model the card rendered (bitmap memory-key parity). */
    openItem: suspend (RowItem, previewCover: Any?) -> Pair<Screen, Long>?,
    onItemRendered: (RowItem) -> Unit,
) {
    // A history row with nothing consumed yet renders nothing — an
    // empty "Recently Read" header + chip is noise on a fresh home.
    if (spec.feed is Feed.History &&
        rowState != null &&
        !rowState.loading &&
        rowState.items.isEmpty()
    ) {
        return
    }

    // History headers keep the localized recents labels; other feeds go
    // through the screen model for dynamic names (watch providers,
    // source names).
    val title = if (spec.feed is Feed.History) {
        stringResource(
            when (spec.medium) {
                WorkIdResolver.MEDIUM_SHOWS -> AYMR.strings.label_recent_anime
                WorkIdResolver.MEDIUM_MANGA -> AYMR.strings.label_recently_read
                else -> AYMR.strings.label_recent_novels
            },
        )
    } else {
        val resolved by produceState(initialValue = spec.displayTitle(), spec) {
            value = resolveTitle(spec)
        }
        resolved
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))

        when {
            rowState == null || rowState.loading -> CatalogRowShimmer()
            rowState.error != null -> CatalogRowErrorChip(rowState.error)
            rowState.items.isEmpty() -> CatalogRowEmptyChip()
            else -> CatalogCardRow(
                items = rowState.items,
                medium = spec.medium,
                openItem = openItem,
                onItemRendered = onItemRendered,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CatalogCardRow(
    items: List<RowItem>,
    medium: String,
    openItem: suspend (RowItem, previewCover: Any?) -> Pair<Screen, Long>?,
    onItemRendered: (RowItem) -> Unit,
) {
    val cardShape = RoundedCornerShape(8.dp)
    val gradientScrim = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.35f to Color.Black.copy(alpha = 0.35f),
                1.00f to Color.Black.copy(alpha = 0.82f),
            ),
        )
    }
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val imageContext = LocalContext.current

    // Shared-element latch — the tapped card flies to the detail hero
    // under the library cell's `cover-image-{entryId}`. The entry id only
    // exists after the tap resolves it, so the card re-keys before the
    // push (armSharedCover). Never armed at rest: one entry can appear in
    // several rows, and a duplicate key breaks pairing.
    var navSharedEntry by rememberSaveable { mutableStateOf<Pair<String, Long>?>(null) }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Key = mintKey, else title+position — a bare title isn't unique
        // (favorited extension + canonical rows share it, and extension
        // feeds can repeat one), and a duplicate lazy key is fatal.
        itemsIndexed(items, key = { index, item ->
            item.mintKey ?: "${item.title}#$index"
        }) { index, item ->
            // Mint-on-browse — composition is the render signal, same as
            // the discovery grid.
            LaunchedEffect(Unit) { onItemRendered(item) }
            val itemKey = item.mintKey ?: "${item.title}#$index"

            // The cover model behind the image request — feeds either
            // carry the real entry cover model already (library rows) or
            // a bare url; a stable per-item placeholder id keeps the
            // cell's bitmap keyed under the same `covercell` key the
            // pushed screen's preview cover looks up.
            val coverModel = (item.coverUrl as? EntryCoverModel) ?: run {
                val placeholderId =
                    (item.mintKey ?: item.title).hashCode().toLong()
                val url = item.coverUrl as? String
                when (medium) {
                    WorkIdResolver.MEDIUM_SHOWS ->
                        AnimeCover(placeholderId, CINEMETA_SOURCE_ID, false, url, 0L)
                    WorkIdResolver.MEDIUM_BOOKS ->
                        NovelCover(placeholderId, CANONICAL_SOURCE_ID, false, url, 0L)
                    else ->
                        MangaCover(placeholderId, CANONICAL_SOURCE_ID, false, url, 0L)
                }
            }
            val sharedEntryId = navSharedEntry
                ?.takeIf { it.first == itemKey }
                ?.second

            Box(
                modifier = Modifier
                    .width(128.dp)
                    .height(192.dp)
                    // The library cell's cover-to-hero transition — the
                    // whole card (artwork + scrim + title) morphs into
                    // the detail hero box.
                    .then(sharedCoverModifier(coverModel, enabled = false, entryId = sharedEntryId))
                    .clip(cardShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        scope.launch {
                            val (screen, entryId) = openItem(item, coverModel) ?: return@launch
                            armSharedCover(coverModel, entryId) { navSharedEntry = itemKey to entryId }
                            navigator.push(screen)
                        }
                    },
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(imageContext)
                        .data(coverModel)
                        .memoryCacheKey(coverCellMemoryKey(coverModel))
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(gradientScrim),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = item.title,
                        // Same typeface the library item titles use —
                        // entry titles render one style everywhere.
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White,
                            shadow = Shadow(
                                color = Color.Black,
                                blurRadius = 4f,
                            ),
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!item.subtitle.isNullOrEmpty()) {
                        Text(
                            text = item.subtitle,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRowEmptyChip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(AYMR.strings.home_catalog_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CatalogRowErrorChip(error: CatalogRowError?) {
    val text = when (error) {
        CatalogRowError.NO_TMDB_KEY ->
            stringResource(AYMR.strings.discovery_tmdb_missing_key)
        CatalogRowError.UNSUPPORTED_FEED ->
            stringResource(AYMR.strings.home_catalog_unsupported)
        CatalogRowError.EXTENSION_MISSING ->
            stringResource(AYMR.strings.home_catalog_ext_missing)
        null, CatalogRowError.FETCH_FAILED ->
            stringResource(AYMR.strings.home_catalog_error)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CatalogRowShimmer() {
    val isDark = isSystemInDarkTheme()
    val infiniteTransition = rememberInfiniteTransition(label = "catalog_row_shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "catalog_row_shimmer_alpha",
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(5) {
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .height(192.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isDark) {
                            Color.White.copy(alpha = shimmerAlpha * 0.15f)
                        } else {
                            Color.Black.copy(alpha = shimmerAlpha * 0.12f)
                        },
                    ),
            )
        }
    }
}
