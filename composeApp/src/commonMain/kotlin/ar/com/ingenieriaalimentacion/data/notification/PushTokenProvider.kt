package ar.com.ingenieriaalimentacion.data.notification

import ar.com.ingenieriaalimentacion.domain.model.notification.PushPlatform

interface PushTokenProvider {
    val platform: PushPlatform
    val deviceName: String?
    suspend fun currentToken(): Result<String>
    suspend fun deleteToken(): Result<Unit>
}
