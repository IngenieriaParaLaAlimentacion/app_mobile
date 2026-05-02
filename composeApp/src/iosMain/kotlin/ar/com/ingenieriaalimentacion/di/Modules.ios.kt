package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.auth.storage.TokenStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngine> { Darwin.create() }
    single { TokenStore() }
}
