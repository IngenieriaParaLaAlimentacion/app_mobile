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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoodMovementHistoryRepositoryImplTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val cache = FakeActivityLocalStore()

    /** `null` for a direction means that call fails, exercising the partial-failure branches. */
    private fun repository(
        intakes: String?,
        outflows: String?,
    ): FoodMovementHistoryRepositoryImpl {
        val engine = MockEngine { request ->
            val body = if (request.url.parameters["type"] == "food_intake") intakes else outflows
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
    fun `both sources succeed - merged date-descending and cached under two distinct types`() = runTest {
        val repo = repository(page(1, 1, intake1, intake2), page(1, 1, outflow1))

        val result = repo.getPage(page = 1, pageSize = 20).getOrThrow()

        assertEquals(listOf("i1", "o1", "i2"), result.items.map { it.id })
        assertEquals(false, result.isStale)
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
            page(1, 1, movementJson("food_intake", "same", "2026-04-03T00:00:00Z")),
            page(1, 1, movementJson("food_outflow", "same", "2026-04-02T00:00:00Z")),
        )

        repo.getPage(page = 1, pageSize = 20).getOrThrow()

        assertEquals(listOf("in:same"), cache.getPage(ActivityItemType.FOOD_INTAKE, 20, 0).map { it.id })
        assertEquals(listOf("out:same"), cache.getPage(ActivityItemType.FOOD_OUTFLOW, 20, 0).map { it.id })
    }

    @Test
    fun `the watermark is the oldest row of the only source that still has pages`() = runTest {
        // Intakes have a page 2; outflows are exhausted, so only intakes constrain the cut.
        val repo = repository(page(2, 1, intake1, intake2), page(1, 1, outflow1))

        val result = repo.getPage(page = 1, pageSize = 20).getOrThrow()

        assertEquals(1775001600000L, result.cutoffMillis) // i2, 2026-04-01
        assertEquals(true, result.hasMore)
    }

    @Test
    fun `both sources exhausted hides nothing`() = runTest {
        val repo = repository(page(1, 1, intake1, intake2), page(1, 1, outflow1))

        val result = repo.getPage(page = 1, pageSize = 20).getOrThrow()

        assertNull(result.cutoffMillis)
        assertEquals(false, result.hasMore)
    }

    @Test
    fun `no rows at all leaves the watermark null`() = runTest {
        val repo = repository(page(0, 1), page(0, 1))

        val result = repo.getPage(page = 1, pageSize = 20).getOrThrow()

        assertNull(result.cutoffMillis)
        assertEquals(false, result.hasMore)
        assertEquals(emptyList(), result.items)
    }

    @Test
    fun `hasMore comes from total_pages, not from a full page of items`() = runTest {
        // A page whose items fill it exactly but which the envelope declares final.
        val repo = repository(page(1, 1, intake1, intake2), page(1, 1, outflow1))

        assertEquals(false, repo.getPage(page = 1, pageSize = 3).getOrThrow().hasMore)
    }

    @Test
    fun `one source failing is a partial success that does not constrain the watermark`() = runTest {
        val repo = repository(intakes = null, outflows = page(2, 1, outflow1))

        val result = repo.getPage(page = 1, pageSize = 20).getOrThrow()

        assertEquals(listOf("o1"), result.items.map { it.id })
        assertTrue(result.isStale)
        // Only outflows constrain: intakes failed and will contribute nothing this round.
        assertEquals(1775088000000L, result.cutoffMillis)
    }

    @Test
    fun `both sources failing with a non-empty cache serves the cached page`() = runTest {
        val seeded = repository(page(1, 1, intake1), page(1, 1, outflow1))
        seeded.getPage(page = 1, pageSize = 20).getOrThrow()

        val offline = repository(intakes = null, outflows = null)
        val result = offline.getPage(page = 1, pageSize = 20).getOrThrow()

        assertEquals(listOf("i1", "o1"), result.items.map { it.id })
        assertNull(result.cutoffMillis)
        assertTrue(result.isStale)
    }

    @Test
    fun `both sources failing with an empty cache is the only failure path`() = runTest {
        val repo = repository(intakes = null, outflows = null)

        assertTrue(repo.getPage(page = 1, pageSize = 20).isFailure)
    }

    @Test
    fun `observeLocalMerged unions both cache types, sorts globally and skips undecodable rows`() = runTest {
        val repo = repository(page(1, 1, intake1, intake2), page(1, 1, outflow1))
        repo.getPage(page = 1, pageSize = 20).getOrThrow()
        cache.upsertPage(
            ActivityItemType.FOOD_INTAKE,
            listOf(CachedActivityRow(id = "in:broken", occurredAtMillis = 9_999L, payloadJson = "not json")),
        )

        val merged = repo.observeLocalMerged().first()

        assertEquals(listOf("i1", "o1", "i2"), merged.map { it.id })
    }
}
