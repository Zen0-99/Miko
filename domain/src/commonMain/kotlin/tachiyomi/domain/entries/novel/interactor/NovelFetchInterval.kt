package tachiyomi.domain.entries.novel.interactor

import tachiyomi.domain.items.chapter.model.NovelChapter
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class NovelFetchInterval {

    /**
     * Calculate the average interval (in days) between chapter releases.
     *
     * Returns null when there isn't enough date data to compute a meaningful
     * interval (fewer than 3 chapters with valid dates).
     */
    fun calculateInterval(chapters: List<NovelChapter>, zone: ZoneId = ZoneId.systemDefault()): Int? {
        val chapterWindow = if (chapters.size <= 8) 3 else 10

        val uploadDates = chapters.asSequence()
            .filter { it.dateUpload > 0L }
            .sortedByDescending { it.dateUpload }
            .map {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.dateUpload), zone)
                    .toLocalDate()
                    .atStartOfDay()
            }
            .distinct()
            .take(chapterWindow)
            .toList()

        val fetchDates = chapters.asSequence()
            .sortedByDescending { it.dateFetch }
            .map {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.dateFetch), zone)
                    .toLocalDate()
                    .atStartOfDay()
            }
            .distinct()
            .take(chapterWindow)
            .toList()

        val interval = when {
            uploadDates.size >= 3 -> {
                val delta = uploadDates.last().until(uploadDates.first(), ChronoUnit.DAYS)
                val period = uploadDates.indexOf(uploadDates.last())
                delta.floorDiv(period).toInt()
            }
            fetchDates.size >= 3 -> {
                val delta = fetchDates.last().until(fetchDates.first(), ChronoUnit.DAYS)
                val period = fetchDates.indexOf(fetchDates.last())
                delta.floorDiv(period).toInt()
            }
            else -> return null
        }

        return interval.coerceIn(1, MAX_INTERVAL)
    }

    companion object {
        const val MAX_INTERVAL = 28
    }
}
