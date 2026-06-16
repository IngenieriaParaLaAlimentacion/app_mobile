package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.auth.storage.TokenStore
import ar.com.ingenieriaalimentacion.data.db.AndroidDatabaseDriverFactory
import ar.com.ingenieriaalimentacion.data.db.DatabaseDriverFactory
import ar.com.ingenieriaalimentacion.data.device.DeviceIdStore
import ar.com.ingenieriaalimentacion.data.network.connectivity.AndroidConnectivityObserver
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    single { TokenStore(context = androidContext()) }
    single { DeviceIdStore(context = androidContext()) }
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }
    single<ConnectivityObserver> { AndroidConnectivityObserver(androidContext()) }
}
