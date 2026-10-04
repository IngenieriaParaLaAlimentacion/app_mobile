package ar.com.ingenieriaalimentacion.data.profile

import ar.com.ingenieriaalimentacion.fake.FakeCurrentEstablishmentLocalStore
import ar.com.ingenieriaalimentacion.fake.FakeEstablishmentLocalStore
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceError
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceNotice
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentRequestError
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithdrawalError
import ar.com.ingenieriaalimentacion.domain.model.profile.ProfileError
import ar.com.ingenieriaalimentacion.domain.model.profile.UpdateProfile
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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileRepositoryImplTest {

    private fun repository(
        local: FakeEstablishmentLocalStore = FakeEstablishmentLocalStore(),
        current: FakeCurrentEstablishmentLocalStore = FakeCurrentEstablishmentLocalStore(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ProfileRepositoryImpl {
        val client = HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            defaultRequest { url("https://api.example.com/") }
        }
        return ProfileRepositoryImpl(ProfileApi(client), local, current)
    }

    private fun respondJson(scope: MockRequestHandleScope, body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        scope.respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private val establishmentJson = """
        {
          "id": "e1", "name": "Comedor Norte", "formatted_address": "Calle 1, Ciudad",
          "latitude": -34.6, "longitude": -58.4, "place_id": "p1",
          "city": "Ciudad", "province": "Provincia", "status": "approved"
        }
    """.trimIndent()

    // --- getMe three-state ---

    @Test
    fun `getMe with null establishments marks non-manager`() = runTest {
        val repo = repository {
            respondJson(
                this,
                """{"id":"u1","email":"a@b.com","name":"Ana","last_name":"García","role":"analyst","establishments":null}""",
            )
        }

        val profile = repo.getMe().getOrThrow()

        assertNull(profile.establishments)
        assertEquals(false, profile.isManager)
    }

    @Test
    fun `getMe with empty establishments is manager without relations`() = runTest {
        val repo = repository {
            respondJson(
                this,
                """{"id":"u1","email":"a@b.com","name":"Ana","last_name":"García","role":"field_worker","establishments":[]}""",
            )
        }

        val profile = repo.getMe().getOrThrow()

        assertEquals(emptyList(), profile.establishments)
        assertTrue(profile.isManager)
    }

    @Test
    fun `getMe maps establishments with status`() = runTest {
        val repo = repository {
            respondJson(
                this,
                """{"id":"u1","email":"a@b.com","name":"Ana","last_name":"García","role":"field_worker","establishments":[$establishmentJson]}""",
            )
        }

        val profile = repo.getMe().getOrThrow()

        assertEquals(1, profile.establishments?.size)
        assertEquals(EstablishmentStatus.Approved, profile.establishments?.first()?.status)
        assertEquals("Comedor Norte", profile.establishments?.first()?.name)
    }

    @Test
    fun `getMe maps 401 to Unauthorized`() = runTest {
        val repo = repository { respond("nope", HttpStatusCode.Unauthorized) }

        val failure = repo.getMe().exceptionOrNull()

        assertTrue(failure is ProfileFailure)
        assertEquals(ProfileError.Unauthorized, failure.error)
    }

    @Test
    fun `getMe maps 404 to UserNotFound`() = runTest {
        val repo = repository { respond("nope", HttpStatusCode.NotFound) }

        val failure = repo.getMe().exceptionOrNull()

        assertTrue(failure is ProfileFailure)
        assertEquals(ProfileError.UserNotFound, failure.error)
    }

    // --- updateMe omits unchanged field ---

    @Test
    fun `updateMe sends only the changed field`() = runTest {
        var capturedBody: String? = null
        val repo = repository { request ->
            capturedBody = (request.body as? TextContent)?.text
            respondJson(
                this,
                """{"id":"u1","email":"a@b.com","name":"Nuevo","last_name":"García","role":"analyst","establishments":null}""",
            )
        }

        repo.updateMe(UpdateProfile(name = "Nuevo")).getOrThrow()

        assertEquals("""{"name":"Nuevo"}""", capturedBody)
    }

    @Test
    fun `updateMe maps 422 to ValidationError`() = runTest {
        val repo = repository { respond("bad", HttpStatusCode.UnprocessableEntity) }

        val failure = repo.updateMe(UpdateProfile(name = "X")).exceptionOrNull()

        assertTrue(failure is ProfileFailure)
        assertEquals(ProfileError.ValidationError, failure.error)
    }

    // --- available catalog pagination ---

    @Test
    fun `getAvailableEstablishments maps paginated payload`() = runTest {
        val repo = repository {
            respondJson(
                this,
                """
                {
                  "items": [
                    { "id": "e1", "name": "Comedor Norte", "formatted_address": "Calle 1",
                      "latitude": -34.6, "longitude": -58.4, "place_id": "p1",
                      "city": "Ciudad", "province": "Provincia" }
                  ],
                  "total": 21, "page": 2, "page_size": 20, "total_pages": 2
                }
                """.trimIndent(),
            )
        }

        val paged = repo.getAvailableEstablishments(search = "norte", page = 2, pageSize = 20).getOrThrow()

        assertEquals(1, paged.items.size)
        assertEquals(2, paged.page)
        assertEquals(2, paged.totalPages)
        assertEquals(21, paged.total)
        assertEquals("Comedor Norte", paged.items.first().name)
    }

    // --- request access ---

    @Test
    fun `requestEstablishmentAccess returns relations with status`() = runTest {
        val repo = repository {
            respondJson(this, "[$establishmentJson]", HttpStatusCode.Created)
        }

        val relations = repo.requestEstablishmentAccess(listOf("e1")).getOrThrow()

        assertEquals(1, relations.size)
        assertEquals(EstablishmentStatus.Approved, relations.first().status)
    }

    @Test
    fun `requestEstablishmentAccess maps 403 to Forbidden`() = runTest {
        val repo = repository { respond("forbidden", HttpStatusCode.Forbidden) }

        val failure = repo.requestEstablishmentAccess(listOf("e1")).exceptionOrNull()

        assertTrue(failure is EstablishmentRequestFailure)
        assertEquals(EstablishmentRequestError.Forbidden, failure.error)
    }

    @Test
    fun `requestEstablishmentAccess maps 404 to EstablishmentNotFound`() = runTest {
        val repo = repository { respond("missing", HttpStatusCode.NotFound) }

        val failure = repo.requestEstablishmentAccess(listOf("e1")).exceptionOrNull()

        assertTrue(failure is EstablishmentRequestFailure)
        assertEquals(EstablishmentRequestError.EstablishmentNotFound, failure.error)
    }

    @Test
    fun `requestEstablishmentAccess merges the new relations into the cache`() = runTest {
        val local = FakeEstablishmentLocalStore(
            listOf(
                EstablishmentWithStatus("e0", "Oeste", "Calle 0", 0.0, 0.0, "p0", "C", "P", EstablishmentStatus.Approved),
                EstablishmentWithStatus("e1", "Norte", "Calle 1", 0.0, 0.0, "p1", "C", "P", EstablishmentStatus.Rejected),
            ),
        )
        val repo = repository(local) {
            respondJson(this, "[${establishmentJson.replace("approved", "pending")}]", HttpStatusCode.Created)
        }

        repo.requestEstablishmentAccess(listOf("e1")).getOrThrow()

        assertEquals(listOf("e0", "e1"), local.rows.map { it.id })
        assertEquals(EstablishmentStatus.Pending, local.rows.first { it.id == "e1" }.status)
    }

    // --- admin subscription (backend #151) ---

    @Test
    fun `subscribeToEstablishments posts all ids in one call and caches the approved relations`() = runTest {
        val local = FakeEstablishmentLocalStore()
        val calls = mutableListOf<String>()
        var capturedBody: String? = null
        val repo = repository(local) { request ->
            calls += "${request.method.value} ${request.url.encodedPath}"
            capturedBody = (request.body as? TextContent)?.text
            respondJson(
                this,
                "[$establishmentJson, ${establishmentJson.replace("\"e1\"", "\"e2\"")}]",
                HttpStatusCode.Created,
            )
        }

        val relations = repo.subscribeToEstablishments(listOf("e1", "e2")).getOrThrow()

        assertEquals(listOf("POST /api/v1/users/me/establishments/subscriptions"), calls)
        assertEquals("""{"establishment_ids":["e1","e2"]}""", capturedBody)
        assertTrue(relations.all { it.status == EstablishmentStatus.Approved })
        assertEquals(listOf("e1", "e2"), local.rows.map { it.id })
    }

    @Test
    fun `subscribeToEstablishments maps 404 to EstablishmentNotFound and caches nothing`() = runTest {
        val local = FakeEstablishmentLocalStore()
        val repo = repository(local) { respond("missing", HttpStatusCode.NotFound) }

        val failure = repo.subscribeToEstablishments(listOf("e1", "e2")).exceptionOrNull()

        assertTrue(failure is EstablishmentRequestFailure)
        assertEquals(EstablishmentRequestError.EstablishmentNotFound, failure.error)
        assertTrue(local.rows.isEmpty())
    }

    @Test
    fun `unsubscribeFromEstablishments deletes with repeated query ids, evicts them and clears the current one`() = runTest {
        val local = FakeEstablishmentLocalStore(
            listOf(
                EstablishmentWithStatus("e1", "Norte", "Calle 1", 0.0, 0.0, "p1", "C", "P", EstablishmentStatus.Approved),
                EstablishmentWithStatus("e2", "Sur", "Calle 2", 0.0, 0.0, "p2", "C", "P", EstablishmentStatus.Approved),
                EstablishmentWithStatus("e3", "Este", "Calle 3", 0.0, 0.0, "p3", "C", "P", EstablishmentStatus.Approved),
            ),
        )
        val current = FakeCurrentEstablishmentLocalStore(initial = "e2")
        var captured: String? = null
        var capturedIds: List<String>? = null
        val repo = repository(local, current) { request ->
            captured = "${request.method.value} ${request.url.encodedPath}"
            capturedIds = request.url.parameters.getAll("establishment_ids")
            respond("", HttpStatusCode.NoContent)
        }

        repo.unsubscribeFromEstablishments(listOf("e1", "e2")).getOrThrow()

        assertEquals("DELETE /api/v1/users/me/establishments/subscriptions", captured)
        assertEquals(listOf("e1", "e2"), capturedIds)
        assertEquals(listOf("e3"), local.rows.map { it.id })
        assertNull(current.value)
    }

    @Test
    fun `unsubscribeFromEstablishments maps 403 to Forbidden and keeps the cache`() = runTest {
        val local = FakeEstablishmentLocalStore(
            listOf(
                EstablishmentWithStatus("e1", "Norte", "Calle 1", 0.0, 0.0, "p1", "C", "P", EstablishmentStatus.Approved),
            ),
        )
        val repo = repository(local) { respond("nope", HttpStatusCode.Forbidden) }

        val failure = repo.unsubscribeFromEstablishments(listOf("e1")).exceptionOrNull()

        assertTrue(failure is EstablishmentWithdrawalFailure)
        assertEquals(EstablishmentWithdrawalError.Forbidden, failure.error)
        assertEquals(listOf("e1"), local.rows.map { it.id })
    }

    // --- withdraw from establishment ---

    @Test
    fun `withdrawFromEstablishment clears the current establishment only when it is the withdrawn one`() = runTest {
        val current = FakeCurrentEstablishmentLocalStore(initial = "e2")
        val repo = repository(current = current) { respondJson(this, "[$establishmentJson]") }

        repo.withdrawFromEstablishment("e1").getOrThrow()
        assertEquals("e2", current.value)

        repo.withdrawFromEstablishment("e2").getOrThrow()
        assertNull(current.value)
    }

    @Test
    fun `withdrawFromEstablishment sends the id and evicts it from the cache`() = runTest {
        val cached = listOf(
            EstablishmentWithStatus("e1", "Norte", "Calle 1", 0.0, 0.0, "p1", "C", "P", EstablishmentStatus.Approved),
            EstablishmentWithStatus("e2", "Sur", "Calle 2", 0.0, 0.0, "p2", "C", "P", EstablishmentStatus.Pending),
        )
        val local = FakeEstablishmentLocalStore(cached)
        var capturedPath: String? = null
        var capturedBody: String? = null
        val repo = repository(local) { request ->
            capturedPath = request.url.encodedPath
            capturedBody = (request.body as? TextContent)?.text
            respondJson(this, "[$establishmentJson]")
        }

        repo.withdrawFromEstablishment("e1").getOrThrow()

        assertEquals("/api/v1/users/me/establishments/withdraw", capturedPath)
        assertEquals("""{"establishment_ids":["e1"]}""", capturedBody)
        assertEquals(listOf("e2"), local.rows.map { it.id })
    }

    @Test
    fun `withdrawFromEstablishment maps 404 to EstablishmentNotFound and leaves the cache untouched`() = runTest {
        val cached = listOf(
            EstablishmentWithStatus("e1", "Norte", "Calle 1", 0.0, 0.0, "p1", "C", "P", EstablishmentStatus.Approved),
        )
        val local = FakeEstablishmentLocalStore(cached)
        val repo = repository(local) { respond("missing", HttpStatusCode.NotFound) }

        val failure = repo.withdrawFromEstablishment("e1").exceptionOrNull()

        assertTrue(failure is EstablishmentWithdrawalFailure)
        assertEquals(EstablishmentWithdrawalError.EstablishmentNotFound, failure.error)
        assertEquals(listOf("e1"), local.rows.map { it.id })
    }

    @Test
    fun `withdrawFromEstablishment maps 409 to NotWithdrawable`() = runTest {
        val repo = repository { respond("conflict", HttpStatusCode.Conflict) }

        val failure = repo.withdrawFromEstablishment("e1").exceptionOrNull()

        assertTrue(failure is EstablishmentWithdrawalFailure)
        assertEquals(EstablishmentWithdrawalError.NotWithdrawable, failure.error)
    }

    @Test
    fun `withdrawFromEstablishment maps 401 to Unauthorized`() = runTest {
        val repo = repository { respond("no token", HttpStatusCode.Unauthorized) }

        val failure = repo.withdrawFromEstablishment("e1").exceptionOrNull()

        assertTrue(failure is EstablishmentWithdrawalFailure)
        assertEquals(EstablishmentWithdrawalError.Unauthorized, failure.error)
    }

    // --- notify establishment absence ---

    @Test
    fun `notifyEstablishmentAbsence posts the notice with nulls for missing fields`() = runTest {
        var capturedPath: String? = null
        var capturedBody: String? = null
        val repo = repository { request ->
            capturedPath = request.url.encodedPath
            capturedBody = (request.body as? TextContent)?.text
            respondJson(
                this,
                """{"id":"r1","user_id":"u1","status":"pending","created_at":"2026-08-10T00:00:00Z"}""",
                HttpStatusCode.Created,
            )
        }

        repo.notifyEstablishmentAbsence(
            EstablishmentAbsenceNotice(name = "Comedor Norte", formattedAddress = "Av. Siempre Viva 742"),
        ).getOrThrow()

        assertEquals("/api/v1/establishments/requests", capturedPath)
        assertEquals(
            """{"name":"Comedor Norte","formatted_address":"Av. Siempre Viva 742","city":null,"province":null}""",
            capturedBody,
        )
    }

    @Test
    fun `notifyEstablishmentAbsence maps 401 to Unauthorized`() = runTest {
        val repo = repository { respond("no token", HttpStatusCode.Unauthorized) }

        val failure = repo.notifyEstablishmentAbsence(EstablishmentAbsenceNotice(name = "Comedor", formattedAddress = "Calle 1")).exceptionOrNull()

        assertTrue(failure is EstablishmentAbsenceFailure)
        assertEquals(EstablishmentAbsenceError.Unauthorized, failure.error)
    }

    @Test
    fun `notifyEstablishmentAbsence maps 422 to Validation`() = runTest {
        val repo = repository { respond("too long", HttpStatusCode.UnprocessableEntity) }

        val failure = repo.notifyEstablishmentAbsence(EstablishmentAbsenceNotice(name = "Comedor", formattedAddress = "Calle 1")).exceptionOrNull()

        assertTrue(failure is EstablishmentAbsenceFailure)
        assertEquals(EstablishmentAbsenceError.Validation, failure.error)
    }

    @Test
    fun `notifyEstablishmentAbsence maps 400 to Validation`() = runTest {
        val repo = repository { respond("bad request", HttpStatusCode.BadRequest) }

        val failure = repo.notifyEstablishmentAbsence(EstablishmentAbsenceNotice(name = "Comedor", formattedAddress = "Calle 1")).exceptionOrNull()

        assertTrue(failure is EstablishmentAbsenceFailure)
        assertEquals(EstablishmentAbsenceError.Validation, failure.error)
    }

    @Test
    fun `notifyEstablishmentAbsence maps 500 to Network`() = runTest {
        val repo = repository { respond("boom", HttpStatusCode.InternalServerError) }

        val failure = repo.notifyEstablishmentAbsence(EstablishmentAbsenceNotice(name = "Comedor", formattedAddress = "Calle 1")).exceptionOrNull()

        assertTrue(failure is EstablishmentAbsenceFailure)
        assertEquals(EstablishmentAbsenceError.Network, failure.error)
    }
}
