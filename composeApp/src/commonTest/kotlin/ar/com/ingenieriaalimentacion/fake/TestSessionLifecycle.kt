package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.auth.storage.MemorySessionManager
import ar.com.ingenieriaalimentacion.domain.model.auth.AuthUser
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession

internal suspend fun activeSessionLifecycle(ownerId: String = "owner"): SessionLifecycle =
    inactiveSessionLifecycle().apply {
        bootstrap()
        signInForTest(ownerId)
    }

internal suspend fun SessionLifecycle.signInForTest(ownerId: String) {
    completeSignIn(
        beginSignIn(),
        UserSession(
            accessToken = "access-$ownerId",
            refreshToken = "refresh-$ownerId",
            user = AuthUser(ownerId, "$ownerId@example.org", "Test", "User", "field_worker"),
        ),
    )
}

internal fun inactiveSessionLifecycle() =
    SessionLifecycle(MemorySessionManager(), FakeDatabaseCleaner(), FakeIdGenerator())
