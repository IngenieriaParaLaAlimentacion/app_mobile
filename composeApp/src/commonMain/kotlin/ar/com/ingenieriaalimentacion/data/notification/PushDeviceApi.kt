package ar.com.ingenieriaalimentacion.data.notification

import ar.com.ingenieriaalimentacion.data.notification.dto.RegisterDeviceRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class PushDeviceApi(private val client: HttpClient) {
    suspend fun register(body: RegisterDeviceRequest) {
        client.post("api/v1/users/me/devices") {
            expectSuccess = true
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun unregister(installationId: String) {
        client.delete("api/v1/users/me/devices/$installationId") {
            expectSuccess = true
        }
    }
}
