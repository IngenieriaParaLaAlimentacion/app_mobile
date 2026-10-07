package ar.com.ingenieriaalimentacion.data.network

import ar.com.ingenieriaalimentacion.env.AlEnv

/**
 * Environment-scoped backend configuration.
 *
 * Values are baked at build time by BuildKonfig from `local.properties` (`api_url_tst` /
 * `api_url_prod`, `web_host_tst` / `web_host_prod`) — see `local.properties.example` and the
 * `buildkonfig` block in `composeApp/build.gradle.kts`. This is the only place that reads the generated `AlEnv`,
 * so adding a new environment value means touching Gradle plus this file and nothing else.
 */
object ApiConfig {

    /** Either `"tst"` or `"prod"`; selected by the Android flavor or `-PtargetEnvironment`. */
    val ENVIRONMENT: String = AlEnv.APP_ENVIRONMENT

    val IS_PROD: Boolean = ENVIRONMENT == "prod"

    val BASE_URL: String = AlEnv.API_URL

    /**
     * Bare host of this environment's backoffice web app (`web_host_tst` / `web_host_prod`,
     * each with a committed default). The verification email links to
     * `https://WEB_HOST/verify-email`; the Android manifest claims the same host through the
     * `webHost` manifest placeholder.
     */
    val WEB_HOST: String = AlEnv.WEB_HOST
}
