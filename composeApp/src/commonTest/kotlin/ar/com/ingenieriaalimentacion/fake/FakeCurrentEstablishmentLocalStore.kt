package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.data.profile.local.CurrentEstablishmentLocalStore

/** In-memory [CurrentEstablishmentLocalStore] for repository tests. */
class FakeCurrentEstablishmentLocalStore(
    initial: String? = null,
) : CurrentEstablishmentLocalStore {

    var value: String? = initial
        private set

    override suspend fun get(): String? = value

    override suspend fun set(establishmentId: String?) {
        value = establishmentId
    }
}
