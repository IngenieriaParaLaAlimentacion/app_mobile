package ar.com.ingenieriaalimentacion.data.registration

import ar.com.ingenieriaalimentacion.data.registration.dto.RegisterManagerRequest
import ar.com.ingenieriaalimentacion.data.registration.dto.RegistrationResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Self-registration endpoint. Unauthenticated, so it is constructed against the
 * no-auth refresh [HttpClient] (Koin qualifier `"refresh"`).
 */
class RegistrationApi(private val refreshClient: HttpClient) {

    suspend fun registerManager(body: RegisterManagerRequest): RegistrationResponse =
        refreshClient.post("api/v1/auth/register/establishment-manager") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
}
