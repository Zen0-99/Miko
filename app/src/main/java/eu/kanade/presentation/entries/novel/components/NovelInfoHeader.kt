package eu.kanade.presentation.entries.novel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.novelsource.model.SNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun NovelInfoBox(
    appBarPadding: Dp,
    novel: Novel,
    sourceName: String,
    accentColor: Color?,
    onCoverClick: () -> Unit,
    doSearch: (query: String, global: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        val backdropGradientColors = listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.background,
        )

        // Layer 1: Solid color background derived from cover accent (Miko-style)
        // This creates the vibrant tint behind the blurred cover
        if (accentColor != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(accentColor.copy(alpha = 0.3f)),
            )
        }

        // Layer 2: Blurred cover art backdrop (15% alpha, matching Miko)
        coil3.compose.AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(novel.asNovelCover())
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .blur(4.dp)
                .alpha(0.15f),
        )

        // Layer 3: Gradient overlay (transparent to background) for text readability
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    drawRect(
                        brush = Brush.verticalGradient(colors = backdropGradientColors),
                    )
                },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = appBarPadding + 16.dp,
                    bottom = 16.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ItemCover.Book(
                modifier = Modifier.sizeIn(maxWidth = 90.dp),
                data = ImageRequest.Builder(LocalContext.current)
                    .data(novel.asNovelCover())
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(MR.strings.manga_cover),
                onClick = onCoverClick,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = novel.author?.takeIf { it.isNotBlank() }
                        ?: stringResource(MR.strings.unknown_author),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA),
                )
                Text(
                    text = statusText(novel.status),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA),
                )
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA),
                )
            }
        }
    }
}

@Composable
private fun statusText(status: Long): String {
    return stringResource(
        when (status) {
            SNovel.ONGOING.toLong() -> MR.strings.ongoing
            SNovel.COMPLETED.toLong() -> MR.strings.completed
            SNovel.LICENSED.toLong() -> MR.strings.licensed
            SNovel.PUBLISHING_FINISHED.toLong() -> MR.strings.publishing_finished
            SNovel.CANCELLED.toLong() -> MR.strings.cancelled
            SNovel.ON_HIATUS.toLong() -> MR.strings.on_hiatus
            else -> MR.strings.unknown
        },
    )
}
