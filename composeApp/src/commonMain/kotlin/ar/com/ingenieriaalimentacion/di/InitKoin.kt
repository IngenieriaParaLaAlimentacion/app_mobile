package ar.com.ingenieriaalimentacion.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(
    config: KoinAppDeclaration? = null,
): KoinApplication {
    return startKoin {
        config?.invoke(this)
        modules(
            listOf(
                platformModule,
                sharedModule,
                databaseModule,
                clientsModule,
                repositoriesModule,
                useCasesModule,
                viewModelsModule,
            )
        )
    }
}

@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@kotlin.native.ObjCName("doInitKoin")
fun doInitKoin() {
    if (!KoinGlobalContext.isInitialized) {
        KoinGlobalContext.initializeGlobalContext()
    }
}
