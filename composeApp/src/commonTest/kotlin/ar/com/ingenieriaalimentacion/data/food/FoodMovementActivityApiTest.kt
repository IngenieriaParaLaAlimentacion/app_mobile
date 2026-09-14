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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoodMovementActivityApiTest {

    private fun api(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): FoodMovementActivityApi {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        return FoodMovementActivityApi(client)
    }

    private fun respondPage(scope: MockRequestHandleScope, body: String) = scope.respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private val emptyPage = """
        { "items": [], "total": 0, "page": 1, "page_size": 20, "total_pages": 0 }
    """.trimIndent()

    /** `quantity` arrives as a JSON number here and as a string in the outflow page below — the two
     *  shapes `QuantitySerializer` must accept interchangeably. */
    private fun intakePage(quantity: String) = """
        {
          "items": [
            {
              "type": "food_intake",
              "occurred_at": "2026-04-01T00:00:00Z",
              "food_intake": {
                "id": "fi-1",
                "occurred_at": "2026-04-01T00:00:00Z",
                "establishment": { "id": "e1", "name": "Comedor Los Aromos", "city": "Córdoba", "province": "Córdoba" },
                "source": "manual",
                "comments": "Donación parroquia",
                "items": [
                  { "food": { "id": "f1", "name": "Arroz" }, "unit": "kilogramos", "quantity": $quantity }
                ]
              },
              "food_outflow": null
            }
          ],
          "total": 1, "page": 1, "page_size": 20, "total_pages": 1
        }
    """.trimIndent()

    @Test
    fun `list sends page and page_size`() = runTest {
        var captured: HttpRequestData? = null
        val movementApi = api {
            captured = it
            respondPage(this, emptyPage)
        }

        movementApi.list(page = 2, pageSize = 10)

        val params = requireNotNull(captured).url.parameters
        assertEquals("2", params["page"])
        assertEquals("10", params["page_size"])
    }

    @Test
    fun `list appends type twice rather than overwriting`() = runTest {
        var captured: HttpRequestData? = null
        val movementApi = api {
            captured = it
            respondPage(this, emptyPage)
        }

        movementApi.list(page = 1, pageSize = 20)

        val url = requireNotNull(captured).url
        // `getAll`, not `get`: `get` returns only the first value and would pass even if the second
        // were dropped — precisely the failure mode an older backend would then hide.
        assertEquals(listOf("food_intake", "food_outflow"), url.parameters.getAll("type"))
        // The literal wire form, so a future Ktor upgrade changing `parameter`'s append semantics
        // from multi-map to overwrite fails here instead of silently halving the feed.
        assertTrue(url.toString().contains("type=food_intake&type=food_outflow"))
    }

    @Test
    fun `decodes a full payload with quantity as a JSON number`() = runTest {
        val movementApi = api { respondPage(this, intakePage("0.750")) }

        val page = movementApi.list(page = 1, pageSize = 20)

        assertEquals(1, page.totalPages)
        val payload = requireNotNull(page.items.single().foodIntake)
        assertEquals("fi-1", payload.id)
        assertEquals("Comedor Los Aromos", payload.establishment.name)
        assertEquals("Donación parroquia", payload.comments)
        assertEquals("0.750", payload.items.single().quantity)
        assertEquals("kilogramos", payload.items.single().unit)
    }

    @Test
    fun `decodes quantity sent as a string just as well`() = runTest {
        val movementApi = api { respondPage(this, intakePage("\"0.750\"")) }

        val page = movementApi.list(page = 1, pageSize = 20)

        assertEquals("0.750", requireNotNull(page.items.single().foodIntake).items.single().quantity)
    }

    @Test
    fun `decodes an establishment with city and province absent`() = runTest {
        val body = """
            {
              "items": [
                {
                  "type": "food_outflow",
                  "occurred_at": "2026-04-01T00:00:00Z",
                  "food_outflow": {
                    "id": "fo-1",
                    "establishment": { "id": "e1", "name": "Comedor" },
                    "items": [ { "food": { "id": "f1", "name": "Arroz" }, "unit": "unidades", "quantity": 3 } ]
                  }
                }
              ],
              "total": 1, "page": 1, "page_size": 20, "total_pages": 1
            }
        """.trimIndent()
        val movementApi = api { respondPage(this, body) }

        val payload = requireNotNull(movementApi.list(1, 20).items.single().foodOutflow)

        assertNull(payload.establishment.city)
        assertNull(payload.establishment.province)
        assertNull(payload.comments)
    }

    @Test
    fun `a non-2xx status throws — the authed client does not set expectSuccess`() = runTest {
        val movementApi = api {
            respond(
                content = "",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        assertFailsWith<IllegalStateException> { movementApi.list(1, 20) }
    }
}
