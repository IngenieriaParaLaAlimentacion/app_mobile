package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
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
import kotlinx.serialization.json.Json

class SubjectQueryRepositoryImplTest {

    private fun api(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): SubjectApi {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        return SubjectApi(client)
    }

    private fun page(items: String, page: Int = 1, totalPages: Int = 1): String = """
        { "items": [$items], "total": 1, "page": $page, "page_size": 20, "total_pages": $totalPages }
    """.trimIndent()

    private fun remoteSubject(id: String, dni: Int, name: String, lastName: String): String =
        """{ "id": "$id", "dni": $dni, "name": "$name", "last_name": "$lastName", "birth_date":"1990-01-01","sex": "male" }"""

    private fun respondPage(scope: MockRequestHandleScope, body: String) = scope.respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun upload(
        localId: String,
        dni: Int,
        name: String,
        lastName: String,
        state: SyncState,
        canonicalId: String? = null,
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
        canonicalId = canonicalId,
        errorMessage = null,
        attemptCount = 0,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `unions local pending subject ahead of remote items`() = runTest {
        val store = FakeSubjectLocalStore().apply {
            insert(upload("local-1", 111, "Local", "Pendiente", SyncState.Pending))
        }
        val api = api { respondPage(this, page(remoteSubject("remote-1", 222, "Remoto", "Servidor"))) }

        val result = SubjectQueryRepositoryImpl(api, store).searchSubjects(null, page = 1, pageSize = 20)

        val items = result.getOrThrow().items
        assertEquals(listOf("local-1", "remote-1"), items.map { it.id })
    }

    @Test
    fun `dedups by id when the synced local row is also returned remotely`() = runTest {
        // Happy path: localId == server PK, so a SYNCED local row collides with its remote row.
        val store = FakeSubjectLocalStore().apply {
            insert(upload("shared-id", 111, "Ana", "García", SyncState.Synced))
        }
        val api = api { respondPage(this, page(remoteSubject("shared-id", 111, "Ana", "García"))) }

        val items = SubjectQueryRepositoryImpl(api, store)
            .searchSubjects(null, page = 1, pageSize = 20).getOrThrow().items

        assertEquals(1, items.count { it.id == "shared-id" })
    }

    @Test
    fun `hides MERGED and FAILED local rows`() = runTest {
        val store = FakeSubjectLocalStore().apply {
            insert(upload("merged-1", 1, "Merge", "Ado", SyncState.Merged, canonicalId = "canon-1"))
            insert(upload("failed-1", 2, "Fall", "Ido", SyncState.Failed))
            insert(upload("pending-1", 3, "Pend", "Iente", SyncState.Pending))
        }
        val api = api { respondPage(this, page("")) }

        val items = SubjectQueryRepositoryImpl(api, store)
            .searchSubjects(null, page = 1, pageSize = 20).getOrThrow().items

        assertEquals(listOf("pending-1"), items.map { it.id })
    }

    @Test
    fun `filters local rows by query on name, lastName and dni`() = runTest {
        val store = FakeSubjectLocalStore().apply {
            insert(upload("a", 111, "Juan", "Pérez", SyncState.Pending))
            insert(upload("b", 222, "María", "Gómez", SyncState.Pending))
        }
        val api = api { respondPage(this, page("")) }
        val repo = SubjectQueryRepositoryImpl(api, store)

        assertEquals(listOf("a"), repo.searchSubjects("juan", 1, 20).getOrThrow().items.map { it.id })
        assertEquals(listOf("b"), repo.searchSubjects("gómez", 1, 20).getOrThrow().items.map { it.id })
        assertEquals(listOf("b"), repo.searchSubjects("222", 1, 20).getOrThrow().items.map { it.id })
    }

    @Test
    fun `offline serves local rows as a single page instead of failing`() = runTest {
        val store = FakeSubjectLocalStore().apply {
            insert(upload("local-1", 111, "Local", "Solo", SyncState.Pending))
        }
        val api = api { respond("boom", HttpStatusCode.InternalServerError) }

        val result = SubjectQueryRepositoryImpl(api, store).searchSubjects(null, page = 1, pageSize = 20)

        val pageResult = result.getOrThrow()
        assertEquals(listOf("local-1"), pageResult.items.map { it.id })
        assertEquals(1, pageResult.page)
        assertEquals(1, pageResult.totalPages)
    }

    @Test
    fun `offline with no local rows propagates the failure`() = runTest {
        val store = FakeSubjectLocalStore()
        val api = api { respond("boom", HttpStatusCode.InternalServerError) }

        val result = SubjectQueryRepositoryImpl(api, store).searchSubjects(null, page = 1, pageSize = 20)

        assertTrue(result.isFailure)
    }

    @Test
    fun `pages beyond the first return remote items only`() = runTest {
        val store = FakeSubjectLocalStore().apply {
            insert(upload("local-1", 111, "Local", "Pendiente", SyncState.Pending))
        }
        val api = api { respondPage(this, page(remoteSubject("remote-2", 222, "Remoto", "Dos"), page = 2, totalPages = 2)) }

        val items = SubjectQueryRepositoryImpl(api, store)
            .searchSubjects(null, page = 2, pageSize = 20).getOrThrow().items

        assertEquals(listOf("remote-2"), items.map { it.id })
    }
}
