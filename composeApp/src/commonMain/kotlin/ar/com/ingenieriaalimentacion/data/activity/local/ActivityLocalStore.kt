package ar.com.ingenieriaalimentacion.data.activity.local

import kotlinx.coroutines.flow.Flow

enum class ActivityItemType { SUBJECT, MEASUREMENT }

data class CachedActivityRow(val id: String, val occurredAtMillis: Long, val payloadJson: String)

interface ActivityLocalStore {
    fun observe(type: ActivityItemType): Flow<List<CachedActivityRow>>
    suspend fun getPage(type: ActivityItemType, limit: Int, offset: Int): List<CachedActivityRow>
    suspend fun upsertPage(type: ActivityItemType, rows: List<CachedActivityRow>, cap: Int = DEFAULT_CAP)
    suspend fun clear()

    companion object { const val DEFAULT_CAP = 200 }
}
