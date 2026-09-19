package ar.com.ingenieriaalimentacion.data.notification

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ar.com.ingenieriaalimentacion.MainActivity
import ar.com.ingenieriaalimentacion.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class StaleEstablishmentMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushTokenBridge.handleNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val push = StaleEstablishmentPushParser.parse(message.data) ?: return
        val notification = message.notification ?: return
        val tag = "stale-establishment:${push.establishmentId}"
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            message.data.forEach { (key, value) -> putExtra(key, value) }
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val built = NotificationCompat.Builder(this, STALE_ESTABLISHMENTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(this).notify(tag, NOTIFICATION_ID, built)
    }

    companion object {
        const val STALE_ESTABLISHMENTS_CHANNEL_ID = "stale_establishments"
        private const val NOTIFICATION_ID = 1
    }
}
