package ar.com.ingenieriaalimentacion.data.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val password: String,
)
