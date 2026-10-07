package ar.com.ingenieriaalimentacion.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class DeepLinksTest {
    // Pins the contract with the backend (BACKOFFICE_DEEPLINK_BASE + "/verify-email?token=").
    @Test
    fun `verify-email pattern is https on the given host with a token query`() {
        assertEquals(
            "https://backoffice.example.org/verify-email?token={token}",
            DeepLinks.verifyEmailUriPattern("backoffice.example.org"),
        )
    }

    // Navigation fills the route from the `{token}` placeholder by property name; a rename on
    // either side would open the screen with an empty token.
    @Test
    fun `pattern placeholder is the argument name of the VerifyEmail route`() {
        assertEquals(
            DeepLinks.TOKEN_QUERY_PARAM,
            Route.VerifyEmail.serializer().descriptor.getElementName(0),
        )
    }
}
