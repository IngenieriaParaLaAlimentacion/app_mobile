package ar.com.ingenieriaalimentacion.data.profile.local

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus

/**
 * Local read-through cache for the user's establishments, backing the measurement-screen
 * establishment picker offline. The concrete [DefaultEstablishmentLocalStore] wraps SQLDelight;
 * tests use a fake.
 *
 * The raw establishment list is cached (filtering to Approved happens at the read site, as it
 * already does in the ViewModel); cleared on logout so no previous user's data leaks.
 */
interface EstablishmentLocalStore {
    suspend fun getAll(): List<EstablishmentWithStatus>

    /** Transactional overwrite: replaces the entire cache with [items]. */
    suspend fun replaceAll(items: List<EstablishmentWithStatus>)

    suspend fun clear()
}
