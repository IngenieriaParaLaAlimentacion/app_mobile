package ar.com.ingenieriaalimentacion.presentation.navigation

import ar.com.ingenieriaalimentacion.data.network.ApiConfig

/**
 * URI patterns for links that open the app from outside.
 *
 * The verification link is an https App Link on the backoffice host, which differs per
 * environment, so the pattern is built from [ApiConfig.WEB_HOST] rather than written as a
 * literal. The Android manifest claims the same host and path; the three must stay in step
 * with the backend's `BACKOFFICE_DEEPLINK_BASE` + `/verify-email?token=`.
 */
object DeepLinks {
    const val VERIFY_EMAIL_PATH = "/verify-email"
    const val TOKEN_QUERY_PARAM = "token"

    /** `{token}` must match the property name of `Route.VerifyEmail`. */
    fun verifyEmailUriPattern(host: String): String =
        "https://$host$VERIFY_EMAIL_PATH?$TOKEN_QUERY_PARAM={token}"

    val VERIFY_EMAIL_URI_PATTERN: String = verifyEmailUriPattern(ApiConfig.WEB_HOST)
}
