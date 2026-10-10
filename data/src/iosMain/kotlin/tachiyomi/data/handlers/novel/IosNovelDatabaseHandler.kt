package tachiyomi.data.handlers.novel

import androidx.paging.PagingSource
import app.cash.sqldelight.Query
import kotlinx.coroutines.CoroutineDispatcher
import tachiyomi.core.common.util.lang.ioDispatcher
import tachiyomi.data.handlers.IosDatabaseHandler
import tachiyomi.novel.data.NovelDatabase

class IosNovelDatabaseHandler(
    db: NovelDatabase,
    queryDispatcher: CoroutineDispatcher = ioDispatcher,
) : IosDatabaseHandler<NovelDatabase>(db, queryDispatcher), NovelDatabaseHandler {

    override fun <T : Any> subscribeToPagingSource(
        countQuery: NovelDatabase.() -> Query<Long>,
        queryProvider: NovelDatabase.(Long, Long) -> Query<T>,
    ): PagingSource<Long, T> {
        return QueryPagingNovelSource(
            handler = this,
            countQuery = countQuery,
            queryProvider = { limit, offset ->
                queryProvider.invoke(db, limit, offset)
            },
        )
    }
}
