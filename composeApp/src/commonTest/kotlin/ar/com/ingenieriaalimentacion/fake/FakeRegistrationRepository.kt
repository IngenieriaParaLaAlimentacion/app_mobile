package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.registration.FieldWorkerRegistration
import ar.com.ingenieriaalimentacion.domain.model.registration.RegistrationResult
import ar.com.ingenieriaalimentacion.domain.repository.RegistrationRepository
import kotlinx.coroutines.CompletableDeferred

class FakeRegistrationRepository : RegistrationRepository {
    var result: Result<RegistrationResult> = Result.success(
        RegistrationResult(id = "id-1", email = "manager@example.com", status = "pending_validation"),
    )

    /** When set, [registerFieldWorker] suspends on this gate before returning — lets tests observe in-flight state. */
    var gate: CompletableDeferred<Unit>? = null

    var lastRegistration: FieldWorkerRegistration? = null

    override suspend fun registerFieldWorker(registration: FieldWorkerRegistration): Result<RegistrationResult> {
        lastRegistration = registration
        gate?.await()
        return result
    }
}
