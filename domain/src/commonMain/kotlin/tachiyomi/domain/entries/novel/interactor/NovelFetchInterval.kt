package tachiyomi.domain.entries.novel.interactor

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
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.items.chapter.model.NovelChapter
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.Instant

class NovelFetchInterval {

    /**
     * Expected next-fetch update for [novel], given its local [chapters] —
     * mirrors [tachiyomi.domain.entries.manga.interactor.MangaFetchInterval.toMangaUpdate]
     * with the chapters supplied by the caller (novels have no chapter-fetch
     * interactor dependency here).
     */
    fun toNovelUpdate(
        novel: Novel,
        chapters: List<NovelChapter>,
        dateTime: Instant,
        timeZone: TimeZone,
        window: Pair<Long, Long>,
    ): NovelUpdate {
        val interval = novel.fetchInterval.takeIf { it < 0 }
            ?: calculateInterval(chapters, timeZone)
            ?: DEFAULT_INTERVAL
        val currentWindow = if (window.first == 0L && window.second == 0L) {
            getWindow(Clock.System.now(), timeZone)
        } else {
            window
        }
        val nextUpdate = calculateNextUpdate(novel, interval, dateTime, timeZone, currentWindow)
        return NovelUpdate(id = novel.id, nextUpdate = nextUpdate, fetchInterval = interval)
    }

    fun getWindow(dateTime: Instant, timeZone: TimeZone): Pair<Long, Long> {
        val today = dateTime.toLocalDateTime(timeZone).date.atStartOfDayIn(timeZone)
        val lowerBound = today.minus(GRACE_PERIOD.toInt(), DateTimeUnit.DAY, timeZone)
        val upperBound = today.plus(GRACE_PERIOD.toInt(), DateTimeUnit.DAY, timeZone)
        return Pair(lowerBound.epochSeconds * 1000, upperBound.epochSeconds * 1000 - 1)
    }

    private fun calculateNextUpdate(
        novel: Novel,
        interval: Int,
        dateTime: Instant,
        timeZone: TimeZone,
        window: Pair<Long, Long>,
    ): Long {
        if (novel.nextUpdate in window.first.rangeTo(window.second + 1)) {
            return novel.nextUpdate
        }

        val latestDate = (
            if (novel.lastUpdate > 0) {
                Instant.fromEpochMilliseconds(novel.lastUpdate)
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

        private const val GRACE_PERIOD = 1L
        private const val DEFAULT_INTERVAL = 7
    }
}
