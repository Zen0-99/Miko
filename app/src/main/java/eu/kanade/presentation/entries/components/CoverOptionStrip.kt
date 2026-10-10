package eu.kanade.presentation.entries.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * One selectable cover artwork — [key] is the stable pin marker persisted
 * in prefs, [data] any Coil-loadable model (url string or cover object).
 */
data class CoverOption(
    val key: String,
    val label: String,
    val data: Any?,
)

/**
 * Preference key for a pinned cover — the [CoverOption.key] of the
 * artwork the user picked. [kind] scopes the id namespace (`"novel"` /
 * `"manga"` / `"anime"` — entry ids collide across tables).
 */
fun coverPinPrefKey(kind: String, id: Long): String = "cover_pin_${kind}_$id"

const val COVER_PIN_KIND_NOVEL = "novel"
const val COVER_PIN_KIND_MANGA = "manga"
const val COVER_PIN_KIND_ANIME = "anime"

/**
 * Display label for the provider serving a cover url — the art "Auto"
 * resolves to gets its own pickable tile, named after where it's from.
 * Null when the host isn't a known metadata provider.
 */
fun coverProviderLabel(url: String): String? {
    val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return null
    return when {
        "mangadex" in host -> "MangaDex"
        "mangabaka" in host -> "MangaBaka"
        "metron" in host -> "Metron"
        "kitsu" in host -> "Kitsu"
        "anilist" in host -> "AniList"
        "myanimelist" in host -> "MAL"
        "tmdb" in host -> "TMDB"
        "metahub" in host || "cinemeta" in host -> "Cinemeta"
        "openlibrary" in host -> "OpenLibrary"
        else -> null
    }
}

/** `1.0` → `"1"`, `1.5` → `"1.5"` — for the compact `V. n`/`S. n` labels. */
fun coverOrdinal(n: Double): String =
    if (n % 1.0 == 0.0) n.toInt().toString() else n.toString()

private val TileWidth = 72.dp
private val TileHeight = 108.dp
private val TileShape = RoundedCornerShape(10.dp)

/** Reserved strip height: tile + label + the strip's own padding. The
 *  slot is claimed on frame one so arriving options never re-center
 *  the artwork above it. */
private val StripSlotHeight = 160.dp

/**
 * The strip's slot in the cover dialog: a fixed-height container that
 * fades the row in on mount. Reserving the height up front is what
 * keeps the centered image still — a bare `if (options.isNotEmpty())`
 * appear would shove it upward the moment the first tile lands.
 */
@Composable
fun CoverStripSlot(
    options: List<CoverOption>,
    selectedKey: String?,
    onSelect: (CoverOption?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = remember { MutableTransitionState(false) }
    SideEffect { visible.targetState = true }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(StripSlotHeight),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(250)),
            exit = fadeOut(),
        ) {
            CoverOptionStrip(
                options = options,
                selectedKey = selectedKey,
                onSelect = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
            )
        }
    }
}

/**
 * The cover-picker's thumbnail row: an "Auto" tile (clears the user's pin
 * and returns to automatic resolution) followed by every artwork source
 * the entry knows — volume/season covers, per-carrier art, provider art.
 * Selecting a tile pins that art entry-wide until Auto is picked again.
 */
@Composable
fun CoverOptionStrip(
    options: List<CoverOption>,
    selectedKey: String?,
    onSelect: (CoverOption?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "__auto__") {
            Column(
                modifier = Modifier.width(TileWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = TileWidth, height = TileHeight)
                        .clip(TileShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .then(
                            if (selectedKey == null) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, TileShape)
                            } else {
                                Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), TileShape)
                            },
                        )
                        .clickable { onSelect(null) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.secondaryItemAlpha(),
                    )
                }
                Text(
                    text = "Auto",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .secondaryItemAlpha(),
                )
            }
        }
        items(options, key = { it.key }) { option ->
            val selected = option.key == selectedKey
            Column(
                modifier = Modifier.width(TileWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = TileWidth, height = TileHeight)
                        .clip(TileShape)
                        .then(
                            if (selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, TileShape)
                            } else {
                                Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), TileShape)
                            },
                        )
                        .clickable { onSelect(option) },
                ) {
                    AsyncImage(
                        model = option.data,
                        contentDescription = option.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .secondaryItemAlpha(),
                )
            }
        }
    }
}
