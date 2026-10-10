package tachiyomi.data.handlers.manga

import androidx.paging.PagingSource
import app.cash.sqldelight.Query
import kotlinx.coroutines.CoroutineDispatcher
import tachiyomi.core.common.util.lang.ioDispatcher
import tachiyomi.data.Database
import tachiyomi.data.handlers.IosDatabaseHandler

class IosMangaDatabaseHandler(
    db: Database,
    queryDispatcher: CoroutineDispatcher = ioDispatcher,
) : IosDatabaseHandler<Database>(db, queryDispatcher), MangaDatabaseHandler {

    override fun <T : Any> subscribeToPagingSource(
        countQuery: Database.() -> Query<Long>,
        queryProvider: Database.(Long, Long) -> Query<T>,
    ): PagingSource<Long, T> {
        return QueryPagingMangaSource(
            handler = this,
            countQuery = countQuery,
            queryProvider = { limit, offset ->
                queryProvider.invoke(db, limit, offset)
            },
        )
    }
}
