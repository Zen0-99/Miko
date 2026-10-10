package tachiyomi.domain.entries.shows.repository

import kotlinx.coroutines.flow.Flow
import miko.domain.shows.SeasonAnime
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.entries.shows.model.AnimeUpdate
import tachiyomi.domain.library.shows.LibraryAnime
import tachiyomi.domain.source.anime.model.DeletableAnime

interface AnimeRepository {

    suspend fun getAnimeById(id: Long): Anime

    suspend fun getAnimeByIdAsFlow(id: Long): Flow<Anime>

    suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): Anime?

    fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Anime?>

    suspend fun getAnimeFavorites(): List<Anime>

    suspend fun getWatchedAnimeNotInLibrary(): List<Anime>

    suspend fun getLibraryAnime(): List<LibraryAnime>

    fun getLibraryAnimeAsFlow(): Flow<List<LibraryAnime>>

    fun getAnimeFavoritesBySourceId(sourceId: Long): Flow<List<Anime>>

    suspend fun getDuplicateLibraryAnime(id: Long, title: String): List<Anime>

    suspend fun getUpcomingAnime(statuses: Set<Long>): Flow<List<Anime>>

    suspend fun resetAnimeViewerFlags(): Boolean

    suspend fun setAnimeCollections(animeId: Long, collectionIds: List<Long>)

    suspend fun insertAnime(anime: Anime): Long?

    suspend fun updateAnime(update: AnimeUpdate): Boolean

    suspend fun updateAllAnime(animeUpdates: List<AnimeUpdate>): Boolean

    /**
     * Deletes extension-source anime rows that were materialized as a side
     * effect of search/browse and never gained episodes, history, tracker
     * sync, or categories. Cinemeta/local (source 0) rows and ids in
     * [protectedIds] (user-picked source mappings) are never deleted.
     */
    suspend fun deleteJunkAnime(protectedIds: Collection<Long>): Boolean

    suspend fun getAnimeSeasonsById(parentId: Long): List<SeasonAnime>

    fun getAnimeSeasonsByIdAsFlow(parentId: Long): Flow<List<SeasonAnime>>

    suspend fun removeParentIdByIds(animeIds: List<Long>)

    fun getDeletableParentAnime(): Flow<List<DeletableAnime>>

    suspend fun getChildrenByParentId(parentId: Long): List<Anime>
}
