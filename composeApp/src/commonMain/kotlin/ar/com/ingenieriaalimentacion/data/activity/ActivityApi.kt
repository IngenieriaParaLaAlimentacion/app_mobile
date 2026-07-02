package ar.com.ingenieriaalimentacion.data.activity

import ar.com.ingenieriaalimentacion.data.activity.dto.PaginatedActivityResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess

/**
 * `/me/activity` — this user's own subjects+measurements, merged & paginated. Mirrors
 * [ar.com.ingenieriaalimentacion.data.subject.SubjectApi.list]'s status-check convention.
 */
class ActivityApi(private val client: HttpClient) {
    suspend fun list(page: Int, pageSize: Int, type: String? = null): PaginatedActivityResponse {
        val response = client.get("api/v1/users/me/activity") {
            parameter("page", page)
            parameter("page_size", pageSize)
            type?.let { parameter("type", it) }
        }
        if (!response.status.isSuccess()) error("activity list failed: ${response.status.value}")
        return response.body()
    }
}
