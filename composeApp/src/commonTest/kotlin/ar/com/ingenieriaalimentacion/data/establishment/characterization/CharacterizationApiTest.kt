package ar.com.ingenieriaalimentacion.data.establishment.characterization

import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationError
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationFailure
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationStatus
import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization
import ar.com.ingenieriaalimentacion.domain.model.establishment.Meal
import ar.com.ingenieriaalimentacion.domain.model.establishment.Premises
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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CharacterizationApiTest {

    // Same configuration as the production authed client: no expectSuccess, defaults encoded.
    private fun repository(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): EstablishmentCharacterizationRepositoryImpl {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
            defaultRequest { url("https://api.example.com/") }
        }
        return EstablishmentCharacterizationRepositoryImpl(CharacterizationApi(client))
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private fun MockRequestHandleScope.problem(status: HttpStatusCode, field: String? = null) = respond(
        content = """{"type":"/errors/validation-failed","title":"x","status":${status.value}""" +
            (field?.let { ""","field":"$it"""" } ?: "") + "}",
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
    )

    private suspend fun EstablishmentCharacterizationRepositoryImpl.replaceError(): CharacterizationError {
        val failure = replace("e1", EstablishmentCharacterization.EMPTY).exceptionOrNull()
        return assertIs<CharacterizationFailure>(failure).error
    }

    @Test
    fun `get reads the profile from the dashboard and ignores the metrics`() = runTest {
        var path: String? = null
        val repo = repository { request ->
            path = request.url.encodedPath
            json(
                """{"establishment":{"id":"e1"},"has_data":false,"metrics":{},
                   "characterization":{"meals":["lunch"],"premises":"street","opening_hours":[]}}""",
            )
        }

        val profile = repo.get("e1").getOrThrow()

        assertEquals("/api/v1/establishments/e1/dashboard", path)
        assertEquals(setOf(Meal.Lunch), profile.meals)
        assertEquals(Premises.Street, profile.premises)
    }

    @Test
    fun `get treats a missing profile as empty`() = runTest {
        val repo = repository { json("""{"has_data":false}""") }

        assertEquals(EstablishmentCharacterization.EMPTY, repo.get("e1").getOrThrow())
    }

    @Test
    fun `replace puts the whole profile and returns the computed status`() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var body: String? = null
        val repo = repository { request ->
            method = request.method
            path = request.url.encodedPath
            body = (request.body as TextContent).text
            json("""{"characterization_status":"complete","characterization":{}}""")
        }

        val status = repo.replace("e1", EstablishmentCharacterization(meals = setOf(Meal.Dinner))).getOrThrow()

        assertEquals(CharacterizationStatus.Complete, status)
        assertEquals(HttpMethod.Put, method)
        assertEquals("/api/v1/establishments/e1/characterization", path)
        val sent = Json.parseToJsonElement(body!!).jsonObject
        assertEquals("dinner", sent.getValue("meals").jsonArray.single().jsonPrimitive.content)
        // Full replace: unanswered fields still travel, so the backend clears them.
        assertTrue("care_tasks" in sent && "premises" in sent && "opening_hours" in sent)
    }

    @Test
    fun `422 maps to InvalidField with the reported field`() = runTest {
        val repo = repository { problem(HttpStatusCode.UnprocessableEntity, field = "premises_other") }

        assertEquals(CharacterizationError.InvalidField("premises_other"), repo.replaceError())
    }

    @Test
    fun `400 without a field maps to InvalidField with no field`() = runTest {
        val repo = repository { problem(HttpStatusCode.BadRequest) }

        assertEquals(CharacterizationError.InvalidField(null), repo.replaceError())
    }

    @Test
    fun `403 maps to Forbidden`() = runTest {
        val repo = repository { problem(HttpStatusCode.Forbidden) }

        assertEquals(CharacterizationError.Forbidden, repo.replaceError())
    }

    @Test
    fun `404 maps to NotFound`() = runTest {
        val repo = repository { problem(HttpStatusCode.NotFound) }

        assertEquals(CharacterizationError.NotFound, repo.replaceError())
    }

    @Test
    fun `401 maps to Unauthorized`() = runTest {
        val repo = repository { problem(HttpStatusCode.Unauthorized) }

        assertEquals(CharacterizationError.Unauthorized, repo.replaceError())
    }

    @Test
    fun `5xx maps to Network`() = runTest {
        val repo = repository { respondError(HttpStatusCode.ServiceUnavailable) }

        assertEquals(CharacterizationError.Network, repo.replaceError())
    }

    @Test
    fun `transport failures map to Network`() = runTest {
        val repo = repository { throw RuntimeException("offline") }

        assertEquals(CharacterizationError.Network, repo.replaceError())
    }
}
