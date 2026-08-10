package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceNotice
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.PagedEstablishments
import ar.com.ingenieriaalimentacion.domain.model.profile.Profile
import ar.com.ingenieriaalimentacion.domain.model.profile.UpdateProfile

interface ProfileRepository {
    suspend fun getMe(): Result<Profile>
    suspend fun updateMe(update: UpdateProfile): Result<Profile>

    /**
     * Manager-only. Returns the caller's establishments annotated with approval status.
     * Cache-first: returns the local cache when warm, otherwise fetches and caches.
     */
    suspend fun getMyEstablishments(): Result<List<EstablishmentWithStatus>>

    /** Bypass the cache, fetch fresh, and overwrite the local cache. Best-effort (no-op offline). */
    suspend fun refreshMyEstablishments(): Result<Unit>

    /** Clear the establishment cache (call on logout so no previous user's data leaks). */
    suspend fun clearEstablishmentCache()

    /** The locally-remembered "current establishment" id Home sets, for pre-filling measurement uploads. */
    suspend fun getCurrentEstablishmentId(): String?

    /** Sets (or clears, with `null`) the current establishment id. */
    suspend fun setCurrentEstablishmentId(establishmentId: String?)

    suspend fun getAvailableEstablishments(
        search: String?,
        page: Int,
        pageSize: Int,
    ): Result<PagedEstablishments>

    suspend fun requestEstablishmentAccess(ids: List<String>): Result<List<EstablishmentWithStatus>>

    /** Notifies an admin that an establishment is missing from the catalog. */
    suspend fun notifyEstablishmentAbsence(notice: EstablishmentAbsenceNotice): Result<Unit>
}
