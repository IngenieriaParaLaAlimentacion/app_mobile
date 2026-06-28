package ar.com.ingenieriaalimentacion.data.registration.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterFieldWorkerRequest(
    val email: String,
    val password: String,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val establishments: List<String>,
)
