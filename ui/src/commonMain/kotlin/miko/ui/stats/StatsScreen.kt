package miko.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.StringResource
import miko.ui.carrier.BrowseCarrier
import miko.ui.extensions.ExtensionHeader
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Android's Statistics screen, scoped — per-carrier sections with counts
 * from the live library + history data.
 */
@Composable
fun StatsScreen(
    model: StatsScreenModel,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()

    if (!state.initialized) {
        LoadingScreen(modifier = modifier.fillMaxSize())
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        state.carriers.forEach { stats ->
            ExtensionHeader(stringResource(carrierLabel(stats.carrier)))
            StatRow(MR.strings.label_library, "${stats.libraryEntries}")
            StatRow(MR.strings.unread, "${stats.unreadItems}")
            StatRow(MR.strings.history, "${stats.historySessions}")
            stats.readDurationMs?.let {
                StatRow(MR.strings.label_read_duration, durationLabel(it))
            }
        }
    }
}

@Composable
private fun StatRow(labelRes: StringResource, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun carrierLabel(carrier: BrowseCarrier): StringResource = when (carrier) {
    BrowseCarrier.SHOWS -> AYMR.strings.carrier_shows
    BrowseCarrier.MANGA -> AYMR.strings.carrier_manga
    BrowseCarrier.BOOKS -> AYMR.strings.carrier_books
}

/** "2h 34m", "45m", "12s" — no String.format (JVM-only on K/N). */
private fun durationLabel(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}
