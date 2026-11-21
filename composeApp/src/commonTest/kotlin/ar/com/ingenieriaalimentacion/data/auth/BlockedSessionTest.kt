package ar.com.ingenieriaalimentacion.data.auth

import ar.com.ingenieriaalimentacion.data.auth.storage.MemorySessionManager
import ar.com.ingenieriaalimentacion.data.network.HttpClientFactory
import ar.com.ingenieriaalimentacion.domain.model.auth.AuthError
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import ar.com.ingenieriaalimentacion.domain.repository.SessionStorageException
import ar.com.ingenieriaalimentacion.domain.repository.StoredSession
import ar.com.ingenieriaalimentacion.fake.FakeDatabaseCleaner
import ar.com.ingenieriaalimentacion.fake.FakeIdGenerator
import ar.com.ingenieriaalimentacion.fake.signInForTest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BlockedSessionTest {
    private val store = MemorySessionManager()
    private val cleaner = FakeDatabaseCleaner()
    private val lifecycle = SessionLifecycle(store, cleaner, FakeIdGenerator())

    private suspend fun signIn() {
        lifecycle.bootstrap()
        lifecycle.signInForTest("A")
    }

    private fun authApi(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        AuthApi(HttpClientFactory.createRefreshClient(MockEngine(handler), HttpClientFactory.json()))

    private fun authed(
        api: AuthApi = authApi { error("Unexpected refresh") },
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = HttpClientFactory.createAuthedClient(
        MockEngine(handler), HttpClientFactory.json(), lifecycle, api, getDeviceId = { "device-id" },
    )

    private fun MockRequestHandleScope.problem(
        status: HttpStatusCode,
        type: String = USER_BLOCKED_TYPE,
    ) = respond(
        """{"type":"$type","detail":"Example"}""",
        status,
        headersOf(HttpHeaders.ContentType, "application/problem+json"),
    )

    @Test
    fun loginMatchesStatusAndType() = runTest {
        for ((status, type, error) in listOf(
            Triple(HttpStatusCode.Forbidden, USER_BLOCKED_TYPE, AuthError.AccountBlocked),
            Triple(HttpStatusCode.Forbidden, "/errors/account-pending-validation", AuthError.AccountPendingValidation),
            Triple(HttpStatusCode.Forbidden, "/errors/role-denied", AuthError.Network),
            Triple(HttpStatusCode.Unauthorized, USER_BLOCKED_TYPE, AuthError.InvalidCredentials),
        )) {
            val api = authApi { problem(status, type) }
            assertEquals(error, assertFailsWith<AuthFailure> { api.signIn("a@example.org", "secret", "device") }.error)
        }
    }

    @Test
    fun activeBearerBlockEndsSessionAndRestoresMarker() = runTest {
        signIn()
        authed { request ->
            assertEquals("Bearer access-A", request.headers[HttpHeaders.Authorization])
            problem(HttpStatusCode.Forbidden)
        }.use { client ->
            assertFailsWith<SessionEnded> { client.get("api/v1/users/me") }
        }
        assertEquals(SessionStatus.AccountBlocked, lifecycle.status.value)
        assertEquals(StoredSession.Blocked, store.load())
        assertEquals(3, cleaner.clearCachesCount)
        assertTrue(cleaner.clearedOwnerIds.isEmpty())

        val restarted = SessionLifecycle(store, cleaner, FakeIdGenerator())
        restarted.bootstrap()
        assertEquals(SessionStatus.AccountBlocked, restarted.status.value)
        assertNull(restarted.leaseOrNull())
        assertEquals(4, cleaner.clearCachesCount)
    }

    @Test
    fun expiredBearerRequestsTypedRefreshAndDoesNotRetryAfterBlock() = runTest {
        signIn()
        var refreshes = 0
        val api = authApi { request ->
            refreshes++
            assertEquals("/api/v1/auth/refresh", request.url.encodedPath)
            assertEquals(null, request.headers[HttpHeaders.Authorization])
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"report_user_blocked\":true"))
            assertTrue(body.contains("\"refresh_token\":\"refresh-A\""))
            problem(HttpStatusCode.Forbidden)
        }
        var requests = 0
        authed(api) {
            requests++
            respond("", HttpStatusCode.Unauthorized)
        }.use { client ->
            assertFailsWith<SessionEnded> { client.get("api/v1/users/me") }
        }
        assertEquals(1, requests)
        assertEquals(1, refreshes)
        assertEquals(StoredSession.Blocked, store.load())
    }

    @Test
    fun legacyRefreshRejectionKeepsOrdinarySignedOutState() = runTest {
        signIn()
        val api = authApi { problem(HttpStatusCode.Unauthorized, "/errors/auth/invalid-refresh-token") }
        authed(api) { respond("", HttpStatusCode.Unauthorized) }.use { client ->
            assertEquals(HttpStatusCode.Unauthorized, client.get("api/v1/users/me").status)
        }
        assertEquals(SessionStatus.NotAuthenticated(), lifecycle.status.value)
        assertEquals(StoredSession.Empty, store.load())
    }

    @Test
    fun unrelatedAndMalformedForbiddenBodiesRemainReadable() = runTest {
        signIn()
        val cases = listOf("{broken", """{"type":"/errors/establishment-access-denied"}""")
        for (body in cases) {
            authed { respond(body, HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "application/problem+json")) }
                .use { client ->
                    val actual = client.get("api/v1/users/me")
                    assertEquals(HttpStatusCode.Forbidden, actual.status)
                    assertEquals(body, actual.bodyAsText())
                }
        }
        assertIs<SessionStatus.Authenticated>(lifecycle.status.value)
    }

    @Test
    fun refreshRetriesWithRotatedBearerAndKeepsOwnerAndGeneration() = runTest {
        signIn()
        val lease = lifecycle.leaseOrNull()
        val api = authApi {
            respond(
                """{"access_token":"fresh-access","refresh_token":"fresh-refresh"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val headers = mutableListOf<String?>()
        authed(api) { request ->
            headers += request.headers[HttpHeaders.Authorization]
            if (headers.size == 1) respond("", HttpStatusCode.Unauthorized) else respond("ok", HttpStatusCode.OK)
        }.use { client ->
            assertEquals("ok", client.get("api/v1/users/me").bodyAsText())
        }
        assertEquals(listOf<String?>("Bearer access-A", "Bearer fresh-access"), headers)
        assertEquals(lease, lifecycle.leaseOrNull())
        val restarted = SessionLifecycle(store, cleaner, FakeIdGenerator())
        restarted.bootstrap()
        assertEquals(lease, restarted.leaseOrNull())
        assertEquals("fresh-refresh", restarted.active()?.session?.refreshToken)
    }

    @Test
    fun delayedOldResponsesCannotReturnDataOrEndTheNewSession() = runTest {
        signIn()
        val oldLease = lifecycle.leaseOrNull()!!
        for (status in listOf(HttpStatusCode.OK, HttpStatusCode.Forbidden)) {
            if (lifecycle.leaseOrNull()?.ownerId != "A") {
                lifecycle.end(lifecycle.leaseOrNull()!!, SessionExit.SignedOut)
                lifecycle.signInForTest("A")
            }
            val requestStarted = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            authed {
                requestStarted.complete(Unit)
                release.await()
                problem(status)
            }.use { client ->
                val stale = async { assertFailsWith<SessionEnded> { client.get("api/v1/users/me") } }
                requestStarted.await()
                lifecycle.end(lifecycle.leaseOrNull()!!, SessionExit.Blocked)
                lifecycle.signInForTest("B")
                release.complete(Unit)
                stale.await()
            }
            assertEquals("B", lifecycle.leaseOrNull()?.ownerId)
            assertNotEquals(oldLease, lifecycle.leaseOrNull())
        }
    }

    @Test
    fun twoBlockResponsesCloseTheSameSessionOnlyOnce() = runTest {
        signIn()
        val requestStarted = Channel<Unit>(capacity = 2)
        val release = CompletableDeferred<Unit>()
        authed {
            requestStarted.send(Unit)
            release.await()
            problem(HttpStatusCode.Forbidden)
        }.use { client ->
            val requests = List(2) {
                async { assertFailsWith<SessionEnded> { client.get("api/v1/users/me") } }
            }
            repeat(2) { requestStarted.receive() }
            release.complete(Unit)
            requests.forEach { it.await() }
        }
        assertEquals(SessionStatus.AccountBlocked, lifecycle.status.value)
        assertEquals(3, cleaner.clearCachesCount)
    }

    @Test
    fun concurrentExpiredRequestsRefreshOnceAndRetryWithTheSameNewBearer() = runTest {
        signIn()
        val originalRequests = Channel<Unit>(capacity = 2)
        val retryRequests = Channel<String?>(capacity = 2)
        val release = CompletableDeferred<Unit>()
        var refreshes = 0
        val api = authApi {
            refreshes++
            respond(
                """{"access_token":"fresh-access","refresh_token":"fresh-refresh"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        authed(api) { request ->
            val authorization = request.headers[HttpHeaders.Authorization]
            if (authorization == "Bearer access-A") {
                originalRequests.send(Unit)
                release.await()
                respond("", HttpStatusCode.Unauthorized)
            } else {
                retryRequests.send(authorization)
                respond("ok", HttpStatusCode.OK)
            }
        }.use { client ->
            val calls = List(2) { async { client.get("api/v1/users/me").bodyAsText() } }
            repeat(2) { originalRequests.receive() }
            release.complete(Unit)
            calls.forEach { assertEquals("ok", it.await()) }
        }
        assertEquals(1, refreshes)
        repeat(2) { assertEquals("Bearer fresh-access", retryRequests.receive()) }
    }

    @Test
    fun cancellingAnOriginatingRequestDoesNotDiscardARotatedCredential() = runTest {
        signIn()
        val refreshStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = authApi {
            refreshStarted.complete(Unit)
            release.await()
            respond(
                """{"access_token":"fresh-access","refresh_token":"fresh-refresh"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        authed(api) { respond("", HttpStatusCode.Unauthorized) }.use { client ->
            val request = async { client.get("api/v1/users/me") }
            refreshStarted.await()
            request.cancel()
            release.complete(Unit)
            request.join()
        }
        assertEquals("fresh-refresh", lifecycle.active()?.session?.refreshToken)
        assertEquals("fresh-refresh", assertIs<StoredSession.Active>(store.load()).session.refreshToken)
    }

    @Test
    fun durableWriteFailureKeepsClosedAndRetriesMarker() = runTest {
        val stored = MemorySessionManager()
        val broken = object : SessionManager by stored {
            var canWriteMarker = false
            override suspend fun replace(value: StoredSession) {
                if (value == StoredSession.Blocked && !canWriteMarker) {
                    throw SessionStorageException("Test storage temporarily unavailable")
                }
                stored.replace(value)
            }
        }
        val sessions = SessionLifecycle(broken, cleaner, FakeIdGenerator())
        sessions.bootstrap()
        sessions.signInForTest("A")
        val lease = sessions.leaseOrNull()!!
        val closing = async { sessions.end(lease, SessionExit.Blocked) }
        runCurrent()
        assertEquals(SessionStatus.Closing, sessions.status.value)
        assertNull(sessions.leaseOrNull())
        assertFailsWith<SessionEnded> { sessions.local(lease) { error("An ended lease ran a write") } }
        broken.canWriteMarker = true
        closing.await()
        assertEquals(StoredSession.Blocked, stored.load())
        assertEquals(SessionStatus.AccountBlocked, sessions.status.value)
    }

    @Test
    fun storedCredentialsWithoutAnOwnerCannotRestoreSession() = runTest {
        store.replace(StoredSession.Active(UserSession("access", "refresh"), "existing-generation"))
        lifecycle.bootstrap()
        assertNull(lifecycle.leaseOrNull())
        assertEquals(StoredSession.Empty, store.load())
        assertEquals(SessionStatus.NotAuthenticated(), lifecycle.status.value)
    }
}
