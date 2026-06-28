package ar.com.ingenieriaalimentacion.data.registration

import ar.com.ingenieriaalimentacion.domain.model.registration.FieldWorkerRegistration
import ar.com.ingenieriaalimentacion.domain.model.registration.RegistrationError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegistrationRepositoryImplTest {

    private val registration = FieldWorkerRegistration(
        email = "manager@example.com",
        password = "password123",
        name = "Ana",
        lastName = "García",
        establishmentIds = listOf("est-1", "est-2"),
    )

    private val successBody = """
        {
          "id": "id-1",
          "email": "manager@example.com",
          "name": "Ana",
          "last_name": "García",
          "role": "field_worker",
          "status": "pending_validation",
          "establishments": ["est-1", "est-2"]
        }
    """.trimIndent()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        val engine = MockEngine(handler)
        return HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
    }

    private fun repository(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        RegistrationRepositoryImpl(RegistrationApi(client(handler)))

    @Test
    fun `201 maps to success result`() = runTest {
        val repo = repository {
            respond(
                content = successBody,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = repo.registerFieldWorker(registration)

        val value = result.getOrNull()
        assertEquals("id-1", value?.id)
        assertEquals("pending_validation", value?.status)
    }

    @Test
    fun `request body serializes with snake_case last_name and establishments array`() = runTest {
        var capturedBody: String? = null
        val repo = repository { request ->
            capturedBody = (request.body as TextContent).text
            respond(
                content = successBody,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        repo.registerFieldWorker(registration)

        val body = capturedBody
        assertTrue(body != null && body.contains("\"last_name\""), "body should contain last_name: $body")
        assertTrue(body.contains("\"establishments\""), "body should contain establishments: $body")
        assertTrue(body.contains("est-1") && body.contains("est-2"), "body should contain establishment ids: $body")
    }

    @Test
    fun `409 maps to UserAlreadyExists`() = runTest {
        assertErrorFor(HttpStatusCode.Conflict, RegistrationError.UserAlreadyExists)
    }

    @Test
    fun `404 maps to EstablishmentNotFound`() = runTest {
        assertErrorFor(HttpStatusCode.NotFound, RegistrationError.EstablishmentNotFound)
    }

    @Test
    fun `400 maps to InvalidData`() = runTest {
        assertErrorFor(HttpStatusCode.BadRequest, RegistrationError.InvalidData)
    }

    @Test
    fun `500 maps to Network`() = runTest {
        assertErrorFor(HttpStatusCode.InternalServerError, RegistrationError.Network)
    }

    private suspend fun assertErrorFor(status: HttpStatusCode, expected: RegistrationError) {
        val repo = repository {
            respond(
                content = """{"detail":"error"}""",
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
            )
        }

        val result = repo.registerFieldWorker(registration)

        val failure = result.exceptionOrNull()
        assertTrue(failure is RegistrationFailure, "expected RegistrationFailure but was $failure")
        assertEquals(expected, failure.error)
    }
}
