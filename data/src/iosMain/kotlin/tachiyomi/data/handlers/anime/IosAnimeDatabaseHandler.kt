package tachiyomi.data.handlers.anime

import androidx.paging.PagingSource
import app.cash.sqldelight.Query
import kotlinx.coroutines.CoroutineDispatcher
import tachiyomi.core.common.util.lang.ioDispatcher
import tachiyomi.data.handlers.IosDatabaseHandler
import tachiyomi.mi.data.AnimeDatabase

class IosAnimeDatabaseHandler(
    db: AnimeDatabase,
    queryDispatcher: CoroutineDispatcher = ioDispatcher,
) : IosDatabaseHandler<AnimeDatabase>(db, queryDispatcher), AnimeDatabaseHandler {

    override fun <T : Any> subscribeToPagingSource(
        countQuery: AnimeDatabase.() -> Query<Long>,
        queryProvider: AnimeDatabase.(Long, Long) -> Query<T>,
    ): PagingSource<Long, T> {
        return QueryPagingAnimeSource(
            handler = this,
            countQuery = countQuery,
            queryProvider = { limit, offset ->
                queryProvider.invoke(db, limit, offset)
            },
        )
    }
}
