package ar.com.ingenieriaalimentacion.data.profile.local

import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.db.CurrentEstablishmentQueries
import ar.com.ingenieriaalimentacion.data.db.queryForOwner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class DefaultCurrentEstablishmentLocalStore(
    private val queries: CurrentEstablishmentQueries,
    private val now: () -> Long,
    private val lifecycle: SessionLifecycle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CurrentEstablishmentLocalStore {

    override suspend fun get(): String? = lifecycle.queryForOwner(dispatcher) {
        queries.select().executeAsOneOrNull()?.establishmentId
    }

    override suspend fun set(establishmentId: String?): Unit = lifecycle.queryForOwner(dispatcher) {
        queries.set(establishmentId, now())
    }

    override suspend fun clearIfSelected(establishmentId: String): Unit = lifecycle.queryForOwner(dispatcher) {
        queries.clearIfSelected(updatedAt = now(), establishmentId = establishmentId)
    }
}
