package ar.com.ingenieriaalimentacion.domain.repository

/** Removes reusable caches and, on explicit sign-out, the departing account's queued work. */
interface DatabaseCleaner {
    suspend fun clearCaches()
    suspend fun clearForSignOut(ownerId: String)
}

class DatabaseCleanupException(cause: Throwable) : Exception("Unable to clear local data", cause)
