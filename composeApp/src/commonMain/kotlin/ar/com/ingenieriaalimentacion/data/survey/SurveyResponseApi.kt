package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseEditRequest
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseRequest
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * Survey-response create/edit/detail endpoints. Same status-branching convention as
 * [ar.com.ingenieriaalimentacion.data.subject.SubjectApi] / [SurveyApi].
 */
class SurveyResponseApi(private val client: HttpClient) {

    suspend fun create(body: SurveyResponseRequest): SurveyResponseUploadOutcome =
        runCatching {
            val response = client.post("api/v1/survey-responses") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            when (response.status.value) {
                200, 201 -> SurveyResponseUploadOutcome.Created(response.body())
                400, 422 -> SurveyResponseUploadOutcome.InvalidData
                401 -> SurveyResponseUploadOutcome.Unauthorized
                403 -> SurveyResponseUploadOutcome.Forbidden
                404 -> SurveyResponseUploadOutcome.NotFound
                else -> SurveyResponseUploadOutcome.Retryable(null)
            }
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            SurveyResponseUploadOutcome.Retryable(t)
        }

    suspend fun edit(responseId: String, body: SurveyResponseEditRequest): SurveyResponseEditOutcome =
        runCatching {
            val response = client.put("api/v1/survey-responses/$responseId") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            when (response.status.value) {
                200 -> SurveyResponseEditOutcome.Applied(response.body())
                400 -> SurveyResponseEditOutcome.InvalidData
                401 -> SurveyResponseEditOutcome.Unauthorized
                403 -> SurveyResponseEditOutcome.Forbidden
                404 -> SurveyResponseEditOutcome.NotFound
                else -> SurveyResponseEditOutcome.Retryable(null)
            }
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            SurveyResponseEditOutcome.Retryable(t)
        }

    suspend fun detail(responseId: String): SurveyResponseDetailOutcome = runCatching {
        val response = client.get("api/v1/survey-responses/$responseId")
        when (response.status.value) {
            200 -> SurveyResponseDetailOutcome.Found(response.body())
            403 -> SurveyResponseDetailOutcome.Forbidden
            404 -> SurveyResponseDetailOutcome.NotFound
            else -> SurveyResponseDetailOutcome.Retryable(null)
        }
    }.getOrElse { t ->
        if (t is CancellationException) throw t
        SurveyResponseDetailOutcome.Retryable(t)
    }
}

sealed interface SurveyResponseUploadOutcome {
    data class Created(val response: SurveyResponseResponse) : SurveyResponseUploadOutcome
    data object InvalidData : SurveyResponseUploadOutcome
    data object Forbidden : SurveyResponseUploadOutcome
    data object NotFound : SurveyResponseUploadOutcome
    data object Unauthorized : SurveyResponseUploadOutcome
    data class Retryable(val cause: Throwable?) : SurveyResponseUploadOutcome
}

sealed interface SurveyResponseEditOutcome {
    data class Applied(val response: SurveyResponseResponse) : SurveyResponseEditOutcome
    data object InvalidData : SurveyResponseEditOutcome
    data object Forbidden : SurveyResponseEditOutcome
    data object NotFound : SurveyResponseEditOutcome
    data object Unauthorized : SurveyResponseEditOutcome
    data class Retryable(val cause: Throwable?) : SurveyResponseEditOutcome
}

sealed interface SurveyResponseDetailOutcome {
    data class Found(val response: SurveyResponseResponse) : SurveyResponseDetailOutcome
    data object Forbidden : SurveyResponseDetailOutcome
    data object NotFound : SurveyResponseDetailOutcome
    data class Retryable(val cause: Throwable?) : SurveyResponseDetailOutcome
}
