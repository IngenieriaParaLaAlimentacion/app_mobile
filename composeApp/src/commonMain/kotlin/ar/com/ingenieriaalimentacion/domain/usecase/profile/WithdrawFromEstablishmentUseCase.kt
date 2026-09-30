package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.auth.EstablishmentLinkMode
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

/**
 * Drops the caller's link to an establishment. An admin's direct subscription is removed
 * outright (unsubscribe); a requested link is marked withdrawn.
 */
class WithdrawFromEstablishmentUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(
        establishmentId: String,
        mode: EstablishmentLinkMode = EstablishmentLinkMode.RequestAccess,
    ): Result<Unit> = when (mode) {
        EstablishmentLinkMode.Subscribe -> repository.unsubscribeFromEstablishment(establishmentId)
        EstablishmentLinkMode.RequestAccess -> repository.withdrawFromEstablishment(establishmentId)
    }
}
