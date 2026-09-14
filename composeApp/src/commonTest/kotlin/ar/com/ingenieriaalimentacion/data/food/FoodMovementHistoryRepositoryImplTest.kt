package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.activity.local.ActivityItemType
import ar.com.ingenieriaalimentacion.data.activity.local.CachedActivityRow
import ar.com.ingenieriaalimentacion.fake.FakeActivityLocalStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoodMovementHistoryRepositoryImplTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val cache = FakeActivityLocalStore()

    /** [requests] records every request the engine saw — the fan-out guard reads it. */
    private val requests = mutableListOf<HttpRequestData>()

    /** `null` means the single request fails, exercising the offline-fallback branches. */
    private fun repository(body: String?): FoodMovementHistoryRepositoryImpl {
        val engine = MockEngine { request ->
            requests += request
            if (body == null) {
                respondError(HttpStatusCode.InternalServerError)
            } else {
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            defaultRequest { url("https://api.example.com/") }
        }
        return FoodMovementHistoryRepositoryImpl(FoodMovementActivityApi(client), cache, json)
    }

    private fun movementJson(direction: String, id: String, occurredAt: String) = """
        {
          "type": "$direction",
          "occurred_at": "$occurredAt",
          "$direction": {
            "id": "$id",
            "establishment": { "id": "e1", "name": "Comedor" },
            "items": [ { "food": { "id": "f1", "name": "Arroz" }, "unit": "kilogramos", "quantity": 1 } ]
          }
        }
    """.trimIndent()

    private fun page(totalPages: Int, page: Int = 1, vararg items: String) = """
        { "items": [${items.joinToString(",")}], "total": ${items.size}, "page": $page,
          "page_size": 20, "total_pages": $totalPages }
    """.trimIndent()

    private val intake1 = movementJson("food_intake", "i1", "2026-04-03T00:00:00Z")
    private val intake2 = movementJson("food_intake", "i2", "2026-04-01T00:00:00Z")
    private val outflow1 = movementJson("food_outflow", "o1", "2026-04-02T00:00:00Z")

    @Test
    fun `getPage issues exactly ONE http request`() = runTest {
        val repo = repository(page(1, 1, intake1, outflow1))

        repo.getPage(page = 1, pageSize = 20).getOrThrow()

        // The guard against the fan-out creeping back: two independently paginated sources driven
        // by one page counter is exactly the design this feature removed.
        assertEquals(1, requests.size)
    }

    @Test
    fun `the request carries type twice, ingresos and salidas`() = runTest {
        val repo = repository(page(1, 1, intake1))

        repo.getPage(page = 2, pageSize = 10).getOrThrow()

        val params = requests.single().url.parameters
        assertEquals(listOf("food_intake", "food_outflow"), params.getAll("type"))
        assertEquals("2", params["page"])
        assertEquals("10", params["page_size"])
    }

    @Test
    fun `one merged response is sorted date-descending and cached under two distinct types`() = runTest {
        val repo = repository(page(1, 1, intake1, outflow1, intake2))

        val result = repo.getPage(page = 1, pageSize = 20).getOrThrow()

        assertEquals(listOf("i1", "o1", "i2"), result.items.map { it.id })
        assertEquals(
            listOf("in:i1", "in:i2"),
            cache.getPage(ActivityItemType.FOOD_INTAKE, 20, 0).map { it.id },
        )
        assertEquals(
            listOf("out:o1"),
            cache.getPage(ActivityItemType.FOOD_OUTFLOW, 20, 0).map { it.id },
        )
    }

    @Test
    fun `an intake and an outflow sharing an id both survive thanks to the cache id prefix`() = runTest {
        val repo = repository(
            page(
                1,
                1,
                movementJson("food_intake", "same", "2026-04-03T00:00:00Z"),
                movementJson("food_outflow", "same", "2026-04-02T00:00:00Z"),
            ),
        )

        repo.getPage(page = 1, pageSize = 20).getOrThrow()

        assertEquals(listOf("in:same"), cache.getPage(ActivityItemType.FOOD_INTAKE, 20, 0).map { it.id })
        assertEquals(listOf("out:same"), cache.getPage(ActivityItemType.FOOD_OUTFLOW, 20, 0).map { it.id })
    }

    @Test
    fun `a page with rows in only one direction still writes both cache keys`() = runTest {
        cache.seed(
            ActivityItemType.FOOD_OUTFLOW,
            listOf(CachedActivityRow(id = "out:seeded", occurredAtMillis = 1L, payloadJson = "{}")),
        )
        val repo = repository(page(1, 1, intake1))

        repo.getPage(page = 1, pageSize = 20).getOrThrow()

        assertEquals(listOf("in:i1"), cache.getPage(ActivityItemType.FOOD_INTAKE, 20, 0).map { it.id })
        // The empty-list upsert is a no-op: it must not clear the direction that contributed nothing.
        assertEquals(listOf("out:seeded"), cache.getPage(ActivityItemType.FOOD_OUTFLOW, 20, 0).map { it.id })
    }

    @Test
    fun `an item whose type the client does not recognise is skipped, not fatal`() = runTest {
        val unknown = """
            {
              "type": "food_transfer",
              "occurred_at": "2026-04-04T00:00:00Z",
              "food_transfer": { "id": "t1" }
            }
        """.trimIndent()
        val repo = repository(page(1, 1, unknown, intake1))

        val result = repo.getPage(page = 1, pageSize = 20).getOrThrow()

        assertEquals(listOf("i1"), result.items.map { it.id })
    }

    @Test
    fun `hasMore comes from total_pages, not from a full page of items`() = runTest {
        // A page whose items fill it exactly but which the envelope declares final.
        val repo = repository(page(1, 1, intake1, outflow1, intake2))

        assertEquals(false, repo.getPage(page = 1, pageSize = 3).getOrThrow().hasMore)
    }

    @Test
    fun `hasMore is true while the envelope declares further pages`() = runTest {
        val repo = repository(page(3, 1, intake1))

        assertEquals(true, repo.getPage(page = 1, pageSize = 20).getOrThrow().hasMore)
    }

    @Test
    fun `an empty page is neither an error nor paginated further`() = runTest {
        val repo = repository(page(0, 1))

        val result = repo.getPage(page = 1, pageSize = 20).getOrThrow()

        assertEquals(emptyList(), result.items)
        assertEquals(false, result.hasMore)
    }

    @Test
    fun `a failed request with a non-empty cache serves the cached page`() = runTest {
        val seeded = repository(page(1, 1, intake1, outflow1))
        seeded.getPage(page = 1, pageSize = 20).getOrThrow()

        val offline = repository(null)
        val result = offline.getPage(page = 1, pageSize = 20).getOrThrow()

        assertEquals(listOf("i1", "o1"), result.items.map { it.id })
    }

    @Test
    fun `an offline page is terminal so the page counter cannot advance off the cache`() = runTest {
        val seeded = repository(page(5, 1, intake1, intake2, outflow1))
        seeded.getPage(page = 1, pageSize = 2).getOrThrow()

        val offline = repository(null)
        val result = offline.getPage(page = 1, pageSize = 2).getOrThrow()

        assertTrue(result.items.isNotEmpty())
        // The cache is a recency-capped LRU, not a paginated mirror of the server.
        assertEquals(false, result.hasMore)
    }

    @Test
    fun `a failed request with an empty cache is the only failure path`() = runTest {
        val repo = repository(null)

        assertTrue(repo.getPage(page = 1, pageSize = 20).isFailure)
    }

    @Test
    fun `observeLocalMerged unions both cache types, sorts globally and skips undecodable rows`() = runTest {
        val repo = repository(page(1, 1, intake1, outflow1, intake2))
        repo.getPage(page = 1, pageSize = 20).getOrThrow()
        cache.upsertPage(
            ActivityItemType.FOOD_INTAKE,
            listOf(CachedActivityRow(id = "in:broken", occurredAtMillis = 9_999L, payloadJson = "not json")),
        )

        val merged = repo.observeLocalMerged().first()

        assertEquals(listOf("i1", "o1", "i2"), merged.map { it.id })
    }
}
