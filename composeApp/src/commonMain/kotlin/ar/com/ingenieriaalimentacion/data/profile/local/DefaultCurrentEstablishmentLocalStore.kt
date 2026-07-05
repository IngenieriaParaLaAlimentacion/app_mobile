package ar.com.ingenieriaalimentacion.data.profile.local

import ar.com.ingenieriaalimentacion.data.db.CurrentEstablishmentQueries
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultCurrentEstablishmentLocalStore(
    private val queries: CurrentEstablishmentQueries,
    private val now: () -> Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CurrentEstablishmentLocalStore {

    override suspend fun get(): String? = withContext(dispatcher) {
        queries.select().executeAsOneOrNull()?.establishmentId
    }

    override suspend fun set(establishmentId: String?): Unit = withContext(dispatcher) {
        queries.set(establishmentId, now())
    }
}
