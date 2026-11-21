package ar.com.ingenieriaalimentacion.data.subject.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ar.com.ingenieriaalimentacion.data.auth.SessionEnded
import ar.com.ingenieriaalimentacion.data.auth.SessionExit
import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.auth.storage.MemorySessionManager
import ar.com.ingenieriaalimentacion.data.db.AppDatabase
import ar.com.ingenieriaalimentacion.data.db.SqlDelightDatabaseCleaner
import ar.com.ingenieriaalimentacion.data.subject.SubjectApi
import ar.com.ingenieriaalimentacion.data.subject.SubjectRepositoryImpl
import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.fake.FakeIdGenerator
import ar.com.ingenieriaalimentacion.fake.signInForTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class SubjectQueueSessionTest {
    @Test
    fun `a delayed upload cannot touch a later session and keeps its id for replay`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        val lifecycle = SessionLifecycle(
            MemorySessionManager(),
            SqlDelightDatabaseCleaner(database, Dispatchers.Unconfined),
            FakeIdGenerator(),
        )
        lifecycle.bootstrap()
        lifecycle.signInForTest("owner-a")
        val store = DefaultSubjectLocalStore(
            database.pendingSubjectQueries,
            lifecycle = lifecycle,
            now = { 100L },
            dispatcher = Dispatchers.Unconfined,
        )
        val uploadedIds = Channel<String>(Channel.UNLIMITED)
        val releaseFirstResponse = CompletableDeferred<Unit>()
        var requestCount = 0
        val client = HttpClient(MockEngine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            val id = Json.parseToJsonElement(body).jsonObject.getValue("id").jsonPrimitive.content
            uploadedIds.send(id)
            if (++requestCount == 1) releaseFirstResponse.await()
            respond(
                """{"id":"$id","dni":1,"sex":"male","name":"A","last_name":"B",
                   "birth_date":"1990-01-01","is_rural":false,"is_indigenous":false}""",
                HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        val repository = SubjectRepositoryImpl(
            SubjectApi(client), store, FakeIdGenerator(), now = { 100L }, lifecycle = lifecycle,
        )
        val newSubject = NewSubject(1, Sex.Male, "A", "B", LocalDate.parse("1990-01-01"), false, false)
        try {
            val originalId = repository.enqueue(newSubject).getOrThrow()
            val observedFirst = CompletableDeferred<String>()
            val priorObservation = async {
                repository.observeUploads().onEach { observedFirst.complete(it.single().localId) }.toList()
            }
            assertEquals(originalId, observedFirst.await())
            val upload = async { repository.syncPending() }
            assertEquals(originalId, uploadedIds.receive())
            assertEquals(SyncState.Syncing, store.find(originalId)?.syncState)

            lifecycle.end(lifecycle.capture(), SessionExit.Blocked)
            assertFailsWith<SessionEnded> { repository.pruneTerminal(Duration.ZERO) }
            lifecycle.signInForTest("owner-b")
            assertTrue(repository.observeUploads().first().isEmpty())
            assertNull(store.find(originalId))
            val otherId = repository.enqueue(newSubject.copy(dni = 2)).getOrThrow()
            repository.delete(originalId).getOrThrow()

            releaseFirstResponse.complete(Unit)
            assertFailsWith<SessionEnded> { upload.await() }
            assertTrue(priorObservation.await().flatten().all { it.localId == originalId })
            assertEquals(listOf(otherId), store.all().map { it.localId })
            assertEquals(1, repository.syncPending().synced)
            assertEquals(otherId, uploadedIds.receive())

            lifecycle.end(lifecycle.capture(), SessionExit.SignedOut)
            assertTrue(database.pendingSubjectQueries.selectAll("owner-b").executeAsList().isEmpty())
            lifecycle.signInForTest("owner-a")
            val retained = store.all().single()
            assertEquals(originalId, retained.localId)
            assertEquals(SyncState.Syncing, retained.syncState)
            assertEquals(0, retained.attemptCount)
            repository.resetStuckSyncing()
            assertEquals(1, repository.syncPending().synced)
            assertEquals(originalId, uploadedIds.receive())
        } finally {
            releaseFirstResponse.complete(Unit)
            client.close()
            driver.close()
        }
    }
}
