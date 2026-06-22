package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
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

class SubjectApiTest {

    private val pageBody = """
        {
          "items": [
            { "id": "1", "dni": 30111222, "name": "Ana", "last_name": "Pérez", "sex": "female" },
            { "id": "2", "dni": 28999888, "name": "Luis", "last_name": "Gómez", "sex": "male" }
          ],
          "total": 5,
          "page": 1,
          "page_size": 20,
          "total_pages": 3
        }
    """.trimIndent()

    // Mirrors the authed client: no `expectSuccess`, so list() must check the status itself.
    private fun repository(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): SubjectQueryRepositoryImpl {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        return SubjectQueryRepositoryImpl(SubjectApi(client))
    }

    @Test
    fun `parses paginated envelope to domain page`() = runTest {
        val repo = repository {
            respond(
                content = pageBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val page = repo.searchSubjects(query = null, page = 1, pageSize = 20).getOrThrow()

        assertEquals(2, page.items.size)
        assertEquals(3, page.totalPages)
        assertEquals(1, page.page)
        assertEquals("Pérez", page.items.first().lastName)
        assertEquals(Sex.Female, page.items.first().sex)
        assertEquals(Sex.Male, page.items[1].sex)
    }

    @Test
    fun `server error yields failure`() = runTest {
        val repo = repository {
            respond(content = "error", status = HttpStatusCode.InternalServerError)
        }

        val result = repo.searchSubjects(query = "x", page = 1, pageSize = 20)

        assertTrue(result.isFailure)
    }
}
