package ar.com.ingenieriaalimentacion.domain.usecase.registration

import ar.com.ingenieriaalimentacion.domain.model.registration.ManagerRegistration
import ar.com.ingenieriaalimentacion.domain.model.registration.RegistrationResult
import ar.com.ingenieriaalimentacion.domain.repository.RegistrationRepository

class RegisterManagerUseCase(private val repository: RegistrationRepository) {
    suspend operator fun invoke(registration: ManagerRegistration): Result<RegistrationResult> =
        repository.registerManager(registration)
}
