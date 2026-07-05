package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

/**
 * Resolves the persisted current-establishment id against [approved] — the caller's own,
 * already-Approved-filtered establishment list. An id no longer present there (revoked, deleted)
 * silently resolves to `null`; there's nothing else to check, this is the whole ADR-014 rule.
 */
class GetCurrentEstablishmentUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(approved: List<EstablishmentWithStatus>): EstablishmentWithStatus? {
        val currentId = repository.getCurrentEstablishmentId() ?: return null
        return approved.firstOrNull { it.id == currentId }
    }
}
