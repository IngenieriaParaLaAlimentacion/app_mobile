package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.data.activity.ActivityApi
import ar.com.ingenieriaalimentacion.data.activity.local.ActivityItemType
import ar.com.ingenieriaalimentacion.data.activity.local.CachedActivityRow
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.fake.FakeActivityLocalStore
import ar.com.ingenieriaalimentacion.fake.FakeSubjectLocalStore
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SubjectHistoryRepositoryImplTest {

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

    private fun remoteSubjectItem(id: String, dni: Int, name: String, lastName: String): String = """
        {
            "type": "subject",
            "occurred_at": "2026-06-01T12:00:00Z",
            "subject": { "id": "$id", "dni": $dni, "name": "$name", "last_name": "$lastName", "birth_date":"1990-01-01","sex": "male" }
        }
    """.trimIndent()

    private fun respondPage(scope: MockRequestHandleScope, body: String) = scope.respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun queuedUpload(
        localId: String,
        dni: Int = 111,
        name: String = "Local",
        lastName: String = "Queue",
        state: SyncState = SyncState.Pending,
        updatedAt: Long = 0L,
    ) = SubjectUpload(
        localId = localId,
        dni = dni,
        sex = Sex.Male,
        name = name,
        lastName = lastName,
        birthDate = LocalDate(2000, 1, 1),
        isRural = false,
        isIndigenous = false,
        syncState = state,
        canonicalId = null,
        errorMessage = null,
        attemptCount = 0,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun cachedRow(
        id: String,
        occurredAtMillis: Long = 1L,
        dni: Int = 222,
        name: String = "Cached",
        lastName: String = "Row",
    ) = CachedActivityRow(
        id = id,
        occurredAtMillis = occurredAtMillis,
        payloadJson = testJson.encodeToString(
            SubjectHistoryEntry(
                id = id,
                dni = dni,
                sex = Sex.Male,
                name = name,
                lastName = lastName,
                syncState = SyncState.Synced,
                canonicalId = null,
                errorMessage = null,
                isLocal = false,
                createdAt = occurredAtMillis,
                updatedAt = occurredAtMillis,
            ),
        ),
    )

    @Test
    fun `queue's copy wins when the same id is also returned remotely on page 1`() = runTest {
        val queue = FakeSubjectLocalStore().apply {
            insert(queuedUpload("shared-id", name = "Local", updatedAt = 5L))
        }
        val cache = FakeActivityLocalStore()
        val activityApi = api { respondPage(this, page(remoteSubjectItem("shared-id", 111, "Remote", "Server"))) }
        val repo = SubjectHistoryRepositoryImpl(activityApi, cache, queue, testJson)

        val items = repo.loadPage(page = 1, pageSize = 20).getOrThrow().items

        assertEquals(1, items.count { it.id == "shared-id" })
        assertEquals("Local", items.single { it.id == "shared-id" }.name)
    }

    @Test
    fun `a FAILED-only local row with no remote counterpart still appears on page 1`() = runTest {
        val queue = FakeSubjectLocalStore().apply {
            insert(queuedUpload("failed-1", state = SyncState.Failed, updatedAt = 5L))
        }
        val cache = FakeActivityLocalStore()
        val activityApi = api { respondPage(this, page("")) }
        val repo = SubjectHistoryRepositoryImpl(activityApi, cache, queue, testJson)

        val items = repo.loadPage(page = 1, pageSize = 20).getOrThrow().items

        assertEquals(listOf("failed-1"), items.map { it.id })
    }

    @Test
    fun `page 2 request never includes local queue rows`() = runTest {
        val queue = FakeSubjectLocalStore().apply {
            insert(queuedUpload("local-1", updatedAt = 5L))
        }
        val cache = FakeActivityLocalStore()
        val activityApi = api {
            respondPage(this, page(remoteSubjectItem("remote-2", 222, "Remote", "Two"), page = 2, totalPages = 2))
        }
        val repo = SubjectHistoryRepositoryImpl(activityApi, cache, queue, testJson)

        val items = repo.loadPage(page = 2, pageSize = 20).getOrThrow().items

        assertEquals(listOf("remote-2"), items.map { it.id })
    }

    @Test
    fun `network failure on page 1 with a warm queue and cache falls back to a merged local page`() = runTest {
        val queue = FakeSubjectLocalStore().apply {
            insert(queuedUpload("local-1", updatedAt = 5L))
        }
        val cache = FakeActivityLocalStore().apply {
            seed(ActivityItemType.SUBJECT, listOf(cachedRow("cached-1")))
        }
        val activityApi = api { respond("boom", HttpStatusCode.InternalServerError) }
        val repo = SubjectHistoryRepositoryImpl(activityApi, cache, queue, testJson)

        val result = repo.loadPage(page = 1, pageSize = 20)

        val ids = result.getOrThrow().items.map { it.id }
        assertTrue(ids.containsAll(listOf("local-1", "cached-1")))
    }

    @Test
    fun `network failure on page 2 falls back to the cached page`() = runTest {
        val queue = FakeSubjectLocalStore()
        val cache = FakeActivityLocalStore().apply {
            seed(
                ActivityItemType.SUBJECT,
                listOf(cachedRow("cached-1", occurredAtMillis = 2L), cachedRow("cached-2", occurredAtMillis = 1L)),
            )
        }
        val activityApi = api { respond("boom", HttpStatusCode.InternalServerError) }
        val repo = SubjectHistoryRepositoryImpl(activityApi, cache, queue, testJson)

        // pageSize=1 so page 2 (offset 1) lands on the older cached row only.
        val result = repo.loadPage(page = 2, pageSize = 1)

        assertEquals(listOf("cached-2"), result.getOrThrow().items.map { it.id })
    }

    @Test
    fun `network failure with nothing local or cached propagates the failure`() = runTest {
        val queue = FakeSubjectLocalStore()
        val cache = FakeActivityLocalStore()
        val activityApi = api { respond("boom", HttpStatusCode.InternalServerError) }
        val repo = SubjectHistoryRepositoryImpl(activityApi, cache, queue, testJson)

        val result = repo.loadPage(page = 1, pageSize = 20)

        assertTrue(result.isFailure)
    }
}
