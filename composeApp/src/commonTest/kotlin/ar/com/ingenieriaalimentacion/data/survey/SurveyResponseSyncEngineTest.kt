package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.data.survey.dto.SURVEY_RESPONSE_CONFLICT_TYPE
import ar.com.ingenieriaalimentacion.domain.model.subject.MAX_SYNC_ATTEMPTS
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.MISSING_REQUIRED_RESPONSE_NUMBER_MESSAGE
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseConflict
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseTargetRef
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import ar.com.ingenieriaalimentacion.fake.FakeSubjectLocalStore
import ar.com.ingenieriaalimentacion.fake.FakeSurveyResponseLocalStore
import ar.com.ingenieriaalimentacion.fake.FakeSurveyRepository
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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SurveyResponseSyncEngineTest {

    private var currentTime = 1_000L
    private val now: () -> Long = { currentTime }
    private val store = FakeSurveyResponseLocalStore(now)
    private val json = Json { ignoreUnknownKeys = true }

    private var apiCalls = 0
    private var responder: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respondError(HttpStatusCode.InternalServerError) }

    /** Captured request bodies/paths, so the target-shape assertions can look at the real wire. */
    private val sentBodies = mutableListOf<String>()
    private val sentPaths = mutableListOf<String>()

    private val api: SurveyResponseApi = run {
        val client = HttpClient(
            MockEngine { req ->
                apiCalls++
                sentPaths += req.url.encodedPath
                sentBodies += (req.body as? TextContent)?.text.orEmpty()
                responder(req)
            },
        ) {
            // Mirrors the app's shared Json (encodeDefaults = true) — otherwise the
            // "no target keys on an anonymous body" assertion would pass vacuously.
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
            defaultRequest { url("https://api.example.com/") }
        }
        SurveyResponseApi(client)
    }

    private val subjects = FakeSubjectLocalStore(now)
    private val surveys = FakeSurveyRepository().apply {
        detailResult = Result.success(
            SurveyDetail("sv-1", "Encuesta", SurveyResponseTarget.Establishment, true, emptyList()),
        )
    }

    private val repo = SurveyResponseRepositoryImpl(
        api = api,
        local = store,
        subjects = subjects,
        surveys = surveys,
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
        target: ResponseTargetRef = ResponseTargetRef.Establishment("est-1", "Norte"),
        responseNumber: Int? = 7,
        errorMessage: String? = null,
    ) = SurveyResponseUpload(
        localId = localId,
        surveyId = "sv-1",
        surveyName = "Encuesta",
        target = target,
        answersJson = """{"q1":"opt-1"}""",
        eventAt = "2026-01-01T00:00:00Z",
        syncState = syncState,
        canonicalId = null,
        errorMessage = errorMessage,
        conflict = null,
        attemptCount = attemptCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        responseNumber = responseNumber,
    )

    private fun setNumberPolicy(required: Boolean?) {
        surveys.detailResult = Result.success(
            SurveyDetail(
                "sv-1",
                "Encuesta",
                SurveyResponseTarget.Establishment,
                true,
                emptyList(),
                required,
            ),
        )
    }

    @Test
    fun `optional numberless response omits response number and syncs`() = runTest {
        setNumberPolicy(false)
        store.insert(row("r1", responseNumber = null))
        responder = { respond(successBody("r1"), HttpStatusCode.Created, jsonHeaders()) }

        repo.syncPending()

        assertEquals(SyncState.Synced, store.current("r1")?.syncState)
        assertFalse(sentBodies.single().contains("response_number"))
    }

    @Test
    fun `required numberless response stays failed without posting`() = runTest {
        setNumberPolicy(true)
        store.insert(row("r1", responseNumber = null))

        repo.syncPending()

        assertEquals(SyncState.Failed, store.current("r1")?.syncState)
        assertEquals(MISSING_REQUIRED_RESPONSE_NUMBER_MESSAGE, store.current("r1")?.errorMessage)
        assertEquals(0, apiCalls)
    }

    @Test
    fun `legacy missing-number failure is recovered only for optional policy`() = runTest {
        setNumberPolicy(null)
        store.insert(
            row(
                "r1",
                syncState = SyncState.Failed,
                responseNumber = null,
                errorMessage = MISSING_REQUIRED_RESPONSE_NUMBER_MESSAGE,
            ),
        )
        responder = { respond(successBody("r1"), HttpStatusCode.Created, jsonHeaders()) }

        repo.syncPending()

        assertEquals(SyncState.Synced, store.current("r1")?.syncState)
    }

    @Test
    fun `policy fetch failure preserves numberless row for later retry`() = runTest {
        surveys.detailResult = Result.failure(IllegalStateException("offline"))
        store.insert(row("r1", responseNumber = null))

        repo.syncPending()

        assertEquals(SyncState.Pending, store.current("r1")?.syncState)
        assertEquals(0, apiCalls)
    }

    @Test
    fun `unrelated terminal failure is not selected for policy recovery`() = runTest {
        setNumberPolicy(false)
        store.insert(
            row(
                "r1",
                syncState = SyncState.Failed,
                responseNumber = null,
                errorMessage = "Las respuestas no son válidas.",
            ),
        )

        repo.syncPending()

        assertEquals("Las respuestas no son válidas.", store.current("r1")?.errorMessage)
        assertEquals(0, apiCalls)
    }

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

    // ---- target shapes on the wire ----------------------------------------------------------

    private fun subjectRow(localId: String, subjectId: String) =
        row(localId, target = ResponseTargetRef.Subject(subjectId, "Ana García"))

    @Test
    fun `the POST goes to the survey-nested route`() = runTest {
        store.insert(row("r1"))
        responder = { respond(successBody("r1"), HttpStatusCode.Created, jsonHeaders()) }

        repo.syncPending()

        assertEquals("/api/v1/surveys/sv-1/responses", sentPaths.single())
    }

    @Test
    fun `an establishment target sends establishment_id and no subject_id`() = runTest {
        store.insert(row("r1"))
        responder = { respond(successBody("r1"), HttpStatusCode.Created, jsonHeaders()) }

        repo.syncPending()

        val body = sentBodies.single()
        assertTrue(body.contains("\"establishment_id\":\"est-1\""), body)
        assertFalse(body.contains("subject_id"), body)
    }

    @Test
    fun `a subject target sends subject_id and no establishment_id`() = runTest {
        store.insert(subjectRow("r1", "subj-1"))
        responder = { respond(successBody("r1"), HttpStatusCode.Created, jsonHeaders()) }

        repo.syncPending()

        val body = sentBodies.single()
        assertTrue(body.contains("\"subject_id\":\"subj-1\""), body)
        assertFalse(body.contains("establishment_id"), body)
    }

    @Test
    fun `an anonymous body carries neither target key`() = runTest {
        store.insert(row("r1", target = ResponseTargetRef.Anonymous))
        responder = { respond(successBody("r1"), HttpStatusCode.Created, jsonHeaders()) }

        repo.syncPending()

        // Guards the @EncodeDefault(NEVER) decision: the shared Json would otherwise emit
        // "subject_id": null and trip the server's "nothing may be sent to an anonymous survey".
        val body = sentBodies.single()
        assertFalse(body.contains("subject_id"), body)
        assertFalse(body.contains("establishment_id"), body)
    }

    // ---- 409 classification ------------------------------------------------------------------

    private fun conflictBody(type: String = SURVEY_RESPONSE_CONFLICT_TYPE) = """
        {"type":"$type","title":"Survey response already exists","status":409,
         "detail":"This survey has already been answered for that target.",
         "instance":"/api/v1/surveys/sv-1/responses",
         "existing_response":{"id":"0f7c","submitted_by":{"id":"3ab1","name":"Ana"},
          "event_at":"2026-08-12T14:03:00Z"}}
    """.trimIndent()

    @Test
    fun `409 with the survey-response type becomes a terminal conflict row`() = runTest {
        store.insert(subjectRow("r1", "subj-1"))
        responder = { respond(conflictBody(), HttpStatusCode.Conflict, jsonHeaders()) }

        val summary = repo.syncPending()

        val stored = checkNotNull(store.current("r1"))
        assertEquals(SyncState.Failed, stored.syncState)
        assertEquals(ResponseConflict("Ana", "2026-08-12T14:03:00Z"), stored.conflict)
        assertEquals(1, summary.failed)

        // And no later sweep ever touches it again.
        val callsAfterConflict = apiCalls
        currentTime = 10_000_000L
        repo.syncPending()
        assertEquals(callsAfterConflict, apiCalls)
    }

    @Test
    fun `409 with a different type is an ordinary failure, not a conflict`() = runTest {
        store.insert(subjectRow("r1", "subj-1"))
        // POST /subjects also answers 409, with its own type — status alone must not classify.
        responder = { respond(conflictBody("/errors/subject-already-exists"), HttpStatusCode.Conflict, jsonHeaders()) }

        repo.syncPending()

        val stored = checkNotNull(store.current("r1"))
        assertEquals(SyncState.Failed, stored.syncState)
        assertNull(stored.conflict)
        assertEquals("Las respuestas no son válidas.", stored.errorMessage)
    }

    // ---- offline-subject resolution ----------------------------------------------------------

    private fun subjectUpload(localId: String, syncState: SyncState, canonicalId: String? = null) =
        SubjectUpload(
            localId = localId,
            dni = 42,
            sex = Sex.Female,
            name = "Ana",
            lastName = "García",
            birthDate = LocalDate(1990, 1, 1),
            isRural = false,
            isIndigenous = false,
            syncState = syncState,
            canonicalId = canonicalId,
            errorMessage = null,
            attemptCount = 0,
            createdAt = 0L,
            updatedAt = 0L,
        )

    @Test
    fun `a subject-targeted response waits while its subject is still Pending`() = runTest {
        subjects.insert(subjectUpload("subj-local", SyncState.Pending))
        store.insert(subjectRow("r1", "subj-local"))
        responder = { respond(successBody("r1"), HttpStatusCode.Created, jsonHeaders()) }

        val summary = repo.syncPending()

        assertEquals(0, apiCalls)
        assertEquals(SyncState.Pending, store.current("r1")?.syncState)
        assertEquals(1, summary.stillPending)
    }

    @Test
    fun `a merged subject swaps in its canonical id on the next sweep`() = runTest {
        subjects.insert(subjectUpload("subj-local", SyncState.Merged, canonicalId = "subj-server"))
        store.insert(subjectRow("r1", "subj-local"))
        responder = { respond(successBody("r1"), HttpStatusCode.Created, jsonHeaders()) }

        repo.syncPending()

        assertTrue(sentBodies.single().contains("\"subject_id\":\"subj-server\""), sentBodies.single())
        assertEquals(SyncState.Synced, store.current("r1")?.syncState)
    }

    @Test
    fun `a dead-lettered subject dead-letters its response too`() = runTest {
        subjects.insert(subjectUpload("subj-local", SyncState.Failed))
        store.insert(subjectRow("r1", "subj-local"))

        val summary = repo.syncPending()

        assertEquals(0, apiCalls)
        assertEquals(SyncState.Failed, store.current("r1")?.syncState)
        assertEquals("El sujeto no se pudo sincronizar.", store.current("r1")?.errorMessage)
        assertEquals(1, summary.failed)
    }

    @Test
    fun `a subject with no local row is treated as a pre-existing server id`() = runTest {
        store.insert(subjectRow("r1", "subj-server"))
        responder = { respond(successBody("r1"), HttpStatusCode.Created, jsonHeaders()) }

        repo.syncPending()

        assertTrue(sentBodies.single().contains("\"subject_id\":\"subj-server\""), sentBodies.single())
    }

    // ---- CA8 replay --------------------------------------------------------------------------

    @Test
    fun `a retried sweep reuses the same client-generated id`() = runTest {
        store.insert(row("r1"))
        responder = { respondError(HttpStatusCode.InternalServerError) }
        repo.syncPending()

        currentTime = 1_000_000L
        responder = { respond(successBody("r1"), HttpStatusCode.Created, jsonHeaders()) }
        repo.syncPending()

        // Both attempts carry the same `id`, which is exactly what lets the server replay the
        // second one as 200 instead of reading it as a genuine second answer.
        assertEquals(2, sentBodies.size)
        assertTrue(sentBodies.all { it.contains("\"id\":\"r1\"") }, sentBodies.toString())
    }
}
