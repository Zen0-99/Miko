package eu.kanade.presentation.history.shows.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import tachiyomi.domain.history.shows.model.AnimeHistoryWithRelations
import kotlin.time.Instant

internal class ShowsHistoryWithRelationsProvider : PreviewParameterProvider<AnimeHistoryWithRelations> {

    private val simple = AnimeHistoryWithRelations(
        id = 1L,
        episodeId = 2L,
        animeId = 3L,
        title = "Test Title",
        episodeNumber = 10.2,
        seenAt = Instant.fromEpochMilliseconds(1697247357L),
        coverData = tachiyomi.domain.entries.shows.model.AnimeCover(
            animeId = 3L,
            sourceId = 4L,
            isAnimeFavorite = false,
            url = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    private val historyWithoutReadAt = AnimeHistoryWithRelations(
        id = 1L,
        episodeId = 2L,
        animeId = 3L,
        title = "Test Title",
        episodeNumber = 10.2,
        seenAt = null,
        coverData = tachiyomi.domain.entries.shows.model.AnimeCover(
            animeId = 3L,
            sourceId = 4L,
            isAnimeFavorite = false,
            url = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    private val historyWithNegativeChapterNumber = AnimeHistoryWithRelations(
        id = 1L,
        episodeId = 2L,
        animeId = 3L,
        title = "Test Title",
        episodeNumber = -2.0,
        seenAt = Instant.fromEpochMilliseconds(1697247357L),
        coverData = tachiyomi.domain.entries.shows.model.AnimeCover(
            animeId = 3L,
            sourceId = 4L,
            isAnimeFavorite = false,
            url = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    override val values: Sequence<AnimeHistoryWithRelations>
        get() = sequenceOf(simple, historyWithoutReadAt, historyWithNegativeChapterNumber)
}
