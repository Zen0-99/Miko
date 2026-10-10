package tachiyomi.presentation.core.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

@Composable
fun LoadingScreen(
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = color ?: MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * [LoadingScreen] that renders nothing for the first [delayMillis] — loads
 * that complete inside the window (warm cache, fast DB) never flash a
 * spinner; only genuinely slow loads show one.
 */
@Composable
fun DelayedLoadingScreen(
    modifier: Modifier = Modifier,
    color: Color? = null,
    delayMillis: Long = 250,
) {
    val showSpinner by produceState(initialValue = false) {
        delay(delayMillis)
        value = true
    }
    if (showSpinner) {
        LoadingScreen(modifier = modifier, color = color)
    }
}
