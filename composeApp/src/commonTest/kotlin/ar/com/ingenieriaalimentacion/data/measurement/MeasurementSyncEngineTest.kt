package ar.com.ingenieriaalimentacion.data.measurement

import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.MAX_SYNC_ATTEMPTS
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import ar.com.ingenieriaalimentacion.fake.FakeMeasurementLocalStore
import ar.com.ingenieriaalimentacion.fake.FakeSubjectLocalStore
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

class MeasurementSyncEngineTest {

    private var currentTime = 1_000L
    private val now: () -> Long = { currentTime }
    private val store = FakeMeasurementLocalStore(now)

    private var apiCalls = 0
    private var responder: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respondError(HttpStatusCode.InternalServerError) }

    private val api: MeasurementApi = run {
        val client = HttpClient(MockEngine { req -> apiCalls++; responder(req) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        MeasurementApi(client)
    }

    private val subjectStore = FakeSubjectLocalStore(now)

    private val repo = MeasurementRepositoryImpl(
        api = api,
        local = store,
        subjects = subjectStore,
        idGenerator = object : IdGenerator { override fun newId() = "generated" },
        now = now,
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun successBody(id: String, bmi: Double = 25.1, withAlert: Boolean = true): String {
        val alerts = if (withAlert) {
            """[{"alert_type":"bmi_high","severity":"warning","detail":"Sobrepeso"}]"""
        } else {
            "[]"
        }
        return """
            {"id":"$id","subject_id":"s","establishment_id":"e","weight_kg":72.5,"height_cm":170.0,
             "measured_at":"2026-06-21T18:30:00Z","bmi":$bmi,"alerts":$alerts}
        """.trimIndent()
    }

    private fun row(
        localId: String,
        syncState: SyncState = SyncState.Pending,
        attemptCount: Int = 0,
        createdAt: Long = 0L,
        updatedAt: Long = 0L,
    ) = MeasurementUpload(
        localId = localId,
        subjectId = "s",
        subjectName = "Ana",
        subjectLastName = "García",
        subjectDni = 42,
        establishmentId = "e",
        establishmentName = "Escuela",
        weightKg = 72.5,
        heightCm = 170.0,
        measuredAt = "2026-06-21T18:30:00Z",
        syncState = syncState,
        errorMessage = null,
        attemptCount = attemptCount,
        bmi = null,
        alerts = emptyList(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `Created maps to Synced and persists bmi plus serialized alerts`() = runTest {
        store.insert(row("r1"))
        responder = { respond(successBody("r1"), HttpStatusCode.Created, jsonHeaders()) }

        val summary = repo.syncPending()

        assertEquals(SyncState.Synced, store.current("r1")?.syncState)
        assertEquals(25.1, store.current("r1")?.bmi)
        assertEquals(1, summary.synced)
        val alertsJson = store.lastSyncedAlertsJson
        assertTrue(alertsJson?.contains("bmi_high") == true, "alertsJson: $alertsJson")
    }

    @Test
    fun `200 idempotent replay also lands Synced`() = runTest {
        store.insert(row("r1"))
        responder = { respond(successBody("r1"), HttpStatusCode.OK, jsonHeaders()) }

        repo.syncPending()

        assertEquals(SyncState.Synced, store.current("r1")?.syncState)
    }

    @Test
    fun `404 dead-letters immediately to Failed`() = runTest {
        store.insert(row("r1"))
        responder = { respondError(HttpStatusCode.NotFound) }

        val summary = repo.syncPending()

        assertEquals(SyncState.Failed, store.current("r1")?.syncState)
        assertEquals(1, summary.failed)
    }

    @Test
    fun `403 dead-letters immediately to Failed`() = runTest {
        store.insert(row("r1"))
        responder = { respondError(HttpStatusCode.Forbidden) }

        repo.syncPending()

        assertEquals(SyncState.Failed, store.current("r1")?.syncState)
    }

    @Test
    fun `422 dead-letters immediately to Failed`() = runTest {
        store.insert(row("r1"))
        responder = { respondError(HttpStatusCode.UnprocessableEntity) }

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
        currentTime = 1_000_000L // past the backoff window so the row is eligible
        responder = { respondError(HttpStatusCode.InternalServerError) }

        val summary = repo.syncPending()

        assertEquals(SyncState.Failed, store.current("r1")?.syncState)
        assertEquals(1, summary.failed)
    }

    @Test
    fun `backoff gates a freshly-bumped row out then lets it in after the window`() = runTest {
        store.insert(row("r1", createdAt = 0L))
        currentTime = 1_000L
        responder = { respondError(HttpStatusCode.InternalServerError) }
        repo.syncPending() // bumps to attemptCount=1, updatedAt=1000

        assertEquals(1, store.current("r1")?.attemptCount)
        val callsAfterFirst = apiCalls

        // Within the backoff window (1000 + 2000 = 3000): no POST, row unchanged.
        currentTime = 2_500L
        repo.syncPending()
        assertEquals(callsAfterFirst, apiCalls)
        assertEquals(1, store.current("r1")?.attemptCount)

        // Past the window: eligible again, now succeeds.
        currentTime = 3_500L
        responder = { respond(successBody("r1"), HttpStatusCode.Created, jsonHeaders()) }
        repo.syncPending()
        assertEquals(SyncState.Synced, store.current("r1")?.syncState)
    }

    @Test
    fun `resetStuckSyncing flips a SYNCING row back to PENDING`() = runTest {
        store.insert(row("r1", syncState = SyncState.Syncing))

        repo.resetStuckSyncing()

        assertEquals(SyncState.Pending, store.current("r1")?.syncState)
    }

    // --- Phase 3: resolve-at-read gating against the subject queue ---

    private fun subjectRow(
        localId: String,
        syncState: SyncState,
        canonicalId: String? = null,
    ) = SubjectUpload(
        localId = localId,
        dni = 42,
        sex = Sex.Male,
        name = "Ana",
        lastName = "García",
        birthDate = LocalDate(2000, 1, 1),
        isRural = false,
        isIndigenous = false,
        syncState = syncState,
        canonicalId = canonicalId,
        errorMessage = null,
        attemptCount = 0,
        createdAt = 0,
        updatedAt = 0,
    )

    /** Captures the `subject_id` field POSTed to the measurements endpoint. */
    private var capturedBody: String? = null

    private fun capturingSuccess(id: String): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { req ->
            capturedBody = (req.body as? io.ktor.http.content.TextContent)?.text
            respond(successBody(id), HttpStatusCode.Created, jsonHeaders())
        }

    @Test
    fun `no subject row posts subjectId unchanged`() = runTest {
        store.insert(row("r1")) // subjectId = "s", no local subject row
        responder = capturingSuccess("r1")

        repo.syncPending()

        assertEquals(SyncState.Synced, store.current("r1")?.syncState)
        assertTrue(capturedBody?.contains("\"subject_id\":\"s\"") == true, "body: $capturedBody")
    }

    @Test
    fun `synced subject posts its localId`() = runTest {
        subjectStore.insert(subjectRow("s", SyncState.Synced, canonicalId = "s"))
        store.insert(row("r1"))
        responder = capturingSuccess("r1")

        repo.syncPending()

        assertEquals(SyncState.Synced, store.current("r1")?.syncState)
        assertTrue(capturedBody?.contains("\"subject_id\":\"s\"") == true, "body: $capturedBody")
    }

    @Test
    fun `merged subject posts the canonical id`() = runTest {
        subjectStore.insert(subjectRow("s", SyncState.Merged, canonicalId = "canonical-x"))
        store.insert(row("r1"))
        responder = capturingSuccess("r1")

        repo.syncPending()

        assertEquals(SyncState.Synced, store.current("r1")?.syncState)
        assertTrue(capturedBody?.contains("\"subject_id\":\"canonical-x\"") == true, "body: $capturedBody")
    }

    @Test
    fun `pending subject holds the measurement without attempting`() = runTest {
        subjectStore.insert(subjectRow("s", SyncState.Pending))
        store.insert(row("r1"))
        val callsBefore = apiCalls

        val summary = repo.syncPending()

        assertEquals(callsBefore, apiCalls) // no POST attempted
        assertEquals(SyncState.Pending, store.current("r1")?.syncState)
        assertEquals(0, store.current("r1")?.attemptCount)
        assertEquals(1, summary.stillPending)
    }

    @Test
    fun `syncing subject holds the measurement without attempting`() = runTest {
        subjectStore.insert(subjectRow("s", SyncState.Syncing))
        store.insert(row("r1"))
        val callsBefore = apiCalls

        repo.syncPending()

        assertEquals(callsBefore, apiCalls)
        assertEquals(SyncState.Pending, store.current("r1")?.syncState)
    }

    @Test
    fun `failed subject dead-letters the dependent measurement`() = runTest {
        subjectStore.insert(subjectRow("s", SyncState.Failed))
        store.insert(row("r1"))
        val callsBefore = apiCalls

        val summary = repo.syncPending()

        assertEquals(callsBefore, apiCalls)
        assertEquals(SyncState.Failed, store.current("r1")?.syncState)
        assertEquals("El sujeto no se pudo sincronizar.", store.current("r1")?.errorMessage)
        assertEquals(1, summary.failed)
    }

    @Test
    fun `enqueue uses prepared instant and repository UUID and queue clock`() = runTest {
        val captured = kotlin.time.Instant.parse("2026-10-17T00:30:00Z")
        val subject = ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary(
            "s", 42, "Ana", "García", Sex.Female, LocalDate(1990, 1, 1),
        )
        val draft = ar.com.ingenieriaalimentacion.domain.model.measurement.NewMeasurement(
            subject, "e", "Escuela", 72.55, 170.25, captured, observation = "Verificado",
        )
        currentTime = captured.toEpochMilliseconds() + 86_400_000L
        assertTrue(repo.enqueue(draft).isSuccess)
        val row = kotlin.test.assertNotNull(store.current("generated"))
        assertEquals(captured.toString(), row.measuredAt)
        assertEquals(currentTime, row.createdAt)
        assertEquals(currentTime, row.updatedAt)
        assertEquals("generated", row.localId)
        assertEquals(72.55, row.weightKg)
        assertEquals(170.25, row.heightCm)
        assertEquals("Verificado", row.observation)
        assertEquals(0, apiCalls)
    }
    @Test
    fun `queued observation reaches API unchanged on transient retry`() = runTest {
        val note = "Se verificó peso y talla.\nSin calzado."
        store.insert(row("noted").copy(observation = note))
        val bodies = mutableListOf<String>()
        responder = { request ->
            bodies += (request.body as io.ktor.http.content.TextContent).text
            respondError(HttpStatusCode.InternalServerError)
        }
        repo.syncPending()
        assertEquals(note, store.current("noted")?.observation)
        currentTime += 60_000L
        responder = { request ->
            bodies += (request.body as io.ktor.http.content.TextContent).text
            respond(successBody("noted"), HttpStatusCode.Created, jsonHeaders())
        }
        repo.syncPending()
        assertEquals(2, bodies.size)
        assertEquals(bodies.first(), bodies.last())
        val request = Json.decodeFromString<ar.com.ingenieriaalimentacion.data.measurement.dto.MeasurementRequest>(bodies.last())
        assertEquals(note, request.observation)
        assertEquals(SyncState.Synced, store.current("noted")?.syncState)
    }

}
