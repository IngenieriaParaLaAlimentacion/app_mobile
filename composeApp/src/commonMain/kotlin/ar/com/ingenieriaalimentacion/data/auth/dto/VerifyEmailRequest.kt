package ar.com.ingenieriaalimentacion.data.auth.dto

import kotlinx.serialization.Serializable

/** Body of `POST /api/v1/auth/verify-email/confirm`. */
@Serializable
data class VerifyEmailRequest(val token: String)
