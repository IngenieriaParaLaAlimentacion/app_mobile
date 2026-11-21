package ar.com.ingenieriaalimentacion.data.profile.local

import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.db.CachedEstablishment
import ar.com.ingenieriaalimentacion.data.db.CachedEstablishmentQueries
import ar.com.ingenieriaalimentacion.data.db.queryForOwner
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * SQLDelight-backed [EstablishmentLocalStore]. Writes stamp `updatedAt` via the injected [now]
 * source so tests stay deterministic; DB I/O runs off the main thread on [dispatcher].
 */
class DefaultEstablishmentLocalStore(
    private val queries: CachedEstablishmentQueries,
    private val now: () -> Long,
    private val lifecycle: SessionLifecycle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : EstablishmentLocalStore {

    override suspend fun getAll(): List<EstablishmentWithStatus> = lifecycle.queryForOwner(dispatcher) {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun replaceAll(items: List<EstablishmentWithStatus>): Unit =
        lifecycle.queryForOwner(dispatcher) { queries.transaction { replace(items) } }

    override suspend fun update(transform: (List<EstablishmentWithStatus>) -> List<EstablishmentWithStatus>) =
        lifecycle.queryForOwner(dispatcher) {
            queries.transaction {
                replace(transform(queries.selectAll().executeAsList().map { it.toDomain() }))
            }
        }

    private fun replace(items: List<EstablishmentWithStatus>) {
        val stamp = now()
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

    override suspend fun clear(): Unit = lifecycle.queryForOwner(dispatcher) {
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
