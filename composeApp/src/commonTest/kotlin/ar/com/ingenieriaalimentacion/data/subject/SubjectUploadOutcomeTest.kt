package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.data.subject.dto.SexDto
import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SubjectUploadOutcomeTest {

    private val request = SubjectRequest(
        id = "local-1",
        dni = 12345678,
        sex = SexDto.MALE,
        name = "Juan",
        lastName = "Pérez",
        birthDate = "1990-01-01",
        isRural = false,
        isIndigenous = false,
    )

    private val successBody = """
        {
          "id": "local-1",
          "dni": 12345678,
          "sex": "male",
          "name": "Juan",
          "last_name": "Pérez",
          "birth_date": "1990-01-01",
          "is_rural": false,
          "is_indigenous": false
        }
    """.trimIndent()

    // Authed client does NOT set expectSuccess — non-2xx must not throw.
    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): SubjectApi {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        return SubjectApi(client)
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `201 maps to Created with server id`() = runTest {
        val api = api { respond(successBody, HttpStatusCode.Created, jsonHeaders()) }
        val outcome = api.create(request)
        val created = assertIs<SubjectUploadOutcome.Created>(outcome)
        assertEquals("local-1", created.response.id)
    }

    @Test
    fun `200 replay maps to Created`() = runTest {
        val api = api { respond(successBody, HttpStatusCode.OK, jsonHeaders()) }
        assertIs<SubjectUploadOutcome.Created>(api.create(request))
    }

    @Test
    fun `409 with canonical_subject maps to Duplicate`() = runTest {
        val body = """{"detail":"dup","canonical_subject":{"id":"canon-9"}}"""
        val api = api {
            respond(body, HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType, "application/problem+json"))
        }
        val dup = assertIs<SubjectUploadOutcome.Duplicate>(api.create(request))
        assertEquals("canon-9", dup.canonicalId)
    }

    @Test
    fun `409 without extension maps to Retryable`() = runTest {
        val api = api {
            respond("""{"detail":"dup"}""", HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType, "application/problem+json"))
        }
        assertIs<SubjectUploadOutcome.Retryable>(api.create(request))
    }

    @Test
    fun `400 maps to InvalidData`() = runTest {
        val api = api { respondError(HttpStatusCode.BadRequest) }
        assertEquals(SubjectUploadOutcome.InvalidData, api.create(request))
    }

    @Test
    fun `401 maps to Unauthorized`() = runTest {
        val api = api { respondError(HttpStatusCode.Unauthorized) }
        assertEquals(SubjectUploadOutcome.Unauthorized, api.create(request))
    }

    @Test
    fun `403 maps to Forbidden`() = runTest {
        val api = api { respondError(HttpStatusCode.Forbidden) }
        assertEquals(SubjectUploadOutcome.Forbidden, api.create(request))
    }

    @Test
    fun `500 maps to Retryable`() = runTest {
        val api = api { respondError(HttpStatusCode.InternalServerError) }
        assertIs<SubjectUploadOutcome.Retryable>(api.create(request))
    }

    @Test
    fun `connection failure maps to Retryable with cause`() = runTest {
        val api = api { throw RuntimeException("no network") }
        val outcome = api.create(request)
        val retryable = assertIs<SubjectUploadOutcome.Retryable>(outcome)
        assertTrue(retryable.cause != null)
    }

    @Test
    fun `request body includes the idempotency id`() = runTest {
        var captured: String? = null
        val api = api { req ->
            captured = (req.body as io.ktor.http.content.TextContent).text
            respond(successBody, HttpStatusCode.Created, jsonHeaders())
        }
        api.create(request)
        assertTrue(captured?.contains("\"id\":\"local-1\"") == true, "body: $captured")
    }
}
