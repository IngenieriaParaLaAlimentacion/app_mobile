package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.registration.FieldWorkerRegistration
import ar.com.ingenieriaalimentacion.domain.model.registration.RegistrationResult

interface RegistrationRepository {
    suspend fun registerFieldWorker(registration: FieldWorkerRegistration): Result<RegistrationResult>
}
