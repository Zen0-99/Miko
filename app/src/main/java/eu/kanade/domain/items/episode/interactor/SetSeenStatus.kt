package eu.kanade.domain.items.episode.interactor

import eu.kanade.domain.download.anime.interactor.DeleteEpisodeDownload
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import java.util.Date

class SetSeenStatus(
    private val downloadPreferences: DownloadPreferences,
    private val deleteDownload: DeleteEpisodeDownload,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val upsertHistory: UpsertAnimeHistory? = null,
    private val achievementEventBus: AchievementEventBus? = null,
) {

    private val mapper = { episode: Episode, read: Boolean ->
        EpisodeUpdate(
            seen = read,
            lastSecondSeen = if (!read) 0 else null,
            id = episode.id,
        )
    }

    suspend fun await(seen: Boolean, vararg episodes: Episode): Result = withNonCancellableContext {
        val episodesToUpdate = episodes.filter {
            when (seen) {
                true -> !it.seen
                false -> it.seen || it.lastSecondSeen > 0
            }
        }
        if (episodesToUpdate.isEmpty()) {
            return@withNonCancellableContext Result.NoEpisodes
        }

        try {
            episodeRepository.updateAllEpisodes(
                episodesToUpdate.map { mapper(it, seen) },
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        // Emit achievement events for episodes marked as watched
        if (seen) {
            // Record history entries for manually-marked-as-watched episodes
            if (upsertHistory != null) {
                val now = Date()
                episodesToUpdate.forEach { episode ->
                    upsertHistory.await(
                        AnimeHistoryUpdate(
                            episodeId = episode.id,
                            seenAt = now,
                        ),
                    )
                }
            }

            episodesToUpdate.forEach { episode ->
                achievementEventBus?.tryEmit(
                    AchievementEvent.EpisodeWatched(
                        animeId = episode.animeId,
                        episodeNumber = episode.episodeNumber.toInt(),
                    ),
                )
            }

            // Check for anime completion (all episodes watched)
            episodesToUpdate.map { it.animeId }.distinct().forEach { animeId ->
                val allEpisodes = episodeRepository.getEpisodeByAnimeId(animeId)
                if (allEpisodes.isNotEmpty() && allEpisodes.all { it.seen }) {
                    achievementEventBus?.tryEmit(
                        AchievementEvent.AnimeCompleted(animeId = animeId),
                    )
                }
            }
        }

        if (seen && downloadPreferences.removeAfterMarkedAsRead().get()) {
            episodesToUpdate
                .groupBy { it.animeId }
                .forEach { (animeId, episodes) ->
                    deleteDownload.awaitAll(
                        anime = animeRepository.getAnimeById(animeId),
                        episodes = episodes.toTypedArray(),
                    )
                }
        }

        Result.Success
    }

    suspend fun await(animeId: Long, seen: Boolean): Result = withNonCancellableContext {
        await(
            seen = seen,
            episodes = episodeRepository
                .getEpisodeByAnimeId(animeId)
                .toTypedArray(),
        )
    }

    suspend fun await(anime: Anime, seen: Boolean) =
        await(anime.id, seen)

    sealed interface Result {
        data object Success : Result
        data object NoEpisodes : Result
        data class InternalError(val error: Throwable) : Result
    }
}
