package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Simple top bar layout with optional back navigation and actions.
 * Uses MaterialTheme color scheme — mode-aware via the per-content-mode
 * TachiyomiTheme wrapper.
 */
@Composable
fun SimpleTopBarLayout(
    title: String,
    titleContent: (@Composable () -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onNavigateUp != null) {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(MR.strings.action_bar_up_description),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        if (titleContent != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (onNavigateUp != null) 12.dp else 4.dp,
                        end = 12.dp,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                titleContent()
            }
        } else {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (onNavigateUp != null) 12.dp else 4.dp,
                        end = 12.dp,
                    ),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}