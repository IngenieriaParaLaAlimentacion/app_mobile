package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.auth.storage.TokenStore
import ar.com.ingenieriaalimentacion.data.db.DatabaseDriverFactory
import ar.com.ingenieriaalimentacion.data.db.IosDatabaseDriverFactory
import ar.com.ingenieriaalimentacion.data.device.DeviceIdStore
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.data.network.connectivity.IosConnectivityObserver
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngine> { Darwin.create() }
    single { TokenStore() }
    single { DeviceIdStore() }
    single<DatabaseDriverFactory> { IosDatabaseDriverFactory() }
    single<ConnectivityObserver> { IosConnectivityObserver() }
}
