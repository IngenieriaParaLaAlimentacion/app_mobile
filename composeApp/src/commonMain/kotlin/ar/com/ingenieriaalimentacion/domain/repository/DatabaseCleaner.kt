package ar.com.ingenieriaalimentacion.domain.repository

/** Clears all locally persisted data. Implemented in the data layer over the SQLDelight database. */
interface DatabaseCleaner {
    suspend fun clearAll()
}
