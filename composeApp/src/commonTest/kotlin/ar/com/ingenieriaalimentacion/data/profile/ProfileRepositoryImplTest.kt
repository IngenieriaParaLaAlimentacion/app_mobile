package ar.com.ingenieriaalimentacion.data.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentRequestError
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
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
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ProfileRepositoryImpl {
        val client = HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            defaultRequest { url("https://api.example.com/") }
        }
        return ProfileRepositoryImpl(ProfileApi(client))
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
}
