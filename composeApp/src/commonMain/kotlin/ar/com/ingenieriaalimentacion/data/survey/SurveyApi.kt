package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.data.survey.dto.PaginatedSurveysResponse
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyDetailResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * Surveys endpoint. Uses the default authed [HttpClient] (no Koin qualifier). The authed client does
 * NOT set `expectSuccess`, so non-2xx responses do not throw; we branch on the status explicitly and
 * deserialize the matching body. Mirrors [ar.com.ingenieriaalimentacion.data.subject.SubjectApi].
 */
class SurveyApi(private val client: HttpClient) {

    suspend fun available(page: Int, pageSize: Int): PaginatedSurveysResponse {
        val response = client.get("api/v1/surveys/available") {
            parameter("page", page)
            parameter("page_size", pageSize)
        }
        if (!response.status.isSuccess()) error("surveys available failed: ${response.status.value}")
        return response.body()
    }

    suspend fun detail(surveyId: String): SurveyDetailOutcome = runCatching {
        val response = client.get("api/v1/surveys/$surveyId")
        when (response.status.value) {
            200 -> SurveyDetailOutcome.Found(response.body())
            403 -> SurveyDetailOutcome.Forbidden
            404 -> SurveyDetailOutcome.NotFound
            else -> SurveyDetailOutcome.Retryable(null)
        }
    }.getOrElse { t ->
        if (t is CancellationException) throw t
        SurveyDetailOutcome.Retryable(t)
    }
}

sealed interface SurveyDetailOutcome {
    data class Found(val response: SurveyDetailResponse) : SurveyDetailOutcome
    data object Forbidden : SurveyDetailOutcome
    data object NotFound : SurveyDetailOutcome
    data class Retryable(val cause: Throwable?) : SurveyDetailOutcome
}
