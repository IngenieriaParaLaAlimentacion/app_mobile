package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.data.survey.dto.PaginatedSurveyResponseActivityResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess

/**
 * `/me/activity?type=survey_response` — same URL as
 * [ar.com.ingenieriaalimentacion.data.activity.ActivityApi.list], but deserialized into the
 * independent [PaginatedSurveyResponseActivityResponse] shape so this file doesn't need to know
 * about [ar.com.ingenieriaalimentacion.data.activity.dto.ActivityResponse]'s subject/measurement
 * item types, or vice versa.
 */
class SurveyResponseActivityApi(private val client: HttpClient) {
    suspend fun list(page: Int, pageSize: Int): PaginatedSurveyResponseActivityResponse {
        val response = client.get("api/v1/users/me/activity") {
            parameter("page", page)
            parameter("page_size", pageSize)
            parameter("type", "survey_response")
        }
        if (!response.status.isSuccess()) error("survey response activity list failed: ${response.status.value}")
        return response.body()
    }
}
