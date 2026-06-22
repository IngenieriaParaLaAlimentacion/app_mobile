package ar.com.ingenieriaalimentacion.data.measurement

import ar.com.ingenieriaalimentacion.data.measurement.dto.MeasurementRequest
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

class MeasurementUploadOutcomeTest {

    private val request = MeasurementRequest(
        id = "local-1",
        subjectId = "subj-1",
        establishmentId = "est-1",
        weightKg = 72.5,
        heightCm = 170.0,
        measuredAt = "2026-06-21T18:30:00Z",
    )

    private val successBody = """
        {
          "id": "local-1",
          "subject_id": "subj-1",
          "establishment_id": "est-1",
          "weight_kg": 72.5,
          "height_cm": 170.0,
          "measured_at": "2026-06-21T18:30:00Z",
          "bmi": 25.1,
          "alerts": [
            {"alert_type": "bmi_high", "severity": "warning", "detail": "Sobrepeso"}
          ]
        }
    """.trimIndent()

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): MeasurementApi {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        return MeasurementApi(client)
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `201 maps to Created and parses bmi and alerts`() = runTest {
        val api = api { respond(successBody, HttpStatusCode.Created, jsonHeaders()) }
        val created = assertIs<MeasurementUploadOutcome.Created>(api.create(request))
        assertEquals("local-1", created.response.id)
        assertEquals(25.1, created.response.bmi)
        assertEquals(1, created.response.alerts.size)
        assertEquals("bmi_high", created.response.alerts.first().alertType)
    }

    @Test
    fun `200 replay maps to Created`() = runTest {
        val api = api { respond(successBody, HttpStatusCode.OK, jsonHeaders()) }
        assertIs<MeasurementUploadOutcome.Created>(api.create(request))
    }

    @Test
    fun `401 maps to Unauthorized`() = runTest {
        val api = api { respondError(HttpStatusCode.Unauthorized) }
        assertEquals(MeasurementUploadOutcome.Unauthorized, api.create(request))
    }

    @Test
    fun `403 maps to Forbidden`() = runTest {
        val api = api { respondError(HttpStatusCode.Forbidden) }
        assertEquals(MeasurementUploadOutcome.Forbidden, api.create(request))
    }

    @Test
    fun `404 maps to NotFound`() = runTest {
        val api = api { respondError(HttpStatusCode.NotFound) }
        assertEquals(MeasurementUploadOutcome.NotFound, api.create(request))
    }

    @Test
    fun `422 maps to InvalidData`() = runTest {
        val api = api { respondError(HttpStatusCode.UnprocessableEntity) }
        assertEquals(MeasurementUploadOutcome.InvalidData, api.create(request))
    }

    @Test
    fun `500 maps to Retryable`() = runTest {
        val api = api { respondError(HttpStatusCode.InternalServerError) }
        assertIs<MeasurementUploadOutcome.Retryable>(api.create(request))
    }

    @Test
    fun `connection failure maps to Retryable with cause`() = runTest {
        val api = api { throw RuntimeException("no network") }
        val retryable = assertIs<MeasurementUploadOutcome.Retryable>(api.create(request))
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
