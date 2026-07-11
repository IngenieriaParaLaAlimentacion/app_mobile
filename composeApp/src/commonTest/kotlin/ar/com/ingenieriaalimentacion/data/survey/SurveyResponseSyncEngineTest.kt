package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.domain.model.subject.MAX_SYNC_ATTEMPTS
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import ar.com.ingenieriaalimentacion.fake.FakeSurveyResponseLocalStore
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

class SurveyResponseSyncEngineTest {

    private var currentTime = 1_000L
    private val now: () -> Long = { currentTime }
    private val store = FakeSurveyResponseLocalStore(now)
    private val json = Json { ignoreUnknownKeys = true }

    private var apiCalls = 0
    private var responder: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respondError(HttpStatusCode.InternalServerError) }

    private val api: SurveyResponseApi = run {
        val client = HttpClient(MockEngine { req -> apiCalls++; responder(req) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        SurveyResponseApi(client)
    }

    private val repo = SurveyResponseRepositoryImpl(
        api = api,
        local = store,
        idGenerator = object : IdGenerator { override fun newId() = "generated" },
        json = json,
        now = now,
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun successBody(id: String) = """
        {"id":"$id","survey_id":"sv-1","establishment_id":"est-1","submitted_by_id":"user-1",
         "answers":{"q1":"opt-1"},"event_at":"2026-01-01T00:00:00Z",
         "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}
    """.trimIndent()

    private fun row(
        localId: String,
        syncState: SyncState = SyncState.Pending,
        attemptCount: Int = 0,
        createdAt: Long = 0L,
        updatedAt: Long = 0L,
    ) = SurveyResponseUpload(
        localId = localId,
        surveyId = "sv-1",
        surveyName = "Encuesta",
        establishmentId = "est-1",
        establishmentName = "Norte",
        answersJson = """{"q1":"opt-1"}""",
        eventAt = "2026-01-01T00:00:00Z",
        syncState = syncState,
        canonicalId = null,
        errorMessage = null,
        attemptCount = attemptCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `Created maps to Synced and counts`() = runTest {
        store.insert(row("r1"))
        responder = { respond(successBody("r1"), HttpStatusCode.Created, jsonHeaders()) }

        val summary = repo.syncPending()

        assertEquals(SyncState.Synced, store.current("r1")?.syncState)
        assertEquals("r1", store.current("r1")?.canonicalId)
        assertEquals(1, summary.synced)
    }

    @Test
    fun `200 replay also lands Synced`() = runTest {
        store.insert(row("r1"))
        responder = { respond(successBody("r1"), HttpStatusCode.OK, jsonHeaders()) }

        repo.syncPending()

        assertEquals(SyncState.Synced, store.current("r1")?.syncState)
    }

    @Test
    fun `400 dead-letters immediately to Failed`() = runTest {
        store.insert(row("r1"))
        responder = { respondError(HttpStatusCode.BadRequest) }

        val summary = repo.syncPending()

        assertEquals(SyncState.Failed, store.current("r1")?.syncState)
        assertEquals(1, summary.failed)
    }

    @Test
    fun `422 dead-letters immediately to Failed`() = runTest {
        store.insert(row("r1"))
        responder = { respondError(HttpStatusCode.UnprocessableEntity) }

        repo.syncPending()

        assertEquals(SyncState.Failed, store.current("r1")?.syncState)
    }

    @Test
    fun `403 dead-letters immediately to Failed`() = runTest {
        store.insert(row("r1"))
        responder = { respondError(HttpStatusCode.Forbidden) }

        repo.syncPending()

        assertEquals(SyncState.Failed, store.current("r1")?.syncState)
    }

    @Test
    fun `404 dead-letters immediately to Failed`() = runTest {
        store.insert(row("r1"))
        responder = { respondError(HttpStatusCode.NotFound) }

        repo.syncPending()

        assertEquals(SyncState.Failed, store.current("r1")?.syncState)
    }

    @Test
    fun `network keeps row Pending and bumps attemptCount`() = runTest {
        store.insert(row("r1"))
        responder = { respondError(HttpStatusCode.InternalServerError) }

        val summary = repo.syncPending()

        val updated = store.current("r1")
        assertEquals(SyncState.Pending, updated?.syncState)
        assertEquals(1, updated?.attemptCount)
        assertEquals(1, summary.stillPending)
    }

    @Test
    fun `401 keeps row Pending and bumps attemptCount`() = runTest {
        store.insert(row("r1"))
        responder = { respondError(HttpStatusCode.Unauthorized) }

        repo.syncPending()

        assertEquals(SyncState.Pending, store.current("r1")?.syncState)
        assertEquals(1, store.current("r1")?.attemptCount)
    }

    @Test
    fun `at the attempt cap a transient failure dead-letters to Failed`() = runTest {
        store.insert(row("r1", attemptCount = MAX_SYNC_ATTEMPTS - 1, updatedAt = 0L))
        currentTime = 1_000_000L
        responder = { respondError(HttpStatusCode.InternalServerError) }

        val summary = repo.syncPending()

        assertEquals(SyncState.Failed, store.current("r1")?.syncState)
        assertEquals(1, summary.failed)
    }
}
