package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.registration.ManagerRegistration
import ar.com.ingenieriaalimentacion.domain.model.registration.RegistrationResult

interface RegistrationRepository {
    suspend fun registerManager(registration: ManagerRegistration): Result<RegistrationResult>
}
