package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.dto.FoodLineRequest
import ar.com.ingenieriaalimentacion.data.food.dto.FoodOutflowRequest
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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FoodOutflowApiTest {
    private val request = FoodOutflowRequest(
        id = "11111111-1111-4111-8111-111111111111",
        establishmentId = "22222222-2222-4222-8222-222222222222",
        occurredAt = "2026-09-02T10:00:00Z",
        category = "other",
        comments = "Almuerzo",
        items = listOf(FoodLineRequest("food-1", "4.000")),
    )
    private val successBody = """{"id":"${request.id}"}"""

    private fun api(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): FoodOutflowApi = FoodOutflowApi(
        HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            defaultRequest { url("https://api.example.com/") }
        },
    )

    private fun jsonHeaders() =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun problemHeaders() = headersOf(HttpHeaders.ContentType, "application/problem+json")

    @Test
    fun `201 and 200 replay map to Created`() = runTest {
        listOf(HttpStatusCode.Created, HttpStatusCode.OK).forEach { status ->
            val outcome = api { respond(successBody, status, jsonHeaders()) }.create(request)
            assertEquals(request.id, assertIs<FoodOutflowUploadOutcome.Created>(outcome).response.id)
        }
    }

    @Test
    fun `request uses occurred_at and unquoted exact quantity`() = runTest {
        var body: String? = null
        api { captured ->
            body = (captured.body as TextContent).text
            respond(successBody, HttpStatusCode.Created, jsonHeaders())
        }.create(request)

        val encoded = assertNotNull(body)
        assertTrue(encoded.contains("\"occurred_at\":\"2026-09-02T10:00:00Z\""))
        assertTrue(encoded.contains("\"category\":\"other\""))
        assertTrue(encoded.contains("\"comments\":\"Almuerzo\""))
        assertTrue(encoded.contains("\"quantity\":4.000"))
    }

    @Test
    fun `non other request omits comments key even with global encode defaults`() = runTest {
        var body: String? = null
        api { captured ->
            body = (captured.body as TextContent).text
            respond(successBody, HttpStatusCode.Created, jsonHeaders())
        }.create(request.copy(category = "kitchen", comments = null))

        val encoded = assertNotNull(body)
        assertTrue(encoded.contains("\"category\":\"kitchen\""))
        assertTrue(!encoded.contains("\"comments\""))
    }

    @Test
    fun `409 decodes insufficient stock extensions`() = runTest {
        val outcome = api {
            respond(
                """{"type":"/errors/insufficient-stock","food_id":"food-1","requested":"4","available":"2.500"}""",
                HttpStatusCode.Conflict,
                problemHeaders(),
            )
        }.create(request)

        assertEquals(
            FoodOutflowUploadOutcome.InsufficientStock("food-1", "4", "2.500"),
            outcome,
        )
    }

    @Test
    fun `400 and 422 map to InvalidData`() = runTest {
        listOf(HttpStatusCode.BadRequest, HttpStatusCode.UnprocessableEntity).forEach { status ->
            assertEquals(
                FoodOutflowUploadOutcome.InvalidData,
                api { respondError(status) }.create(request),
            )
        }
    }

    @Test
    fun `401 maps to Unauthorized`() = runTest {
        assertEquals(
            FoodOutflowUploadOutcome.Unauthorized,
            api { respondError(HttpStatusCode.Unauthorized) }.create(request),
        )
    }

    @Test
    fun `403 maps to EstablishmentAccessDenied`() = runTest {
        assertEquals(
            FoodOutflowUploadOutcome.EstablishmentAccessDenied,
            api { respondError(HttpStatusCode.Forbidden) }.create(request),
        )
    }

    @Test
    fun `404 types distinguish food and establishment`() = runTest {
        val food = api {
            respond("""{"type":"/errors/food-not-found"}""", HttpStatusCode.NotFound, problemHeaders())
        }.create(request)
        val establishment = api {
            respond(
                """{"type":"/errors/establishment-not-found"}""",
                HttpStatusCode.NotFound,
                problemHeaders(),
            )
        }.create(request)

        assertEquals(FoodOutflowUploadOutcome.FoodNotFound, food)
        assertEquals(FoodOutflowUploadOutcome.EstablishmentNotFound, establishment)
    }

    @Test
    fun `unknown 404 and malformed 409 map to InvalidData`() = runTest {
        val unknown = api {
            respond("""{"type":"/errors/new"}""", HttpStatusCode.NotFound, problemHeaders())
        }.create(request)
        val malformed = api {
            respond(
                """{"type":"/errors/insufficient-stock","food_id":"food-1"}""",
                HttpStatusCode.Conflict,
                problemHeaders(),
            )
        }.create(request)

        assertEquals(FoodOutflowUploadOutcome.InvalidData, unknown)
        assertEquals(FoodOutflowUploadOutcome.InvalidData, malformed)
    }

    @Test
    fun `500 and transport failure are Retryable`() = runTest {
        assertIs<FoodOutflowUploadOutcome.Retryable>(
            api { respondError(HttpStatusCode.InternalServerError) }.create(request),
        )
        val transport = assertIs<FoodOutflowUploadOutcome.Retryable>(
            api { throw RuntimeException("offline") }.create(request),
        )
        assertNotNull(transport.cause)
    }
}
