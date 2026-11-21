package ar.com.ingenieriaalimentacion.data.activity.local

import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.db.CachedActivityItem
import ar.com.ingenieriaalimentacion.data.db.CachedActivityItemQueries
import ar.com.ingenieriaalimentacion.data.db.observeForOwner
import ar.com.ingenieriaalimentacion.data.db.queryForOwner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * SQLDelight-backed [ActivityLocalStore]. Caps each type's rows independently on every
 * [upsertPage] so the table can't grow unbounded across a multi-year install.
 */
class DefaultActivityLocalStore(
    private val queries: CachedActivityItemQueries,
    private val lifecycle: SessionLifecycle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ActivityLocalStore {

    override fun observe(type: ActivityItemType): Flow<List<CachedActivityRow>> =
        observeForOwner(lifecycle, dispatcher, emptyList(), select = { queries.selectByType(type.name) }) { query ->
            query.executeAsList().map { it.toDomain() }
        }

    override suspend fun getPage(type: ActivityItemType, limit: Int, offset: Int) = lifecycle.queryForOwner(dispatcher) {
        queries.selectPage(type.name, limit.toLong(), offset.toLong()).executeAsList().map { it.toDomain() }
    }

    override suspend fun upsertPage(type: ActivityItemType, rows: List<CachedActivityRow>, cap: Int) =
        lifecycle.queryForOwner(dispatcher) {
            queries.transaction {
                rows.forEach { queries.upsert(it.id, type.name, it.occurredAtMillis, it.payloadJson) }
                queries.evictBeyondCap(type.name, cap.toLong())
            }
        }

    override suspend fun clear(): Unit = lifecycle.queryForOwner(dispatcher) { queries.deleteAll() }
}

private fun CachedActivityItem.toDomain() = CachedActivityRow(id, occurredAtMillis, payloadJson)
