package ar.com.ingenieriaalimentacion.data.food

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
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EstablishmentFoodMovementApiTest {
    private fun api(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): EstablishmentFoodMovementApi {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
            defaultRequest { url("https://api.example.com/") }
        }
        return EstablishmentFoodMovementApi(client)
    }

    private fun MockRequestHandleScope.response(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = ContentType.Application.Json.toString(),
    ) = respond(body, status, headersOf(HttpHeaders.ContentType, contentType))

    private fun MockRequestHandleScope.problem(status: HttpStatusCode, type: String) = response(
        """{"type":"$type","title":"x","detail":"y"}""",
        status,
        "application/problem+json",
    )

    private val pageJson = """{"items":[{"id":"m1","direction":"in","occurred_at":"2026-09-19T12:00:00Z","responsible":{"id":"u1","name":"Ana","last_name":"Pérez"},"food_count":3}],"total":1,"page":1,"page_size":20,"total_pages":1}"""
    private val detailJson = """{"id":"m1","direction":"in","occurred_at":"2026-09-19T12:00:00Z","source":"manual","comments":"Donación","responsible":{"id":"u1","name":"Ana","last_name":"Pérez"},"items":[{"food":{"id":"f1","name":"Arroz"},"unit":"kilogramos","quantity":"12.500"},{"food":{"id":"f1","name":"Arroz"},"unit":"kilogramos","quantity":"1.000"}]}"""

    @Test
    fun `list sends path pagination and non-null filters`() = runTest {
        var request: HttpRequestData? = null
        val movementApi = api { request = it; response(pageJson) }

        movementApi.list("e1", "in", "from", "to", "f1", 2, 20)

        val url = requireNotNull(request).url
        assertEquals("/api/v1/establishments/e1/food-movements", url.encodedPath)
        assertEquals("2", url.parameters["page"])
        assertEquals("20", url.parameters["page_size"])
        assertEquals("in", url.parameters["direction"])
        assertEquals("from", url.parameters["occurred_from"])
        assertEquals("to", url.parameters["occurred_to"])
        assertEquals("f1", url.parameters["food_id"])
    }

    @Test
    fun `list omits absent and blank filters`() = runTest {
        var request: HttpRequestData? = null
        val movementApi = api { request = it; response(pageJson) }
        movementApi.list("e1", null, "", null, "  ", 1, 20)
        val params = requireNotNull(request).url.parameters
        assertNull(params["direction"])
        assertNull(params["occurred_from"])
        assertNull(params["occurred_to"])
        assertNull(params["food_id"])
    }

    @Test
    fun `success decodes summary detail nullable comments quantities and duplicate lines`() = runTest {
        val page = assertIs<FoodMovementPageOutcome.Loaded>(
            api { response(pageJson) }.list("e1", null, null, null, null, 1, 20),
        ).response
        assertEquals("Pérez", page.items.single().responsible.lastName)
        assertEquals(3, page.items.single().foodCount)

        val detail = assertIs<FoodMovementDetailOutcome.Loaded>(
            api { response(detailJson) }.detail("e1", "m1"),
        ).response
        assertEquals("Donación", detail.comments)
        assertEquals(listOf("f1", "f1"), detail.items.map { it.food.id })
        assertEquals(listOf("12.500", "1.000"), detail.items.map { it.quantity })

        val numeric = detailJson.replace("\"12.500\"", "12.500")
        assertEquals(
            "12.500",
            assertIs<FoodMovementDetailOutcome.Loaded>(
                api { response(numeric) }.detail("e1", "m1"),
            ).response.items.first().quantity,
        )
        assertNull(
            assertIs<FoodMovementDetailOutcome.Loaded>(
                api { response(detailJson.replace("\"Donación\"", "null")) }
                    .detail("e1", "m1"),
            ).response.comments,
        )
    }

    @Test
    fun `list maps status codes and typed 404`() = runTest {
        suspend fun outcome(status: HttpStatusCode, type: String = "/errors/x") =
            api { problem(status, type) }.list("e1", null, null, null, null, 1, 20)
        assertEquals(FoodMovementPageOutcome.InvalidRequest, outcome(HttpStatusCode.BadRequest))
        assertEquals(FoodMovementPageOutcome.InvalidRequest, outcome(HttpStatusCode.UnprocessableEntity))
        assertEquals(FoodMovementPageOutcome.Unauthorized, outcome(HttpStatusCode.Unauthorized))
        assertEquals(FoodMovementPageOutcome.EstablishmentAccessDenied, outcome(HttpStatusCode.Forbidden))
        assertEquals(
            FoodMovementPageOutcome.EstablishmentNotFound,
            outcome(HttpStatusCode.NotFound, "/errors/establishment-not-found"),
        )
        assertEquals(FoodMovementPageOutcome.InvalidRequest, outcome(HttpStatusCode.NotFound))
        assertIs<FoodMovementPageOutcome.Retryable>(outcome(HttpStatusCode.InternalServerError))
        assertIs<FoodMovementPageOutcome.Retryable>(
            api { throw RuntimeException("offline") }
                .list("e1", null, null, null, null, 1, 20),
        )
    }

    @Test
    fun `detail maps status codes and discriminates 404 types`() = runTest {
        suspend fun outcome(status: HttpStatusCode, type: String = "/errors/x") =
            api { problem(status, type) }.detail("e1", "m1")

        assertEquals(FoodMovementDetailOutcome.InvalidRequest, outcome(HttpStatusCode.BadRequest))
        assertEquals(
            FoodMovementDetailOutcome.InvalidRequest,
            outcome(HttpStatusCode.UnprocessableEntity),
        )
        assertEquals(FoodMovementDetailOutcome.Unauthorized, outcome(HttpStatusCode.Unauthorized))
        assertEquals(
            FoodMovementDetailOutcome.EstablishmentAccessDenied,
            outcome(HttpStatusCode.Forbidden),
        )
        val establishment = api {
            problem(HttpStatusCode.NotFound, "/errors/establishment-not-found")
        }
        val movement = api { problem(HttpStatusCode.NotFound, "/errors/movement-not-found") }
        assertEquals(
            FoodMovementDetailOutcome.EstablishmentNotFound,
            establishment.detail("e1", "m1"),
        )
        assertEquals(FoodMovementDetailOutcome.MovementNotFound, movement.detail("e1", "m1"))
        assertIs<FoodMovementDetailOutcome.Retryable>(
            outcome(HttpStatusCode.InternalServerError),
        )
        assertIs<FoodMovementDetailOutcome.Retryable>(
            api { throw RuntimeException("offline") }.detail("e1", "m1"),
        )
    }

    @Test
    fun `cancellation is rethrown`() = runTest {
        assertFailsWith<CancellationException> {
            api { throw CancellationException("cancel") }
                .list("e1", null, null, null, null, 1, 20)
        }
        assertFailsWith<CancellationException> {
            api { throw CancellationException("cancel") }.detail("e1", "m1")
        }
    }
}
