package ar.com.ingenieriaalimentacion.data.notification

import ar.com.ingenieriaalimentacion.domain.model.notification.PushPlatform
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class IosPushTokenProvider : PushTokenProvider {
    override val platform = PushPlatform.Ios
    override val deviceName: String = "iOS"

    override suspend fun currentToken(): Result<String> = suspendCancellableCoroutine { continuation ->
        IosFirebaseMessagingBridgeProvider.requireBridge().getToken { token, error ->
            continuation.resume(
                token?.takeIf { it.isNotBlank() }?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException(error ?: "FCM returned no token")),
            )
        }
    }

    override suspend fun deleteToken(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        IosFirebaseMessagingBridgeProvider.requireBridge().deleteToken { error ->
            continuation.resume(
                if (error == null) Result.success(Unit)
                else Result.failure(IllegalStateException(error)),
            )
        }
    }
}
