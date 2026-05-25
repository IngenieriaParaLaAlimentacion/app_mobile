package ar.com.ingenieriaalimentacion.data.auth.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResetPasswordRequest(
    val token: String,
    @SerialName("new_password") val password: String,
)
