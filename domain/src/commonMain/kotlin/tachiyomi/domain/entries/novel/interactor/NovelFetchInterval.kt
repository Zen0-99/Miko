package tachiyomi.domain.entries.novel.interactor

import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.items.chapter.model.NovelChapter
import kotlin.time.Instant

class NovelFetchInterval {

    /**
     * Calculate the average interval (in days) between chapter releases.
     *
     * Returns null when there isn't enough date data to compute a meaningful
     * interval (fewer than 3 chapters with valid dates).
     */
    fun calculateInterval(
        chapters: List<NovelChapter>,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): Int? {
        val chapterWindow = if (chapters.size <= 8) 3 else 10

        val uploadDates = chapters.asSequence()
            .filter { it.dateUpload > 0L }
            .sortedByDescending { it.dateUpload }
            .map {
                Instant.fromEpochMilliseconds(it.dateUpload).toLocalDateTime(zone).date
            }
            .distinct()
            .take(chapterWindow)
            .toList()

        val fetchDates = chapters.asSequence()
            .sortedByDescending { it.dateFetch }
            .map {
                Instant.fromEpochMilliseconds(it.dateFetch).toLocalDateTime(zone).date
            }
            .distinct()
            .take(chapterWindow)
            .toList()

        val interval = when {
            uploadDates.size >= 3 -> {
                val delta = uploadDates.last().daysUntil(uploadDates.first())
                val period = uploadDates.indexOf(uploadDates.last())
                delta.floorDiv(period)
            }
            fetchDates.size >= 3 -> {
                val delta = fetchDates.last().daysUntil(fetchDates.first())
                val period = fetchDates.indexOf(fetchDates.last())
                delta.floorDiv(period)
            }
            else -> return null
        }

        return interval.coerceIn(1, MAX_INTERVAL)
    }

    companion object {
        const val MAX_INTERVAL = 28
    }
}
