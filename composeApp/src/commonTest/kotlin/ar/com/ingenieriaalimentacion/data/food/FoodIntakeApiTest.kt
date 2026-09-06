package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.dto.FoodIntakeRequest
import ar.com.ingenieriaalimentacion.data.food.dto.FoodLineRequest
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FoodIntakeApiTest {

    private val request = FoodIntakeRequest(
        id = "11111111-1111-4111-8111-111111111111",
        establishmentId = "22222222-2222-4222-8222-222222222222",
        receivedAt = "2026-09-06T12:00:00Z",
        comments = "Donación",
        items = listOf(FoodLineRequest(foodId = "food-1", quantity = "12.5")),
    )

    private val successBody = """{"id":"11111111-1111-4111-8111-111111111111"}"""

    // The authed client deliberately does NOT set expectSuccess, so a non-2xx must not throw and
    // the test exercises the real status branching instead of masking it.
    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): FoodIntakeApi {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        return FoodIntakeApi(client)
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun problemHeaders() = headersOf(HttpHeaders.ContentType, "application/problem+json")

    @Test
    fun `201 maps to Created`() = runTest {
        val api = api { respond(successBody, HttpStatusCode.Created, jsonHeaders()) }
        val created = assertIs<FoodIntakeUploadOutcome.Created>(api.create(request))
        assertEquals("11111111-1111-4111-8111-111111111111", created.response.id)
    }

    @Test
    fun `200 idempotent replay maps to Created`() = runTest {
        val api = api { respond(successBody, HttpStatusCode.OK, jsonHeaders()) }
        assertIs<FoodIntakeUploadOutcome.Created>(api.create(request))
    }

    @Test
    fun `403 maps to EstablishmentAccessDenied`() = runTest {
        val api = api { respondError(HttpStatusCode.Forbidden) }
        assertEquals(FoodIntakeUploadOutcome.EstablishmentAccessDenied, api.create(request))
    }

    @Test
    fun `404 food-not-found maps to FoodNotFound`() = runTest {
        val api = api {
            respond("""{"type":"/errors/food-not-found"}""", HttpStatusCode.NotFound, problemHeaders())
        }
        assertEquals(FoodIntakeUploadOutcome.FoodNotFound, api.create(request))
    }

    @Test
    fun `404 establishment-not-found maps to EstablishmentNotFound`() = runTest {
        val api = api {
            respond("""{"type":"/errors/establishment-not-found"}""", HttpStatusCode.NotFound, problemHeaders())
        }
        assertEquals(FoodIntakeUploadOutcome.EstablishmentNotFound, api.create(request))
    }

    @Test
    fun `404 with an unrecognized type maps to InvalidData`() = runTest {
        val api = api {
            respond("""{"type":"/errors/something-new"}""", HttpStatusCode.NotFound, problemHeaders())
        }
        assertEquals(FoodIntakeUploadOutcome.InvalidData, api.create(request))
    }

    @Test
    fun `422 maps to InvalidData`() = runTest {
        val api = api { respondError(HttpStatusCode.UnprocessableEntity) }
        assertEquals(FoodIntakeUploadOutcome.InvalidData, api.create(request))
    }

    @Test
    fun `401 maps to Unauthorized`() = runTest {
        val api = api { respondError(HttpStatusCode.Unauthorized) }
        assertEquals(FoodIntakeUploadOutcome.Unauthorized, api.create(request))
    }

    @Test
    fun `500 maps to Retryable`() = runTest {
        val api = api { respondError(HttpStatusCode.InternalServerError) }
        assertIs<FoodIntakeUploadOutcome.Retryable>(api.create(request))
    }

    @Test
    fun `transport failure maps to Retryable with cause`() = runTest {
        val api = api { throw RuntimeException("no network") }
        val retryable = assertIs<FoodIntakeUploadOutcome.Retryable>(api.create(request))
        assertTrue(retryable.cause != null)
    }

    @Test
    fun `quantity is serialized as a JSON number, not a string`() = runTest {
        var captured: String? = null
        val api = api { req ->
            captured = (req.body as io.ktor.http.content.TextContent).text
            respond(successBody, HttpStatusCode.Created, jsonHeaders())
        }
        api.create(request)
        val body = assertNotNull(captured)
        assertTrue(body.contains("\"quantity\":12.5"), "body: $body")
        assertFalse(body.contains("\"quantity\":\"12.5\""), "quantity must not be quoted: $body")
    }

    @Test
    fun `request body carries the idempotency id`() = runTest {
        var captured: String? = null
        val api = api { req ->
            captured = (req.body as io.ktor.http.content.TextContent).text
            respond(successBody, HttpStatusCode.Created, jsonHeaders())
        }
        api.create(request)
        assertTrue(
            captured?.contains("\"id\":\"11111111-1111-4111-8111-111111111111\"") == true,
            "body: $captured",
        )
    }
}
