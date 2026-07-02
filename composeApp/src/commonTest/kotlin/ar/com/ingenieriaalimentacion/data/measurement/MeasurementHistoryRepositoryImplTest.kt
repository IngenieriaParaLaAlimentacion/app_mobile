package ar.com.ingenieriaalimentacion.data.measurement

import ar.com.ingenieriaalimentacion.data.activity.ActivityApi
import ar.com.ingenieriaalimentacion.data.activity.local.ActivityItemType
import ar.com.ingenieriaalimentacion.data.activity.local.CachedActivityRow
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.fake.FakeActivityLocalStore
import ar.com.ingenieriaalimentacion.fake.FakeMeasurementLocalStore
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeasurementHistoryRepositoryImplTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private fun api(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ActivityApi {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        return ActivityApi(client)
    }

    private fun page(items: String, page: Int = 1, totalPages: Int = 1): String = """
        { "items": [$items], "total": 1, "page": $page, "page_size": 20, "total_pages": $totalPages }
    """.trimIndent()

    private fun remoteMeasurementItem(id: String): String = """
        {
            "type": "measurement",
            "occurred_at": "2026-06-01T12:00:00Z",
            "measurement": {
                "id": "$id",
                "measured_at": "2026-06-01T12:00:00Z",
                "weight_kg": 70.0,
                "height_cm": 170.0,
                "bmi": 24.2,
                "subject": {
                    "id": "subj-1", "dni": 111, "sex": "male", "name": "Remote", "last_name": "Server",
                    "birth_date": "1990-01-01"
                },
                "establishment": { "id": "est-1", "name": "Escuela", "city": "Ciudad", "province": "Provincia" },
                "alerts": []
            }
        }
    """.trimIndent()

    private fun respondPage(scope: MockRequestHandleScope, body: String) = scope.respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun queuedUpload(
        localId: String,
        state: SyncState = SyncState.Pending,
        updatedAt: Long = 0L,
        subjectName: String = "Local",
    ) = MeasurementUpload(
        localId = localId,
        subjectId = "s1",
        subjectName = subjectName,
        subjectLastName = "Queue",
        subjectDni = 42,
        establishmentId = "e1",
        establishmentName = "Escuela 1",
        weightKg = 70.0,
        heightCm = 172.0,
        measuredAt = "2026-06-01T10:00:00Z",
        syncState = state,
        errorMessage = null,
        attemptCount = 0,
        bmi = null,
        alerts = emptyList(),
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun cachedRow(id: String, occurredAtMillis: Long = 1L) = CachedActivityRow(
        id = id,
        occurredAtMillis = occurredAtMillis,
        payloadJson = testJson.encodeToString(
            MeasurementHistoryEntry(
                id = id,
                subjectName = "Cached",
                subjectLastName = "Row",
                subjectDni = 99,
                establishmentName = "Escuela",
                weightKg = 65.0,
                heightCm = 165.0,
                measuredAt = "2026-06-01T09:00:00Z",
                syncState = SyncState.Synced,
                errorMessage = null,
                bmi = 23.9,
                alerts = emptyList(),
                isLocal = false,
                createdAt = occurredAtMillis,
                updatedAt = occurredAtMillis,
            ),
        ),
    )

    @Test
    fun `queue's copy wins when the same id is also returned remotely on page 1`() = runTest {
        val queue = FakeMeasurementLocalStore().apply {
            insert(queuedUpload("shared-id", subjectName = "Local", updatedAt = 5L))
        }
        val cache = FakeActivityLocalStore()
        val activityApi = api { respondPage(this, page(remoteMeasurementItem("shared-id"))) }
        val repo = MeasurementHistoryRepositoryImpl(activityApi, cache, queue, testJson)

        val items = repo.loadPage(page = 1, pageSize = 20).getOrThrow().items

        assertEquals(1, items.count { it.id == "shared-id" })
        assertEquals("Local", items.single { it.id == "shared-id" }.subjectName)
    }

    @Test
    fun `a FAILED-only local row with no remote counterpart still appears on page 1`() = runTest {
        val queue = FakeMeasurementLocalStore().apply {
            insert(queuedUpload("failed-1", state = SyncState.Failed, updatedAt = 5L))
        }
        val cache = FakeActivityLocalStore()
        val activityApi = api { respondPage(this, page("")) }
        val repo = MeasurementHistoryRepositoryImpl(activityApi, cache, queue, testJson)

        val items = repo.loadPage(page = 1, pageSize = 20).getOrThrow().items

        assertEquals(listOf("failed-1"), items.map { it.id })
    }

    @Test
    fun `page 2 request never includes local queue rows`() = runTest {
        val queue = FakeMeasurementLocalStore().apply {
            insert(queuedUpload("local-1", updatedAt = 5L))
        }
        val cache = FakeActivityLocalStore()
        val activityApi = api {
            respondPage(this, page(remoteMeasurementItem("remote-2"), page = 2, totalPages = 2))
        }
        val repo = MeasurementHistoryRepositoryImpl(activityApi, cache, queue, testJson)

        val items = repo.loadPage(page = 2, pageSize = 20).getOrThrow().items

        assertEquals(listOf("remote-2"), items.map { it.id })
    }

    @Test
    fun `network failure on page 1 with a warm queue and cache falls back to a merged local page`() = runTest {
        val queue = FakeMeasurementLocalStore().apply {
            insert(queuedUpload("local-1", updatedAt = 5L))
        }
        val cache = FakeActivityLocalStore().apply {
            seed(ActivityItemType.MEASUREMENT, listOf(cachedRow("cached-1")))
        }
        val activityApi = api { respond("boom", HttpStatusCode.InternalServerError) }
        val repo = MeasurementHistoryRepositoryImpl(activityApi, cache, queue, testJson)

        val result = repo.loadPage(page = 1, pageSize = 20)

        val ids = result.getOrThrow().items.map { it.id }
        assertTrue(ids.containsAll(listOf("local-1", "cached-1")))
    }

    @Test
    fun `network failure on page 2 falls back to the cached page`() = runTest {
        val queue = FakeMeasurementLocalStore()
        val cache = FakeActivityLocalStore().apply {
            seed(
                ActivityItemType.MEASUREMENT,
                listOf(cachedRow("cached-1", occurredAtMillis = 2L), cachedRow("cached-2", occurredAtMillis = 1L)),
            )
        }
        val activityApi = api { respond("boom", HttpStatusCode.InternalServerError) }
        val repo = MeasurementHistoryRepositoryImpl(activityApi, cache, queue, testJson)

        // pageSize=1 so page 2 (offset 1) lands on the older cached row only.
        val result = repo.loadPage(page = 2, pageSize = 1)

        assertEquals(listOf("cached-2"), result.getOrThrow().items.map { it.id })
    }

    @Test
    fun `network failure with nothing local or cached propagates the failure`() = runTest {
        val queue = FakeMeasurementLocalStore()
        val cache = FakeActivityLocalStore()
        val activityApi = api { respond("boom", HttpStatusCode.InternalServerError) }
        val repo = MeasurementHistoryRepositoryImpl(activityApi, cache, queue, testJson)

        val result = repo.loadPage(page = 1, pageSize = 20)

        assertTrue(result.isFailure)
    }
}
