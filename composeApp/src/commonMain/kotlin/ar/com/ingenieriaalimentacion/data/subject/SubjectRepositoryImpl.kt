package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.data.subject.mapper.toDomain
import ar.com.ingenieriaalimentacion.data.subject.mapper.toRequest
import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.Subject
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectError
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

class SubjectRepositoryImpl(private val api: SubjectApi) : SubjectRepository {
    override suspend fun create(newSubject: NewSubject): Result<Subject> =
        runCatching { api.create(newSubject.toRequest()).toDomain() }
            .recoverCatching { throwable -> throw SubjectFailure(mapToError(throwable), throwable) }

    private fun mapToError(t: Throwable): SubjectError = when (t) {
        is ClientRequestException -> when (t.response.status) {
            HttpStatusCode.BadRequest -> SubjectError.InvalidData
            HttpStatusCode.Unauthorized -> SubjectError.Unauthorized
            HttpStatusCode.Forbidden -> SubjectError.Forbidden
            else -> SubjectError.Network
        }
        is ResponseException -> SubjectError.Network
        else -> SubjectError.Unknown(t)
    }
}

class SubjectFailure(val error: SubjectError, cause: Throwable?) :
    Exception(error.toString(), cause)
