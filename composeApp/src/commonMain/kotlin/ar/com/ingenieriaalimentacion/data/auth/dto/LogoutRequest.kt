package ar.com.ingenieriaalimentacion.data.auth.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LogoutRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("refresh_token") val refreshToken: String,
)
