package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.data.activity.local.ActivityItemType
import ar.com.ingenieriaalimentacion.data.activity.local.ActivityLocalStore
import ar.com.ingenieriaalimentacion.data.activity.local.CachedActivityRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [ActivityLocalStore] mirroring the SQL semantics of [DefaultActivityLocalStore]. */
class FakeActivityLocalStore : ActivityLocalStore {

    private val rows = mutableMapOf<ActivityItemType, MutableStateFlow<List<CachedActivityRow>>>()

    private fun flowFor(type: ActivityItemType) = rows.getOrPut(type) { MutableStateFlow(emptyList()) }

    fun seed(type: ActivityItemType, items: List<CachedActivityRow>) {
        flowFor(type).value = items
    }

    override fun observe(type: ActivityItemType): Flow<List<CachedActivityRow>> = flowFor(type)

    override suspend fun getPage(type: ActivityItemType, limit: Int, offset: Int): List<CachedActivityRow> =
        flowFor(type).value.sortedByDescending { it.occurredAtMillis }.drop(offset).take(limit)

    override suspend fun upsertPage(type: ActivityItemType, rows: List<CachedActivityRow>, cap: Int) {
        val current = flowFor(type).value.associateBy { it.id }.toMutableMap()
        rows.forEach { current[it.id] = it }
        flowFor(type).value = current.values.sortedByDescending { it.occurredAtMillis }.take(cap)
    }

    override suspend fun clear() {
        rows.keys.forEach { flowFor(it).value = emptyList() }
    }
}
