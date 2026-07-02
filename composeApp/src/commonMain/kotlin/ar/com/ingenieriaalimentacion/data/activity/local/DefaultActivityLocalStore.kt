package ar.com.ingenieriaalimentacion.data.activity.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import ar.com.ingenieriaalimentacion.data.db.CachedActivityItem
import ar.com.ingenieriaalimentacion.data.db.CachedActivityItemQueries
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [ActivityLocalStore]. Caps each type's rows independently on every
 * [upsertPage] so the table can't grow unbounded across a multi-year install.
 */
class DefaultActivityLocalStore(
    private val queries: CachedActivityItemQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ActivityLocalStore {

    override fun observe(type: ActivityItemType): Flow<List<CachedActivityRow>> =
        queries.selectByType(type.name).asFlow().mapToList(dispatcher).map { it.map { row -> row.toDomain() } }

    override suspend fun getPage(type: ActivityItemType, limit: Int, offset: Int) = withContext(dispatcher) {
        queries.selectPage(type.name, limit.toLong(), offset.toLong()).executeAsList().map { it.toDomain() }
    }

    override suspend fun upsertPage(type: ActivityItemType, rows: List<CachedActivityRow>, cap: Int) =
        withContext(dispatcher) {
            queries.transaction {
                rows.forEach { queries.upsert(it.id, type.name, it.occurredAtMillis, it.payloadJson) }
                queries.evictBeyondCap(type.name, cap.toLong())
            }
        }

    override suspend fun clear(): Unit = withContext(dispatcher) { queries.deleteAll() }
}

private fun CachedActivityItem.toDomain() = CachedActivityRow(id, occurredAtMillis, payloadJson)
