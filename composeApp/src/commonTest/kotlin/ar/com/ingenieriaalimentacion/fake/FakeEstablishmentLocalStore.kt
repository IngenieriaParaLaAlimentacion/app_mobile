package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.data.profile.local.EstablishmentLocalStore
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus

/** In-memory [EstablishmentLocalStore] for repository tests. */
class FakeEstablishmentLocalStore(
    initial: List<EstablishmentWithStatus> = emptyList(),
) : EstablishmentLocalStore {

    var rows: List<EstablishmentWithStatus> = initial
        private set

    var replaceCount: Int = 0
        private set
    var clearCount: Int = 0
        private set

    override suspend fun getAll(): List<EstablishmentWithStatus> = rows

    override suspend fun replaceAll(items: List<EstablishmentWithStatus>) {
        rows = items
        replaceCount++
    }

    override suspend fun clear() {
        rows = emptyList()
        clearCount++
    }
}
