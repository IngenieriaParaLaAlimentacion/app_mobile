package ar.com.ingenieriaalimentacion.data.notification.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequest(
    @SerialName("installation_id") val installationId: String,
    @SerialName("fcm_token") val fcmToken: String,
    val platform: String,
    @SerialName("device_name")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val deviceName: String? = null,
)
