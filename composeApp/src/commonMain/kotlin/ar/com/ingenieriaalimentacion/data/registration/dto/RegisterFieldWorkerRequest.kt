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
    val locality: String? = null,
    val description: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
)
