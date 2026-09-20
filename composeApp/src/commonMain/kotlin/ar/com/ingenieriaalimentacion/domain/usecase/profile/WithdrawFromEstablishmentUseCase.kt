package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

class WithdrawFromEstablishmentUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(establishmentId: String): Result<Unit> =
        repository.withdrawFromEstablishment(establishmentId)
}
