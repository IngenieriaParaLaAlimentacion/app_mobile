package ar.com.ingenieriaalimentacion.di

import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformModule: Module

val sharedModule = module {
    // Place app-wide non-platform-specific singletons here as the project grows.
}
