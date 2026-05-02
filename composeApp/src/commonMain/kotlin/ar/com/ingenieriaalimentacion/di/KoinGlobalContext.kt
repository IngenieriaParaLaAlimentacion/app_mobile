package ar.com.ingenieriaalimentacion.di

import org.koin.core.KoinApplication
import org.koin.dsl.KoinAppDeclaration

object KoinGlobalContext {
    private lateinit var koinApplicationInstance: KoinApplication

    val isInitialized: Boolean
        get() = this::koinApplicationInstance.isInitialized

    fun initializeGlobalContext(config: KoinAppDeclaration? = null) {
        if (isInitialized) return
        koinApplicationInstance = initKoin(config)
    }

    fun koin() = if (isInitialized) {
        koinApplicationInstance.koin
    } else {
        throw IllegalStateException("KoinGlobalContext has not been initialized.")
    }
}

internal fun getKoinInstance() = KoinGlobalContext.koin()
