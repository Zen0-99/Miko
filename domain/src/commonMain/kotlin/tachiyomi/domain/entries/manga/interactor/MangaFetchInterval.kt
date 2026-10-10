package tachiyomi.domain.entries.manga.interactor

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.offsetIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.Instant

class MangaFetchInterval(
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    suspend fun toMangaUpdate(
        manga: Manga,
        dateTime: Instant,
        timeZone: TimeZone,
        window: Pair<Long, Long>,
    ): MangaUpdate {
        val interval = manga.fetchInterval.takeIf { it < 0 } ?: calculateInterval(
            chapters = getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true),
            zone = timeZone,
        )
        val currentWindow = if (window.first == 0L && window.second == 0L) {
            getWindow(Clock.System.now(), timeZone)
        } else {
            window
        }
        val nextUpdate = calculateNextUpdate(manga, interval, dateTime, timeZone, currentWindow)

        return MangaUpdate(id = manga.id, nextUpdate = nextUpdate, fetchInterval = interval)
    }

    fun getWindow(dateTime: Instant, timeZone: TimeZone): Pair<Long, Long> {
        val today = dateTime.toLocalDateTime(timeZone).date.atStartOfDayIn(timeZone)
        val lowerBound = today.minus(GRACE_PERIOD.toInt(), DateTimeUnit.DAY, timeZone)
        val upperBound = today.plus(GRACE_PERIOD.toInt(), DateTimeUnit.DAY, timeZone)
        return Pair(lowerBound.epochSeconds * 1000, upperBound.epochSeconds * 1000 - 1)
    }

    fun calculateInterval(chapters: List<Chapter>, zone: TimeZone): Int {
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
            // Enough upload date from source
            uploadDates.size >= 3 -> {
                val uploadDelta = uploadDates.last().daysUntil(uploadDates.first())
                val uploadPeriod = uploadDates.indexOf(uploadDates.last())
                uploadDelta.floorDiv(uploadPeriod)
            }
            // Enough fetch date from client
            fetchDates.size >= 3 -> {
                val fetchDelta = fetchDates.last().daysUntil(fetchDates.first())
                val uploadPeriod = fetchDates.indexOf(fetchDates.last())
                fetchDelta.floorDiv(uploadPeriod)
            }
            // Default to 7 days
            else -> 7
        }

        return interval.coerceIn(1, MAX_INTERVAL)
    }

    private fun calculateNextUpdate(
        manga: Manga,
        interval: Int,
        dateTime: Instant,
        timeZone: TimeZone,
        window: Pair<Long, Long>,
    ): Long {
        if (manga.nextUpdate in window.first.rangeTo(window.second + 1)) {
            return manga.nextUpdate
        }

        val latestDate = (
            if (manga.lastUpdate > 0) {
                Instant.fromEpochMilliseconds(manga.lastUpdate)
            } else {
                Clock.System.now()
            }
            )
            .toLocalDateTime(timeZone)
            .date
        val timeSinceLatest = latestDate.daysUntil(dateTime.toLocalDateTime(timeZone).date)
        val cycle = timeSinceLatest.floorDiv(
            interval.absoluteValue.takeIf { interval < 0 }
                ?: increaseInterval(interval, timeSinceLatest, increaseWhenOver = 10),
        )
        return latestDate
            .plus((cycle + 1) * interval.absoluteValue, DateTimeUnit.DAY)
            .atTime(0, 0)
            .toInstant(dateTime.offsetIn(timeZone))
            .epochSeconds * 1000
    }

    private fun increaseInterval(delta: Int, timeSinceLatest: Int, increaseWhenOver: Int): Int {
        if (delta >= MAX_INTERVAL) return MAX_INTERVAL

        // double delta again if missed more than 9 check in new delta
        val cycle = timeSinceLatest.floorDiv(delta) + 1
        return if (cycle > increaseWhenOver) {
            increaseInterval(delta * 2, timeSinceLatest, increaseWhenOver)
        } else {
            delta
        }
    }

    companion object {
        const val MAX_INTERVAL = 28

        private const val GRACE_PERIOD = 1L
    }
}
