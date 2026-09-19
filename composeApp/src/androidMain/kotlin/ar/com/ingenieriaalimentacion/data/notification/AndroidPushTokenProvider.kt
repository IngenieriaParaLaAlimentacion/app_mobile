package ar.com.ingenieriaalimentacion.data.notification

import ar.com.ingenieriaalimentacion.domain.model.notification.PushPlatform
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidPushTokenProvider : PushTokenProvider {
    override val platform = PushPlatform.Android
    override val deviceName: String = "Android"

    override suspend fun currentToken(): Result<String> =
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val token = if (task.isSuccessful) task.result?.takeIf { it.isNotBlank() } else null
                continuation.resume(
                    token?.let { Result.success(it) }
                        ?: Result.failure(task.exception ?: IllegalStateException("FCM returned no token")),
                )
            }
        }

    override suspend fun deleteToken(): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
                continuation.resume(
                    if (task.isSuccessful) Result.success(Unit)
                    else Result.failure(
                        task.exception ?: IllegalStateException("FCM token deletion failed"),
                    ),
                )
            }
        }
}
