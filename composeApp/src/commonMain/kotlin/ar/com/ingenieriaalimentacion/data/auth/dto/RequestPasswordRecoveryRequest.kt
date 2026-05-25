package ar.com.ingenieriaalimentacion.data.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class RequestPasswordRecoveryRequest(val email: String)
