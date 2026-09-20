package eu.kanade.presentation.entries.novel.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.IndicatorModifier
import eu.kanade.presentation.components.IndicatorSize
import eu.kanade.presentation.components.IndicatorStrokeWidth
import eu.kanade.presentation.components.commonClickable
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.IconButtonTokens
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * Per-chapter audio-render state — the `NovelDownload.State` analog for the
 * second chapter-row icon (D-07). `NOT_RENDERED`/`RENDERED` are derived from
 * the `.m4a` on disk; `QUEUED`/`RENDERING`/`ERROR` mirror the render queue's
 * `ChapterRender.State`.
 */
enum class NovelChapterAudioState {
    NOT_RENDERED,
    QUEUED,
    RENDERING,
    RENDERED,
    ERROR,
}

@Composable
fun NovelChapterAudioIndicator(
    enabled: Boolean,
    audioStateProvider: () -> NovelChapterAudioState,
    audioProgressProvider: () -> Int,
    onClick: (NovelChapterAudioAction) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
    when (audioStateProvider()) {
        NovelChapterAudioState.NOT_RENDERED -> NotRenderedAudioIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
            tint = accent,
        )
        NovelChapterAudioState.QUEUED -> QueuedAudioIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
            tint = accent,
        )
        NovelChapterAudioState.RENDERING -> RenderingAudioIndicator(
            enabled = enabled,
            modifier = modifier,
            audioProgressProvider = audioProgressProvider,
            onClick = onClick,
            tint = accent,
        )
        NovelChapterAudioState.RENDERED -> RenderedAudioIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
            tint = accent,
        )
        NovelChapterAudioState.ERROR -> AudioErrorIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
    }
}

@Composable
private fun NotRenderedAudioIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (NovelChapterAudioAction) -> Unit,
    tint: Color,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(NovelChapterAudioAction.RENDER_NOW) },
                onClick = { onClick(NovelChapterAudioAction.RENDER) },
            )
            .secondaryItemAlpha(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_audio_item_24dp),
            contentDescription = stringResource(AYMR.strings.novel_audio_render_cd),
            modifier = Modifier.size(IndicatorSize),
            tint = tint,
        )
    }
}

@Composable
private fun QueuedAudioIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (NovelChapterAudioAction) -> Unit,
    tint: Color,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(NovelChapterAudioAction.CANCEL) },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = IndicatorModifier,
            color = tint,
            strokeWidth = IndicatorStrokeWidth,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Butt,
        )
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(AYMR.strings.action_start_render_now)) },
                onClick = {
                    onClick(NovelChapterAudioAction.RENDER_NOW)
                    isMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_cancel)) },
                onClick = {
                    onClick(NovelChapterAudioAction.CANCEL)
                    isMenuExpanded = false
                },
            )
        }
        val queuedDescription = stringResource(AYMR.strings.novel_audio_queued_cd)
        Icon(
            imageVector = Icons.Outlined.Headphones,
            contentDescription = null,
            modifier = AudioGlyphModifier.semantics { contentDescription = queuedDescription },
            tint = tint,
        )
    }
}

@Composable
private fun RenderingAudioIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    audioProgressProvider: () -> Int,
    onClick: (NovelChapterAudioAction) -> Unit,
    tint: Color,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(NovelChapterAudioAction.CANCEL) },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = audioProgressProvider() / 100f,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "progress",
        )
        // Thin ring over a faint track: the outline stays visible and the
        // progress sweep fills it — outline-fill, not a disc (user feedback).
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = IndicatorModifier,
            color = tint,
            strokeWidth = IndicatorStrokeWidth,
            trackColor = tint.copy(alpha = 0.25f),
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
        )
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_cancel)) },
                onClick = {
                    onClick(NovelChapterAudioAction.CANCEL)
                    isMenuExpanded = false
                },
            )
        }
        val renderingDescription = stringResource(AYMR.strings.novel_audio_rendering_cd)
        Icon(
            imageVector = Icons.Outlined.Headphones,
            contentDescription = null,
            modifier = AudioGlyphModifier.semantics { contentDescription = renderingDescription },
            tint = tint,
        )
    }
}

@Composable
private fun RenderedAudioIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (NovelChapterAudioAction) -> Unit,
    tint: Color,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { isMenuExpanded = true },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play_circle_filled_24),
            contentDescription = stringResource(AYMR.strings.novel_audio_available_cd),
            modifier = Modifier.size(IndicatorSize),
            tint = tint,
        )
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            // D-08 verbatim order: Play / Delete / Re-render.
            DropdownMenuItem(
                text = { Text(text = stringResource(AYMR.strings.action_play)) },
                onClick = {
                    onClick(NovelChapterAudioAction.PLAY)
                    isMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_delete)) },
                onClick = {
                    onClick(NovelChapterAudioAction.DELETE)
                    isMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(AYMR.strings.action_re_render_audio)) },
                onClick = {
                    onClick(NovelChapterAudioAction.RE_RENDER)
                    isMenuExpanded = false
                },
            )
        }
    }
}

@Composable
private fun AudioErrorIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (NovelChapterAudioAction) -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(NovelChapterAudioAction.RENDER) },
                onClick = { onClick(NovelChapterAudioAction.RENDER) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = stringResource(AYMR.strings.novel_audio_error_cd),
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

// Inner-glyph size for the ring states — the `ArrowModifier` analog
// (IndicatorSize - 7.dp = 19dp, UI-SPEC spacing exceptions).
private val AudioGlyphModifier = Modifier.size(IndicatorSize - 7.dp)
