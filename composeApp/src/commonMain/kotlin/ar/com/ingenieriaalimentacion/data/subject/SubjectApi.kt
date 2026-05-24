package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectRequest
import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Subjects endpoint. Requires an authenticated establishment_manager — uses the
 * default authed [HttpClient] (no Koin qualifier).
 */
class SubjectApi(private val client: HttpClient) {

    suspend fun create(body: SubjectRequest): SubjectResponse =
        client.post("api/v1/subjects") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
}
