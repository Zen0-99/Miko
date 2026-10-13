package miko.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.StringResource
import miko.ui.carrier.BrowseCarrier
import miko.ui.extensions.ExtensionHeader
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Android's per-carrier Statistics screens (Manga/Shows/Novel content
 * composables), folded into one column — the same four SectionCards per
 * carrier: overview, titles, items, trackers. The tracker section renders
 * Android's logged-out state (iOS tracker services arrive with Phase 32).
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(state.carriers) { stats ->
            Column {
                ExtensionHeader(stringResource(carrierLabel(stats.carrier)))
                OverviewSection(stats)
                TitlesSection(stats)
                ItemsSection(stats)
                TrackerSection()
            }
        }
    }
}

@Composable
private fun LazyItemScope.OverviewSection(stats: StatsScreenModel.CarrierStats) {
    val none = stringResource(MR.strings.none)
    val duration = stats.totalDurationMs
        .toDuration(DurationUnit.MILLISECONDS)
        .toDurationString(fallback = none)
    SectionCard(MR.strings.label_overview_section) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            StatsOverviewItem(
                title = stats.libraryCount.toString(),
                subtitle = stringResource(MR.strings.in_library),
                icon = Icons.Outlined.CollectionsBookmark,
            )
            StatsOverviewItem(
                title = duration,
                subtitle = stringResource(
                    when (stats.carrier) {
                        BrowseCarrier.SHOWS -> AYMR.strings.label_watched_duration
                        else -> MR.strings.label_read_duration
                    },
                ),
                icon = Icons.Outlined.Schedule,
            )
            StatsOverviewItem(
                title = stats.completedCount.toString(),
                subtitle = stringResource(MR.strings.label_completed_titles),
                icon = Icons.Outlined.LocalLibrary,
            )
        }
    }
}

@Composable
private fun LazyItemScope.TitlesSection(stats: StatsScreenModel.CarrierStats) {
    SectionCard(MR.strings.label_titles_section) {
        Row {
            StatsItem(
                stats.globalUpdateCount.toString(),
                stringResource(MR.strings.label_titles_in_global_update),
            )
            StatsItem(
                stats.startedCount.toString(),
                stringResource(MR.strings.label_started),
            )
            StatsItem(
                stats.localCount.toString(),
                stringResource(MR.strings.label_local),
            )
        }
    }
}

@Composable
private fun LazyItemScope.ItemsSection(stats: StatsScreenModel.CarrierStats) {
    SectionCard(
        when (stats.carrier) {
            BrowseCarrier.SHOWS -> AYMR.strings.episodes
            else -> MR.strings.chapters
        },
    ) {
        Row {
            StatsItem(
                stats.totalItemCount.toString(),
                stringResource(MR.strings.label_total_chapters),
            )
            StatsItem(
                stats.readItemCount.toString(),
                stringResource(
                    when (stats.carrier) {
                        BrowseCarrier.SHOWS -> AYMR.strings.label_watched_episodes
                        else -> MR.strings.label_read_chapters
                    },
                ),
            )
            StatsItem(
                stats.downloadCount.toString(),
                stringResource(MR.strings.label_downloaded),
            )
        }
    }
}

@Composable
private fun LazyItemScope.TrackerSection() {
    val notApplicable = stringResource(MR.strings.not_applicable)
    SectionCard(MR.strings.label_tracker_section) {
        Row {
            StatsItem("0", stringResource(MR.strings.label_tracked_titles))
            StatsItem(notApplicable, stringResource(MR.strings.label_mean_score))
            StatsItem("0", stringResource(MR.strings.label_used))
        }
    }
}

@Composable
private fun RowScope.StatsOverviewItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
) {
    BaseStatsItem(
        title = title,
        titleStyle = MaterialTheme.typography.titleLarge,
        subtitle = subtitle,
        subtitleStyle = MaterialTheme.typography.bodyMedium,
        icon = icon,
    )
}

@Composable
private fun RowScope.StatsItem(
    title: String,
    subtitle: String,
) {
    BaseStatsItem(
        title = title,
        titleStyle = MaterialTheme.typography.bodyMedium,
        subtitle = subtitle,
        subtitleStyle = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun RowScope.BaseStatsItem(
    title: String,
    titleStyle: TextStyle,
    subtitle: String,
    subtitleStyle: TextStyle,
    icon: ImageVector? = null,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = titleStyle.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = subtitle,
            style = subtitleStyle.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
            ),
            textAlign = TextAlign.Center,
        )
        if (icon != null) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun carrierLabel(carrier: BrowseCarrier): StringResource = when (carrier) {
    BrowseCarrier.SHOWS -> AYMR.strings.carrier_shows
    BrowseCarrier.MANGA -> AYMR.strings.carrier_manga
    BrowseCarrier.BOOKS -> AYMR.strings.carrier_books
}

/** Android's `Duration.toDurationString` — same unit rules ("2d 3h",
 *  minutes only when days or hours are zero, seconds only under an hour). */
@Composable
private fun Duration.toDurationString(fallback: String): String {
    val days = inWholeDays
    val hours = (this - days.days).inWholeHours
    val minutes = (this - days.days - hours.hours).inWholeMinutes
    val seconds = (this - days.days - hours.hours - minutes.minutes).inWholeSeconds
    return buildList(4) {
        if (days != 0L) add(stringResource(MR.strings.day_short, days))
        if (hours != 0L) add(stringResource(MR.strings.hour_short, hours))
        if (minutes != 0L && (days == 0L || hours == 0L)) {
            add(stringResource(MR.strings.minute_short, minutes))
        }
        if (seconds != 0L && days == 0L && hours == 0L) {
            add(stringResource(MR.strings.seconds_short, seconds))
        }
    }.joinToString(" ").ifBlank { fallback }
}
