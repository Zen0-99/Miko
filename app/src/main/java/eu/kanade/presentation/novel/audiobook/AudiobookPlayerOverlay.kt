package eu.kanade.presentation.novel.audiobook

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.entries.components.rememberEntryAccentColor
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlayerController
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlayerState
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlayerTheme
import eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.SleepTimer
import eu.kanade.tachiyomi.util.EntryCoverMetadata
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs
import kotlin.math.roundToInt

// Swipe-down dismiss anchors: 0 = open, 1 = dragged past screen bottom.
private const val ANCHOR_OPEN = 0
private const val ANCHOR_DISMISSED = 1

// What occupies the overlay's middle band — keyed into one Crossfade so the
// cover, card, synced text, and empty states all morph into each other.
private enum class MiddleContent { Card, Text, Empty, Loading }

/**
 * Full-screen audiobook player overlay (REQ-008, D-08).
 *
 * Attached at the app root (MainActivity's navigator Box) so it covers every
 * screen including pushed detail screens. Visibility is a plain state flag
 * on [AudiobookPlayerController] — no navigation, so dismissing (system
 * back or swipe-down, PB-8) leaves playback running through the session
 * service.
 *
 * Styling follows Retro Music Player: a full-bleed blurred-cover backdrop
 * with a dark scrim, a large rounded cover, and a clean transport row.
 */
@Composable
fun AudiobookPlayerOverlay(
    controller: AudiobookPlayerController,
    onOpenNovel: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    // track != null OR loading: play() raises overlayVisible the instant
    // it's tapped, so the player chrome is on screen while the playlist
    // still builds behind it.
    val visible = state.overlayVisible && (state.track != null || state.loading)

    BackHandler(enabled = visible) {
        controller.hideOverlay()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) +
            fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) +
            fadeOut(animationSpec = tween(250)),
        modifier = modifier,
    ) {
        PlayerSurface(state = state, controller = controller, onOpenNovel = onOpenNovel)
    }
}

@Composable
private fun PlayerSurface(
    state: AudiobookPlayerState,
    controller: AudiobookPlayerController,
    onOpenNovel: (Long) -> Unit,
) {
    val context = LocalContext.current
    val coverRequest = remember(state.cover) {
        ImageRequest.Builder(context)
            .data(state.cover)
            .crossfade(true)
            .build()
    }

    // Now-playing theme + synced-text mode (Retro Music Player port) and the
    // novel's cover accent via the app's existing extractor — no theme flash
    // when the color was already pre-extracted while browsing.
    val ttsPrefs = remember { Injekt.get<NovelTtsPreferences>() }
    val theme by ttsPrefs.audiobookPlayerTheme().collectAsState()
    val syncedText by ttsPrefs.audiobookSyncedText().collectAsState()
    val textAccent by ttsPrefs.audiobookSyncedTextAccent().collectAsState()
    val syncedActive = syncedText && state.paragraphs.isNotEmpty()

    // Synced paragraphs need contrast over the art-heavy backdrops — deepen
    // the blur on FULL and GRADIENT while the list is up (same 550ms as the
    // theme/middle crossfades, so the whole transition reads as one morph).
    val syncedBackdropBlur by animateDpAsState(
        targetValue = if (syncedActive) 18.dp else 0.dp,
        animationSpec = tween(550),
        label = "syncedBackdropBlur",
    )
    val accent = rememberEntryAccentColor(
        entryId = state.novelId,
        cover = state.cover,
        type = EntryCoverMetadata.EntryType.NOVEL,
    )
    val onAccent = remember(accent) {
        accent?.let {
            if (ColorUtils.calculateLuminance(it.toArgb()) > 0.45) Color.Black else Color.White
        }
    }

    // ---- Swipe-down dismiss (PB-8) — vertical anchoredDraggable over the
    // whole surface; the seek Slider only consumes horizontal drags.
    val density = LocalDensity.current
    val dismissDistance = with(density) {
        LocalConfiguration.current.screenHeightDp.dp.toPx() * 1.2f
    }
    val decayAnimationSpec = rememberSplineBasedDecay<Float>()
    val draggableState = remember {
        AnchoredDraggableState(
            initialValue = ANCHOR_OPEN,
            snapAnimationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            decayAnimationSpec = decayAnimationSpec,
            positionalThreshold = { totalDistance -> totalDistance * 0.4f },
            velocityThreshold = { with(density) { 800.dp.toPx() } },
        )
    }
    LaunchedEffect(dismissDistance) {
        draggableState.updateAnchors(
            DraggableAnchors {
                ANCHOR_OPEN at 0f
                ANCHOR_DISMISSED at dismissDistance
            },
        )
    }
    // Reset to open when the overlay is re-shown.
    LaunchedEffect(state.overlayVisible) {
        if (state.overlayVisible) draggableState.animateTo(ANCHOR_OPEN)
    }
    LaunchedEffect(draggableState.currentValue) {
        if (draggableState.currentValue == ANCHOR_DISMISSED) {
            controller.hideOverlay()
        }
    }
    val dragProgress by remember {
        derivedStateOf {
            val offset = try {
                draggableState.requireOffset()
            } catch (_: IllegalStateException) {
                0f
            }
            (offset / dismissDistance).coerceIn(0f, 1f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Opaque base — a missing cover must never leak the screen beneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101010)),
        )
        // Backdrop — the Retro now-playing themes differ mostly here.
        // Crossfaded so theme picks morph into each other instead of snapping.
        Crossfade(
            targetState = theme,
            animationSpec = tween(550),
            label = "backdrop",
            modifier = Modifier.fillMaxSize(),
        ) { backdropTheme ->
            when (backdropTheme) {
                AudiobookPlayerTheme.BLUR, AudiobookPlayerTheme.BLUR_CARD -> {
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(32.dp)
                            .alpha(0.45f),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                    )
                }
                AudiobookPlayerTheme.FULL -> {
                    // Cover art edge-to-edge; a bottom-weighted scrim keeps the
                    // controls legible over any art. Synced text blurs the art.
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(syncedBackdropBlur),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.30f),
                                    0.45f to Color.Black.copy(alpha = 0.40f),
                                    1f to Color.Black.copy(alpha = 0.92f),
                                ),
                            ),
                    )
                }
                AudiobookPlayerTheme.COLOR -> {
                    // Cover-accent wash fading into the dark base.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to (accent ?: MaterialTheme.colorScheme.primary)
                                        .copy(alpha = 0.55f),
                                    0.55f to Color(0xFF101010),
                                    1f to Color(0xFF101010),
                                ),
                            ),
                    )
                }
                AudiobookPlayerTheme.GRADIENT -> {
                    // The entry-detail header's recipe: accent wash, softly
                    // blurred art, then a transparent→base gradient.
                    if (accent != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(accent.copy(alpha = 0.3f)),
                        )
                    }
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(4.dp + syncedBackdropBlur)
                            .alpha(0.15f),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xFF101010)),
                                ),
                            ),
                    )
                }
                AudiobookPlayerTheme.CARD, AudiobookPlayerTheme.PLAIN -> {
                    // Flat backdrop — the opaque base carries these themes.
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    val oy = try {
                        draggableState.requireOffset()
                    } catch (_: IllegalStateException) {
                        0f
                    }
                    IntOffset(0, oy.roundToInt())
                }
                .graphicsLayer { alpha = 1f - dragProgress * 0.6f }
                .anchoredDraggable(state = draggableState, orientation = Orientation.Vertical)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar — collapse chevron + speed text + overflow menu.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = controller::hideOverlay) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(AYMR.strings.action_dismiss),
                        tint = Color.White,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                SpeedButton(state = state, accent = accent, onSetSpeed = controller::setSpeed)
                PlayerOptionsMenu(
                    state = state,
                    syncedText = syncedText,
                    textAccent = textAccent,
                    theme = theme,
                    accent = accent,
                    onToggleSynced = { ttsPrefs.audiobookSyncedText().set(it) },
                    onToggleTextAccent = { ttsPrefs.audiobookSyncedTextAccent().set(it) },
                    onPickTheme = { ttsPrefs.audiobookPlayerTheme().set(it) },
                    controller = controller,
                )
            }

            // Foreground art only as a framed card in CARD and BLUR_CARD.
            // BLUR is Full-with-a-blur: the art lives only in the backdrop.
            val coverVisible = theme == AudiobookPlayerTheme.CARD ||
                theme == AudiobookPlayerTheme.BLUR_CARD
            val coverShape = RoundedCornerShape(16.dp)

            // Middle band — everything crossfades: card ⇄ synced text ⇄
            // empty, so theme switches and the cover-tap lyrics toggle morph
            // instead of snapping (~550ms). The art fills the band (Book's
            // 2:3 aspectRatio resolves against the padding).
            val middleContent = when {
                state.loading -> MiddleContent.Loading
                syncedActive -> MiddleContent.Text
                coverVisible -> MiddleContent.Card
                else -> MiddleContent.Empty
            }
            Crossfade(
                targetState = middleContent,
                animationSpec = tween(550),
                label = "middleContent",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { content ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            enabled = state.paragraphs.isNotEmpty(),
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { ttsPrefs.audiobookSyncedText().set(!syncedText) },
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    when (content) {
                        MiddleContent.Loading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = accent ?: Color.White,
                            )
                        }
                        MiddleContent.Text -> SyncedParagraphs(
                            paragraphs = state.paragraphs,
                            offsetsMs = state.paragraphOffsetsMs,
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            // Text Accent off → plain white highlight.
                            activeColor = if (textAccent) accent ?: Color.White else Color.White,
                            onSeekTo = controller::seekTo,
                            modifier = Modifier.fillMaxSize(),
                        )
                        MiddleContent.Empty -> Unit
                        MiddleContent.Card -> ItemCover.Book(
                            data = coverRequest,
                            shape = coverShape,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 44.dp, end = 44.dp, top = 16.dp)
                                .shadow(32.dp, coverShape)
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.10f),
                                    coverShape,
                                ),
                        )
                    }
                }
            }

            // Bottom block — novel name carries the headline, chapter is the
            // secondary line. Anchored to the bottom in every theme with a
            // uniform 24dp gap to whatever sits in the middle band.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = state.novelTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            enabled = state.novelId != null,
                            onClickLabel = stringResource(AYMR.strings.audiobook_open_novel_cd),
                        ) {
                            controller.hideOverlay()
                            state.novelId?.let(onOpenNovel)
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.track?.chapterName
                        ?: state.pendingChapterName.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (state.loading) {
                    CircularProgressIndicator(
                        color = accent ?: Color.White,
                        modifier = Modifier.height(88.dp),
                    )
                } else {
                    SeekRow(state = state, accent = accent, onSeekTo = controller::seekTo)

                    Spacer(modifier = Modifier.height(8.dp))

                    TransportRow(
                        state = state,
                        accent = accent,
                        onAccent = onAccent,
                        onTogglePause = controller::togglePause,
                        onRewind = controller::rewind,
                        onForward = controller::forward,
                        onPrevious = controller::previousChapter,
                        onNext = controller::nextChapter,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SeekRow(state: AudiobookPlayerState, accent: Color?, onSeekTo: (Long) -> Unit) {
    // Scrub-live (PB-12): local value while dragging, commit on release.
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val position = scrubPosition?.toLong() ?: state.positionMs.coerceIn(0L, duration)
    val activeColor = accent ?: Color.White
    val sliderColors = SliderDefaults.colors(
        thumbColor = activeColor,
        activeTrackColor = activeColor,
        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = position.toFloat(),
            onValueChange = { scrubPosition = it },
            onValueChangeFinished = {
                scrubPosition?.let { onSeekTo(it.toLong()) }
                scrubPosition = null
            },
            valueRange = 0f..duration.toFloat(),
            colors = sliderColors,
            // No end-of-track stop dots — a continuous line reads cleaner.
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = sliderColors,
                    drawStopIndicator = null,
                )
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDurationMs(position),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = "-${formatDurationMs(duration - position)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun TransportRow(
    state: AudiobookPlayerState,
    accent: Color?,
    onAccent: Color?,
    onTogglePause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(
            onClick = onPrevious,
            // PB-13: disabled at playlist bounds, not hidden.
            enabled = state.chapterIndex > 0,
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(AYMR.strings.audiobook_previous_chapter_cd),
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onRewind) {
            Icon(
                imageVector = Icons.Filled.Replay30,
                contentDescription = stringResource(AYMR.strings.audiobook_rewind_cd),
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        FilledIconButton(
            onClick = onTogglePause,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = accent ?: Color.White,
                contentColor = onAccent ?: Color.Black,
            ),
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (state.isPlaying) AYMR.strings.action_pause else AYMR.strings.action_play,
                ),
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = onForward) {
            Icon(
                imageVector = Icons.Filled.Forward10,
                contentDescription = stringResource(AYMR.strings.audiobook_forward_cd),
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(
            onClick = onNext,
            enabled = state.chapterIndex in 0 until state.chapterCount - 1,
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(AYMR.strings.audiobook_next_chapter_cd),
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun SpeedButton(
    state: AudiobookPlayerState,
    accent: Color?,
    onSetSpeed: (Float) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    // Plain unfilled text — reads as a label, not a chip.
    Text(
        text = "%.1f×".format(state.speed),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { dialogOpen = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )

    if (dialogOpen) {
        // PB-9: 0.5–3.0 in 0.1 steps — same convention as the TTS rate control.
        var sliderValue by remember { mutableStateOf(state.speed) }
        val sheetAccent = accent ?: MaterialTheme.colorScheme.primary
        val onAccent = if (ColorUtils.calculateLuminance(sheetAccent.toArgb()) > 0.45) {
            Color.Black
        } else {
            Color.White
        }
        AdaptiveSheet(onDismissRequest = { dialogOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TabbedDialogPaddings.Horizontal)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = stringResource(AYMR.strings.audiobook_speed_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Text(
                    text = "%.1f×".format(sliderValue),
                    style = MaterialTheme.typography.displaySmall,
                    color = sheetAccent,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp),
                )
                val speedSliderColors = SliderDefaults.colors(
                    thumbColor = sheetAccent,
                    activeTrackColor = sheetAccent,
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = (it * 10).roundToInt() / 10f },
                    onValueChangeFinished = { onSetSpeed(sliderValue) },
                    valueRange = 0.5f..3.0f,
                    colors = speedSliderColors,
                    // Rounded to 0.1 in onValueChange — no visible steps/ticks.
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = speedSliderColors,
                            drawStopIndicator = null,
                        )
                    },
                )
                // Quick presets — one tap jumps the slider and applies.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).forEach { preset ->
                        // Exact-match epsilon: a loose tolerance lit the 1.25
                        // chip for 1.2 and 1.3 as well (both are ~0.0499 away).
                        val selected = abs(sliderValue - preset) < 0.001f
                        Text(
                            // toString, not %.2g — that rounded 1.25 to "1.3".
                            text = "$preset×",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                onAccent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) {
                                        sheetAccent
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                )
                                .clickable {
                                    sliderValue = preset
                                    onSetSpeed(preset)
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Three-dot overflow — sleep timer, player theme, synced text. Tints when the
 * sleep timer is armed so armed state stays visible without a dedicated icon.
 */
@Composable
private fun PlayerOptionsMenu(
    state: AudiobookPlayerState,
    syncedText: Boolean,
    textAccent: Boolean,
    theme: AudiobookPlayerTheme,
    accent: Color?,
    onToggleSynced: (Boolean) -> Unit,
    onToggleTextAccent: (Boolean) -> Unit,
    onPickTheme: (AudiobookPlayerTheme) -> Unit,
    controller: AudiobookPlayerController,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var themeDialogOpen by remember { mutableStateOf(false) }
    var sleepDialogOpen by remember { mutableStateOf(false) }
    val armed = state.sleepTimer !is SleepTimer.Off

    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                tint = if (armed) accent ?: MaterialTheme.colorScheme.primary else Color.White,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.audiobook_sleep_timer)) },
                trailingIcon = if (armed) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    menuOpen = false
                    sleepDialogOpen = true
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.audiobook_theme_title)) },
                onClick = {
                    menuOpen = false
                    themeDialogOpen = true
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.audiobook_synced_text)) },
                trailingIcon = if (syncedText) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    menuOpen = false
                    onToggleSynced(!syncedText)
                },
            )
            if (syncedText) {
                DropdownMenuItem(
                    text = { Text(stringResource(AYMR.strings.audiobook_synced_text_accent)) },
                    trailingIcon = if (textAccent) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        menuOpen = false
                        onToggleTextAccent(!textAccent)
                    },
                )
            }
        }
    }

    val sheetAccent = accent ?: MaterialTheme.colorScheme.primary

    if (themeDialogOpen) {
        AdaptiveSheet(onDismissRequest = { themeDialogOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TabbedDialogPaddings.Horizontal)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = stringResource(AYMR.strings.audiobook_theme_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                AudiobookPlayerTheme.entries.forEach { option ->
                    SleepTimerRow(
                        label = stringResource(option.labelRes),
                        selected = option == theme,
                        selectedColor = sheetAccent,
                        onClick = {
                            onPickTheme(option)
                            themeDialogOpen = false
                        },
                    )
                }
            }
        }
    }

    if (sleepDialogOpen) {
        SleepTimerDialog(
            state = state,
            accent = accent,
            controller = controller,
            onDismiss = { sleepDialogOpen = false },
        )
    }
}

@Composable
private fun SleepTimerDialog(
    state: AudiobookPlayerState,
    accent: Color?,
    controller: AudiobookPlayerController,
    onDismiss: () -> Unit,
) {
    val armed = state.sleepTimer !is SleepTimer.Off
    val now = System.currentTimeMillis()
    val sheetAccent = accent ?: MaterialTheme.colorScheme.primary
    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TabbedDialogPaddings.Horizontal)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(AYMR.strings.audiobook_sleep_timer),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            // Active-state line (PB-10 countdown).
            when (val timer = state.sleepTimer) {
                is SleepTimer.Minutes -> Text(
                    text = stringResource(
                        AYMR.strings.audiobook_sleep_timer_stops_in,
                        formatDurationMs(timer.remainingMs(now)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sheetAccent,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                SleepTimer.EndOfChapter -> Text(
                    text = stringResource(AYMR.strings.audiobook_sleep_timer_stops_at_chapter_end),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sheetAccent,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                SleepTimer.Off -> Unit
            }
            SleepTimer.MINUTE_PRESETS.forEach { minutes ->
                SleepTimerRow(
                    label = stringResource(AYMR.strings.audiobook_sleep_timer_minutes, minutes),
                    selected = (state.sleepTimer as? SleepTimer.Minutes)?.presetMinutes == minutes,
                    selectedColor = sheetAccent,
                    onClick = {
                        controller.armSleepMinutes(minutes)
                        onDismiss()
                    },
                )
            }
            SleepTimerRow(
                label = stringResource(AYMR.strings.audiobook_sleep_timer_end_of_chapter),
                selected = state.sleepTimer is SleepTimer.EndOfChapter,
                selectedColor = sheetAccent,
                onClick = {
                    controller.armEndOfChapter()
                    onDismiss()
                },
            )
            if (armed) {
                SleepTimerRow(
                    label = stringResource(AYMR.strings.audiobook_sleep_timer_off),
                    selected = false,
                    selectedColor = sheetAccent,
                    onClick = {
                        controller.disarmSleepTimer()
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun SleepTimerRow(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) selectedColor else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    )
}

/**
 * Retro's synced-lyrics list adapted to rendered-paragraph timing: the active
 * paragraph is computed from the manifest's `paragraphOffsetsMs` and tapping a
 * paragraph seeks to its offset.
 *
 * Scroll target is a fraction down the active paragraph pinned just above the
 * midline: short paragraphs sit centered (fraction 0.5) while paragraphs taller
 * than ~55% of the viewport get a continuous pan — the fraction follows the
 * audio position between this paragraph's offset and the next one's, so long
 * paragraphs glide through the viewport instead of overflowing it. The pan is
 * re-anchored every position tick (500ms) with a linear tween, and suspends
 * while the user is dragging.
 */
@Composable
private fun SyncedParagraphs(
    paragraphs: List<String>,
    offsetsMs: List<Long>,
    positionMs: Long,
    durationMs: Long,
    activeColor: Color,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val activeIndex = offsetsMs.indexOfLast { it <= positionMs }.coerceAtLeast(0)
    // Plain Long params don't register snapshot reads — re-wrap so the
    // follow-loop below observes each 500ms tick.
    val currentPos by rememberUpdatedState(positionMs)

    LaunchedEffect(activeIndex) {
        fun targetDelta(): Float? {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == activeIndex }
                ?: return null
            val viewport = (info.viewportEndOffset - info.viewportStartOffset)
                .coerceAtLeast(1)
            val startMs = offsetsMs.getOrNull(activeIndex) ?: return null
            val endMs = offsetsMs.getOrNull(activeIndex + 1) ?: durationMs
            val spanMs = (endMs - startMs).coerceAtLeast(1L)
            val tall = item.size > viewport * 0.55f
            val frac = if (tall) {
                ((currentPos - startMs).toFloat() / spanMs).coerceIn(0f, 1f)
            } else {
                0.5f
            }
            val anchor = info.viewportStartOffset + viewport * 0.45f
            return (item.offset + item.size * frac) - anchor
        }

        // Transition to the newly-active item.
        var delta = targetDelta()
        if (delta == null) {
            // Item not laid out yet — jump to it first, then re-anchor.
            if (activeIndex in paragraphs.indices) {
                listState.scrollToItem(activeIndex)
                delta = targetDelta()
            }
        }
        if (delta != null && abs(delta) > 24) {
            listState.animateScrollBy(delta)
        }

        // Follow playback through tall paragraphs.
        snapshotFlow { currentPos }.collect {
            if (listState.isScrollInProgress) return@collect
            val d = targetDelta() ?: return@collect
            if (abs(d) > 8f) {
                listState.animateScrollBy(d, tween(450, easing = LinearEasing))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        // Generous end padding so the first/last paragraphs can center.
        contentPadding = PaddingValues(vertical = 130.dp),
    ) {
        itemsIndexed(paragraphs) { index, text ->
            val active = index == activeIndex
            val color by animateColorAsState(
                targetValue = if (active) activeColor else Color.White.copy(alpha = 0.38f),
                animationSpec = tween(250),
                label = "paragraphColor",
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = color,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = index < offsetsMs.size) { onSeekTo(offsetsMs[index]) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

private val AudiobookPlayerTheme.labelRes
    get() = when (this) {
        AudiobookPlayerTheme.BLUR -> AYMR.strings.audiobook_theme_blur
        AudiobookPlayerTheme.BLUR_CARD -> AYMR.strings.audiobook_theme_blur_card
        AudiobookPlayerTheme.CARD -> AYMR.strings.audiobook_theme_card
        AudiobookPlayerTheme.FULL -> AYMR.strings.audiobook_theme_full
        AudiobookPlayerTheme.COLOR -> AYMR.strings.audiobook_theme_color
        AudiobookPlayerTheme.GRADIENT -> AYMR.strings.audiobook_theme_gradient
        AudiobookPlayerTheme.PLAIN -> AYMR.strings.audiobook_theme_plain
    }

/** `m:ss` for sub-hour durations, `h:mm:ss` past an hour. */
private fun formatDurationMs(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
