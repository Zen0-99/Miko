package tachiyomi.domain.entries.shows.interactor

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
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.entries.shows.model.AnimeUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.Instant

class AnimeFetchInterval(
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
) {

    suspend fun toAnimeUpdate(
        anime: Anime,
        dateTime: Instant,
        timeZone: TimeZone,
        window: Pair<Long, Long>,
    ): AnimeUpdate {
        val interval = anime.fetchInterval.takeIf { it < 0 } ?: calculateInterval(
            episodes = getEpisodesByAnimeId.await(anime.id),
            zone = timeZone,
        )
        val currentWindow = if (window.first == 0L && window.second == 0L) {
            getWindow(Clock.System.now(), timeZone)
        } else {
            window
        }
        val nextUpdate = calculateNextUpdate(anime, interval, dateTime, timeZone, currentWindow)

        return AnimeUpdate(id = anime.id, nextUpdate = nextUpdate, fetchInterval = interval)
    }

    fun getWindow(dateTime: Instant, timeZone: TimeZone): Pair<Long, Long> {
        val today = dateTime.toLocalDateTime(timeZone).date.atStartOfDayIn(timeZone)
        val lowerBound = today.minus(GRACE_PERIOD.toInt(), DateTimeUnit.DAY, timeZone)
        val upperBound = today.plus(GRACE_PERIOD.toInt(), DateTimeUnit.DAY, timeZone)
        return Pair(lowerBound.epochSeconds * 1000, upperBound.epochSeconds * 1000 - 1)
    }

    fun calculateInterval(episodes: List<Episode>, zone: TimeZone): Int {
        val episodeWindow = if (episodes.size <= 8) 3 else 10

        val uploadDates = episodes.asSequence()
            .filter { it.dateUpload > 0L }
            .sortedByDescending { it.dateUpload }
            .map {
                Instant.fromEpochMilliseconds(it.dateUpload).toLocalDateTime(zone).date
            }
            .distinct()
            .take(episodeWindow)
            .toList()

        val fetchDates = episodes.asSequence()
            .sortedByDescending { it.dateFetch }
            .map {
                Instant.fromEpochMilliseconds(it.dateFetch).toLocalDateTime(zone).date
            }
            .distinct()
            .take(episodeWindow)
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
        anime: Anime,
        interval: Int,
        dateTime: Instant,
        timeZone: TimeZone,
        window: Pair<Long, Long>,
    ): Long {
        if (anime.nextUpdate in window.first.rangeTo(window.second + 1)) {
            return anime.nextUpdate
        }

        val latestDate = (
            if (anime.lastUpdate > 0) {
                Instant.fromEpochMilliseconds(anime.lastUpdate)
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
