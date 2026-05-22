package ar.com.ingenieriaalimentacion.data.establishment

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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EstablishmentRepositoryImplTest {

    private val paginatedBody = """
        {
          "items": [
            { "id": "1", "name": "Comedor Norte", "address": "Calle 1" },
            { "id": "2", "name": "Comedor Sur", "address": "Calle 2" }
          ],
          "total": 2,
          "page": 1,
          "page_size": 100,
          "total_pages": 1
        }
    """.trimIndent()

    private fun repository(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): EstablishmentRepositoryImpl {
        val client = HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        return EstablishmentRepositoryImpl(EstablishmentApi(client))
    }

    @Test
    fun `maps paginated items to domain`() = runTest {
        val repo = repository {
            respond(
                content = paginatedBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = repo.getEstablishments()

        val items = result.getOrNull()
        assertEquals(2, items?.size)
        assertEquals("Comedor Norte", items?.first()?.name)
        assertEquals("Calle 2", items?.get(1)?.address)
    }

    @Test
    fun `error response yields failure`() = runTest {
        val repo = repository {
            respond(
                content = "error",
                status = HttpStatusCode.InternalServerError,
            )
        }

        val result = repo.getEstablishments()

        assertTrue(result.isFailure)
    }
}
