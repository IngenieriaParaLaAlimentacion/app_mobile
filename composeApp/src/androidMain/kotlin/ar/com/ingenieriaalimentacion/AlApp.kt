package ar.com.ingenieriaalimentacion

import android.app.Application
import ar.com.ingenieriaalimentacion.di.KoinGlobalContext
import org.koin.android.ext.koin.androidContext

class AlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KoinGlobalContext.initializeGlobalContext {
            androidContext(this@AlApp)
        }
    }
}
