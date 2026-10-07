package ar.com.ingenieriaalimentacion.data.auth

import ar.com.ingenieriaalimentacion.data.network.HttpClientFactory
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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class AuthApiVerifyEmailTest {

    // Mirrors the production refresh client, including expectSuccess = true: the point of
    // these tests is that verifyEmail branches on the status instead of throwing.
    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): AuthApi {
        val client = HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) { json(HttpClientFactory.json()) }
            defaultRequest { url("https://api.example.com/") }
        }
        return AuthApi(refreshClient = client)
    }

    private fun problemHeaders() = headersOf(HttpHeaders.ContentType, "application/problem+json")

    private fun problem(type: String) =
        """{"type":"$type","title":"x","status":401,"detail":"y"}"""

    @Test
    fun `204 maps to Verified`() = runTest {
        val api = api { respond("", HttpStatusCode.NoContent) }
        assertEquals(VerifyEmailOutcome.Verified, api.verifyEmail("tok"))
    }

    @Test
    fun `posts the token to the confirm endpoint`() = runTest {
        var captured: HttpRequestData? = null
        val api = api { request ->
            captured = request
            respond("", HttpStatusCode.NoContent)
        }

        api.verifyEmail("tok-123")

        val request = assertNotNull(captured)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/auth/verify-email/confirm", request.url.encodedPath)
        assertEquals("""{"token":"tok-123"}""", (request.body as TextContent).text)
    }

    @Test
    fun `401 verification-token-used maps to TokenUsed`() = runTest {
        val api = api {
            respond(problem("/errors/auth/verification-token-used"), HttpStatusCode.Unauthorized, problemHeaders())
        }
        assertEquals(VerifyEmailOutcome.TokenUsed, api.verifyEmail("tok"))
    }

    @Test
    fun `401 verification-token-expired maps to TokenExpired`() = runTest {
        val api = api {
            respond(problem("/errors/auth/verification-token-expired"), HttpStatusCode.Unauthorized, problemHeaders())
        }
        assertEquals(VerifyEmailOutcome.TokenExpired, api.verifyEmail("tok"))
    }

    @Test
    fun `401 invalid-verification-token maps to InvalidToken`() = runTest {
        val api = api {
            respond(problem("/errors/auth/invalid-verification-token"), HttpStatusCode.Unauthorized, problemHeaders())
        }
        assertEquals(VerifyEmailOutcome.InvalidToken, api.verifyEmail("tok"))
    }

    @Test
    fun `401 with an unrecognized type maps to InvalidToken`() = runTest {
        val api = api {
            respond(problem("/errors/auth/something-new"), HttpStatusCode.Unauthorized, problemHeaders())
        }
        assertEquals(VerifyEmailOutcome.InvalidToken, api.verifyEmail("tok"))
    }

    @Test
    fun `401 without a body maps to InvalidToken`() = runTest {
        val api = api { respondError(HttpStatusCode.Unauthorized) }
        assertEquals(VerifyEmailOutcome.InvalidToken, api.verifyEmail("tok"))
    }

    @Test
    fun `422 maps to InvalidToken`() = runTest {
        val api = api { respondError(HttpStatusCode.UnprocessableEntity) }
        assertEquals(VerifyEmailOutcome.InvalidToken, api.verifyEmail("tok"))
    }

    @Test
    fun `500 maps to Retryable`() = runTest {
        val api = api { respondError(HttpStatusCode.InternalServerError) }
        assertIs<VerifyEmailOutcome.Retryable>(api.verifyEmail("tok"))
    }

    @Test
    fun `transport failure maps to Retryable with cause`() = runTest {
        val api = api { throw RuntimeException("no network") }
        val retryable = assertIs<VerifyEmailOutcome.Retryable>(api.verifyEmail("tok"))
        assertNotNull(retryable.cause)
    }
}
