package ar.com.ingenieriaalimentacion.domain.model.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Serializable
data class UserSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 0L,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val expiresAt: Instant = Clock.System.now() + expiresIn.seconds,
    val user: AuthUser? = null,
)
