package tachiyomi.data.handlers

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.lang.ioDispatcher

/**
 * SQLDelight handler for iOS backed by a single-driver model.
 *
 * Queries dispatch to [queryDispatcher]. Explicit transactions are serialized
 * through [transactionMutex] and run inside a real driver transaction via
 * [runBlocking] on the calling worker — the Native driver serializes all
 * statements through one connection, so blocking during a transaction is safe
 * as long as transactions cannot interleave.
 */
abstract class IosDatabaseHandler<DB : Transacter>(
    val db: DB,
    private val queryDispatcher: CoroutineDispatcher = ioDispatcher,
) {
    private val transactionMutex = Mutex()

    suspend fun <T> await(inTransaction: Boolean, block: suspend DB.() -> T): T {
        return dispatch(inTransaction, block)
    }

    suspend fun <T : Any> awaitList(
        inTransaction: Boolean,
        block: suspend DB.() -> Query<T>,
    ): List<T> {
        return dispatch(inTransaction) { block(db).executeAsList() }
    }

    suspend fun <T : Any> awaitOne(
        inTransaction: Boolean,
        block: suspend DB.() -> Query<T>,
    ): T {
        return dispatch(inTransaction) { block(db).executeAsOne() }
    }

    suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean,
        block: suspend DB.() -> ExecutableQuery<T>,
    ): T {
        return dispatch(inTransaction) { block(db).executeAsOne() }
    }

    suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean,
        block: suspend DB.() -> Query<T>,
    ): T? {
        return dispatch(inTransaction) { block(db).executeAsOneOrNull() }
    }

    suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean,
        block: suspend DB.() -> ExecutableQuery<T>,
    ): T? {
        return dispatch(inTransaction) { block(db).executeAsOneOrNull() }
    }

    fun <T : Any> subscribeToList(block: DB.() -> Query<T>): Flow<List<T>> {
        return block(db).asFlow().mapToList(queryDispatcher)
    }

    fun <T : Any> subscribeToOne(block: DB.() -> Query<T>): Flow<T> {
        return block(db).asFlow().mapToOne(queryDispatcher)
    }

    fun <T : Any> subscribeToOneOrNull(block: DB.() -> Query<T>): Flow<T?> {
        return block(db).asFlow().mapToOneOrNull(queryDispatcher)
    }

    private suspend fun <T> dispatch(inTransaction: Boolean, block: suspend DB.() -> T): T {
        return withContext(queryDispatcher) {
            if (!inTransaction) {
                return@withContext block(db)
            }
            transactionMutex.withLock {
                db.transactionWithResult {
                    runBlocking { block(db) }
                }
            }
        }
    }
}
