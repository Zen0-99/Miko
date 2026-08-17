package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.ResumeMode
import `is`.xyz.mpv.Utils
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ResumeOverlay(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val showOverlay by viewModel.showResumeOverlay.collectAsState()
    val resumePosition by viewModel.resumePosition.collectAsState()
    val playerPreferences = remember { Injekt.get<PlayerPreferences>() }

    AnimatedVisibility(
        visible = showOverlay,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(MaterialTheme.padding.large)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(MaterialTheme.padding.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                Text(
                    text = stringResource(AYMR.strings.player_resume_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        AYMR.strings.player_resume_message,
                        Utils.prettyTime(resumePosition.toInt()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.dismissResumeOverlay(false) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(AYMR.strings.player_resume_start_over))
                    }
                    Button(
                        onClick = { viewModel.dismissResumeOverlay(true) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(AYMR.strings.player_resume_continue))
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        onClick = {
                            playerPreferences.resumeMode().set(ResumeMode.StartOver)
                            viewModel.dismissResumeOverlay(false)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(AYMR.strings.player_resume_always_start_over))
                    }
                    TextButton(
                        onClick = {
                            playerPreferences.resumeMode().set(ResumeMode.Resume)
                            viewModel.dismissResumeOverlay(true)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(AYMR.strings.player_resume_always_continue))
                    }
                }
            }
        }
    }
}
