package ar.com.ingenieriaalimentacion.data.registration.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegistrationResponse(
    val id: String,
    val email: String,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val role: String,
    val status: String,
    val establishments: List<String>,
)
