package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.data.survey.dto.SURVEY_RESPONSE_CONFLICT_TYPE
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseConflictProblem
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
 *
 * Every route is nested under its survey (`/surveys/{survey_id}/responses`) — the responses router
 * moved there server-side, so `surveyId` is a path segment, not a body field.
 */
class SurveyResponseApi(private val client: HttpClient) {

    suspend fun create(surveyId: String, body: SurveyResponseRequest): SurveyResponseUploadOutcome =
        runCatching {
            val response = client.post("api/v1/surveys/$surveyId/responses") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            when (response.status.value) {
                200, 201 -> SurveyResponseUploadOutcome.Created(response.body())
                400, 422 -> SurveyResponseUploadOutcome.InvalidData
                401 -> SurveyResponseUploadOutcome.Unauthorized
                403 -> SurveyResponseUploadOutcome.Forbidden
                404 -> SurveyResponseUploadOutcome.NotFound
                409 -> {
                    val problem = runCatching { response.body<SurveyResponseConflictProblem>() }.getOrNull()
                    if (problem?.type == SURVEY_RESPONSE_CONFLICT_TYPE) {
                        SurveyResponseUploadOutcome.AlreadyAnswered(
                            submittedByName = problem.existingResponse?.submittedBy?.name,
                            eventAt = problem.existingResponse?.eventAt,
                        )
                    } else {
                        // A 409 this build doesn't recognize: surface it as a terminal failure
                        // rather than spinning on a retry that can only conflict again.
                        SurveyResponseUploadOutcome.InvalidData
                    }
                }
                else -> SurveyResponseUploadOutcome.Retryable(null)
            }
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            SurveyResponseUploadOutcome.Retryable(t)
        }

    suspend fun edit(
        surveyId: String,
        responseId: String,
        body: SurveyResponseEditRequest,
    ): SurveyResponseEditOutcome =
        runCatching {
            val response = client.put("api/v1/surveys/$surveyId/responses/$responseId") {
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

    suspend fun detail(surveyId: String, responseId: String): SurveyResponseDetailOutcome = runCatching {
        val response = client.get("api/v1/surveys/$surveyId/responses/$responseId")
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

    /** `409` + the survey-response conflict `type`: this survey is already answered for this
     *  target. Terminal — a retry with the same target can only conflict again. Both fields are
     *  nullable because the server may omit the `existing_response` detail. */
    data class AlreadyAnswered(
        val submittedByName: String?,
        val eventAt: String?,
    ) : SurveyResponseUploadOutcome

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
