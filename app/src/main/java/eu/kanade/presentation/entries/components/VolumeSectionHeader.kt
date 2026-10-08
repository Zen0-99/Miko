package eu.kanade.presentation.entries.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.data.mikodb.CanonicalVolumes
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Header card for a volume-scoped chapter group (Phase 21-03 / NCM-016).
 * Rendered above the chapter carousel when canonical boundaries drove
 * the grouping — the cover is the worker's per-volume MangaBaka art
 * (en preferred, then ja); a boundary map without covers renders
 * text-only.
 *
 * @param label pill label for the group ("Volume 9")
 * @param meta cover/pages for the volume, or null when the map came
 *   without gallery data (text-only header per the degradation ladder)
 * @param firstChapter lowest chapter number the map assigns to this
 *   volume within the visible list
 * @param lastChapter highest chapter number for the same
 */
@Composable
fun VolumeSectionHeader(
    label: String,
    meta: CanonicalVolumes.VolumeMeta?,
    firstChapter: Double?,
    lastChapter: Double?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (meta?.coverThumbUrl != null) {
            ItemCover.Book(
                data = meta.coverThumbUrl,
                modifier = Modifier.width(40.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                if (firstChapter != null && lastChapter != null) {
                    stringResource(
                        MR.strings.volume_group_chapter_range,
                        formatChapterNumber(firstChapter),
                        formatChapterNumber(lastChapter),
                    )
                } else {
                    null
                },
                meta?.pages?.let { stringResource(MR.strings.volume_group_pages, it) },
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
