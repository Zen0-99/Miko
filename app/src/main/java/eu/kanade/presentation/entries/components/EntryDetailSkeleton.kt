package eu.kanade.presentation.entries.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.EntryScreenItem
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

/**
 * How late into a push a Success may arrive and still swap the skeleton for
 * the real screen. Cached entries resolve within the first frames — the swap
 * happens while the morph has barely moved and reads as a normal open. A
 * Success past this point holds the skeleton (identical geometry) to landing
 * so its mount can't stall the flight.
 */
internal const val SKELETON_LATE_ARRIVAL_MS = 120L

/**
 * How long the skeleton takes to dissolve once the real screen composes —
 * quick enough to read as a settle, slow enough that the ghost bars fade
 * into the text rows they were standing in for instead of popping off.
 */
internal const val SKELETON_FADE_MS = 180

/**
 * Deterministic loading layout for entry detail screens — the same chrome
 * the loaded screen composes (Scaffold + EntryToolbar + LazyColumn with the
 * real [EnhancedEntryInfoBox], continue row, description block and chapter
 * header), filled with what the caller already knows (cover, title, logo).
 *
 * Every shared element (cover, hero image, title, meta, resume pill, nav
 * icons) gets its landing target from the very first frame, computed by
 * the same code as the loaded screen. A cold open used to land them on an
 * approximated preview box that was ~110dp shorter than the real hero; when
 * Success arrived mid-flight the target re-aimed and the cover visibly
 * shifted toward the side it was tapped from. With identical geometry,
 * Success only fills in data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailSkeleton(
    cover: Any?,
    sharedKey: String,
    heroContentPadding: Dp,
    title: String?,
    logo: String?,
    navigateUp: () -> Unit,
    isManga: Boolean,
    panHorizontally: Boolean = false,
    favorite: Boolean = false,
    isVideo: Boolean = false,
    /** The entry's cached cover accent when one exists — the skeleton's
     *  continue row and hero tint use it so a cold open lands already
     *  "in color" instead of swapping gray chrome for accented chrome
     *  when Success arrives. */
    accentColor: Color? = null,
    /** The continue-row label the caller already knows (home hero /
     *  library unread position) — renders inside the shared pill during
     *  Loading so the morph carries real text instead of an icon-only
     *  bar that gets its label pasted in on Success. Null falls back to
     *  "Start" — the most common post-load label. */
    resumeLabel: String? = null,
    /** Whether the pre-filled action is tappable on the loaded screen —
     *  "Continue"/"Start" render the accent fill; only "All up to date"
     *  keeps the muted tint. An unknown state (no [resumeLabel]) also
     *  renders accent — caught-up is the rarer landing. */
    resumeEnabled: Boolean = false,
    /** Grey placeholder bars only make sense when the load is genuinely
     *  slow — a cached entry's Success lands inside a few frames, and
     *  ghosting anyway reads as skeleton-hijacking every transition. The
     *  chrome (toolbar, action row, headers, icons) always renders; the
     *  bars fade in only after the caller's delay. Bars keep their boxes
     *  when hidden — the geometry contract must not shift. */
    showGhosts: Boolean = true,
    /** Whether the loaded screen is expected to render a genre row inside
     *  the action-row item. Callers that already know the entry's genres
     *  (the home hero displays them) pass the real answer — a genre-less
     *  entry gets no ghost bar and no ~28dp of phantom height to shed. */
    genreGhostVisible: Boolean = true,
    /** Reports each ghost item's measured pixel height under its
     *  [EntryScreenItem] key — the loaded screen seeds the matching real
     *  items at these heights for one frame, then animateContentSize
     *  glides every boundary the ghosts got slightly wrong so items
     *  below slide instead of snapping on swap. */
    onItemHeight: (key: Any, heightPx: Int) -> Unit = { _, _ -> },
) {
    val keyId = sharedKey.removePrefix("hero-image-")
    Scaffold(
        topBar = {
            // Download + share present so the toolbar renders the same
            // icon slots (download, overflow) the loaded toolbar has —
            // their shared elements pair from frame one. No-op until load.
            EntryToolbar(
                title = title.orEmpty(),
                hasFilters = false,
                navigateUp = navigateUp,
                onClickFilter = {},
                onClickShare = {},
                onClickDownload = {},
                onClickEditCollection = null,
                onClickMigrate = null,
                onClickSettings = null,
                changeAnimeSkipIntro = null,
                actionModeCounter = 0,
                onCancelActionMode = {},
                onSelectAll = {},
                onInvertSelection = {},
                titleAlphaProvider = { 0f },
                backgroundAlphaProvider = { 0f },
                isManga = isManga,
                toolbarBackgroundColor = MaterialTheme.colorScheme.background,
            )
        },
    ) { contentPadding ->
        val topPadding = contentPadding.calculateTopPadding()
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            userScrollEnabled = false,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding(),
            ),
        ) {
            item(key = EntryScreenItem.INFO_BOX, contentType = EntryScreenItem.INFO_BOX) {
                EnhancedEntryInfoBox(
                    appBarPadding = maxOf(topPadding, statusBarTop),
                    title = title.orEmpty(),
                    backdrop = cover,
                    sharedKey = sharedKey,
                    logoUrl = logo,
                    metaItems = emptyList(),
                    rating = null,
                    onCoverClick = {},
                    heroContentPadding = heroContentPadding,
                    panHorizontally = panHorizontally,
                    previewCover = cover,
                    reserveMeta = true,
                    metaGhostVisible = showGhosts,
                    accentColor = accentColor,
                )
            }
            item(key = EntryScreenItem.ACTION_ROW, contentType = EntryScreenItem.ACTION_ROW) {
                // Same shape as the real item: the continue row, then the
                // genre pill row underneath it (a bar here, not in the
                // description — the loaded screens keep genre pills inside
                // this item).
                Column(
                    modifier = Modifier.onSizeChanged {
                        onItemHeight(EntryScreenItem.ACTION_ROW, it.height)
                    },
                ) {
                    EntryContinueRow(
                        sharedKey = "hero-resume-$keyId",
                        actions = listOf(
                            EntryNextUpAction(
                                icon = if (isVideo) {
                                    Icons.Filled.PlayArrow
                                } else {
                                    Icons.AutoMirrored.Filled.MenuBook
                                },
                                // Known callers pass the real label;
                                // unknown lands as "Start" — the label
                                // most entries resolve to.
                                label = resumeLabel
                                    ?: stringResource(MR.strings.action_start),
                                onClickLabel = stringResource(MR.strings.action_resume),
                                onClick = {},
                            ),
                        ),
                        actionsEnabled = resumeEnabled || resumeLabel == null,
                        accentColor = accentColor,
                        favorite = favorite,
                        allDone = false,
                        favoriteContentDescription = stringResource(MR.strings.add_to_library),
                        doneContentDescription = stringResource(MR.strings.action_mark_all_as_read),
                        onFavoriteClick = {},
                        onToggleDoneClick = {},
                    )
                    if (genreGhostVisible) {
                        // Sized to the real genre row: centered bodyMedium
                        // text with 4dp vertical padding (~28dp), not a
                        // tall left-aligned pill.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        MaterialTheme.colorScheme.onBackground
                                            .copy(alpha = if (showGhosts) 0.08f else 0f),
                                    ),
                            )
                        }
                    }
                }
            }
            item(
                key = EntryScreenItem.DESCRIPTION_WITH_TAG,
                contentType = EntryScreenItem.DESCRIPTION_WITH_TAG,
            ) {
                // Ghost bars at the collapsed description's size. Order
                // matches the real item: "Description" header, then the
                // body text (genres live in the action row's pill strip).
                val bar = MaterialTheme.colorScheme.onBackground
                    .copy(alpha = if (showGhosts) 0.08f else 0f)
                Column(
                    modifier = Modifier
                        .onSizeChanged {
                            onItemHeight(EntryScreenItem.DESCRIPTION_WITH_TAG, it.height)
                        }
                        .padding(bottom = 8.dp),
                ) {
                    // The "Description" header is a fixed label, not data —
                    // render it for real so the swap only fills the body.
                    Text(
                        text = stringResource(MR.strings.description),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 4.dp)
                            .padding(horizontal = 16.dp),
                    )
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        // One slot per real text line — bodyMedium's
                        // lineHeight is the row box and the bar is centered
                        // inside it, so each bar lands exactly where a line
                        // of the loaded description will draw.
                        val lineSlot = with(LocalDensity.current) {
                            MaterialTheme.typography.bodyMedium.lineHeight.toDp()
                        }
                        listOf(1f, 1f, 0.6f).forEach { fraction ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(lineSlot),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(bar),
                                )
                            }
                        }
                    }
                }
            }
            item(key = EntryScreenItem.ITEM_HEADER, contentType = EntryScreenItem.ITEM_HEADER) {
                // Same layout contract as EntryChapterHeader: count text on the
                // left, filter icon on the right. The count itself is a ghost
                // bar — it's always present on the real screen, but the number
                // isn't known yet so nothing may be guessed.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged {
                            onItemHeight(EntryScreenItem.ITEM_HEADER, it.height)
                        }
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                MaterialTheme.colorScheme.onBackground
                                    .copy(alpha = if (showGhosts) 0.08f else 0f),
                            ),
                    )
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground.copy(
                                alpha = SECONDARY_ALPHA,
                            ),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
