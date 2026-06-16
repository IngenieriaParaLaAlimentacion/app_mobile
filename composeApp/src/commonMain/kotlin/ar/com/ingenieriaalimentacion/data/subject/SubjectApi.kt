package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectConflictProblem
import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * Subjects endpoint. Requires an authenticated establishment_manager — uses the default authed
 * [HttpClient] (no Koin qualifier). The authed client does NOT set `expectSuccess`, so non-2xx
 * responses do not throw; we branch on [io.ktor.client.statement.HttpResponse.status] explicitly and
 * deserialize the matching body. This is robust regardless of `expectSuccess`.
 */
class SubjectApi(private val client: HttpClient) {

    suspend fun create(body: SubjectRequest): SubjectUploadOutcome =
        runCatching {
            val response = client.post("api/v1/subjects") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            when (response.status.value) {
                200, 201 -> SubjectUploadOutcome.Created(response.body())
                409 -> {
                    val problem = runCatching { response.body<SubjectConflictProblem>() }.getOrNull()
                    val canonical = problem?.canonicalSubject?.id
                    if (canonical != null) SubjectUploadOutcome.Duplicate(canonical)
                    else SubjectUploadOutcome.Retryable(null) // 409 without extension → retry/surface
                }
                400 -> SubjectUploadOutcome.InvalidData
                401 -> SubjectUploadOutcome.Unauthorized
                403 -> SubjectUploadOutcome.Forbidden
                else -> SubjectUploadOutcome.Retryable(null)  // 5xx etc.
            }
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            SubjectUploadOutcome.Retryable(t)                 // connection failures
        }
}
