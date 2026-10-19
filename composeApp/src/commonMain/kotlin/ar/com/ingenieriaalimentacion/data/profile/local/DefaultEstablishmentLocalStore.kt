package ar.com.ingenieriaalimentacion.data.profile.local

import ar.com.ingenieriaalimentacion.data.db.CachedEstablishment
import ar.com.ingenieriaalimentacion.data.db.CachedEstablishmentQueries
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [EstablishmentLocalStore]. Writes stamp `updatedAt` via the injected [now]
 * source so tests stay deterministic; DB I/O runs off the main thread on [dispatcher].
 */
class DefaultEstablishmentLocalStore(
    private val queries: CachedEstablishmentQueries,
    private val now: () -> Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : EstablishmentLocalStore {

    override suspend fun getAll(): List<EstablishmentWithStatus> = withContext(dispatcher) {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun replaceAll(items: List<EstablishmentWithStatus>): Unit =
        withContext(dispatcher) {
            val stamp = now()
            queries.transaction {
                queries.deleteAll()
                items.forEach { item ->
                    queries.insert(
                        id = item.id,
                        name = item.name,
                        formattedAddress = item.formattedAddress,
                        latitude = item.latitude,
                        longitude = item.longitude,
                        placeId = item.placeId,
                        city = item.city,
                        province = item.province,
                        status = item.status.name,
                        characterizationStatus = item.characterizationStatus?.name,
                        updatedAt = stamp,
                    )
                }
            }
        }

    override suspend fun clear(): Unit = withContext(dispatcher) {
        queries.deleteAll()
    }
}

private fun CachedEstablishment.toDomain(): EstablishmentWithStatus = EstablishmentWithStatus(
    id = id,
    name = name,
    formattedAddress = formattedAddress,
    latitude = latitude,
    longitude = longitude,
    placeId = placeId,
    city = city,
    province = province,
    status = status.toEstablishmentStatus(),
    characterizationStatus = CharacterizationStatus.entries.firstOrNull { it.name == characterizationStatus },
)

private fun String.toEstablishmentStatus(): EstablishmentStatus =
    EstablishmentStatus.entries.firstOrNull { it.name == this } ?: EstablishmentStatus.Unknown
