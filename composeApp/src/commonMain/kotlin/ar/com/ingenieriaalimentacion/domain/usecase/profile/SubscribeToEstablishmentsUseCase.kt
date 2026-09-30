package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

/** Admin-only direct subscription: the links come back already approved. */
class SubscribeToEstablishmentsUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(ids: List<String>): Result<List<EstablishmentWithStatus>> =
        repository.subscribeToEstablishments(ids)
}
