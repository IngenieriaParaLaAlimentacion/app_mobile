package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyAnswerValue
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditUpload
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseTargetRef
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import ar.com.ingenieriaalimentacion.fake.FakeSurveyResponseEditLocalStore
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
import kotlin.test.assertNotEquals

class SurveyResponseEditSyncEngineTest {

    private var currentTime = 1_000L
    private val now: () -> Long = { currentTime }
    private val editStore = FakeSurveyResponseEditLocalStore(now)
    private val responseStore = FakeSurveyResponseLocalStore(now)
    private val json = Json { ignoreUnknownKeys = true }

    private var responder: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respondError(HttpStatusCode.InternalServerError) }

    private val api: SurveyResponseApi = run {
        val client = HttpClient(MockEngine { req -> responder(req) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        SurveyResponseApi(client)
    }

    private var idCounter = 0
    private val repo = SurveyResponseEditRepositoryImpl(
        api = api,
        local = editStore,
        responses = responseStore,
        idGenerator = object : IdGenerator { override fun newId() = "generated-edit-${idCounter++}" },
        json = json,
        now = now,
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun successBody(responseId: String) = """
        {"id":"$responseId","survey_id":"sv-1","establishment_id":"est-1","submitted_by_id":"user-1",
         "answers":{"q1":"opt-2"},"event_at":"2026-01-01T01:00:00Z",
         "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T01:00:00Z"}
    """.trimIndent()

    private fun responseRow(localId: String, syncState: SyncState) = SurveyResponseUpload(
        localId = localId,
        surveyId = "sv-1",
        surveyName = "Encuesta",
        target = ResponseTargetRef.Establishment("est-1", "Norte"),
        answersJson = """{"q1":"opt-1"}""",
        eventAt = "2026-01-01T00:00:00Z",
        syncState = syncState,
        canonicalId = if (syncState == SyncState.Synced) localId else null,
        errorMessage = null,
        conflict = null,
        attemptCount = 0,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun editRow(editId: String, responseId: String, syncState: SyncState = SyncState.Pending) =
        SurveyResponseEditUpload(
            editId = editId,
            responseId = responseId,
            surveyId = "s1",
            surveyName = "Encuesta",
            establishmentName = "Norte",
            answersJson = """{"q1":"opt-2"}""",
            eventAt = "2026-01-01T01:00:00Z",
            syncState = syncState,
            errorMessage = null,
            attemptCount = 0,
            createdAt = 0L,
            updatedAt = 0L,
        )

    @Test
    fun `edit held Pending while parent response is still Pending`() = runTest {
        responseStore.insert(responseRow("r1", SyncState.Pending))
        editStore.insert(editRow("e1", "r1"))

        val summary = repo.syncPending()

        assertEquals(SyncState.Pending, editStore.current("e1")?.syncState)
        assertEquals(1, summary.stillPending)
    }

    @Test
    fun `edit held Pending while parent response is Syncing`() = runTest {
        responseStore.insert(responseRow("r1", SyncState.Syncing))
        editStore.insert(editRow("e1", "r1"))

        repo.syncPending()

        assertEquals(SyncState.Pending, editStore.current("e1")?.syncState)
    }

    @Test
    fun `parent Failed dead-letters the edit immediately`() = runTest {
        responseStore.insert(responseRow("r1", SyncState.Failed))
        editStore.insert(editRow("e1", "r1"))

        val summary = repo.syncPending()

        assertEquals(SyncState.Failed, editStore.current("e1")?.syncState)
        assertEquals(1, summary.failed)
    }

    @Test
    fun `parent Synced allows the edit to be attempted and applied`() = runTest {
        responseStore.insert(responseRow("r1", SyncState.Synced))
        editStore.insert(editRow("e1", "r1"))
        responder = { respond(successBody("r1"), HttpStatusCode.OK, jsonHeaders()) }

        val summary = repo.syncPending()

        assertEquals(SyncState.Synced, editStore.current("e1")?.syncState)
        assertEquals(1, summary.synced)
    }

    @Test
    fun `no local parent row for a cross-device response allows the edit to be attempted`() = runTest {
        // No responseStore.insert — this device never submitted the original response.
        editStore.insert(editRow("e1", "r-remote"))
        responder = { respond(successBody("r-remote"), HttpStatusCode.OK, jsonHeaders()) }

        val summary = repo.syncPending()

        assertEquals(SyncState.Synced, editStore.current("e1")?.syncState)
        assertEquals(1, summary.synced)
    }

    @Test
    fun `400 dead-letters the edit immediately`() = runTest {
        responseStore.insert(responseRow("r1", SyncState.Synced))
        editStore.insert(editRow("e1", "r1"))
        responder = { respondError(HttpStatusCode.BadRequest) }

        repo.syncPending()

        assertEquals(SyncState.Failed, editStore.current("e1")?.syncState)
    }

    @Test
    fun `403 dead-letters the edit immediately`() = runTest {
        responseStore.insert(responseRow("r1", SyncState.Synced))
        editStore.insert(editRow("e1", "r1"))
        responder = { respondError(HttpStatusCode.Forbidden) }

        repo.syncPending()

        assertEquals(SyncState.Failed, editStore.current("e1")?.syncState)
    }

    @Test
    fun `404 dead-letters the edit immediately`() = runTest {
        responseStore.insert(responseRow("r1", SyncState.Synced))
        editStore.insert(editRow("e1", "r1"))
        responder = { respondError(HttpStatusCode.NotFound) }

        repo.syncPending()

        assertEquals(SyncState.Failed, editStore.current("e1")?.syncState)
    }

    @Test
    fun `network keeps the edit Pending and bumps attemptCount`() = runTest {
        responseStore.insert(responseRow("r1", SyncState.Synced))
        editStore.insert(editRow("e1", "r1"))
        responder = { respondError(HttpStatusCode.InternalServerError) }

        val summary = repo.syncPending()

        assertEquals(SyncState.Pending, editStore.current("e1")?.syncState)
        assertEquals(1, editStore.current("e1")?.attemptCount)
        assertEquals(1, summary.stillPending)
    }

    @Test
    fun `enqueueEdit coalesces into the still-Pending edit for the same response`() = runTest {
        repo.enqueueEdit(
            responseId = "r1",
            surveyId = "s1",
            surveyName = "Encuesta",
            establishmentName = "Norte",
            answers = mapOf("q1" to SurveyAnswerValue.SingleChoice("opt-2")),
            eventAtMs = 1_000L,
        )
        val firstEditId = editStore.snapshot().single().editId

        repo.enqueueEdit(
            responseId = "r1",
            surveyId = "s1",
            surveyName = "Encuesta",
            establishmentName = "Norte",
            answers = mapOf("q1" to SurveyAnswerValue.SingleChoice("opt-3")),
            eventAtMs = 2_000L,
        )

        assertEquals(1, editStore.snapshot().size)
        assertEquals(firstEditId, editStore.snapshot().single().editId)
        assertEquals("""{"q1":"opt-3"}""", editStore.snapshot().single().answersJson)
    }

    @Test
    fun `enqueueEdit mints a fresh id once the previous edit is no longer Pending`() = runTest {
        repo.enqueueEdit(
            responseId = "r1",
            surveyId = "s1",
            surveyName = "Encuesta",
            establishmentName = "Norte",
            answers = mapOf("q1" to SurveyAnswerValue.SingleChoice("opt-2")),
            eventAtMs = 1_000L,
        )
        val firstEditId = editStore.snapshot().single().editId
        editStore.markSynced(firstEditId) // simulate the first edit having already landed

        repo.enqueueEdit(
            responseId = "r1",
            surveyId = "s1",
            surveyName = "Encuesta",
            establishmentName = "Norte",
            answers = mapOf("q1" to SurveyAnswerValue.SingleChoice("opt-4")),
            eventAtMs = 3_000L,
        )

        assertEquals(2, editStore.snapshot().size)
        assertNotEquals(firstEditId, editStore.snapshot().last().editId)
    }
}
