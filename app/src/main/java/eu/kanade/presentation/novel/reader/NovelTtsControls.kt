package eu.kanade.presentation.novel.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackState

/**
 * TTS playback controls bar shown above the reader bottom bar.
 *
 * Displays the current paragraph text and play/pause/skip/stop buttons.
 * Visible when TTS playback is active (initialized or playing).
 */
@Composable
fun NovelTtsControlsBar(
    state: NovelTtsPlaybackState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
) {
    AnimatedVisibility(
        visible = state.isInitialized || state.isPlaying,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // Current text preview
                if (state.currentText.isNotEmpty()) {
                    Text(
                        text = state.currentText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                // Error message
                state.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                // Transport controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val controlTint = accentColor ?: MaterialTheme.colorScheme.primary

                    IconButton(onClick = onPrevious) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous paragraph",
                            tint = controlTint,
                        )
                    }

                    // Play/Pause button — highlighted with a circle background
                    IconButton(
                        onClick = if (state.isPlaying) onPause else onPlay,
                        modifier = Modifier
                            .size(48.dp)
                            .background(controlTint.copy(alpha = 0.12f), CircleShape),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Transparent,
                        ),
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = controlTint,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    IconButton(onClick = onNext) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next paragraph",
                            tint = controlTint,
                        )
                    }

                    IconButton(onClick = onStop) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Stop TTS",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Progress indicator
                if (state.totalParagraphs > 0) {
                    Text(
                        text = "${state.currentParagraphIndex + 1} / ${state.totalParagraphs}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}
