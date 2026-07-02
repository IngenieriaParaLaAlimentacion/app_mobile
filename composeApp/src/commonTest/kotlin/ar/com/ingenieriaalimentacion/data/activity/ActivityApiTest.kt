package ar.com.ingenieriaalimentacion.data.activity

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
import kotlin.test.assertNull

class ActivityApiTest {

    private fun api(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ActivityApi {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        return ActivityApi(client)
    }

    private fun emptyPage(page: Int = 1): String = """
        { "items": [], "total": 0, "page": $page, "page_size": 20, "total_pages": 1 }
    """.trimIndent()

    private fun respondPage(scope: MockRequestHandleScope, body: String) = scope.respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    @Test
    fun `sends page and page_size, omits type when null`() = runTest {
        var capturedRequest: HttpRequestData? = null
        val activityApi = api {
            capturedRequest = it
            respondPage(this, emptyPage())
        }

        activityApi.list(page = 1, pageSize = 20)

        val params = requireNotNull(capturedRequest).url.parameters
        assertEquals("1", params["page"])
        assertEquals("20", params["page_size"])
        assertNull(params["type"])
    }

    @Test
    fun `sends type when non-null`() = runTest {
        var capturedRequest: HttpRequestData? = null
        val activityApi = api {
            capturedRequest = it
            respondPage(this, emptyPage())
        }

        activityApi.list(page = 2, pageSize = 10, type = "subject")

        val params = requireNotNull(capturedRequest).url.parameters
        assertEquals("2", params["page"])
        assertEquals("10", params["page_size"])
        assertEquals("subject", params["type"])
    }
}
