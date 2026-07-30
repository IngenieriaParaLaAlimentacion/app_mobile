package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.domain.model.subject.MAX_SYNC_ATTEMPTS
import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
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
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubjectSyncEngineTest {

    private var currentTime = 1_000L
    private val now: () -> Long = { currentTime }
    private val store = FakeSubjectLocalStore(now)

    private var apiCalls = 0
    private var responder: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respondError(HttpStatusCode.InternalServerError) }

    private val api: SubjectApi = run {
        val client = HttpClient(MockEngine { req -> apiCalls++; responder(req) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        SubjectApi(client)
    }

    private val repo = SubjectRepositoryImpl(
        api = api,
        local = store,
        idGenerator = object : IdGenerator { override fun newId() = "generated" },
        now = now,
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun successBody(id: String) = """
        {"id":"$id","dni":1,"sex":"male","name":"A","last_name":"B","birth_date":"1990-01-01","is_rural":false,"is_indigenous":false}
    """.trimIndent()

    private fun row(
        localId: String,
        syncState: SyncState = SyncState.Pending,
        attemptCount: Int = 0,
        createdAt: Long = 0L,
        updatedAt: Long = 0L,
    ) = SubjectUpload(
        localId = localId,
        dni = 1,
        sex = Sex.Male,
        name = "A",
        lastName = "B",
        birthDate = LocalDate.parse("1990-01-01"),
        isRural = false,
        isIndigenous = false,
        syncState = syncState,
        canonicalId = null,
        errorMessage = null,
        attemptCount = attemptCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `enqueue returns the generated localId it persisted`() = runTest {
        val newSubject = NewSubject(
            dni = 12345678,
            sex = Sex.Male,
            name = "Juan",
            lastName = "Pérez",
            birthDate = LocalDate.parse("1990-01-01"),
            isRural = false,
            isIndigenous = false,
        )

        val localId = repo.enqueue(newSubject).getOrThrow()

        // The caller needs this id to reference the subject from a measurement captured right after.
        assertEquals("generated", localId)
        assertEquals(SyncState.Pending, store.current(localId)?.syncState)
        assertEquals(12345678, store.current(localId)?.dni)
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
    fun `Duplicate maps to Merged with canonical id`() = runTest {
        store.insert(row("r1"))
        responder = {
            respond(
                """{"canonical_subject":{"id":"canon-1"}}""",
                HttpStatusCode.Conflict,
                headersOf(HttpHeaders.ContentType, "application/problem+json"),
            )
        }

        val summary = repo.syncPending()

        assertEquals(SyncState.Merged, store.current("r1")?.syncState)
        assertEquals("canon-1", store.current("r1")?.canonicalId)
        assertEquals(1, summary.merged)
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
    fun `403 dead-letters immediately to Failed`() = runTest {
        store.insert(row("r1"))
        responder = { respondError(HttpStatusCode.Forbidden) }

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
        // createdAt=0 so jitter is 0; attemptCount=1 → backoff 2000ms.
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

    @Test
    fun `retry clears the row back to a fresh Pending`() = runTest {
        store.insert(row("r1", syncState = SyncState.Failed, attemptCount = 5))

        repo.retry("r1")

        val updated = store.current("r1")
        assertEquals(SyncState.Pending, updated?.syncState)
        assertEquals(0, updated?.attemptCount)
        assertNull(updated?.errorMessage)
    }
}
