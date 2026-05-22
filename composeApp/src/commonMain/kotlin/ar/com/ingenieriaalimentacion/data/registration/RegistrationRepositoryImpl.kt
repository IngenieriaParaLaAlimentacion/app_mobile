package ar.com.ingenieriaalimentacion.data.registration

import ar.com.ingenieriaalimentacion.data.registration.mapper.toRequest
import ar.com.ingenieriaalimentacion.domain.model.registration.ManagerRegistration
import ar.com.ingenieriaalimentacion.domain.model.registration.RegistrationError
import ar.com.ingenieriaalimentacion.domain.model.registration.RegistrationResult
import ar.com.ingenieriaalimentacion.domain.repository.RegistrationRepository
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

class RegistrationRepositoryImpl(private val api: RegistrationApi) : RegistrationRepository {
    override suspend fun registerManager(registration: ManagerRegistration): Result<RegistrationResult> =
        runCatching {
            val res = api.registerManager(registration.toRequest())
            RegistrationResult(id = res.id, email = res.email, status = res.status)
        }.recoverCatching { throwable ->
            throw RegistrationFailure(mapToError(throwable), throwable)
        }

    private fun mapToError(t: Throwable): RegistrationError = when (t) {
        is ClientRequestException -> when (t.response.status) {
            HttpStatusCode.Conflict -> RegistrationError.UserAlreadyExists
            HttpStatusCode.NotFound -> RegistrationError.EstablishmentNotFound
            HttpStatusCode.BadRequest -> RegistrationError.InvalidData
            else -> RegistrationError.Network
        }
        is ResponseException -> RegistrationError.Network
        else -> RegistrationError.Unknown(t)
    }
}

/**
 * Wraps a typed [RegistrationError] for callers that bubble it through `Result.failure(...)`.
 * The Register feature pattern-matches on `error` to render user-facing messages.
 */
class RegistrationFailure(val error: RegistrationError, cause: Throwable?) :
    Exception(error.toString(), cause)
