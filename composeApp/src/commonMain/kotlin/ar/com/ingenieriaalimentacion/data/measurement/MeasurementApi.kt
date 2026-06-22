package ar.com.ingenieriaalimentacion.data.measurement

import ar.com.ingenieriaalimentacion.data.measurement.dto.MeasurementRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * Measurements endpoint. Uses the default authed [HttpClient] (no Koin qualifier). The authed client
 * does NOT set `expectSuccess`, so non-2xx responses do not throw; we branch on the status explicitly
 * and deserialize the matching body — robust regardless of `expectSuccess`. Mirrors
 * [ar.com.ingenieriaalimentacion.data.subject.SubjectApi].
 */
class MeasurementApi(private val client: HttpClient) {

    suspend fun create(body: MeasurementRequest): MeasurementUploadOutcome =
        runCatching {
            val response = client.post("api/v1/measurements") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            when (response.status.value) {
                200, 201 -> MeasurementUploadOutcome.Created(response.body())
                401 -> MeasurementUploadOutcome.Unauthorized
                403 -> MeasurementUploadOutcome.Forbidden
                404 -> MeasurementUploadOutcome.NotFound
                422 -> MeasurementUploadOutcome.InvalidData
                else -> MeasurementUploadOutcome.Retryable(null)   // 5xx etc.
            }
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            MeasurementUploadOutcome.Retryable(t)                  // transport failure
        }
}
