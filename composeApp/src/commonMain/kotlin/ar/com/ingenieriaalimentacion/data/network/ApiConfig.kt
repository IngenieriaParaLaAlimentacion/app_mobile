package ar.com.ingenieriaalimentacion.data.network

import ar.com.ingenieriaalimentacion.env.AlEnv

/**
 * Environment-scoped backend configuration.
 *
 * Values are baked at build time by BuildKonfig from `local.properties` (`api_url_tst` /
 * `api_url_prod`) — see `local.properties.example` and the `buildkonfig` block in
 * `composeApp/build.gradle.kts`. This is the only place that reads the generated `AlEnv`,
 * so adding a new environment value means touching Gradle plus this file and nothing else.
 */
object ApiConfig {

    /** Either `"tst"` or `"prod"`; selected by the Android flavor or `-PtargetEnvironment`. */
    val ENVIRONMENT: String = AlEnv.APP_ENVIRONMENT

    val IS_PROD: Boolean = ENVIRONMENT == "prod"

    val BASE_URL: String = AlEnv.API_URL
}
