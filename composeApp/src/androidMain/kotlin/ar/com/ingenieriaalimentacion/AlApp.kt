package ar.com.ingenieriaalimentacion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import ar.com.ingenieriaalimentacion.data.notification.StaleEstablishmentMessagingService
import ar.com.ingenieriaalimentacion.di.KoinGlobalContext
import org.koin.android.ext.koin.androidContext

class AlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KoinGlobalContext.initializeGlobalContext {
            androidContext(this@AlApp)
        }
        val channel = NotificationChannel(
            StaleEstablishmentMessagingService.STALE_ESTABLISHMENTS_CHANNEL_ID,
            "Recordatorios de stock",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Avisos de establecimientos sin egresos recientes"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
