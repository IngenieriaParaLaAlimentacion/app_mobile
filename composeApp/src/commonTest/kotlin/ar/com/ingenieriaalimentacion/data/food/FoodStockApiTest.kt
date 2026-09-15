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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoodStockApiTest {

    // The authed client deliberately does NOT set expectSuccess, so a non-2xx must not throw and
    // the test exercises the real status branching instead of masking it.
    private fun api(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): FoodStockApi {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
            defaultRequest { url("https://api.example.com/") }
        }
        return FoodStockApi(client)
    }

    private fun MockRequestHandleScope.ok(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun MockRequestHandleScope.problem(status: HttpStatusCode, type: String) = respond(
        content = """{"type":"$type","title":"x","detail":"y"}""",
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
    )

    private val onePage = """
        {
          "items": [
            {
              "food": { "id": "f1", "name": "Arroz" },
              "category": { "id": "c1", "name": "Cereales" },
              "unit": "kilogramos",
              "quantity": "10.000",
              "updated_at": "2026-09-02T10:15:33.412000Z"
            }
          ],
          "total": 1, "page": 1, "page_size": 20, "total_pages": 1
        }
    """.trimIndent()

    @Test
    fun `listStock pins every outbound query param`() = runTest {
        var captured: HttpRequestData? = null
        val stockApi = api { captured = it; ok(onePage) }

        stockApi.listStock(
            establishmentId = "e-1",
            onlyAvailable = true,
            categoryId = "c-9",
            sortOrder = "quantity_desc",
            page = 3,
            pageSize = 50,
        )

        val url = requireNotNull(captured).url
        // Relative path, no leading slash — resolved against defaultRequest's base URL.
        assertEquals("/api/v1/establishments/e-1/stock", url.encodedPath)
        assertEquals("3", url.parameters["page"])
        assertEquals("50", url.parameters["page_size"])
        assertEquals("quantity_desc", url.parameters["sort_order"])
        assertEquals("true", url.parameters["only_available"])
        assertEquals("c-9", url.parameters["category_id"])
    }

    @Test
    fun `only_available and category_id are omitted rather than sent empty`() = runTest {
        var captured: HttpRequestData? = null
        val stockApi = api { captured = it; ok(onePage) }

        stockApi.listStock(
            establishmentId = "e-1",
            onlyAvailable = false,
            categoryId = null,
            sortOrder = "name_asc",
            page = 1,
            pageSize = 20,
        )

        val params = requireNotNull(captured).url.parameters
        assertNull(params["only_available"])
        assertNull(params["category_id"])
        assertEquals("name_asc", params["sort_order"])
    }

    @Test
    fun `a blank category_id is omitted, never sent as an empty string`() = runTest {
        var captured: HttpRequestData? = null
        val stockApi = api { captured = it; ok(onePage) }

        stockApi.listStock("e-1", onlyAvailable = true, categoryId = "  ", sortOrder = "name_asc", page = 1, pageSize = 20)

        assertNull(requireNotNull(captured).url.parameters["category_id"])
    }

    @Test
    fun `a 200 decodes quantity as an exact decimal string`() = runTest {
        val stockApi = api { ok(onePage) }

        val outcome = stockApi.listStock("e-1", true, null, "name_asc", 1, 20)

        val loaded = assertIs<FoodStockPageOutcome.Loaded>(outcome)
        val row = loaded.response.items.single()
        assertEquals("10.000", row.quantity)
        assertEquals("kilogramos", row.unit)
        assertEquals("Cereales", row.category.name)
        assertEquals(1, loaded.response.totalPages)
    }

    @Test
    fun `quantity sent as a bare JSON number decodes just as well`() = runTest {
        val stockApi = api { ok(onePage.replace("\"10.000\"", "10.000")) }

        val loaded = assertIs<FoodStockPageOutcome.Loaded>(
            stockApi.listStock("e-1", true, null, "name_asc", 1, 20),
        )
        assertEquals("10.000", loaded.response.items.single().quantity)
    }

    @Test
    fun `403 is establishment-access-denied, not a retry`() = runTest {
        val stockApi = api {
            problem(HttpStatusCode.Forbidden, "/errors/establishment-access-denied")
        }

        assertEquals(
            FoodStockPageOutcome.EstablishmentAccessDenied,
            stockApi.listStock("e-1", true, null, "name_asc", 1, 20),
        )
    }

    @Test
    fun `404 discriminates on the problem type`() = runTest {
        val known = api { problem(HttpStatusCode.NotFound, "/errors/establishment-not-found") }
        val unknown = api { problem(HttpStatusCode.NotFound, "/errors/who-knows") }

        assertEquals(
            FoodStockPageOutcome.EstablishmentNotFound,
            known.listStock("e-1", true, null, "name_asc", 1, 20),
        )
        // An unrecognized 404 is terminal, never retryable — the same URL can only 404 again.
        assertEquals(
            FoodStockPageOutcome.InvalidRequest,
            unknown.listStock("e-1", true, null, "name_asc", 1, 20),
        )
    }

    @Test
    fun `400 and 422 are terminal InvalidRequest, and 401 is Unauthorized`() = runTest {
        val badRequest = api { problem(HttpStatusCode.BadRequest, "/errors/bad-request") }
        val unprocessable = api { problem(HttpStatusCode.UnprocessableEntity, "/errors/validation") }
        val unauthorized = api { problem(HttpStatusCode.Unauthorized, "/errors/unauthorized") }

        assertEquals(
            FoodStockPageOutcome.InvalidRequest,
            badRequest.listStock("e-1", true, null, "name_asc", 1, 20),
        )
        assertEquals(
            FoodStockPageOutcome.InvalidRequest,
            unprocessable.listStock("e-1", true, null, "name_asc", 1, 20),
        )
        assertEquals(
            FoodStockPageOutcome.Unauthorized,
            unauthorized.listStock("e-1", true, null, "name_asc", 1, 20),
        )
    }

    @Test
    fun `a 5xx is retryable and does NOT throw — the authed client sets no expectSuccess`() = runTest {
        val stockApi = api {
            respond(
                "",
                HttpStatusCode.InternalServerError,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        assertIs<FoodStockPageOutcome.Retryable>(
            stockApi.listStock("e-1", true, null, "name_asc", 1, 20),
        )
    }

    @Test
    fun `a transport failure maps to Retryable with the cause`() = runTest {
        val stockApi = api { throw RuntimeException("no network") }

        val retryable = assertIs<FoodStockPageOutcome.Retryable>(
            stockApi.listStock("e-1", true, null, "name_asc", 1, 20),
        )
        assertTrue(retryable.cause != null)
    }
}
