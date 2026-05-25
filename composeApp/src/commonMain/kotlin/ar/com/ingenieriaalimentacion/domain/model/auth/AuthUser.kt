package ar.com.ingenieriaalimentacion.domain.model.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthUser(
    val id: String,
    val email: String,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val role: String,
)
