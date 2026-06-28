package ar.com.ingenieriaalimentacion.domain.usecase.registration

import ar.com.ingenieriaalimentacion.domain.model.registration.FieldWorkerRegistration
import ar.com.ingenieriaalimentacion.domain.model.registration.RegistrationResult
import ar.com.ingenieriaalimentacion.domain.repository.RegistrationRepository

class RegisterFieldWorkerUseCase(private val repository: RegistrationRepository) {
    suspend operator fun invoke(registration: FieldWorkerRegistration): Result<RegistrationResult> =
        repository.registerFieldWorker(registration)
}
