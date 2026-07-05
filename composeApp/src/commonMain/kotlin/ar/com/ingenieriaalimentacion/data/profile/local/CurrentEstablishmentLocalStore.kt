package ar.com.ingenieriaalimentacion.data.profile.local

/**
 * Local single-value store for the Home "current establishment" pick. Stores only the id; display
 * fields are resolved against the establishment list at read time (see `GetCurrentEstablishmentUseCase`).
 */
interface CurrentEstablishmentLocalStore {
    suspend fun get(): String?
    suspend fun set(establishmentId: String?)
}
