package ar.com.ingenieriaalimentacion.data.notification

import ar.com.ingenieriaalimentacion.data.network.HttpClientFactory
import ar.com.ingenieriaalimentacion.data.notification.dto.RegisterDeviceRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PushDeviceApiTest {
    @Test
    fun `register posts agreed body and omits null device name`() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond("{}", HttpStatusCode.OK)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(HttpClientFactory.json()) }
            defaultRequest { url("https://example.test/") }
        }

        PushDeviceApi(client).register(RegisterDeviceRequest("install-1", "token-1", "android"))

        assertEquals("/api/v1/users/me/devices", captured!!.url.encodedPath)
        assertEquals(HttpMethod.Post, captured!!.method)
        assertEquals(
            "{\"installation_id\":\"install-1\",\"fcm_token\":\"token-1\",\"platform\":\"android\"}",
            (captured!!.body as TextContent).text,
        )
    }

    @Test
    fun `register sends device name and accepts any 2xx`() = runTest {
        var captured: HttpRequestData? = null
        val client = HttpClient(MockEngine { request ->
            captured = request
            respond("", HttpStatusCode.Created)
        }) {
            install(ContentNegotiation) { json(HttpClientFactory.json()) }
            defaultRequest { url("https://example.test/") }
        }

        PushDeviceApi(client).register(
            RegisterDeviceRequest("install-1", "token-1", "android", "Android"),
        )

        assertEquals(
            "{\"installation_id\":\"install-1\",\"fcm_token\":\"token-1\",\"platform\":\"android\",\"device_name\":\"Android\"}",
            (captured!!.body as TextContent).text,
        )
    }

    @Test
    fun `unregister sends installation path`() = runTest {
        var captured: HttpRequestData? = null
        val client = HttpClient(MockEngine { request ->
            captured = request
            respond("", HttpStatusCode.NoContent)
        }) { defaultRequest { url("https://example.test/") } }

        PushDeviceApi(client).unregister("install-1")

        assertEquals(HttpMethod.Delete, captured!!.method)
        assertEquals("/api/v1/users/me/devices/install-1", captured!!.url.encodedPath)
    }

    @Test
    fun `non 2xx fails because api opts into expect success`() = runTest {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.BadRequest) }) {
            defaultRequest { url("https://example.test/") }
        }
        assertFailsWith<ClientRequestException> {
            PushDeviceApi(client).unregister("install-1")
        }
    }
}
