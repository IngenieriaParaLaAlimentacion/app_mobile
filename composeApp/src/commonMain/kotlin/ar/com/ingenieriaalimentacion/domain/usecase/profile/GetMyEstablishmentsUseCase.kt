package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

/** Relations the user withdrew from, or that an admin revoked, are excluded: they're
 *  no longer part of the user's establishment list, unlike a still-visible rejection. */
private val HIDDEN_STATUSES = setOf(EstablishmentStatus.Withdrawn, EstablishmentStatus.Revoked)

class GetMyEstablishmentsUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(): Result<List<EstablishmentWithStatus>> =
        repository.getMyEstablishments().map { establishments ->
            establishments.filterNot { it.status in HIDDEN_STATUSES }
        }
}
