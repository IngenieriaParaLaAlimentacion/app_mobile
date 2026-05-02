package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.app.AppViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.home.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelsModule = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::HomeViewModel)
}
