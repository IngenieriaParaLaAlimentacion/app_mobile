package ar.com.ingenieriaalimentacion.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class DeepLinksTest {
    // Pins the contract with the backend (BACKOFFICE_DEEPLINK_BASE + "/verify-email?token=")
    // and with the manifest's android:path. The `{token}` name must match Route.VerifyEmail.
    @Test
    fun `verify-email pattern is https on the given host with a token query`() {
        assertEquals(
            "https://backoffice.example.org/verify-email?token={token}",
            DeepLinks.verifyEmailUriPattern("backoffice.example.org"),
        )
    }
}
